package components.redesign

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import kotlinx.js.*
import dom.html.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div

external interface RoomTabsProps : PropsWithPalette<RoomPalette>, PropsWithClassName {
    var onChange: (() -> Unit)?
}
external interface RoomTabProps : HTMLAttributes<HTMLDivElement>, PropsWithPalette<RoomPalette> {
    var active: Boolean
}

val RoomTab = ForwardRef<HTMLDivElement, RoomTabProps> {props, fRef ->
    val palette = props.palette ?: RoomPalette.red
    div {
        css {
            backgroundColor = Color("#FFFFFF")
        }
        div {
            css {
                backgroundColor = palette.color
                if (props.active)
                    borderBottomRightRadius = 5.px
                width = 5.px
                boxSizing = BoxSizing.borderBox
                height = 100.pct
            }
        }
    }
    div {
        +props
        delete(asDynamic().active)
        ref = fRef
        css(props.className) {
            if (props.active) {
                backgroundColor = Color("#FFFFFF")
                borderTopLeftRadius = 5.px
                borderTopRightRadius = 5.px
                color = Color("#000000")
            } else {
                backgroundColor = palette.color
                color = palette.text.color
                cursor = Cursor.pointer
            }
            boxSizing = BoxSizing.borderBox
            height = 32.px
            padding = 8.px
            lineHeight = 16.px
            fontSize = 13.px
            textAlign = TextAlign.center
            fontFamily = FontFamily.sansSerif
            fontWeight = FontWeight.bolder
        }
    }
    div {
        css {
            backgroundColor = Color("#FFFFFF")
        }
        div {
            css {
                backgroundColor = palette.color
                if (props.active)
                    borderBottomLeftRadius = 5.px
                width = 5.px
                boxSizing = BoxSizing.borderBox
                height = 100.pct
            }
        }
    }

}

val RoomTabs = FC<RoomTabsProps> {props ->
    val palette = props.palette ?: RoomPalette.red
    var active by useState(0)

    Stack {
        direction = FlexDirection.row
        css(props.className) {
            padding = Padding(0.px, 10.px)
            backgroundColor = palette.color
        }
        (0..3).map {
            RoomTab {
                this.palette = palette
                css {
                    flexGrow = number(1.0)
                }
                this.active = (active == it)
                onClick = { _ -> active = it; props.onChange?.invoke() }
                +"Tab $it"
            }
        }
    }
}
