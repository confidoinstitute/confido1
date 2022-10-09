package components.rooms

import components.AppStateContext
import csstype.number
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.requests.CreateNewInvite
import react.*
import react.dom.onChange
import react.router.useLocation
import rooms.Forecaster
import rooms.InviteLink
import rooms.Moderator
import rooms.RoomPermission
import utils.byTheme
import utils.eventValue
import utils.themed
import kotlin.coroutines.EmptyCoroutineContext

val NewInvite = FC<Props> {
    val state = useContext(AppStateContext)
    val room = useContext(RoomContext)
    var lastInvite by useState<InviteLink?>(null)

    fun createInvite(invite: CreateNewInvite) {
        CoroutineScope(EmptyCoroutineContext).launch {
            val newInvite: InviteLink = Client.postDataAndReceive("/invite/create", invite)
            lastInvite = newInvite
        }
    }

    if (!state.state.hasPermission(room, RoomPermission.MANAGE_MEMBERS)) {
        Alert {
            severity = AlertColor.error
            +"No permission to manage members."
        }
    }

    Button {
        +"Create invite link (forecaster)"
        color = ButtonColor.primary
        variant = ButtonVariant.contained
        onClick = {
            createInvite(CreateNewInvite(room.id, null, Forecaster))
        }
    }

    Button {
        +"Create invite link (moderator)"
        color = ButtonColor.primary
        variant = ButtonVariant.contained
        onClick = {
            createInvite(CreateNewInvite(room.id, null, Moderator))
        }
    }

    if (lastInvite != null) {
        Typography {
            sx {
                padding = themed(2)
            }
            variant = TypographyVariant.body1
            val url = "${window.location.origin}/room/${room.id}/invite/${lastInvite!!.token}"
            +"Created new invite, link: "
            Link {
                variant = TypographyVariant.body1
                href = url
                +url
            }
        }
    }
}
