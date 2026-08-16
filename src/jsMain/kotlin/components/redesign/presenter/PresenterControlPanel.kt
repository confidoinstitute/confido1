package components.redesign.presenter

import components.usePresenterWindowActive
import components.usePresenterInfo
import components.useDisconnected
import components.presenter.getPresenterControlsComponent
import components.redesign.CloseIcon
import components.redesign.PresenterIcon
import components.redesign.basic.*
import components.redesign.forms.Button
import components.redesign.forms.ButtonBase
import components.redesign.forms.ButtonBaseProps
import web.cssom.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.utils.capFirst

private val PanelButton = Button.withStyle {
    margin = 0.px
    padding = 8.px
    fontSize = 14.px
    flexGrow = number(1.0)
}

private val PillButton = Button.withStyle {
    width = 44.px
    height = 44.px
    margin = 0.px
    padding = 0.px
    borderRadius = 50.pct
    display = Display.flex
    alignItems = AlignItems.center
    justifyContent = JustifyContent.center
    boxShadow = BoxShadow(0.px, 0.px, 10.px, Color("#00000040"))
}

private val CollapseButton = ButtonBase.withStyle<ButtonBaseProps> {
    display = Display.flex
    alignItems = AlignItems.center
    justifyContent = JustifyContent.center
    width = 24.px
    height = 24.px
    borderRadius = 50.pct
    color = Color("#888888")
    flexShrink = number(0.0)
    hover {
        backgroundColor = Color("#EEEEEE")
    }
}

/**
 * A small overlay in the corner of the normal app UI that appears while a
 * presentation window is open. It shows what is being presented and, for view
 * types that register a controls component (see `view2controls`), lets the
 * presenter tweak the view's parameters without touching the presented page
 * itself.
 */
val PresenterControlPanel = FC<Props> {
    val ctl = use(PresenterContext)!!
    var expanded by useState(false)

    val info = usePresenterInfo()
    val view = info?.view
    // Show the panel while a presenter window is open, or while a recently set
    // presenter view lingers (offering to reopen the window or stop presenting).
    val visible = info != null && view != null && (usePresenterWindowActive() || info.isValid)
    val controlsComp = view?.let { getPresenterControlsComponent(it) }

    // Auto-expand when a view with controls becomes active, so the controls are discoverable.
    // Must run unconditionally (before any early return) to keep the hook order stable.
    useEffect(visible, view?.let { it::class.simpleName }, controlsComp != null) {
        if (visible && controlsComp != null) expanded = true
    }

    if (!visible || view == null) return@FC

    div {
        css {
            position = Position.fixed
            bottom = 16.px
            right = 16.px
            zIndex = integer(1500)
        }
        if (!expanded) {
            PillButton {
                palette = MainPalette.primary
                title = "Presentation controls"
                onClick = { expanded = true }
                PresenterIcon {}
            }
        } else {
            Stack {
                css {
                    backgroundColor = Color("#FFFFFF")
                    borderRadius = 10.px
                    boxShadow = BoxShadow(0.px, 0.px, 10.px, Color("#00000040"))
                    padding = 12.px
                    gap = 10.px
                    width = 280.px
                    maxWidth = 90.vw
                    fontFamily = sansSerif
                }
                // Header: what is being presented + collapse button
                Stack {
                    direction = FlexDirection.row
                    css {
                        alignItems = AlignItems.center
                        gap = 8.px
                    }
                    div {
                        css {
                            display = Display.flex
                            alignItems = AlignItems.center
                            color = MainPalette.primary.color
                            flexShrink = number(0.0)
                        }
                        PresenterIcon {}
                    }
                    div {
                        css {
                            flexGrow = number(1.0)
                            fontSize = 14.px
                            lineHeight = 17.px
                            fontWeight = integer(600)
                        }
                        +"Presenting: "
                        PresenterViewDescription { this.view = view }
                    }
                    CollapseButton {
                        title = "Hide controls"
                        onClick = { expanded = false }
                        CloseIcon {}
                    }
                }
                // View-specific controls, if this view type has any
                controlsComp?.let { comp ->
                    comp {
                        this.view = view
                        this.disabled = useDisconnected()
                        this.onChange = { newView -> ctl.set(newView) }
                    }
                }
                Stack {
                    direction = FlexDirection.row
                    css { gap = 8.px }
                    if (!usePresenterWindowActive()) {
                        PanelButton {
                            palette = MainPalette.primary
                            disabled = useDisconnected()
                            onClick = { openPresenterWindow() }
                            +"Reopen window"
                        }
                    }
                    PanelButton {
                        palette = MainPalette.secondary
                        disabled = useDisconnected()
                        onClick = { ctl.stop() }
                        +"Stop presenting"
                    }
                }
            }
        }
    }
}
