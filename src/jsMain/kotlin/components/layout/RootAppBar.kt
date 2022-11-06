package components.layout

import components.*
import csstype.Color
import csstype.None
import csstype.number
import emotion.react.css
import icons.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mui.material.*
import mui.system.responsive
import mui.system.sx
import org.w3c.dom.HTMLElement
import react.*
import react.dom.html.ButtonType
import react.dom.onChange
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.sup
import react.router.useLocation
import react.router.useNavigate
import tools.confido.state.appConfig
import utils.eventValue
import utils.themed
import kotlin.coroutines.EmptyCoroutineContext

val FeedbackForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val location = useLocation()

    var formOpen by useState(false)
    var feedback by useState("")

    Dialog {
        open = formOpen
        onClose = {_, _ -> formOpen = false}
        DialogTitle {
            +"Send feedback"
        }
        form {
            onSubmit = {
                it.preventDefault()
                CoroutineScope(EmptyCoroutineContext).launch {
                    Client.httpClient.post("/feedback") {
                        this.parameter("url", location.pathname)
                        setBody(feedback)
                    }
                    formOpen = false
                }
            }
            DialogContent {
                DialogContentText {
                    +"Have you found a bug? Do you think something can be improved? Please, let us know."
                }

                TextField {
                    fullWidth = true
                    multiline = true
                    value = feedback
                    onChange = {feedback = it.eventValue()}
                    rows = 10
                }
            }
            DialogActions {
                Button {
                    type = ButtonType.submit
                    disabled = stale
                    +"Send"
                }
            }
        }
    }

    Button {
        sx {
            marginLeft = themed(2)
        }
        disabled = stale
        onClick = {formOpen = true}
        color = ButtonColor.inherit
        +"Feedback"
    }
}

val ProfileMenu = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val user = appState.session.user ?: return@FC
    var anchorElement by useState<HTMLElement?>(null)
    val navigate = useNavigate()

    var menuOpen by useState(false)
    val closeMenu: (Any) -> Unit = useMemo() {
        {menuOpen = false}
    }

    IconButton {
        onClick = {menuOpen = true; anchorElement = it.currentTarget}
        UserAvatar {
            this.user = user
        }
    }
    Menu {

        ListItem {
            ListItemAvatar {
                +userListItemText(user)
            }
        }

        if (appState.isAdmin()) {
            ListSubheader {
                key = "admin_subheader"
                +"Administration"
            }
            MenuItemNavigation {
                key = "admin_user_view"
                to = "/admin/users"
                this.onNavigate = closeMenu
                ListItemText {
                    primary = ReactNode("User overview")
                }
            }
        }

        Divider {}

        open = menuOpen
        onClose = closeMenu
        anchorEl = anchorElement.asDynamic()
        MenuItemNavigation {
            key = "user_settings"
            to = "/profile"
            this.onNavigate = closeMenu

            ListItemIcon {
                SettingsIcon {}
            }
            ListItemText {
                primary = ReactNode("User settings")
            }
        }
        MenuItem {
            key = "log_out"
            disabled = stale
            ListItemIcon {
                LogoutIcon {}
            }
            ListItemText {
                primary = ReactNode("Log out")
            }
            onClick = {
                Client.post("/logout") {
                    navigate("/")
                }
            }
        }
    }
}

external interface RootAppBarProps : Props {
    var hasDrawer: Boolean
    var onDrawerOpen: (() -> Unit)?
}

val RootAppBar = FC<RootAppBarProps> { props ->
    val (_, stale) = useContext(AppStateContext)

    AppBar {
        position = AppBarPosition.fixed
        Toolbar {
            if (props.hasDrawer) {
                IconButton {
                    sx {
                        display = responsive(permanentBreakpoint to None.none)
                        marginRight = themed(2)
                    }
                    color = IconButtonColor.inherit
                    onClick = { props.onDrawerOpen?.invoke() }
                    MenuIcon()
                }
            }
            smallLogo {}
            Typography {
                sx {
                    marginLeft = themed(1)
                    flexGrow = number(1.0)
                }
                +"Confido"
                if (appConfig.betaIndicator)
                    sup{
                        css { color = Color("red") }
                        +"beta"
                    }
            }
            if (stale) {
                Chip {
                    this.color = ChipColor.error
                    this.label = ReactNode("Disconnected")
                }
            }
            FeedbackForm {}
            ProfileMenu {}
        }
    }
}