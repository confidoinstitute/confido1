import components.App
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import browser.document
import components.showError
import io.ktor.client.plugins.websocket.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import kotlinx.coroutines.delay
import react.create
import react.dom.client.createRoot
import tools.confido.extensions.ClientExtension
import tools.confido.serialization.confidoJSON

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
        if (payload !is String)
            contentType(ContentType.Application.Json.withParameter("charset", "utf-8"))
        setBody(payload)
        req()
    }

    suspend inline fun send(
        url: String,
        method: HttpMethod = HttpMethod.Post,
        crossinline req: HttpRequestBuilder.() -> Unit = {},
        crossinline onError: suspend HttpResponse.(String) -> Unit,
        crossinline block: suspend HttpResponse.() -> Unit,
    ) {
        val resp = httpClient.request(url) {
            this.method = method
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
        console.log("Send data", url, payload!!::class.simpleName, payload)
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
