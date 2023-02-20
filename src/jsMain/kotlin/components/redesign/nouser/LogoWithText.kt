package components.redesign.nouser

import components.redesign.basic.*
import csstype.*
import dom.html.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.img
import utils.AUTO

external interface LogoWithTextProps: ImgHTMLAttributes<HTMLElement>, PropsWithPalette<PaletteWithText>

val confidoLogoWithTextClass = emotion.css.ClassName {
    display = Display.block
    width = 240.px
    marginTop = 90.px  // TODO: Reduce after the login screen is aligned vertically to the center.
    marginRight = AUTO
    marginBottom = 10.px
    marginLeft = AUTO
}

val LogoWithText = FC<LogoWithTextProps> { props ->
    val palette = props.palette ?: MainPalette.primary
    img {
        +props

        css(confidoLogoWithTextClass, override=props) {
        }
        src = "/static/logo-text-horizontal.svg"
    }
}
