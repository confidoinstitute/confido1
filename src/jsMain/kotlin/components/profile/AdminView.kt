package components.profile

import components.AppStateContext
import components.DemoEmailAlert
import components.DialogCloseButton
import csstype.pct
import hooks.EditEntityDialogProps
import hooks.useEditDialog
import icons.AddIcon
import icons.EditIcon
import io.ktor.client.request.*
import kotlinx.datetime.Clock
import mui.material.*
import mui.system.sx
import react.*
import react.dom.html.InputType
import react.dom.onChange
import tools.confido.refs.eqid
import users.User
import users.UserType
import utils.eventValue
import utils.isEmailValid
import utils.toDateTime

external interface EditUserDialogProps : EditEntityDialogProps<User> {
}

val EditUserDialog = FC<EditUserDialogProps> { props ->
    val (appState, stale) = useContext(AppStateContext)

    val newUser = props.entity == null
    val htmlId = useId()
    val isSelf = props.entity?.let { appState.session.user eqid it } ?: false

    var nick by useState(props.entity?.nick ?: "")
    var email by useState(props.entity?.email ?: "")
    var userType by useState(props.entity?.type ?: UserType.MEMBER)
    var active by useState(props.entity?.active ?: true)
    var invite by useState(false)

    val errorEmptyEmail = email.isEmpty() && userType != UserType.GUEST
    var errorBadEmail by useState(false)

    fun submitUser() {
        if (errorEmptyEmail) return
        if (email.isNotEmpty() && !isEmailValid(email)) {
            errorBadEmail = true
            return
        }
        val user = User(
            id = props.entity?.id ?: "",
            nick = nick.ifEmpty { null },
            email = email?.lowercase()?.ifEmpty { null },
            emailVerified = true,
            type = userType,
            password = null,
            active = active,
            createdAt = props.entity?.createdAt ?: Clock.System.now(),
            lastLoginAt = props.entity?.lastLoginAt
        )

        if (newUser) {
            Client.postData("/users/add", user) {
                if (invite)
                    this.parameter("invite", 1)
            }
        } else {
            Client.postData("/users/edit", user)
        }
        props.onClose?.invoke()
    }

    Dialog {
        open = props.open
        onClose = { _, _ -> props.onClose?.invoke() }
        DialogTitle {
            if (newUser) +"New user" else +"Edit user"
            DialogCloseButton {
                onClose = { props.onClose?.invoke() }
            }
        }

        DialogContent {
            TextField {
                label = ReactNode("Nickname")
                fullWidth = true
                value = nick
                onChange = { nick = it.eventValue()}
                margin = FormControlMargin.normal
                helperText = ReactNode("Not setting the nickname will make the user anonymous.")
            }
            TextField {
                label = ReactNode("E-mail")
                fullWidth = true
                type = InputType.email
                value = email
                onChange = { email = it.eventValue(); errorBadEmail = false }
                margin = FormControlMargin.normal
                if (errorEmptyEmail) {
                    this.error = true
                    helperText = ReactNode("Only guests can have an e-mail not set.")
                } else if (errorBadEmail) {
                    this.error = true
                }
            }

            if (!isSelf) {
                FormControl {
                    margin = FormControlMargin.normal
                    fullWidth = true
                    InputLabel {
                        this.id = htmlId + "_label"
                        +"Status"
                    }
                    val select: FC<SelectProps<String>> = Select
                    select {
                        labelId = htmlId + "_label"
                        value = userType.name
                        label = ReactNode("Answer Type")
                        onChange = { event, _ ->
                            userType = UserType.valueOf(event.target.value)
                        }
                        MenuItem {
                            value = "GUEST"
                            +"Guest"
                        }
                        MenuItem {
                            value = "MEMBER"
                            +"Member"
                        }
                        MenuItem {
                            value = "ADMIN"
                            +"Administrator"
                        }
                    }
                }
                if (newUser) {
                    FormGroup {
                        FormControlLabel {
                            label = ReactNode("Send e-mail link")
                            control = Checkbox.create {
                                disabled = email.isEmpty()
                                checked = invite
                            }
                            onChange = { _, checked -> invite = checked }
                        }
                    }
                } else if (!isSelf) {
                    FormGroup {
                        FormControlLabel {
                            label = ReactNode("User is active")
                            control = Checkbox.create {
                                checked = active
                            }
                            onChange = { _, checked -> active = checked }
                        }
                    }
                }
            }
            DialogActions {
                Button {
                    size = Size.small
                    disabled = stale || errorEmptyEmail
                    color = ButtonColor.primary
                    onClick = {submitUser()}
                    +"Confirm"
                }
            }
        }
    }
}

val AdminView = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    if (!appState.isAdmin()) return@FC

    val editUserOpen = useEditDialog(EditUserDialog)

    if (appState.appConfig.demoMode) {
        DemoEmailAlert {}
    }

    TableContainer {
        sx {
            width = 100.pct
        }
        component = Paper
        Table {
            TableHead {
                TableRow {
                    TableCell {+"Nick"}
                    TableCell {+"E-mail"}
                    TableCell {+"Status"}
                    TableCell {+"Active"}
                    TableCell {+"Last login"}
                    TableCell {}
                }
            }
            TableBody {
                appState.users.values.sortedWith(compareBy(
                        {when (it.type) {
                            UserType.ADMIN -> 0
                            UserType.MEMBER -> 1
                            UserType.GUEST -> 2
                        } },
                        {if (it.email != null) 0 else 1},
                        {if (it.nick != null) 0 else 1},
                        {it.nick},
                        {it.email},
                    )).map { user ->
                    TableRow {
                        key = user.id
                        TableCell {+(user.nick ?: "")}
                        TableCell {+(user.email ?: "")}
                        TableCell {+user.type.name}
                        TableCell {
                            Checkbox {
                                val isSelf = user eqid appState.session.user
                                this.disabled = stale || isSelf
                                this.checked = user.active
                                onClick = {
                                    if (!isSelf) {
                                        Client.postData("/users/edit", user.copy(active = !user.active))
                                    }
                                }
                            }
                        }
                        TableCell {+(user.lastLoginAt?.epochSeconds?.toDateTime() ?: "Never")}
                        TableCell {
                            IconButton {
                                disabled = stale
                                onClick = {editUserOpen(user)}
                                EditIcon {}
                            }
                        }
                    }
                }
            }
        }
    }

    Button {
        this.key = "##add##"
        this.startIcon = AddIcon.create()
        this.color = ButtonColor.primary
        this.disabled = stale
        onClick = { editUserOpen(null) }
        +"Add user…"
    }
}