package components.redesign.rooms

import components.*
import components.redesign.basic.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import web.cssom.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.router.*
import react.router.dom.*
import tools.confido.rooms.*
import utils.ExtendableLinkProps
import utils.except
import tools.confido.extensions.ClientExtension

external interface RoomTabsProps : PropsWithPalette<RoomPalette>, PropsWithClassName {
    var onChange: (() -> Unit)?
}
external interface RoomTabProps : ExtendableLinkProps, PropsWithPalette<RoomPalette> {
    var active: Boolean
}

val RoomTab = FC<RoomTabProps> { props->
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
        +props.except("active")
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
    val room = use(RoomContext)!!
    val location = useLocation()
    val locationValue = location.pathname.split('/').getOrNull(3) ?: ""
    val layoutMode = use(LayoutModeContext)!!

    val scoringEnabled = room.scoring != null
    val canViewScoreboard = usePermission(room, RoomPermission.VIEW_SCORES)

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

        if (usePermission(room, RoomPermission.VIEW_QUESTIONS))
            tab("", "Questions")

        if (usePermission(room, RoomPermission.VIEW_ROOM_COMMENTS))
            tab("discussion",
                if (layoutMode == LayoutMode.PHONE) "Discuss" else "Discussion"
            )

        if (usePermission(room, RoomPermission.MANAGE_MEMBERS))
            tab("members", if (layoutMode >= LayoutMode.TABLET) "Room members" else "Members")

        if (usePermission(room, RoomPermission.VIEW_QUESTIONS))
            tab("calibration",
                if (scoringEnabled && layoutMode == LayoutMode.PHONE) "Calib." else "Calibration"
            )

        // Score tab visible when scoring is enabled for the room (not just when scoreboard is visible)
        if (scoringEnabled) {
            tab("score", "Score")
        }


        if (usePermission(room, RoomPermission.MANAGE_QUESTIONS) && layoutMode >= LayoutMode.TABLET)
            tab("manage_questions", "Question management")

        if (usePermission(room, RoomPermission.MANAGE_QUESTIONS)
            && room.presentation != null && layoutMode >= LayoutMode.TABLET)
            tab("presentation",
                if (room._presentationRunning == true) "Presentation \u25CF" else "Presentation")

        ClientExtension.enabled.forEach {
            it.roomTabsExtra(room, layoutMode).forEach { (path, title) -> tab(path, title)  }
        }
    }
}
