package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.withContext
import kotlinx.html.HTML
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import payloads.responses.WSData
import payloads.responses.WSError
import payloads.responses.WSResponse
import tools.confido.application.index
import tools.confido.application.singleThreadContext
import tools.confido.serialization.confidoJSON

typealias RouteBodyCall = suspend RoutingContext.() -> Unit

//data class RouteBody(val call: ApplicationCall)

suspend fun RoutingContext.serveFrontend() {
    call.respondHtml(HttpStatusCode.OK, HTML::index)
}

fun Route.performST(routeMeth: Route.(String, suspend RoutingContext.()->Unit) -> Route, path: String, body: RouteBodyCall): Route =
    routeMeth(this, path) {
        withContext(singleThreadContext) {
            try {
                body()
            } catch (e: ResponseError) {
                call.application.log.info("Responding ${e.httpCode.value} (${e.httpCode.description}): ${e.message}")
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
) = webSocket(path, protocol) {
    // Websocket handlers read and write the same in-memory entity maps as the HTTP
    // handlers, so they must share their dispatcher. Note this only covers the body
    // and anything it starts via coroutineScope/async; a `launch` inside the handler
    // resolves against the websocket session's own scope and needs the context passed
    // explicitly (see the /state multiplex handler in Server.kt).
    withContext(singleThreadContext) { handler(this@webSocket) }
}


data class WSBody(val call: ApplicationCall)

// ==== Topic-multiplexed state websocket (PERSISTENCE_SYNC_REDESIGN.md §4.1) ====
//
// All subscription data is carried by the single /state websocket. Topics are
// identified by the URL paths the former dedicated websocket endpoints used
// (e.g. "/state/rooms/{rID}/comments"), optionally with a query string; handlers
// therefore keep reading call.parameters / call.request.queryParameters through
// a synthetic TopicCall. The connection loop lives in Server.kt.

/** An [ApplicationCall] view whose parameters come from a topic string rather than routing. */
class TopicCall(delegate: ApplicationCall, override val parameters: Parameters) : ApplicationCall by delegate {
    override val request: ApplicationRequest = object : ApplicationRequest by delegate.request {
        override val queryParameters: Parameters = parameters
        override val rawQueryParameters: Parameters = parameters
    }
}

sealed class TopicHandler(val pattern: List<String>)
/** Re-evaluated on every refresh signal; payload deduplicated per subscription. */
class PullTopic(pattern: List<String>, val eval: suspend (WSBody) -> JsonElement) : TopicHandler(pattern)
/** Runs as a per-subscription coroutine pushing frames via emit; cancelled on unsubscribe/disconnect. */
class StreamTopic(pattern: List<String>, val run: suspend (WSBody, suspend (JsonElement) -> Unit) -> Unit) : TopicHandler(pattern)

object TopicRegistry {
    val handlers = mutableListOf<TopicHandler>()

    fun split(path: String) = path.split('/')

    /** Match a topic path (query string already stripped) against registered patterns. */
    fun match(topicPath: String): Pair<TopicHandler, Parameters>? {
        val segments = split(topicPath)
        outer@ for (h in handlers) {
            if (h.pattern.size != segments.size) continue
            val params = ParametersBuilder()
            for ((pat, seg) in h.pattern.zip(segments)) {
                if (pat.startsWith("{") && pat.endsWith("}")) {
                    if (seg.isEmpty()) continue@outer
                    params.append(pat.substring(1, pat.length - 1), seg.decodeURLPart())
                } else if (pat != seg) continue@outer
            }
            return h to params.build()
        }
        return null
    }
}

/**
 * Register a pull topic: [body] is evaluated on subscription and on every refresh
 * signal, in the same form the old `getWS` endpoints used. The [Route] receiver is
 * unused (registration is global) and kept only so call sites read naturally.
 */
inline fun <reified T> Route.topicWS(path: String, crossinline body: suspend WSBody.() -> T) {
    TopicRegistry.handlers.add(PullTopic(TopicRegistry.split(path)) { ctx ->
        val message: WSResponse<T> = try {
            WSData(body(ctx))
        } catch (e: ResponseError) {
            WSError(e.wsCode, e.message)
        }
        confidoJSON.encodeToJsonElement(message)
    })
}

/**
 * Register a stream topic: [body] runs for the lifetime of each subscription and
 * pushes payloads via `emit`; use try/finally for per-subscription cleanup.
 */
inline fun <reified T> Route.streamWS(path: String, crossinline body: suspend WSBody.(emit: suspend (T) -> Unit) -> Unit) {
    TopicRegistry.handlers.add(StreamTopic(TopicRegistry.split(path)) { ctx, rawEmit ->
        try {
            ctx.body { value -> rawEmit(confidoJSON.encodeToJsonElement<WSResponse<T>>(WSData(value))) }
        } catch (e: ResponseError) {
            rawEmit(confidoJSON.encodeToJsonElement<WSResponse<T>>(WSError(e.wsCode, e.message)))
        }
    })
}
