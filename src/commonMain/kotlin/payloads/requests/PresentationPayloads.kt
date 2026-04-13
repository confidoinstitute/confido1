package payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.state.FlowAction
import tools.confido.state.FlowItem

@Serializable
data class CreatePresentation(val name: String = "Presentation")

@Serializable
data class EditPresentation(
    val name: String? = null,
    val items: List<FlowItem>? = null,
    val slideLabels: List<String>? = null,
)

@Serializable
enum class NavigateDirection { NEXT, PREV, GOTO }

@Serializable
data class NavigatePresentation(
    val direction: NavigateDirection,
    /** Target index, only used for GOTO. */
    val index: Int? = null,
)
