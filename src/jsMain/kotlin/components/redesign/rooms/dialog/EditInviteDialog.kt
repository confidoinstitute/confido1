package components.redesign.rooms.dialog

import components.AppStateContext
import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.forms.*
import components.rooms.RoomContext
import components.showError
import csstype.*
import emotion.react.css
import hooks.EditEntityDialogProps
import hooks.useCoroutineLock
import payloads.requests.CreateNewInvite
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.option
import rooms.*
import tools.confido.state.FeatureFlag
import tools.confido.state.appConfig

external interface EditInviteDialogProps : EditEntityDialogProps<InviteLink>

val EditInviteDialog = FC<EditInviteDialogProps> { props ->
    val i = props.entity
    val room = useContext(RoomContext)
    val stale = useContext(AppStateContext).stale

    // Invitation link values
    var description by useState(i?.description ?: "Shared Invite Link")
    var role by useState(i?.role ?: Forecaster)
    var anonymous by useState(i?.allowAnonymous ?: false)
    var linkState by useState(i?.state ?: InviteLinkState.ENABLED)


    val submit = useCoroutineLock()

    fun submitInviteLink() = submit {
        if (i == null) {
            val invite = CreateNewInvite(description, role, anonymous)
            Client.sendData("${room.urlPrefix}/invites/create", invite, onError = { showError?.invoke(it)}) {
                props.onClose?.invoke()
            }
        } else {
            val invite = i.copy(
                description = description,
                role = role,
                allowAnonymous = anonymous,
                state = linkState,
            )
            Client.sendData("${room.urlPrefix}/invites/edit", invite, onError = { showError?.invoke(it)}) {
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
                            role = when(event.target.value) {
                                "viewer" -> Viewer
                                "forecaster" -> Forecaster
                                "question_writer" -> QuestionWriter
                                else -> error("This should not happen!")
                            }
                        }
                        ( mapOf("viewer" to "Viewer", "forecaster" to "Forecaster")
                                        + if (FeatureFlag.QUESTION_WRITER_ROLE in appConfig.featureFlags)
                                    mapOf("question_writer" to "Question Writer")
                                else emptyMap()
                        ).map { (value, label) ->
                            option {
                                this.value = value
                                +label
                            }
                        }
                    }
                    comment = "Any new user that joins via this link will get this role."
                }
            }

            FormSection {
                title = "Identification"

                RadioGroup<Boolean>()() {
                    title = "User identification"
                    options = listOf(
                        true to "Allow anonymous access (using only a nickname)",
                        false to "Require e-mail",
                    )
                    value = anonymous
                    onChange = {value -> anonymous = value}
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
