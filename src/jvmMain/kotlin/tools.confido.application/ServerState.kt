@file:Suppress("NOTHING_TO_INLINE")

package tools.confido.state

import com.mongodb.ConnectionString
import com.mongodb.MongoException
import com.mongodb.client.model.Collation
import com.mongodb.client.model.CollationStrength
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.ClientSession
import io.ktor.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.reactivestreams.KMongo
import rooms.Room
import tools.confido.distributions.*
import tools.confido.extensions.ServerExtension
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.spaces.*
import tools.confido.utils.*
import users.EmailVerificationLink
import users.LoginLink
import users.PasswordResetLink
import users.User
import java.lang.RuntimeException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class DuplicateIdException : Exception() {}
object MutationLockedContextElement : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<MutationLockedContextElement>

}
class TransactionContextElement(val sess: ClientSession) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionContextElement>
}

fun getenvBool(name: String, default: Boolean = false): Boolean {
    return (System.getenv(name)?.ifEmpty {null} ?: return default) == "1"
}
fun getenvList(name: String, default: List<String> = emptyList()): List<String> {
    return (System.getenv(name)?.ifEmpty {null} ?: return default).split(',')
}

fun loadConfig() = AppConfig(
    devMode = getenvBool("CONFIDO_DEVMODE"),
    demoMode = getenvBool("CONFIDO_DEMO"),
    betaIndicator = getenvBool("CONFIDO_BETA_INDICATOR"),
    featureFlags = FeatureFlag.values().filter{ getenvBool("CONFIDO_FEAT_${it.name}", it in DEFAULT_FEATURE_FLAGS)}.toSet(),
    privacyPolicyUrl = System.getenv("CONFIDO_PRIVACY_POLICY_URL")?.ifEmpty {null},
    enabledExtensionIds = getenvList("CONFIDO_EXTENSIONS").toSet(),
    predictionCoalesceInterval = System.getenv("CONFIDO_PREDICTION_COALESCE_INTERVAL")?.toInt() ?: 60,
)

actual val appConfig = loadConfig()

data class ShortLink(
    val room: Ref<Room>,
    val linkId: String,
    val shortcode: String = generate(),
    val expires: Int = unixNow() + VALIDITY,
) {
    fun isValid() = unixNow() < expires
    companion object {
        const val LENGTH = 6
        const val VALIDITY = 30 * 60
        val REGEX = Regex("^[0-9]{$LENGTH}$")
        fun generate() = generateNonce(LENGTH).joinToString("") { (it % 10).toString() }
    }
}

object serverState : GlobalState() {
    val groupPred : MutableMap<Ref<Question>, Prediction?> = mutableMapOf()
    override val predictorCount: MutableMap<Ref<Question>, Int> = mutableMapOf()
    override val commentCount: MutableMap<Ref<Question>, Int> = mutableMapOf()
    //val presenterByUser: MutableMap<Ref<User>, PresenterInfo> = mutableMapOf() // this does not persist after restart
    //val presenterByToken: MutableMap<String, PresenterInfo> = mutableMapOf() // this does not persist after restart

    // Now, for simplicity, serialize all mutations
    val mutationMutex = Mutex()

    val client = KMongo.createClient(ConnectionString(System.getenv("CONFIDO_MONGODB_URL") ?: "mongodb://localhost/?replicaSet=rs01")).coroutine
    val database = client.getDatabase(System.getenv("CONFIDO_DB_NAME") ?: "confido1")

    val shortInviteLinks = mutableMapOf<String, ShortLink>()

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
            onEntityUpdated({ _, new -> cb(new) })
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
    interface ExpiringEntityManager<E: HasExpiration> {
        suspend fun cleanupExpired()
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
                             merge: (orig: E, new: E)->E = { _, newM -> newM }, ) : E =
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

    open class InMemoryExpiringEntityManager<E: ExpiringEntity>(mongoCollection: CoroutineCollection<E>, val tolerance: Duration = 0.seconds)
        : InMemoryEntityManager<E>(mongoCollection), ExpiringEntityManager<E> {
        override suspend fun cleanupExpired() {
            val now = Clock.System.now()
            val toDelete = mutableListOf<String>()
            this.entityMap.values.forEach {
                if (now > it.expiryTime + tolerance)
                    toDelete.add(it.id)
            }

            toDelete.forEach { this.deleteEntity(it) }
        }
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
            mongoCollection.ensureIndex(User::email)

            try {
                mongoCollection.ensureUniqueIndex(
                    User::email, indexOptions = IndexOptions().collation(
                        Collation.builder().locale("simple").collationStrength(
                            CollationStrength.SECONDARY
                        ).build()
                    )
                )
            } catch (e: MongoException) {
                println("Creating unique index failed, there are probably duplicate emails already")
            }
        }
        val byEmail: MutableMap<String, User> = mutableMapOf()
        init {
            onEntityAddedOrUpdated { it.email?.let{ email-> byEmail[email.lowercase()] = it } }
            onEntityDeleted { it.email?.let{ email -> byEmail.remove(email.lowercase()) } }
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
            mongoCollection.find().toFlow().collect { notifyEntityAdded(it) }
            recalcGroupPred()
        }

        fun get(question: Ref<Question>, user: Ref<User>) =
            userPred[question]?.get(user)

        suspend fun save(savedPred: Prediction)  = withMutationLock {
            require(savedPred.user != null)
            val pred = savedPred.copy(id = "${savedPred.question.id}:${savedPred.user.id}")
            val orig = get(pred.question, pred.user!!)
            val filter = and(Prediction::question eq pred.question, Prediction::user eq pred.user)

            when (val sess = coroutineContext.getSession()) {
                null -> mongoCollection.replaceOne(filter, pred, ReplaceOptions().upsert(true))
                else -> mongoCollection.replaceOne(sess, filter, pred, ReplaceOptions().upsert(true))
            }

            if (orig == null) notifyEntityAdded(pred)
            else notifyEntityUpdated(orig, pred)
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

    object userPredHistManager : IdBasedEntityManager<Prediction>(database.getCollection("userPredHist")) {
        val totalPredictions: MutableMap<Ref<Question>, Int> = mutableMapOf()
        override suspend fun initialize() {
            mongoCollection.ensureIndex(Prediction::question, Prediction::user, Prediction::ts)
            mongoCollection.ensureIndex(Prediction::question, Prediction::ts)
        }

        override suspend fun doLoad() {
            @Serializable
            data class Result(val question: Ref<Question>, val cnt: Int)
            mongoCollection.aggregate<Result>(
                group(Prediction::question, Result::question first Prediction::question, Result::cnt.sum(1))
            ).toFlow().collect { totalPredictions[it.question] = it.cnt }
            //println("STATS!!! $totalPredictions")
        }
        fun query(question: Ref<Question>, user: Ref<User>) =
            mongoCollection.find(and(Prediction::question eq question, Prediction::user eq user))
                .sort(ascending(Prediction::ts)).toFlow()

        suspend fun at(question: Ref<Question>, user: Ref<User>, ts: Int) =
            mongoCollection.find(and(Prediction::question eq question,
                                                            Prediction::user eq user,
                                                            Prediction::ts lte ts))
                .sort(descending(Prediction::ts)).first()

        suspend fun at(question: Ref<Question>, user: Ref<User>, ts: Instant) =
            if (ts == Instant.DISTANT_FUTURE) userPred[question]?.get(user)
            else at(question, user, ts.epochSeconds.toInt())
        init {
            onEntityAdded {
                totalPredictions.compute(it.question) { _, cnt -> (cnt ?: 0) + 1 }
            }
            onEntityDeleted {
                totalPredictions.compute(it.question) { _, cnt -> (cnt ?: 0) - 1 }
            }
        }
    }

    override val predictionCount: Map<Ref<Question>, Int> by userPredHistManager::totalPredictions
    object groupPredHistManager : IdBasedEntityManager<Prediction>(database.getCollection("groupPredHist")) {
        override suspend fun initialize() {
            mongoCollection.ensureIndex(Prediction::question, Prediction::ts)
        }
        fun query(question: Ref<Question>) =
            mongoCollection.find(and(Prediction::question eq question))
                .sort(ascending(Prediction::ts)).toFlow()

        suspend fun at(question: Ref<Question>, ts: Int) =
            mongoCollection.find(and(Prediction::question eq question, Prediction::ts lte ts))
                .sort(descending(Prediction::ts)).first()
        suspend fun at(question: Ref<Question>, ts: Instant) =
            if (ts == Instant.DISTANT_FUTURE) groupPred[question]
            else at(question, ts.epochSeconds.toInt())
    }

    object questionCommentManager: InMemoryEntityManager<QuestionComment>(database.getCollection("questionComments")) {
        val questionComments : MutableMap<Ref<Question>, MutableMap<String, QuestionComment>> = mutableMapOf()
        fun updateCommentCount(question: Ref<Question>) {
            val size = questionComments[question]?.size ?: 0
            if (size > 0)
                commentCount[question] = size
            else
                commentCount.remove(question)
        }
        init {
            onEntityAddedOrUpdated { comment ->
                questionComments.getOrPut(comment.question){ mutableMapOf() }[comment.id] = comment
                updateCommentCount(comment.question)
            }
            onEntityDeleted {comment ->
                (questionComments[comment.question] ?: return@onEntityDeleted).remove(comment.id)
                updateCommentCount(comment.question)
            }
        }
    }
    val questionComments: MutableMap<Ref<Question>, MutableMap<String, QuestionComment>> by questionCommentManager::questionComments
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
    val roomComments by roomCommentManager::roomComments

    //object commentLikeManager : EntityManager<CommentLike>(database.getCollection("commentLikes")) {
    //    val numLikes = mutableMapOf<Ref<Comment>, Int>()
    //    override suspend fun initialize() {
    //        mongoCollection.ensureUniqueIndex(CommentLike::comment, CommentLike::user)
    //    }
    //    override suspend fun doLoad() {
    //        @Serializable
    //        data class Result(val comment: Ref<Comment>, val cnt: Int)
    //        mongoCollection.aggregate<Result>(
    //            group(CommentLike::comment, Result::comment first CommentLike::comment, Result::cnt.sum(1))
    //        ).toFlow().collect { numLikes[it.comment] = it.cnt }
    //        //println("STATS!!! $totalPredictions")
    //    }

    //    suspend fun addLike(comment: Ref<Comment>, user: Ref<User>) {
    //        try {
    //            mongoCollection.insertOne(CommentLike(generateId(), comment, user))
    //            numLikes.compute(comment, {_, cnt -> (cnt?:0) + 1})
    //        } catch (e: MongoWriteException) {
    //            if (e.error.category != ErrorCategory.DUPLICATE_KEY) throw e;
    //        }
    //    }
    //}
    object commentLikeManager : InMemoryEntityManager<CommentLike>(database.getCollection("commentLikes")) {
        val numLikes = mutableMapOf<Ref<Comment>, Int>()
        val byComment = mutableMapOf<Ref<Comment>, MutableSet<Ref<User>>>()
        val byUser = mutableMapOf<Ref<User>, MutableSet<Ref<Comment>>>()

        suspend fun addLike(comment: Ref<Comment>, user: Ref<User>) {
            withMutationLock {
                if (user in (byComment[comment] ?: setOf())) return@withMutationLock // already liked
                insertEntity(CommentLike(id = "", comment, user))
            }
        }
        suspend fun removeLike(comment: Ref<Comment>, user: Ref<User>) {
            if (user !in (byComment[comment]?:setOf())) return; // not liked
            withMutationLock {
                mongoCollection.deleteMany(and(CommentLike::comment eq comment, CommentLike::user eq user))
                (byComment[comment]?:mutableSetOf()).remove(user)
                (byUser[user]?:mutableSetOf()).remove(comment)
                numLikes[comment] = byComment[comment]?.size ?: 0
            }
        }
        suspend fun setLike(comment: Ref<Comment>, user: Ref<User>, state: Boolean) {
            if (state) addLike(comment, user)
            else removeLike(comment, user)
        }
        suspend fun deleteAllUserLikes(user: Ref<User>) {
            withMutationLock {
                mongoCollection.deleteMany(CommentLike::user eq user)
                byUser[user]?.forEach { comment ->
                    byComment[comment]?.remove(user)
                    numLikes[comment] = byComment[comment]?.size ?: 0
                }
                byUser.remove(user)
            }
        }

        suspend fun deleteAllCommentLikes(comment: Ref<Comment>) {
            withMutationLock {
                mongoCollection.deleteMany(CommentLike::comment eq comment)
                byComment[comment]?.forEach { user ->
                    byUser[user]?.remove(comment)
                }
                byComment.remove(comment)
                numLikes.remove(comment)
            }
        }
        init {
            onEntityAdded {
                byComment.getOrPut(it.comment, {mutableSetOf()}).add(it.user)
                byUser.getOrPut(it.user, {mutableSetOf()}).add(it.comment)
                numLikes[it.comment] = byComment[it.comment]?.size ?: 0
            }
            onEntityDeleted { like->
                byComment[like.comment]?.remove(like.user)
                byUser[like.user]?.remove(like.comment)
                numLikes[like.comment] = byComment[like.comment]?.size ?: 0
            }
        }
    }

    object loginLinkManager : InMemoryEntityManager<LoginLink>(database.getCollection("loginLinks")) {
        val byToken: MutableMap<String, LoginLink> = mutableMapOf()
        init {
            onEntityAddedOrUpdated { byToken[it.token] = it }
            onEntityDeleted { byToken.remove(it.token) }
        }
    }

    object verificationLinkManager : InMemoryEntityManager<EmailVerificationLink>(database.getCollection("mailVerificationLinks")) {
        val byToken: MutableMap<String, EmailVerificationLink> = mutableMapOf()
        init {
            onEntityAddedOrUpdated { byToken[it.token] = it }
            onEntityDeleted { byToken.remove(it.token) }
        }
    }

    object passwordResetLinkManager : InMemoryEntityManager<PasswordResetLink>(database.getCollection("passwordResetLinks")) {
        val byToken: MutableMap<String, PasswordResetLink> = mutableMapOf()
        init {
            onEntityAddedOrUpdated { byToken[it.token] = it }
            onEntityDeleted { byToken.remove(it.token) }
        }
    }

    object userSessionManager : InMemoryExpiringEntityManager<UserSession>(database.getCollection("userSessions"))
    init {
        roomManager.register()
        questionManager.register()
        userManager.register()
        userPredManager.register()
        questionCommentManager.register()
        roomCommentManager.register()
        loginLinkManager.register()
        verificationLinkManager.register()
        userSessionManager.register()
        userPredManager.register()
        userPredHistManager.register()
        groupPredHistManager.register()
        commentLikeManager.register()
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
            1 -> preds.first().copy(user = null, id = "") // id contains uid of predicting user; don't want to leak
            else -> {
                ServerExtension.enabled.mapNotNull {
                    it.calculateGroupPred(question, preds)
                }.firstOrNull() ?:
                Prediction(user = null, ts = preds.map{it.ts}.max(), question = question.ref,
                            dist = calculateGroupDist(question.answerSpace, preds.map{it.dist}))
            }
        }

    fun recalcGroupPred(question: Question?): Prediction? {
        question ?: return null
        val gp = calculateGroupPred(question, userPred[question.ref]?.values ?: emptyList())
        groupPred[question.ref] = gp
        predictorCount[question.ref] = userPred[question.ref]?.size ?: 0
        return gp
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

    suspend fun addPrediction(pred: Prediction) {
        val q = pred.question.deref()
        require(q != null)
        val sched  = q.effectiveSchedule
        withTransaction {
            userPredManager.save(pred)
            val myLastPred = userPredHistManager.mongoCollection.find(and(Prediction::question eq pred.question,
                Prediction::user eq pred.user,
                ))
                .sort(descending(Prediction::ts)).first()

            // If predictions are less than 1min apart, replace previous one instead of adding another history
            // entry. This prevents clogging up history with lots of records and also makes the "number of uptates"
            // metric more meaningful. Exception: if previous prediction was before score time and new one after
            // we need to keep the previous one in order to preserve scoring.
            var lastPredOther: Prediction? = null
            if (myLastPred != null && pred.ts - myLastPred.ts < appConfig.predictionCoalesceInterval &&
                    !(sched.score != null && myLastPred.ts <= sched.score.epochSeconds && pred.ts > sched.score.epochSeconds)) {
                userPredHistManager.deleteEntity(myLastPred)
                lastPredOther = userPredHistManager.mongoCollection.find(and(Prediction::question eq pred.question))
                    .sort(descending(Prediction::ts)).first()
            }

            userPredHistManager.insertEntity(pred.copy(id=""))
            val gp = recalcGroupPred(q)
            gp?.let {
                check(gp.user==null)
                if (lastPredOther != null) {
                    // If last group prediction update was due to my deleted prediction, delete it also.
                    val lastGroupPred =
                        groupPredHistManager.mongoCollection.find(and(Prediction::question eq pred.question))
                            .sort(descending(Prediction::ts)).first()
                    if (lastGroupPred != null && lastGroupPred.ts >= lastPredOther.ts) {
                        groupPredHistManager.deleteEntity(lastGroupPred)
                    }
                }
                groupPredHistManager.insertEntity(gp.copy(id = ""))
            }
            serverState.extensions.forEach { it.onPrediction(q, pred) }
        }
    }

    override val extensions: List<ServerExtension> get() = ServerExtension.enabled
}
suspend inline fun <reified  E: HasId> serverState.IdBasedEntityManager<E>.insertEntity(entity: E, forceId: Boolean = false) =
    insertWithId(entity.assignIdIfNeeded())
suspend fun <E: Entity> serverState.IdBasedEntityManager<E>.deleteEntity(ref: Ref<E>, ignoreNonexistent: Boolean = false) =
    deleteEntity(ref.id, ignoreNonexistent)
suspend fun <E: Entity> serverState.IdBasedEntityManager<E>.modifyEntity(ref: Ref<E>, modify: (E) -> E) =
    modifyEntity(ref.id, modify)


actual val globalState: GlobalState = serverState
