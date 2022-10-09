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
