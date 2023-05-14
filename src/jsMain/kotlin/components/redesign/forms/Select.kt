package components.redesign.forms

import components.redesign.basic.sansSerif
import csstype.*
import emotion.styled.*
import react.dom.html.ReactHTML.select

val Select = select.styled {props, theme ->
    all = Globals.unset
    boxSizing = BoxSizing.borderBox
    border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
    borderRadius = 5.px

    fontFamily = sansSerif
    fontSize = 17.px
    lineHeight = 18.px
    color = Color("#555555")

    padding = Padding(10.px, 8.px)
    paddingLeft = 20.px

    backgroundColor = Color("#FFFFFF")
    backgroundImage = url("\"data:image/svg+xml;utf8,<svg height='5' width='10' xmlns='http://www.w3.org/2000/svg'><path fill='%23dddddd' d='M0,0H10L5,5Z'/></svg>\"")
    backgroundRepeat = BackgroundRepeat.noRepeat
    backgroundPositionX = 6.px
    backgroundPositionY = 18.px

    focus {
        outline = Outline(2.px, LineStyle.solid)
        outlineColor = Color("#6319FF")
        color = Color("#000000")
        margin = 0.px
    }
    "&>option:selected" {
        backgroundColor = Color("#E9E5F0")
    }

    disabled {
        opacity = number(0.3)
        backgroundImage = None.none
    }
}