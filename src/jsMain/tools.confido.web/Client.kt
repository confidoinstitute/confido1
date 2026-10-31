package tools.confido.web

import tools.confido.web.components.App
import tools.confido.web.components.basic.showError
import tools.confido.web.websocket.MultiplexWS
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import js.objects.unsafeJso as jso
import kotlinx.coroutines.*
import web.http.*
import js.promise.await
import kotlin.coroutines.EmptyCoroutineContext
import react.create
import react.dom.client.createRoot
import tools.confido.extensions.ClientExtension
import tools.confido.serialization.confidoJSON
import tools.confido.state.appConfig
import tools.confido.utils.ApiMethod
import tools.confido.utils.BoundApi
import tools.confido.utils.encodesUnit
import web.dom.document

/**
 * Failure of a typed endpoint call ([Client.call]): carries the HTTP status and the
 * server's message body ([status] is null when the call was refused locally because
 * the state websocket is disconnected). Escaping ApiErrors are turned into an error
 * toast by the standard coroutine entry points (runCoroutine, useCoroutineLock,
 * useCoroutine) and into a null result by useSuspendResult — actions toast, loads
 * render empty. Catch it explicitly for custom handling.
 */
class ApiError(val status: HttpStatusCode?, override val message: String) : Exception(message)

object Client {
    val httpClient by lazy  { HttpClient {
        install(ContentNegotiation) {
            json(confidoJSON)
        }
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(confidoJSON)
        }
    } }

    suspend inline fun <reified T> sendDataRequest(
        url: String,
        payload: T,
        method: HttpMethod = HttpMethod.Post,
        crossinline req: HttpRequestBuilder.() -> Unit = {},
    ) = httpClient.request(url) {
        this.method = method
        this.fetchOptions { credentials = "include" }
        if (payload !is String)
            contentType(ContentType.Application.Json.withParameter("charset", "utf-8"))
        val encodedPayload = if (payload is String) payload else confidoJSON.encodeToString(payload)
        // Request bodies include credentials; never log them outside development.
        if (appConfig.devMode) println("encoded payload: $encodedPayload")
        setBody(encodedPayload)
        req()
    }

    /**
     * Backstop for the disconnect-gating policy: while the state websocket has been
     * down long enough to surface (MultiplexWS.disconnected), mutations are refused
     * with a clear message instead of firing at (and acting on) divergent state.
     * UI affordances gray out via useDisconnected; this catches anything they miss.
     */
    fun refuseWhenDisconnected(): Boolean {
        if (!MultiplexWS.disconnected) return false
        showError("No connection to the server — try again when reconnected.")
        return true
    }

    suspend inline fun send(
        url: String,
        method: HttpMethod = HttpMethod.Post,
        crossinline req: HttpRequestBuilder.() -> Unit = {},
        crossinline onError: suspend HttpResponse.(String) -> Unit,
        crossinline block: suspend HttpResponse.() -> Unit,
    ) {
        if (refuseWhenDisconnected()) return
        val resp = httpClient.request(url) {
            this.method = method
            this.fetchOptions { credentials = "include" }
            req()
        }
        if (resp.status.isSuccess())
            block(resp)
        else
            onError(resp, resp.body())
    }

    suspend inline fun <reified T> sendData(
        url: String,
        payload: T,
        method: HttpMethod = HttpMethod.Post,
        crossinline req: HttpRequestBuilder.() -> Unit = {},
        crossinline onError: suspend HttpResponse.(String) -> Unit,
        crossinline block: suspend HttpResponse.() -> Unit,
    ) {
        if (refuseWhenDisconnected()) return
        if (appConfig.devMode) console.log("Send data", url, payload!!::class.simpleName, payload)
        val resp = sendDataRequest(url, payload, method, req)
        if (resp.status.isSuccess())
            block(resp)
        else
            onError(resp, resp.body())
    }

    // ==== Typed endpoints (tools.confido.api.Api) ====
    //
    // A BoundApi carries the method, URL and both wire serializers from the shared spec,
    // so the payload and response types are pinned to the server's declaration — no
    // string URLs, no reified guessing, and String payloads are JSON like everything else
    // (unlike the raw-body special case in sendDataRequest above).

    val ApiMethod.ktor get() = when (this) {
        ApiMethod.GET -> HttpMethod.Get
        ApiMethod.POST -> HttpMethod.Post
        ApiMethod.PUT -> HttpMethod.Put
        ApiMethod.DELETE -> HttpMethod.Delete
    }

    private suspend fun <Req, Resp> request(
        api: BoundApi<Req, Resp>,
        payload: Req,
        req: HttpRequestBuilder.() -> Unit = {},
    ) = httpClient.request(api.url) {
        method = api.method.ktor
        fetchOptions { credentials = "include" }
        if (!api.reqSer.encodesUnit) {
            contentType(ContentType.Application.Json.withParameter("charset", "utf-8"))
            setBody(confidoJSON.encodeToString(api.reqSer, payload))
        }
        req()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <Resp> decode(api: BoundApi<*, Resp>, body: String): Resp =
        if (api.respSer.encodesUnit) Unit as Resp
        else confidoJSON.decodeFromString(api.respSer, body)

    private const val DISCONNECTED_MESSAGE = "No connection to the server — try again when reconnected."

    /**
     * Invoke a typed endpoint: the decoded response on success, [ApiError] on failure
     * (non-2xx status, or a locally refused call while the state websocket is down —
     * the disconnect-gating backstop, same policy as [refuseWhenDisconnected]).
     * Plain suspend + exception, so results are values and error handling composes
     * with ordinary control flow; the default toast lives in the coroutine entry
     * points (see [ApiError]).
     */
    suspend fun <Req, Resp> call(
        api: BoundApi<Req, Resp>,
        payload: Req,
        req: HttpRequestBuilder.() -> Unit = {},
    ): Resp {
        if (MultiplexWS.disconnected) throw ApiError(null, DISCONNECTED_MESSAGE)
        if (appConfig.devMode) console.log("Call", api.toString(), payload)
        val resp = request(api, payload, req)
        if (!resp.status.isSuccess()) throw ApiError(resp.status, resp.bodyAsText())
        return decode(api, resp.bodyAsText())
    }

    /**
     * [call], but the request leaves *now*: a `fetch` with `keepalive`, started
     * synchronously, so it is delivered even if the page is unloaded or frozen right
     * after (pagehide, visibilitychange). Ktor cannot promise that — its engine starts
     * the fetch from a dispatched task, which may never run during unload. Await the
     * result for [call]'s semantics while the page lives. Browsers cap keepalive
     * request bodies (64 KiB in total).
     */
    fun <Req, Resp> callKeepalive(api: BoundApi<Req, Resp>, payload: Req): Deferred<Resp> {
        if (MultiplexWS.disconnected)
            return CompletableDeferred<Resp>().apply { completeExceptionally(ApiError(null, DISCONNECTED_MESSAGE)) }
        if (appConfig.devMode) console.log("Call (keepalive)", api.toString(), payload)
        val response = fetchAsync(api.url, jso {
            method = api.method.name.unsafeCast<RequestMethod>()
            credentials = RequestCredentials.include
            keepalive = true
            if (!api.reqSer.encodesUnit) {
                headers = web.http.Headers().apply { set("Content-Type", "application/json; charset=utf-8") }
                body = BodyInit(confidoJSON.encodeToString(api.reqSer, payload))
            }
        })
        return CoroutineScope(EmptyCoroutineContext).async {
            val resp = response.await()
            val body = resp.text()
            if (!resp.ok) throw ApiError(HttpStatusCode.fromValue(resp.status.toInt()), body)
            decode(api, body)
        }
    }

    suspend fun <Resp> call(
        api: BoundApi<Unit, Resp>,
        req: HttpRequestBuilder.() -> Unit = {},
    ): Resp = call(api, Unit, req)
}

fun main() {
    ClientExtension.forEach { it.clientInit() }
    val app = App.create {}
    createRoot(document.body).render(app)
}
