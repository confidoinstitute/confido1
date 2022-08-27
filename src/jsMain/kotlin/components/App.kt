package components
import kotlinext.js.getOwnPropertyNames
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.WebSocket
import react.*
import tools.confido.question.Question

val App = FC<Props> {
    var questions by useState<List<Question>>(emptyList())
    val webSocket = useRef<WebSocket>(null)

    useEffectOnce {
        webSocket.current = WebSocket("ws://localhost:8080/state")
        val ws = webSocket.current ?: error("WebSocket does not exist???")
        ws.onmessage = {
            questions = Json.decodeFromString(it.data.toString())
            Unit // This is not redundant, because assignment fails some weird type checks
        }
        cleanup {
            ws.close()
        }
    }

    QuestionList {
        this.questions = questions
    }
}