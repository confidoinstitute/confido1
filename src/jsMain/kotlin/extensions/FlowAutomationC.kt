package extensions.flow_automation

import components.redesign.basic.Dialog
import components.redesign.forms.Button
import components.redesign.forms.FormField
import components.redesign.forms.MultilineTextInput
import components.redesign.layout.LayoutMode
import components.rooms.RoomContext
import csstype.TextDecoration
import csstype.em
import emotion.react.css
import hooks.useCoroutineLock
import io.ktor.client.request.*
import io.ktor.http.*
import mui.material.FormGroup
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ol
import react.router.Route
import rooms.Room
import rooms.RoomPermission
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.get
import tools.confido.state.SentState
import tools.confido.state.clientState

val FlowPage = FC<Props> {
    val room = useContext(RoomContext)
    val flow = room.extensionData[RoomFlowKey]
    var curFlow by useState(flow)
    val sendingLock = useCoroutineLock()
    var doneSteps by useState(setOf<String>())

    var editOpen by useState(false)
    Button {
        +"Edit flow"
        onClick = { editOpen = true }
    }
    Dialog {
        open = editOpen
        onClose = { editOpen = false }
        FormGroup {
            FormField {
                title = "Flow JSON"
                MultilineTextInput {
                    css {
                        height = 8.em
                    }
                    placeholder = "JSON"
                    value = curFlow
                    disabled = sendingLock.running
                    onChange = { e -> curFlow = e.target.value }
                }
            }
            Button {
                +"Save"
                onClick = {
                    sendingLock {
                        Client.httpClient.post("/api/${room.urlPrefix}/ext/flow_automation/flow") {
                            setBody(curFlow)
                        }
                    }
                }
            }

        }
    }
    if (flow == null) {
        +"No flow defined for this room"
        return@FC
    }
    var dynFlow  = arrayOf<dynamic>()
    try {
        dynFlow = JSON.parse<Array<dynamic>>(flow)
    } catch (e:Throwable
    ){
        console.log(e)
        +"Flow error: $e"
    }
    console.log(dynFlow)
    ol {
        dynFlow.forEach { item->
            li {
                val name = (item.name as String)
                css {
                    if (name in doneSteps) textDecoration = TextDecoration.lineThrough
                }
                a {
                    +name
                    suspend fun doAction(act: dynamic) {
                        if (act.multi != undefined) {
                            (act.multi as Array<dynamic>).forEach {
                                doAction(it)
                            }
                        }
                        if (act.path != undefined) {
                            val path = (act.path as String).replace("\$room", room.id)
                            val resp = Client.httpClient.post(path) {
                                if (act.json != undefined) {
                                    headers.append("Content-Type", "application/json; charset=UTF-8")
                                    setBody(JSON.stringify(act.json).replace("\$room", room.id))
                                } else if (act.body != undefined) {
                                    setBody(act.body as String)
                                }
                            }
                            if (resp.status.isSuccess() )
                                doneSteps = doneSteps + setOf(name)
                        }
                    }
                    onClick = {
                        sendingLock {
                            doAction(item)
                        }
                        it.preventDefault()
                    }
                }
            }
        }
    }
}

object FlowAutomationCE : ClientExtension, FlowAutomationExtension() {
    override fun roomTabsExtra(room: Room, appState: SentState, layoutMode: LayoutMode): List<Pair<String, String>> =
        if (layoutMode >= LayoutMode.TABLET && appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS))
            listOf("flow" to "Flow")
        else emptyList()

    override fun roomRoutesExtra(room: Room, cb: ChildrenBuilder) {
        cb.apply {
            Route {
                path = "flow"
                element = FlowPage.create {}
            }
        }
    }

}
