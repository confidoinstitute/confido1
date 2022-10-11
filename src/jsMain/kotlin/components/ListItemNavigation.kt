package components

import kotlinx.js.Object
import kotlinx.js.delete
import mui.material.*
import org.w3c.dom.HTMLAnchorElement
import react.*
import react.router.dom.Link
import react.router.dom.LinkProps


external interface ListItemNavigationProps : ListItemButtonProps {
    var to: String
    var onNavigate: ((String) -> Unit)?
}

fun linkComponent(to: String, onNavigate: ((String) -> Unit)?) =
    ForwardRef<HTMLAnchorElement, LinkProps> { linkProps, forwardedRef ->
        Link {
            Object.assign(this, linkProps)
            delete(this.asDynamic().onNavigate)
            this.to = to
            this.ref = forwardedRef
            this.role = null
            onClick = { onNavigate?.invoke(to) }
        }
    }

val ListItemNavigation = FC<ListItemNavigationProps> {props ->

    val renderLink = useMemo(props.to, props.onNavigate) {
        linkComponent(props.to, props.onNavigate)
    }

    ListItemButton {
        Object.assign(this, props)
        this.asDynamic().component = renderLink
    }
}

external interface MenuItemNavigationProps : MenuItemProps {
    var to: String
    var onNavigate: ((String) -> Unit)?
}

val MenuItemNavigation = FC<MenuItemNavigationProps> {props ->

    val renderLink = useMemo(props.to, props.onNavigate) {
        linkComponent(props.to, props.onNavigate)
    }

    MenuItem {
        Object.assign(this, props)
        this.asDynamic().component = renderLink
    }
}