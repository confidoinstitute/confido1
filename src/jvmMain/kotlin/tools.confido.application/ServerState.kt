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
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import tools.confido.store.TransientCell
import tools.confido.store.commentData
import tools.confido.store.latestPredictions
import tools.confido.store.predictionData
import tools.confido.store.questionStore
import tools.confido.store.roomStore
import tools.confido.store.userStore
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
    private val _presentationRunStates =
        TransientCell<PersistentMap<Ref<Room>, PresentationRunState>>(persistentMapOf())
    /** Snapshot flow of the run states; topic derivations subscribe here. */
    val presentationRunStatesState: StateFlow<PersistentMap<Ref<Room>, PresentationRunState>>
        get() = _presentationRunStates.state
    private var presentationRunStatesMap by _presentationRunStates::value
    val presentationRunStates: Map<Ref<Room>, PresentationRunState> get() = presentationRunStatesMap

    /** Set or clear (null) a room's presentation run state. Returns the previous state. */
    fun setPresentationRunState(room: Ref<Room>, state: PresentationRunState?): PresentationRunState? {
        val prev = presentationRunStatesMap[room]
        presentationRunStatesMap =
            if (state == null) presentationRunStatesMap.remove(room)
            else presentationRunStatesMap.put(room, state)
        return prev
    }

    val questionRoom: Map<Ref<Question>, Ref<Room>> get() = roomStore.questionRoom
    override val rooms: Map<String, Room> get() = roomStore.asMap
    override val questions: Map<String, Question> get() = questionStore.asMap
    val users: Map<String, User> get() = userStore.asMap





    suspend fun initialize() {
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
        Stores.all.flatMap { it.mongoCollections }.associate { coll ->
            val docClass = coll.collection.documentClass
            coll.namespace.collectionName to { name: String ->
                database.database.getCollection(name, docClass).coroutine.find().toFlow().collect {}
            }
        }
    // initial load from database
    suspend fun load() {
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
        return super.derefNonBlocking(entityType, id)
    }

    override val extensions: List<ServerExtension> get() = ServerExtension.enabled
}
actual val globalState: GlobalState = serverState
