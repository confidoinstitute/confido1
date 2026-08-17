package tools.confido.web.components.redesign.questions.dialog

import tools.confido.web.components.useDisconnected
import tools.confido.web.Client
import tools.confido.web.components.*
import tools.confido.web.components.redesign.*
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.rooms.RoomContext
import tools.confido.web.hooks.*
import io.ktor.http.*
import tools.confido.payloads.requests.*
import react.*
import react.router.*
import tools.confido.api.Api
import tools.confido.question.*
import tools.confido.refs.ref
import tools.confido.web.utils.*
import tools.confido.extensions.ClientExtension

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
    val room = use(RoomContext)!!
    val navigate = useNavigate()

    val editLock = useCoroutineLock()
    var deleteConfirmOpen by useState(false)

    fun delete() = runCoroutine {
        Client.call(Api.questionDelete(props.question.ref))
        navigate(room.urlPrefix)
        props.onClose?.invoke()
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
                        Client.call(Api.questionSetState(props.question.ref), newState)
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
                disabled = useDisconnected()
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
                disabled = useDisconnected()
                onClick = {
                    props.onClose?.invoke()
                    deleteConfirmOpen = true
                }
            }
            DialogMenuSeparator {}
            ClientExtension.enabled.forEach {
                it.questionQuickSettingsExtra(props, this, props.onClose?: {})
            }
        }
        DialogMenuCommonActions {
            pageName = props.question.name
            onClose = props.onClose
        }
    }
}
