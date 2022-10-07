package components

import kotlinx.js.Object
import kotlinx.js.delete
import mui.material.ListItemButton
import mui.material.ListItemButtonProps
import mui.material.ListItemIcon
import mui.material.ListItemText
import org.w3c.dom.HTMLAnchorElement
import react.*
import react.router.dom.Link
import react.router.dom.LinkProps


external interface ListItemNavigationProps : ListItemButtonProps {
    var to: String
    var onNavigate: ((String) -> Unit)?
}

val ListItemNavigation = FC<ListItemNavigationProps> {props ->

    val renderLink = useMemo(props.to) {
        ForwardRef<HTMLAnchorElement, LinkProps> { linkProps, forwardedRef ->
            Link {
                Object.assign(this, linkProps)
                delete(this.asDynamic().onNavigate)
                this.to = props.to
                this.ref = forwardedRef
                this.role = null
                onClick = { props.onNavigate?.invoke(props.to) }
            }
        }
    }

    ListItemButton {
        Object.assign(this, props)
        this.asDynamic().component = renderLink
    }
}