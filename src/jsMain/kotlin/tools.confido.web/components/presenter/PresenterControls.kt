package tools.confido.web.components.presenter

import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.option
import tools.confido.extensions.ClientExtension
import tools.confido.payloads.responses.WSData
import tools.confido.rooms.TimelineMark
import tools.confido.spaces.BinarySpace
import tools.confido.state.*
import tools.confido.utils.unixNow
import tools.confido.web.components.redesign.forms.FormField
import tools.confido.web.components.redesign.forms.FormSwitch
import tools.confido.web.components.redesign.forms.OptionGroup
import tools.confido.web.components.redesign.forms.Select
import tools.confido.web.components.redesign.questions.predictions.formatUpdateDateTime
import tools.confido.web.components.useQuestion
import tools.confido.web.hooks.useSubscription
import web.cssom.pct
import kotlin.math.abs
import kotlin.reflect.KClass

external interface PresenterControlsProps<V: PresenterView> : Props {
    var view: V
    /** Called with an updated copy of the view; the control panel persists it via /presenter/set. */
    var onChange: (PresenterView) -> Unit
    var disabled: Boolean
}

typealias PresenterControlsType = FC<PresenterControlsProps<PresenterView>>

inline fun <reified V: PresenterView> presenterControlsMap(pc: FC<PresenterControlsProps<V>>) =
    V::class as KClass<out PresenterView> to pc.unsafeCast<PresenterControlsType>()

/** Controls for the group prediction view: toggle resolution display. */
val GroupPredPC = FC<PresenterControlsProps<GroupPredPV>> { props ->
    val question = useQuestion(props.view.question) ?: return@FC
    FormSwitch {
        label = "Show resolution"
        disabled = props.disabled || question.resolution == null
        checked = props.view.showResolution && question.resolution != null
        onChange = { e -> props.onChange(props.view.copy(showResolution = e.target.checked)) }
        if (question.resolution == null)
            comment = "This question has no resolution yet."
    }
}

/** Controls for the binary histogram view: toggle showing predictor nicknames. */
val BinaryHistogramPC = FC<PresenterControlsProps<BinaryHistogramPV>> { props ->
    FormSwitch {
        label = "Show nicknames"
        disabled = props.disabled
        checked = props.view.showNames
        onChange = { e -> props.onChange(props.view.copy(showNames = e.target.checked)) }
        comment = "Shows who predicted what. Available only if you may view individual predictions."
    }
}

/** Controls for the multi-question view: toggle group prediction, histogram, and resolution. */
val MultiQuestionPC = FC<PresenterControlsProps<MultiQuestionPV>> { props ->
    FormSwitch {
        label = "Show group predictions"
        disabled = props.disabled
        checked = props.view.showGroupPred
        onChange = { e -> props.onChange(props.view.copy(showGroupPred = e.target.checked)) }
    }
    FormSwitch {
        label = "Show prediction histogram"
        disabled = props.disabled
        checked = props.view.showHistogram
        onChange = { e -> props.onChange(props.view.copy(showHistogram = e.target.checked)) }
        comment = "Histogram of individual predictions (binary questions only)."
    }
    FormSwitch {
        label = "Show resolution"
        disabled = props.disabled
        checked = props.view.showResolution
        onChange = { e -> props.onChange(props.view.copy(showResolution = e.target.checked)) }
    }
}

/** Marker/now selector for one side of a comparison. Graph-picking is not offered
 *  here — there is no timeline to pick from; the question page's compare view has
 *  the full endpoint pickers. */
private fun ChildrenBuilder.compareEndpointField(
    title: String, tp: TimepointRef, marks: List<TimelineMark>,
    disabled: Boolean, onSet: (TimepointRef) -> Unit,
) {
    val currentKey = when (tp) {
        is NowTP -> "now"
        is MarkTP -> "mark:${tp.name}"
        // A fixed instant reads as its coinciding marker when there is one.
        is AtTP -> marks.firstOrNull { it.ts == tp.ts }?.let { "mark:${it.name}" } ?: "at:${tp.ts}"
    }
    FormField {
        this.title = title
        Select {
            value = currentKey
            this.disabled = disabled
            onChange = { e ->
                val v = e.target.value
                when {
                    v == "now" -> onSet(NowTP)
                    v.startsWith("mark:") -> onSet(MarkTP(v.removePrefix("mark:")))
                }
            }
            css { width = 100.pct }
            option { value = "now"; +"now" }
            marks.forEach { m ->
                option { value = "mark:${m.name}"; +"${m.name} · ${formatUpdateDateTime(m.ts)}" }
            }
            // A pinned/picked instant from the question page: keep it selectable
            // (and displayed) rather than forcing a switch away from it.
            if (currentKey.startsWith("at:") && tp is AtTP)
                option { value = currentKey; +formatUpdateDateTime(tp.ts) }
        }
    }
}

/** Controls for the compare view: mode tabs and nicknames (binary questions),
 *  and marker/now selectors for both compared moments. */
val ComparePC = FC<PresenterControlsProps<ComparePV>> { props ->
    val question = useQuestion(props.view.question)
    val rooms = (useSubscription(Topics.rooms()) as? WSData)?.data
    val room = rooms?.values?.firstOrNull { props.view.question in it.questions }
    val marks = ((room?.timelineMarks ?: emptyList()) + (question?.timelineMarks ?: emptyList()))
        .sortedBy { it.ts }.distinctBy { it.name }

    if (question?.answerSpace is BinarySpace) {
        FormField {
            title = "View"
            OptionGroup<CompareViewMode>()() {
                options = listOf(
                    CompareViewMode.DISTRIBUTIONS to "Distributions",
                    CompareViewMode.CHANGES to "Changes",
                    CompareViewMode.SCATTER to "Scatter",
                )
                value = props.view.mode
                disabled = props.disabled
                onChange = { m -> props.onChange(props.view.copy(mode = m)) }
            }
        }
        FormSwitch {
            label = "Show nicknames"
            disabled = props.disabled || props.view.mode == CompareViewMode.DISTRIBUTIONS
            checked = props.view.showNames && props.view.mode != CompareViewMode.DISTRIBUTIONS
            onChange = { e -> props.onChange(props.view.copy(showNames = e.target.checked)) }
            comment = "Labels each predictor's movement in the individual views. " +
                "Available only if the presenting window may view individual predictions."
        }
    }
    compareEndpointField("Compare from", props.view.tp1, marks, props.disabled) {
        props.onChange(props.view.copy(tp1 = it))
    }
    compareEndpointField("Compare to", props.view.tp2, marks, props.disabled) {
        props.onChange(props.view.copy(tp2 = it))
    }
}

private val TIMELINE_WINDOWS = listOf(90 * 86400 to "3m", 30 * 86400 to "1m", 7 * 86400 to "1w", 86400 to "1d")

/** Controls for the timeline view: quick time-window presets (anchored to now),
 *  mirroring the question-page range chips. */
val TimelinePC = FC<PresenterControlsProps<TimelinePV>> { props ->
    val v = props.view
    val now = unixNow()
    val span = if (v.windowStart != null && v.windowEnd != null) v.windowEnd - v.windowStart else null
    val currentKey = when {
        span == null -> "All"
        else -> TIMELINE_WINDOWS.firstOrNull { (secs, _) ->
            abs(span - secs) < secs * 0.05 && abs((v.windowEnd ?: 0) - now) < secs * 0.2
        }?.second ?: "(custom)"
    }
    FormField {
        title = "Time window"
        if (currentKey == "(custom)") comment = "Currently showing the window captured on the question page."
        OptionGroup<String>()() {
            options = (listOf("All" to "All") + TIMELINE_WINDOWS.map { it.second to it.second }) +
                if (currentKey == "(custom)") listOf("(custom)" to "custom") else emptyList()
            value = currentKey
            disabled = props.disabled
            onChange = { key ->
                val secs = TIMELINE_WINDOWS.firstOrNull { it.second == key }?.first
                when {
                    key == "All" -> props.onChange(v.copy(windowStart = null, windowEnd = null))
                    secs != null -> props.onChange(v.copy(windowStart = unixNow() - secs, windowEnd = unixNow()))
                }
            }
        }
    }
}

val view2controls by lazy { mapOf(
    presenterControlsMap(GroupPredPC),
    presenterControlsMap(BinaryHistogramPC),
    presenterControlsMap(tools.confido.web.components.redesign.presentation.PresentationPC),
    presenterControlsMap(MultiQuestionPC),
    presenterControlsMap(ComparePC),
    presenterControlsMap(TimelinePC),
) + ClientExtension.enabled.map { it.registerPresenterControls() }.fold(emptyMap()) { a, b -> a+b } }

/** Look up the control panel component for a given view type (null if the view has no controls). */
fun getPresenterControlsComponent(view: PresenterView): PresenterControlsType? = view2controls[view::class]
