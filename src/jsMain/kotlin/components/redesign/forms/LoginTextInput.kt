package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import dom.html.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.textarea

internal fun PropertiesBuilder.loginTextInputCss() {
    padding = Padding(10.px, 12.px)
    backgroundColor = Color("#96FFFF33")
    color = Color("#FFFFFF")
    borderWidth = 0.px
    borderRadius = 10.px
    width = 100.pct

    fontSize = 17.px
    lineHeight = 20.px
    fontFamily = FontFamily.sansSerif
    resize = "none".asDynamic()

    focus {
        outline = Outline(2.px, LineStyle.solid, MainPalette.primary.color)
    }

    placeholder {
        color = Color("#FFFFFF80")
    }
}

val LoginTextInput = FC<InputHTMLAttributes<HTMLInputElement>> {
    input {
        +it
        onChange

        css(it.className) {
            loginTextInputCss()
        }
    }
}

