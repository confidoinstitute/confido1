package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div

external interface RoomColorChooserProps : Props {
    var palette: RoomPalette
    var onChange: ((RoomPalette) -> Unit)?
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
            +"Colour"
        }
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.center
                gap = 15.px
            }

            RoomPalette.values().map { palette ->
                Checkbox {
                    this.palette = palette
                    checked = props.palette == palette
                    alwaysColorBackground = true
                    onChange = { props.onChange?.invoke(palette) }
                }
            }
        }
    }
}
