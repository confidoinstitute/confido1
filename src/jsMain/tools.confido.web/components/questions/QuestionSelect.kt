package tools.confido.web.components.questions

import tools.confido.api.Api
import tools.confido.payloads.requests.SearchRequest
import tools.confido.payloads.responses.QuestionHitPayload
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.search.SearchScope
import tools.confido.search.searchTerms
import tools.confido.web.Client
import tools.confido.web.components.forms.TextInput
import tools.confido.web.components.useQuestion
import tools.confido.web.components.useRooms
import tools.confido.web.hooks.useDebounce
import tools.confido.web.utils.runCoroutine
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.datalist
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import web.autofill.AutoFill
import web.cssom.pct
import web.html.InputType
import web.html.text

external interface QuestionSelectProps : Props {
    var value: Ref<Question>?
    var onChange: ((Ref<Question>?) -> Unit)?

    /** Restricts the offered questions (e.g. binary-only). Applied to search hits. */
    var filter: ((Question) -> Boolean)?
    var placeholder: String?
    var disabled: Boolean?
}

/** The trailing "[id]" token of a formatted option (also accepted when pasted). */
private val OPTION_ID_RE = Regex("\\[([a-zA-Z0-9_-]+)\\]\$")

/**
 * A workspace-wide question picker: a text input with search-backed autocomplete
 * (Api.search over question titles, debounced), rendered as a native datalist so
 * keyboard and assistive-tech behavior come for free. Options carry the question id
 * in a trailing "[id]" token — selecting an option (or pasting such a string, or a
 * question URL's id) emits the ref; any other text emits null. The current value's
 * name resolves via its question/<id> subscription.
 */
val QuestionSelect = FC<QuestionSelectProps> { props ->
    val rooms = useRooms()
    val datalistId = useId()
    fun fmtq(q: Question) = "${q.name} [${q.id}]"
    val current = useQuestion(props.value)
    var touched by useState(false)
    var internalVal by useState("")
    // Display the configured question once its subscription delivers, until the
    // user starts editing.
    useEffect(current?.id) {
        if (!touched && current != null) internalVal = fmtq(current)
    }

    var options by useState<List<QuestionHitPayload>>(emptyList())
    val requestSeq = useRef(0)
    useDebounce(200, internalVal) {
        val seq = (requestSeq.current ?: 0) + 1
        requestSeq.current = seq
        val query = OPTION_ID_RE.replace(internalVal, "").trim()
        if (searchTerms(query).isEmpty()) {
            options = emptyList()
        } else runCoroutine {
            val hits = Client.call(Api.search(), SearchRequest(query, setOf(SearchScope.QUESTION_NAME))).questions
            if (requestSeq.current == seq)
                options = hits.filter { props.filter?.invoke(it.question) ?: true }
        }
    }

    div {
        css { width = 100.pct }
        TextInput {
            css { width = 100.pct }
            type = InputType.text
            placeholder = props.placeholder ?: "Search for a question…"
            list = datalistId.toString()
            value = internalVal
            disabled = props.disabled == true
            autoComplete = "off" as AutoFill
            onChange = { event ->
                val newVal = event.target.value
                touched = true
                internalVal = newVal
                val m = OPTION_ID_RE.find(newVal)
                props.onChange?.invoke(m?.let { Ref(it.groups[1]!!.value) })
            }
        }
        datalist {
            id = datalistId
            options.forEach { hit ->
                option {
                    value = fmtq(hit.question)
                    // Shown as the option's secondary line: the room the hit lives in.
                    +(rooms[hit.room.id]?.name ?: "")
                }
            }
        }
    }
}
