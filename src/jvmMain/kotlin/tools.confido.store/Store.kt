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
    // the writer lock — withTxnScope guarantees both (it opens a transaction when there
    // is no ambient one), so callers can freely compose these inside transact {}.
    //
    // Writes go through a per-(store, id) net-effect slot in the transaction's write
    // set (TxnScope.slot): write combinators see each other's staged results — a
    // second modify of one entity composes with the first, insert-then-modify works —
    // while plain reads (get/asMap/state) keep the committed-snapshot contract. The
    // slot flushes and emits the transaction's NET effect per entity: one Updated
    // spanning committed-old to final-new, an insert cancelled by a delete emits
    // nothing. (Delete hooks still run eagerly at the delete() call.)

    private inner class EntitySlot(val committedOld: E?) : StagedWrite {
        /** The entity's effective value within this transaction; null = deleted. */
        var current: E? = committedOld

        // Evaluated after the block: a net-nothing slot needs no database write.
        override val needsDb get() = !(committedOld == null && current == null)

        override suspend fun flush(sess: ClientSession?) {
            val cur = current
            when {
                committedOld == null && cur != null ->
                    if (sess == null) coll.insertOne(cur) else coll.insertOne(sess, cur)
                committedOld != null && cur != null ->
                    if (sess == null) coll.replaceOneById(cur.id, cur) else coll.replaceOneById(sess, cur.id, cur)
                committedOld != null && cur == null ->
                    if (sess == null) coll.deleteOneById(committedOld.id) else coll.deleteOneById(sess, committedOld.id)
                else -> {} // inserted and deleted within one transaction: net nothing
            }
        }

        override fun apply() {
            val cur = current
            when {
                committedOld == null && cur != null -> {
                    _state.value = _state.value.put(cur.id, cur)
                    emitChange(Change.Added(cur))
                }
                committedOld != null && cur != null -> {
                    _state.value = _state.value.put(cur.id, cur)
                    emitChange(Change.Updated(committedOld, cur))
                }
                committedOld != null && cur == null -> {
                    _state.value = _state.value.remove(committedOld.id)
                    emitChange(Change.Removed(committedOld))
                }
                else -> {}
            }
        }
    }

    private fun slotFor(scope: TxnScope, id: String): EntitySlot =
        scope.slot(this to id) { EntitySlot(get(id)) }

    /** Insert a new entity; assigns an id if empty. Throws [DuplicateIdException]. */
    suspend fun insert(entity: E): E {
        var stored = if (entity.id.isEmpty()) withId(entity, generateId()) else entity
        withTxnScope { scope ->
            val slot = slotFor(scope, stored.id)
            if (slot.current != null) throw DuplicateIdException()
            stored = applyBeforeSave(null, stored)
            slot.current = stored
        }
        return stored
    }

    /** Insert or replace by id (the old replaceEntity(upsert = true)). */
    suspend fun put(entity: E): E {
        var stored = if (entity.id.isEmpty()) withId(entity, generateId()) else entity
        withTxnScope { scope ->
            val slot = slotFor(scope, stored.id)
            stored = applyBeforeSave(slot.current, stored)
            slot.current = stored
        }
        return stored
    }

    suspend fun delete(ref: Ref<E>, ignoreNonexistent: Boolean = false): E? {
        var old: E? = null
        withTxnScope { scope ->
            val slot = slotFor(scope, ref.id)
            val removed = slot.current
                ?: if (ignoreNonexistent) return@withTxnScope else throw NoSuchElementException()
            old = removed
            beforeDeleteHooks.forEach { it(removed) }
            slot.current = null
        }
        return old
    }

    suspend fun delete(id: String, ignoreNonexistent: Boolean = false): E? =
        delete(Ref(id), ignoreNonexistent)

    /** Atomic read-modify-write of one entity, in the ambient transaction. [f] runs
     *  eagerly, once, against the entity's effective (write-set) value, and the return
     *  value is exact — so consecutive modifies of one entity compose. */
    suspend fun modify(ref: Ref<E>, f: (E) -> E): E {
        lateinit var new: E
        withTxnScope { scope ->
            val slot = slotFor(scope, ref.id)
            val old = slot.current ?: throw NoSuchElementException()
            new = applyBeforeSave(old, f(old))
            check(new.id == old.id)
            slot.current = new
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
            .forEach { stale ->
                // Each delete suspends, so the snapshot goes stale mid-sweep: the entity
                // may have been renewed under the same id (e.g. a session the user just
                // logged back into) or deleted concurrently. Re-check inside the deleting
                // transaction and tolerate a concurrent disappearance.
                transact {
                    val cur = get(stale.id)
                    if (cur != null && now > cur.expiryTime + tolerance)
                        delete(Ref<E>(cur.id), ignoreNonexistent = true)
                }
            }
    }
}
