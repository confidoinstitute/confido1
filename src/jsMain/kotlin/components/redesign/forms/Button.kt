package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import dom.html.*
import emotion.styled.styled
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.button
import react.dom.svg.ReactSVG
import react.dom.svg.StrokeLinecap

external interface ButtonProps: ButtonHTMLAttributes<HTMLButtonElement>, PropsWithPalette<PaletteWithText>
external interface TextButtonProps: ButtonHTMLAttributes<HTMLButtonElement>, PropsWithPalette<TextPalette>


val confidoButtonClass = emotion.css.ClassName {
    margin = Margin(10.px, 0.px)
    padding = 12.px
    borderWidth = 0.px
    borderRadius = 5.px
    fontWeight = FontWeight.bolder
    fontFamily = FontFamily.sansSerif
    fontSize = 18.px
    lineHeight = 21.px
    cursor = Cursor.pointer

    rippleCss()
}

val Button = FC<ButtonProps> { props ->
    val palette = props.palette ?: MainPalette.primary
    button {
        +props

        onClick = {
            createRipple(it, palette.text.color)
            props.onClick?.invoke(it)
        }

        css(confidoButtonClass, override=props) {
            backgroundColor = palette.color
            color = palette.text.color

            disabled {
                opacity = number(0.3)
                filter = saturate(0.1)
            }
        }
    }
}

val TextButton = FC<TextButtonProps> {props ->
    val palette = props.palette ?: TextPalette.action
    button {
        +props

        onClick = {
            createRipple(it, palette.color)
            props.onClick?.invoke(it)
        }

        css(confidoButtonClass, override=props) {
            padding = 8.px
            color = palette.color
            background = None.none

            hover {
                backgroundColor = palette.hoverColor
            }

            disabled {
                opacity = number(0.3)
                filter = saturate(0.1)
            }
        }
    }
}

val iconButtonBase = emotion.css.ClassName {
    display = Display.flex
    alignItems = AlignItems.center
    justifyContent = JustifyContent.center
    width = 30.px
    height = 30.px
    padding = 0.px
    borderRadius = 100.pct

    background = None.none
    border = None.none
    cursor = Cursor.pointer

    disabled {
        opacity = number(0.3)
        filter = saturate(0.1)
    }

}

val IconButton = FC<TextButtonProps> {props ->
    val palette = props.palette ?: TextPalette.black

    button {
        onClick = { createRipple(it, palette.color); props.onClick?.invoke(it) }
        css(iconButtonBase, override = props) {
            color = palette.color

            hover {
                backgroundColor = palette.hoverColor
            }
            "svg" {
                asDynamic().fill = palette.color
                asDynamic().stroke = palette.color
            }

            rippleCss()
        }
        +props.children
    }
}
