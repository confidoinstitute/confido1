package components.redesign.basic

import browser.document
import components.redesign.forms.Button
import components.redesign.forms.IconButton
import components.redesign.forms.TextButton
import csstype.*
import emotion.css.keyframes
import emotion.react.css
import react.*
import react.dom.createPortal
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.StrokeLinecap

external interface DialogProps: PropsWithChildren {
    var open: Boolean
    var onClose: (() -> Unit)?
    var title: String
    var action: String
    var onAction: (() -> Unit)?
}

val Dialog = FC<DialogProps> {props ->
    val slideKF = keyframes {
        0.pct {
            transform = translatey(100.pct)
        }
        100.pct {
            transform = translatey(0.pct)
        }
    }
    val fadeKF = keyframes {
        0.pct {
            opacity = number(0.0)
        }
        100.pct {
            opacity = number(1.0)
        }
    }

    if (props.open)
        +createPortal(
            Fragment.create {
                div {
                    css {
                        position = Position.fixed
                        top = 0.px
                        width = 100.pct
                        height = 100.pct
                        overflow = Overflow.hidden
                        backgroundColor = rgba(0, 0, 0, 0.5)
                        zIndex = (2000).asDynamic()
                        animationName = fadeKF
                        animationDuration = 0.25.s
                        animationTimingFunction = AnimationTimingFunction.easeOut
                    }
                    onClick = { console.log("Backdrop pressed"); props.onClose?.invoke(); it.preventDefault() }
                }
                Stack {
                    css {
                        maxHeight = 100.pct
                        width = 100.pct
                        zIndex = (2100).asDynamic()
                        position = Position.fixed
                        bottom = 0.px
                        animationName = slideKF
                        animationDuration = 0.25.s
                        animationTimingFunction = AnimationTimingFunction.easeOut
                    }
                    div {
                        css {
                            flexGrow = number(1.0)
                            flexBasis = 44.px
                            flexShrink = number(0.0)
                        }
                        onClick = { console.log("Backdrop pressed"); props.onClose?.invoke(); it.preventDefault() }
                    }
                    Stack {
                        direction = FlexDirection.row
                        css {
                            alignItems = AlignItems.center
                            flexShrink = number(0.0)
                            flexGrow = number(0.0)
                            backgroundColor = Color("#FFFFFF")
                            borderTopLeftRadius = 10.px
                            borderTopRightRadius = 10.px
                            height = 44.px
                            fontWeight = FontWeight.bold
                        }
                        div {
                            css {
                                paddingLeft = 4.px
                                flexGrow = number(1.0)
                                flexBasis = 0.px
                            }
                            IconButton {
                                onClick = { props.onClose?.invoke() }
                                svg {
                                    width = 16.0
                                    height = 16.0
                                    strokeWidth = 2.0
                                    stroke = "#000000"
                                    viewBox = "0 0 16 16"
                                    strokeLinecap = StrokeLinecap.round
                                    path {
                                        d = "M1 1 L15 15 M15 1 L1 15"
                                    }
                                }
                            }
                        }
                        div {
                            css {
                                flexShrink = number(1.0)
                                fontFamily = FontFamily.sansSerif
                                fontSize = 17.px
                                lineHeight = 21.px
                                whiteSpace = WhiteSpace.nowrap
                            }
                            +props.title
                        }
                        div {
                            css {
                                paddingRight = 4.px
                                flexBasis = 0.px
                                flexGrow = number(1.0)
                                display = Display.flex
                                justifyContent = JustifyContent.flexEnd
                                flexDirection = FlexDirection.row
                            }
                            TextButton {
                                palette = TextPalette.action
                                +props.action
                            }
                        }
                    }
                    div {
                        css {
                            flexShrink = number(1.0)
                            minHeight = 0.px
                            overflow = "auto".asDynamic()
                            backgroundColor = Color("#FFFFFF")
                        }
                        +props.children
                    }
                }
            }, document.body.asDynamic())
}
