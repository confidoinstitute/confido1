package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import dom.html.HTMLElement
import emotion.react.*
import hooks.*
import kotlinx.js.*
import react.*
import react.dom.html.ReactHTML.div
import kotlin.math.*

external interface BinaryPredictionProps : Props {
    var yesProb: Double?
    var baseHeight: Length?
}

fun ChildrenBuilder.proportionalCircle(text: String, color: Color, prob: Double?, size: Double = 145.0) {
    // Base size from Figma is 145 px, scale accordingly
    val scalar = size / 145.0
    div {
        css {
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            this.color = Color("#FFFFFF")
            fontFamily = FontFamily.sansSerif
            width = size.px
            height = size.px
        }

        if (prob == null) {
            div {
                css {
                    borderRadius = 100.pct
                    border = Border(1.px, LineStyle.solid, rgba(0,0,0,0.05))
                }
                style = jso {
                    width = (sqrt(0.5) * size).px
                    height = (sqrt(0.5) * size).px
                }
            }
        } else {
            div {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center
                    width = 145.px
                    height = 145.px
                    flexShrink = number(0.0)
                    borderRadius = 100.pct
                    backgroundColor = color
                }
                style = jso {
                    transform = scale(scalar*sqrt(prob))
                }
                // When the font is too small, hide the text
                if (scalar >= 0.3) {
                    div {
                        css {
                            fontWeight = FontWeight.bold
                            fontSize = 50.px
                            lineHeight = 60.px
                        }
                        +"${round(prob * 100)}%"
                    }
                    div {
                        css {
                            fontWeight = FontWeight.bold
                            fontSize = 30.px
                            lineHeight = 35.px
                        }
                        +text
                    }
                }
            }
        }

    }
}

val BinaryPrediction = FC<BinaryPredictionProps> {props ->
    val realSize = useElementSize<HTMLElement>()

    val baseHeight = props.baseHeight ?: 145.px
    val circleSize: Double = min(realSize.height, realSize.width / 2)

    Stack {
        ref = realSize.ref
        direction = FlexDirection.row
        css {
            width = 100.pct
            height = baseHeight
            justifyContent = JustifyContent.center
            alignItems = AlignItems.center
        }
        proportionalCircle("No", Color("#FF5555"), props.yesProb?.let {1 - it}, size = circleSize)
        proportionalCircle("Yes", Color("#00CC2E"), props.yesProb, size = circleSize)
    }
}
