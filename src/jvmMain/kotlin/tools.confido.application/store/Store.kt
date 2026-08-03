package tools.confido.store

import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.litote.kmongo.coroutine.CoroutineCollection
import tools.confido.refs.Entity
import tools.confido.refs.HasId
import tools.confido.refs.Ref
import tools.confido.utils.generateId

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
    /** Resident stores by entity class, consulted by serverState.derefNonBlocking. */
    val resident = mutableMapOf<kotlin.reflect.KClass<*>, Store<*>>()
    fun register(store: ManagedStore) { all.add(store) }
}

class DuplicateIdException : Exception()

/**
 * Generic Mongo-backed resident [Store]: full collection load at boot (replayed through
 * the same change listeners that maintain indexes live), write-through staged mutations.
 */
open class MongoStore<E : Entity>(
    internal val coll: CoroutineCollection<E>,
    private val withId: (E, String) -> E,
) : ChangeSource<E>(), Store<E>, ManagedStore {
    private val _state = MutableStateFlow<PersistentMap<String, E>>(persistentMapOf())
    override val state: StateFlow<PersistentMap<String, E>> get() = _state
    override fun get(id: String): E? = _state.value[id]
    override val mongoCollections get() = listOf(coll)

    override suspend fun load() {
        coll.find().toFlow().collect { e ->
            _state.value = _state.value.put(e.id, e)
            emitChange(Change.Added(e)) // boot replays through the same folds as live changes
        }
    }

    // The read (duplicate check / current value) and the staging must both happen under
    // the writer lock, so each mutation wraps itself in transact — which joins an ambient
    // transaction when there is one, so callers can freely compose these in transact {}.

    /** Insert a new entity; assigns an id if empty. Throws [DuplicateIdException]. */
    suspend fun insert(entity: E): E {
        val stored = if (entity.id.isEmpty()) withId(entity, generateId()) else entity
        transact {
            if (get(stored.id) != null) throw DuplicateIdException()
            inTransaction { it.stage(object : StagedWrite {
                override suspend fun flush(sess: ClientSession?) {
                    if (sess == null) coll.insertOne(stored) else coll.insertOne(sess, stored)
                }
                override fun apply() {
                    _state.value = _state.value.put(stored.id, stored)
                    emitChange(Change.Added(stored))
                }
            }) }
        }
        return stored
    }

    suspend fun delete(ref: Ref<E>, ignoreNonexistent: Boolean = false): E? {
        var old: E? = null
        transact {
            old = get(ref.id) ?: if (ignoreNonexistent) return@transact else throw NoSuchElementException()
            val removed = old!!
            inTransaction { it.stage(object : StagedWrite {
                override suspend fun flush(sess: ClientSession?) {
                    if (sess == null) coll.deleteOneById(removed.id) else coll.deleteOneById(sess, removed.id)
                }
                override fun apply() {
                    _state.value = _state.value.remove(removed.id)
                    emitChange(Change.Removed(removed))
                }
            }) }
        }
        return old
    }

    /** Atomic read-modify-write of one entity, in the ambient transaction. */
    suspend fun modify(ref: Ref<E>, f: (E) -> E): E {
        lateinit var new: E
        transact {
            val old = get(ref.id) ?: throw NoSuchElementException()
            new = f(old)
            check(new.id == old.id)
            inTransaction { it.stage(object : StagedWrite {
                override suspend fun flush(sess: ClientSession?) {
                    if (sess == null) coll.replaceOneById(new.id, new) else coll.replaceOneById(sess, new.id, new)
                }
                override fun apply() {
                    _state.value = _state.value.put(new.id, new)
                    emitChange(Change.Updated(old, new))
                }
            }) }
        }
        return new
    }
}
