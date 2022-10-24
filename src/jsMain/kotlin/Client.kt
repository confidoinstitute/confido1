import components.App
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import react.create
import react.dom.client.createRoot
import tools.confido.serialization.confidoJSON
import utils.postJson
import kotlin.coroutines.EmptyCoroutineContext

object Client {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(confidoJSON)
        }
    }

    fun post(url: String, block: (() -> Unit)? = null) = CoroutineScope(EmptyCoroutineContext).launch {
        httpClient.post(url) {}
        block?.invoke()
    }

    inline fun <reified T> postData(url: String, payload: T, crossinline block: (HttpRequestBuilder.() -> Unit) = {})
    = CoroutineScope(EmptyCoroutineContext).launch {
        httpClient.postJson(url, payload, block)
    }

    suspend inline fun <reified T, reified R> postDataAndReceive(url: String, payload: T, block: (HttpRequestBuilder.() -> Unit) = {}): R {
        return httpClient.postJson(url, payload, block).body()
    }
}

fun main() {
    val container = document.createElement("div")
    document.body!!.appendChild(container)

    val app = App.create {}
    createRoot(container).render(app)
}