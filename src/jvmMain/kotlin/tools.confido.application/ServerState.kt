@file:Suppress("NOTHING_TO_INLINE")

package tools.confido.state

import com.mongodb.ConnectionString
import com.mongodb.MongoCommandException
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
import kotlinx.serialization.json.Json
import org.bson.Document
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.reactivestreams.KMongo
import rooms.Room
import tools.confido.distributions.*
import tools.confido.extensions.ServerExtension
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.serialization.confidoJSON
import tools.confido.spaces.*
import tools.confido.store.Stores
import tools.confido.store.commentData
import tools.confido.store.latestPredictions
import tools.confido.store.predictionData
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

fun loadConfig(): AppConfig {
    val enabledExtensionIds = getenvList("CONFIDO_EXTENSIONS").toSet()
    return AppConfig(
        devMode = getenvBool("CONFIDO_DEVMODE"),
        demoMode = getenvBool("CONFIDO_DEMO"),
        betaIndicator = getenvBool("CONFIDO_BETA_INDICATOR"),
        featureFlags = FeatureFlag.values().filter{ getenvBool("CONFIDO_FEAT_${it.name}", it in DEFAULT_FEATURE_FLAGS)}.toSet(),
        privacyPolicyUrl = System.getenv("CONFIDO_PRIVACY_POLICY_URL")?.ifEmpty {null},
        enabledExtensionIds = enabledExtensionIds,
        predictionCoalesceInterval = System.getenv("CONFIDO_PREDICTION_COALESCE_INTERVAL")?.toInt() ?: 60,
        // XXX      vvvv-- cannot use confidoJSON here at this would create an initialization cycle
        extraData = Json.decodeFromString(System.getenv("CONFIDO_EXTRA_DATA") ?: "{}"),
        commentSpoiler = getenvBool("CONFIDO_COMMENT_SPOILER"),
        // Roles must be loaded after enabledExtensionIds is known (for extension permission registration)
        // but before appConfig is assigned (since role deserialization depends on appConfig).
        // We pass enabledExtensionIds directly to avoid the circular dependency:
        // appConfig -> loadRoles() -> Extension.enabled -> appConfig
        roles = loadRoles(enabledExtensionIds),
    )
}

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
        fun generate(): String {
            val digits = StringBuilder(LENGTH)
            while (digits.length < LENGTH) {
                generateNonce(LENGTH).forEach { b ->
                    // Bytes are signed; mask before the modulo. Values >= 250 are
                    // discarded so that the remaining range stays a multiple of 10.
                    val v = b.toInt() and 0xFF
                    if (v < 250 && digits.length < LENGTH) digits.append(v % 10)
                }
            }
            return digits.toString()
        }
    }
}

object serverState : GlobalState() {
    // Prediction data lives in the new store layer (tools.confido.store.Predictions);
    // these facade properties keep the read surface unchanged for censors and routes.
    val groupPred: Map<Ref<Question>, Prediction> get() = predictionData.groupPred
    override val predictorCount: Map<Ref<Question>, Int> get() = predictionData.predictorCount.asMap
    override val predictionCount: Map<Ref<Question>, Int> get() = predictionData.predictionCount.asMap
    val userPred: Map<Ref<Question>, Map<Ref<User>, Prediction>> get() = latestPredictions.asMap
    override val commentCount: Map<Ref<Question>, Int> get() = commentData.commentCount.asMap
    //val presenterByUser: MutableMap<Ref<User>, PresenterInfo> = mutableMapOf() // this does not persist after restart
    //val presenterByToken: MutableMap<String, PresenterInfo> = mutableMapOf() // this does not persist after restart

    // Now, for simplicity, serialize all mutations
    val mutationMutex = Mutex()

    val client = KMongo.createClient(ConnectionString(System.getenv("CONFIDO_MONGODB_URL") ?: "mongodb://localhost/?replicaSet=rs01")).coroutine
    val database = client.getDatabase(System.getenv("CONFIDO_DB_NAME") ?: "confido1")

    val shortInviteLinks = mutableMapOf<String, ShortLink>()

    /** Room-scoped presentation run states. In-memory only, lost on server restart. */
    val presentationRunStates: MutableMap<Ref<Room>, PresentationRunState> = mutableMapOf()

    abstract class EntityManager<E: HasId>(val mongoCollection: CoroutineCollection<E>) {
        var isLoading = false
        val updateCallbacks: MutableList<EntityManager<E>.(E, E) -> Unit> = mutableListOf()
        val addCallbacks: MutableList<EntityManager<E>.(E) -> Unit> = mutableListOf()
        val deleteCallbacks: MutableList<EntityManager<E>.(E) -> Unit> = mutableListOf()
        val beforeSaveCallbacks: MutableList<EntityManager<E>.(E?, E) -> E> = mutableListOf()
        val beforeDeleteCallbacks: MutableList<suspend EntityManager<E>.(E) -> Unit> = mutableListOf()

        /** Register a callback that transforms an entity before it is persisted.
         * The callback receives (oldEntity?, newEntity) where oldEntity is null for inserts.
         */
        fun beforeEntitySave(cb: EntityManager<E>.(E?, E) -> E) {
            beforeSaveCallbacks.add(cb)
        }

        /** Register a callback that is called before an entity is deleted.
         *  Can be used for cascade operations, cleanup, or to prevent deletion (by throwing). */
        fun beforeEntityDelete(cb: suspend EntityManager<E>.(E) -> Unit) {
            beforeDeleteCallbacks.add(cb)
        }

        fun applyBeforeSave(old: E?, new: E): E =
            beforeSaveCallbacks.fold(new) { acc, cb -> cb(old, acc) }

        suspend fun notifyBeforeDelete(entity: E) {
            beforeDeleteCallbacks.forEach { it(entity) }
        }
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
                val new = applyBeforeSave(orig, modify(orig))
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
                val merged = applyBeforeSave(orig, merge(orig, new))
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
                notifyBeforeDelete(orig)
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
                notifyBeforeDelete(orig)
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
                val transformed = applyBeforeSave(null, entity)
                when (val sess = coroutineContext.getSession()) {
                    null -> mongoCollection.insertOne(transformed)
                    else -> mongoCollection.insertOne(sess, transformed)
                }
                notifyEntityAdded(transformed)
                return@withMutationLock transformed
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
            // Strip computed fields before persisting to DB (safety net against accidental persistence)
            beforeEntitySave { _, room ->
                room.copy(
                    _effectiveMembers = null,
                    _effectiveQuestions = null,
                    _presentationRunning = null,
                    members = room.members.map {
                        it.copy(implied = false, impliedFrom = null, effectivePermissions = null)
                    }
                )
            }
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
            // Bugfix: originally we created a unique index on email without a null filter.
            // This prevented multiple user entries with null email. And also it had incorrect
            // collation so it wasn't case-insensitive.
            mongoCollection.listIndexes<Document>()
                .toList().find { index ->
                    val isEmailIndex = index.get("key", Document::class.java)
                        ?.containsKey(User::email.name) == true
                    val isUnique = index.getBoolean("unique") ?: false
                    val hasNoPartialFilter = !index.containsKey("partialFilterExpression")

                    isEmailIndex && isUnique && hasNoPartialFilter
                }
                ?.let {
                    try {
                        mongoCollection.dropIndex(it)
                        println("Dropped old email index without partial filter: $it")
                    } catch (e: MongoCommandException) {
                        if (e.errorCode != 27) { // 27 is "index not found"
                            throw e
                        }
                    }
                }
            try {
                // Unique constraint on emails, case insensitive, ignores nulls.
                mongoCollection.ensureUniqueIndex(
                    User::email,
                    indexOptions = IndexOptions()
                        .collation(
                            Collation.builder()
                                .locale("simple")
                                .collationStrength(CollationStrength.SECONDARY)
                                .build()
                        )
                        .partialFilterExpression(
                            Document(User::email.name, Document("\$type", "string"))
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

    object presentationManager : InMemoryEntityManager<Presentation>(database.getCollection("presentations")) {
        init {
            onEntityDeleted { pres ->
                // Clean up run state when presentation is deleted
                presentationRunStates.remove(Ref(pres.room.id))
            }
        }
    }

    object userSessionManager : InMemoryExpiringEntityManager<UserSession>(database.getCollection("userSessions"))
    init {
        roomManager.register()
        questionManager.register()
        userManager.register()
        verificationLinkManager.register()
        passwordResetLinkManager.register()
        userSessionManager.register()
        presentationManager.register()
    }




    suspend fun initialize() {
        managers.values.forEach { it.initialize() }
        additionalManagers.forEach { it.initialize() }
        Stores.all.forEach { it.initialize() }
    }

    /**
     * For each managed collection, a check that strictly decodes every document with
     * the real entity serializers, throwing on the first failure. Keyed by live
     * collection name; the passed argument is the collection to actually read (the
     * migration working copy). Used as the MigrationRunner verify pass
     * (PERSISTENCE_SYNC_REDESIGN.md §6).
     */
    fun collectionVerifiers(): Map<String, suspend (String) -> Unit> =
        ((managers.values + additionalManagers).map { it.mongoCollection } +
                Stores.all.flatMap { it.mongoCollections }).associate { coll ->
            val docClass = coll.collection.documentClass
            coll.namespace.collectionName to { name: String ->
                database.database.getCollection(name, docClass).coroutine.find().toFlow().collect {}
            }
        }
    // initial load from database
    suspend fun load() {
        managers.values.forEach { it.load() }
        additionalManagers.forEach { it.load() }
        Stores.all.forEach { it.load() }
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
        Stores.resident[entityType]?.let { return it.get(id) }
        managers[entityType]?.let { manager ->
            when (manager) {
                is InMemoryEntityManager ->
                    return manager.entityMap[id] as Entity?
                else -> {}
            }
        }
        return super.derefNonBlocking(entityType, id)
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
