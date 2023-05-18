package components.redesign.admin


import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.*
import components.showError
import csstype.*
import emotion.react.css
import hooks.EditEntityDialogProps
import hooks.useEditDialog
import hooks.useCoroutineLock
import icons.AddIcon
import icons.EditIcon
import io.ktor.client.request.*
import kotlinx.datetime.Clock
import react.*
import react.dom.html.InputType
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.TdAlign
import react.dom.onChange
import tools.confido.refs.eqid
import tools.confido.state.appConfig
import users.User
import users.UserType
import utils.eventValue
import utils.isEmailValid
import utils.themed
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

    val submit = useCoroutineLock()

    fun submitUser() {
        if (errorEmptyEmail) return
        if (email.isNotEmpty() && !isEmailValid(email)) {
            errorBadEmail = true
            return
        }
        val user = User(
            id = props.entity?.id ?: "",
            nick = nick.ifEmpty { null },
            email = email.lowercase().ifEmpty { null },
            emailVerified = true,
            type = userType,
            password = null,
            active = active,
            createdAt = props.entity?.createdAt ?: Clock.System.now(),
            lastLoginAt = props.entity?.lastLoginAt
        )

        val url = if (newUser) "add" else "edit"
        submit {
            Client.sendData("/users/$url", user, req = {
                if (invite)
                    this.parameter("invite", 1)
            }, onError = {showError(it)}) {
                props.onClose?.invoke()
            }
        }
    }

    Dialog {
        open = props.open
        onClose = {  props.onClose?.invoke() }
        title = if (newUser) "New user" else "Edit user"
        action = if (newUser) "Create" else "Save"
        onAction = {
            submitUser()
        }
        disabledAction = stale || errorEmptyEmail || submit.running

        Form {
            FormSection {
                FormField {
                    title = "Name"
                    TextInput {
                        value = nick
                        onChange = { nick = it.target.value }
                    }
                    comment = "User's name or nickname. This will be visible to others. Not setting the name will make the user anonymous."
                }
                FormField {
                    title = "E-mail"
                    TextInput {
                        type = InputType.email
                        value = email
                        onChange = { email = it.target.value; errorBadEmail = false }
                    }
                    if (errorEmptyEmail) {
                        this.error = "Email address is required for non-guest accounts."
                    } else if (errorBadEmail) {
                        this.error = "Incorrect e-mail address"
                    }
                }

                if (!isSelf) {
                    FormField {
                        title = "User type"

                        Select {
                            value = userType.name
                            onChange = { event ->
                                userType = UserType.valueOf(event.target.value)
                            }
                            option {
                                value = "GUEST"
                                +"Guest"
                            }
                            option {
                                value = "MEMBER"
                                +"Member"
                            }
                            option {
                                value = "ADMIN"
                                +"Administrator"
                            }
                        }
                    }
                }
                if (newUser) {
                    FormSwitch {
                        disabled = email.isEmpty()
                        checked = invite
                        onChange = { event -> invite = event.target.checked }
                        label = "Send an e-mail invitation"
                    }
                } else if (!isSelf) {
                    FormSwitch {
                        label = "User is active"
                        checked = active
                        onChange = { event -> active = event.target.checked }
                    }
                }
            }
            DialogMainButton {
                disabled = stale || errorEmptyEmail || submit.running
                onClick = { submitUser() }
                +if (newUser) "Create user" else "Save"
            }
        }
    }
}

val UserAdmin = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    if (!appState.isAdmin()) return@FC

    val editUserOpen = useEditDialog(EditUserDialog)

    val activate = useCoroutineLock()

    PageHeader {
        title = "User management"
        navigateBack = "/"
    }

    Stack {
        css {
            margin = 20.px
        }
        table {
            css {
                "th" {
                    textAlign = TextAlign.left
                }
                "td, th" {
                    margin = 0.px
                    padding = Padding(4.px, 8.px)
                }
                fontFamily = sansSerif
                padding = 0.px
                margin = 0.px
                borderCollapse = BorderCollapse.collapse
            }
            thead {
                tr {
                    css { "th" { paddingBottom = 10.px; paddingTop = 5.px; } }
                    th { +"Nick" }
                    th { +"E-mail" }
                    th { +"Status" }
                    th { +"Active" }
                    th { +"Last login" }
                    th {} // Edit button
                }
            }
            tbody {
                css {
                    "&:before" {
                        display = Display.block
                        height  = 15.px
                    }
                    backgroundColor = NamedColor.white
                    "tr" {
                        backgroundColor = NamedColor.white
                    }
                    padding = 15.px
                }
                appState.users.values.sortedWith(compareBy(
                    {
                        when (it.type) {
                            UserType.ADMIN -> 0
                            UserType.MEMBER -> 1
                            UserType.GUEST -> 2
                        }
                    },
                    { if (it.email != null) 0 else 1 },
                    { if (it.nick != null) 0 else 1 },
                    { it.nick },
                    { it.email },
                )).map { user ->
                    tr {
                        key = user.id
                        td {
                            +(user.nick ?: "")
                        }
                        td { +(user.email ?: "") }
                        td { +user.type.name }
                       td {
                            Checkbox {
                                css {
                                    cursor = Cursor.default
                                }
                                val isSelf = user eqid appState.session.user
                                this.disabled = stale || isSelf
                                this.checked = user.active
                                readOnly = true
                                onClick = {
                                    it.preventDefault()
                                    //activate {
                                    //    if (!isSelf) {
                                    //        Client.sendData("/users/edit", user.copy(active = !user.active), onError = {showError(it)}) {}
                                    //    }
                                    //}
                                }
                            }
                        }
                        td { +(user.lastLoginAt?.epochSeconds?.toDateTime() ?: "Never") }
                        td {
                            IconButton {
                                disabled = stale
                                onClick = { editUserOpen(user) }
                                EditIcon {}
                            }
                        }
                    }
                }
            }
        }

    Button {
        this.key = "##add##"
        // TODO icon
        onClick = { editUserOpen(null) }
        +"Add user…"
    }
    }
}
