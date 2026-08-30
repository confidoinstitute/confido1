package tools.confido.web.components.redesign.questions.dialog

import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.forms.*
import web.cssom.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.router.dom.Link

enum class QuestionPreset(val title: String, val subtitle: String, val emoji: String) {
    FORECASTING("Forecasting question", "Correct answer will be known in the future", "\uD83D\uDD2E"),
    KNOWLEDGE("Knowledge question", "Correct answer is known in the present", "\uD83D\uDCD6"),
    BELIEF("Implicit belief question", "Not intended to be resolved", "\uD83D\uDCAD"),
    SENSITIVE("Sensitive question", "Answers are anonymous", "\uD83D\uDD12"),
    NONE("No template", "Create question from scratch", "");
}

external interface AddQuestionPresetDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
    var onPreset: ((QuestionPreset) -> Unit)?
    /** When set, offer a CSV import option navigating to this (router-relative) path. */
    var importHref: String?
}

private val PresetButton = Button.withStyle {
    padding = 18.px
    borderRadius = 10.px
    margin = 0.px
    alignItems = AlignItems.center
}

private external interface PresetRowContentProps : Props {
    var emoji: String
    var title: String
    var subtitle: String
}

private val PresetRowContent = FC<PresetRowContentProps> { props ->
    Stack {
        direction = FlexDirection.row
        css {
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            gap = 17.px
        }
        span {
            css {
                fontSize = 30.px
                lineHeight = 36.px
            }
            +props.emoji
        }

        Stack {
            div {
                css {
                    fontWeight = integer(600)
                    fontSize = 24.px
                    lineHeight = 29.px
                }
                +props.title
            }
            div {
                css {
                    fontWeight = integer(400)
                    fontSize = 12.px
                    lineHeight = 15.px
                }
                +props.subtitle
            }
        }
    }
}

val AddQuestionPresetDialog = FC<AddQuestionPresetDialogProps> { props ->
    Dialog {
        +props
        title = "Choose question template"
        Stack {
            css {
                backgroundColor = Color("#f2f2f2")
                padding = 15.px
                fontFamily = sansSerif
                color = rgb(0,0,0,0.3)
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
            QuestionPreset.values().map { preset ->
                PresetButton {
                    palette = MainPalette.default
                    onClick = { props.onPreset?.invoke(preset) }
                    PresetRowContent {
                        emoji = preset.emoji
                        title = preset.title
                        subtitle = preset.subtitle
                    }
                }
            }
            props.importHref?.let { href ->
                Link {
                    to = href
                    css(LinkUnstyled) {
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        alignItems = AlignItems.stretch
                    }
                    PresetButton {
                        palette = MainPalette.default
                        onClick = { props.onClose?.invoke() }
                        PresetRowContent {
                            emoji = "\uD83D\uDCC4"
                            title = "Import from CSV"
                            subtitle = "Bulk-create questions from a spreadsheet"
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
