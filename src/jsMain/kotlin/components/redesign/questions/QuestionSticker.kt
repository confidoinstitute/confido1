package components.redesign.questions

import components.redesign.basic.RoomPalette
import csstype.*
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.router.dom.Link
import rooms.Room
import tools.confido.question.Question

external interface QuestionStickerProps : Props {
    var room: Room
    var question: Question
}

val QuestionSticker = FC<QuestionStickerProps> {props ->
    val palette = RoomPalette.red
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
        }
        div {
            css {
                fontFamily = FontFamily.sansSerif
                fontWeight = FontWeight.bold
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
                fontWeight = FontWeight.bold
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
