package components.redesign.questions.dialog

import Client
import components.*
import components.redesign.basic.*
import components.redesign.forms.*
import components.rooms.*
import csstype.*
import emotion.react.*
import hooks.*
import payloads.requests.*
import react.*
import react.dom.html.ButtonType
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

internal enum class QuestionStatus {
    OPEN,
    CLOSED,
    RESOLVED,
}

val EditQuestionDialog = FC<EditQuestionDialogProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    var questionTitle by useState(props.entity?.name ?: "")
    var questionDescription by useState(props.entity?.description ?: "")

    // ANSWER
    var answerSpace: Space? by useState(props.entity?.answerSpace ?: BinarySpace)
    var answerSpaceValid: Boolean by useState(false)

    // RESOLUTION
    var questionStatus by useState {
        props.entity?.let {
            when {
                it.resolved && it.resolutionVisible -> QuestionStatus.RESOLVED
                it.open -> QuestionStatus.OPEN
                else -> QuestionStatus.CLOSED
            }
        } ?: QuestionStatus.OPEN
    }
    var resolution: Value? by useState(props.entity?.resolution)
    var resolutionValid: Boolean by useState(false)

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
    val questionValid = questionTitle.isNotEmpty() && answerSpaceValid && resolutionValid

    val submit = useCoroutineLock()
    fun submitQuestion() = submit {
        if (!questionValid) return@submit
        val question = tools.confido.question.Question(
            id = props.entity?.id ?: "",
            // QUESTION
            name = questionTitle,
            description = questionDescription,
            // ANSWER
            answerSpace = answerSpace!!,
            // RESOLUTION
            open = questionStatus == QuestionStatus.OPEN,
            resolutionVisible = questionStatus == QuestionStatus.RESOLVED,
            resolution = resolution,
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
        )

        if (props.entity == null) {
            Client.sendData("${room.urlPrefix}/questions/add", question, onError = {showError?.invoke(it)}) {props.onClose?.invoke()}
        } else {
            val editQuestion: EditQuestion = EditQuestionComplete(question)
            Client.sendData("${props.entity?.urlPrefix}/edit", editQuestion, onError = {showError?.invoke(it)}) {props.onClose?.invoke()}
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
                onChange = { answerSpace = it; answerSpaceValid = true }
                onError = {
                    answerSpace = null
                    answerSpaceValid = false
                }
            }

            EditQuestionDialogResolution {
                this.preset = props.preset
                status = questionStatus
                onStatusChange = {questionStatus = it}

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

