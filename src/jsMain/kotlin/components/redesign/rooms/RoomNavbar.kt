package components.redesign.rooms

import components.redesign.*
import components.redesign.basic.*
import components.redesign.forms.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div

external interface RoomNavbarProps : PropsWithChildren, PropsWithPalette<RoomPalette> {
    var navigateBack: String?
    var onMenu: (() -> Unit)?
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
                IconLink {
                    this.palette = palette.text
                    to = it
                    BackIcon { }
                }
            }
        }
        div {
            css {
                fontFamily = FontFamily.serif
                fontSize = 17.px
                lineHeight = 21.px
                fontWeight = integer(700)
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
