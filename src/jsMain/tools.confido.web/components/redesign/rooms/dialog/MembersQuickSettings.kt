package tools.confido.web.components.redesign.rooms.dialog

import tools.confido.web.components.usePermission
import tools.confido.web.components.useDisconnected
import tools.confido.web.components.redesign.*
import tools.confido.web.components.redesign.basic.DialogMenu
import tools.confido.web.components.redesign.basic.DialogMenuHeader
import tools.confido.web.components.redesign.basic.DialogMenuItem
import tools.confido.web.components.redesign.basic.DialogMenuItemVariant
import tools.confido.web.components.redesign.presenter.PresenterContext
import tools.confido.web.components.redesign.rooms.RoomContext
import web.cssom.px
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.use
import react.useState
import tools.confido.rooms.InviteLink
import tools.confido.rooms.RoomPermission
import tools.confido.refs.ref
import tools.confido.state.InviteLinkPV

external interface MemberQuickSettingsDialogProps : Props {
    var open: Boolean
    var hasEmail: Boolean
    var name: String
    var canDelete: Boolean
    var onClose: (() -> Unit)?
    var onMail: (() -> Unit)?
    var onDelete: (() -> Unit)?
}

external interface InvitationQuickSettingsDialogProps : Props {
    var link: InviteLink
    var hasUsers: Boolean
    var open: Boolean
    var onClose: (() -> Unit)?
    var onCopy: (() -> Unit)?
    var onEdit: (() -> Unit)?
    var onDelete: ((Boolean) -> Unit)?
}

val MemberQuickSettingsDialog = FC<MemberQuickSettingsDialogProps> {props ->

    DialogMenu {
        open = props.open
        onClose = { props.onClose?.invoke() }

        if (props.hasEmail)
            DialogMenuItem {
                text = "Write an email to ${props.name}"
                icon = MailIcon
                onClick = {
                    props.onClose?.invoke()
                    props.onMail?.invoke()
                }
            }

        if (props.canDelete)
        DialogMenuItem {
            text = "Remove ${props.name} from this room"
            icon = LogoutIcon
            variant = DialogMenuItemVariant.dangerous
            onClick = {
                props.onClose?.invoke()
                props.onDelete?.invoke()
            }
            disabled = useDisconnected()
        }
    }
}

val InvitationQuickSettingsDialog = FC<InvitationQuickSettingsDialogProps> {props ->
    val room = use(RoomContext)!!
    val presenterCtl = use(PresenterContext)!!

    var deleting by useState(false)

    DialogMenu {
        open = props.open && !deleting
        onClose = { deleting = false; props.onClose?.invoke() }
        DialogMenuHeader {
            text = "Invitation link settings"
        }
        DialogMenuItem {
            text = "Copy link"
            icon = CopyIcon
            disabled = useDisconnected()
            onClick = {
                props.onClose?.invoke()
                props.onCopy?.invoke()
            }
        }
        DialogMenuItem {
            text = "Show link & QR code in presentation mode"
            icon = PresenterIcon
            disabled = useDisconnected()
            onClick = {
                props.onClose?.invoke()
                presenterCtl.offer(InviteLinkPV(room.ref, props.link.id))
            }
        }
        DialogMenuItem {
            text = "Edit this invitation link"
            icon = EditIcon
            disabled = useDisconnected()
            onClick = {
                props.onClose?.invoke()
                props.onEdit?.invoke()
            }
        }
        DialogMenuItem {
            text = "Delete this invitation link"
            icon = BinIcon
            disabled = useDisconnected()
            variant = DialogMenuItemVariant.dangerous
            onClick = {
                if (props.hasUsers)
                    deleting = true
                else {
                    props.onClose?.invoke()
                    props.onDelete?.invoke(true)
                }
            }
        }
    }

    DialogMenu {
        open = props.open && deleting
        onClose = { deleting = false; props.onClose?.invoke() }
        DialogMenuHeader {
            text = "Delete invitation link"
        }
        div {
            css {
                fontSize = 14.px
                lineHeight = 17.px
                margin = 20.px
            }
            +"There are members who joined this room via this invitation link. By deleting it, you must choose whether these members are kept and become permanent members or are also removed."
        }
        if (usePermission(room, RoomPermission.MANAGE_MEMBERS)) {
            DialogMenuItem {
                text = "Keep invited users as members"
                // TODO icon
                disabled = useDisconnected()
                variant = DialogMenuItemVariant.dangerous
                onClick = {
                    props.onClose?.invoke()
                    props.onDelete?.invoke(true)
                }
            }
            DialogMenuItem {
                text = "Remove invited users from the room"
                // TODO icon
                disabled = useDisconnected()
                variant = DialogMenuItemVariant.dangerous
                onClick = {
                    props.onClose?.invoke()
                    props.onDelete?.invoke(false)
                }
            }
        }
    }
}
