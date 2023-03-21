package components.redesign.questions

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.router.dom.*
import rooms.*
import tools.confido.question.*
import utils.*

external interface QuestionStickerProps : Props {
    var room: Room
    var question: Question
}

val QuestionSticker = FC<QuestionStickerProps> {props ->
    val palette = roomPalette(props.room.id)
    Link {
        to = props.room.urlPrefix + props.question.urlPrefix
        css {
            width = 144.px
            height = 144.px
            borderRadius = 10.px
            flexShrink = number(0.0)
            textDecoration = None.none
            overflow = Overflow.hidden
            backgroundColor = Color("#FFFFFF")

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
            +props.room.name
        }
        div {
            css {
                fontFamily = FontFamily.serif
                fontWeight = integer(700)
                fontSize = 14.px
                lineHeight = 16.px
                overflow = Overflow.hidden
                padding = 10.px
                color = Color("#000000")
            }
            +props.question.name
        }
    }
}
