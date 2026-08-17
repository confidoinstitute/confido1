package tools.confido.store

import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import org.litote.kmongo.coroutine.commitTransactionAndAwait
import org.slf4j.LoggerFactory
import tools.confido.state.serverState

private val storeLog = LoggerFactory.getLogger("confido.store")!!

/**
 * A store write staged inside a [transact] block. Split into the database write (inside
 * the Mongo transaction) and the in-memory application + change emission (strictly after
 * a successful commit) — so a failed commit leaves RAM untouched.
 */
interface StagedWrite {
    /** Whether [flush] performs a database write. A transaction that stages no such op
     *  never opens a Mongo session at all (transient-only transactions are memory-only —
     *  durability is a per-node policy, not a property of the transaction, §3.5). */
    val needsDb: Boolean get() = true
    /** Perform the database write, inside the transaction when [sess] is non-null. */
    suspend fun flush(sess: ClientSession?)
    /** Swap in-memory state and emit changes. Runs only after the commit succeeded. */
    fun apply()
}

class TxnScope internal constructor() {
    internal val staged = mutableListOf<StagedWrite>()
    private val slots = mutableMapOf<Any, StagedWrite>()
    private var closed = false

    fun stage(op: StagedWrite) {
        // Coroutines launched inside a transact block inherit the ambient scope; a
        // write staged after the transaction flushed would be silently lost (or worse,
        // applied to RAM without its database write) — make that a loud bug instead.
        check(!closed) { "Transaction already flushed — do not stage writes from coroutines outliving their transact block" }
        staged.add(op)
    }

    /**
     * Keyed net-effect slot (the per-transaction write set): the first touch of [key]
     * creates the op via [create] and stages it at the current list position; later
     * touches return the same op for in-place composition. This is what makes write
     * combinators (store modify/insert/delete, cell update) see each other's staged
     * results — eager evaluation against the write set — while plain reads keep the
     * committed-snapshot contract. One flush and one net change event per touched key.
     */
    fun <T : StagedWrite> slot(key: Any, create: () -> T): T {
        check(!closed) { "Transaction already flushed — do not stage writes from coroutines outliving their transact block" }
        slots[key]?.let {
            @Suppress("UNCHECKED_CAST")
            return it as T
        }
        return create().also { slots[key] = it; staged.add(it) }
    }

    internal fun close() { closed = true }
}

/** The executing code's ambient transaction scope, or null (see [currentTxnScope]). */
internal fun ambientTxnScope(): TxnScope? = currentTxnScope.get()

/**
 * The executing code's ambient transaction, readable from non-suspending code. There is
 * only ever one open transaction (single writer), but "open" is not "ours": the
 * transaction coroutine suspends (the commit await, in-block reads) and interleaved
 * coroutines are NOT inside it. The [asContextElement] bridge keeps this accurate: the
 * thread-local is set whenever the transaction coroutine is dispatched onto the thread
 * and restored to null whenever it suspends — so it is non-null exactly while code
 * (transitively) inside the transact block is executing, and null everywhere else,
 * including the post-commit apply phase and its synchronous change dispatch.
 */
private val currentTxnScope = ThreadLocal<TxnScope?>()

/**
 * Atomic multi-store read-modify-write. Serializes against all other writers (via the
 * global mutation lock), so reads inside the block see the latest committed state and
 * it cannot change before the block commits.
 *
 * Order of operations: run block (store and cell ops stage themselves) → open a Mongo
 * session iff any staged op writes the database → flush → commit → apply staged writes
 * to memory and emit changes. A transaction that stages only transient (memory-only)
 * ops skips the Mongo session entirely.
 *
 * Nesting: a transact inside a transact joins the ambient transaction (the outermost one
 * flushes and applies).
 *
 * No slow I/O inside the block: it holds the single writer. The flush+commit at the end
 * is the only awaited I/O.
 */
suspend fun <R> transact(block: suspend () -> R): R {
    currentTxnScope.get()?.let {
        return block() // join the ambient transaction; outermost flushes and applies
    }
    val scope = TxnScope()
    val ret = serverState.withMutationLock {
        withContext(currentTxnScope.asContextElement(scope)) {
            val r = block()
            scope.close() // anything staged past this point is a leaked child coroutine
            // The abstract transaction (writer lock + staged scope) and the database
            // transaction are separate concerns: the Mongo session opens lazily, only
            // once something actually staged a database write. A transient-only
            // transaction — cell updates — never touches Mongo.
            val dbOps = scope.staged.filter { it.needsDb }
            if (dbOps.isNotEmpty()) {
                serverState.client.startSession().use { sess ->
                    sess.startTransaction()
                    dbOps.forEach { it.flush(sess) }
                    sess.commitTransactionAndAwait()
                }
            }
            r
        }
    }
    try {
        scope.staged.forEach {
            // The commit already happened — there is no rollback. A throwing apply
            // (typically a fold listener or extension onChange) must not skip the
            // remaining applies, or RAM diverges from the committed writes wholesale;
            // log it and keep the divergence to the one broken consumer.
            try { it.apply() }
            catch (e: Throwable) { storeLog.error("Post-commit apply failed; state may be partially stale", e) }
        }
    } finally {
        // Advance the transaction clock strictly after apply+emit: one bump covers the
        // store applies and everything the synchronous fold dispatch updated, so view
        // validation (§3.5) observes the whole transaction as one tick. In a `finally`
        // so a failed apply cannot leave views cutting off against a stale serial.
        TxClock.bump()
    }
    return ret
}

/**
 * Run [body] with the ambient [TxnScope], opening a fresh transaction when there is none —
 * so the body always executes under the writer lock *and* has the scope to stage into.
 * This is the store-internal primitive behind every write method: it makes store ops work
 * both standalone (`archive.put(comment)`) and inside an explicit `transact { }` block.
 * Callers that don't need the scope use [transact], which deliberately doesn't expose it.
 */
suspend fun withTxnScope(body: suspend (TxnScope) -> Unit) {
    val ambient = currentTxnScope.get()
    if (ambient != null) body(ambient)
    else transact { body(currentTxnScope.get()!!) }
}
