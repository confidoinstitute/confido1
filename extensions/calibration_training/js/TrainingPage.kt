package extensions.calibration_training

import Client
import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.Button
import components.redesign.forms.TextButton
import components.redesign.layout.LayoutMode
import components.redesign.questions.predictions.BinaryPredInput
import components.redesign.questions.predictions.BinaryPrediction
import components.redesign.questions.predictions.MyPredictionDescription
import components.redesign.questions.predictions.binaryColors
import components.redesign.questions.predictions.descriptionClass
import components.redesign.questions.predictions.yesNoColored
import emotion.css.ClassName
import emotion.react.css
import hooks.useCoroutineLock
import io.ktor.client.call.*
import io.ktor.http.*
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ol
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import tools.confido.distributions.BinaryDistribution
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.BinaryValue
import tools.confido.utils.formatPercent
import web.cssom.*
import web.cssom.FontStyle.Companion.italic
import web.cssom.TextAlign.Companion.center

/**
 * Training session state.
 */
enum class TrainingViewState {
    LOADING,
    WELCOME,
    ANSWERING,
    FEEDBACK,
    COMPLETED,
    ERROR,
}

/**
 * Main training page component.
 */
val TrainingPage = FC<Props> {
    val (appState, stale) = use(AppStateContext)!!
    
    var viewState by useState(TrainingViewState.LOADING)
    var currentQuestion by useState<TrainingQuestion?>(null)
    var progress by useState(TrainingProgress(0, 0))
    var userPrediction by useState<Double?>(null)
    var feedback by useState<AnswerFeedbackResponse?>(null)
    var errorMessage by useState<String?>(null)
    var statsDialogOpen by useState(false)
    var resetDialogOpen by useState(false)
    var hasStartedTraining by useState(false)
    
    val submitLock = useCoroutineLock()
    
    // Fetch next question
    val fetchNextQuestion: suspend (Boolean) -> Unit = { skipWelcome ->
        viewState = TrainingViewState.LOADING
        userPrediction = null
        feedback = null
        
        Client.send("/api/training/next", method = HttpMethod.Get, onError = { err ->
            errorMessage = err
            viewState = TrainingViewState.ERROR
        }) {
            val response: NextQuestionResponse = body()
            progress = response.progress
            if (response.question != null) {
                // Show welcome screen for first-time users who haven't started
                if (response.progress.answered == 0 && !hasStartedTraining && !skipWelcome) {
                    currentQuestion = response.question
                    viewState = TrainingViewState.WELCOME
                } else {
                    currentQuestion = response.question
                    viewState = TrainingViewState.ANSWERING
                }
            } else {
                viewState = TrainingViewState.COMPLETED
            }
        }
    }
    
    // Submit answer
    val submitAnswer: suspend () -> Unit = {
        val prediction = userPrediction
        val question = currentQuestion
        if (prediction != null && question != null) {
            Client.sendData(
                "/api/training/submit",
                SubmitAnswerRequest(question.id, prediction),
                onError = { err ->
                    errorMessage = err
                    viewState = TrainingViewState.ERROR
                }
            ) {
                val response: AnswerFeedbackResponse = body()
                feedback = response
                viewState = TrainingViewState.FEEDBACK
            }
        }
    }
    
    // Load first question on mount
    useEffectOnce {
        submitLock { fetchNextQuestion(false) }
    }
    
    // Start training (called from welcome screen)
    val startTraining: () -> Unit = {
        hasStartedTraining = true
        viewState = TrainingViewState.ANSWERING
    }
    
    PageHeader {
        title = "Calibration training"
        navigateBack = "/"
        allowSidebar = true
        
        rightExtra = div.create {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                gap = 8.px
            }
            // Stats button - opens stats dialog
            TextButton {
                onClick = { statsDialogOpen = true }
                +"Stats"
            }
        }
    }
    
    // Dialogs
    TrainingStatsDialog {
        open = statsDialogOpen
        onClose = { statsDialogOpen = false }
    }
    
    ConfirmResetDialog {
        open = resetDialogOpen
        onClose = { resetDialogOpen = false }
        onConfirm = {
            // Refresh to start over
            hasStartedTraining = false
            submitLock { fetchNextQuestion(false) }
        }
    }
    
    LayoutWidthWrapper {
            css {
                paddingTop = 20.px
                paddingBottom = 40.px
                display = Display.flex
                flexDirection = FlexDirection.column
                flexGrow = number(1.0)
            }
            
            // Main content area - takes up available space
            div {
                css {
                    flexGrow = number(1.0)
                }
            
            when (viewState) {
                TrainingViewState.LOADING -> {
                    div {
                        css {
                            textAlign = TextAlign.center
                            padding = 40.px
                            color = Color("#666")
                        }
                        +"Loading..."
                    }
                }
                
                TrainingViewState.WELCOME -> {
                    TrainingWelcomeView {
                        this.onStart = startTraining
                    }
                }
                
                TrainingViewState.ANSWERING -> {
                    currentQuestion?.let { question ->
                        TrainingQuestionView {
                            this.question = question
                            this.prediction = userPrediction
                            this.onPredictionChange = { userPrediction = it }
                            this.onSubmit = { submitLock { submitAnswer() } }
                            this.onSkip = { submitLock { fetchNextQuestion(true) } }
                            this.disabled = submitLock.running
                        }
                    }
                }
                
                TrainingViewState.FEEDBACK -> {
                    feedback?.let { fb ->
                        TrainingFeedbackView {
                            this.feedback = fb
                            this.onNext = { submitLock { fetchNextQuestion(true) } }
                            this.disabled = submitLock.running
                        }
                    }
                }
                
                TrainingViewState.COMPLETED -> {
                    TrainingCompletedView {
                        this.progress = progress
                        this.onReset = { resetDialogOpen = true }
                        this.onViewStats = { statsDialogOpen = true }
                    }
                }
                
                TrainingViewState.ERROR -> {
                    div {
                        css {
                            textAlign = TextAlign.center
                            padding = 40.px
                            color = Color("#c00")
                        }
                        +"Error: ${errorMessage ?: "Unknown error"}"
                        div {
                            css { marginTop = 20.px }
                            Button {
                                onClick = { submitLock { fetchNextQuestion(true) } }
                                +"Try again"
                            }
                        }
                    }
                }
            }
            } // end main content div
            
    }
    // Progress indicator - positioned at bottom left
    if (progress.answered > 0) {
        div {
            css {
                fontSize = 16.px
                color = Color("#666")
                margin = Margin(20.px, 15.px)
            }
            +"${progress.answered} question${if (progress.answered > 1) "s" else ""} answered"
        }
    }
}

/**
 * Props for question view.
 */
external interface TrainingQuestionViewProps : Props {
    var question: TrainingQuestion
    var prediction: Double?
    var onPredictionChange: (Double) -> Unit
    var onSubmit: () -> Unit
    var onSkip: () -> Unit
    var disabled: Boolean?
}

val QuestionText  = MobileSidePad.withStyleLM { props, layoutMode ->
    fontFamily = sansSerif
    fontWeight = integer(800)
    fontSize = if (layoutMode == LayoutMode.PHONE) 24.px else 34.px
    lineHeight = 105.pct
    color = Color("#000000")
    flexGrow = number(1.0)
}
/**
 * Question display with prediction input.
 */
val TrainingQuestionView = FC<TrainingQuestionViewProps> { props ->
    val question = props.question
    val prediction = props.prediction
    val disabled = props.disabled ?: false
    
    Stack {
        css { gap = 24.px }
        
        // Question text
        QuestionText {
            +question.text
        }
        
        // Prediction input
        Stack {
            div {
                css {
                    backgroundColor = Color("#fff")
                }
                BinaryPredInput {
                    space = BinarySpace
                    dist = prediction?.let { BinaryDistribution(it) }
                    onChange = { newDist, _ ->
                        val binaryDist = newDist as? BinaryDistribution
                        binaryDist?.let { props.onPredictionChange(it.yesProb) }
                    }
                    this.disabled = disabled
                }
            }
            // no gap here, MyPredictionDescription has its own padding
            if (prediction != null) {
                MyPredictionDescription {
                    this.dist = BinaryDistribution(prediction)
                }
            }
        }
        
        // Buttons
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.center
                gap = 16.px
                marginTop = 8.px
            }
            
            TextButton {
                onClick = { props.onSkip() }
                this.disabled = disabled
                +"Skip question"
            }
            
            Button {
                onClick = { props.onSubmit() }
                this.disabled = disabled || prediction == null
                +"Submit answer"
            }
        }
    }
}

/**
 * Props for feedback view.
 */
external interface TrainingFeedbackViewProps : Props {
    var feedback: AnswerFeedbackResponse
    var onNext: () -> Unit
    var disabled: Boolean?
}

/**
 * Feedback screen after submitting an answer.
 */
val TrainingFeedbackView = FC<TrainingFeedbackViewProps> { props ->
    val fb = props.feedback
    val disabled = props.disabled ?: false
    
    val correctAnswer = fb.question.answer
    val userAnswer = fb.userPrediction >= 0.5
    val correctSide = if (correctAnswer) "Yes" else "No"
    val userSide = if (fb.userPrediction >= 0.5) "Yes" else "No"
    val userConfidence = if (fb.userPrediction >= 0.5)
        fb.userPrediction
    else 
        (1 - fb.userPrediction)
    
    Stack {
        css { gap = 16.px }
        QuestionText {
            +fb.question.text
            +" "
            span {
                css {
                    color = binaryColors[correctAnswer]
                }
                +correctSide
                +"."
            }
        }
        if (!fb.question.comment.isNullOrBlank())
            div{
                css { fontStyle = italic}
                +fb.question.comment
            }

        Stack {
            div {
                css {
                    backgroundColor = Color("#fff")
                }
                BinaryPrediction {
                    space = BinarySpace
                    dist = BinaryDistribution(fb.userPrediction)
                    resolution = BinaryValue(correctAnswer)
                    baseHeight = 100.px
                    // There are more elements on the feedback page (e.g. calibration gauge) so leave more space
                    // so that even on mobile one does not need to scroll
                }
            }
            // no gap here, MyPredictionDescription has its own padding
            div {
                css(descriptionClass) {}
                +"You were "
                ReactHTML.b {
                    css { this.color = binaryColors[userAnswer] }
                    +"${formatPercent(userConfidence, space = false, decimals = if (fb.userPrediction >= 0.99 && fb.userPrediction < 1) 2 else 0)} "
                }
                +"confident the answer would be "
                yesNoColored(userAnswer)
                +"."
                br{}
                +"The correct answer is "
                yesNoColored(correctAnswer)
                +"."
            }
        }

        // Bucket accuracy gauge
        // Show only when we have enough data
        if (fb.bucketFeedback.totalInBucket >= 5) {
            BucketAccuracyGauge {
                bucketName = fb.bucketFeedback.bucketName
                correct = fb.bucketFeedback.correctInBucket
                total = fb.bucketFeedback.totalInBucket
                targetAccuracy = fb.bucketFeedback.targetAccuracy
            }
        } else {
            div {
                css {
                    fontStyle = FontStyle.italic
                    textAlign = center
                }
                +"You'll see your calibration after you answer more questions at this confidence level."
            }
        }
        
        // Next button
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.center
                marginTop = 16.px
            }
            
            Button {
                onClick = { props.onNext() }
                this.disabled = disabled
                +"Next question"
            }
        }
    }
}

/**
 * Props for bucket accuracy gauge.
 */
external interface BucketAccuracyGaugeProps : Props {
    var bucketName: String
    var correct: Int
    var total: Int
    var targetAccuracy: Double
}

/**
 * Horizontal gauge showing accuracy in a confidence bucket.
 */
val BucketAccuracyGauge = FC<BucketAccuracyGaugeProps> { props ->
    val accuracy = if (props.total > 0) props.correct.toDouble() / props.total else 0.0
    val targetPercent = (props.targetAccuracy * 100).toInt()
    
    div {
        css {
            padding = Padding(16.px, 20.px)
            backgroundColor = Color("#f5f5f5")
            borderRadius = 8.px
        }
        
        div {
            css {
                fontSize = 14.px
                color = Color("#666")
                marginBottom = 12.px
            }
            +"Your accuracy at ${props.bucketName} confidence:"
        }
        
        // Gauge container
        div {
            css {
                position = Position.relative
                height = 24.px
                backgroundColor = Color("#e0e0e0")
                borderRadius = 4.px
                overflow = Overflow.hidden
            }
            
            // Filled portion
            div {
                css {
                    position = Position.absolute
                    top = 0.px
                    left = 0.px
                    bottom = 0.px
                    width = (accuracy * 100).pct
                    backgroundColor = when {
                        props.total == 0 -> Color("#9e9e9e")
                        accuracy >= props.targetAccuracy - 0.05 && accuracy <= props.targetAccuracy + 0.05 -> Color("#4caf50")
                        accuracy < props.targetAccuracy -> Color("#ff9800")
                        else -> Color("#2196f3")
                    }
                    transition = Transition(ident("width"), 0.3.s, TransitionTimingFunction.easeOut)
                }
            }
            
            // Target marker
            div {
                css {
                    position = Position.absolute
                    top = 0.px
                    bottom = 0.px
                    left = (props.targetAccuracy * 100).pct
                    width = 2.px
                    backgroundColor = Color("#333")
                    transform = translatex((-50).pct)
                }
            }
        }
        
        // Labels
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.spaceBetween
                marginTop = 8.px
                fontSize = 12.px
                color = Color("#666")
            }
            
            div {
                if (props.total > 0) {
                    +"${props.correct}/${props.total} (${(accuracy * 100).toInt()}%)"
                } else {
                    +"No data yet"
                }
            }
            
            div {
                +"Target: $targetPercent%"
            }
        }
    }
}

/**
 * Props for welcome view.
 */
external interface TrainingWelcomeViewProps : Props {
    var onStart: () -> Unit
}

/**
 * Welcome screen shown to first-time users.
 */
val TrainingWelcomeView = FC<TrainingWelcomeViewProps> { props ->
    Stack {
        css {
            alignItems = AlignItems.center
            textAlign = TextAlign.center
            padding = Padding(20.px, 20.px)
            gap = 16.px
        }
        
        // Icon
        div {
            css {
                fontSize = 48.px
                marginBottom = 8.px
            }
            TrainingIcon {
                size = 64
                color = "#666"
            }
        }
        
        // Title
        div {
            css {
                fontSize = 28.px
                fontWeight = integer(700)
                color = Color("#333")
            }
            +"Welcome to Calibration Training"
        }
        
        // Description
        div {
            css {
                fontSize = 16.px
                lineHeight = 1.6.em
                color = Color("#555")
                maxWidth = 500.px
            }
            p{+"Calibration training helps you get better at understanding and expressing how sure you are of something. "}
            p{+"You'll answer a series of yes/no trivia questions by giving your confidence level."}
        }
        
        // How it works section
        div {
            css {
                backgroundColor = Color("#f8f9fa")
                borderRadius = 12.px
                padding = Padding(20.px, 24.px)
                maxWidth = 500.px
                width = 100.pct
                textAlign = TextAlign.left
            }
            
            div {
                css {
                    fontSize = 14.px
                    fontWeight = integer(600)
                    color = Color("#333")
                    marginBottom = 12.px
                }
                +"How it works:"
            }

            ol {
                css {
                    fontSize = 14.px
                    color = Color("#555")
                    lineHeight = 1.5.em
                    margin = 0.px
                    paddingLeft = 20.px
                    "li::marker" {
                        fontWeight = integer(700)
                    }
                    li {  marginBottom = 8.px  }
                }

                li {
                    +"Read each question, estimate whether you think the answer is more likely \"Yes\" or \"No\" and how certain you are about it."
                }

                li {
                    +"After answering, you'll see if you were right and how your calibration is developing."
                }

                li {
                    +"Good calibration means when you say 70%, you're right about 70% of the time."
                }
                
                li {
                    +"It's not a knowledge contest. Knowing the answer for sure is not better than being uncertain. The improtant thing is knowing how (un)sure you are."
                }
                
                li {
                    +"Don't look the answers up. Use only your own knowledge, guesses, inferences, logic, intuitions."
                }
            }
        }
        
        // Start button
        div {
            css {
                marginTop = 8.px
            }
            Button {
                onClick = { props.onStart() }
                +"Start Training"
            }
        }
    }
}

/**
 * Props for completed view.
 */
external interface TrainingCompletedViewProps : Props {
    var progress: TrainingProgress
    var onReset: () -> Unit
    var onViewStats: () -> Unit
}

/**
 * View shown when all questions have been answered.
 */
val TrainingCompletedView = FC<TrainingCompletedViewProps> { props ->
    Stack {
        css {
            alignItems = AlignItems.center
            textAlign = TextAlign.center
            padding = 40.px
        }
        
        div {
            css {
                fontSize = 24.px
                fontWeight = integer(600)
                marginBottom = 16.px
            }
            +"All questions completed!"
        }
        
        div {
            css {
                fontSize = 16.px
                color = Color("#666")
                marginBottom = 24.px
            }
            +"You've answered all ${props.progress.answered} questions. Check your calibration stats to see how you did."
        }
        
        Stack {
            direction = FlexDirection.row
            css { gap = 16.px }
            
            TextButton {
                onClick = { props.onReset() }
                +"Reset progress"
            }
            
            Button {
                onClick = { props.onViewStats() }
                +"View stats"
            }
        }
    }
}
