package components.redesign.rooms.import

import components.redesign.basic.*
import components.redesign.rooms.RoomContext
import payloads.requests.ColumnMapping
import payloads.requests.CsvParseResponse
import payloads.requests.ImportQuestionOptions
import react.*
import react.dom.html.ReactHTML.div
import web.cssom.*
import emotion.react.css

enum class WizardStep(val label: String) {
    UPLOAD("Upload"),
    MAP_COLUMNS("Map Columns"),
    REVIEW("Review & Import"),
}

/** Shared state across wizard steps. */
data class ImportWizardState(
    val parseResponse: CsvParseResponse? = null,
    val hasHeader: Boolean = true,
    val columnMappings: List<ColumnMapping> = emptyList(),
    val questionType: QuestionType = QuestionType.BINARY,
    val defaultUnit: String = "",
    val defaultDecimals: Int = 1,
    val options: ImportQuestionOptions = ImportQuestionOptions(),
)

external interface StepperProps : Props {
    var steps: List<WizardStep>
    var currentStep: Int
}

/** Step progress indicator: ①——②——③ with labels. */
val Stepper = FC<StepperProps> { props ->
    Stack {
        direction = FlexDirection.row
        css {
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            gap = 0.px
            padding = Padding(20.px, 30.px)
        }
        props.steps.forEachIndexed { index, step ->
            if (index > 0) {
                // Connector line
                div {
                    css {
                        height = 2.px
                        width = 60.px
                        backgroundColor = if (index <= props.currentStep) Color("#6319FF") else Color("#CCCCCC")
                    }
                }
            }
            // Step circle + label
            Stack {
                css {
                    alignItems = AlignItems.center
                    gap = 6.px
                }
                div {
                    css {
                        width = 32.px
                        height = 32.px
                        borderRadius = 50.pct
                        display = Display.flex
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                        fontFamily = sansSerif
                        fontSize = 14.px
                        fontWeight = integer(600)
                        if (index < props.currentStep) {
                            // Completed
                            backgroundColor = Color("#6319FF")
                            color = Color("#FFFFFF")
                        } else if (index == props.currentStep) {
                            // Active
                            backgroundColor = Color("#6319FF")
                            color = Color("#FFFFFF")
                        } else {
                            // Pending
                            backgroundColor = Color("#E0E0E0")
                            color = Color("#888888")
                        }
                    }
                    if (index < props.currentStep) {
                        +"\u2713" // checkmark
                    } else {
                        +"${index + 1}"
                    }
                }
                div {
                    css {
                        fontFamily = sansSerif
                        fontSize = 12.px
                        lineHeight = 15.px
                        color = if (index <= props.currentStep) Color("#333333") else Color("#999999")
                        fontWeight = if (index == props.currentStep) integer(600) else integer(400)
                        whiteSpace = WhiteSpace.nowrap
                    }
                    +step.label
                }
            }
        }
    }
}

val ImportWizard = FC<Props> {
    val room = use(RoomContext)!!

    var currentStep by useState(0)
    var wizardState by useState(ImportWizardState())

    PageHeader {
        title = "Import Questions"
        navigateBack = room.urlPrefix
        action = ""
        allowSidebar = true
    }

    Stepper {
        steps = WizardStep.entries
        this.currentStep = currentStep
    }

    // Render current step
    when (WizardStep.entries[currentStep]) {
        WizardStep.UPLOAD -> {
            ImportStepUpload {
                this.room = room
                this.onParsed = { response ->
                    wizardState = wizardState.copy(
                        parseResponse = response,
                        hasHeader = response.detectedHeader,
                        columnMappings = response.suggestedMappings
                            ?: List(response.rows.firstOrNull()?.size ?: 0) { ColumnMapping.SKIP },
                    )
                    currentStep = 1
                }
            }
        }
        WizardStep.MAP_COLUMNS -> {
            ImportStepMapColumns {
                this.state = wizardState
                this.onStateChange = { wizardState = it }
                this.onBack = { currentStep = 0 }
                this.onContinue = { currentStep = 2 }
            }
        }
        WizardStep.REVIEW -> {
            ImportStepReview {
                this.room = room
                this.state = wizardState
                this.onStateChange = { wizardState = it }
                this.onBack = { currentStep = 1 }
            }
        }
    }
}
