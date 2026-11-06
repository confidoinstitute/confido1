package tools.confido.payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.state.FlowAction
import tools.confido.state.FlowItem
import tools.confido.state.AudienceMode
import tools.confido.state.PresentationEndBehavior

@Serializable
data class CreatePresentation(
    val name: String = "Presentation",
    // API default keeps the pre-setting behavior; the create dialog proposes ONE_AT_A_TIME.
    val audienceMode: AudienceMode = AudienceMode.GUIDED,
    val endBehavior: PresentationEndBehavior = PresentationEndBehavior.KEEP_COVERED,
)

@Serializable
data class EditPresentation(
    val name: String? = null,
    val items: List<FlowItem>? = null,
    val slideLabels: List<String>? = null,
    val audienceMode: AudienceMode? = null,
    val endBehavior: PresentationEndBehavior? = null,
)

@Serializable
enum class NavigateDirection { NEXT, PREV, GOTO }

@Serializable
data class NavigatePresentation(
    val direction: NavigateDirection,
    /** Target index, only used for GOTO. */
    val index: Int? = null,
)
