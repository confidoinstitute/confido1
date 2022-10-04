package components

import mui.material.Tab
import mui.material.Tabs
import react.*
import react.router.dom.NavLink
import react.router.useLocation

val locations = mapOf(
    "/" to "Questions",
    "/group_predictions" to "Group predictions",
    "/edit_questions" to "Edit questions",
    "/set_name" to "Change name",
)

val Navigation = FC<Props>
{
    val location = useLocation()
    val locationValue = if(location.pathname.startsWith("/questions")) "/" else location.pathname

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