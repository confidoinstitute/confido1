import components.App
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.launch
import react.create
import react.dom.client.createRoot
import tools.confido.payloads.SetName
import utils.postJson

object Client {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    inline fun <reified T> postData(url: String, payload: T) = CoroutineScope(EmptyCoroutineContext).launch {
        httpClient.postJson(url, payload) {}
    }
}
fun main() {
    val container = document.createElement("div")
    document.body!!.appendChild(container)

    val app = App.create {}
    createRoot(container).render(app)
}