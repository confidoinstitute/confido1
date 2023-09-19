package components.redesign.questions.dialog

import Client
import components.*
import components.redesign.basic.*
import components.redesign.forms.*
import components.rooms.*
import csstype.*
import emotion.react.*
import hooks.*
import kotlinx.datetime.Clock
import payloads.requests.*
import react.*
import react.dom.html.AnchorTarget
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import tools.confido.question.*
import tools.confido.spaces.*

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

val EditQuestionDialog = FC<EditQuestionDialogProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    val isEdit = props.entity != null

    var questionTitle by useState(props.entity?.name ?: "")
    var questionDescription by useState(props.entity?.description ?: "")

    // ANSWER
    var answerSpace: Space? by useState(props.entity?.answerSpace ?: BinarySpace)
    var answerSpaceValid: Boolean by useState(false)
    val answerSpaceReadOnly by useState(props.entity?.answerSpace != null && props.entity?.numPredictions != 0)

    // RESOLUTION
    var questionStatus by useState {
        props.entity?.state ?: QuestionState.OPEN
    }
    var resolution: Value? by useState(props.entity?.resolution)
    var resolutionValid: Boolean by useState(true)
    var scheduleValid: Boolean by useState(true)

    // SCHEDULE
    var customSchedule by useState(room.defaultSchedule == QuestionSchedule())
    var schedule by useState(props.entity?.schedule ?: room.defaultSchedule)

    // ANCHORING
    var groupPredictionVisibility by useState(props.entity?.groupPredictionVisibility ?: GroupPredictionVisibility.ANSWERED)

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
    var isVisible by useState {
        props.entity?.visible ?: true
    }
    var allowComments by useState {
        props.entity?.allowComments ?: true
    }
    var isSensitive by useState {
        props.entity?.sensitive ?: (props.preset == QuestionPreset.SENSITIVE)
    }

    // Validity
    val questionValid = questionTitle.isNotEmpty() && answerSpaceValid && resolutionValid && (scheduleValid || !customSchedule)

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
        open = questionStatus == QuestionState.OPEN,
        resolutionVisible = questionStatus == QuestionState.RESOLVED,
        resolution = resolution,
        annulled = questionStatus == QuestionState.ANNULLED,
        // ANCHORING
        groupPredVisible = groupPredictionVisibility.groupPredVisible,
        groupPredRequirePrediction = groupPredictionVisibility.groupPredRequirePrediction,
        // TERMINOLOGY
        predictionTerminology = predictionTerminology,
        groupTerminology = groupTerminology,
        // VISIBILITY
        visible = isVisible,
        allowComments = allowComments,
        sensitive = isSensitive,
        schedule = if (customSchedule) schedule else null,
    ) else null
    fun submitQuestion() = submit {
        val question = assembleQuestion() ?: return@submit

        if (props.entity == null) {
            Client.sendData("${room.urlPrefix}/questions/add", question, onError = {showError(it)}) {props.onClose?.invoke()}
        } else {
            val editQuestion: EditQuestion = EditQuestionComplete(question)
            Client.sendData("${props.entity?.urlPrefix}/edit", editQuestion, onError = {showError(it)}) {props.onClose?.invoke()}
        }
    }

    Dialog {
        open = props.open
        onClose = props.onClose
        title = if (props.entity != null) "Edit this question" else "Create a question"
        action = if (props.entity != null) "Save" else "Create"
        disabledAction = (stale || !questionValid)
        onAction = { submitQuestion() }

        Form {
            onSubmit = { submitQuestion() }
            FormSection {
                title = "Question"
                FormField {
                    title = "Title"
                    required = true
                    comment = "The question title should cover the main topic. Try and make your question specific and resolvable – so that after the event, everyone will agree on what the outcome is."
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
                    TextInput {
                        placeholder = "Tell others more about the question"
                        value = questionDescription
                        onChange = { e -> questionDescription = e.target.value }
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
                            target = AnchorTarget._blank
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
                title = "Visibility"
                FormSwitch {
                    label = "Question visible"
                    checked = isVisible
                    onChange = { e -> isVisible = e.target.checked }
                    comment = if (isVisible) {
                        "This question will be visible to all room members."
                    } else {
                        "This question will be visible to moderators only."
                    }
                }
                FormSwitch {
                    label = "Allow comments"
                    checked = allowComments
                    onChange = { e -> allowComments = e.target.checked }
                    comment = if (allowComments) {
                        "It will possible for all room members to comment on the question."
                    } else {
                        "No room member can comment on the question."
                    }
                }
                // TODO: Re-enable once sensitivity is implemented fully.
                /*
                if (props.preset != QuestionPreset.SENSITIVE)
                    FormSwitch {
                        label = "Sensitive"
                        comment = if (isSensitive) {
                            "Moderators will not be able to see individual answers."
                        } else {
                            "Moderators will be able to see individual answers."
                        }
                        checked = isSensitive
                        onChange = { e -> isSensitive = e.target.checked }
                    }
                */
            }

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
                    disabled = (stale || !questionValid)
                }
            }
        }
    }
}

