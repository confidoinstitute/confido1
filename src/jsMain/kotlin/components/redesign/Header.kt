package components.redesign

import csstype.Color
import ext.helmet.Helmet
import react.dom.html.ReactHTML.title
import react.dom.html.ReactHTML.meta
import react.FC
import react.Props

external interface HeaderProps : Props {
    var title: String?
    var appBarColor: Color?
}

val Header = FC<HeaderProps> {props ->
    Helmet {
        props.title?.let { titleStr ->
            title {
                if (titleStr.isNotEmpty()) +"$titleStr - Confido" else +"Confido"
            }
        }
        props.appBarColor?.let {color ->
            val colorStr = color.toString()
            meta {
                name = "theme-color"
                content = colorStr
            }
            meta {
                name = "msapplication-navbutton-color"
                content = colorStr
            }
            meta {
                name = "apple-mobile-web-app-status-bar-style"
                content = colorStr
            }
        }
    }
}
