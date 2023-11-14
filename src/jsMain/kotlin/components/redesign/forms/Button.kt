package components.redesign.forms

import components.redesign.HelpIcon
import components.redesign.HelpIconNoPad
import components.redesign.basic.*
import csstype.*
import dom.html.*
import emotion.react.css
import react.FC
import react.dom.html.*
import react.dom.html.ReactHTML.button
import react.router.dom.*

typealias ButtonBaseProps = ButtonHTMLAttributes<HTMLButtonElement>
external interface ButtonProps: ButtonBaseProps, PropsWithPalette<PaletteWithText>
external interface TextButtonProps: ButtonBaseProps, PropsWithPalette<TextPalette>

val ButtonBase = button.withRipple().withStyle {
    all = Globals.unset
    boxSizing = BoxSizing.borderBox
    userSelect = None.none

    cursor = Cursor.pointer
    border = None.none
    borderRadius = 5.px

    disabled {
        opacity = number(0.3)
        filter = saturate(0.1)
    }
}

val Button = ButtonBase.withStyle<ButtonProps>("palette") {props ->
    val palette = props.palette ?: MainPalette.primary

    backgroundColor = palette.color
    color = palette.text.color

    textAlign = TextAlign.center
    fontWeight = integer(500)
    fontFamily = sansSerif
    fontSize = 18.px
    lineHeight = 21.px

    margin = Margin(10.px, 0.px)
    padding = 12.px

    ".ripple" {
        backgroundColor = palette.text.color
    }
}

val TextButton = ButtonBase.withStyle<TextButtonProps>("palette") {props ->
    val palette = props.palette ?: TextPalette.action

    textAlign = TextAlign.center
    fontWeight = integer(500)
    fontFamily = sansSerif
    fontSize = 18.px
    lineHeight = 21.px

    margin = Margin(10.px, 0.px)
    padding = 8.px
    color = palette.color
    background = None.none

    hover {
        backgroundColor = palette.hoverColor
    }

    ".ripple" {
        backgroundColor = palette.color
    }

    disabled {
        opacity = number(0.3)
        filter = saturate(0.1)
    }
}

fun PropertiesBuilder.iconButton(palette: TextPalette?) {
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


    palette?.color?.let { color = it }

    ".ripple" {
        backgroundColor = palette?.color ?: NamedColor.black
    }

    hover {
        backgroundColor = palette?.hoverColor ?: Color("#00000010")
    }
}

val IconButton = ButtonBase.withStyle<TextButtonProps>("palette") {props ->
    val palette = props.palette

    iconButton(palette)
}

val ButtonUnstyled = button.withStyle {
    border = None.none
    borderRadius = 0.px
    padding = 0.px
    margin = 0.px
    background = None.none
    fontFamily = "inherit".unsafeCast<FontFamily>()
    fontSize = "inherit".unsafeCast<FontSize>()
}

external interface IconLinkProps: LinkProps, PropsWithPalette<TextPalette>

val IconLink = Link.withRipple().withStyle<IconLinkProps>("palette") {props ->
    val palette = props.palette ?: TextPalette.black

    iconButton(palette)
}

val InlineHelpButton = FC<ButtonHTMLAttributes<HTMLButtonElement>> { props->
    ButtonUnstyled {
        +props
        title = "Show help"
        HelpIconNoPad {
            css {
                height = 1.em
                padding = 0.03.em
                paddingLeft = 0.1.em
                position = Position.relative
                top = 0.15.em
            }
            color = "#999"
        }
    }
}