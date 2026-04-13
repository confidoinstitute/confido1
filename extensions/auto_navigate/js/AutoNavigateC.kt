package extensions.auto_navigate

import components.redesign.basic.sansSerif
import components.redesign.forms.TextInput
import components.redesign.presentation.FieldLabel
import emotion.react.css
import hooks.useCoroutine
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import react.FC
import react.dom.html.ReactHTML.div
import react.router.useNavigate
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.FlowItemCategory
import tools.confido.extensions.FlowItemEditorProps
import tools.confido.extensions.FlowItemTypeEntry
import tools.confido.state.AutoNavigateAction
import tools.confido.state.clientState
import users.UserType
import utils.webSocketUrl
import web.cssom.*
import kotlin.time.Duration.Companion.seconds

private val AutoNavigateEditor = FC<FlowItemEditorProps> { props ->
    val action = props.action as? AutoNavigateAction ?: return@FC
    FieldLabel { +"URL" }
    TextInput {
        value = action.url
        onChange = { e -> props.onChange(action.copy(url = e.target.value)) }
        placeholder = "/rooms/abc123"
        css { width = 100.pct }
    }
    div {
        css { fontFamily = sansSerif; fontSize = 12.px; color = Color("#999"); marginTop = 4.px }
        +"All guest users will be navigated to this URL."
    }
}

object AutoNavigateCE : ClientExtension, AutoNavigateExtension() {
    override fun rootLayoutStartHook() {
        val nav = useNavigate()
        useCoroutine {
            if (clientState.session.user?.type == UserType.GUEST) {
                while (true) {
                    try {
                        Client.httpClient.webSocket(webSocketUrl("/api/ext/auto_navigate/follow.ws")) {
                            while (true) {
                                (this.incoming.receive() as? Frame.Text)?.readText()?.let {
                                    console.log("Auto-navigated to $it")
                                    nav(it)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        console.log(e)
                    }
                    delay(15.seconds)
                }
            }
        }
    }

    override fun registerFlowItemTypes() = listOf(
        FlowItemTypeEntry(
            key = "autoNavigate",
            label = "Navigate users to URL",
            category = FlowItemCategory.SIDE_EFFECT,
            createDefault = { AutoNavigateAction("") },
            editor = AutoNavigateEditor,
            matches = { it is AutoNavigateAction },
        )
    )
}
