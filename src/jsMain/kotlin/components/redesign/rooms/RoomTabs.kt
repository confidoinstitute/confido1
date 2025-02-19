package components.redesign.rooms

import components.*
import components.redesign.basic.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.rooms.*
import csstype.*
import dom.html.*
import emotion.react.*
import hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.js.*
import react.*
import react.dom.html.ReactHTML.div
import react.router.*
import react.router.dom.*
import rooms.*
import tools.confido.state.clientState

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
            fontFamily = sansSerif
            fontWeight = integer(600)
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
    val layoutMode = useContext(LayoutModeContext)

    val sbm = room.scoring?.scoreboardMode ?: ScoreboardMode.NONE
    val canScore = (
                (sbm == ScoreboardMode.PUBLIC  && appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS)) ||
                (sbm == ScoreboardMode.PRIVATE && appState.hasPermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS))
            )

    Stack {
        direction = FlexDirection.row
        css(props.className) {
            padding = Padding(0.px, 10.px)
            backgroundColor = palette.color
            justifyContent = JustifyContent.center
        }

        fun tab(to: String, label: String) {
            RoomTab {
                this.palette = palette
                css {
                    flexGrow = if (layoutMode == LayoutMode.PHONE) number(1.0) else number(0.0)
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
            tab("discussion",
                if (layoutMode == LayoutMode.PHONE) "Discuss" else "Discussion"
            )

        if (appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS))
            tab("members", if (layoutMode >= LayoutMode.TABLET) "Room members" else "Members")

        if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS))
            tab("calibration",
                if (canScore && layoutMode == LayoutMode.PHONE) "Calib." else "Calibration"
            )

        if (canScore) {
            tab("score", "Score")

        }


        if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS) && layoutMode >= LayoutMode.TABLET)
            tab("manage_questions", "Question management")

        clientState.extensions.forEach {
            it.roomTabsExtra(room, appState, layoutMode).forEach { (path, title) -> tab(path, title)  }
        }
    }
}
