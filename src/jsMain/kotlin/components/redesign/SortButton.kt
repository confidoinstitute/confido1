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
    SET_BY_MODERATOR,
}

external interface SortButtonProps : Props {
    var sortType: SortType
    var onChange: ((SortType) -> Unit)?
    /**
     * A list of available sort type options.
     * Defaults to [SortType.NEWEST], [SortType.OLDEST].
    */
    var options: List<SortType>?
}

val SortButton = FC<SortButtonProps> { props ->
    val options = props.options ?: listOf(SortType.NEWEST, SortType.OLDEST)

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

            hover {
                backgroundColor = Color("#88888810")
            }

            rippleCss()
        }

        SortIcon { }
        val text = when (props.sortType) {
            SortType.NEWEST -> "Newest first"
            SortType.OLDEST -> "Oldest first"
            SortType.SET_BY_MODERATOR -> "Set by moderator"
        }
        +text

        onClick = {
            createRipple(it, rgba(0, 0, 0, 0.2))

            // This will work if the sort type is not within options as well,
            // falling back to the first option thanks to indexOf returning -1.
            val nextIndex = (options.indexOf(props.sortType) + 1) % options.size
            val newType = options[nextIndex]
            props.onChange?.invoke(newType)
        }
    }
}