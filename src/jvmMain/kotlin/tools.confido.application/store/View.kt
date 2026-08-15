package tools.confido.store

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/**
 * The global transaction clock (PERSISTENCE_SYNC_REDESIGN.md §3.5): a monotone serial
 * advanced by every source-state mutation — [transact] bumps it once after apply+emit
 * (covering store applies and all synchronous fold dispatch), and [TransientCell]
 * setters bump it for out-of-transaction transient state. Under the single-writer
 * single-thread model no synchronization is needed, and all bumps belonging to one
 * mutation happen within one non-suspending span, so view validation only ever
 * observes quiescent states. Multiple bumps per logical transaction are harmless:
 * validation needs monotonicity, not one-tick-per-transaction.
 */
object TxClock {
    var serial: Long = 1L
        private set
    fun bump(): Long = ++serial
}

/** Identity-then-structural inequality: cheap for persistent structures. */
private fun neq(a: Any?, b: Any?) = a !== b && a != b

/**
 * A read-only reactive view over server state — the §3.1 View shape of
 * PERSISTENCE_SYNC_REDESIGN.md, formalized: a cheap, non-suspending, always-current
 * [value] plus a wake signal [changes], now with §3.5 versioned memoization.
 *
 * Consistency contract: all state mutation happens in non-suspending apply/fold code on
 * the single server thread, so any set of [value] reads performed without suspending in
 * between observes a transactionally consistent snapshot. Derivation transforms
 * ([map]/[combineViews]) must therefore be non-suspending. This is what makes composed
 * views immune to the classic Flow.combine glitch (a transform running on fresh A with a
 * stale cached B after a multi-store transaction): views never cache *inputs*, they
 * re-read all inputs at computation time, and computations only ever run between
 * transactions.
 *
 * Versioning: [validate] brings the view's cache up to date and returns the transaction
 * serial at which the value last *actually changed*. Consumers that computed at serial
 * `c` skip recomputation when `validate() <= c` (input cutoff); a recompute that yields
 * an equal value keeps the old change stamp so downstream consumers cut off too (early
 * cutoff). The default implementation is conservative — "assume changed every
 * transaction" — so ad-hoc View implementations stay sound and the combinators opt into
 * real memoization via [MemoView].
 *
 * [changes] may over-signal (fire without the value actually differing); it schedules
 * *when* consumers look, while [validate] decides *what* recomputes. Consumers
 * deduplicate at the flow boundary ([asFlow] applies distinctUntilChanged).
 */
interface View<out T> {
    val value: T
    val changes: Flow<*>
    fun validate(): Long = TxClock.serial
}

/**
 * Base for memoized derived views (§3.5): caches the computed value with two stamps —
 * `computedAt` (serial at which the cache was last known valid) and the change stamp
 * returned by [validate]. [validateSources] validates the inputs and returns the max
 * serial at which any of them last changed; [compute] recomputes from current input
 * values (may throw, e.g. a gate's ResponseError — the memo state is left unstamped and
 * the next validate retries). Reads and validation are non-suspending; under the single
 * server thread a validation pass is atomic.
 */
abstract class MemoView<T> : View<T> {
    private var computedAt = -1L
    private var changedAt = -1L
    private var cached: T? = null
    private var hasValue = false

    protected abstract fun validateSources(): Long
    protected abstract fun compute(): T

    final override fun validate(): Long {
        val now = TxClock.serial
        if (computedAt == now) return changedAt
        val srcChanged = validateSources()
        if (hasValue && srcChanged <= computedAt) {
            computedAt = now  // inputs unchanged since our compute: cache still valid
            return changedAt
        }
        val v = compute()
        if (!hasValue || neq(v, cached)) {
            cached = v
            hasValue = true
            changedAt = now  // else: recomputed but equal — keep old stamp (early cutoff)
        }
        computedAt = now
        return changedAt
    }

    final override val value: T
        get() {
            validate()
            @Suppress("UNCHECKED_CAST")
            return cached as T
        }
}

/** A constant view; [changes] never fires and [validate] reports "never changed". */
fun <T> viewOf(value: T): View<T> = object : View<T> {
    override val value = value
    override val changes get() = emptyFlow<Any?>()
    override fun validate(): Long = 0L
}

/**
 * Adapt a [StateFlow] (e.g. a store's `state`) to a [View] — a versioned leaf: change
 * detection is identity-then-structural against the last observed value, stamped at
 * first observation after the change (conservative for consumers that validated in
 * between, exact thereafter).
 */
fun <T> StateFlow<T>.asView(): View<T> = object : View<T> {
    private var seen: T? = null
    private var hasSeen = false
    private var computedAt = -1L
    private var changedAt = -1L

    override fun validate(): Long {
        val now = TxClock.serial
        if (computedAt == now) return changedAt
        val v = this@asView.value
        if (!hasSeen || neq(v, seen)) {
            seen = v
            hasSeen = true
            changedAt = now
        }
        computedAt = now
        return changedAt
    }

    override val value get() = this@asView.value
    override val changes get() = this@asView.drop(1)  // skip the replayed current value
}

/** Memoized projection with early cutoff; [f] must not suspend. */
fun <T, R> View<T>.map(f: (T) -> R): View<R> = object : MemoView<R>() {
    override fun validateSources() = this@map.validate()
    override fun compute() = f(this@map.value)
    override val changes get() = this@map.changes
}

/** Keyed cell of a map view — the "family of keyed flows", derivation-style. */
fun <K, V> View<out Map<K, V>>.at(key: K): View<V?> = map { it[key] }

/**
 * Follow a reference/selection: the view of `f(value)`, re-selected when the outer
 * value changes (the selected inner view is cached between outer changes, so its own
 * memoization is preserved). Wakes on outer changes and on changes of the currently
 * selected inner view.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R> View<T>.flatMap(f: (T) -> View<R>): View<R> = object : MemoView<R>() {
    private var inner: View<R>? = null
    private var selectedAt = -1L

    override fun validateSources(): Long {
        val outerChanged = this@flatMap.validate()
        if (inner == null || outerChanged > selectedAt) {
            inner = f(this@flatMap.value)
            selectedAt = TxClock.serial
            // Fresh selection: its value may differ even if the inner view itself
            // reports an old change stamp — force a recompute (equality cutoff in
            // MemoView still applies to the result).
            return TxClock.serial
        }
        return inner!!.validate()
    }

    override fun compute(): R = inner!!.value

    override val changes get() = this@flatMap.changes
        .onStart { emit(Unit) }
        .flatMapLatest { f(this@flatMap.value).changes.onStart { emit(Unit) } }
        .drop(1)  // drop the subscription-time inner onStart; asFlow emits initially anyway
}

/**
 * Untyped n-ary combine, for wake-signal-only composition (e.g. a transform that reads
 * broad global state, like the transitional /state censor): the value is the list of
 * input values, typically ignored by a following [map]. [validate] is the max over the
 * inputs; no own caching (building the list is cheap, downstream memoization cuts off).
 */
fun combineViews(vararg views: View<*>): View<List<*>> = object : View<List<*>> {
    override val value get() = views.map { it.value }
    override val changes get() = merge(*views.map { it.changes }.toTypedArray())
    override fun validate(): Long = views.maxOf { it.validate() }
}

/** Glitch-free memoized combine: [f] re-reads all values at computation time. Must not suspend. */
fun <A, B, R> combineViews(a: View<A>, b: View<B>, f: (A, B) -> R): View<R> =
    object : MemoView<R>() {
        override fun validateSources() = maxOf(a.validate(), b.validate())
        override fun compute() = f(a.value, b.value)
        override val changes get() = merge(a.changes, b.changes)
    }

fun <A, B, C, R> combineViews(a: View<A>, b: View<B>, c: View<C>, f: (A, B, C) -> R): View<R> =
    object : MemoView<R>() {
        override fun validateSources() = maxOf(a.validate(), b.validate(), c.validate())
        override fun compute() = f(a.value, b.value, c.value)
        override val changes get() = merge(a.changes, b.changes, c.changes)
    }

fun <A, B, C, D, R> combineViews(a: View<A>, b: View<B>, c: View<C>, d: View<D>, f: (A, B, C, D) -> R): View<R> =
    object : MemoView<R>() {
        override fun validateSources() = maxOf(a.validate(), b.validate(), c.validate(), d.validate())
        override fun compute() = f(a.value, b.value, c.value, d.value)
        override val changes get() = merge(a.changes, b.changes, c.changes, d.changes)
    }

fun <A, B, C, D, E, R> combineViews(a: View<A>, b: View<B>, c: View<C>, d: View<D>, e: View<E>, f: (A, B, C, D, E) -> R): View<R> =
    object : MemoView<R>() {
        override fun validateSources() = maxOf(a.validate(), b.validate(), c.validate(), d.validate(), e.validate())
        override fun compute() = f(a.value, b.value, c.value, d.value, e.value)
        override val changes get() = merge(a.changes, b.changes, c.changes, d.changes, e.changes)
    }

/**
 * The subscription boundary: recompute on wake (conflated), emit the current value on
 * collect, deduplicate output. Each [value] read happens between suspensions, i.e. is a
 * consistent snapshot computation.
 */
fun <T> View<T>.asFlow(): Flow<T> =
    changes.conflate().map { value }.onStart { emit(value) }.distinctUntilChanged()

/**
 * Mutable transient server state as a versioned source cell (§3.5): the same
 * StateFlow surface the topic derivations already subscribe, plus a [TxClock] bump on
 * every actual change — required for out-of-transaction mutations (timer callbacks,
 * connection lifecycle), harmless extra bumps when set inside a transaction. Durability
 * is a property of the node: a [TransientCell] is simply a source with none.
 */
class TransientCell<T>(initial: T) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<T> get() = _state
    var value: T
        get() = _state.value
        set(v) {
            if (v === _state.value) return
            TxClock.bump()
            _state.value = v
        }
}
