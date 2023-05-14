package components.redesign

import components.*
import csstype.Globals
import emotion.styled.styled
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.a

external interface TextWithLinksProps : Props {
    var text: String
}

val A = a.styled {props, theme ->
    color = Globals.inherit
}

val TextWithLinks = FC<TextWithLinksProps> { props ->
    val parts = useMemo(props.text) { splitLinks(props.text) }
    parts.forEach {
        when (it.first) {
            SplitLinkPart.TEXT -> +it.second
            SplitLinkPart.LINK -> A {
                href = if (it.second.contains("://")) it.second else "http://${it.second}"
                rel = "noreferrer"
                target = AnchorTarget._blank
                +it.second
            }
        }
    }
}
