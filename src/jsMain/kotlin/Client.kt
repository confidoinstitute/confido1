import kotlinx.browser.document
import kotlinx.html.dom.append
import react.create
import react.dom.client.createRoot
import space.kscience.plotly.plot

fun main() {
    val container = document.createElement("div")
    document.body!!.appendChild(container)

    val welcome = Welcome.create {
        name = "Kotlin/JS"
    }
    createRoot(container).render(welcome)
}