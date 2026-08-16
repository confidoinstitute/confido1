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
import tools.confido.state.InviteLinkPV
import web.location.location
import tools.confido.state.Topics
import tools.confido.refs.ref

val InviteLinkPP = FC<PresenterPageProps<InviteLinkPV>> { props->
    // Invite links only travel on the per-room detail topic (the global rooms map
    // carries directory-level rooms without them), so subscribe it here.
    val roomWS = useSubscription(Topics.room(props.view.room))
    // Hooks precede the data-dependent early returns: on a cold load the
    // subscriptions deliver after the first render, and the hook order must
    // not change between the loading and loaded renders (React #310).
    val room = (roomWS as? WSData)?.data
    val link = room?.inviteLinks?.firstOrNull { it.id == props.view.id  || props.view.id == "FIRST" }
    val ws = useSubscription(if (room != null && link != null) Topics.inviteShortlink(room.ref, link.id) else null)
    val viewportSize = useViewportSize()
    if (room == null || link == null) return@FC
    val baseUrl = link.link(location.origin, room)
    // Use /pres/ prefix for follow-enabled links (cleaner URL for QR codes and display)
    val url = if (props.view.followPresentation) baseUrl.replace("/join/", "/pres/") else baseUrl

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