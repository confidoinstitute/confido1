package components.redesign.rooms

import components.redesign.basic.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import csstype.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span

external interface DividerHeadingProps : PropsWithChildren {
    /** The text of the heading. */
    var text: String
    /** If true, do not show in the [LayoutMode.PHONE] mode. */
    var hiddenOnPhone: Boolean
}

external interface DividerBarProps : PropsWithChildren {
    var text: String
}

/**
 * A heading which changes to a divider bar on mobile.
 *
 * The children are placed to the right of the heading.
 */
val DividerHeading = FC<DividerHeadingProps> { props ->
    val layoutMode = useContext(LayoutModeContext)

    if (layoutMode == LayoutMode.PHONE && props.hiddenOnPhone) {
        return@FC
    }

    if (layoutMode == LayoutMode.PHONE) {
        DividerBar {
            text = props.text
            +props.children
        }
    } else {
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.spaceBetween
            }
            span {
                css {
                    marginTop = 40.px
                    fontFamily = sansSerif
                    fontWeight = integer(600)
                    fontSize = 24.px
                    lineHeight = 29.px
                }
                +props.text
            }
            +props.children
        }
    }
}

private val DividerBar = FC<DividerBarProps> { props ->
    Stack {
        direction = FlexDirection.row
        css {
            padding = Padding(12.px, 13.px, 13.px, 15.px)
            backgroundColor = Color("#f2f2f2")
            justifyContent = JustifyContent.spaceBetween
            alignItems = AlignItems.center
        }
        div {
            css {
                textTransform = TextTransform.uppercase
                fontFamily = sansSerif
                fontSize = 13.px
                lineHeight = 16.px
                color = Color("#777777")
            }
            +props.text
        }
        +props.children
    }
}
