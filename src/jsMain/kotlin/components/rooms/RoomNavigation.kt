package components.rooms

import components.AppStateContext
import components.layout.permanentBreakpoint
import mui.material.Tab
import mui.material.Tabs
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.material.useMediaQuery
import mui.system.Breakpoint
import mui.system.sx
import react.*
import react.router.dom.NavLink
import react.router.useLocation
import rooms.RoomPermission
import utils.themed

val RoomNavigation = FC<Props>
{
    val state = useContext(AppStateContext).state
    val room = useContext(RoomContext)
    val location = useLocation()
    val locationValue = location.pathname.split('/').getOrNull(3) ?: ""

    val largeScreenQuery = mui.material.styles.useTheme<mui.material.styles.Theme>().breakpoints.up(Breakpoint.md)
    val largeScreen = useMediaQuery(largeScreenQuery)
    // TODO: Fix if we are keeping this, see https://mui.com/material-ui/guides/routing/#tabs

    Tabs {
        value = locationValue
        sx {
            marginBottom = themed(2)
        }

        fun tab(to: String, label: String) {
            Tab {
                key = to
                value = to
                this.label = ReactNode(label)
                this.asDynamic().component = NavLink
                this.asDynamic().to = to
            }
        }

        if (state.hasPermission(room, RoomPermission.VIEW_QUESTIONS))
        tab("", "Questions")

        if (state.hasPermission(room, RoomPermission.VIEW_ROOM_COMMENTS))
        tab("discussion", "Discussion")

        if (largeScreen && state.hasPermission(room, RoomPermission.MANAGE_QUESTIONS))
        tab("manage_questions", "Question management")

        if (state.hasPermission(room, RoomPermission.MANAGE_MEMBERS))
        tab("members", "Room members")
    }
}