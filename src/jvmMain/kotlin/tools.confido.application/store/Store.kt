package tools.confido.store

import kotlinx.collections.immutable.PersistentMap
import kotlinx.coroutines.flow.StateFlow
import org.litote.kmongo.coroutine.CoroutineCollection
import tools.confido.refs.Entity
import tools.confido.refs.HasId

/**
 * A change to a stored entity, emitted strictly after the backing database commit.
 * Views (indexes, counts, aggregates) are maintained as incremental folds over these.
 */
sealed interface Change<out E : HasId> {
    /** The resulting entity ([Updated.new] for updates, the removed entity for removals). */
    val entity: E
    data class Added<E : HasId>(override val entity: E) : Change<E>
    data class Updated<E : HasId>(val old: E, val new: E) : Change<E> {
        override val entity get() = new
    }
    data class Removed<E : HasId>(override val entity: E) : Change<E>
}

/**
 * Emits [Change]s to synchronously invoked listeners.
 *
 * Listener dispatch is deliberately synchronous (not a coroutine Flow): the whole server
 * mutates and reads on one thread, and route handlers read derived views immediately after
 * a mutation — an asynchronously collected fold could be one step stale at that point.
 * Changes are emitted strictly after the database commit (see [transact]); boot-time loads
 * replay through the same listeners so views need no separate initialization path.
 * A Flow surface for async consumers (change-driven topics) is Phase 3 work.
 */
abstract class ChangeSource<E : HasId> {
    private val listeners = mutableListOf<(Change<E>) -> Unit>()
    fun onChange(listener: (Change<E>) -> Unit) { listeners.add(listener) }
    protected fun emitChange(change: Change<E>) { listeners.forEach { it(change) } }
}

/**
 * A fully-resident entity store: memory is the source of truth, the database is a
 * durability log read once at boot. Non-suspending [get]; consistent snapshots via [state].
 * Writes go through [transact]-staged operations defined on the implementation.
 */
interface Store<E : Entity> {
    val state: StateFlow<PersistentMap<String, E>>
    fun get(id: String): E?
    val asMap: Map<String, E> get() = state.value
}

/**
 * A non-resident, partitioned store: all access suspends, and the core read primitive is
 * fetching one whole partition (a question's comments, a room's comments). By construction
 * there is no non-suspending deref for archived entities.
 */
interface Archive<E : HasId, P> {
    suspend fun all(partition: P): List<E>
    suspend fun get(partition: P, id: String): E?
}

/**
 * Lifecycle contract tying a store into server boot and the migration verify pass.
 * Mirrors the EntityManager initialize/load split so old and new coexist during transition.
 */
interface ManagedStore {
    /** Typed collections owned by this store, for index creation and migration verification. */
    val mongoCollections: List<CoroutineCollection<*>>
    /** Create indexes etc. Runs before migrations. */
    suspend fun initialize() {}
    /** Load resident state / boot aggregates. Runs after migrations. */
    suspend fun load() {}
}

/** Registry of new-style stores, iterated by serverState boot alongside the legacy managers. */
object Stores {
    val all = mutableListOf<ManagedStore>()
    fun register(store: ManagedStore) { all.add(store) }
}
