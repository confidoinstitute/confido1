package tools.confido.web.components.redesign.questions

import tools.confido.web.components.redesign.basic.*
import web.cssom.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.router.dom.*
import tools.confido.rooms.*
import tools.confido.question.*

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

            hover {
                boxShadow = BoxShadow(0.px, 0.px, 5.px, Color("#CCCCCC"))
            }
        }
        div {
            css {
                fontFamily = sansSerif
                fontWeight = integer(600)
                fontSize = 13.px
                lineHeight = 36.px
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
