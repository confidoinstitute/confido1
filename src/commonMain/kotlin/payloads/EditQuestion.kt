package tools.confido.payloads

import kotlinx.serialization.Serializable

@Serializable
enum class EditQuestionField {
    VISIBLE,
    ENABLED,
    PREDICTIONS_VISIBLE,
    RESOLVED,
}

@Serializable
data class EditQuestion (
    val field: EditQuestionField,
    val value: Boolean,
)