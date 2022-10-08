package tools.confido.state

import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import rooms.Room
import tools.confido.question.Question
import tools.confido.refs.*
import users.User


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

}


val serverState = ServerGlobalState()
actual val globalState: GlobalState = serverState