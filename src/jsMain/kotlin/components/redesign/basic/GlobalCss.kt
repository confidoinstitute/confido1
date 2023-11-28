package components.redesign.basic

import components.redesign.layout.LayoutMode
import csstype.*
import emotion.css.ClassName
import emotion.react.*
import kotlinx.js.jso
import react.*
import utils.buildProps

external interface GlobalCssProps : Props {
    var backgroundColor: BackgroundColor
}

val sansSerif = string("Inter, sans-serif")
val serif = string("TeX Gyre Pagella, Palatino, serif")

val linkCSS = buildProps {
    textDecoration = TextDecoration.underline
    color = MainPalette.primary.color
    cursor = Cursor.pointer
}

val baseTableCSS = ClassName {

    borderCollapse = BorderCollapse.separate
    borderSpacing = 3.px
    "th" {
        textAlign = TextAlign.center
        paddingLeft = 5.px // align with content
        fontWeight = integer(600)
        color = Color("#666")
        fontSize = 90.pct
    }
    "td" {
        textAlign = TextAlign.center
        border = None.none
        backgroundColor = NamedColor.white
        //paddingLeft = 5.px
        //paddingRight = 5.px
        padding = 5.px
        verticalAlign = VerticalAlign.middle
    }
    ".tabplus &" {
        "tbody tr:first-child td:first-child" {
            borderTopLeftRadius = 10.px
        }
        "tbody tr:last-child td:first-child" {
            borderBottomLeftRadius = 10.px
        }
        "tbody tr:first-child td:last-child" {
            borderTopRightRadius = 10.px
        }
        "tbody tr:last-child td:last-child" {
            borderBottomRightRadius = 10.px
        }
    }
    "tbody" {
        fontSize = 90.pct
    }
}
val rowStyleTableCSS = ClassName(baseTableCSS) {

    borderSpacing = "0 3px".unsafeCast<BorderSpacing>()
    "th" {
        fontWeight = integer(600)
        color = Color("#666")
        fontSize = 90.pct
        paddingLeft = 5.px // align with content
        textAlign = TextAlign.left
    }
    "td" {
        border = None.none
        verticalAlign = VerticalAlign.middle
        textAlign = TextAlign.left
        padding = 0.px
        paddingLeft = 5.px
        paddingRight = 5.px
    }
}

val GlobalCss = FC<GlobalCssProps> { props ->
    Global {
        styles {
            "*" {
                boxSizing = BoxSizing.borderBox
            }
            "html,body" {
                margin = 0.px
                padding = 0.px
                minHeight = 100.vh
                backgroundColor = props.backgroundColor
                display = Display.flex
                flexDirection = FlexDirection.column
                fontFamily = sansSerif
            }
            "button" {
                cursor = Cursor.pointer
            }
            "a" {
                +linkCSS
            }
        }
    }

    // We need to use a separate Global per @font-face, as this is internally a javascript object
    // and the repeated name would conflict. The wrapper API does not support passing in an array
    // (see https://github.com/emotion-js/emotion/issues/1395), so we use multiple Globals instead.

    Global {
        styles {
            fontFace {
                fontFamily = "Inter"
                src = "url('/static/Inter.var.woff2') format('woff2')"
            }
        }
    }
    Global {
        styles {
            fontFace {
                fontFamily = "TeX Gyre Pagella"
                fontWeight = FontWeight.normal
                fontStyle = FontStyle.normal
                src = "url('/static/texgyrepagella-regular.woff2') format('woff2')"
            }
        }
    }
    Global {
        styles {
            fontFace {
                fontFamily = "TeX Gyre Pagella"
                fontWeight = FontWeight.normal
                fontStyle = FontStyle.italic
                src = "url('/static/texgyrepagella-italic.woff2') format('woff2')"
            }
        }
    }
    Global {
        styles {
            fontFace {
                fontFamily = "TeX Gyre Pagella"
                fontWeight = FontWeight.bold
                fontStyle = FontStyle.normal
                src = "url('/static/texgyrepagella-bold.woff2') format('woff2')"
            }
        }
    }
    Global {
        styles {
            fontFace {
                fontFamily = "TeX Gyre Pagella"
                fontWeight = FontWeight.bold
                fontStyle = FontStyle.italic
                src = "url('/static/texgyrepagella-bolditalic.woff2') format('woff2')"
            }
        }
    }
}
