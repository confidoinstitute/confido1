package tools.confido.application

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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import payloads.responses.WSError
import payloads.responses.WSErrorType
import payloads.responses.WSResponse
import tools.confido.application.sessions.transientUserData
import tools.confido.application.sessions.userSession
import tools.confido.serialization.confidoJSON

fun Route.getST(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route =
    get(path) { arg-> withContext(singleThreadContext) { body(this@get, arg) } }
fun Route.postST(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route =
    post(path) { arg-> withContext(singleThreadContext) { body(this@post, arg) } }
fun Route.putST(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route =
    put(path) { arg-> withContext(singleThreadContext) { body(this@put, arg) } }
fun Route.deleteST(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route =
    delete(path) { arg-> withContext(singleThreadContext) { body(this@delete, arg) } }

fun Route.webSocketST(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) = webSocket(path, protocol) {  handler(this) }


suspend inline fun PipelineContext<Unit, ApplicationCall>.badRequest(msg: String = "") {
    System.err.println("bad request: $msg")
    System.err.flush()
    call.respond(HttpStatusCode.BadRequest, msg)
}
suspend inline fun PipelineContext<Unit, ApplicationCall>.unauthorized(msg: String = "") {
    System.err.println("unauthorized: $msg")
    System.err.flush()
    call.respond(HttpStatusCode.Unauthorized, msg)
}
suspend inline fun PipelineContext<Unit, ApplicationCall>.notFound(msg: String = "") {
    System.err.println("not found: $msg")
    System.err.flush()
    call.respond(HttpStatusCode.NotFound, msg)
}


class GetWSContext(val call: ApplicationCall)

inline fun <reified T> Route.getWS(path: String, crossinline body: suspend GetWSContext.() -> WSResponse<T>) =
    webSocketST(path) {
        val closeNotifier = MutableStateFlow(false)

        launch {
            incoming.receiveCatching().onFailure {
                closeNotifier.emit(true)
            }
        }

        val context = GetWSContext(call)
        call.transientUserData?.runRefreshable(closeNotifier) {
            if (call.userSession == null) {
                closeNotifier.emit(true)
                return@runRefreshable
            }
            val message: WSResponse<T> = body(context)
            val encoded = confidoJSON.encodeToString( message )
            send(Frame.Text(encoded))
        }
    }

fun <T> GetWSContext.badRequest(msg: String) = WSError<T>(WSErrorType.BAD_REQUEST, msg)
fun <T> GetWSContext.unauthorized(msg: String) = WSError<T>(WSErrorType.UNAUTHORIZED, msg)
fun <T> GetWSContext.notFound(msg: String) = WSError<T>(WSErrorType.NOT_FOUND, msg)
