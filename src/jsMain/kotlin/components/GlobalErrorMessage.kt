package components

import csstype.ms
import mui.material.AlertColor
import mui.material.SnackbarCloseReason
import react.*
import web.timers.setTimeout
import kotlin.time.Duration.Companion.milliseconds

var showError: ((String) -> Unit)? = null

val GlobalErrorMessage = FC<PropsWithChildren> {

    var errorOpen by useState(false)
    var errorMessage by useState("")

    var queuedErrors by useState(emptyList<String>())

    useEffectOnce {
        if (showError != null)
            console.error("There can be only one GlobalErrorMessage component!")

        showError = { message ->
            console.log("Queueing error", message)
            queuedErrors = queuedErrors + listOf(message)
        }

        cleanup {
            showError = null
        }
    }

    useEffect(queuedErrors, errorOpen) {
        if (!errorOpen && queuedErrors.isNotEmpty()) {
            setTimeout(100.milliseconds) {
                errorOpen = true
                errorMessage = queuedErrors[0]
                queuedErrors = queuedErrors.drop(1)
            }
        }
    }


    AlertSnackbar {
        severity = AlertColor.error
        open = errorOpen
        autoHideDuration = 6000
        onClose = {_, reason -> if (reason != SnackbarCloseReason.clickaway) errorOpen = false}
        +errorMessage
    }
    Fragment {
        +it.children
    }
}
