package tools.confido.store

import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import tools.confido.rooms.Room
import tools.confido.question.Comment
import tools.confido.question.Question
import tools.confido.question.QuestionComment
import tools.confido.question.RoomComment
import tools.confido.refs.Ref
import tools.confido.refs.eqid
import tools.confido.refs.ref
import tools.confido.state.serverState
import tools.confido.utils.generateId
import tools.confido.users.User
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Comments as an Archive (PERSISTENCE_SYNC_REDESIGN.md §3.1): non-resident, suspend-only,
 * with partition fetch (`all(question)` / `all(room)`) as the core read primitive — plus
 * subscription-scoped hot partitions ([ResidentPartitions]) for the comment topics. Likes
 * are a `likes` array on the comment document itself, so they travel with the comment.
 *
 * One fold is maintained from the change stream and boot-initialized by an aggregate:
 * `commentCount` per question. (The former `commentAuthors` folds died with the
 * censor's referencedUsers — comment authors ship as embedded presentations, §5.3.)
 */
open class MongoCommentArchive<E : Comment, P : Ref<*>>(
    /** Typed by the sealed parent on purpose: the stored format carries a top-level
     *  `___type` discriminator (kept from the KMongo era, see ShortSerialNamesMigration),
     *  which the codec only writes when encoding through the polymorphic serializer.
     *  Each archive holds one subtype; [entityClass] checks the cast on read. */
    internal val coll: MongoCollection<Comment>,
    private val entityClass: KClass<E>,
    private val partitionProp: KProperty1<E, P>,
    private val userProp: KProperty1<E, Ref<User>>,
    private val withId: (E, String) -> E,
    private val withLikes: (E, List<Ref<User>>) -> E,
) : ChangeSource<E>(), Archive<E, P> {

    /** Subscription-scoped residency (tier-2, §3.1 amendment): comment topics pin
     *  their partition here, making their payloads plain [View] compositions — one
     *  load per hot partition instead of a fetch per subscriber per change, and
     *  frame consistency with the count folds (same synchronous change dispatch). */
    val partitions = object : ResidentPartitions<E, P>(this@MongoCommentArchive, { partitionProp.get(it) }) {
        override suspend fun fetch(partition: P) = all(partition)
    }

    @Suppress("UNCHECKED_CAST")
    private fun typed(c: Comment): E {
        check(entityClass.isInstance(c)) { "${coll.namespace.collectionName}: unexpected ${c::class.simpleName} ${c.id}" }
        return c as E
    }

    override suspend fun all(partition: P): List<E> =
        coll.find(partitionProp eq partition).toList().map(::typed)

    override suspend fun get(partition: P, id: String): E? =
        coll.find(idFilter(id)).firstOrNull()?.let(::typed)?.takeIf { partitionProp.get(it) == partition }

    suspend fun byAuthor(user: Ref<User>): List<E> =
        coll.find(userProp eq user).toList().map(::typed)

    /** Insert a new comment; assigns an id if empty. Returns the stored entity. */
    suspend fun insert(entity: E): E {
        val stored = if (entity.id.isEmpty()) withId(entity, generateId()) else entity
        withTxnScope {
            it.stage(Op(stored) { sess ->
                if (sess == null) coll.insertOne(stored) else coll.insertOne(sess, stored)
            }.emitting { Change.Added(stored) })
            // Same txn scope: a comment is masked activity, so it must raise the
            // attribution promise floor atomically with its own insert.
            onInserted(stored)
        }
        return stored
    }

    /** Called on genuine inserts only (never on loads); subclasses stamp promise floors. */
    protected open suspend fun onInserted(entity: E) {}

    /** Replace an existing comment (same id). */
    suspend fun update(old: E, new: E) {
        check(old.id == new.id)
        withTxnScope { it.stage(Op(new) { sess ->
            if (sess == null) coll.replaceOne(idFilter(new.id), new)
            else coll.replaceOne(sess, idFilter(new.id), new)
        }.emitting { Change.Updated(old, new) }) }
    }

    suspend fun delete(entity: E) {
        withTxnScope { it.stage(Op(entity) { sess ->
            if (sess == null) coll.deleteOne(idFilter(entity.id)) else coll.deleteOne(sess, idFilter(entity.id))
        }.emitting { Change.Removed(entity) }) }
    }

    /** Cascade helper: delete a whole partition, emitting Removed per comment. */
    suspend fun deletePartition(partition: P) {
        withTxnScope { scope ->
            scope.stage(object : StagedWrite {
                var removed: List<E> = emptyList()
                override suspend fun flush(sess: ClientSession?) {
                    removed = all(partition)
                    val filter = partitionProp eq partition
                    if (sess == null) coll.deleteMany(filter) else coll.deleteMany(sess, filter)
                }
                override fun apply() { removed.forEach { emitChange(Change.Removed(it)) } }
            })
        }
    }

    /** Remove [user] from every comment's likes (user deletion). Rare; whole-collection op. */
    suspend fun stripUserLikes(user: Ref<User>) {
        withTxnScope { scope ->
            scope.stage(object : StagedWrite {
                override suspend fun flush(sess: ClientSession?) {
                    val filter = Comment::likes contains user
                    val update = Updates.pull(Comment::likes.field, user.id)
                    if (sess == null) coll.updateMany(filter, update)
                    else coll.updateMany(sess, filter, update)
                }
                // Change emission is skipped (like-only updates affect no fold), so the
                // resident partitions are patched explicitly — the cache must not keep
                // a deleted user's likes alive.
                override fun apply() = partitions.mutateAll { c ->
                    if (user in c.likes) withLikes(c, c.likes - user) else c
                }
            })
        }
    }

    private inner class Op(val entity: E, val doFlush: suspend (ClientSession?) -> Unit) : StagedWrite {
        lateinit var change: () -> Change<E>
        fun emitting(change: () -> Change<E>): Op { this.change = change; return this }
        override suspend fun flush(sess: ClientSession?) { doFlush(sess) }
        override fun apply() { emitChange(change()) }
    }
}

object questionCommentArchive : MongoCommentArchive<QuestionComment, Ref<Question>>(
    serverState.database.getCollection<Comment>("questionComments"), QuestionComment::class,
    QuestionComment::question, QuestionComment::user,
    { e, id -> e.copy(id = id) },
    { e, likes -> e.copy(likes = likes) },
) {
    override suspend fun onInserted(entity: QuestionComment) =
        stampQuestionAttributionFloor(entity.question)
}

object roomCommentArchive : MongoCommentArchive<RoomComment, Ref<Room>>(
    serverState.database.getCollection<Comment>("roomComments"), RoomComment::class,
    RoomComment::room, RoomComment::user,
    { e, id -> e.copy(id = id) },
    { e, likes -> e.copy(likes = likes) },
) {
    override suspend fun onInserted(entity: RoomComment) =
        stampRoomAttributionFloor(entity.room)
}

object commentData : ManagedStore {
    // Verified through the sealed parent, like the archives read: the migration verify
    // pass runs on the batch's final state only, so the top-level `___type` is checked too.
    override val mongoCollections get() = listOf(questionCommentArchive.coll, roomCommentArchive.coll)

    val commentCount = CountView<Ref<Question>>()

    init {
        questionCommentArchive.onChange { change ->
            when (change) {
                is Change.Added -> commentCount.increment(change.entity.question)
                is Change.Removed -> commentCount.increment(change.entity.question, -1)
                is Change.Updated -> {}
            }
        }
    }

    override suspend fun initialize() {
        questionCommentArchive.coll.ensureIndex(QuestionComment::question)
        questionCommentArchive.coll.ensureIndex(QuestionComment::user)
        roomCommentArchive.coll.ensureIndex(RoomComment::room)
        roomCommentArchive.coll.ensureIndex(RoomComment::user)
    }

    override suspend fun load() {
        // Boot = one per-question count aggregate instead of loading every comment
        // into memory. Raw BSON: refs are stored as plain string ids.
        questionCommentArchive.coll.aggregate<org.bson.Document>(listOf(
            Aggregates.group("$" + QuestionComment::question.field, Accumulators.sum("cnt", 1))
        )).collect { doc ->
            val q = doc.getString("_id") ?: return@collect
            commentCount.increment(Ref(q), doc.getInteger("cnt"))
        }
    }
}

/** Register comment stores and their cascade hooks. Called once from server startup. */
fun registerCommentStores() {
    Stores.register(commentData)
    // Cascades: deleting a question deletes its comments (previously orphaned);
    // deleting a room deletes its room comments (previously a manual loop in the route).
    tools.confido.store.questionStore.beforeEntityDelete { q -> questionCommentArchive.deletePartition(q.ref) }
    tools.confido.store.roomStore.beforeEntityDelete { r -> roomCommentArchive.deletePartition(r.ref) }
}
