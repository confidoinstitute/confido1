package components.redesign.feedback

import components.redesign.*
import components.redesign.basic.Dialog
import components.redesign.forms.*
import components.showError
import csstype.*
import emotion.react.css
import emotion.styled.styled
import hooks.useCoroutineLock
import io.ktor.client.request.*
import react.dom.html.ReactHTML.option
import react.*


external interface FeedbackDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
    /** Optional. The feedback dialog will offer an option to include this context if not null. */
    var page: FeedbackPage?
}

data class FeedbackPage(
    /** A user-facing name of the page this feedback relates to. */
    val pageName: String,
    /** The path name (HTML DOM Location pathname) of the page this feedback relates to. */
    val pathName: String
)

private enum class SendFeedbackTo(val value: String, val optionName: String) {
    CONFIDO_DEVELOPERS("devs", "Confido Developers");

    companion object {
        fun findByValue(value: String): SendFeedbackTo? {
            return SendFeedbackTo.values().find { it.value == value }
        }
    }
}

private val FullWidthSelect = Select.styled { _, _ ->
    width = 100.pct
}

val FeedbackDialog = FC<FeedbackDialogProps> { props ->
    var feedbackText by useState("")
    var sendTo by useState(SendFeedbackTo.CONFIDO_DEVELOPERS)
    var connectToPage by useState(true)

    val sendingLock = useCoroutineLock()

    fun send() {
        val pathname = if (connectToPage) {
            props.page?.pathName
        } else {
            null
        }
        sendingLock {
            when (sendTo) {
                SendFeedbackTo.CONFIDO_DEVELOPERS -> {
                    Client.send("/feedback", req = {
                        this.parameter("url", pathname ?: "not provided")
                        setBody(feedbackText)
                    }, onError = { showError?.invoke(it) }) {
                        feedbackText = ""
                        props.onClose?.invoke()
                    }
                }
            }
        }
    }

    ThemeProvider {
        theme = { it.copy(colors = it.colors.copy(form = AltFormColors)) }
        Dialog {
            open = props.open
            onClose = props.onClose
            onAction = ::send
            fullSize = true
            title = "Send feedback"
            action = "Send"
            disabledAction = sendingLock.running

            FormSection {
                FormField {
                    title = "Your feedback"
                    MultilineTextInput {
                        css {
                            height = 8.em
                        }
                        placeholder = "Write your feedback"
                        value = feedbackText
                        disabled = sendingLock.running
                        onChange = { e -> feedbackText = e.target.value }
                    }
                }

                FormField {
                    title = "Send to"
                    FullWidthSelect {
                        value = sendTo.value
                        onChange = { e ->
                            sendTo = SendFeedbackTo.findByValue(e.target.value) ?: SendFeedbackTo.CONFIDO_DEVELOPERS
                        }
                        listOf(SendFeedbackTo.CONFIDO_DEVELOPERS).map { to ->
                            option {
                                value = to.value
                                +to.optionName
                            }
                        }
                    }
                }

                props.page?.let { context ->
                    FormSwitch {
                        label = "Connect feedback to page"
                        comment =
                            "The recipient of the feedback will know that you are referring to the page “${context.pageName}”."
                        checked = connectToPage
                        onChange = { e -> connectToPage = e.target.checked }
                    }
                }

                Button {
                    +"Send"
                    onClick = { send() }
                    disabled = sendingLock.running
                }
            }
        }
    }
}
