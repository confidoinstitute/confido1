package components.redesign.basic

import components.redesign.BackIcon
import components.redesign.SidebarContext
import components.redesign.SidebarIcon
import components.redesign.forms.IconButton
import components.redesign.forms.IconLink
import components.redesign.forms.TextButton
import csstype.*
import react.FC
import react.Props
import emotion.react.css
import react.ReactNode
import react.dom.html.ReactHTML.div
import react.useContext

external interface PageHeaderProps : Props {
    var title: String
    var disabledAction: Boolean
    var navigateBack: String?
    var action: String
    var allowSidebar: Boolean
    var rightExtra: ReactNode?
    var onAction: (() -> Unit)?
}

val PageHeader = FC<PageHeaderProps> { props ->
    val sidebar = useContext(SidebarContext)
    Stack {
        direction = FlexDirection.row
        css {
            alignItems = AlignItems.center
            flexShrink = number(0.0)
            flexGrow = number(0.0)
            minHeight = 12.px
            maxHeight = 44.px
            fontWeight = integer(600)
        }
        div {
            css {
                paddingLeft = 4.px
                flexGrow = number(1.0)
                flexBasis = 0.px
            }
            if (sidebar.isAvailable && props.allowSidebar) {
                IconButton {
                    onClick = { sidebar.toggle() }
                    SidebarIcon { }
                }
            } else {
                props.navigateBack?.let {
                    IconLink {
                        this.palette = TextPalette.black
                        to = it
                        BackIcon { }
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
            +props.rightExtra
        }
    }
}
