package tools.confido.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.refs.Entity
import tools.confido.refs.Ref
import tools.confido.refs.ServerImmediateDerefEntity
import tools.confido.utils.HasUrlPrefix
import tools.confido.utils.generateId

/**
 * A presentation flow associated with a room. Contains an ordered list of
 * flow items (slides, actions, presenter views, notes) that can be stepped
 * through during a live session.
 */
@Serializable
data class Presentation(
    @SerialName("_id") override val id: String = "",
    val room: Ref<Room>,
    val name: String = "Presentation",
    val items: List<FlowItem> = emptyList(),
    /** Total number of slide images from uploaded PDF. */
    val numSlides: Int = 0,
    /** User-editable labels for each slide, indexed by slide number. Defaults to "Slide 1", etc. */
    val slideLabels: List<String> = emptyList(),
    /** Random token required in slide image URLs as basic access protection. */
    val fileToken: String = generateId(),
) : ServerImmediateDerefEntity, HasUrlPrefix {
    override val urlPrefix get() = "/presentations/$id"

    /** Build the URL path for a slide image. */
    fun slideUrl(slideIndex: Int): String {
        val num = (slideIndex + 1).toString().padStart(4, '0')
        return "/presentations/$id/$fileToken/slides/$num.png"
    }
}

/**
 * A single item in a presentation flow. Items form a flat ordered list
 * that is stepped through sequentially during a presentation run.
 */
@Serializable
sealed class FlowItem {
    /** Unique ID within the presentation, used for DnD ordering and action tracking. */
    abstract val id: String
}

/** A slide from the uploaded PDF. */
@Serializable
@SerialName("slide")
data class SlideItem(
    override val id: String = "",
    /** Zero-based index into the presentation's slide images. */
    val slideIndex: Int,
) : FlowItem()

/** Show a specific presenter view (e.g. group predictions, invite link QR code). */
@Serializable
@SerialName("presenterView")
data class PresenterViewItem(
    override val id: String = "",
    val view: PresenterView,
) : FlowItem()

/** Execute one or more server-side actions (e.g. change question state). */
@Serializable
@SerialName("action")
data class ActionItem(
    override val id: String = "",
    val action: FlowAction,
) : FlowItem()

/** A presenter note, shown only on the controller (not on the projector). */
@Serializable
@SerialName("note")
data class NoteItem(
    override val id: String = "",
    val text: String,
    /** If true, shown prominently as a warning/reminder. */
    val warning: Boolean = false,
) : FlowItem()

/**
 * Server-side run state for a presentation. Maintained in memory (not persisted),
 * scoped to the room. Sent to moderator clients via the presentation WebSocket.
 */
@Serializable
data class PresentationRunState(
    val currentIndex: Int = 0,
    /** The effective presenter view to display (derived from the current item). */
    val currentView: PresenterView? = null,
    /** IDs of action items that have been executed in this run (to avoid re-execution). */
    val executedActionIds: Set<String> = emptySet(),
    /** ID of the action that was executed on the most recent navigation step (null if none). */
    val lastExecutedId: String? = null,
)

/**
 * Combined data sent over the presentation WebSocket endpoint.
 */
@Serializable
data class PresentationWSData(
    val presentation: Presentation,
    /** Null when the presentation is not running. */
    val runState: PresentationRunState? = null,
)
