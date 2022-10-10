package tools.confido.state

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import rooms.Room
import tools.confido.distributions.*
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.question.QuestionComment
import tools.confido.question.RoomComment
import tools.confido.refs.*
import tools.confido.spaces.*
import tools.confido.utils.*
import users.User
import java.lang.RuntimeException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext


class DuplicateIdException : Exception() {}
object MutationLockedContextElement : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<MutationLockedContextElement>

}
class TransactionContextElement(val sess: ClientSession) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionContextElement>
}


class ServerGlobalState : GlobalState() {
    override val rooms:  MutableMap<String, Room> = mutableMapOf()
    override val questions:  MutableMap<String, Question> = mutableMapOf()
    override val users:  MutableMap<String, User> = mutableMapOf()

    val userPred : MutableMap<Ref<Question>, MutableMap<Ref<User>, Prediction>> = mutableMapOf()
    override val groupPred : MutableMap<Ref<Question>, Prediction?> = mutableMapOf()
    val questionComments : MutableMap<Ref<Question>, MutableMap<String, QuestionComment>> = mutableMapOf()
    val roomComments : MutableMap<Ref<Room>, MutableMap<String, RoomComment>> = mutableMapOf()

    // Now, for simplicity, serialize all mutations
    val mutationMutex = Mutex()

    val client = KMongo.createClient().coroutine
    val database = client.getDatabase(System.getenv("CONFIDO_DB_NAME") ?: "confido1")
    val questionsColl = database.getCollection<Question>("questions")
    val usersColl = database.getCollection<User>("users")
    val roomsColl = database.getCollection<Room>("rooms")
    val userPredColl = database.getCollection<Prediction>("userPred")
    val questionCommentsColl = database.getCollection<QuestionComment>("questionComments")
    val roomCommentsColl = database.getCollection<RoomComment>("questionComments")


    suspend fun <T: HasId> loadEntityMap(coll: CoroutineCollection<T>) : Map<String, T> =
        coll.find().toList().associateBy { entity -> entity.id }.toMap()

    suspend fun initialize() {
        userPredColl.ensureUniqueIndex(Prediction::question, Prediction::user)
        userPredColl.ensureIndex(Prediction::question)
        userPredColl.ensureIndex(Prediction::user)
        questionCommentsColl.ensureIndex(QuestionComment::question, QuestionComment::timestamp)
        questionCommentsColl.ensureIndex(QuestionComment::user, QuestionComment::timestamp)
        roomCommentsColl.ensureIndex(RoomComment::room)
        roomCommentsColl.ensureIndex(RoomComment::user)
    }
    // initial load from database
    suspend fun load() {
        questions.putAll(loadEntityMap(questionsColl))
        rooms.putAll(loadEntityMap(roomsColl))
        users.putAll(loadEntityMap(usersColl))
        userPredColl.find().toFlow().collect {
            userPred.getOrPut(it.question) {mutableMapOf()}[it.user ?: return@collect] = it
        }
        questionCommentsColl.find().toFlow().collect {
            questionComments.getOrPut(it.question) {mutableMapOf()}[it.id] = it
        }
        roomCommentsColl.find().toFlow().collect {
            roomComments.getOrPut(it.room) {mutableMapOf()}[it.id] = it
        }
        recalcGroupPred()
    }

    fun calculateGroupDist(space: Space, dists: Collection<ProbabilityDistribution>): ProbabilityDistribution =
        when (space) {
            is NumericSpace -> {
                val probs = dists.mapNotNull { (it as? ContinuousProbabilityDistribution)?.discretize()?.binProbs }
                    .fold(zeros(space.bins)) { a,b -> a `Z+` b }.normalize()
                DiscretizedContinuousDistribution(space, probs)
            }
            is BinarySpace -> {
                val prob = dists.mapNotNull { (it as? BinaryDistribution)?.yesProb  }.average().clamp01()
                BinaryDistribution(prob)
            }
        }

    fun calculateGroupPred(question: Question, preds: Collection<Prediction>): Prediction? =
        when (preds.size) {
            0 -> null
            1 -> preds.first().copy(user = null)
            else -> {
                Prediction(user = null, ts = preds.map{it.ts}.max(), question = question.ref,
                            dist = calculateGroupDist(question.answerSpace, preds.map{it.dist}))
            }
        }

    fun recalcGroupPred(question: Question?) {
        question ?: return
        val gp = calculateGroupPred(question, userPred[question.ref]?.values ?: emptyList())
        groupPred[question.ref] = gp
    }
    fun recalcGroupPred() {
        questions.values.forEach {
            recalcGroupPred(it)
        }
    }

    fun export(session: UserSession): SentState {
        // TODO censor
        return SentState(
            rooms = rooms,
            questions = questions,
            users = users,
            session = session
        )
    }

    companion object {
        fun <T : Entity> defaultMerge(): (T, T) -> T = { orig, new -> new }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: Entity> getCollection(): CoroutineCollection<T> = when (T::class) {
        Question::class -> questionsColl as CoroutineCollection<T>
        else -> throw IllegalArgumentException()
    }
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T: Entity> getMap(): MutableMap<String, T> = when (T::class) {
        Question::class -> questions as MutableMap<String, T>
        else -> throw IllegalArgumentException()
    }

    fun CoroutineContext.getSession() =
        this[TransactionContextElement]?.sess

    suspend inline fun <reified T: ServerImmediateDerefEntity>
    updateEntity(new: T, compare: T? = null,
                upsert: Boolean = false,
                crossinline merge: (orig: T, new: T)->T = { _, new -> new }, ) : T{
        mutationMutex.withLock {
            if (upsert && compare != null) throw IllegalArgumentException()
            val map = getMap<T>()
            val coll = getCollection<T>()
            val orig = map[new.id]
            if (orig == null) {
                if (upsert) {
                    return insertEntity(new)
                } else {
                    throw NoSuchElementException()
                }
            }
            compare?.let {
                if (compare.id != new.id) throw IllegalArgumentException()
                if (orig != compare) throw ConcurrentModificationException()
            }
            val merged = merge(orig, new)
            check(merged.id == new.id)
            map[new.id] = merged
            when (val sess = coroutineContext.getSession()) {
                null -> coll.replaceOne(merged, ReplaceOptions())
                else -> coll.replaceOneById(sess, merged.id, merged, ReplaceOptions())
            }
            return merged
        }
    }

    suspend inline fun <R> withMutationLock(crossinline body: suspend ServerGlobalState.()->R): R =
        if (coroutineContext[MutationLockedContextElement.Key] != null) { // parent has already locked
            body()
        } else {
            mutationMutex.withLock {
                withContext(MutationLockedContextElement) {
                    body()
                }
            }
        }

    suspend inline fun <reified T: ServerImmediateDerefEntity>
    modifyEntity(ref: Ref<T>, crossinline modify: (T)->T): T =
        withMutationLock {
            val map = getMap<T>()
            val coll = getCollection<T>()
            val orig = map[ref.id] ?: throw NoSuchElementException()
            check(orig.id == ref.id)
            val new = modify(orig)
            map[ref.id] = new
            when (val sess = coroutineContext.getSession()) {
                null -> coll.replaceOne(new, ReplaceOptions())
                else -> coll.replaceOneById(sess, new.id, new, ReplaceOptions())
            }
            return@withMutationLock new
        }

    suspend inline fun <reified T: ServerImmediateDerefEntity>
    insertEntity(entity: T, forceId: Boolean = false) : T =
        withMutationLock {
            if (!entity.id.isEmpty() && !forceId) throw IllegalArgumentException()
            val new = entity.assignIdIfNeeded()
            val map = getMap<T>()
            val coll = getCollection<T>()
            if (map.containsKey(new.id)) throw DuplicateIdException()
            map[entity.id] = new
            when (val sess = coroutineContext.getSession()) {
                null -> coll.insertOne(new)
                else -> coll.insertOne(sess, new)
            }
            return@withMutationLock new
        }

    suspend inline fun <reified T: ServerImmediateDerefEntity>
            deleteEntity(ref: Ref<T>, ignoreNonexistent: Boolean = false) : T? =
        withMutationLock {
            val map = getMap<T>()
            val coll = getCollection<T>()
            val orig = map.remove(ref.id)
            map[ref.id] ?: if (ignoreNonexistent) return@withMutationLock null else throw NoSuchElementException()
            when (val sess = coroutineContext.getSession()) {
                null -> coll.deleteOneById(ref.id)
                else -> coll.deleteOneById(sess, ref.id)
            }
            map.remove(ref.id)
            return@withMutationLock orig
        }
    suspend inline fun <reified T: ServerImmediateDerefEntity>
            deleteEntity(entity: T, ignoreNonexistent: Boolean = false) : T? {
        mutationMutex.withLock {
            val map = getMap<T>()
            val coll = getCollection<T>()
            val orig = map[entity.id] ?: if (ignoreNonexistent) return null else throw NoSuchElementException()
            if (orig != entity) throw ConcurrentModificationException()
            map.remove(entity.id)
            when (val sess = coroutineContext.getSession()) {
                null -> coll.deleteOneById(entity.id)
                else -> coll.deleteOneById(sess, entity.id)
            }
            return orig
        }
    }

    suspend inline fun <R> withTransaction(crossinline body: suspend ServerGlobalState.()->R): R {
        if (coroutineContext[TransactionContextElement.Key] != null)
            throw RuntimeException("Nested transactions are not supported")
        return withMutationLock{
            client.startSession().use { clientSession ->
                clientSession.startTransaction()
                var ret: R
                withContext(TransactionContextElement(clientSession)) {
                    ret = body()
                }
                clientSession.commitTransactionAndAwait()
                return@use ret
            }
        }
    }

    suspend fun addPrediction( pred: Prediction) {
        val pred = pred.copy(id = generateId())
        require(pred.user != null)
        require(pred.user.deref() != null)
        require(pred.question.deref() != null)
        withMutationLock {
            userPred.getOrPut(pred.question){mutableMapOf()}[pred.user] = pred
            when (val sess = coroutineContext.getSession()) {
                null -> userPredColl.replaceOne( and(Prediction::user eq pred.user, Prediction::question eq pred.question), pred)
                else -> userPredColl.replaceOne(sess, and(Prediction::user eq pred.user, Prediction::question eq pred.question), pred)
            }
        }
        recalcGroupPred(pred.question.deref())
    }

    suspend fun addQuestionComment(comment: QuestionComment) {
        require(comment.question.deref() != null)
        require(comment.user.deref() != null)
        withMutationLock {
            questionComments.getOrPut(comment.question){mutableMapOf()}[comment.id] = comment
            when (val sess = coroutineContext.getSession()) {
                null -> questionCommentsColl.insertOne(comment)
                else -> questionCommentsColl.insertOne(sess, comment)
            }
        }
    }
    suspend fun addRoomComment(comment: QuestionComment) {
        require(comment.question.deref() != null)
        require(comment.user.deref() != null)
        withMutationLock {
            questionComments.getOrPut(comment.question){mutableMapOf()}[comment.id] = comment
            when (val sess = coroutineContext.getSession()) {
                null -> questionCommentsColl.insertOne(comment)
                else -> questionCommentsColl.insertOne(sess, comment)
            }
        }
    }
}


val serverState = ServerGlobalState()
actual val globalState: GlobalState = serverState