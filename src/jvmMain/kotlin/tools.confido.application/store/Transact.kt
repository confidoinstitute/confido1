package tools.confido.store

import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.coroutines.withContext
import tools.confido.state.TransactionContextElement
import tools.confido.state.serverState
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * A store write staged inside a [transact] block. Split into the database write (inside
 * the Mongo transaction) and the in-memory application + change emission (strictly after
 * a successful commit) — so a failed commit leaves RAM untouched.
 */
interface StagedWrite {
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
 * global mutation lock inside [serverState.withTransaction]), so reads inside the block
 * see the latest committed state and it cannot change before the block commits.
 *
 * Order of operations: run block (store ops stage themselves; legacy manager ops execute
 * as today, sharing the same Mongo session) → flush staged writes → Mongo commit → apply
 * staged writes to memory and emit changes.
 *
 * Nesting: a transact inside a transact joins the ambient transaction (the outermost one
 * flushes and applies). A transact inside a *legacy* `withTransaction` fails loudly
 * ("Nested transactions are not supported") — convert that call site to transact instead;
 * legacy manager ops keep working unchanged inside it.
 *
 * No slow I/O inside the block: it holds the single writer. The flush+commit at the end
 * is the only awaited I/O.
 */
suspend fun <R> transact(block: suspend () -> R): R {
    coroutineContext[TxnContextElement]?.let {
        return block() // join the ambient transaction; outermost flushes and applies
    }
    val scope = TxnScope()
    val ret = serverState.withTransaction {
        withContext(TxnContextElement(scope)) {
            val r = block()
            val sess = coroutineContext[TransactionContextElement]?.sess
            scope.staged.forEach { it.flush(sess) }
            r
        }
    }
    scope.staged.forEach { it.apply() }
    return ret
}

/**
 * Run [body] with the ambient [TxnScope] if one exists, else in a fresh single-op
 * transaction. This is what lets store write methods work both standalone
 * (`archive.put(comment)`) and inside an explicit `transact { }` block.
 */
suspend fun inTransaction(body: suspend (TxnScope) -> Unit) {
    val ambient = coroutineContext[TxnContextElement]?.scope
    if (ambient != null) body(ambient)
    else transact { body(coroutineContext[TxnContextElement]!!.scope) }
}
