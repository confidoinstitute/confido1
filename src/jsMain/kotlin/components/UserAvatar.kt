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

fun userListItemText(user: User) = ListItemText.create {
    val nick = user.nick
    val email = user.email
    if (nick != null && email != null) {
        primary = ReactNode(nick)
        secondary = ReactNode(email)
    } else if (nick != null) {
        primary = ReactNode(nick)
        secondary = ReactNode("Temporary guest")
    } else if (email != null) {
        primary = ReactNode(email)
    } else {
        primary = ReactNode("Temporary guest")
    }
}
