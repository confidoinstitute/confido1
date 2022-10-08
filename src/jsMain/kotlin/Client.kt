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

    fun post(url: String) = CoroutineScope(EmptyCoroutineContext).launch {
        httpClient.post(url) {}
    }

    inline fun <reified T> postData(url: String, payload: T) = CoroutineScope(EmptyCoroutineContext).launch {
        httpClient.postJson(url, payload) {}
    }

    suspend inline fun <reified T, reified R> postDataAndReceive(url: String, payload: T): R {
        return httpClient.postJson(url, payload) {}.body()
    }
}

fun main() {
    val container = document.createElement("div")
    document.body!!.appendChild(container)

    val app = App.create {}
    createRoot(container).render(app)
}