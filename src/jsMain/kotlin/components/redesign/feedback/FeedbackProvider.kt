package components.redesign.feedback

import react.FC
import react.PropsWithChildren
import react.createContext
import react.useState
import web.location.location

class Feedback internal constructor(private val setOpen: (String?) -> Unit) {
    fun open(pageName: String?) {
        setOpen(pageName)
    }
}

val FeedbackContext = createContext<Feedback>()

val FeedbackProvider = FC<PropsWithChildren> {
    val (feedbackOpen, setFeedbackOpen) = useState(false)
    val (feedbackPage, setFeedbackPage) = useState<FeedbackPage?>(null)

    FeedbackDialog {
        open = feedbackOpen
        onClose = { setFeedbackOpen(false) }
        page = feedbackPage
    }

    FeedbackContext.Provider {
        value = Feedback { pageName ->
            setFeedbackPage(pageName?.let { FeedbackPage(it, location.pathname) })
            setFeedbackOpen(true)
        }
        +it.children
    }
}
