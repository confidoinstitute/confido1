package components.redesign.basic

import browser.*
import components.AppStateContext
import components.redesign.AboutIcon
import components.redesign.IconProps
import components.redesign.SidebarContext
import components.redesign.feedback.FeedbackMenuItem
import components.redesign.forms.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.transitions.*
import csstype.*
import dom.html.*
import emotion.react.*
import hooks.useEffectNotFirst
import kotlinx.js.jso
import react.*
import react.dom.*
import react.dom.html.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.svg.*
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.router.dom.Link
import tools.confido.utils.generateId
import web.location.*
import web.storage.*

external interface DialogProps : BaseDialogProps, PropsWithChildren, PropsWithRef<HTMLElement> {
    var title: String
    var action: String
    var disabledAction: Boolean
    var onAction: (() -> Unit)?
    var fullSize: Boolean
}

val Dialog = ForwardRef<HTMLElement, DialogProps> { props, fRef ->
    DialogCore {
        ref = fRef
        open = props.open
        onClose = props.onClose
        fullSize = props.fullSize
        header = DialogHeader.create {
            this.onClose = props.onClose
            this.title = props.title
            this.action = props.action
            this.disabledAction = props.disabledAction
            this.onAction = props.onAction
        }
        +props.children
    }
}

fun <P: BaseDialogProps> ChildrenBuilder.useDialog(comp: FC<P>, props: P = jso{}): StateInstance<Boolean> {
    val isOpenState = useState(false)
    var isOpen by isOpenState
    comp {
        +props
        open = isOpen
        onClose = { isOpen = false }
    }
    return isOpenState
}

external interface DialogMenuProps : PropsWithChildren, PropsWithRef<HTMLElement> {
    var open: Boolean
    var onClose: (() -> Unit)?

    /** Defaults to true. */
    var hasCloseButton: Boolean?
}

val DialogMenu = ForwardRef<HTMLElement, DialogMenuProps> { props, fRef ->
    DialogCore {
        this.ref = fRef
        this.open = props.open
        this.onClose = props.onClose
        Stack {
            div { className = ClassName("dialogmenu-start") }
            +props.children



            if (props.hasCloseButton != false) {
                ButtonBase {
                    css {
                        border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
                        margin = 15.px
                        padding = 10.px
                        alignSelf = AlignSelf.stretch

                        fontFamily = sansSerif
                        fontStyle = FontStyle.normal
                        fontWeight = integer(400)
                        fontSize = 17.px
                        lineHeight = 20.px
                        color = Color("#999999")
                        backgroundColor = Color("#FFFFFF")
                        textAlign = TextAlign.center

                        hover {
                            backgroundColor = Color("#FBFBFB")
                        }

                        ".ripple" {
                            backgroundColor = Color("#999999")
                        }
                    }
                    +"Close"
                    onClick = { props.onClose?.invoke() }
                }
            }
        }
    }
}

external interface BaseDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
}
/** State that is reset each time dialog is opened. */
fun <T> useDialogState(init: T, props: BaseDialogProps): StateInstance<T> {
    val si = useState(init)
    useEffectNotFirst(props.open) {
        if (props.open) si.component2()(init)
    }
    return si
}
fun <P: BaseDialogProps> dialogStateWrapper(component: FC<P>, displayName: String?=null): FC<P> {
    return FC(displayName ?: component.displayName?.let { it + "DSW" } ?: "DialogStateWrapper") { props ->
        var key by useState(generateId())
        useEffectNotFirst(props.open) {
            // No need to do this on close. Also, if we do this on close,
            // it breaks the close animation on mobile.
            if (props.open) key = generateId()
        }
        component {
            this.key = key
            +props
        }
    }
}
external interface DialogCoreProps : PropsWithChildren, PropsWithRef<HTMLElement>, BaseDialogProps {
    var header: ReactNode
    var fullSize: Boolean
}

val DialogCore = FC<DialogCoreProps> { props ->
    val nodeRef = useRef<HTMLElement>()
    val layoutMode = useContext(LayoutModeContext)
    val sidebarState = useContext(SidebarContext)

    // This can be null in case we are not within a layout that has a sidebar.
    @Suppress("SENSELESS_COMPARISON")
    val sidebarOffset = if (sidebarState != null) {
        sidebarState.marginOffset
    } else {
        0.px
    }

    val theme = hooks.useTheme()
    useBackdrop(props.open) {
        props.onClose?.invoke()
    }
    Slide {
        appear = true
        `in` = props.open
        mountOnEnter = true
        unmountOnExit = true
        direction = SlideDirection.up
        timeout = 250
        this.nodeRef = nodeRef
        Fragment { +createPortal(
            Stack.create {
                ref = nodeRef
                css {
                    height = 100.pct
                    width = 100.pct
                    zIndex = integer(2100)
                    position = Position.fixed
                    bottom = 0.px
                    if (layoutMode >= LayoutMode.TABLET) {
                        width = 640.px
                        left = "calc((100vw - 640px + $sidebarOffset) / 2)" as Length
                        right = "calc((100vw - 640px - $sidebarOffset) / 2)" as Length
                    } else {
                        left = 0.px
                        right = 0.px
                    }
                }
                if (!props.fullSize || layoutMode >= LayoutMode.TABLET)
                div {
                    css {
                        flexGrow = number(if (props.fullSize) 0.0 else 1.0)
                        flexBasis = 44.px
                        flexShrink = number(0.0)
                    }
                    onClick = { props.onClose?.invoke() }
                }
                Stack {
                    direction = FlexDirection.row
                    css {
                        alignItems = AlignItems.center
                        flexShrink = number(0.0)
                        flexGrow = number(0.0)
                        backgroundColor = Color("#FFFFFF")
                        if (!props.fullSize  || layoutMode >= LayoutMode.TABLET) {
                            borderTopLeftRadius = 10.px
                            borderTopRightRadius = 10.px
                        }
                        minHeight = 12.px
                        maxHeight = 44.px
                        fontWeight = integer(600)
                    }
                    +props.header
                }
                Stack {
                    css {
                        flexShrink = number(1.0)
                        minHeight = 0.px
                        justifyContent = JustifyContent.flexStart
                        if (props.fullSize) {
                            flexGrow = number(1.0)
                        }
                        overflow = Auto.auto
                        backgroundColor = theme.colors.form.background //Color("#FFFFFF")
                        "p" {
                            fontFamily = sansSerif
                            margin = Margin(10.px, 20.px)
                            flexGrow = number(0.0)
                        }

                    }
                    +props.children
                }
                if (layoutMode >= LayoutMode.TABLET) {

                    div {
                        css {
                            borderBottomLeftRadius = 10.px
                            borderBottomRightRadius = 10.px
                            paddingBottom = 10.px
                            flexBasis = 10.px
                            flexGrow = number(0.0)
                            flexShrink = number(0.0)
                            backgroundColor =  theme.colors.form.background //Color("#FFFFFF")
                        }
                        onClick = { props.onClose?.invoke() }
                    }
                    div {
                        css {
                            flexGrow = number(if (props.fullSize) 0.0 else 1.0)
                            flexBasis = 44.px
                            flexShrink = number(0.0)
                        }
                        onClick = { props.onClose?.invoke() }
                    }
                }
            }, document.body
        )}
    }
}

external interface DialogHeaderProps : Props {
    var title: String
    var disabledAction: Boolean
    var action: String
    var onClose: (() -> Unit)?
    var onAction: (() -> Unit)?
}

val DialogHeader = FC<DialogHeaderProps> { props ->
    div {
        css {
            paddingLeft = 4.px
            flexGrow = number(1.0)
            flexBasis = 0.px
        }
        IconButton {
            onClick = { props.onClose?.invoke() }
            svg {
                width = 16.0
                height = 16.0
                strokeWidth = 2.0
                stroke = "#000000"
                viewBox = "0 0 16 16"
                strokeLinecap = StrokeLinecap.round
                path {
                    d = "M1 1 L15 15 M15 1 L1 15"
                }
            }
        }
    }
    div {
        css {
            flexShrink = number(1.0)
            fontFamily = sansSerif
            fontWeight = integer(600)
            fontSize = 17.px
            lineHeight = 21.px
            whiteSpace = WhiteSpace.nowrap
        }
        +props.title
    }
    div {
        css {
            paddingRight = 4.px
            flexBasis = 0.px
            flexGrow = number(1.0)
            display = Display.flex
            justifyContent = JustifyContent.flexEnd
            flexDirection = FlexDirection.row
        }
        TextButton {
            css {
                fontWeight = integer(600)
                fontSize = 17.px
                lineHeight = 21.px
            }
            palette = TextPalette.action
            +props.action
            disabled = props.disabledAction
            onClick = { props.onAction?.invoke() }
        }
    }
}

enum class DialogMenuItemVariant {
    normal, dangerous,
}

val DialogMenuSeparator = FC<DialogMenuItemProps> { props ->
    ReactHTML.hr {
        css {
            margin = Margin(15.px, 0.px)
            border = None.none
            borderBottom = Border(0.5.px, LineStyle.solid, Color("#DDDDDD"))
            ".dialogmenu-start + &" {
                display = None.none
            }
            "& + &" {
                display = None.none
            }
        }
    }
}

external interface DialogMenuHeaderProps: PropsWithClassName {
    var text: String
}

val DialogMenuHeader = FC<DialogMenuHeaderProps> { props->
    div {
        css(override=props) {
            color = Color("#BBBBBB")
            fontSize = 14.px
            lineHeight = 17.px
            fontWeight = integer(500)
            fontFamily = sansSerif
            padding = Padding(0.px, 15.px, 8.px)
        }
        +props.text
    }
}

external interface DialogMenuItemProps : Props {
    var text: String

    /** Defaults to black. */
    var color: Color?
    /** Defaults to false. */
    var disabled: Boolean?

    /** Defaults to [DialogMenuItemVariant.normal]. */
    var variant: DialogMenuItemVariant?
    var onClick: (() -> Unit)?
    var icon: ComponentType<IconProps>?
}

external interface DialogMenuNavProps : DialogMenuItemProps {
    var navigate: String
}

val DialogMenuItem = FC<DialogMenuItemProps> { props ->
    val variant = props.variant ?: DialogMenuItemVariant.normal
    val color = when (variant) {
        DialogMenuItemVariant.normal -> Color("#000000")
        DialogMenuItemVariant.dangerous -> Color("#ff0000")
    }
    val disabled = props.disabled ?: false

    // We have an extra Stack rather than using the button directly
    // to avoid the padding from being clickable.
    Stack {
        css {
            padding = Padding(6.px, 15.px)

            if (!disabled) {
                hover {
                    backgroundColor = rgba(0, 0, 0, 0.05)
                }
            }

            rippleCss()
        }

        button {
            css {
                all = Globals.unset
                if (!disabled) {
                    cursor = Cursor.pointer
                }
                userSelect = None.none

                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
                gap = 10.px

                this.color = color
                if (disabled) {
                    this.opacity = number(0.5)
                }
            }

            div {
                css {
                    flex = None.none
                    display = Display.flex
                    justifyContent = JustifyContent.center
                    alignItems = AlignItems.center
                    height = 30.px
                    width = 30.px
                }
                props.icon?.let {
                    +it.create()
                }
            }

            div {
                css {
                    fontSize = 17.px
                    lineHeight = 20.px
                    fontFamily = sansSerif
                    flexGrow = number(1.0)
                }
                +props.text
            }
            onClick = {
                if (!disabled) {
                    createRipple(it, Color("#999999"))
                    props.onClick?.invoke()
                }
            }
        }
    }
}

val DialogMenuNav = FC<DialogMenuNavProps> { props ->
    val color = when (props.variant ?: DialogMenuItemVariant.normal) {
        DialogMenuItemVariant.normal -> Color("#000000")
        DialogMenuItemVariant.dangerous -> Color("#ff0000")
    }
    val disabled = props.disabled ?: false

    // We have an extra Stack rather than using the button directly
    // to avoid the padding from being clickable.
    Stack {
        css {
            padding = Padding(6.px, 15.px)

            if (!disabled) {
                hover {
                    backgroundColor = rgba(0, 0, 0, 0.05)
                }
            }

            rippleCss()
        }

        Link {
            css {
                all = Globals.unset
                if (!disabled) {
                    cursor = Cursor.pointer
                }
                userSelect = None.none

                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
                gap = 10.px

                this.color = color
                if (disabled) {
                    this.opacity = number(0.5)
                }
            }

            div {
                css {
                    flex = None.none
                    display = Display.flex
                    justifyContent = JustifyContent.center
                    alignItems = AlignItems.center
                    height = 30.px
                    width = 30.px
                }
                props.icon?.let {
                    +it.create()
                }
            }

            div {
                css {
                    fontSize = 17.px
                    lineHeight = 20.px
                    fontFamily = sansSerif
                    flexGrow = number(1.0)
                }
                +props.text
            }
            to = props.navigate
        }
    }
}

external interface DialogMenuCommonActionsProps : Props {
    /**
     * A user-facing name of the page, for the feedback dialog.
     *
     * Optional. When not provided, the feedback dialog will not have an option to attach the page name.
     */
    var pageName: String
    /** Triggered when any of the actions closes the dialog. */
    var onClose: (() -> Unit)?
}

val DialogMenuCommonActions = FC<DialogMenuCommonActionsProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    FeedbackMenuItem {
        pageName = props.pageName
        onClick = {
            props.onClose?.invoke()
        }
    }
    /*
    When implementing these, see also the Sidebar, this is duplicated there.

    DialogMenuItem {
        text = "How to use this page"
        disabled = true
    }
     */
    DialogMenuItem {
        text = "About Confido"
        icon = AboutIcon
        onClick = {
            // Warning: This is currently duplicated in DialogMenuCommonActions
            window.open("https://confido.institute/confido-app.html", "_blank")
        }
    }
    DialogMenuItem {
        text = "Switch to old UI"
        onClick = {
            localStorage.setItem("layoutVersion", "legacy")
            location.reload()
        }
    }
}

val DialogMainButton = Button.withStyle {
    display = Display.block
    margin = Margin(20.px, 20.px, 10.px)
    fontWeight = integer(500)
    alignSelf = AlignSelf.stretch
}