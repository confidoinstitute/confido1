package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import payloads.responses.WSError
import payloads.responses.WSResponse
import tools.confido.application.sessions.transientUserData
import tools.confido.application.sessions.userSession
import tools.confido.application.singleThreadContext
import tools.confido.serialization.confidoJSON

typealias RouteBodyCall = suspend RouteBody.() -> Unit

data class RouteBody(val call: ApplicationCall)

fun Route.performST(route: Route.(String, PipelineInterceptor<Unit, ApplicationCall>) -> Route, path: String, body: RouteBodyCall): Route =
    this.route(path) {
        withContext(singleThreadContext) {
            try {
                body(RouteBody(call))
            } catch (e: ResponseError) {
                System.err.println("${e.httpCode.description}: ${e.message}")
                System.err.flush()
                call.respond(e.httpCode, e.message)
            }
        }
    }

fun Route.getST(path: String, body: RouteBodyCall) = performST(Route::get, path, body)
fun Route.postST(path: String, body: RouteBodyCall) = performST(Route::post, path, body)
fun Route.putST(path: String, body: RouteBodyCall) = performST(Route::put, path, body)
fun Route.deleteST(path: String, body: RouteBodyCall) = performST(Route::delete, path, body)

fun Route.webSocketST(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) = webSocket(path, protocol) {  handler(this) }


inline fun <reified T> Route.getWS(path: String, crossinline body: suspend RouteBody.() -> WSResponse<T>) =
    webSocketST(path) {
        val closeNotifier = MutableStateFlow(false)

        launch {
            incoming.receiveCatching().onFailure {
                closeNotifier.emit(true)
            }
        }

        val context = RouteBody(call)
        call.transientUserData?.runRefreshable(closeNotifier) {
            print("Checking session")
            if (call.userSession == null) {
                closeNotifier.emit(true)
                return@runRefreshable
            }
            val message: WSResponse<T> = try {
                body(context)
            } catch (e: ResponseError) {
                WSError(e.wsCode, e.message)
            }
            val encoded = confidoJSON.encodeToString(message)
            send(Frame.Text(encoded))
        }
    }
