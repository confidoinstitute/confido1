package components.redesign.basic

import browser.*
import components.redesign.forms.*
import components.redesign.transitions.*
import csstype.*
import dom.html.*
import emotion.react.*
import react.*
import react.dom.*
import react.dom.html.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.svg.*
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import web.location.*
import web.storage.*

external interface DialogProps : PropsWithChildren, PropsWithRef<HTMLElement> {
    var open: Boolean
    var onClose: (() -> Unit)?
    var title: String
    var action: String
    var disabledAction: Boolean
    var onAction: (() -> Unit)?
}

val Dialog = ForwardRef<HTMLElement, DialogProps> { props, fRef ->
    DialogCore {
        ref = fRef
        open = props.open
        onClose = props.onClose
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
            +props.children

            DialogMenuSeparator {}

            DialogMenuItem {
                text = "Switch to old UI"
                onClick = {
                    localStorage.setItem("layoutVersion","legacy")
                    location.reload()
                }
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
}

external interface DialogCoreProps : PropsWithChildren, PropsWithRef<HTMLElement> {
    var open: Boolean
    var onClose: (() -> Unit)?
    var header: ReactNode
    var fullSize: Boolean
}

val DialogCore = FC<DialogCoreProps> { props ->
    val setBackground = useContext(BackdropContext)
    val nodeRef = useRef<HTMLElement>()
    useEffect(props.open) {
        setBackground(props.open)
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
                }
                if (!props.fullSize)
                div {
                    css {
                        flexGrow = number(1.0)
                        flexBasis = 44.px
                        flexShrink = number(0.0)
                    }
                    onClick = { props.onClose?.invoke(); it.preventDefault() }
                }
                Stack {
                    direction = FlexDirection.row
                    css {
                        alignItems = AlignItems.center
                        flexShrink = number(0.0)
                        flexGrow = number(0.0)
                        backgroundColor = Color("#FFFFFF")
                        if (!props.fullSize) {
                            borderTopLeftRadius = 10.px
                            borderTopRightRadius = 10.px
                        }
                        minHeight = 12.px
                        maxHeight = 44.px
                        fontWeight = integer(600)
                    }
                    +props.header
                }
                div {
                    css {
                        flexShrink = number(1.0)
                        minHeight = 0.px
                        if (props.fullSize) {
                            flexGrow = number(1.0)
                        }
                        overflow = Auto.auto
                        backgroundColor = Color("#FFFFFF")
                    }
                    +props.children
                }
            }, document.body.asDynamic()
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
        }
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
    var icon: ComponentType<PropsWithClassName>?
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

            hover {
                backgroundColor = rgba(0, 0, 0, 0.05)
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

