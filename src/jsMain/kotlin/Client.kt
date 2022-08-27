import components.App
import components.QuestionList
import kotlinx.browser.document
import kotlinx.html.dom.append
import react.create
import react.dom.client.createRoot
import space.kscience.plotly.plot
import org.w3c.dom.WebSocket
import react.createContext

fun main() {
    val container = document.createElement("div")
    document.body!!.appendChild(container)

    val app = App.create {}
    createRoot(container).render(app)
}