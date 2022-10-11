package tools.confido.state

import com.mongodb.ConnectionString
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
import users.LoginLink
import users.User
import java.lang.RuntimeException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass


class DuplicateIdException : Exception() {}
object MutationLockedContextElement : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<MutationLockedContextElement>

}
class TransactionContextElement(val sess: ClientSession) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionContextElement>
}


object serverState : GlobalState() {
    override val groupPred : MutableMap<Ref<Question>, Prediction?> = mutableMapOf()

    // Now, for simplicity, serialize all mutations
    val mutationMutex = Mutex()

    val client = KMongo.createClient(ConnectionString(System.getenv("CONFIDO_MONGODB_URL") ?: "mongodb://localhost")).coroutine
    val database = client.getDatabase(System.getenv("CONFIDO_DB_NAME") ?: "confido1")


    abstract class EntityManager<E: HasId>(val mongoCollection: CoroutineCollection<E>) {
        var isLoading = false
        val updateCallbacks: MutableList<EntityManager<E>.(E, E) -> Unit> = mutableListOf()
        val addCallbacks: MutableList<EntityManager<E>.(E) -> Unit> = mutableListOf()
        val deleteCallbacks: MutableList<EntityManager<E>.(E) -> Unit> = mutableListOf()
        fun onEntityUpdated(cb: EntityManager<E>.(E, E) -> Unit) {
            updateCallbacks.add(cb)
        }

        fun onEntityAdded(cb: EntityManager<E>.(E) -> Unit) {
            addCallbacks.add(cb)
        }

        fun onEntityAddedOrUpdated(cb: EntityManager<E>.(E) -> Unit) {
            onEntityAdded(cb)
            onEntityUpdated({ old, new -> cb(new) })
        }

        fun onEntityDeleted(cb: EntityManager<E>.(E) -> Unit) {
            deleteCallbacks.add(cb)
        }

        fun notifyEntityAdded(entity: E) {
            addCallbacks.forEach { it(entity) }
        }

        fun notifyEntityUpdated(old: E, new: E) {
            updateCallbacks.forEach { it(old, new) }
        }

        fun notifyEntityDeleted(entity: E) {
            deleteCallbacks.forEach { it(entity) }
        }

        open suspend fun initialize() {}
        open suspend fun doLoad() {}
        suspend fun load() {
            isLoading = true
            try {
                doLoad()
            } finally {
                isLoading = false
            }
        }

    }
    open class IdBasedEntityManager<E: HasId>(mongoCollection: CoroutineCollection<E>) : EntityManager<E>(mongoCollection) {
        open suspend fun get(id: String): E? {
            return mongoCollection.findOneById(id)
        }

        suspend fun modifyEntity(id: String, modify: (E)->E): E =
            withMutationLock {
                val orig = this.get(id) ?: throw NoSuchElementException()
                check(orig.id == id)
                val new = modify(orig)
                when (val sess = coroutineContext.getSession()) {
                    null -> mongoCollection.replaceOneById(new.id, new, ReplaceOptions())
                    else -> mongoCollection.replaceOneById(sess, new.id, new, ReplaceOptions())
                }
                notifyEntityUpdated(orig, new)
                return@withMutationLock new
            }
        suspend fun replaceEntity(new: E, compare: E? = null,
                             upsert: Boolean = false,
                             merge: (orig: E, new: E)->E = { _, new -> new }, ) : E =
            withMutationLock {
                if (upsert && compare != null) throw IllegalArgumentException()
                val orig: E? = get(new.id)
                if (orig == null) {
                    if (upsert) {
                        return@withMutationLock insertWithId(new)
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
                when (val sess = coroutineContext.getSession()) {
                    null -> mongoCollection.replaceOneById(merged.id, merged, ReplaceOptions())
                    else -> mongoCollection.replaceOneById(sess, merged.id, merged, ReplaceOptions())
                }
                notifyEntityUpdated(orig, merged)
                return@withMutationLock merged
            }


        suspend fun deleteEntity(id: String, ignoreNonexistent: Boolean = false) : E? =
            withMutationLock {
                val orig: E = get(id) ?: if (ignoreNonexistent) return@withMutationLock null else throw NoSuchElementException()
                when (val sess = coroutineContext.getSession()) {
                    null -> mongoCollection.deleteOneById(id)
                    else -> mongoCollection.deleteOneById(sess, id)
                }
                notifyEntityDeleted(orig)
                return@withMutationLock orig
            }
        suspend fun deleteEntity(entity: E, ignoreNonexistent: Boolean = false,
                                    check: (E,E)->Boolean = { found, expected -> found == expected }) : E? =
            withMutationLock {
                val orig: E = get(entity.id) ?: if (ignoreNonexistent) return@withMutationLock null else throw NoSuchElementException()
                if (orig != entity) throw ConcurrentModificationException()
                when (val sess = coroutineContext.getSession()) {
                    null -> mongoCollection.deleteOneById(entity.id)
                    else -> mongoCollection.deleteOneById(sess, entity.id)
                }
                notifyEntityDeleted(orig)
                return@withMutationLock orig
            }
        suspend fun insertWithId(entity: E) =
            withMutationLock {
                require(!entity.id.isEmpty())
                val old: E? = get(entity.id)
                if (old != null) throw DuplicateIdException()
                when (val sess = coroutineContext.getSession()) {
                    null -> mongoCollection.insertOne(entity)
                    else -> mongoCollection.insertOne(sess, entity)
                }
                notifyEntityAdded(entity)
                return@withMutationLock entity
            }

    }

    open class InMemoryEntityManager<E: Entity>(mongoCollection: CoroutineCollection<E>)
            : IdBasedEntityManager<E>(mongoCollection) {
        val entityMap: MutableMap<String, E> = mutableMapOf()
        override suspend fun doLoad() {
            mongoCollection.find().toFlow().collect { notifyEntityAdded(it) }
        }

        override suspend fun get(id: String): E? = entityMap[id]
        init {
            onEntityAddedOrUpdated { entityMap[it.id] = it }
            onEntityDeleted { entityMap.remove(it.id) }
        }
        // FIXME move general parts to EntityManager to handle case when all entities are not cached in memory
    }

    val managers : MutableMap<KClass<*>, EntityManager<*>> = mutableMapOf()
    val additionalManagers : MutableList<EntityManager<*>> = mutableListOf()
    @Suppress("UNCHECKED_CAST")
    inline fun <reified  E: Entity> getManager() =
        managers[E::class] as EntityManager<E>

    inline fun getManager(entity: Entity) =
        managers[entity::class]
    inline fun <reified  E: Entity> EntityManager<E>.register() {
        managers[E::class] = this
    }
    @JvmName("register2")
    inline fun <reified  E: HasId> EntityManager<E>.register() {
        additionalManagers.add(this)
    }
    val questionRoom: MutableMap<Ref<Question>, Ref<Room>> = mutableMapOf()
    object roomManager : InMemoryEntityManager<Room>(database.getCollection<Room>("rooms")) {
        init {
            onEntityAdded { room->
                room.questions.forEach { questionRoom[it] = room.ref }
            }
            onEntityUpdated { old, new->
                val addedQuestions = new.questions.toSet() - old.questions.toSet()
                val removedQuestions = old.questions.toSet() - new.questions.toSet()
                addedQuestions.forEach { questionRoom[it] = new.ref }
                removedQuestions.forEach { if (questionRoom[it] == new.ref) questionRoom.remove(it) }
            }
            onEntityDeleted { room ->
                room.questions.forEach { if (questionRoom[it] == room.ref) questionRoom.remove(it) }
            }
        }
    }
    override val rooms by roomManager::entityMap
    object questionManager : InMemoryEntityManager<Question>(database.getCollection("questions")) {
    }
    override val questions by questionManager::entityMap

    object userManager : InMemoryEntityManager<User>(database.getCollection("users")) {
        override suspend fun initialize() {
            super.initialize()
            mongoCollection.ensureUniqueIndex(User::email)
        }
        val byEmail: MutableMap<String, User> = mutableMapOf()
        init {
            onEntityAddedOrUpdated { it.email?.let{ email-> byEmail[email] = it } }
            onEntityDeleted { it.email?.let{ email -> byEmail.remove(email) } }
        }

    }
    override val users by userManager::entityMap

    object userPredManager : EntityManager<Prediction>(database.getCollection("userPred")) {
        val userPred : MutableMap<Ref<Question>, MutableMap<Ref<User>, Prediction>> = mutableMapOf()
        override suspend fun initialize() {
            mongoCollection.ensureUniqueIndex(Prediction::question, Prediction::user)
            mongoCollection.ensureIndex(Prediction::question)
            mongoCollection.ensureIndex(Prediction::user)
        }

        override suspend fun doLoad() {
            super.doLoad()
            recalcGroupPred()
        }

        fun get(question: Ref<Question>, user: Ref<User>) =
            userPred[question]?.get(user)

        suspend fun save(pred: Prediction)  = withMutationLock {
            val pred = if (pred.id != "") pred else pred.copy(id=generateId())
            require(pred.user != null)
            val orig = get(pred.question, pred.user)
            val filter = and(Prediction::question eq pred.question, Prediction::user eq pred.user)

            when (val sess = coroutineContext.getSession()) {
                null -> mongoCollection.replaceOne(filter, pred, ReplaceOptions().upsert(true))
                else -> mongoCollection.replaceOne(sess, filter, pred, ReplaceOptions().upsert(true))
            }

            if (orig == null) notifyEntityAdded(pred)
        }
        init {
            onEntityAddedOrUpdated {  pred ->
                userPred.getOrPut(pred.question){ mutableMapOf() }[pred.user ?: return@onEntityAddedOrUpdated] = pred
                if (!isLoading) recalcGroupPred(questions[pred.question.id])
            }
            onEntityDeleted { pred->
                val qp = userPred[pred.question] ?: return@onEntityDeleted
                val userRef = pred.user ?: return@onEntityDeleted
                if (qp[userRef] eqid pred) qp.remove(userRef)
                if (!isLoading) recalcGroupPred(questions[pred.question.id])
            }
        }
    }
    val userPred by userPredManager::userPred

    object questionCommentManager: InMemoryEntityManager<QuestionComment>(database.getCollection("questionComments")) {
        val questionComments : MutableMap<Ref<Question>, MutableMap<String, QuestionComment>> = mutableMapOf()
        init {
            onEntityAddedOrUpdated { comment ->
                questionComments.getOrPut(comment.question){ mutableMapOf() }[comment.id] = comment
            }
            onEntityDeleted {comment ->
                (questionComments[comment.question] ?: return@onEntityDeleted).remove(comment.id)
            }
        }
    }
    override val questionComments: MutableMap<Ref<Question>, MutableMap<String, QuestionComment>> by questionCommentManager::questionComments
    object roomCommentManager: InMemoryEntityManager<RoomComment>(database.getCollection("roomComments")) {
        val roomComments : MutableMap<Ref<Room>, MutableMap<String, RoomComment>> = mutableMapOf()
        init {
            onEntityAddedOrUpdated { comment ->
                roomComments.getOrPut(comment.room){ mutableMapOf() }[comment.id] = comment
            }
            onEntityDeleted {comment ->
                (roomComments[comment.room] ?: return@onEntityDeleted).remove(comment.id)
            }
        }
    }
    override val roomComments by roomCommentManager::roomComments

    object loginLinkManager : InMemoryEntityManager<LoginLink>(database.getCollection("loginLinks")) {
        val byToken: MutableMap<String, LoginLink> = mutableMapOf()
        init {
            onEntityAddedOrUpdated { byToken[it.token] = it }
            onEntityDeleted { byToken.remove(it.token) }
        }
    }

    init {
        roomManager.register()
        questionManager.register()
        userManager.register()
        userPredManager.register()
        questionCommentManager.register()
        roomCommentManager.register()
    }




    suspend fun initialize() {
        managers.values.forEach { it.initialize() }
        additionalManagers.forEach { it.initialize() }
    }
    // initial load from database
    suspend fun load() {
        managers.values.forEach { it.load() }
        additionalManagers.forEach { it.load() }
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

    fun CoroutineContext.getSession() =
        this[TransactionContextElement]?.sess


    suspend inline fun <R> withMutationLock(crossinline body: suspend ()->R): R =
        if (coroutineContext[MutationLockedContextElement.Key] != null) { // parent has already locked
            body()
        } else {
            mutationMutex.withLock {
                withContext(MutationLockedContextElement) {
                    body()
                }
            }
        }



    suspend inline fun <R> withTransaction(crossinline body: suspend serverState.()->R): R {
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

    @DelicateRefAPI
    override fun derefNonBlocking(entityType: KClass<*>, id: String): Entity? {
        managers[entityType]?.let { manager ->
            when (manager) {
                is InMemoryEntityManager ->
                    return manager.entityMap[id] as Entity?
                else -> {}
            }
        }
        return super.derefNonBlocking(entityType, id)
    }

}
suspend inline fun <reified  E: Entity> serverState.IdBasedEntityManager<E>.insertEntity(entity: E, forceId: Boolean = false) =
    insertWithId(entity.assignIdIfNeeded())
suspend fun <E: Entity> serverState.IdBasedEntityManager<E>.deleteEntity(ref: Ref<E>, ignoreNonexistent: Boolean = false) =
    deleteEntity(ref.id, ignoreNonexistent)
suspend fun <E: Entity> serverState.IdBasedEntityManager<E>.modifyEntity(ref: Ref<E>, modify: (E) -> E) =
    modifyEntity(ref.id, modify)


actual val globalState: GlobalState = serverState