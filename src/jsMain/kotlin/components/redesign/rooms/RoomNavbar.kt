package components.redesign.rooms

import components.redesign.BackIcon
import components.redesign.NavMenuIcon
import components.redesign.basic.RoomPalette
import components.redesign.basic.createRipple
import components.redesign.basic.rippleCss
import components.redesign.forms.Button
import components.redesign.forms.IconButton
import components.rooms.RoomContext
import csstype.*
import emotion.react.css
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML
import react.dom.svg.ReactSVG
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.StrokeLinecap
import react.router.dom.Link
import react.useContext

external interface RoomNavbarProps : PropsWithChildren {
    var navigateBack: String?
    var onMenu: (() -> Unit)?
}

val RoomNavbar = FC<RoomNavbarProps> { props ->
    val room = useContext(RoomContext)
    // TODO connect room palette
    val palette = RoomPalette.red

    ReactHTML.div {
        css {
            backgroundColor = palette.color
            flexBasis = 44.px
            flexShrink = number(0.0)
            display = Display.flex
            flexDirection = FlexDirection.row
            height = 44.px
            top = 0.px
            width = 100.pct
            padding = Padding(0.px, 4.px)
        }
        ReactHTML.div {
            css {
                flexGrow = number(1.0)
                display = Display.flex
                justifyContent = JustifyContent.flexStart
                alignItems = AlignItems.center
            }
            props.navigateBack?.let {
                Link {
                    to = it
                    css {
                        width = 30.px
                        height = 30.px
                        borderRadius = 100.pct
                        display = Display.flex
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                        "svg" {
                            asDynamic().fill = palette.text.color
                            asDynamic().stroke = palette.text.color
                        }
                        rippleCss()
                    }
                    onClick = { createRipple(it, palette.text.color) }
                    BackIcon { }
                }
            }
        }
        +props.children
        ReactHTML.div {
            css {
                flexGrow = number(1.0)
                display = Display.flex
                justifyContent = JustifyContent.flexEnd
                alignItems = AlignItems.center
            }
            IconButton {
                this.palette = palette.text
                onClick = { props.onMenu?.invoke() }
                NavMenuIcon {}
            }
        }
    }
}

