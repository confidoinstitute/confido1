package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import dom.html.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.textarea

internal fun PropertiesBuilder.textInputCss() {
    padding = Padding(10.px, 12.px)
    backgroundColor = Color("#F8F8F8")
    borderWidth = 0.px
    borderRadius = 5.px
    width = 100.pct

    fontSize = 17.px
    lineHeight = 20.px
    fontFamily = FontFamily.sansSerif
    resize = "none".asDynamic()

    focus {
        outline = Outline(2.px, LineStyle.solid, MainPalette.primary.color)
    }

    placeholder {
        color = Color("#BBBBBB")
    }
}

val TextInput = FC<InputHTMLAttributes<HTMLInputElement>> {
    input {
        +it
        onChange

        css(it.className) {
            textInputCss()
        }
    }
}
val MultilineTextInput = FC<TextareaHTMLAttributes<HTMLTextAreaElement>> {
    textarea {
        +it

        css(it.className) {
            textInputCss()
        }
    }
}
