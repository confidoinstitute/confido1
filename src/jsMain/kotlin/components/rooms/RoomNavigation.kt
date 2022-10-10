package components.rooms

import components.AppStateContext
import mui.material.Tab
import mui.material.Tabs
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.*
import react.router.dom.NavLink
import react.router.useLocation
import rooms.RoomPermission
import utils.themed

val locations = mapOf(
    "" to "Questions",
    "group_predictions" to "Group predictions",
    "edit_questions" to "Edit questions",
    "members" to "Members"
)

val RoomNavigation = FC<Props>
{
    val state = useContext(AppStateContext).state
    val room = useContext(RoomContext)
    val location = useLocation()
    val locationValue = location.pathname.split('/').getOrNull(3) ?: ""
    // TODO: Fix if we are keeping this, see https://mui.com/material-ui/guides/routing/#tabs

    if (!(state.hasPermission(room, RoomPermission.MANAGE_MEMBERS) || state.hasPermission(room, RoomPermission.MANAGE_QUESTIONS))) {
        Typography {
            sx {
                paddingBottom = themed(2)
            }
            variant = TypographyVariant.button
            +"Questions"
        }
        return@FC
    }

    Tabs {
        value = locationValue
        sx {
            marginBottom = themed(2)
        }

        Tab {
            value = ""
            this.label = ReactNode("Questions")
            this.asDynamic().component = NavLink
            this.asDynamic().to = ""
        }

        if (state.hasPermission(room, RoomPermission.MANAGE_QUESTIONS))
        Tab {
            value = "edit_questions"
            this.label = ReactNode("Question management")
            this.asDynamic().component = NavLink
            this.asDynamic().to = "edit_questions"
        }

        if (state.hasPermission(room, RoomPermission.MANAGE_MEMBERS))
            Tab {
                value = "members"
                this.label = ReactNode("Room members")
                this.asDynamic().component = NavLink
                this.asDynamic().to = "members"
            }
    }
}