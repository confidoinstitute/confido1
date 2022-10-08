package tools.confido.state

import com.mongodb.DuplicateKeyException
import com.mongodb.client.model.ReplaceOptions
import org.litote.kmongo.coroutine.*
import org.litote.kmongo.reactivestreams.KMongo
import rooms.Room
import tools.confido.question.Question
import tools.confido.refs.*
import users.User


class DuplicateIdException : Exception() {}


class ServerGlobalState : GlobalState() {
    override val rooms:  MutableMap<String, Room> = mutableMapOf()
    override val questions:  MutableMap<String, Question> = mutableMapOf()
    override val users:  MutableMap<String, User> = mutableMapOf()

    val client = KMongo.createClient().coroutine
    val database = client.getDatabase(System.getenv("CONFIDO_DB_NAME") ?: "confido1")
    val questionsColl = database.getCollection<Question>("questions")
    val usersColl = database.getCollection<User>("users")
    val roomsColl = database.getCollection<Room>("rooms")

    suspend fun <T: Entity> loadEntityMap(coll: CoroutineCollection<T>) : Map<String, T> =
        coll.find().toList().associateBy { question -> question.id }.toMap()

    // initial load from database
    suspend fun load() {
        questions.putAll(loadEntityMap(questionsColl))
        rooms.putAll(loadEntityMap(roomsColl))
        users.putAll(loadEntityMap(usersColl))
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

    suspend inline fun <reified T: ServerImmediateDerefEntity>
    updateEntity(new: T, compare: T? = null,
                upsert: Boolean = false,
                merge: (T,T)->T = { orig, new -> new }, ) : T{
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
        map[new.id] = merged
        coll.replaceOne(merged, ReplaceOptions())
        return merged
    }

    suspend inline fun <reified T: ServerImmediateDerefEntity>
    modifyEntity(ref: Ref<T>, modify: (T)->T): T {
        val map = getMap<T>()
        val coll = getCollection<T>()
        val orig = map[ref.id] ?: throw NoSuchElementException()
        check(orig.id == ref.id)
        val new = modify(orig)
        map[ref.id] = new
        coll.replaceOne(new, ReplaceOptions())
        return new
    }

    suspend inline fun <reified T: ServerImmediateDerefEntity>
    insertEntity(entity: T, forceId: Boolean = false) : T {
        if (!entity.id.isEmpty() && !forceId) throw IllegalArgumentException()
        val new = entity.assignIdIfNeeded()
        val map = getMap<T>()
        val coll = getCollection<T>()
        if (map.containsKey(new.id)) throw DuplicateIdException()
        map[entity.id] = new
        coll.insertOne(new)
        return new
    }


}


val serverState = ServerGlobalState()
actual val globalState: GlobalState = serverState