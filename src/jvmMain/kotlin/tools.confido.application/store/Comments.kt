package tools.confido.store

import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.serialization.Serializable
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineCollection
import rooms.Room
import tools.confido.question.Question
import tools.confido.question.QuestionComment
import tools.confido.question.RoomComment
import tools.confido.refs.Ref
import tools.confido.refs.eqid
import tools.confido.refs.ref
import tools.confido.state.serverState
import tools.confido.utils.generateId
import users.User
import kotlin.reflect.KProperty1

/**
 * Comments as an Archive (PERSISTENCE_SYNC_REDESIGN.md §3.1): non-resident, suspend-only,
 * with partition fetch (`all(question)` / `all(room)`) as the core read primitive. Likes
 * are a `likes` array on the comment document itself, so they travel with the comment.
 *
 * Two folds are maintained from the change stream and boot-initialized by aggregates:
 * `commentCount` per question (the SentState field) and `commentAuthors` per partition
 * (feeding StateCensor's referencedUsers until the Phase 3 censor redesign removes it).
 */
open class MongoCommentArchive<E : tools.confido.question.Comment, P : Ref<*>>(
    internal val coll: CoroutineCollection<E>,
    private val partitionProp: KProperty1<E, P>,
    private val userProp: KProperty1<E, Ref<User>>,
    private val withId: (E, String) -> E,
) : ChangeSource<E>(), Archive<E, P> {

    override suspend fun all(partition: P): List<E> =
        coll.find(partitionProp eq partition).toList()

    override suspend fun get(partition: P, id: String): E? =
        coll.findOneById(id)?.takeIf { partitionProp.get(it) == partition }

    suspend fun byAuthor(user: Ref<User>): List<E> =
        coll.find(userProp eq user).toList()

    /** Insert a new comment; assigns an id if empty. Returns the stored entity. */
    suspend fun insert(entity: E): E {
        val stored = if (entity.id.isEmpty()) withId(entity, generateId()) else entity
        withTxnScope { it.stage(Op(stored) { sess ->
            if (sess == null) coll.insertOne(stored) else coll.insertOne(sess, stored)
        }.emitting { Change.Added(stored) }) }
        return stored
    }

    /** Replace an existing comment (same id). */
    suspend fun update(old: E, new: E) {
        check(old.id == new.id)
        withTxnScope { it.stage(Op(new) { sess ->
            if (sess == null) coll.replaceOneById(new.id, new)
            else coll.replaceOneById(sess, new.id, new)
        }.emitting { Change.Updated(old, new) }) }
    }

    suspend fun delete(entity: E) {
        withTxnScope { it.stage(Op(entity) { sess ->
            if (sess == null) coll.deleteOneById(entity.id) else coll.deleteOneById(sess, entity.id)
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
                    val filter = tools.confido.question.Comment::likes contains user
                    val update = pull(tools.confido.question.Comment::likes, user)
                    if (sess == null) coll.updateMany(filter, update)
                    else coll.updateMany(sess, filter, update)
                }
                // Likes live only in the documents; no resident state to update. (Change
                // emission is skipped: like-only updates affect no fold.)
                override fun apply() {}
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
    serverState.database.getCollection("questionComments"),
    QuestionComment::question, QuestionComment::user,
    { e, id -> e.copy(id = id) },
)

object roomCommentArchive : MongoCommentArchive<RoomComment, Ref<Room>>(
    serverState.database.getCollection("roomComments"),
    RoomComment::room, RoomComment::user,
    { e, id -> e.copy(id = id) },
)

object commentData : ManagedStore {
    override val mongoCollections get() = listOf(questionCommentArchive.coll, roomCommentArchive.coll)

    val commentCount = CountView<Ref<Question>>()
    val questionCommentAuthors = RefCountIndex<Ref<Question>, Ref<User>>()
    val roomCommentAuthors = RefCountIndex<Ref<Room>, Ref<User>>()

    init {
        questionCommentArchive.onChange { change ->
            when (change) {
                is Change.Added -> {
                    commentCount.increment(change.entity.question)
                    questionCommentAuthors.add(change.entity.question, change.entity.user)
                }
                is Change.Removed -> {
                    commentCount.increment(change.entity.question, -1)
                    questionCommentAuthors.remove(change.entity.question, change.entity.user)
                }
                is Change.Updated -> if (!(change.old.user eqid change.new.user)) {
                    questionCommentAuthors.remove(change.old.question, change.old.user)
                    questionCommentAuthors.add(change.new.question, change.new.user)
                }
            }
        }
        roomCommentArchive.onChange { change ->
            when (change) {
                is Change.Added -> roomCommentAuthors.add(change.entity.room, change.entity.user)
                is Change.Removed -> roomCommentAuthors.remove(change.entity.room, change.entity.user)
                is Change.Updated -> if (!(change.old.user eqid change.new.user)) {
                    roomCommentAuthors.remove(change.old.room, change.old.user)
                    roomCommentAuthors.add(change.new.room, change.new.user)
                }
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
        // Boot = one (partition, author) count aggregate per fold instead of loading
        // every comment into memory. Raw BSON: refs are stored as plain string ids.
        suspend fun authorCounts(coll: CoroutineCollection<*>, partitionField: String,
                                 collect: (String, String, Int) -> Unit) {
            coll.aggregate<org.bson.Document>(listOf(
                org.bson.Document("\$group", org.bson.Document("_id",
                    org.bson.Document("p", "\$$partitionField").append("u", "\$user"))
                    .append("cnt", org.bson.Document("\$sum", 1)))
            )).toFlow().collect { doc ->
                val key = doc.get("_id", org.bson.Document::class.java)
                val p = key.getString("p") ?: return@collect
                val u = key.getString("u") ?: return@collect
                collect(p, u, doc.getInteger("cnt"))
            }
        }
        authorCounts(questionCommentArchive.coll, "question") { q, u, cnt ->
            commentCount.increment(Ref(q), cnt)
            questionCommentAuthors.add(Ref(q), Ref(u), cnt)
        }
        authorCounts(roomCommentArchive.coll, "room") { r, u, cnt ->
            roomCommentAuthors.add(Ref(r), Ref(u), cnt)
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
