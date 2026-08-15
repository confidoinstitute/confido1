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
import kotlinx.serialization.KSerializer
import payloads.responses.WSData
import payloads.responses.WSError
import payloads.responses.WSResponse
import payloads.responses.WSResponseSerializer
import tools.confido.application.index
import tools.confido.application.singleThreadContext
import tools.confido.serialization.confidoJSON
import tools.confido.store.MemoView
import tools.confido.store.TxClock
import tools.confido.store.View
import tools.confido.utils.PathTemplate
import tools.confido.utils.TopicSpec0
import tools.confido.utils.TopicSpec1
import tools.confido.utils.TopicSpec2

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
// identified by typed PathTemplates (tools.confido.state.Topics): registration and
// client-side construction share one template per topic, and handlers receive the
// decoded path parameters as typed arguments. An optional query string (e.g. the
// room group_pred `?qids=`) still reaches handlers through a synthetic TopicCall.
// The connection loop lives in Server.kt.

/** An [ApplicationCall] view whose query parameters come from a topic string rather than routing. */
class TopicCall(delegate: ApplicationCall, override val parameters: Parameters) : ApplicationCall by delegate {
    override val request: ApplicationRequest = object : ApplicationRequest by delegate.request {
        override val queryParameters: Parameters = parameters
        override val rawQueryParameters: Parameters = parameters
    }
}

/** Base of the two topic shapes; [template] serves matching (typed) and display. */
sealed class Topic(val template: PathTemplate)

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
class FlowTopic(template: PathTemplate, val flow: (WSBody, List<Any>) -> Flow<JsonElement>) : Topic(template)

/**
 * A topic backed by a [View]: [build] composes the encoded-payload view for one
 * subscription. The connection loop reads [View.value] at frame-flush time, so all
 * ViewTopic payloads of one frame are computed back-to-back without suspension — a
 * transactionally consistent snapshot by the [View] contract, whatever order the wake
 * signals arrived in. [View.changes] only schedules a flush; the per-topic wire diff
 * in the connection loop keeps unchanged recomputations off the wire.
 */
class ViewTopic(template: PathTemplate, val build: suspend (WSBody, List<Any>) -> View<JsonElement>) : Topic(template)

object TopicRegistry {
    val handlers = mutableListOf<Topic>()

    /** Match a topic path (query string already stripped): typed decoded params or null. */
    fun match(topicPath: String): Pair<Topic, List<Any>>? {
        val segments = topicPath.split('/').map { it.decodeURLPart() }
        for (h in handlers) h.template.matchValues(segments)?.let { return h to it }
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
/**
 * The encoded-payload wrapper: memoizes the JSON-encoded payload (so the equality
 * cutoff deduplicates at the payload level). A throwing validate (e.g. a gate's
 * ResponseError surfacing during a source recompute) counts as "changed now": the
 * compute re-runs and captures the error as a WSError payload; error states forgo
 * cutoff and re-evaluate per flush, recovering when state changes.
 */
fun <P> topicPayloadView(serializer: KSerializer<P>, view: View<P>): View<JsonElement> =
    object : MemoView<JsonElement>() {
        override fun validateSources(): Long =
            try { view.validate() } catch (e: ResponseError) { TxClock.serial }

        override fun compute(): JsonElement =
            confidoJSON.encodeToJsonElement(
                WSResponseSerializer(serializer),
                try {
                    WSData(view.value)
                } catch (e: ResponseError) {
                    WSError(e.wsCode, e.message)
                }
            )

        override val changes get() = view.changes
    }

fun <P> Route.viewWS(spec: TopicSpec0<P>, body: suspend WSBody.() -> View<P>) {
    TopicRegistry.handlers.add(ViewTopic(spec.path) { ctx, _ -> topicPayloadView(spec.serializer, ctx.body()) })
}

fun <A : Any, P> Route.viewWS(spec: TopicSpec1<A, P>, body: suspend WSBody.(A) -> View<P>) {
    TopicRegistry.handlers.add(ViewTopic(spec.path) { ctx, args ->
        @Suppress("UNCHECKED_CAST")
        topicPayloadView(spec.serializer, ctx.body(args[0] as A))
    })
}

fun <A : Any, B : Any, P> Route.viewWS(spec: TopicSpec2<A, B, P>, body: suspend WSBody.(A, B) -> View<P>) {
    TopicRegistry.handlers.add(ViewTopic(spec.path) { ctx, args ->
        @Suppress("UNCHECKED_CAST")
        topicPayloadView(spec.serializer, ctx.body(args[0] as A, args[1] as B))
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
fun <G, P> gateFetchFlow(
    serializer: KSerializer<P>,
    gate: suspend () -> View<G>,
    fetch: suspend (G) -> P,
): Flow<JsonElement> {
    val wsSerializer = WSResponseSerializer(serializer)
    return flow {
        val view = gate()
        val frame: suspend () -> JsonElement = {
            val message: WSResponse<P> = try {
                WSData(fetch(view.value))
            } catch (e: ResponseError) {
                WSError(e.wsCode, e.message)
            }
            confidoJSON.encodeToJsonElement(wsSerializer, message)
        }
        emit(frame())
        emitAll(view.changes.conflate().map { frame() })
    }.catch { e ->
        if (e is ResponseError) emit(confidoJSON.encodeToJsonElement(wsSerializer, WSError(e.wsCode, e.message)))
        else throw e
    }
}

fun <G, P> Route.viewWS(
    spec: TopicSpec0<P>,
    gate: suspend WSBody.() -> View<G>,
    fetch: suspend WSBody.(G) -> P,
) {
    TopicRegistry.handlers.add(FlowTopic(spec.path) { ctx, _ ->
        gateFetchFlow(spec.serializer, gate = { ctx.gate() }, fetch = { g -> ctx.fetch(g) })
    })
}

fun <A : Any, G, P> Route.viewWS(
    spec: TopicSpec1<A, P>,
    gate: suspend WSBody.(A) -> View<G>,
    fetch: suspend WSBody.(A, G) -> P,
) {
    TopicRegistry.handlers.add(FlowTopic(spec.path) { ctx, args ->
        @Suppress("UNCHECKED_CAST")
        gateFetchFlow(spec.serializer, gate = { ctx.gate(args[0] as A) }, fetch = { g -> ctx.fetch(args[0] as A, g) })
    })
}

/**
 * Register a flow topic: [body] runs lazily on each subscription and returns the flow of
 * payloads to deliver. A [ResponseError] thrown while building or collecting the flow
 * becomes a single WSError frame. Use try/finally (or onStart/onCompletion) inside the
 * flow for per-subscription effects and cleanup.
 */
fun <P> payloadFlow(serializer: KSerializer<P>, body: suspend () -> Flow<P>): Flow<JsonElement> {
    val wsSerializer = WSResponseSerializer(serializer)
    return flow { emitAll(body()) }  // flow {} defers the body itself to collection time
        .map { confidoJSON.encodeToJsonElement(wsSerializer, WSData(it)) }
        .catch { e ->
            if (e is ResponseError) emit(confidoJSON.encodeToJsonElement(wsSerializer, WSError(e.wsCode, e.message)))
            else throw e
        }
}

fun <P> Route.flowWS(spec: TopicSpec0<P>, body: suspend WSBody.() -> Flow<P>) {
    TopicRegistry.handlers.add(FlowTopic(spec.path) { ctx, _ -> payloadFlow(spec.serializer) { ctx.body() } })
}

fun <A : Any, B : Any, P> Route.flowWS(spec: TopicSpec2<A, B, P>, body: suspend WSBody.(A, B) -> Flow<P>) {
    TopicRegistry.handlers.add(FlowTopic(spec.path) { ctx, args ->
        @Suppress("UNCHECKED_CAST")
        payloadFlow(spec.serializer) { ctx.body(args[0] as A, args[1] as B) }
    })
}
