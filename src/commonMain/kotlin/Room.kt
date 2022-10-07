package tools.confido.question

import tools.confido.eqid.IdentifiedById
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Temporary simplified model.
enum class RoomAccessibility {
    PUBLIC,
    PRIVATE,
}

@Serializable
class Room(
    @SerialName("_id")
    override val id: String,
    val name: String,
    val accessibility: RoomAccessibility,
    val questions: MutableList<Question> = mutableListOf(),
    val description: String = "",
) : IdentifiedById<String> {
    fun getQuestion(id: String): Question? {
        return questions.find { it.id == id }
    }
}