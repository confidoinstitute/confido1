package tools.confido.web.components.questions.dialog

import tools.confido.web.components.useDisconnected
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.responses.WSData
import tools.confido.state.Topics
import tools.confido.web.Client
import tools.confido.web.components.*
import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.*
import tools.confido.web.components.rooms.AttributionSection
import tools.confido.web.components.rooms.RoomContext
import tools.confido.web.components.rooms.label
import web.cssom.*
import emotion.react.*
import tools.confido.web.hooks.*
import kotlinx.datetime.Clock
import tools.confido.payloads.requests.*
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import tools.confido.api.Api
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionContextPlace
import tools.confido.question.*
import tools.confido.rooms.AttributionLevel
import tools.confido.rooms.AttributionPolicy
import tools.confido.refs.ref
import tools.confido.spaces.*
import web.html.ButtonType
import web.html.submit
import web.window.WindowTarget
import web.window._blank

external interface EditQuestionDialogProps : EditEntityDialogProps<Question> {
    var preset: QuestionPreset
}

internal enum class QuestionType {
    BINARY,
    NUMERIC,
    DATE,
}

internal val Space.questionType: QuestionType
    get() =
        when(this) {
            BinarySpace -> QuestionType.BINARY
            is NumericSpace -> if (representsDays) QuestionType.DATE else QuestionType.NUMERIC
        }

val EditQuestionDialogExtensions = FC<EditQuestionDialogProps> { props->
    ClientExtension.forEach { it.editQuestionDialogExtra(props, this) }
}

val EditQuestionDialog = FC<EditQuestionDialogProps> { props ->
    val room = use(RoomContext)!!
    val extContext = ClientExtension.getContextValues(ExtensionContextPlace.EDIT_QUESTION_DIALOG)

    val isEdit = props.entity != null

    var questionTitle by useState(props.entity?.name ?: "")
    var questionDescription by useState(props.entity?.description ?: "")

    // ANSWER
    var answerSpace: Space? by useState(props.entity?.answerSpace ?: BinarySpace)
    var answerSpaceValid: Boolean by useState(false)
    // Derived, not captured in state: the counts subscription may deliver after the
    // first render, and a predicted question must lock its answer space then too.
    // (Real hook — called unconditionally, before the short-circuiting check.)
    val entityCounts = useQuestionCounts(props.entity?.ref)
    val answerSpaceReadOnly = props.entity?.answerSpace != null && entityCounts.predictions != 0

    // RESOLUTION
    var questionStatus by useState {
        props.entity?.state ?: QuestionState.OPEN
    }
    var resolution: Value? by useState(props.entity?.resolution)
    var resolutionValid: Boolean by useState(true)
    var scheduleValid: Boolean by useState(true)

    // SCHEDULE
    var customSchedule by useState(props.entity?.let { it.schedule != null }
        ?: (room.defaultSchedule == QuestionSchedule()))
    var schedule by useState(props.entity?.schedule ?: room.defaultSchedule)

    // ANCHORING
    var groupPredictionVisibility by useState(props.entity?.groupPredictionVisibility ?: GroupPredictionVisibility.ANSWERED)
    var commentVisibility by useState(props.entity?.commentVisibility ?: CommentVisibility.EVERYONE)

    // LANGUAGE
    var predictionTerminology by useState {
        props.entity?.predictionTerminology ?: when(props.preset) {
            // TODO correctly assign terminology
            else -> PredictionTerminology.PREDICTION
        }
    }
    var groupTerminology by useState {
        props.entity?.groupTerminology ?: when(props.preset) {
            // TODO correctly assign terminology
            else -> GroupTerminology.GROUP
        }
    }

    // VISIBILITY
    var allowComments by useState {
        props.entity?.allowComments ?: true
    }
    // null = follow the room default; the SENSITIVE preset seeds an AGGREGATE_ONLY
    // override (the preset's whole point is the strict promise, room default or not).
    var customAttribution by useState<AttributionPolicy?> {
        props.entity?.attribution
            ?: if (props.preset == QuestionPreset.SENSITIVE) AttributionPolicy(AttributionLevel.AGGREGATE_ONLY) else null
    }
    var attributionValid by useState(true)

    // TAGS
    var tags by useState { props.entity?.tags ?: emptySet() }

    // Compute tag suggestions from all questions in this room (the room questions
    // slice; already subscribed by the room page, so this read is cache-warm)
    val roomQuestions = (useSubscription(Topics.roomQuestions(room.ref)) as? WSData)?.data ?: emptyMap()
    val tagSuggestions: List<String> = useMemo(roomQuestions) {
        room.questions.mapNotNull { roomQuestions[it.id] }.flatMap { it.tags }.distinct().sorted()
    }

    // Validity
    // Switching back to "follow the room setting" is itself a policy weakening when
    // the room default is below this question's promise floor (derived, not state:
    // the AttributionSection error path only covers explicit overrides).
    val inheritViolation = customAttribution == null &&
        props.entity?.attributionFloor?.let { room.effectiveDefaultAttribution.revealsMoreThan(it) } ?: false
    val questionValid = questionTitle.isNotEmpty() && answerSpaceValid && resolutionValid &&
        (scheduleValid || !customSchedule) && attributionValid && !inheritViolation

    val submit = useCoroutineLock()
    fun assembleQuestion() = if (questionValid) Question(
        id = props.entity?.id ?: "",
        stateHistory = props.entity?.stateHistory ?: emptyList(),
        // QUESTION
        name = questionTitle,
        description = questionDescription,
        // ANSWER
        answerSpace = answerSpace!!,
        // RESOLUTION
        resolution = resolution,
        // ANCHORING
        groupPredVisible = groupPredictionVisibility.groupPredVisible,
        groupPredRequirePrediction = groupPredictionVisibility.groupPredRequirePrediction,
        commentVisibility = commentVisibility,
        // TERMINOLOGY
        predictionTerminology = predictionTerminology,
        groupTerminology = groupTerminology,
        // VISIBILITY
        allowComments = allowComments,
        attribution = customAttribution,
        schedule = if (customSchedule) schedule else null,
        scheduleStatus = QuestionScheduleStatus(if (customSchedule) schedule else room.defaultSchedule),
        // TAGS
        tags = tags,
    ).withState(questionStatus).let{ q->
        var curQ = q
        ClientExtension.forEach { curQ = it.assembleQuestion(curQ, extContext[it.extensionId]!!.component1()) }
        curQ
    } else null

    fun submitQuestion() = submit {
        val question = assembleQuestion() ?: return@submit

        val entity = props.entity
        if (entity == null) {
            Client.call(Api.questionAdd(room.ref), question)
        } else {
            Client.call(Api.questionEdit(entity.ref), EditQuestionComplete(question))
        }
        props.onClose?.invoke()
    }

    ClientExtension.contexts[ExtensionContextPlace.EDIT_QUESTION_DIALOG]!!.Provider {
        value = extContext
    Dialog {
        open = props.open
        onClose = props.onClose
        title = if (props.entity != null) "Edit this question" else "Create a question"
        action = if (props.entity != null) "Save" else "Create"
        disabledAction = !questionValid
        onAction = { submitQuestion() }

        Form {
            onSubmit = { submitQuestion() }
            FormSection {
                title = "Question"
                FormField {
                    title = "Title"
                    required = true
                    commentNode = Fragment.create {
                        +"Try and make your question specific and resolvable – so that after the event, everyone will agree on what the outcome is. "
                        a {
                            css {
                                color = Globals.inherit
                                fontWeight = integer(600)
                                textDecoration = TextDecoration.underline
                            }
                            href = "https://confido.institute/encyclopedia/what-makes-a-good-forecasting-question.html"
                            target = WindowTarget._blank
                            +"More on how to write a good question."
                        }
                    }
                    TextInput {
                        placeholder = "Enter the question title"
                        value = questionTitle
                        required = true
                        onChange = { e -> questionTitle = e.target.value }
                    }
                }
                FormField {
                    title = "Description"
                    comment = "The description should contain all resolution criteria."
                    MarkdownEditor {
                        placeholder = "Tell others more about the question"
                        value = questionDescription
                        onChange = {  questionDescription = it }
                    }
                }
            }

            EditQuestionDialogSpace {
                space = answerSpace
                readOnly = answerSpaceReadOnly
                onChange = { answerSpace = it; answerSpaceValid = true }
                onError = {
                    answerSpace = null
                    answerSpaceValid = false
                }
            }


            // TODO properly
            if (props.preset == QuestionPreset.KNOWLEDGE || props.preset == QuestionPreset.NONE) {
                FormSection {
                    title = "Resolution"
                    EditQuestionDialogResolution {
                        this.preset = props.preset
                        state = questionStatus
                        onStateChange = { questionStatus = it }
                        this.terminology = predictionTerminology

                        space = answerSpace
                        value = resolution
                        valid = resolutionValid
                        this.onChange = {
                            resolution = it
                            resolutionValid = true
                        }
                        this.onError = {
                            resolution = null
                            resolutionValid = false
                        }
                    }
                }
            }

            FormSection {
                title = "Schedule"
                EditQuestionDialogSchedule {
                    this.preset = props.preset
                    this.schedule = if (customSchedule) schedule else room.defaultSchedule
                    this.showOpen = (isEdit || questionStatus in setOf(QuestionState.CLOSED, QuestionState.OPEN))
                    this.showClose = (isEdit || questionStatus in setOf(QuestionState.CLOSED, QuestionState.OPEN))
                    this.showResolve = (isEdit || questionStatus in setOf(QuestionState.CLOSED, QuestionState.OPEN)) &&
                            preset != QuestionPreset.BELIEF
                    this.showScore = preset != QuestionPreset.BELIEF
                    this.openPlaceholder = if (isEdit && props.entity?.state == QuestionState.OPEN) "already open"
                                            else if (questionStatus == QuestionState.OPEN) "immediately"
                                            else "manually"
                    this.onChange = { newSched, isError ->
                        schedule = newSched
                        scheduleValid = !isError
                        customSchedule = true

                        val now = Clock.System.now()
                        if (questionStatus == QuestionState.CLOSED && newSched.open != null && now >= newSched.open && (newSched.close == null || now < newSched.close)) {
                            questionStatus = QuestionState.OPEN
                        }
                        if (questionStatus == QuestionState.OPEN && newSched.open != null && now < newSched.open) {
                            questionStatus = QuestionState.CLOSED
                        }
                    }
                }
                div {
                    css {
                        fontSize = 12.px
                        color =  Color("#AAAAAA")
                    }
                    if (room.defaultSchedule == QuestionSchedule()) {
                        +"You can also set a default schedule for the whole room in "
                        a {
                            href = "/rooms/${room.id}/edit?schedule=1"
                            target = WindowTarget._blank
                            +"room settings"
                        }
                        +"."
                    } else if (customSchedule) {
                        +"Using a custom schedule for this question. "
                        a {
                            +"Use default schedule from the room."
                            href="#"
                            onClick = {
                                customSchedule = false
                                schedule = room.defaultSchedule
                            }
                        }
                    } else {
                        +"Using a default schedule configured for the room. If it changes, this question's schedule will change accordingly. You can also set a custom schedule for this question here."
                    }
                }
            }

            if (props.preset != QuestionPreset.SENSITIVE)
                FormSection {
                    title = "Anchoring"
                    RadioGroup<GroupPredictionVisibility>()() {
                        title = "Group answer visible to"
                        options = listOf(
                            GroupPredictionVisibility.EVERYONE to "all room members",
                            GroupPredictionVisibility.ANSWERED to "those who answered",
                            GroupPredictionVisibility.MODERATOR_ONLY to "moderators only",
                        )
                        value = groupPredictionVisibility
                        onChange = { visibility -> groupPredictionVisibility = visibility }
                    }
                }

            if (props.preset == QuestionPreset.NONE)
                FormSection {
                    title = "Terminology"
                    RadioGroup<PredictionTerminology>()() {
                        title = "Call answers"
                        options = PredictionTerminology.values().map {
                            it to "\"${it.plural}\""
                        }
                        value = predictionTerminology
                        onChange = {term -> predictionTerminology = term}
                    }
                    RadioGroup<GroupTerminology>()() {
                        title = "Call group"
                        options = GroupTerminology.values().map {
                            it to "\"${it.term}\""
                        }
                        value = groupTerminology
                        onChange = {term -> groupTerminology = term}
                    }
                }

                FormSection {
                    title = "Comments"
                    FormSwitch {
                        label = "Allow comments"
                        checked = allowComments
                        onChange = { e -> allowComments = e.target.checked }
                        comment = if (allowComments) {
                            "Comments are enabled for this question."
                        } else {
                            "Comments are disabled for this question."
                        }
                    }
                    if (allowComments) {
                        RadioGroup<CommentVisibility>()() {
                            title = "Comments visible to"
                            options = listOf(
                                CommentVisibility.EVERYONE to "all room members",
                                CommentVisibility.ANSWERED to "those who answered",
                                CommentVisibility.MODERATOR_ONLY to "moderators only",
                            )
                            value = commentVisibility
                            onChange = { visibility -> commentVisibility = visibility }
                        }
                    }
                run {
                    // The promise floor (server-maintained) is exactly what the ratchet
                    // compares against; the section reports violations before submit.
                    val ratchetFloor = props.entity?.attributionFloor
                    FormSwitch {
                        label = "Custom attribution"
                        comment =
                            if (customAttribution == null)
                                "Following the room setting: ${room.effectiveDefaultAttribution.level.label.lowercase()}."
                            else "This question overrides the room's attribution setting."
                        checked = customAttribution != null
                        onChange = { e ->
                            customAttribution = if (e.target.checked) room.effectiveDefaultAttribution else null
                            attributionValid = true
                        }
                    }
                    customAttribution?.let { policy ->
                        AttributionSection {
                            value = policy
                            persisted = props.entity?.effectiveAttributionIn(room)
                            this.ratchetFloor = ratchetFloor
                            ratchetWhat = "this question already has activity"
                            onChange = { new, isError ->
                                customAttribution = new
                                attributionValid = !isError
                            }
                        }
                    } ?: run {
                        // Inheriting is also a choice: if the room default is weaker
                        // than this question's promise floor, say so.
                        if (inheritViolation) {
                            div {
                                css { fontSize = 12.px; color = Color("#F35454") }
                                +("The room setting is weaker than the promise this question's " +
                                    "existing activity was made under; it cannot switch back to it.")
                            }
                        }
                    }
                }
            }
            FormSection {
                title = "Tags"
                FormField {
                    title = "Tags"
                    comment = "Tags help organize and filter questions within this room."
                    TagInput {
                        value = tags
                        onChange = { newTags -> tags = newTags }
                        suggestions = tagSuggestions
                        placeholder = "Add tags..."
                    }
                }
            }

            EditQuestionDialogExtensions { +props }


            Stack {
                Button {
                    type = ButtonType.submit
                    css {
                        margin = Margin(20.px, 20.px, 10.px)
                        fontWeight = integer(500)
                    }
                    if (props.entity != null)
                        +"Save"
                    else
                        +"Create question"
                    disabled = useDisconnected() || !questionValid
                }
            }
        }
    }
    }
}
