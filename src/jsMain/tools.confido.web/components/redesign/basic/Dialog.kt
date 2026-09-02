package tools.confido.web.components.redesign.basic

import tools.confido.web.components.useDisconnected
import tools.confido.web.components.redesign.AboutIcon
import tools.confido.web.components.redesign.IconProps
import tools.confido.web.components.redesign.SidebarContext
import tools.confido.web.components.redesign.feedback.FeedbackMenuItem
import tools.confido.web.components.redesign.forms.*
import tools.confido.web.components.redesign.layout.LayoutMode
import tools.confido.web.components.redesign.layout.LayoutModeContext
import tools.confido.web.components.redesign.transitions.*
import web.cssom.*
import web.html.*
import emotion.react.*
import tools.confido.web.hooks.useEffectNotFirst
import js.objects.unsafeJso as jso
import react.*
import react.dom.*
import react.dom.html.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.svg.*
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.router.dom.Link
import react.router.useLocation
import tools.confido.utils.generateId
import web.location.*
import web.storage.*
import web.dom.document
import web.events.EventType
import web.events.addEventListener
import web.events.removeEventListener
import web.keyboard.KeyboardEvent
import web.mouse.MouseEvent
import web.window.WindowTarget
import web.window._blank
import web.window.window

external interface DialogProps : BaseDialogProps, PropsWithChildren, PropsWithRef<HTMLElement> {
    var title: String
    var action: String
    var disabledAction: Boolean
    var onAction: (() -> Unit)?
    var fullSize: Boolean
}

val Dialog = FC<DialogProps> { props->
    DialogCore {
        ref = props.ref
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

val DialogMenu = FC<DialogMenuProps> { props->
    val layoutMode = use(LayoutModeContext)!!
    useEffect { installMenuAnchorTracking() }

    if (layoutMode == LayoutMode.PHONE) {
        DialogCore {
            this.ref = props.ref
            this.open = props.open
            this.onClose = props.onClose
            Stack {
                div { className = ClassName("dialogmenu-start") }
                DialogMenuCloseContext.Provider {
                    value = { props.onClose?.invoke() }
                    +props.children
                }



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
    } else {
        MenuPopover { +props }
    }
}

/** Geometry of the element a menu was opened from, in viewport coordinates. */
private class MenuAnchor(val left: Double, val top: Double, val right: Double, val bottom: Double)

private var lastTriggerAnchor: MenuAnchor? = null
private var menuAnchorTrackingInstalled = false

private fun web.dom.Element.toMenuAnchor(): MenuAnchor {
    val r = getBoundingClientRect()
    return MenuAnchor(r.left, r.top, r.right, r.bottom)
}

/**
 * Remember the geometry of the last activated button/link so that a [DialogMenu]
 * opened by it can anchor its desktop popover to the trigger. The trigger's
 * pointerdown/keydown always precedes the state change that opens the menu, so the
 * anchor is fresh at mount time and call sites need not thread refs through.
 */
private fun installMenuAnchorTracking() {
    if (menuAnchorTrackingInstalled) return
    menuAnchorTrackingInstalled = true
    document.addEventListener(EventType<MouseEvent>("pointerdown"), { e ->
        lastTriggerAnchor = (e.target as? web.dom.Element)?.closest("button, a, [role=\"button\"]")?.toMenuAnchor()
            ?: MenuAnchor(e.clientX.toDouble(), e.clientY.toDouble(), e.clientX.toDouble(), e.clientY.toDouble())
    })
    document.addEventListener(EventType<KeyboardEvent>("keydown"), { e ->
        if (e.key == "Enter" || e.key == " ") {
            val el = document.activeElement ?: return@addEventListener
            if (el.matches("button, a, [role=\"button\"]")) lastTriggerAnchor = el.toMenuAnchor()
        }
    })
}

/** Desktop/tablet rendering of [DialogMenu]: a popover anchored to its trigger. */
private val MenuPopover = FC<DialogMenuProps> { props ->
    // Captured once per opening; the menu should not move if the page scrolls or
    // some other button is pressed while it is open.
    val anchor = useMemo(props.open) { if (props.open) lastTriggerAnchor else null }
    // Above every open dialog, so a menu opened from within one stacks on top.
    val popoverZIndex = useAboveModalsZIndex()

    // Unlike the phone sheet, whose host component typically unmounts on navigation,
    // a popover can outlive it (e.g. the sidebar account menu) — close it whenever
    // the route changes.
    val location = useLocation()
    useEffectNotFirst(location.pathname) {
        if (props.open) props.onClose?.invoke()
    }

    useEffectWithCleanup(props.open) {
        if (props.open) {
            val handler: (KeyboardEvent) -> Unit = { e ->
                if (e.key == "Escape") props.onClose?.invoke()
            }
            document.addEventListener(EventType<KeyboardEvent>("keydown"), handler)
            onCleanup { document.removeEventListener(EventType<KeyboardEvent>("keydown"), handler) }
        }
    }

    if (props.open) +createPortal(
        Fragment.create {
            div {
                // Click-away catcher with a slight tint: enough to separate the white
                // panel from white surroundings (e.g. the sidebar), well short of the
                // phone sheet's full dim.
                css {
                    position = Position.fixed
                    top = 0.px
                    left = 0.px
                    right = 0.px
                    bottom = 0.px
                    zIndex = integer(popoverZIndex)
                    backgroundColor = rgb(0, 0, 0, 0.1)
                }
                onClick = { props.onClose?.invoke() }
            }
            div {
                css {
                    position = Position.fixed
                    zIndex = integer(popoverZIndex + 1)
                    backgroundColor = Color("#FFFFFF")
                    borderRadius = 10.px
                    border = Border(1.px, LineStyle.solid, Color("#EEEEEE"))
                    boxShadow = BoxShadow(0.px, 4.px, 24.px, rgb(0, 0, 0, 0.2))
                    padding = Padding(6.px, 0.px)
                    minWidth = 240.px
                    overflowY = Auto.auto

                    if (anchor != null) {
                        val vw = window.innerWidth.toDouble()
                        val vh = window.innerHeight.toDouble()
                        // Never narrower than the trigger: menus opened from wide,
                        // select-like controls align with them (combo look); the
                        // usual small-button triggers keep the 240..340 defaults.
                        val anchorWidth = anchor.right - anchor.left
                        val widthCap = maxOf(340.0, anchorWidth)
                        minWidth = maxOf(240.0, anchorWidth).px
                        // Grow from the trigger towards the viewport center, and open
                        // downwards unless there is clearly more room above.
                        if ((anchor.left + anchor.right) / 2 < vw / 2) {
                            left = anchor.left.px
                            maxWidth = minOf(widthCap, vw - anchor.left - 8).px
                        } else {
                            right = (vw - anchor.right).px
                            maxWidth = minOf(widthCap, anchor.right - 8).px
                        }
                        val spaceBelow = vh - anchor.bottom
                        if (spaceBelow >= 280 || spaceBelow >= anchor.top) {
                            top = (anchor.bottom + 6).px
                            maxHeight = (vh - anchor.bottom - 18).px
                        } else {
                            bottom = (vh - anchor.top + 6).px
                            maxHeight = (anchor.top - 18).px
                        }
                    } else {
                        // No known trigger (e.g. opened programmatically).
                        top = 25.vh
                        left = 50.pct
                        transform = translate((-50).pct, 0.px)
                        maxWidth = 340.px
                        maxHeight = 50.vh
                    }
                }
                div { className = ClassName("dialogmenu-start") }
                DialogMenuCloseContext.Provider {
                    value = { props.onClose?.invoke() }
                    +props.children
                }
            }
        }, document.body
    )
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
    val layoutMode = use(LayoutModeContext)!!
    val sidebarState = use(SidebarContext)!!

    // This can be null in case we are not within a layout that has a sidebar.
    @Suppress("SENSELESS_COMPARISON")
    val sidebarOffset = if (sidebarState != null) {
        sidebarState.marginOffset
    } else {
        0.px
    }

    val theme = tools.confido.web.hooks.useTheme()
    val dialogZIndex = useBackdrop(props.open) {
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
                    zIndex = integer(dialogZIndex)
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
    // A dialog's action button is by definition a commit affordance, so it gates on
    // the connection automatically — call sites need no per-dialog stale plumbing.
    val disconnected = useDisconnected()
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
            disabled = props.disabledAction || disconnected
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
            "&:last-child" {
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
            color = Color("#666666")
            fontSize = 14.px
            lineHeight = 17.px
            fontWeight = integer(500)
            fontFamily = sansSerif
            padding = Padding(0.px, 15.px, 8.px)
        }
        +props.text
    }
}

/**
 * The enclosing [DialogMenu]'s close callback. Menu items close the menu
 * automatically when selected; see [DialogMenuItemProps.keepOpen] to opt out.
 */
val DialogMenuCloseContext = createContext<() -> Unit>()

external interface DialogMenuItemProps : Props {
    var text: String

    /** Optional second line under [text], smaller and gray. When set, [text]
     *  renders as a bold heading — used for rich chooser menus (e.g. the
     *  sidebar's create-new menu) rather than plain action lists. */
    var description: String?

    /** Defaults to black. */
    var color: Color?
    /** Defaults to false. */
    var disabled: Boolean?

    /** Defaults to [DialogMenuItemVariant.normal]. */
    var variant: DialogMenuItemVariant?
    var onClick: (() -> Unit)?
    var icon: ComponentType<IconProps>?

    /** Keep the menu open after this item is selected (menus close on selection by default). */
    var keepOpen: Boolean?
}

external interface DialogMenuNavProps : DialogMenuItemProps {
    var navigate: String
}

/** The label part of a menu item: single line normally, heading + gray
 *  description when [DialogMenuItemProps.description] is set. */
private val DialogMenuItemLabel = FC<DialogMenuItemProps> { props ->
    div {
        css {
            fontSize = 17.px
            lineHeight = 20.px
            fontFamily = sansSerif
            flexGrow = number(1.0)
            if (props.description != null) fontWeight = integer(600)
        }
        +props.text
        props.description?.let { desc ->
            div {
                css {
                    fontSize = 13.px
                    lineHeight = 16.px
                    fontWeight = integer(400)
                    color = Color("#666666")
                    marginTop = 2.px
                }
                +desc
            }
        }
    }
}

val DialogMenuItem = FC<DialogMenuItemProps> { props ->
    val variant = props.variant ?: DialogMenuItemVariant.normal
    val menuClose = use(DialogMenuCloseContext)
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
                    backgroundColor = rgb(0, 0, 0, 0.05)
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

            DialogMenuItemLabel {
                text = props.text
                description = props.description
            }
            onClick = {
                if (!disabled) {
                    createRipple(it, Color("#999999"))
                    props.onClick?.invoke()
                    if (props.keepOpen != true) menuClose?.invoke()
                }
            }
        }
    }
}

val DialogMenuNav = FC<DialogMenuNavProps> { props ->
    val menuClose = use(DialogMenuCloseContext)
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
                    backgroundColor = rgb(0, 0, 0, 0.05)
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

            DialogMenuItemLabel {
                text = props.text
                description = props.description
            }
            to = props.navigate
            onClick = {
                if (!disabled) {
                    props.onClick?.invoke()
                    if (props.keepOpen != true) menuClose?.invoke()
                }
            }
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
    val sidebar = use(SidebarContext)
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
    // The sidebar already carries the About link; don't duplicate it in every menu
    // when the sidebar is present.
    if (sidebar?.isAvailable != true)
    DialogMenuItem {
        text = "About Confido"
        icon = AboutIcon
        onClick = {
            // Warning: This is currently duplicated in the Sidebar
            window.open("https://confido.institute/confido-app.html", WindowTarget._blank)
        }
    }
}

val DialogMainButton = Button.withStyle {
    display = Display.block
    margin = Margin(20.px, 20.px, 10.px)
    fontWeight = integer(500)
    alignSelf = AlignSelf.stretch
}