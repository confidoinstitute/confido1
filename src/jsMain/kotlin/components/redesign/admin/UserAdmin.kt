package components.redesign.admin


import Client
import components.AppStateContext
import components.redesign.BinIcon
import components.redesign.EditIcon
import components.redesign.ConfirmDialog
import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.StatusChip
import components.showError
import csstype.*
import emotion.react.css
import hooks.EditEntityDialogProps
import hooks.useCoroutineLock
import hooks.useEditDialog
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.js.jso
import react.*
import react.dom.html.InputType
import react.dom.html.OlType
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ol
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.ReactHTML.ul
import tools.confido.refs.eqid
import tools.confido.utils.capFirst
import users.DeleteUserOptions
import users.User
import users.UserType
import utils.isEmailValid
import utils.runCoroutine
import utils.toDateTime

external interface EditUserDialogProps : EditEntityDialogProps<User> {
    var onTypeHelp: (()->Unit)?
}

val DeleteUserDialog = FC<EditEntityDialogProps<User>> { props ->
    val user = props.entity ?: return@FC
    
    ConfirmDialog {
        open = props.open
        onClose = { props.onClose?.invoke() }
        title = "Delete user"
        confirmText = "Delete"
        var commentHandling by useState("anonymize")
        onConfirm = {
            runCoroutine {
                val options = DeleteUserOptions(
                    deleteComments = commentHandling == "delete"
                )
                Client.sendData("/users/${user.id}", options, method = HttpMethod.Delete, onError = { showError(it) }) {}
                props.onClose?.invoke()
            }
        }
        Stack {
            css { gap = 16.px }
            +"Are you sure you want to delete user ${user.displayName}?"
            p {
                +"If the user has made any predictions, these will be preserved anonymized."
            }
            FormSection {
                FormField {
                    title = "Comment handling"
                    FormSwitch {
                        label = "Delete all comments"
                        checked = commentHandling == "delete"
                        onChange = { event -> commentHandling = if (event.target.checked) "delete" else "anonymize" }
                    }
                    comment = "If unchecked, comments will be converted to anonymous"
                }
            }
            p {
                +"This action cannot be undone."
            }
        }
    }
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
                        onInlineHelp = props.onTypeHelp

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

val UserTypeHelpDialog = FC<BaseDialogProps> { props->
    Dialog {
        title="User types"
        +props
        p {
            +"There are three types of users in Confido:"
            ul {
                li {
                    b{
                        css { color = userTypeColors[UserType.ADMIN]!!.color }
                        +"Admins"
                    }
                    +" have complete control over the workspace. They can access all rooms, see all group and individual "
                    +" predictions, create and modify users and more."
                }
                li {
                    b{
                        css { color = userTypeColors[UserType.MEMBER]!!.color }
                        +"Members"
                    }
                    +" are people that are part of the organization or group managing the workspace, for example the employees of your company."
                    +" They are allowed to create new rooms in the workspace and add members to these rooms."
                    +" Only Admins can add new Members to the workspace."
                }
                li {
                    b{
                        css { color = userTypeColors[UserType.GUEST]!!.color }
                        +"Guests"
                    }
                    +" are invited external collaborators. They can be both anonymous and non-anonymous. "
                    +" They can access only the room(s) they were added to and cannot create new rooms."
                    +" Guest accounts are usually not created here in the user administration, but: "
                    ol {
                        type = OlType.a
                        li {
                            +"By entering a new e-mail address in the "
                            i { +"Add member" }
                            +" dialog under the "
                            i { +"Room members" }
                            +" tab of any room."
                        }
                        li {
                            +"By joining the room via a shared invite link (which can also be created "
                            +" on the "
                            i { +"Room members" }
                            +" tab)."
                        }
                    }
                    +"Any room moderator is able to invite guests and create shared invite links, they do not need to be an admin."
                }
            }
        }
    }
}

val userTypeColors = mapOf(
    UserType.ADMIN to RoomPalette.red,
    UserType.MEMBER to RoomPalette.blue,
    UserType.GUEST to RoomPalette.gray,
)

val UserAdmin = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    if (!appState.isAdmin()) return@FC


    val activate = useCoroutineLock()
    var typeHelpOpen by useDialog(UserTypeHelpDialog)
    val editUserOpen = useEditDialog(EditUserDialog, jso { onTypeHelp = {typeHelpOpen = true} }, temporaryHide=typeHelpOpen)
    val deleteUserOpen = useEditDialog(DeleteUserDialog)
    val layoutMode = useContext(LayoutModeContext)

    PageHeader {
        title = "User management"
        navigateBack = "/"
    }

    Stack {
        css {
            ".phone &" { alignItems = AlignItems.stretch }
            ".tabplus &" { alignItems = AlignItems.center }
        }
        Stack {
            css {
                alignItems = AlignItems.stretch
                ".tabplus &" {
                    marginLeft = 20.px
                    marginRight = 20.px
                    maxWidth = 1000.px
                }
            }
            table {
                css(rowStyleTableCSS) {
                    "td" {
                        paddingLeft = 15.px
                    }
                    "th" {
                        paddingLeft = 15.px
                    }
                }
                thead {
                    tr {
                        th { +"Nick" }
                        th { +"E-mail" }
                        th { +"Type"; InlineHelpButton { onClick = { typeHelpOpen = true } } }
                        if (layoutMode >= LayoutMode.TABLET)
                            th { +"Last login" }
                        th {} // Edit button
                    }
                }
                tbody {
                    appState.users.values.filter { it.type != UserType.GHOST }.sortedWith(
                        compareBy(
                            {
                                when (it.type) {
                                    UserType.ADMIN -> 0
                                    UserType.MEMBER -> 1
                                    UserType.GUEST -> 2
                                    UserType.GHOST -> 3
                                }
                            },
                            { if (it.email != null) 0 else 1 },
                            { if (it.nick != null) 0 else 1 },
                            { it.nick },
                            { it.email },
                        )
                    ).map { user ->
                        tr {
                            key = user.id
                            td {
                                +(user.nick ?: "")
                            }
                            td { +(user.email ?: "") }
                            td { StatusChip {
                                color = userTypeColors[user.type]!!.color
                                text = when(user.type) {
                                    UserType.GHOST -> "Deleted user"
                                    else -> user.type.name.lowercase().capFirst()
                                }
                            } }
                            if (layoutMode >= LayoutMode.TABLET)
                                td { +(user.lastLoginAt?.epochSeconds?.toDateTime() ?: "Never") }
                            td {
                                Stack {
                                    direction = FlexDirection.row
                                    IconButton {
                                        disabled = stale
                                        onClick = { editUserOpen(user) }
                                        EditIcon{ size = 14 }
                                    }
                                    if (!(appState.session.user eqid user)) {
                                        IconButton {
                                            disabled = stale
                                            onClick = { deleteUserOpen(user) }
                                            css {
                                                color = RoomPalette.red.color
                                            }
                                            BinIcon { size = 14 }
                                        }
                                    }
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

}
