package components.presenter

import csstype.*
import ext.qrcodereact.QRCodeSVG
import emotion.react.css
import hooks.useWebSocket
import mui.material.Box
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.responses.WSData
import react.FC
import react.dom.html.ReactHTML.i
import tools.confido.refs.deref
import tools.confido.state.InviteLinkPV
import web.location.location

val InviteLinkPP = FC<PresenterPageProps<InviteLinkPV>> { props->
    val room = props.view.room.deref() ?: return@FC
    val link = room.inviteLinks.firstOrNull { it.id == props.view.id } ?: return@FC
    val url = link.link(location.origin, room)
    val ws = useWebSocket<String>("/state/rooms/${room.id}/invites/${link.id}/shortlink")

    mui.material.Stack {
        sx {
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            gap = 20.pt
            width = 100.pct
            height = 100.pct
        }
        Typography {
            variant = TypographyVariant.h4
            +"Join Confido room "
        }
        Typography {
            variant = TypographyVariant.h3
            i { +room.name }
        }
        Box {
            sx {
                margin = Margin(10.pt, 0.px)
            }
            QRCodeSVG {
                value = url
                size = 300
                level = "M"
            }
        }
        if (ws is WSData<String>)
            Typography {
                variant = TypographyVariant.h4
                +location.origin
                +"/join/"
                +ws.data
            }
    }
}