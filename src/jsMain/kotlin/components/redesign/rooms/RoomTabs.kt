package components.redesign.rooms

import components.AppStateContext
import components.redesign.basic.*
import components.rooms.RoomContext
import csstype.*
import emotion.react.*
import kotlinx.js.*
import dom.html.*
import react.*
import react.dom.html.ReactHTML.div
import react.router.dom.Link
import react.router.dom.LinkProps
import react.router.useLocation
import rooms.RoomPermission

external interface RoomTabsProps : PropsWithPalette<RoomPalette>, PropsWithClassName {
    var onChange: (() -> Unit)?
}
external interface RoomTabProps : LinkProps, PropsWithPalette<RoomPalette> {
    var active: Boolean
}

val RoomTab = ForwardRef<HTMLAnchorElement, RoomTabProps> { props, fRef ->
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
    Link {
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
            textDecoration = None.none
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

val RoomTabs = FC<RoomTabsProps> { props ->
    val palette = props.palette ?: RoomPalette.red

    val (appState, _) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val location = useLocation()
    val locationValue = location.pathname.split('/').getOrNull(3) ?: ""

    Stack {
        direction = FlexDirection.row
        css(props.className) {
            padding = Padding(0.px, 10.px)
            backgroundColor = palette.color
        }

        fun tab(to: String, label: String) {
            RoomTab {
                this.palette = palette
                css {
                    flexGrow = number(1.0)
                }
                this.to = to
                this.replace = true
                this.active = (to == locationValue)
                this.onClick = {if (to != locationValue) props.onChange?.invoke()}
                +label
            }
        }

        if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS))
            tab("", "Questions")

        if (appState.hasPermission(room, RoomPermission.VIEW_ROOM_COMMENTS))
            tab("discussion", "Discussion")

        if (appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS))
            tab("members", "Room members")
    }
}
