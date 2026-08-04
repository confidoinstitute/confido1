package tools.confido.store

import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import org.litote.kmongo.coroutine.CoroutineCollection
import tools.confido.refs.Entity
import tools.confido.refs.HasExpiration
import tools.confido.refs.HasId
import tools.confido.refs.Ref
import tools.confido.utils.generateId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    // Extension points, carried over from the EntityManager hooks (same names, so
    // converting a hook registration only changes the receiver). beforeEntitySave
    // transforms/validates inside the transaction before the write is staged (a throw
    // aborts the whole transaction with RAM untouched); beforeEntityDelete runs inside
    // the deleting transaction and typically stages cascade writes into it.
    private val beforeSaveHooks = mutableListOf<(E?, E) -> E>()
    private val beforeDeleteHooks = mutableListOf<suspend (E) -> Unit>()
    fun beforeEntitySave(cb: (old: E?, new: E) -> E) { beforeSaveHooks.add(cb) }
    fun beforeEntityDelete(cb: suspend (E) -> Unit) { beforeDeleteHooks.add(cb) }
    protected fun applyBeforeSave(old: E?, new: E): E = beforeSaveHooks.fold(new) { acc, cb -> cb(old, acc) }

    override suspend fun load() {
        coll.find().toFlow().collect { e ->
            _state.value = _state.value.put(e.id, e)
            emitChange(Change.Added(e)) // boot replays through the same folds as live changes
        }
    }

    // The read (duplicate check / current value) and the staging must both happen under
    // the writer lock, so each mutation wraps itself in transact — which joins an ambient
    // transaction when there is one, so callers can freely compose these in transact {}.

    private fun stagePut(scope: TxnScope, old: E?, new: E) {
        scope.stage(object : StagedWrite {
            override suspend fun flush(sess: ClientSession?) {
                if (old == null) {
                    if (sess == null) coll.insertOne(new) else coll.insertOne(sess, new)
                } else {
                    if (sess == null) coll.replaceOneById(new.id, new) else coll.replaceOneById(sess, new.id, new)
                }
            }
            override fun apply() {
                _state.value = _state.value.put(new.id, new)
                emitChange(if (old == null) Change.Added(new) else Change.Updated(old, new))
            }
        })
    }

    /** Insert a new entity; assigns an id if empty. Throws [DuplicateIdException]. */
    suspend fun insert(entity: E): E {
        var stored = if (entity.id.isEmpty()) withId(entity, generateId()) else entity
        transact {
            if (get(stored.id) != null) throw DuplicateIdException()
            stored = applyBeforeSave(null, stored)
            inTransaction { stagePut(it, null, stored) }
        }
        return stored
    }

    /** Insert or replace by id (the old replaceEntity(upsert = true)). */
    suspend fun put(entity: E): E {
        var stored = if (entity.id.isEmpty()) withId(entity, generateId()) else entity
        transact {
            val old = get(stored.id)
            stored = applyBeforeSave(old, stored)
            inTransaction { stagePut(it, old, stored) }
        }
        return stored
    }

    suspend fun delete(ref: Ref<E>, ignoreNonexistent: Boolean = false): E? {
        var old: E? = null
        transact {
            old = get(ref.id) ?: if (ignoreNonexistent) return@transact else throw NoSuchElementException()
            val removed = old!!
            beforeDeleteHooks.forEach { it(removed) }
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

    suspend fun delete(id: String, ignoreNonexistent: Boolean = false): E? =
        delete(Ref(id), ignoreNonexistent)

    /** Atomic read-modify-write of one entity, in the ambient transaction. */
    suspend fun modify(ref: Ref<E>, f: (E) -> E): E {
        lateinit var new: E
        transact {
            val old = get(ref.id) ?: throw NoSuchElementException()
            new = applyBeforeSave(old, f(old))
            check(new.id == old.id)
            inTransaction { stagePut(it, old, new) }
        }
        return new
    }

    suspend fun modify(id: String, f: (E) -> E): E = modify(Ref(id), f)
}

/** Stores whose entities expire; swept periodically by the background task in Server.kt. */
interface ExpiringStore {
    suspend fun cleanupExpired()
}

open class ExpiringMongoStore<E>(
    coll: CoroutineCollection<E>,
    withId: (E, String) -> E,
    private val tolerance: Duration = 0.seconds,
) : MongoStore<E>(coll, withId), ExpiringStore where E : Entity, E : HasExpiration {
    override suspend fun cleanupExpired() {
        val now = Clock.System.now()
        asMap.values.filter { now > it.expiryTime + tolerance }
            .forEach { delete(Ref<E>(it.id)) }
    }
}
