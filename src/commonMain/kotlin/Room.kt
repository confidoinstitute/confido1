package tools.confido.question

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
    val id: String,
    val name: String,
    val accessibility: RoomAccessibility,
    val questions: MutableList<Question> = mutableListOf()
) {
    fun getQuestion(id: String): Question? {
        return questions.find { it.id == id }
    }
}