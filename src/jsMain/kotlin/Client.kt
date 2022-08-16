import components.Question
import components.QuestionList
import kotlinx.browser.document
import kotlinx.html.dom.append
import react.create
import react.dom.client.createRoot
import space.kscience.plotly.plot

fun main() {
    val container = document.createElement("div")
    document.body!!.appendChild(container)

    val questionList = QuestionList.create {
        questions = listOf(
            Question("question1","How are you?", visible = true),
            Question("question2","Is this good?", visible = true),
            Question("invisible_question","Can you not see this?", visible = false),
        )
    }
    val welcome = Welcome.create {
        name = "Confido"
    }
    createRoot(container).render(welcome)
}