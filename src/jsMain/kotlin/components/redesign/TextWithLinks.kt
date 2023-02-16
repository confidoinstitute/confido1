package components.redesign

import components.SplitLinkPart
import components.splitLinks
import react.FC
import react.Props
import react.dom.html.AnchorTarget
import react.dom.html.ReactHTML.a
import react.useMemo

external interface TextWithLinksProps : Props {
    var text: String
}

val TextWithLinks = FC<TextWithLinksProps> { props ->
    val parts = useMemo(props.text) { splitLinks(props.text) }
    parts.forEach {
        when (it.first) {
            SplitLinkPart.TEXT -> +it.second
            SplitLinkPart.LINK -> a {
                href = if (it.second.contains("://")) it.second else "http://${it.second}"
                rel = "noreferrer"
                target = AnchorTarget._blank
                +it.second
            }
        }
    }
}
