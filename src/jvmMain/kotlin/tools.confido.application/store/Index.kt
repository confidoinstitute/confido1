package tools.confido.store

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tools.confido.refs.HasId
import tools.confido.refs.eqid

/**
 * Unique secondary index over a change stream: maps a derived key to the single entity
 * currently carrying it (`byToken`, `byEmail` pattern). Entities whose [keyOf] returns
 * null are not indexed. Maintained incrementally from [ChangeSource.onChange].
 */
class UniqueIndex<E : HasId, K>(source: ChangeSource<E>, val keyOf: (E) -> K?) {
    private val map = mutableMapOf<K, E>()

    init {
        source.onChange { change ->
            when (change) {
                is Change.Added -> keyOf(change.entity)?.let { map[it] = change.entity }
                is Change.Updated -> {
                    val oldKey = keyOf(change.old)
                    val newKey = keyOf(change.new)
                    if (oldKey != newKey) oldKey?.let { map.remove(it) }
                    newKey?.let { map[it] = change.new }
                }
                is Change.Removed -> keyOf(change.entity)?.let { key ->
                    if (map[key]?.let { it eqid change.entity } == true) map.remove(key)
                }
            }
        }
    }

    operator fun get(key: K?): E? = key?.let { map[it] }
}

/**
 * Reference-counted secondary index: for each key, the set of values currently referenced
 * by at least one entity. (Moved from ServerState.kt; the manual add/remove API remains for
 * the legacy managers until their conversion — new stores maintain instances via onChange.)
 */
class RefCountIndex<K, V> {
    val index: MutableMap<K, MutableMap<V, Int>> = mutableMapOf()
    fun add(k: K, v: V, count: Int = 1) { index.getOrPut(k){ mutableMapOf() }.merge(v, count, Int::plus) }
    fun remove(k: K, v: V) {
        val m = index[k] ?: return
        val n = (m[v] ?: return) - 1
        if (n <= 0) m.remove(v) else m[v] = n
        if (m.isEmpty()) index.remove(k)
    }
    operator fun get(k: K): Set<V> = index[k]?.keys ?: emptySet()
    inline fun forEach(body: (K, Set<V>) -> Unit) = index.forEach { (k, m) -> body(k, m.keys) }
}

/**
 * Per-key counter maintained as an incremental fold over a change stream (the
 * `totalPredictions` pattern: boot-initialized from one aggregate via [set], then
 * incremented/decremented on changes). Zero-count keys are removed.
 */
class CountView<K> {
    private val _state = MutableStateFlow<PersistentMap<K, Int>>(persistentMapOf())
    /** Snapshot flow of the counts; topic derivations subscribe here. */
    val state: StateFlow<PersistentMap<K, Int>> get() = _state
    private var counts by _state::value
    val asMap: Map<K, Int> get() = counts
    fun set(key: K, count: Int) { counts = if (count > 0) counts.put(key, count) else counts.remove(key) }
    fun increment(key: K, by: Int = 1) {
        val n = (counts[key] ?: 0) + by
        counts = if (n > 0) counts.put(key, n) else counts.remove(key)
    }
    operator fun get(key: K): Int = counts[key] ?: 0
}
