package components.redesign.questions.dialog

import Client
import components.*
import components.redesign.*
import components.redesign.basic.*
import components.rooms.*
import hooks.*
import io.ktor.http.*
import payloads.requests.*
import react.*
import react.router.*
import tools.confido.question.*
import utils.*

external interface QuestionQuickSettingsDialogProps : Props {
    var question: Question
    var open: Boolean
    var canEdit: Boolean
    var onOpenResolution: (() -> Unit)?
    var onExport: (() -> Unit)?
    var onEdit: (() -> Unit)?
    var onClose: (() -> Unit)?
}

val QuestionQuickSettingsDialog = FC<QuestionQuickSettingsDialogProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val navigate = useNavigate()

    val editLock = useCoroutineLock()
    var deleteConfirmOpen by useState(false)

    fun delete() = runCoroutine {
        Client.send(
            questionUrl(props.question.id),
            HttpMethod.Delete,
            onError = { showError(it) }) {
            navigate(room.urlPrefix)
            props.onClose?.invoke()
        }
    }

    // TODO: Permissions
    // TODO: Should we allow reopening resolved questions?

    ConfirmDialog {
        open = deleteConfirmOpen
        onClose = { deleteConfirmOpen = false }
        title = "Delete this question?"
        +"This will result in loss of all ${props.question.predictionTerminology.plural} made for “${props.question.name}”. This action cannot be undone."
        onConfirm = ::delete
    }

    DialogMenu {
        open = props.open
        onClose = { props.onClose?.invoke() }
        if (props.canEdit) {
            DialogMenuHeader {
                text = "Quick settings"
            }
            DialogMenuItem {
                text = when (props.question.state) {
                    QuestionState.DRAFT -> "Open"
                    QuestionState.OPEN -> "Close"
                    QuestionState.CLOSED -> "Open"
                    QuestionState.RESOLVED -> "Reopen"
                    QuestionState.CANCELLED -> "Reopen"
                }
                icon = if (props.question.open) { LockIcon } else { UnlockIcon }
                disabled = editLock.running
                onClick = {
                    editLock {
                        val newState: QuestionState = when (props.question.state) {
                            QuestionState.DRAFT -> QuestionState.OPEN
                            QuestionState.OPEN -> QuestionState.CLOSED
                            QuestionState.CLOSED -> QuestionState.OPEN
                            QuestionState.RESOLVED -> QuestionState.OPEN
                            QuestionState.CANCELLED -> QuestionState.OPEN
                        }
                        Client.sendData(
                            "${props.question.urlPrefix}/state",
                            newState,
                            onError = { showError(it) }) {
                        }
                    }
                }
            }
            DialogMenuItem {
                text = if (props.question.state == QuestionState.RESOLVED) {
                    "Change resolution"
                } else {
                    "Resolve"
                }
                icon = ResolveIcon
                onClick = {
                    props.onOpenResolution?.invoke()
                }
            }
            DialogMenuSeparator {}
            DialogMenuItem {
                text = "Edit this question"
                icon = EditIcon
                disabled = stale
                onClick = {
                    props.onEdit?.invoke()
                }
            }
            DialogMenuItem {
                icon = ExportIcon
                text = "Export to CSV..."
                onClick = { props.onExport?.invoke() }
            }
            DialogMenuItem {
                text = "Delete this question"
                icon = BinIcon
                variant = DialogMenuItemVariant.dangerous
                disabled = stale
                onClick = {
                    props.onClose?.invoke()
                    deleteConfirmOpen = true
                }
            }
            DialogMenuSeparator {}
        }
        DialogMenuCommonActions {
            pageName = props.question.name
            onClose = props.onClose
        }
    }
}
