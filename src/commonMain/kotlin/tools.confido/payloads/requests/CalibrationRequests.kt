package tools.confido.payloads.requests

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.rooms.Room
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.users.User

@Serializable
sealed class CalibrationWho
@Serializable
@SerialName("myself")
data object Myself: CalibrationWho()
@Serializable
@SerialName("everyone")
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
