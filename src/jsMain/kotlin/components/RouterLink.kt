package components

import mui.material.*
import react.*

/** MUI Link version of the react.router.dom.Link */
val RouterLink = FC<react.router.dom.LinkProps> { props ->
    Link {
        component = react.router.dom.Link
        +props
    }
}