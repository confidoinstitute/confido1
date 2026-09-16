package tools.confido.web.components.questions

import tools.confido.web.components.basic.*
import web.cssom.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.router.dom.*
import tools.confido.distributions.*
import tools.confido.rooms.*
import tools.confido.question.*
import tools.confido.utils.formatPercent

external interface QuestionStickerProps : Props {
    /** Link target (the question page URL). */
    var to: String
    var roomName: String
    var roomPalette: RoomPalette
    var questionName: String

    /** Optional status line pinned to the sticker's bottom edge. */
    var footer: String?
    /** Color of the [footer] text; defaults to gray. */
    var footerColor: Color?

    /** Show this group prediction (value line + PDF background for numeric).
     *  The caller applies the display policy (answered / the room's scan mode). */
    var groupPred: Prediction?
    /** Unique per rendered sticker, for the background's SVG gradient id. */
    var stickerKey: String?
}

val QuestionSticker = FC<QuestionStickerProps> {props ->
    val palette = props.roomPalette
    Link {
        to = props.to
        css {
            width = 144.px
            height = 144.px
            borderRadius = 10.px
            flexShrink = number(0.0)
            textDecoration = None.none
            overflow = Overflow.hidden
            backgroundColor = Color("#FFFFFF")
            display = Display.flex
            flexDirection = FlexDirection.column
            // Stacking context for the PDF background's negative-z layer.
            position = Position.relative
            zIndex = integer(0)

            hover {
                boxShadow = BoxShadow(0.px, 0.px, 5.px, Color("#CCCCCC"))
            }
        }
        (props.groupPred?.dist as? ContinuousProbabilityDistribution)?.let {
            TileDistBackground { dist = it; idSuffix = "st-${props.stickerKey ?: props.to}" }
        }
        div {
            css {
                fontFamily = sansSerif
                fontWeight = integer(600)
                fontSize = 13.px
                lineHeight = 36.px
                // Never yield height to a long question body; the body is what gets clipped.
                flexShrink = number(0.0)
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                whiteSpace = WhiteSpace.nowrap
                padding = Padding(0.px, 12.px)
                backgroundColor = palette.color
                color = palette.text.color
            }
            +props.roomName
        }
        div {
            css {
                fontFamily = sansSerif
                fontWeight = integer(700)
                fontSize = 14.px
                lineHeight = 16.px
                overflow = Overflow.hidden
                padding = 10.px
                color = Color("#000000")
                flexGrow = number(1.0)
            }
            +props.questionName
        }
        props.groupPred?.let { gp ->
            div {
                css {
                    fontFamily = sansSerif
                    fontWeight = integer(700)
                    fontSize = 13.px
                    lineHeight = 15.px
                    padding = Padding(0.px, 10.px, 4.px)
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                    textAlign = TextAlign.right
                    color = Color("#6319FF")
                    flexShrink = number(0.0)
                }
                title = "group prediction"
                when (val dist = gp.dist) {
                    is BinaryDistribution -> +formatPercent(dist.yesProb, decimals = 0)
                    is ContinuousProbabilityDistribution -> {
                        val ci = dist.confidenceInterval(0.8, preferredCenter = dist.median)
                        +"${dist.space.formatValue(ci.start, showUnit = false)} to ${dist.space.formatValue(ci.endInclusive)}"
                    }
                    else -> {}
                }
            }
        }
        props.footer?.let { footer ->
            div {
                css {
                    fontFamily = sansSerif
                    fontWeight = integer(600)
                    fontSize = 11.px
                    lineHeight = 13.px
                    padding = Padding(0.px, 10.px, 8.px)
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                    color = props.footerColor ?: Color("#666666")
                    flexShrink = number(0.0)
                }
                +footer
            }
        }
    }
}
