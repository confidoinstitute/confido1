package components.redesign.basic

import csstype.*
import emotion.react.*
import react.*

external interface GlobalCssProps : Props {
    var backgroundColor: BackgroundColor
}

val sansSerif = string("Inter, sans-serif")
val serif = string("TeX Gyre Pagella, Palatino, serif")

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