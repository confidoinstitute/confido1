package extensions.flow_automation

import components.presenter.PresenterPageProps
import components.presenter.PresenterPageType
import components.presenter.presenterPageMap
import components.redesign.basic.Dialog
import components.redesign.forms.Button
import components.redesign.forms.FormField
import components.redesign.forms.MultilineTextInput
import components.redesign.layout.LayoutMode
import components.rooms.RoomContext
import csstype.TextDecoration
import csstype.em
import csstype.vh
import csstype.vw
import emotion.react.css
import extensions.QuestionGroupsKey
import extensions.UpdateReferenceQuestionKey
import extensions.UpdateScatterPlotPV
import hooks.useCoroutineLock
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.js.Object
import kotlinx.js.jso
import mui.material.FormGroup
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.body
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ol
import react.router.Route
import rooms.Room
import rooms.RoomPermission
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.get
import tools.confido.question.Question
import tools.confido.state.PresenterView
import tools.confido.state.SentState
import tools.confido.state.clientState
import tools.confido.state.globalState
import kotlin.reflect.KClass

external interface FlowItem {
    var name: String?
    var path: String?
    var body: String?
    var json: dynamic?
    var forEach: String?
    var block: Array<FlowItem>?
    var multi: Array<FlowItem>?
}

fun getCollection(spec: String): List<Question> =
    if (spec.startsWith("@")) {
        val grp = spec.substring(1)
        globalState.questions.values.reversed().filter { grp in it.extensionData[QuestionGroupsKey] }.toList()
    } else {
        spec.split(",").mapNotNull { globalState.questions[it] }
    }

fun expandFlowItem(item: FlowItem): Array<FlowItem> {
    val forEach = item.forEach
    val block = item.block
    if (forEach != null) {
        val bodyTemplate = jso<FlowItem>()
        Object.assign(bodyTemplate, item)
        bodyTemplate.forEach = null
        val r = mutableListOf<FlowItem>()
        getCollection(forEach).forEachIndexed { idx,q->
            var bodyJson = JSON.stringify(bodyTemplate).replace("\$qid", q.id).replace("\$qname", q.name).replace("\$idx", (idx+1).toString())
            q.extensionData[UpdateReferenceQuestionKey]?.let { qref->
                bodyJson = bodyJson.replace("\$qref", qref.id)
            }
            val body =JSON.parse<FlowItem>(bodyJson)
            r.addAll(expandFlowItem(body))
        }
        return r.toTypedArray()
    } else if (block != null) {
        return block
    } else {
        return arrayOf(item)
    }
}

fun expandFlow(flow: Array<FlowItem>) =
    flow.flatMap { expandFlowItem(it).toList() }.toTypedArray()

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
    var dynFlow  = arrayOf<FlowItem>()
    try {
        dynFlow = JSON.parse(flow)
    } catch (e:Throwable
    ){
        console.log(e)
        +"Flow error: $e"
    }
    dynFlow = expandFlow(dynFlow)
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
                    suspend fun doAction(act: dynamic): Boolean {
                        if (act.multi != undefined) {
                            var ret = true
                            (act.multi as Array<dynamic>).forEach {
                                if (!doAction(it)) ret = false
                            }
                            return ret
                        }
                        if (act.path != undefined) {
                            val path = (act.path as String).replace("\$room", room.id)
                            val resp = Client.httpClient.post(path) {
                                if (act.json != undefined) {
                                    headers.append("Content-Type", "application/json; charset=UTF-8")
                                    setBody(JSON.stringify(act.json).replace("\$room", room.id))
                                } else if (act.body != undefined) {
                                    setBody((act.body as String).replace("\$room", room.id))
                                }
                            }
                            return  (resp.status.isSuccess() )
                        }
                        return false
                    }
                    onClick = {
                        sendingLock {
                            if (doAction(item)) {
                                doneSteps = doneSteps + setOf(name)
                            }
                        }
                        it.preventDefault()
                    }
                }
            }
        }
    }
}

val HTML_PP = FC<PresenterPageProps<HTML_PV>> { props ->
    val view = props.view
    div {
        css {
            width = 100.vw
            height = 100.vh
        }
        dangerouslySetInnerHTML = jso { __html = view.html }
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

    override fun registerPresenterPages(): Map<KClass<out PresenterView>, PresenterPageType> = mapOf(
        presenterPageMap(HTML_PP)
    )

}
