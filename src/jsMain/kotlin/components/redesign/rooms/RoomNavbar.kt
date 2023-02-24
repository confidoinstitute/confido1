package components.redesign.rooms

import components.redesign.BackIcon
import components.redesign.NavMenuIcon
import components.redesign.basic.PropsWithPalette
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
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.StrokeLinecap
import react.router.dom.Link
import react.useContext

external interface RoomNavbarProps : PropsWithChildren, PropsWithPalette<RoomPalette> {
    var navigateBack: String?
    var onMenu: (() -> Unit)?
}

fun PropertiesBuilder.navQuestionTitle() {
}

val RoomNavbar = FC<RoomNavbarProps> { props ->
    val palette = props.palette ?: RoomPalette.red

    ReactHTML.nav {
        css {
            position = Position.fixed
            backgroundColor = palette.color
            display = Display.flex
            flexDirection = FlexDirection.row
            height = 44.px
            top = 0.px
            width = 100.pct
            padding = Padding(0.px, 4.px)
            zIndex = integer(20)
        }
        ReactHTML.div {
            css {
                flexGrow = number(1.0)
                flexBasis = 0.px
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
        div {
            css {
                fontFamily = FontFamily.serif
                fontSize = 17.px
                lineHeight = 21.px
                fontWeight = FontWeight.bold
                overflow = Overflow.hidden
                whiteSpace = WhiteSpace.nowrap
                textOverflow = TextOverflow.ellipsis
                padding = 12.px
                flexShrink = number(1.0)
                color = palette.text.color
            }
            +props.children
        }
        ReactHTML.div {
            css {
                flexGrow = number(1.0)
                flexBasis = 0.px
                display = Display.flex
                justifyContent = JustifyContent.flexEnd
                alignItems = AlignItems.center
            }
            props.onMenu?.let {
                IconButton {
                    this.palette = palette.text
                    onClick = { it() }
                    NavMenuIcon {}
                }
            }
        }
    }
}

