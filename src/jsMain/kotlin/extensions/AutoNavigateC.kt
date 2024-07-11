package extensions

import hooks.useCoroutine
import hooks.useWebSocket
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import react.router.useNavigate
import tools.confido.extensions.ClientExtension
import tools.confido.state.clientState
import users.UserType
import utils.webSocketUrl
import kotlin.time.Duration.Companion.seconds

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
}