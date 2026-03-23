package components.redesign.rooms.dialog

import components.AppStateContext
import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.basic.showError
import components.redesign.forms.*
import components.redesign.rooms.RoomContext
import web.cssom.*
import emotion.react.css
import hooks.EditEntityDialogProps
import hooks.useCoroutineLock
import payloads.requests.CreateNewInvite
import react.*
import react.dom.html.ReactHTML.option
import rooms.*
import tools.confido.state.appConfig
import users.UserType
import web.html.ButtonType
import web.html.submit

external interface EditInviteDialogProps : EditEntityDialogProps<InviteLink>

val EditInviteDialog = FC<EditInviteDialogProps> { props ->
    val i = props.entity
    val room = use(RoomContext)!!
    val stale = use(AppStateContext)!!.stale

    // Default to the first guest-available role, or the first role
    val defaultRole = appConfig.roles.firstOrNull { it.availableToGuests } ?: appConfig.roles.first()

    // Invitation link values
    var description by useState(i?.description ?: "Shared Invite Link")
    var role by useState(i?.role ?: defaultRole)
    var nicknamePolicy by useState(i?.nicknamePolicy ?: InviteFieldPolicy.OPTIONAL)
    var emailPolicy by useState(i?.emailPolicy ?: InviteFieldPolicy.OPTIONAL)
    var targetUserType by useState(i?.targetUserType ?: UserType.GUEST)
    var preventDuplicateNicknames by useState(i?.preventDuplicateNicknames ?: false)
    var linkState by useState(i?.state ?: InviteLinkState.ENABLED)

    // Reset preventDuplicateNicknames when nicknamePolicy is DONT_ASK
    useEffect(nicknamePolicy) {
        if (nicknamePolicy == InviteFieldPolicy.DONT_ASK) {
            preventDuplicateNicknames = false
        }
    }


    val submit = useCoroutineLock()

    fun submitInviteLink() = submit {
        if (i == null) {
            val invite = CreateNewInvite(description, role, nicknamePolicy, emailPolicy, targetUserType, preventDuplicateNicknames)
            Client.sendData("${room.urlPrefix}/invites/create", invite, onError = { showError(it)}) {
                props.onClose?.invoke()
            }
        } else {
            val invite = i.copy(
                description = description,
                role = role,
                nicknamePolicy = nicknamePolicy,
                emailPolicy = emailPolicy,
                targetUserType = targetUserType,
                preventDuplicateNicknames = preventDuplicateNicknames,
                state = linkState,
            )
            Client.sendData("${room.urlPrefix}/invites/edit", invite, onError = { showError(it)}) {
                props.onClose?.invoke()
            }
        }
    }


    Dialog {
        open = props.open
        onClose = props.onClose
        title = if (props.entity != null) "Edit this invitation link" else "Create an invitation link"
        action = if (props.entity != null) "Save" else "Create"
        disabledAction = (stale)
        onAction = { submitInviteLink() }

        Form {
            onSubmit = { submitInviteLink() }

            FormSection {
               title = "Properties"

                FormField {
                    title = "Description"
                    TextInput {
                        placeholder = "Shared Invite Link"
                        value = description
                        onChange = { e -> description = e.target.value }
                    }
                    comment = "An internal description for moderation purposes."
                }

                FormField {
                    title = "Member role"
                    Select {
                        css {
                            width = important(100.pct)
                        }
                        value = role.id
                        onChange = { event ->
                            role = appConfig.roleById(event.target.value)
                                ?: error("Unknown role: ${event.target.value}")
                        }
                        // Show guest-available roles for invite links
                        appConfig.roles.filter { it.availableToGuests }.map { r ->
                            option {
                                this.value = r.id
                                +r.name
                            }
                        }
                    }
                    comment = "Any new user that joins via this link will get this role."
                }
            }

            FormSection {
                title = "User Settings"

                FormField {
                    title = "User Type"
                    Select {
                        css {
                            width = important(100.pct)
                        }
                        value = targetUserType.name
                        onChange = { event ->
                            targetUserType = UserType.valueOf(event.target.value)
                        }
                        option {
                            value = UserType.GUEST.name
                            +"Guest (limited to this room)"
                        }
                        option {
                            value = UserType.MEMBER.name
                            +"Member (full workspace access)"
                        }
                    }
                }

            }
            FormSection {
                title = "Identification"
                
                FormField {
                    title = "Ask for nickname"
                    Select {
                        css {
                            width = important(100.pct)
                        }
                        value = nicknamePolicy.name
                        onChange = { event ->
                            nicknamePolicy = InviteFieldPolicy.valueOf(event.target.value)
                        }
                        option {
                            value = InviteFieldPolicy.DONT_ASK.name
                            +"Don't ask"
                        }
                        option {
                            value = InviteFieldPolicy.OPTIONAL.name
                            +"Optional"
                        }
                        option {
                            value = InviteFieldPolicy.REQUIRED.name
                            +"Required"
                        }
                    }
                    comment = "Whether users can/must provide a nickname when joining."
                }

                // Only show "Prevent duplicate nicknames" switch when nickname is not DONT_ASK
                if (nicknamePolicy != InviteFieldPolicy.DONT_ASK) {
                    FormSwitch {
                        label = "Prevent duplicate nicknames"
                        checked = preventDuplicateNicknames
                        onChange = { preventDuplicateNicknames = it.target.checked }
                        comment = "For members, duplicates are checked across workspace. For guests, within room only."
                    }
                }

                FormField {
                    title = "Ask for e-mail"
                    Select {
                        css {
                            width = important(100.pct)
                        }
                        value = emailPolicy.name
                        onChange = { event ->
                            emailPolicy = InviteFieldPolicy.valueOf(event.target.value)
                        }
                        option {
                            value = InviteFieldPolicy.DONT_ASK.name
                            +"Don't ask"
                        }
                        option {
                            value = InviteFieldPolicy.OPTIONAL.name
                            +"Optional"
                        }
                        option {
                            value = InviteFieldPolicy.REQUIRED.name
                            +"Required"
                        }
                    }
                    comment = "Whether users can/must provide an e-mail when joining."
                }

            }

            if (i != null)
            FormSection {
                title = "Access"
                FormSwitch {
                    label = "Prevent new users from joining"
                    checked = linkState != InviteLinkState.ENABLED
                    onChange = { linkState = if(it.target.checked) InviteLinkState.DISABLED_JOIN else InviteLinkState.ENABLED }
                }
                FormSwitch {
                    label = "Prevent already joined users from participating"
                    checked = linkState == InviteLinkState.DISABLED_FULL
                    onChange = { linkState = if (it.target.checked) InviteLinkState.DISABLED_FULL else InviteLinkState.DISABLED_JOIN }
                }
            }

            Stack {
                Button {
                    type = ButtonType.submit
                    css {
                        display = Display.block
                        margin = Margin(20.px, 20.px, 10.px)
                        fontWeight = integer(500)
                    }
                    if (props.entity != null)
                        +"Save"
                    else
                        +"Create invite link"
                    disabled = (stale)
                }
            }
        }
    }
}
