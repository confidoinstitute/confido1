package tools.confido.question

@kotlinx.serialization.Serializable
data class Question(
    val id: String,
    val name: String,
    var visible: Boolean,
)
