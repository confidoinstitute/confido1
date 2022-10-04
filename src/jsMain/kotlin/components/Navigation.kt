package components

import mui.material.Tab
import mui.material.Tabs
import react.*
import react.router.dom.NavLink
import react.router.useLocation

val locations = mapOf(
    "" to "Questions",
    "group_predictions" to "Group predictions",
    "edit_questions" to "Edit questions",
)

val Navigation = FC<Props>
{
    val location = useLocation()
    val locationValue = location.pathname
    // TODO: Fix if we are keeping this, see https://mui.com/material-ui/guides/routing/#tabs

    Tabs {
        value = locationValue
        locations.entries.map { (location, label) ->
            Tab {
                value = location
                this.label = ReactNode(label)
                this.asDynamic().component = NavLink
                this.asDynamic().to = location
            }
        }
    }
}