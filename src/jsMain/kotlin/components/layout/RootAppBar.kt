package components.layout

import components.AppStateContext
import components.ListItemNavigation
import components.MenuItemNavigation
import components.UserAvatar
import csstype.None
import csstype.number
import icons.Feedback
import icons.LogoutIcon
import icons.MenuIcon
import icons.SettingsIcon
import mui.material.*
import mui.system.responsive
import mui.system.sx
import org.w3c.dom.HTMLElement
import react.*
import react.router.useNavigate
import utils.themed

val FeedbackForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)

    var formOpen by useState(false)

    Dialog {
        open = formOpen
        onClose = {_, _ -> formOpen = false}
        DialogTitle {
            +"Send feedback"
        }
        DialogContent {
            DialogContentText {
                +"Have you found a bug? Do you think something can be improved? Please, let us know."
            }

            TextField {
                fullWidth = true
                multiline = true
                rows = 10
            }
        }
        DialogActions {
            Button {
                disabled = stale
                +"Send"
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
                ListItemText {
                    primary = ReactNode(user.nick ?: "Anonymous")
                    secondary = ReactNode(user.email ?: "Temporary guest")
                }
            }
        }

        Divider {}

        open = menuOpen
        onClose = closeMenu
        anchorEl = anchorElement.asDynamic()
        if (appState.isFullUser) {
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
            Typography {
                sx {
                    flexGrow = number(1.0)
                }
                +"Confido"
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