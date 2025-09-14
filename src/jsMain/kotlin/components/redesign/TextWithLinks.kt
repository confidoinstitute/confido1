package components.redesign

import components.*
import csstype.Globals
import emotion.styled.styled
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.a


/**
 * Ugly regex to match URLs in text
 *
 * Requirements:
 *   - should match all urls with http[s]:// and reasonable urls without scheme (e.g. "confido.tools/contact")
 *   - should NOT match punctuation at the end of a sentence ending with a URL:
 *        "Have a look here: http://example.com."
 *     punctuations is here considered to mean: . , : ; ? ! )
 *   - should allow diacritics and weird letters in URLs (IDN, wikipedia article names)
 *   - should match crazy Wikipedia URLs with parentheses at the end, e.g.
 *         "https://en.wikipedia.org/wiki/Naturalism_(philosophy)"
 *     this conflicts with the previous rule because we do not want to match the closing paren in:
 *         "(for more, see https://en.wikipedia.org/wiki/Philosophy)"
 *     This is resolved by matching parentheses in url in pairs (no nesting)
 *   - should support all valid URL characters:
 *     https://stackoverflow.com/questions/7109143/what-characters-are-valid-in-a-url#
 *
 */
val LINK_REGEX = Regex("""\b(https?:\/\/|[\w.-]+\.[a-z]+)([\]\[\w/_~#@${'$'}&'*+%=-]|\([\]\[\w/_~#@${'$'}&'*+%=.,:;?!-]*\)|[.,:;?!)](?!${'$'}|[\s.,:;?!]))+""")

enum class SplitLinkPart {
    TEXT, LINK
}
fun splitLinks(text: String): List<Pair<SplitLinkPart, String>> {
    var pos = 0
    val ret = mutableListOf<Pair<SplitLinkPart, String>>()
    while(pos < text.length) {
        val nextLink = LINK_REGEX.find(text, pos)
        if (nextLink == null) {
            ret.add(SplitLinkPart.TEXT to text.substring(pos))
            break
        } else {
            if (nextLink.range.start > pos)
                ret.add(SplitLinkPart.TEXT to text.substring(pos, nextLink.range.start))
            ret.add(SplitLinkPart.LINK to nextLink.value)
            pos = nextLink.range.endInclusive + 1
        }
    }
    return ret.toList()
}
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
