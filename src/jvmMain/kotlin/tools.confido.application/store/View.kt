package tools.confido.store

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/**
 * A read-only reactive view over server state — the §3.1 View shape of
 * PERSISTENCE_SYNC_REDESIGN.md, formalized: a cheap, non-suspending, always-current
 * [value] plus a wake signal [changes].
 *
 * Consistency contract: all state mutation happens in non-suspending apply/fold code on
 * the single server thread, so any set of [value] reads performed without suspending in
 * between observes a transactionally consistent snapshot. Derivation transforms
 * ([map]/[combineViews]) must therefore be non-suspending. This is what makes composed
 * views immune to the classic Flow.combine glitch (a transform running on fresh A with a
 * stale cached B after a multi-store transaction): views never cache, they re-read all
 * inputs at computation time, and computations only ever run between transactions.
 *
 * [changes] may over-signal (fire without the value actually differing — e.g. a keyed
 * [at] cell wakes on any change of the parent map); consumers deduplicate at the flow
 * boundary ([asFlow] applies distinctUntilChanged).
 */
interface View<out T> {
    val value: T
    val changes: Flow<*>
}

/** A constant view; [changes] never fires. */
fun <T> viewOf(value: T): View<T> = object : View<T> {
    override val value = value
    override val changes get() = emptyFlow<Any?>()
}

/** Adapt a [StateFlow] (e.g. a store's `state`) to a [View]. */
fun <T> StateFlow<T>.asView(): View<T> = object : View<T> {
    override val value get() = this@asView.value
    override val changes get() = this@asView.drop(1)  // skip the replayed current value
}

/** Lazy projection: [f] runs on read, not on change. Must not suspend. */
fun <T, R> View<T>.map(f: (T) -> R): View<R> = object : View<R> {
    override val value get() = f(this@map.value)
    override val changes get() = this@map.changes
}

/** Keyed cell of a map view — the "family of keyed flows", derivation-style. */
fun <K, V> View<out Map<K, V>>.at(key: K): View<V?> = map { it[key] }

/**
 * Follow a reference/selection: the view of `f(value)`, re-selected on every outer
 * change. Wakes on outer changes and on changes of the currently selected inner view.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R> View<T>.flatMap(f: (T) -> View<R>): View<R> = object : View<R> {
    override val value get() = f(this@flatMap.value).value
    override val changes get() = this@flatMap.changes
        .onStart { emit(Unit) }
        .flatMapLatest { f(this@flatMap.value).changes.onStart { emit(Unit) } }
        .drop(1)  // drop the subscription-time inner onStart; asFlow emits initially anyway
}

/** Glitch-free combine: [f] re-reads all values at computation time. Must not suspend. */
fun <A, B, R> combineViews(a: View<A>, b: View<B>, f: (A, B) -> R): View<R> =
    object : View<R> {
        override val value get() = f(a.value, b.value)
        override val changes get() = merge(a.changes, b.changes)
    }

fun <A, B, C, R> combineViews(a: View<A>, b: View<B>, c: View<C>, f: (A, B, C) -> R): View<R> =
    object : View<R> {
        override val value get() = f(a.value, b.value, c.value)
        override val changes get() = merge(a.changes, b.changes, c.changes)
    }

fun <A, B, C, D, R> combineViews(a: View<A>, b: View<B>, c: View<C>, d: View<D>, f: (A, B, C, D) -> R): View<R> =
    object : View<R> {
        override val value get() = f(a.value, b.value, c.value, d.value)
        override val changes get() = merge(a.changes, b.changes, c.changes, d.changes)
    }

fun <A, B, C, D, E, R> combineViews(a: View<A>, b: View<B>, c: View<C>, d: View<D>, e: View<E>, f: (A, B, C, D, E) -> R): View<R> =
    object : View<R> {
        override val value get() = f(a.value, b.value, c.value, d.value, e.value)
        override val changes get() = merge(a.changes, b.changes, c.changes, d.changes, e.changes)
    }

/**
 * The subscription boundary: recompute on wake (conflated), emit the current value on
 * collect, deduplicate output. Each [value] read happens between suspensions, i.e. is a
 * consistent snapshot computation.
 */
fun <T> View<T>.asFlow(): Flow<T> =
    changes.conflate().map { value }.onStart { emit(value) }.distinctUntilChanged()
