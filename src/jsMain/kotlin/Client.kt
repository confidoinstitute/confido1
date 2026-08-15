import components.App
import components.redesign.basic.showError
import websocket.MultiplexWS
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import kotlinx.coroutines.delay
import react.create
import react.dom.client.createRoot
import tools.confido.extensions.ClientExtension
import tools.confido.serialization.confidoJSON
import tools.confido.state.appConfig
import web.dom.document

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
}

fun main() {
    ClientExtension.forEach { it.clientInit() }
    val app = App.create {}
    createRoot(document.body).render(app)
}
