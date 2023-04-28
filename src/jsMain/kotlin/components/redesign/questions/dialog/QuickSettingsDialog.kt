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
import web.prompts.*

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

    DialogMenu {
        open = props.open
        onClose = { props.onClose?.invoke() }
        if (props.canEdit) {
            DialogMenuHeader {
                text = "Quick settings"
            }
            DialogMenuItem {
                // TODO: Better text for "Unhide". Using something like "Show" would not make it clear what this button does (i.e. the question is currently hidden).
                text = if (props.question.visible) { "Hide" } else { "Unhide" }
                icon = if (props.question.visible) { HideIcon } else { UnhideIcon }
                disabled = editLock.running
                onClick = {
                    editLock {
                        val edit: EditQuestion = EditQuestionFlag(EditQuestionFieldType.VISIBLE, !props.question.visible)
                        Client.sendData(
                            "${props.question.urlPrefix}/edit",
                            edit,
                            onError = { showError(it) }) {
                        }
                    }
                }
            }
            DialogMenuItem {
                text = if (props.question.open) { "Close" } else { "Open" }
                icon = if (props.question.open) { LockIcon } else { UnlockIcon }
                disabled = editLock.running
                onClick = {
                    editLock {
                        val edit: EditQuestion = EditQuestionFlag(EditQuestionFieldType.OPEN, !props.question.open)
                        Client.sendData(
                            "${props.question.urlPrefix}/edit",
                            edit,
                            onError = { showError(it) }) {
                        }
                    }
                }
            }
            DialogMenuItem {
                text = if (props.question.resolved) {
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
                text = "Export to CSV..."
                onClick = { props.onExport?.invoke() }
            }
            DialogMenuItem {
                text = "Delete this question"
                icon = BinIcon
                variant = DialogMenuItemVariant.dangerous
                disabled = stale
                onClick = {
                    // TODO: Check for confirmation properly
                    if (confirm("Are you sure you want to delete the question? This action is irreversible. Deleting will also result in loss of all predictions made for this question.")) {
                        delete()
                    }
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
