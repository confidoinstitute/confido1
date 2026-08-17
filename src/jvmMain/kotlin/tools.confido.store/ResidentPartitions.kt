package tools.confido.store

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tools.confido.application.singleThreadContext
import tools.confido.refs.HasId

/**
 * Subscription-scoped residency for [Archive] partitions — the tier-2 residency shape
 * (PERSISTENCE_SYNC_REDESIGN.md §3.1 amendment): a partition is loaded into memory
 * while at least one subscription pins it (plus a grace period covering page
 * refreshes/reconnects) and maintained incrementally from the archive's synchronous
 * change dispatch, so topic payloads over it are plain [View] compositions — no
 * per-subscriber fetches, and full frame consistency with resident folds (a comment
 * and its count change in the same transaction span, hence the same frame).
 *
 * This deliberately does NOT make the entity resident in the §3.1 sense: memory here
 * is bounded by (actively subscribed partitions × partition size) — connected UI
 * surfaces — not by activity × time, and cold access stays suspend-only on the
 * archive. The same shape is the intended future path for loosening strict residency
 * of workspace-structure entities (questions by room), where a topic subscription is
 * precisely the signal that an entity is hot.
 *
 * Concurrency: everything runs on the single server thread ([singleThreadContext] —
 * acquire/release from the subscribe/unsubscribe paths, change dispatch inside the
 * transaction span). The one suspension point is the initial fetch; the load race is
 * closed by buffering — changes (and bulk cache mutations) arriving mid-load are
 * recorded as map transforms and replayed onto the fetched result, which is sound
 * because keyed put/remove replay is idempotent against a fetch that already saw the
 * change.
 */
abstract class ResidentPartitions<E : HasId, P>(
    source: ChangeSource<E>,
    private val partitionOf: (E) -> P,
    private val graceMillis: Long = 60_000,
) {
    /** Load one partition (the archive query). A member function rather than a
     *  constructor lambda on purpose: a *suspend lambda* whose parameter type is a
     *  generic bounded by a value class ([tools.confido.refs.Ref]) miscompiles — the
     *  coroutine spilling unboxes the parameter inconsistently and resumes with null
     *  (hit for real); member suspend functions use the ordinary boxed path. */
    protected abstract suspend fun fetch(partition: P): List<E>

    private val scope = CoroutineScope(singleThreadContext + SupervisorJob())
    private val entries = HashMap<P, Entry>()

    /** Diagnostics: partition loads performed (exposed via /diag/topic_computes). */
    var loads = 0L
        private set

    internal inner class Entry(val partition: P) {
        val cell = TransientCell(persistentMapOf<String, E>())
        var loaded = false
        /** Transforms buffered while the initial fetch is in flight. */
        val pendingDuringLoad = mutableListOf<(PersistentMap<String, E>) -> PersistentMap<String, E>>()
        var refs = 0
        var dropJob: Job? = null
        var loader: Deferred<Unit>? = null
    }

    /**
     * A pinned partition: [view] is valid until [close]. Acquired by a topic's build,
     * released via the subscription's resource hook ([SubscriptionResources]).
     */
    inner class Pin internal constructor(private val entry: Entry) {
        val view: View<PersistentMap<String, E>> = entry.cell.state.asView()
        private var closed = false
        fun close() {
            if (closed) return
            closed = true
            release(entry)
        }
    }

    init {
        source.onChange { change -> mutate(entries[partitionOf(change.entity)] ?: return@onChange) {
            when (change) {
                is Change.Added, is Change.Updated -> it.put(change.entity.id, change.entity)
                is Change.Removed -> it.remove(change.entity.id)
            }
        } }
    }

    /**
     * Apply [f] to every cached entity across live partitions — for rare bulk DB
     * updates that deliberately emit no per-entity changes (stripUserLikes). Runs in
     * the staged write's apply step, i.e. the same transaction span as change dispatch.
     */
    fun mutateAll(f: (E) -> E) {
        entries.values.forEach { e -> mutate(e) { map ->
            var m = map
            map.forEach { (id, v) -> val nv = f(v); if (nv !== v) m = m.put(id, nv) }
            m
        } }
    }

    private fun mutate(e: Entry, f: (PersistentMap<String, E>) -> PersistentMap<String, E>) {
        // mutate runs from the archive's synchronous change dispatch — a transaction's
        // post-commit apply phase, where the ambient scope is gone, so update applies
        // immediately as part of that tick.
        if (e.loaded) e.cell.update(f)
        else e.pendingDuringLoad += f
    }

    /** Pin [partition] resident; suspends on the initial fetch (first pinner loads,
     *  concurrent pinners await the same load). Throws if the load fails — the entry
     *  is discarded so the next acquire retries fresh. */
    suspend fun acquire(partition: P): Pin {
        val e = entries.getOrPut(partition) { Entry(partition) }
        e.dropJob?.cancel(); e.dropJob = null
        e.refs++
        try {
            ensureLoaded(e)
        } catch (ex: Throwable) {
            e.refs--
            if (e.refs == 0) scheduleDrop(e)
            throw ex
        }
        return Pin(e)
    }

    private suspend fun ensureLoaded(e: Entry) {
        if (e.loaded) return
        // The loader runs detached in [scope]: an awaiting subscriber's cancellation
        // must not abort the load other pinners are waiting on.
        val loader = e.loader ?: scope.async {
            loads++
            val fetched = fetch(e.partition)
            var map = persistentMapOf<String, E>()
            fetched.forEach { map = map.put(it.id, it) }
            e.pendingDuringLoad.forEach { map = it(map) }
            e.pendingDuringLoad.clear()
            e.cell.set(map)
            e.loaded = true
        }.also { e.loader = it }
        try {
            loader.await()
        } catch (ex: Throwable) {
            // Only a *failed load* discards the entry; an awaiter's own cancellation
            // leaves the in-flight load for the remaining pinners.
            if (loader.isCompleted && !e.loaded && entries[e.partition] === e)
                entries.remove(e.partition)
            throw ex
        }
    }

    private fun release(e: Entry) {
        e.refs--
        if (e.refs == 0) scheduleDrop(e)
    }

    private fun scheduleDrop(e: Entry) {
        if (entries[e.partition] !== e) return
        e.dropJob = scope.launch {
            delay(graceMillis)
            if (e.refs == 0 && entries[e.partition] === e) entries.remove(e.partition)
        }
    }
}
