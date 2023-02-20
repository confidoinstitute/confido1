package components.redesign

import components.redesign.basic.Stack
import csstype.*
import emotion.react.css
import react.FC
import react.Props

// TODO: Implement sorting logic. For now we just always show "Newest first"

val SortButton = FC<Props> {
    Stack {
        direction = FlexDirection.row
        css {
            fontFamily = FontFamily.sansSerif
            fontWeight = integer(600)
            fontSize = 13.px
            lineHeight = 16.px
            color = Color("#888888")
            gap = 7.px
            alignItems = AlignItems.center
        }
        SortIcon { }
        +"Newest first"
    }
}