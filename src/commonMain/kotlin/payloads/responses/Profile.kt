package payloads.responses

@kotlinx.serialization.Serializable
data class EditProfileResult(
    val nickChanged: Boolean = false,
    val emailChanged: Boolean = false,
    val emailError: String? = null,
    val passwordChanged: Boolean = false,
    val passwordError: String? = null,
)
