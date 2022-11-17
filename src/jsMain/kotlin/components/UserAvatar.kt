package components

import react.*
import mui.system.*
import mui.material.*
import users.User
import utils.stringToColor

external interface UserAvatarProps : Props {
    var user: User
}

val UserAvatar = FC<UserAvatarProps> {props ->
    Avatar {
        sx {
            backgroundColor = stringToColor(props.user.id)
        }
        alt = props.user.nick
        props.user.nick?.let {
            +(it[0].toString())
        }
    }
}

/**
 * @param withInactive if true, add `(inactive)` to the name when user is inactive.
 */
fun userListItemText(user: User, withInactive: Boolean = false) = ListItemText.create {
    val nick = user.nick
    val email = user.email

    val suffix = if (withInactive && !user.active) {
        " (inactive)"
    } else {
        ""
    }

    if (nick != null && email != null) {
        primary = ReactNode("$nick$suffix")
        secondary = ReactNode(email)
    } else if (nick != null) {
        primary = ReactNode("$nick$suffix")
        secondary = ReactNode("Temporary guest")
    } else if (email != null) {
        primary = ReactNode("$email$suffix")
    } else {
        primary = ReactNode("Temporary guest$suffix")
    }
}