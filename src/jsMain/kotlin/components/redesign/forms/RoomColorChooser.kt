package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import rooms.RoomColor

external interface RoomColorChooserProps : Props {
    var color: RoomColor?
    var onChange: ((RoomColor) -> Unit)?
}

val RoomColorChooser = FC<RoomColorChooserProps> { props ->
    Stack {
        direction = FlexDirection.column
        css {
            gap = 10.px
        }
        div {
            css {
                fontFamily = sansSerif
                fontWeight = integer(500)
                fontSize = 14.px
                lineHeight = 17.px
            }
            +"Color"
        }
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.center
                gap = 15.px
                backgroundColor = Color("#FFFFFF")
                padding = 5.px
                borderRadius = 8.px
            }

            RoomColor.values().map { color ->
                Checkbox {
                    this.palette = color.palette
                    checked = props.color == color
                    alwaysColorBackground = true
                    onChange = { props.onChange?.invoke(color) }
                }
            }
        }
    }
}
