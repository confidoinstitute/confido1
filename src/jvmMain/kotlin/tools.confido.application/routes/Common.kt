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
import tools.confido.application.PermClass
import tools.confido.application.index
import tools.confido.application.permClassOf
import tools.confido.application.sessions.userSession
import tools.confido.application.singleThreadContext
import tools.confido.serialization.confidoJSON
import tools.confido.store.*
import tools.confido.utils.ApiMethod
import tools.confido.utils.ApiSpec0
import tools.confido.utils.ApiSpec1
import tools.confido.utils.ApiSpec2
import tools.confido.utils.ParamSeg
import tools.confido.utils.PathTemplate
import tools.confido.utils.TopicSpec0
import tools.confido.utils.TopicSpec1
import tools.confido.utils.TopicSpec2
import tools.confido.utils.encodesUnit

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

// ==== Typed API endpoints (tools.confido.api.Api) ====
//
// An ApiSpec-registered route is the HTTP analogue of viewWS(spec): the Ktor pattern
// comes from the spec's PathTemplate (whose `{name}` syntax is Ktor's own), handlers
// receive decoded path arguments as typed parameters, and request/response bodies go
// through the spec's serializers — so both URL shape and wire types are pinned to the
// same commonMain declaration the client invokes.

private val ApiMethod.routeMeth: Route.(String, suspend RoutingContext.() -> Unit) -> Route
    get() = when (this) {
        ApiMethod.GET -> Route::get
        ApiMethod.POST -> Route::post
        ApiMethod.PUT -> Route::put
        ApiMethod.DELETE -> Route::delete
    }

/** Positional typed path arguments from Ktor's matched parameters. A decode failure is
 *  NOT_FOUND, matching the old behavior of dereferencing a Ref built from a blank id. */
private fun PathTemplate.decodeCallParams(parameters: Parameters): List<Any> =
    segments.filterIsInstance<ParamSeg>().map { seg ->
        val raw = parameters[seg.param.name] ?: badRequest("Missing path parameter '${seg.param.name}'.")
        seg.param.decode(raw) ?: notFound("No such ${seg.param.name}.")
    }

private suspend fun <Req> RoutingContext.receiveApi(ser: KSerializer<Req>): Req =
    if (ser.encodesUnit) @Suppress("UNCHECKED_CAST") (Unit as Req)
    else call.receiveText().let { text ->
        try { confidoJSON.decodeFromString(ser, text) }
        catch (e: Exception) { badRequest("Malformed request body.") }
    }

private suspend fun <Resp> RoutingContext.respondApi(ser: KSerializer<Resp>, value: Resp) {
    if (ser.encodesUnit) call.respond(HttpStatusCode.OK)
    else call.respondText(confidoJSON.encodeToString(ser, value), ContentType.Application.Json, HttpStatusCode.OK)
}

fun <Req, Resp> Route.apiST(spec: ApiSpec0<Req, Resp>, body: suspend RoutingContext.(Req) -> Resp) =
    performST(spec.method.routeMeth, "/" + spec.path.pattern) {
        respondApi(spec.respSer, body(receiveApi(spec.reqSer)))
    }

fun <A : Any, Req, Resp> Route.apiST(spec: ApiSpec1<A, Req, Resp>, body: suspend RoutingContext.(A, Req) -> Resp) =
    performST(spec.method.routeMeth, "/" + spec.path.pattern) {
        val args = spec.path.decodeCallParams(call.parameters)
        @Suppress("UNCHECKED_CAST")
        respondApi(spec.respSer, body(args[0] as A, receiveApi(spec.reqSer)))
    }

fun <A : Any, B : Any, Req, Resp> Route.apiST(spec: ApiSpec2<A, B, Req, Resp>, body: suspend RoutingContext.(A, B, Req) -> Resp) =
    performST(spec.method.routeMeth, "/" + spec.path.pattern) {
        val args = spec.path.decodeCallParams(call.parameters)
        @Suppress("UNCHECKED_CAST")
        respondApi(spec.respSer, body(args[0] as A, args[1] as B, receiveApi(spec.reqSer)))
    }

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


/**
 * Per-subscription resources (ViewTopics are otherwise passive): a topic build that
 * acquires something with a lifetime — e.g. a [tools.confido.store.ResidentPartitions]
 * pin — registers its release via [onClose]; the connection loop runs [closeAll] on
 * unsubscribe, disconnect, and build failure.
 */
class SubscriptionResources {
    private val closers = mutableListOf<() -> Unit>()
    fun onClose(f: () -> Unit) { closers += f }
    fun closeAll() {
        closers.forEach { runCatching(it) }
        closers.clear()
    }
}

data class WSBody(val call: ApplicationCall) {
    val resources = SubscriptionResources()
}

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
 * [statsKey] attributes payload computations in [TopicStats] (devmode diagnostics;
 * also what makes shared-view compute-once observable to the test suite).
 */
fun <P> topicPayloadView(serializer: KSerializer<P>, view: View<P>, statsKey: String? = null): View<JsonElement> =
    object : MemoView<JsonElement>() {
        // While the cached payload is an error frame, forgo the input cutoff entirely.
        // Upstream change stamps describe the *data* value, which the error payload did
        // not consume — the memo soundness argument ("downstream consumed the cache it
        // cuts off against") does not hold across a substituted error. Concretely: gate
        // closes → error frame; gate reopens onto a value EQUAL to the pre-error one
        // (hide/unhide, remove/re-add) → upstream early cutoff reports "unchanged since
        // before the error" and without this flag the subscription would stay wedged on
        // the stale error forever.
        private var wasError = false

        override fun validateSources(): Long =
            if (wasError) TxClock.serial
            else try { view.validate() } catch (e: ResponseError) { TxClock.serial }

        override fun compute(): JsonElement {
            statsKey?.let { TopicStats.bump(it) }
            return confidoJSON.encodeToJsonElement(
                WSResponseSerializer(serializer),
                try {
                    WSData(view.value).also { wasError = false }
                } catch (e: ResponseError) {
                    wasError = true
                    WSError(e.wsCode, e.message)
                }
            )
        }

        override val changes get() = view.changes
    }

/** Payload computations per topic pattern (single-threaded, like all view code).
 *  Exposed in devmode via GET /diag/topic_computes. */
object TopicStats {
    val computes = mutableMapOf<String, Long>()
    fun bump(key: String) { computes[key] = (computes[key] ?: 0L) + 1L }
}

fun <P> Route.viewWS(spec: TopicSpec0<P>, body: suspend WSBody.() -> View<P>) {
    TopicRegistry.handlers.add(ViewTopic(spec.path) { ctx, _ -> topicPayloadView(spec.serializer, ctx.body(), spec.path.pattern) })
}

fun <A : Any, P> Route.viewWS(spec: TopicSpec1<A, P>, body: suspend WSBody.(A) -> View<P>) {
    TopicRegistry.handlers.add(ViewTopic(spec.path) { ctx, args ->
        @Suppress("UNCHECKED_CAST")
        topicPayloadView(spec.serializer, ctx.body(args[0] as A), spec.path.pattern)
    })
}

fun <A : Any, B : Any, P> Route.viewWS(spec: TopicSpec2<A, B, P>, body: suspend WSBody.(A, B) -> View<P>) {
    TopicRegistry.handlers.add(ViewTopic(spec.path) { ctx, args ->
        @Suppress("UNCHECKED_CAST")
        topicPayloadView(spec.serializer, ctx.body(args[0] as A, args[1] as B), spec.path.pattern)
    })
}

// ==== Shared per-(topic, permission-class) views (§4.3, the censor-memoization stage) ====
//
// A topic registered via [sharedViewWS] declares its payload to be a pure function of
// the viewer's [PermClass]: the body receives the class and — deliberately — no
// session/call, so per-viewer reads are impossible by construction (they would leak
// between same-class viewers). Each subscription resolves its viewer's class
// *reactively* (a per-session class view, shared by all of the session's
// subscriptions) and [flatMap]s into the registry entry for (topic, args, class): all
// same-class subscribers hold the *same* memoized encoded-payload view, so by the
// §3.5 memo counters a payload computes and encodes once per transaction regardless
// of subscriber count — sharing subsumes the old shareIn keyed-registry idea. A class
// change (role change, join/leave) re-selects the registry entry on the live
// subscription via the flatMap re-selection path.

/**
 * Weakly-referenced registry of shared views. Entries are pinned by the
 * subscriptions that hold them (the flatMap selection cache and the changes
 * collectors); once the last same-class subscriber unsubscribes, the entry becomes
 * collectable — views are passive (no jobs, no upstream collection), so lifecycle is
 * purely a memory concern and a prematurely collected entry just rebuilds on demand
 * (views are pure; the cost is one recompute). Single-threaded like all view code.
 */
object SharedViews {
    private val map = HashMap<Any, java.lang.ref.WeakReference<Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <V : Any> get(key: Any, build: () -> V): V {
        map[key]?.get()?.let { return it as V }
        map.entries.removeIf { it.value.get() == null }  // opportunistic prune
        return build().also { map[key] = java.lang.ref.WeakReference(it) }
    }
}

/**
 * The viewer's [PermClass] as a view: recomputes on session/user/room churn, with the
 * memo equality cutoff ensuring downstream shared views only re-select when the class
 * *actually* changes (session renewals and unrelated membership churn dedup away).
 * Shared per session — all of a connection's (and a session's connections')
 * subscriptions validate one instance.
 */
internal fun WSBody.sharedClassView(): View<PermClass> {
    val sessionId = call.userSession?.id ?: unauthorized("Not logged in.")
    return SharedViews.get("permclass" to sessionId) {
        combineViews(
            userSessionStore.state.asView().at(sessionId),
            userStore.state.asView(),
            roomStore.state.asView(),
        ) { sess, users, _ ->
            val user = sess?.userRef?.let { users[it.id] } ?: unauthorized("Not logged in.")
            permClassOf(user)
        }
    }
}

/**
 * Per-subscription error boundary over the shared chain: on the happy path it passes
 * the shared encoded element through by reference (identity-equal — no re-encoding,
 * cheap cutoff); a [ResponseError] surfacing from class resolution becomes a WSError
 * payload with the usual per-computation recovery semantics.
 */
private fun wsErrorView(view: View<JsonElement>): View<JsonElement> = object : MemoView<JsonElement>() {
    // Same error-state cutoff exemption as in topicPayloadView (see the comment there):
    // while the cache holds a substituted error, upstream stamps must not cut off.
    private var wasError = false

    override fun validateSources(): Long =
        if (wasError) TxClock.serial
        else try { view.validate() } catch (e: ResponseError) { TxClock.serial }

    override fun compute(): JsonElement =
        try { view.value.also { wasError = false } }
        catch (e: ResponseError) {
            wasError = true
            confidoJSON.encodeToJsonElement(WSError<Unit>(e.wsCode, e.message) as WSResponse<Unit>)
        }

    override val changes get() = view.changes
}

// The [scope] parameter projects the class onto the rooms the topic's censor actually
// reads (PermClass.scopedTo) — the registry key AND the class the body receives are
// the projected class, so key-narrowing cannot leak (an under-projection reads as "no
// permissions" and censors too much, visibly). The projection is recomputed on room
// churn as well as class churn — room *structure* (a question moving, an
// effective-question set changing) can change which rooms are in scope without the
// viewer's class map changing — with the memo equality cutoff ensuring downstream
// re-selection only happens when the projected class actually differs. Default:
// identity (cross-room topics key on the full class; with fragmented membership
// vectors that degrades gracefully to per-viewer cost, and cohorts still share).

fun <P> Route.sharedViewWS(
    spec: TopicSpec0<P>,
    scope: (PermClass) -> PermClass = { it },
    body: (PermClass) -> View<P>,
) {
    TopicRegistry.handlers.add(ViewTopic(spec.path) { ctx, _ ->
        val scopedClass = combineViews(ctx.sharedClassView(), roomStore.state.asView()) { cls, _ -> scope(cls) }
        wsErrorView(scopedClass.flatMap { cls ->
            SharedViews.get(Triple(spec.path.pattern, emptyList<Any>(), cls)) {
                topicPayloadView(spec.serializer, body(cls), spec.path.pattern)
            }
        })
    })
}

/**
 * The general keyed form: [key] builds the per-subscription *reactive sharing key* —
 * it runs with the WSBody in scope, so it may read the session and query parameters;
 * everything per-viewer the payload depends on must flow into the key (e.g. the
 * gate-relevant "has answered" flag of the group-prediction topics, or a query
 * filter). [body] receives the key: the registry entry and the censor input are the
 * same value, so per-viewer reads outside the key are impossible by construction,
 * and an under-keyed topic fails closed. Key types must be equality-friendly (data
 * classes); prefer keys that collapse to constants when a per-viewer fact is
 * irrelevant (e.g. answered=false unless visibility is ANSWERED), so the common case
 * shares maximally.
 */
@JvmName("sharedViewWSKeyed")  // same erased signature as the class-scoped overload
fun <A : Any, K, P> Route.sharedViewWS(
    spec: TopicSpec1<A, P>,
    key: WSBody.(A) -> View<K>,
    body: (K, A) -> View<P>,
) {
    TopicRegistry.handlers.add(ViewTopic(spec.path) { ctx, args ->
        @Suppress("UNCHECKED_CAST")
        val a = args[0] as A
        wsErrorView(ctx.key(a).flatMap { k ->
            SharedViews.get(Triple(spec.path.pattern, args, k)) {
                topicPayloadView(spec.serializer, body(k, a), spec.path.pattern)
            }
        })
    })
}

// The class-scoped form is the keyed form with key = the (projected) PermClass.
// Only such topics may skip query parameters entirely; a keyed topic reading a query
// filter must fold it into the key (the registry key carries only path args).
fun <A : Any, P> Route.sharedViewWS(
    spec: TopicSpec1<A, P>,
    scope: (PermClass, A) -> PermClass = { cls, _ -> cls },
    body: (PermClass, A) -> View<P>,
) = sharedViewWS(spec,
    key = { a -> combineViews(sharedClassView(), roomStore.state.asView()) { cls, _ -> scope(cls, a) } },
    body = body)

/**
 * [View]-gated topic with a suspending fetch stage: [gate] composes the consistent,
 * non-suspending part (permission checks, per-viewer gate inputs — include a version
 * counter for content the fetch reads, so content changes wake the topic) and [fetch]
 * receives the gate's value and may suspend. (The comment topics left this shape for
 * resident partitions + plain views; extension topics still use it.)
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
        // The wake flow must be subscribed BEFORE the initial fetch starts: a change
        // landing while the fetch suspends must trigger a refetch, or the topic serves
        // the pre-change payload until the next unrelated wake. merge() launches its
        // collectors up front; on the single server thread a mutation can only be
        // dispatched after them, so the subscription is in place by the first suspension.
        emitAll(merge(flowOf(Unit), view.changes).conflate().map { frame() })
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
