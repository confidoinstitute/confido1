package components.presenter

import components.redesign.basic.Stack
import web.cssom.*
import web.html.HTMLDivElement
import ext.qrcodereact.QRCodeSVG
import emotion.react.css
import hooks.useElementSize
import hooks.useViewportSize
import hooks.useSubscription
import payloads.responses.WSData
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import tools.confido.refs.deref
import tools.confido.state.InviteLinkPV
import web.location.location

val InviteLinkPP = FC<PresenterPageProps<InviteLinkPV>> { props->
    // Invite links only travel on the per-room detail topic (the global rooms map
    // carries directory-level rooms without them), so subscribe it here.
    val roomWS = useSubscription<rooms.Room>("${rooms.Room.topicPrefix(props.view.room.id)}")
    val room = (roomWS as? payloads.responses.WSData)?.data ?: return@FC
    val link = room.inviteLinks.firstOrNull { it.id == props.view.id  || props.view.id == "FIRST" } ?: return@FC
    val baseUrl = link.link(location.origin, room)
    // Use /pres/ prefix for follow-enabled links (cleaner URL for QR codes and display)
    val url = if (props.view.followPresentation) baseUrl.replace("/join/", "/pres/") else baseUrl
    val ws = useSubscription<String>("${room.topicPrefix}/invites/${link.id}/shortlink")
    val viewportSize = useViewportSize()

    Stack {
        css {
            alignItems = AlignItems.center
            justifyContent = JustifyContent.spaceEvenly
            width = 100.vw
            height = 100.vh
        }
        Stack {
            css {
                fontSize = 6.5.vh
                alignItems = AlignItems.center
                lineHeight = number(1.1)
            }
            div {
                +"Join Confido room "
            }
            div {
                i { +room.name }
            }
        }
        div {
            css {
                margin = Margin(10.pt, 0.px)
            }
            QRCodeSVG {
                value = url
                size = maxOf(viewportSize.height / 2, 300)
                level = "M"
            }
        }
        if (ws is WSData<String>)
            div {
                css {
                    fontSize = 5.5.vh
                }
                +location.origin
                if (props.view.followPresentation) +"/pres/" else +"/join/"
                +ws.data
            }
    }
}