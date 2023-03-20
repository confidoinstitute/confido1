package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import dom.html.*
import emotion.styled.styled
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.button
import react.router.dom.Link
import react.router.dom.LinkProps

typealias ButtonBaseProps = ButtonHTMLAttributes<HTMLButtonElement>
external interface ButtonProps: ButtonBaseProps, PropsWithPalette<PaletteWithText>
external interface TextButtonProps: ButtonBaseProps, PropsWithPalette<TextPalette>

val ButtonBase = button.withRipple().styled {props, theme ->
    cursor = Cursor.pointer
    border = None.none
    borderRadius = 5.px

    disabled {
        opacity = number(0.3)
        filter = saturate(0.1)
    }
}

val Button = ButtonBase.styled<ButtonProps> {props, _ ->
    val palette = props.palette ?: MainPalette.primary

    backgroundColor = palette.color
    color = palette.text.color

    fontWeight = FontWeight.bolder
    fontFamily = FontFamily.sansSerif
    fontSize = 18.px
    lineHeight = 21.px

    margin = Margin(10.px, 0.px)
    padding = 12.px

    ".ripple" {
        backgroundColor = palette.text.color
    }
}

val TextButton = ButtonBase.styled<TextButtonProps> {props, _ ->
    val palette = props.palette ?: TextPalette.action

    fontWeight = FontWeight.bolder
    fontFamily = FontFamily.sansSerif
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

fun PropertiesBuilder.iconButton(palette: TextPalette) {
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


    color = palette.color

    ".ripple" {
        backgroundColor = palette.color
    }

    hover {
        backgroundColor = palette.hoverColor
    }
    "svg" {
        asDynamic().fill = palette.color
        asDynamic().stroke = palette.color
    }
}

val IconButton = ButtonBase.styled<TextButtonProps> {props, _ ->
    val palette = props.palette ?: TextPalette.black

    iconButton(palette)
}

external interface IconLinkProps: LinkProps, PropsWithPalette<TextPalette>

val IconLink = Link.withRipple().styled<IconLinkProps> {props, _ ->
    val palette = props.palette ?: TextPalette.black

    iconButton(palette)
}
