package components.redesign.questions.dialog

import components.redesign.basic.Dialog
import components.redesign.basic.DialogProps
import components.redesign.basic.Stack
import components.redesign.basic.TextPalette
import components.redesign.forms.Button
import components.redesign.forms.TextButton
import csstype.*
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import utils.except

enum class QuestionPreset(val title: String, val subtitle: String) {
    FORECASTING("Forecasting question", "Correct answer will be known in the future"),
    KNOWLEDGE("Knowledge question", "Correct answer is known in the present"),
    BELIEF("Implicit belief question", "Not intended to be resolved"),
    SENSITIVE("Sensitive question", "Answers are anonymous"),
    NONE("No template", "Create question from scratch"),
}

external interface AddQuestionPresetDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
    var onPreset: ((QuestionPreset) -> Unit)?
}

var AddQuestionPresetDialog = FC<AddQuestionPresetDialogProps> { props ->
    Dialog {
        +props
        title = "Choose question template"
        Stack {
            css {
                backgroundColor = Color("#f2f2f2")
                padding = 15.px
                fontFamily = FontFamily.sansSerif
                color = rgba(0,0,0,0.3)
                fontSize = 14.px
                lineHeight = 17.px
                gap = 12.px

                borderTop = Border(1.px, LineStyle.solid, Color("#CCCCCC"))
            }
            div {
                css {
                    textAlign = TextAlign.center
                }
                +"What kind of question are you planning to ask?"
            }
            QuestionPreset.values().map {preset ->
                TextButton {
                    palette = TextPalette.black
                    css {
                        backgroundColor = Color("#ffffff")
                        padding = 18.px
                        borderRadius = 10.px
                        margin = 0.px
                        fontFamily = FontFamily.sansSerif
                        alignItems = AlignItems.center
                    }
                    onClick = {props.onPreset?.invoke(preset); props.onClose?.invoke() }
                    Stack {
                        div {
                            css {
                                fontWeight = integer(600)
                                fontSize = 24.px
                                lineHeight = 29.px
                            }
                            +preset.title
                        }
                        div {
                            css {
                                fontWeight = integer(400)
                                fontSize = 12.px
                                lineHeight = 15.px
                            }
                            +preset.subtitle
                        }
                    }
                }
            }
            div {
                +"Choosing a template customizes the settings you will see in the next step so that it is quicker and easier to create a question. You can always change all settings later."
            }
        }
    }
}
