package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import org.w3c.dom.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.button

external interface ButtonProps: ButtonHTMLAttributes<HTMLButtonElement>, PropsWithPalette<PaletteWithText>

val Button = FC<ButtonProps> { props ->
    val palette = props.palette ?: MainPalette.primary
    button {
        +props

        onClick = {
            createRipple(it, Color("#FFFFFF"))
            props.onClick?.invoke(it)
        }

        css(props.className) {
            margin = Margin(10.px, 0.px)
            padding = 12.px
            borderWidth = 0.px
            borderRadius = 5.px
            fontWeight = FontWeight.bolder
            fontFamily = FontFamily.sansSerif
            fontSize = 18.px
            lineHeight = 21.px
            backgroundColor = palette.color
            color = palette.text.color
            cursor = Cursor.pointer

            rippleCss()
        }
    }
}
