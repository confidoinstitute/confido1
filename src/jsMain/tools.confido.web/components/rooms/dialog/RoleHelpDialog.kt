package tools.confido.web.components.rooms.dialog

import tools.confido.web.components.basic.*
import tools.confido.web.components.rooms.RoomContext
import web.cssom.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.ul
import tools.confido.rooms.RoomPermission
import tools.confido.state.appConfig

external interface RoleHelpDialogProps : BaseDialogProps {
    /** List only the roles that can be given to guests — the ones offered when adding
     *  members or creating invitation links. */
    var guestRolesOnly: Boolean?
}

/**
 * Explains the room roles, in [appConfig.roles] order. A role's freeform description
 * comes from the roles config; a (custom) role without one falls back to listing
 * its permissions, so the dialog never shows a role unexplained.
 */
val RoleHelpDialog = FC<RoleHelpDialogProps> { props ->
    // Group rooms strip question permissions from every membership; leave them
    // out of the fallback lists there too.
    val isGroup = use(RoomContext)?.isGroup == true
    val roles = appConfig.roles.filter { props.guestRolesOnly != true || it.availableToGuests }

    Dialog {
        title = "Room roles"
        open = props.open
        onClose = props.onClose
        p {
            +"A member's role decides what they can see and do in this room:"
        }
        ul {
            roles.map { role ->
                li {
                    key = role.id
                    css { marginBottom = 8.px }
                    b { +role.name }
                    if (role.description.isNotBlank()) {
                        +" — ${role.description}"
                    } else {
                        val perms = role.effectivePermissions
                            .filter { !isGroup || it !in RoomPermission.QUESTION_PERMISSIONS }
                            .map { it.description.ifBlank { it.id } }
                        +if (perms.isEmpty()) " — no permissions." else " can:"
                        if (perms.isNotEmpty()) ul { perms.map { li { key = it; +it } } }
                    }
                }
            }
        }
        if (props.guestRolesOnly != true && appConfig.roles.any { !it.availableToGuests }) {
            p {
                +"Guests can only be given the roles "
                +appConfig.roles.filter { it.availableToGuests }.joinToString(", ") { it.name }
                +"."
            }
        }
        p {
            +"You can only give others roles whose permissions you have yourself."
        }
    }
}
