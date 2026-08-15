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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
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
import tools.confido.store.View

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

/** Base of the two topic shapes; [pattern] is the split topic path. */
sealed class Topic(val pattern: List<String>)

/**
 * A topic backed by a [Flow] — for payloads that suspend (archive fetches) or time
 * themselves (rotators, presence). [flow] builds (or returns) the payload flow for one
 * subscription; it must be cheap and effect-free — all work belongs in the returned
 * flow, which is collected by a per-subscription coroutine (cancelled on
 * unsubscribe/disconnect); emissions are staged into the connection's outbox and
 * deduped there against the last payload the client received. Whether the flow is
 * shared is the lambda's choice: it can return a hot shared flow, or build a cold one
 * from the call parameters. FlowTopic payloads sit *outside* the frame-consistency
 * guarantee of [ViewTopic]s (they are computed at emission time, not at flush time).
 */
class FlowTopic(pattern: List<String>, val flow: (WSBody) -> Flow<JsonElement>) : Topic(pattern)

/**
 * A topic backed by a [View]: [build] composes the encoded-payload view for one
 * subscription. The connection loop reads [View.value] at frame-flush time, so all
 * ViewTopic payloads of one frame are computed back-to-back without suspension — a
 * transactionally consistent snapshot by the [View] contract, whatever order the wake
 * signals arrived in. [View.changes] only schedules a flush; the per-topic wire diff
 * in the connection loop keeps unchanged recomputations off the wire.
 */
class ViewTopic(pattern: List<String>, val build: suspend (WSBody) -> View<JsonElement>) : Topic(pattern)

object TopicRegistry {
    val handlers = mutableListOf<Topic>()

    fun split(path: String) = path.split('/')

    /** Match a topic path (query string already stripped) against registered patterns. */
    fun match(topicPath: String): Pair<Topic, Parameters>? {
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
 * Register a [View]-backed topic: [body] composes a view of the payload from store
 * views ([combineViews]/[at]/[flatMap]), so what the payload reads and what re-computes
 * it are the same declaration. Frames recompute on the view's change signal (conflated).
 * Error semantics are per-computation: a [ResponseError] thrown by the
 * view's transforms becomes a WSError frame and the subscription stays live — the next
 * change recomputes. A [ResponseError] from [body] itself (e.g. unparseable parameters)
 * yields a single error frame.
 */
inline fun <reified T> Route.viewWS(path: String, crossinline body: suspend WSBody.() -> View<T>) {
    TopicRegistry.handlers.add(ViewTopic(TopicRegistry.split(path)) { ctx ->
        val view = ctx.body()
        object : View<JsonElement> {
            // Per-computation error semantics: a ResponseError from the view's
            // transforms encodes as a WSError payload; the subscription stays live
            // and recovers on the next state change.
            override val value: JsonElement
                get() = confidoJSON.encodeToJsonElement<WSResponse<T>>(
                    try {
                        WSData(view.value)
                    } catch (e: ResponseError) {
                        WSError(e.wsCode, e.message)
                    }
                )
            override val changes get() = view.changes
        }
    })
}

/**
 * [View]-gated topic with a suspending fetch stage, for Archive-backed payloads: [gate]
 * composes the consistent, non-suspending part (permission checks, per-viewer gate
 * inputs — include the archive partition's `versionView` so content changes wake the
 * topic) and [fetch] receives the gate's value and may suspend (the partition query).
 * The fetch runs outside the consistency window; it reads committed post-transaction
 * state, and any interleaved change re-wakes the topic, so at-least-once recomputation
 * plus output dedup keeps the delivered payload correct. Errors from either stage are
 * per-computation, as in the [View]-only overload.
 */
inline fun <G, reified T> Route.viewWS(
    path: String,
    crossinline gate: suspend WSBody.() -> View<G>,
    crossinline fetch: suspend WSBody.(G) -> T,
) {
    TopicRegistry.handlers.add(FlowTopic(TopicRegistry.split(path)) { ctx ->
        flow {
            val view = ctx.gate()
            val frame: suspend () -> JsonElement = {
                val message: WSResponse<T> = try {
                    WSData(ctx.fetch(view.value))
                } catch (e: ResponseError) {
                    WSError(e.wsCode, e.message)
                }
                confidoJSON.encodeToJsonElement(message)
            }
            emit(frame())
            emitAll(view.changes.conflate().map { frame() })
        }.catch { e ->
            if (e is ResponseError) emit(confidoJSON.encodeToJsonElement<WSResponse<T>>(WSError(e.wsCode, e.message)))
            else throw e
        }
    })
}

/**
 * Register a flow topic: [body] runs lazily on each subscription and returns the flow of
 * payloads to deliver. A [ResponseError] thrown while building or collecting the flow
 * becomes a single WSError frame. Use try/finally (or onStart/onCompletion) inside the
 * flow for per-subscription effects and cleanup.
 */
inline fun <reified T> Route.flowWS(path: String, crossinline body: suspend WSBody.() -> Flow<T>) {
    TopicRegistry.handlers.add(FlowTopic(TopicRegistry.split(path)) { ctx ->
        flow { emitAll(ctx.body()) }  // flow {} defers the body itself to collection time
            .map { confidoJSON.encodeToJsonElement<WSResponse<T>>(WSData(it)) }
            .catch { e ->
                if (e is ResponseError) emit(confidoJSON.encodeToJsonElement<WSResponse<T>>(WSError(e.wsCode, e.message)))
                else throw e
            }
    })
}
