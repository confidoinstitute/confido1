package extensions.update_scatter_plot

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import tools.confido.extensions.Extension
import tools.confido.extensions.ExtensionDataKeyWithDefault
import tools.confido.extensions.ExtensionDataType
import tools.confido.extensions.add
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.state.FlowAction
import tools.confido.state.PresenterView
import users.User

// ============================================================
//  Timepoint References
// ============================================================

/**
 * A reference to a point in time, used for specifying the x/y axes
 * of the update scatter plot. Resolved to an actual [Instant] at query time.
 */
@Serializable
sealed class TimepointReference {
    abstract fun describe(): String
}

/** The current moment (latest predictions). */
@Serializable
@SerialName("now")
data object NowTimepoint : TimepointReference() {
    override fun describe() = "now"
}

/** A fixed timestamp. */
@Serializable
@SerialName("timestamp")
data class TimestampTimepoint(val time: Instant) : TimepointReference() {
    override fun describe() = "fixed time"
}

/** Resolved by looking up a named snapshot's timestamp at runtime. */
@Serializable
@SerialName("snapshot")
data class SnapshotTimepoint(
    val name: String,
    /** If set, look up the snapshot on this question's extension data. If null, look up room-wide. */
    val question: Ref<Question>? = null,
) : TimepointReference() {
    override fun describe() = "snapshot \"$name\""
}

// ============================================================
//  Presenter View
// ============================================================

@Serializable
data class UpdatePlotPV(
    val question: Ref<Question>,
    val timepoint1: TimepointReference = NowTimepoint,
    val timepoint2: TimepointReference = NowTimepoint,
): PresenterView() {
    override fun describe() = "Update plot"
}

// ============================================================
//  Flow Action: Create Snapshot
// ============================================================

/** Create (or overwrite) a named snapshot for a question or room. */
@Serializable
@SerialName("createSnapshot")
data class CreateSnapshotAction(
    val name: String = "",
    /** If set, create a question-scoped snapshot. If null, create a room-wide snapshot. */
    val question: Ref<Question>? = null,
) : FlowAction() {
    override fun describe() = if (question != null)
        "Create snapshot \"$name\" (question)"
    else
        "Create snapshot \"$name\" (room-wide)"
}

// ============================================================
//  Request payload for the update plot POST API
// ============================================================

@Serializable
data class UpdatePlotRequest(
    val question: Ref<Question>,
    val timepoint1: TimepointReference = NowTimepoint,
    val timepoint2: TimepointReference = NowTimepoint,
    val referenceQuestion: Ref<Question>? = null,
)

// ============================================================
//  Snapshot data model
// ============================================================

val UpdateReferenceQuestionKey = ExtensionDataKeyWithDefault<Ref<Question>?>("update_reference_question", null)

@Serializable
data class Snapshot(
    val ts: Int,
    val title: String,
    val owner: Ref<User>?,
    val public: Boolean = false,
    val id: String? = null,
)

enum class SnapshotScope { ROOM, QUESTION }
val SnapshotsKey = ExtensionDataKeyWithDefault<List<Snapshot>>("snapshots", emptyList())

// ============================================================
//  Extension interface
// ============================================================

interface UpdateScatterPlotExt: Extension {
    override val extensionId get() = "update_scatter_plot"
    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(UpdatePlotPV::class)
    }
    override fun registerFlowActions(builder: PolymorphicModuleBuilder<FlowAction>) {
        builder.subclass(CreateSnapshotAction::class)
    }
    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "QuestionEDT" -> {
                edt.add(UpdateReferenceQuestionKey)
                edt.add(SnapshotsKey)
            }
            "RoomEDT"-> {
                edt.add(SnapshotsKey)
            }
        }
    }
}
