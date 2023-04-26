package components

import mui.material.AlertColor
import mui.material.SnackbarCloseReason
import react.*
import web.timers.setTimeout
import kotlin.time.Duration.Companion.milliseconds

enum class MessageType { SUCCESS, INFO, WARNING, ERROR }
data class Message(val text: String, val type: MessageType = MessageType.INFO)

var showMessage: ((Message) -> Unit) = {}
fun showError(msg: String) = showMessage(Message(msg, MessageType.ERROR))



val GlobalErrorMessage = FC<PropsWithChildren> {

    var errorOpen by useState(false)
    var currentMsg by useState<Message>()

    var queuedMsgs by useState(emptyList<Message>())

    useEffectOnce {
        showMessage = { message ->
            console.log("Queueing error", message)
            queuedMsgs = queuedMsgs + listOf(message)
        }

        cleanup {
            showMessage = {}
        }
    }

    useEffect(queuedMsgs, errorOpen) {
        if (!errorOpen && queuedMsgs.isNotEmpty()) {
            setTimeout(100.milliseconds) {
                errorOpen = true
                currentMsg = queuedMsgs[0]
                queuedMsgs = queuedMsgs.drop(1)
            }
        }
    }


    currentMsg?.let { msg ->
        AlertSnackbar {
            severity = when (msg.type) {
                MessageType.ERROR -> AlertColor.error
                MessageType.SUCCESS -> AlertColor.success
                MessageType.INFO -> AlertColor.info
                MessageType.WARNING -> AlertColor.warning
            }
            open = errorOpen
            autoHideDuration = 6000
            onClose = { _, reason -> if (reason != SnackbarCloseReason.clickaway) errorOpen = false }
            +msg.text
        }
    }
    Fragment {
        +it.children
    }
}
