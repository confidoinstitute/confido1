package components.redesign.questions.dialog

import components.ValueEntry
import components.redesign.forms.*
import kotlinx.datetime.*
import kotlinx.js.jso
import react.*
import tools.confido.question.PredictionTerminology
import tools.confido.question.QuestionState
import tools.confido.spaces.*
import tools.confido.utils.*

internal external interface EditQuestionDialogResolutionProps : Props {
    var preset: QuestionPreset?
    var state: QuestionState
    /** Optional. In case this is null, the status section will not be shown. */
    var onStateChange: ((QuestionState) -> Unit)?
    var valueRequired: Boolean?
    var space: Space?
    var value: Value?
    var valid: Boolean
    var onChange: (Value?) -> Unit
    var onError: () -> Unit
    var terminology: PredictionTerminology?
}

internal val EditQuestionDialogResolution = FC<EditQuestionDialogResolutionProps> { props ->
    val valueRequired = props.valueRequired ?: false
    var resolution by useState(props.value)
    val space = props.space
    val terminology = props.terminology ?: PredictionTerminology.ESTIMATE

    useEffect(space?.identify(), resolution?.identify()) {
        console.log("Resolution part update")
        multiletNotNull(resolution, space) { oldResolution, newSpace ->
            val oldSpace = oldResolution.space
            if (oldSpace == newSpace) {
                // NOP
            } else if (oldSpace is NumericSpace && newSpace is NumericSpace
                        && oldResolution is NumericValue
                        && oldSpace.representsDays == newSpace.representsDays
                        && oldResolution.value in newSpace.range) {
                val newValue = oldResolution.copy(space = newSpace)
                resolution = newValue
                props.onChange(newValue)
            } else {
                // Resolution is incompatible with new space, clear it.
                resolution = null
                props.onChange(null)
            }
        }
    }

    if (props.onStateChange != null) {
        FormField {
            title = "Status"
            OptionGroup<QuestionState>()() {
                options = buildList {
                    add(QuestionState.DRAFT to "Draft")
                    add(QuestionState.OPEN to "Open")
                    add(QuestionState.CLOSED to "Closed")
                    if (props.preset == QuestionPreset.NONE) {
                        add(QuestionState.RESOLVED to "Resolved")
                        add(QuestionState.CANCELLED to "Cancelled")
                    }
                }
                defaultValue = QuestionState.OPEN
                value = props.state
                onChange = { props.onStateChange?.invoke(it) }
            }
            comment = when (props.state) {
                QuestionState.DRAFT -> "The question is visible only to moderators. ${terminology.plural.capFirst()} cannot be added."
                QuestionState.OPEN -> "Forecasters can add new and update existing ${terminology.plural}."
                QuestionState.CLOSED -> "Room members cannot add new ${terminology.plural} or update them."
                QuestionState.RESOLVED -> "The question has a known resolution (correct answer), which is made public. Forecasters cannot add or update ${terminology.plural}."
                QuestionState.CANCELLED -> "It is not (and won't be) possible to resolve the question because of unexpected circumstances. The question is closed, ${terminology.plural} cannot be added or updated and there is no correct answer."
            }
        }
    }
    // In open and closed states, it may make sense to prepare a resolution for resolving in the future.
    // For annulled questions, this is unlikely to be useful, so we hide the resolution section
    // as the resolution is not shown.
    if (props.state != QuestionState.CANCELLED && space != null) {
        InputFormField<Value, SpaceValueEntryProps>()() {
            inputComponent = SpaceValueEntry
            inputProps = jso {
                this.space = space
                value = resolution
                onChange = { v, err -> resolution = v; props.onChange(v) }
            }
            title = "Correct answer"
            required = valueRequired

            if (props.value != null) {
                if (props.state != QuestionState.RESOLVED)
                    comment = "Will be shown to room members when the status is changed to Resolved."
                else
                    comment = "Will be shown to room members."
            } else if (!valueRequired) {
                comment = "This question has no correct answer, thus nothing will be shown."
            }
        }
    }
}
