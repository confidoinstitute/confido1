package components.redesign.basic

import browser.document
import components.redesign.forms.IconButton
import components.redesign.forms.TextButton
import csstype.*
import emotion.css.keyframes
import emotion.react.css
import react.*
import react.dom.createPortal
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.StrokeLinecap
import web.location.location
import web.storage.localStorage

external interface DialogProps : PropsWithChildren {
    var open: Boolean
    var onClose: (() -> Unit)?
    var title: String
    var action: String
    var disabledAction: Boolean
    var onAction: (() -> Unit)?
}

val Dialog = FC<DialogProps> { props ->
    DialogCore {
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

external interface DialogMenuProps : PropsWithChildren {
    var open: Boolean
    var onClose: (() -> Unit)?

    /** Defaults to true. */
    var hasCloseButton: Boolean?
}

val DialogMenu = FC<DialogMenuProps> { props ->
    DialogCore {
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

            if (props.hasCloseButton ?: true) {
                DialogMenuButton {
                    text = "Close"
                    onClick = { props.onClose?.invoke() }
                }
            }
        }
    }
}

external interface DialogCoreProps : PropsWithChildren {
    var open: Boolean
    var onClose: (() -> Unit)?
    var header: ReactNode
    var fullSize: Boolean
}

internal val slideKF = keyframes {
    0.pct {
        transform = translatey(100.pct)
    }
    100.pct {
        transform = translatey(0.pct)
    }
}

val DialogCore = FC<DialogCoreProps> { props ->

    if (props.open) {
        Backdrop {
            onClick = { props.onClose?.invoke(); it.preventDefault() }
        }

        +createPortal(
            Stack.create {
                css {
                    maxHeight = 100.pct
                    width = 100.pct
                    zIndex = integer(2100)
                    position = Position.fixed
                    bottom = 0.px
                    animationName = slideKF
                    animationDuration = 0.25.s
                    animationTimingFunction = AnimationTimingFunction.easeOut
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
                        fontWeight = FontWeight.bold
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
        )
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
            fontFamily = FontFamily.sansSerif
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

external interface DialogMenuButtonProps : Props {
    var onClick: (() -> Unit)?
    var text: String
}

val DialogMenuButton = FC<DialogMenuButtonProps> { props ->
    Stack {
        css {
            padding = 15.px
        }
        button {
            css {
                all = Globals.unset
                cursor = Cursor.pointer

                border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
                borderRadius = 5.px
                padding = 10.px
                alignSelf = AlignSelf.stretch

                fontFamily = FontFamily.sansSerif
                fontStyle = FontStyle.normal
                fontSize = 17.px
                lineHeight = 20.px
                color = Color("#999999")
                textAlign = TextAlign.center
            }
            onClick = { props.onClick?.invoke() }
            +props.text
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
                    fontFamily = FontFamily.sansSerif
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

