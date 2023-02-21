package components.redesign

import components.redesign.basic.createRipple
import components.redesign.basic.rippleCss
import csstype.*
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.button

enum class SortType {
    NEWEST,
    OLDEST,
}

external interface SortButtonProps : Props {
    var sortType: SortType
    var onChange: ((SortType) -> Unit)?
}

val SortButton = FC<SortButtonProps> { props ->
    button {
        css {
            all = Globals.unset
            cursor = Cursor.pointer
            userSelect = None.none

            display = Display.flex
            flexDirection = FlexDirection.row
            fontFamily = FontFamily.sansSerif
            fontWeight = integer(600)
            fontSize = 13.px
            lineHeight = 16.px
            padding = Padding(0.px, 10.px)
            borderRadius = 5.px
            color = Color("#888888")
            gap = 7.px
            alignItems = AlignItems.center

            rippleCss()
        }

        SortIcon { }
        val text = when (props.sortType) {
            SortType.NEWEST -> "Newest first"
            SortType.OLDEST -> "Oldest first"
        }
        +text

        onClick = {
            createRipple(it, rgba(0,0,0,0.2))
            var newType = when (props.sortType) {
                SortType.NEWEST -> SortType.OLDEST
                SortType.OLDEST -> SortType.NEWEST
            }
            props.onChange?.invoke(newType)
        }
    }
}