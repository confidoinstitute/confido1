package tools.confido.web.components.presenter

import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.createContext
import react.use
import react.dom.html.ReactHTML.option
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.extensions.ClientExtension
import tools.confido.payloads.responses.WSData
import tools.confido.rooms.RoomPermission
import tools.confido.rooms.TimelineMark
import tools.confido.spaces.BinarySpace
import tools.confido.state.*
import tools.confido.utils.unixNow
import tools.confido.web.components.redesign.forms.FormField
import tools.confido.web.components.redesign.forms.FormSwitch
import tools.confido.web.components.redesign.forms.OptionGroup
import tools.confido.web.components.redesign.forms.Select
import tools.confido.web.components.redesign.questions.predictions.formatUpdateDateTime
import tools.confido.web.components.useMyUser
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

/**
 * Non-null while a control renders inside the presentation flow editor: the world
 * as expected at the edited step (preceding steps simulated — see [ExpectedWorld]).
 * Contract for controls: read subjects via [useExpectedQuestion]; conditions
 * knowable at authoring time (sensitivity, answer space, simulated state, expected
 * marks) keep gating, while runtime-unknowable ones (participant predictions, a
 * resolution entered later) must render permissively with a hint whenever this
 * context is present. Absent (null) everywhere else — live behavior unchanged.
 */
val ExpectedWorldContext = createContext<ExpectedWorld?>(null)

/** [useQuestion] through the expected-world overlay: prefers the amended copy from
 *  [ExpectedWorldContext] and tolerates blank placeholder refs (authored steps
 *  before a question is picked). Real hook — call unconditionally. */
fun useExpectedQuestion(ref: Ref<Question>?): Question? {
    val world = use(ExpectedWorldContext)
    // Blank placeholders and the loop sentinel never resolve to a question —
    // callers gate against ExpectedWorld.loopCandidates for the latter.
    val cleanRef = ref?.takeIf { it.id.isNotBlank() && it != CURRENT_QUESTION }
    val live = useQuestion(cleanRef)
    return cleanRef?.let { world?.questions?.get(it.id) } ?: live
}

/** Controls for the group prediction view: toggle resolution display. */
val GroupPredPC = FC<PresenterControlsProps<GroupPredPV>> { props ->
    val hypothetical = use(ExpectedWorldContext) != null
    val question = useExpectedQuestion(props.view.question)
    if (question == null && !hypothetical) return@FC
    // Whether a resolution will exist when the step runs is unknowable at
    // authoring time — offer the switch permissively there.
    val noResolutionNow = question?.resolution == null
    FormSwitch {
        label = "Show resolution"
        disabled = props.disabled || (noResolutionNow && !hypothetical)
        checked = props.view.showResolution && !(noResolutionNow && !hypothetical)
        onChange = { e -> props.onChange(props.view.copy(showResolution = e.target.checked)) }
        if (noResolutionNow)
            comment = if (hypothetical) "Takes effect only if the question has a resolution by then."
                      else "This question has no resolution yet."
    }
}

/** Controls for the binary histogram view: toggle showing predictor nicknames
 *  (offered only when the controlling user may view individual predictions —
 *  the presenter window normally shares this session, and flow-driven
 *  presentations are moderator-launched anyway). */
val BinaryHistogramPC = FC<PresenterControlsProps<BinaryHistogramPV>> { props ->
    val world = use(ExpectedWorldContext)
    val hypothetical = world != null
    val myUser = useMyUser()
    val question = useExpectedQuestion(props.view.question)
    val rooms = (useSubscription(Topics.rooms()) as? WSData)?.data
    val room = rooms?.values?.firstOrNull { props.view.question in it.questions }
    // Sensitive questions never show names (server sends nameless bins to everyone)
    // — a knowable condition, gated in both modes. The permission gate concerns the
    // live controlling user; the flow editor is only reachable by room moderators.
    // A loop body gates three-valued against the candidate set: no candidate could
    // show names → hide; only some → show with a hint.
    val cand = world?.loopCandidates?.takeIf { props.view.question == CURRENT_QUESTION }
    val candSomeOk = cand == null || cand.any { it.answerSpace is BinarySpace && !it.sensitive }
    val candAllOk = cand == null || cand.all { it.answerSpace is BinarySpace && !it.sensitive }
    if (question?.sensitive != true && candSomeOk &&
        (hypothetical || room?.hasPermission(myUser, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS) == true)) {
        FormSwitch {
            label = "Show nicknames"
            disabled = props.disabled
            checked = props.view.showNames
            onChange = { e -> props.onChange(props.view.copy(showNames = e.target.checked)) }
            comment = if (candAllOk) "Shows who predicted what."
                      else "Applies only to the loop's binary, non-sensitive questions."
        }
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
 *  the full endpoint pickers. [flowMarks] adds marks that don't exist yet but will
 *  be created by earlier steps of the flow being edited (authoring only). */
private fun ChildrenBuilder.compareEndpointField(
    title: String, tp: TimepointRef, marks: List<TimelineMark>, flowMarks: List<ExpectedMark>,
    disabled: Boolean, onSet: (TimepointRef) -> Unit,
) {
    val knownNames = marks.map { it.name }.toSet() + flowMarks.map { it.name }
    val currentKey = when (tp) {
        is NowTP -> "now"
        is MarkTP -> if (tp.name.isBlank()) "" else "mark:${tp.name}"
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
            if (currentKey == "") option { value = ""; +"Select a marker…" }
            option { value = "now"; +"now" }
            marks.forEach { m ->
                option { value = "mark:${m.name}"; +"${m.name} · ${formatUpdateDateTime(m.ts)}" }
            }
            // Not-yet-existing marks from earlier steps of the edited flow (an
            // existing mark of the same name wins the label — it has a date).
            flowMarks.filter { fm -> marks.none { it.name == fm.name } }.forEach { fm ->
                option { value = "mark:${fm.name}"; +"${fm.name} · created at step ${fm.stepIndex + 1}" }
            }
            // A stored marker name no list knows: keep it selectable (and visible)
            // rather than silently switching away from it.
            if (tp is MarkTP && tp.name.isNotBlank() && tp.name !in knownNames)
                option { value = currentKey; +"${tp.name} (unknown marker)" }
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
    val world = use(ExpectedWorldContext)
    val hypothetical = world != null
    val question = useExpectedQuestion(props.view.question)
    val myUser = useMyUser()
    val rooms = (useSubscription(Topics.rooms()) as? WSData)?.data
    // In authoring the question may be unset; the edited room comes from the world.
    val room = rooms?.values?.firstOrNull { props.view.question in it.questions } ?: world?.room
    val marks = ((room?.timelineMarks ?: emptyList()) + (question?.timelineMarks ?: emptyList()))
        .sortedBy { it.ts }.distinctBy { it.name }
    val flowMarks = world?.marks ?: emptyList()
    // The individual modes need VIEW_INDIVIDUAL_PREDICTIONS; the presenter window
    // normally shares this session, so gate on the controlling user's permission
    // instead of hedging in a caveat (in authoring: moderators only anyway).
    // Sensitive questions have no individual modes for anyone (server-enforced).
    // A loop body gates three-valued against the candidate set.
    val cand = world?.loopCandidates?.takeIf { props.view.question == CURRENT_QUESTION }
    val candSomeOk = cand == null || cand.any { it.answerSpace is BinarySpace && !it.sensitive }
    val candAllOk = cand == null || cand.all { it.answerSpace is BinarySpace && !it.sensitive }
    val canViewIndividual = question?.sensitive != true && candSomeOk &&
        (hypothetical || room?.hasPermission(myUser, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS) == true)
    // Binary-ness is knowable — but with no question picked yet, keep the modes visible.
    val binary = question?.answerSpace is BinarySpace || (hypothetical && question == null)

    if (binary && canViewIndividual) {
        FormField {
            title = "View"
            if (!candAllOk) comment = "Individual views apply only to the loop's binary, non-sensitive questions."
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
            comment = "Labels each predictor's movement in the individual views."
        }
    }
    compareEndpointField("Compare from", props.view.tp1, marks, flowMarks, props.disabled) {
        props.onChange(props.view.copy(tp1 = it))
    }
    compareEndpointField("Compare to", props.view.tp2, marks, flowMarks, props.disabled) {
        props.onChange(props.view.copy(tp2 = it))
    }
}

/** Authoring-only controls for the invite link view: the joiners-follow toggle.
 *  Deliberately NOT registered in [view2controls] — whether joiners follow is a
 *  property of the flow step (it decides what the QR code points at), not a knob
 *  to flip mid-show, and its absence keeps the floating panel from auto-expanding
 *  for a plain invite-link view. The flow editor mounts it via pvFlowEditor's
 *  extraControls slot. */
val InviteLinkPC = FC<PresenterControlsProps<InviteLinkPV>> { props ->
    FormSwitch {
        label = "Joiners follow the presentation"
        disabled = props.disabled
        checked = props.view.followPresentation
        onChange = { e -> props.onChange(props.view.copy(followPresentation = e.target.checked)) }
        comment = "Users joining via this link are navigated along with the presentation."
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
