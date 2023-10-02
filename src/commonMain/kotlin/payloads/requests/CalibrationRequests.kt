package payloads.requests

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.question.Question
import tools.confido.refs.Ref
import users.User

@Serializable
sealed class CalibrationWho
@Serializable
object Myself: CalibrationWho()
@Serializable
object Everyone: CalibrationWho()
//data class UserSet(val users: Set<Ref<User>>) : CalibrationWho()

@Serializable
data class CalibrationRequest(
    val rooms: Set<Ref<Room>>? = null,
    val questions: Set<Ref<Question>>? = null,
    val excludeQuestions: Set<Ref<Question>> = emptySet(),
    val fromTime: Instant? = null,
    val toTime: Instant? = null,
    val who: CalibrationWho = Myself,
    val includeHiddenResolutions: Boolean = false,
    val includeNumeric: Boolean = true,
)