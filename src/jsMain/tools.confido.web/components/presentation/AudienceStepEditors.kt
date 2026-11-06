package tools.confido.web.components.presentation

import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.extensions.FlowItemEditorProps
import tools.confido.state.*
import tools.confido.refs.ref
import tools.confido.web.components.presenter.ExpectedWorldContext
import web.cssom.*

/** The edited presentation's audience mode, for step editors (null outside the editor). */
val AudienceModeContext = createContext<AudienceMode?>(null)

val AudienceMode.title get() = when (this) {
    AudienceMode.ONE_AT_A_TIME -> "One question at a time"
    AudienceMode.GUIDED -> "The whole room, guided"
    AudienceMode.FREE -> "The whole room, free"
}

val AudienceMode.explanation get() = when (this) {
    AudienceMode.ONE_AT_A_TIME -> "Questions appear on people's devices when you ask them, and results when you " +
        "reveal them. Best for talks and events."
    AudienceMode.GUIDED -> "Everyone's device follows you from question to question; the questions themselves " +
        "are left as they are. Best for team meetings with an agenda."
    AudienceMode.FREE -> "Only the projector follows you; people browse and update whatever they like. " +
        "Best for small, informal discussions."
}

val PresentationEndBehavior.title get() = when (this) {
    PresentationEndBehavior.KEEP_COVERED -> "Keep what we covered"
    PresentationEndBehavior.RESTORE -> "Put the room back as it was"
}

val PresentationEndBehavior.explanation get() = when (this) {
    PresentationEndBehavior.KEEP_COVERED -> "Questions you revealed stay visible with their results, so people can " +
        "come back to them. Anything you didn't get to stays hidden."
    PresentationEndBehavior.RESTORE -> "Every question goes back to how it was before the presentation started."
}

external interface PresentationSettingsFormProps : Props {
    var mode: AudienceMode
    var endBehavior: PresentationEndBehavior
    var onChange: (AudienceMode, PresentationEndBehavior) -> Unit
}

/** The audience settings, shared by the create dialog and the editor's settings dialog. */
val PresentationSettingsForm = FC<PresentationSettingsFormProps> { props ->
    fun <T> ChildrenBuilder.choices(title: String, comment: String?, options: List<T>, value: T,
                                    label: (T) -> String, explain: (T) -> String, set: (T) -> Unit) {
        FormSection {
            this.title = title
            comment?.let { c ->
                div { css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#777777") }; +c }
            }
            options.forEach { opt ->
                BinaryFormField {
                    key = opt.toString()
                    input = RadioInput.create {
                        checked = opt == value
                        onChange = { set(opt) }
                    }
                    div {
                        div { css { fontWeight = integer(600) }; +label(opt) }
                        div { css { fontSize = 13.px; color = Color("#666666"); marginTop = 2.px }; +explain(opt) }
                    }
                }
            }
        }
    }

    choices("What do people see on their own devices?", "The projector always shows what you choose.",
        AudienceMode.entries, props.mode, { it.title }, { it.explanation }) { props.onChange(it, props.endBehavior) }
    if (props.mode == AudienceMode.ONE_AT_A_TIME)
        choices("When the presentation ends", "Also applies when you stop it before the end.",
            PresentationEndBehavior.entries, props.endBehavior, { it.title }, { it.explanation }) {
            props.onChange(props.mode, it)
        }
}

private fun ChildrenBuilder.whatHappens(lines: List<Pair<String, String>>) {
    div {
        css {
            fontFamily = sansSerif; fontSize = 13.px; lineHeight = 18.px; color = Color("#555555")
            backgroundColor = Color("#f6f4fb"); borderRadius = 5.px; padding = Padding(8.px, 10.px)
        }
        lines.forEach { (where, what) ->
            div { css { marginTop = 2.px }; +"$where: "; +what }
        }
    }
}

private fun ChildrenBuilder.modeNote(mode: AudienceMode) {
    div {
        css { fontFamily = sansSerif; fontSize = 12.px; color = Color("#999999") }
        +"This presentation: ${mode.title.lowercase()} (change in the presentation's audience settings)."
    }
}

private fun FlowItemEditorProps.selectionField(header: String, selection: QuestionSelection,
                                              update: (QuestionSelection) -> FlowAction, cb: ChildrenBuilder,
                                              questions: List<tools.confido.question.Question>, inLoop: Boolean) {
    with(cb) {
        FieldLabel { +"Questions" }
        QuestionSelectionPicker {
            this.selection = selection
            roomQuestions = questions
            this.header = header
            currentQuestionOption = inLoop
            onUpdate = { f -> onChange(update(f(selection))) }
        }
    }
}

/** Editor for [AskAudienceAction]: the questions, how to ask and when to reveal, and the
 *  resulting clicks — per the audience mode. */
val AskAudienceEditor = FC<FlowItemEditorProps> { props ->
    val questions = useRoomQuestions()
    val world = use(ExpectedWorldContext)
    val mode = use(AudienceModeContext) ?: AudienceMode.GUIDED
    val action = props.action as? AskAudienceAction ?: return@FC
    val inLoop = world?.loopCandidates != null
    props.selectionField("Ask about", action.selection, { action.copy(selection = it) }, this, questions, inLoop)

    val selected = if (inLoop) emptyList() else action.selection.resolve(questions)
    // Dynamic selections may grow later, so offer the several-questions choices for them too.
    val several = !inLoop && (selected.size > 1 || action.selection.all || action.selection.tags.isNotEmpty())
    val oneByOne = several && action.oneByOne
    // "At the end" only differs from "right after" when asking one by one.
    val reveal = if (!oneByOne && action.reveal == RevealTiming.AT_END) RevealTiming.RIGHT_AFTER else action.reveal

    if (several) {
        FieldLabel { +"How to ask them" }
        OptionGroup<Boolean>()() {
            options = listOf(true to "One by one", false to "All at once")
            value = action.oneByOne
            onChange = { props.onChange(action.copy(oneByOne = it)) }
        }
    }
    FieldLabel { +"Reveal the answers" }
    OptionGroup<RevealTiming>()() {
        options = if (oneByOne) listOf(RevealTiming.RIGHT_AFTER to "After each question",
                                       RevealTiming.AT_END to "All together at the end",
                                       RevealTiming.LATER to "Later")
                  else listOf(RevealTiming.RIGHT_AFTER to "Right after", RevealTiming.LATER to "Later")
        value = reveal
        onChange = { props.onChange(action.copy(reveal = it)) }
    }
    if (reveal == RevealTiming.LATER) {
        div {
            css { fontFamily = sansSerif; fontSize = 12.px; color = Color("#999999") }
            +"Add a \"Reveal answers\" step where you want the results — e.g. after a slide or a discussion."
        }
    } else revealSwitches(mode, action.showResolution, action.stopAnswers, selected,
        { props.onChange(action.copy(showResolution = it)) }, { props.onChange(action.copy(stopAnswers = it)) })

    // The click-by-click preview: what pressing Next will do, in order.
    val n = selected.size
    val revealing = reveal != RevealTiming.LATER
    if (n > 1 && oneByOne) {
        val clicks = n * (if (reveal == RevealTiming.RIGHT_AFTER) 2 else 1) + (if (reveal == RevealTiming.AT_END) 1 else 0)
        div {
            css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#555555") }
            +"This step takes $clicks clicks: "
            +when (reveal) {
                RevealTiming.RIGHT_AFTER -> "question 1, its results, question 2, its results, …"
                RevealTiming.AT_END -> "question 1, question 2, … and then all results together."
                RevealTiming.LATER -> "one per question."
            }
        }
    } else if (revealing) div {
        css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#555555") }
        +"This step takes 2 clicks: the question${if (several) "s" else ""}, then the results."
    }

    val target = when {
        several && !oneByOne -> "the room, where these questions are"
        several -> "each question in turn"
        else -> "the question"
    }
    whatHappens(listOf(
        "Projector" to (if (several && !oneByOne) "the questions side by side, with a live count of answers."
                        else "the question, with a live count of answers."),
        "Devices" to when (mode) {
            AudienceMode.ONE_AT_A_TIME -> "people are taken to $target, which appears and can be answered " +
                "(the group answer stays hidden until it is revealed)."
            AudienceMode.GUIDED -> "people are taken to $target. Nothing about the questions is changed."
            AudienceMode.FREE -> "nothing changes — people browse on their own."
        },
    ))
    modeNote(mode)
}

/** The reveal switches shared by Ask (when it reveals) and Reveal. */
private fun ChildrenBuilder.revealSwitches(mode: AudienceMode, showResolution: Boolean, stopAnswers: Boolean,
                                           selected: List<tools.confido.question.Question>,
                                           setResolution: (Boolean) -> Unit, setStop: (Boolean) -> Unit) {
    val withAnswer = selected.count { it.resolution != null }
    FormSwitch {
        label = "Also show the correct answer"
        checked = showResolution
        onChange = { e -> setResolution(e.target.checked) }
        comment = when {
            selected.isEmpty() -> "Where a correct answer has been entered."
            withAnswer == 0 -> "None of these questions has a correct answer entered yet — you can still add one before the presentation."
            withAnswer < selected.size -> "$withAnswer of ${selected.size} questions have a correct answer entered."
            else -> "Shown where a correct answer has been entered."
        }
    }
    if (mode == AudienceMode.ONE_AT_A_TIME) FormSwitch {
        label = "Stop taking answers"
        checked = stopAnswers || showResolution
        disabled = showResolution
        onChange = { e -> setStop(e.target.checked) }
        comment = if (showResolution) "Showing the correct answer always stops answering."
                  else "Keeps the results fair once everyone has seen them. Turn off to let latecomers still answer."
    }
}

/** Editor for [RevealAnswersAction]: the questions, the two switches, and what will happen. */
val RevealAnswersEditor = FC<FlowItemEditorProps> { props ->
    val questions = useRoomQuestions()
    val world = use(ExpectedWorldContext)
    val mode = use(AudienceModeContext) ?: AudienceMode.GUIDED
    val action = props.action as? RevealAnswersAction ?: return@FC
    val inLoop = world?.loopCandidates != null
    props.selectionField("Reveal answers for", action.selection, { action.copy(selection = it) }, this, questions, inLoop)

    val selected = if (inLoop) world?.loopCandidates.orEmpty() else action.selection.resolve(questions)
    revealSwitches(mode, action.showResolution, action.stopAnswers, selected,
        { props.onChange(action.copy(showResolution = it)) }, { props.onChange(action.copy(stopAnswers = it)) })

    val several = !inLoop && selected.size > 1
    whatHappens(listOf(
        "Projector" to ((if (several) "the group answers" else "the group answer") +
            (if (action.showResolution) ", with the correct answer." else ".")),
        "Devices" to when (mode) {
            AudienceMode.ONE_AT_A_TIME -> "people are taken to ${if (several) "the room" else "the question"} " +
                "and see the group answer" + (if (action.showResolution) " and the correct answer" else "") +
                (if (action.stopAnswers || action.showResolution) "; answering is closed." else "; they can still change their answer.")
            AudienceMode.GUIDED -> "people are taken to ${if (several) "the room" else "the question"}. " +
                "Whether they see results there depends on the questions' own settings."
            AudienceMode.FREE -> "nothing changes."
        },
    ))
    modeNote(mode)
}

external interface CreatePresentationDialogProps : BaseDialogProps {
    var room: tools.confido.rooms.Room
}

/** Creates the room's presentation, asking the one question its author knows the answer to up front. */
val CreatePresentationDialog = dialogStateWrapper(FC<CreatePresentationDialogProps> { props ->
    val navigate = react.router.useNavigate()
    val submitLock = tools.confido.web.hooks.useCoroutineLock()
    var mode by useState(AudienceMode.ONE_AT_A_TIME)
    var end by useState(PresentationEndBehavior.KEEP_COVERED)
    Dialog {
        open = props.open
        onClose = { props.onClose?.invoke() }
        title = "Create presentation"
        action = "Create"
        disabledAction = submitLock.running
        onAction = {
            submitLock {
                tools.confido.web.Client.call(tools.confido.api.Api.presentationCreate(props.room.ref),
                    tools.confido.payloads.requests.CreatePresentation(audienceMode = mode, endBehavior = end))
                props.onClose?.invoke()
                navigate("${props.room.urlPrefix}/presentation")
            }
        }
        PresentationSettingsForm {
            this.mode = mode
            endBehavior = end
            onChange = { m, e -> mode = m; end = e }
        }
        div {
            css { fontFamily = sansSerif; fontSize = 12.px; color = Color("#999999"); padding = Padding(0.px, 20.px, 10.px) }
            +"You can change this later in the presentation's audience settings."
        }
    }
})
