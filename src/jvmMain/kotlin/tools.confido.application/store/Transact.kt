package tools.confido.store

import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.coroutines.withContext
import org.litote.kmongo.coroutine.commitTransactionAndAwait
import org.slf4j.LoggerFactory
import tools.confido.state.serverState
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

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
    fun stage(op: StagedWrite) { staged.add(op) }
}

class TxnContextElement(val scope: TxnScope) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TxnContextElement>
}

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
    coroutineContext[TxnContextElement]?.let {
        return block() // join the ambient transaction; outermost flushes and applies
    }
    val scope = TxnScope()
    val ret = serverState.withMutationLock {
        withContext(TxnContextElement(scope)) {
            val r = block()
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
    val ambient = coroutineContext[TxnContextElement]?.scope
    if (ambient != null) body(ambient)
    else transact { body(coroutineContext[TxnContextElement]!!.scope) }
}

/**
 * Stage a memory-only [apply] into the ambient transaction, if one is open; returns
 * false (having done nothing) otherwise. The transactional half of [TransientCell]'s
 * write path: staged applies run strictly after commit, in stage order, so cell
 * updates observe the same emit-after-commit discipline as store writes and a
 * transaction that stages only these never opens a Mongo session.
 */
suspend fun stageIfInTransaction(apply: () -> Unit): Boolean {
    val scope = coroutineContext[TxnContextElement]?.scope ?: return false
    scope.stage(object : StagedWrite {
        override val needsDb get() = false
        override suspend fun flush(sess: ClientSession?) {}
        override fun apply() = apply()
    })
    return true
}
