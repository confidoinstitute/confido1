package components.presenter

import components.redesign.basic.Stack
import csstype.*
import dom.html.HTMLDivElement
import ext.qrcodereact.QRCodeSVG
import emotion.react.css
import hooks.useElementSize
import hooks.useViewportSize
import hooks.useWebSocket
import payloads.responses.WSData
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import tools.confido.refs.deref
import tools.confido.state.InviteLinkPV
import web.location.location

val InviteLinkPP = FC<PresenterPageProps<InviteLinkPV>> { props->
    val room = props.view.room.deref() ?: return@FC
    val link = room.inviteLinks.firstOrNull { it.id == props.view.id } ?: return@FC
    val url = link.link(location.origin, room)
    val ws = useWebSocket<String>("/state${room.urlPrefix}/invites/${link.id}/shortlink")
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
                +"/join/"
                +ws.data
            }
    }
}