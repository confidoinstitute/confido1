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
data object Myself: CalibrationWho()
@Serializable
data object Everyone: CalibrationWho()
//data class UserSet(val users: Set<Ref<User>>) : CalibrationWho()

@Serializable
data class CalibrationRequest(
    val who: CalibrationWho = Myself,
    val rooms: Set<Ref<Room>>? = null,
    val questions: Set<Ref<Question>>? = null,
    val excludeQuestions: Set<Ref<Question>> = emptySet(),
    val fromTime: Instant? = null,
    val toTime: Instant? = null,
    val includeHiddenResolutions: Boolean = false,
    val includeNumeric: Boolean = true,
) {
    fun identify() = "$who:${rooms?.joinToString(","){it.id}}:${questions?.joinToString(",") { it.id }}:" +
                     "${excludeQuestions?.joinToString(","){it.id}}:$fromTime:$toTime:$includeNumeric:$includeHiddenResolutions"

}
