package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import dom.html.*
import hooks.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.textarea

val textInputClass = emotion.css.ClassName {
    padding = Padding(10.px, 12.px)
    borderWidth = 0.px
    borderRadius = 5.px
    width = 100.pct

    fontSize = 17.px
    lineHeight = 20.px
    fontFamily = sansSerif
    resize = None.none

    focus {
        outline = Outline(2.px, LineStyle.solid, MainPalette.primary.color)
    }

    placeholder {
        color = Color("#BBBBBB")
    }

    disabled {
        opacity = number(0.3)
        filter = saturate(0.1)
    }
}

val TextInput = FC<InputHTMLAttributes<HTMLInputElement>> {
    val theme = useTheme()
    input {
        +it

        css(textInputClass, override=it) {
            backgroundColor = theme.colors.form.inputBackground
        }
    }
}
val MultilineTextInput = FC<TextareaHTMLAttributes<HTMLTextAreaElement>> {
    val theme = useTheme()
    textarea {
        +it

        css(textInputClass, override=it) {
            backgroundColor = theme.colors.form.inputBackground
        }
    }
}
