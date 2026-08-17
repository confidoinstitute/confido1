package tools.confido.web.components.redesign.search

import tools.confido.web.components.useRooms
import tools.confido.web.components.useWorkspaceQuestions
import tools.confido.web.components.redesign.ZoomIcon
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.forms.TextInput
import tools.confido.web.components.redesign.layout.LayoutMode
import tools.confido.web.components.redesign.layout.LayoutModeContext
import tools.confido.web.components.redesign.questions.ChipCSS
import tools.confido.web.components.redesign.questions.QuestionItem
import tools.confido.web.components.redesign.title
import emotion.react.css
import tools.confido.web.hooks.useDebounce
import js.array.component1
import js.array.component2
import js.objects.unsafeJso as jso
import react.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.router.dom.Link
import react.router.dom.useSearchParams
import react.router.useNavigate
import web.cssom.*

external fun encodeURIComponent(str: String): String

/** Builds the canonical URL for a search, so the query is linkable and survives a reload. */
fun searchUrl(query: String, scope: Set<SearchScope> = SearchScope.default): String {
    val params = buildList {
        if (query.isNotEmpty()) add("q=${encodeURIComponent(query)}")
        if (scope != SearchScope.default) add("in=${scope.toParam()}")
    }
    return "/search" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
}

external interface HighlightedTextProps : Props {
    var text: String
    var terms: List<String>
}

/** Renders [text] with every occurrence of any of [terms] highlighted. */
val HighlightedText = FC<HighlightedTextProps> { props ->
    val ranges = matchRanges(props.text, props.terms)
    var pos = 0
    ranges.forEach { range ->
        if (range.start > pos) +props.text.substring(pos, range.start)
        span {
            key = "hl${range.start}"
            css {
                backgroundColor = Color("#FFE9A8")
                borderRadius = 3.px
                fontWeight = integer(600)
            }
            +props.text.substring(range.start, range.end)
        }
        pos = range.end
    }
    if (pos < props.text.length) +props.text.substring(pos)
}

private external interface SnippetViewProps : Props {
    var snippet: Snippet
    var terms: List<String>
}

private val SnippetView = FC<SnippetViewProps> { props ->
    if (props.snippet.truncatedStart) +"…"
    HighlightedText {
        text = props.snippet.text
        terms = props.terms
    }
    if (props.snippet.truncatedEnd) +"…"
}

private external interface ScopeChipProps : Props {
    var scope: SearchScope
    var selected: Boolean
    var onToggle: (() -> Unit)?
}

private val ScopeChip = FC<ScopeChipProps> { props ->
    button {
        css(ChipCSS) {
            cursor = Cursor.pointer
            padding = Padding(5.px, 10.px)
            if (props.selected) {
                backgroundColor = Color("#6319FF")
                color = NamedColor.white
                border = Border(1.px, LineStyle.solid, Color("#6319FF"))
            } else {
                backgroundColor = NamedColor.white
                color = Color("#666666")
                border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
            }
        }
        +props.scope.label
        onClick = { props.onToggle?.invoke() }
    }
}

/**
 * Workspace-wide search. Everything shown here comes out of the subscribed workspace slices, so
 * results update as you type without hitting the server; see [searchWorkspace].
 */
val SearchPage = FC<Props> {
    val rooms = useRooms()
    val questions = useWorkspaceQuestions()
    val layoutMode = use(LayoutModeContext)!!
    val navigate = useNavigate()
    val (searchParams, _) = useSearchParams()

    val urlQuery: String = searchParams.get("q")?.toString() ?: ""
    val urlScope = SearchScope.parse(searchParams.get("in")?.toString())

    var query by useState(urlQuery)
    var scope by useState(urlScope)

    // Adopt the URL when it changes from the outside (arriving from the dashboard search box,
    // back/forward navigation, or a shared link).
    useEffect(urlQuery) { if (urlQuery != query) query = urlQuery }
    useEffect(urlScope.toParam()) { if (urlScope != scope) scope = urlScope }

    // Mirror the current search back into the URL once typing settles, replacing the history
    // entry so that Back leaves the search page instead of stepping through every keystroke.
    useDebounce(300, query, scope.toParam()) {
        if (query != urlQuery || scope != urlScope)
            navigate(searchUrl(query, scope), jso<dynamic> { replace = true })
    }

    val results = useMemo(rooms, questions, query, scope.toParam()) {
        searchWorkspace(rooms, questions, query, scope)
    }

    PageHeader {
        title = "Search"
        allowSidebar = true
        navigateBack = "/"
    }

    Stack {
        css {
            alignItems = AlignItems.stretch
            flexShrink = number(0.0)
            width = 100.pct
            maxWidth = layoutMode.contentWidth
            marginLeft = Auto.auto
            marginRight = Auto.auto
            padding = Padding(10.px, layoutMode.contentSidePad, 0.px)
            gap = 10.px
        }

        // Search box
        Stack {
            direction = FlexDirection.row
            css {
                alignItems = AlignItems.center
                backgroundColor = NamedColor.white
                borderRadius = 10.px
                padding = Padding(0.px, 12.px)
                gap = 8.px
                border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
                "&:focus-within" {
                    borderColor = Color("#6319FF")
                }
            }
            div {
                css {
                    display = Display.flex
                    color = Color("#999999")
                    flexShrink = number(0.0)
                }
                ZoomIcon { size = 20 }
            }
            TextInput {
                css {
                    backgroundColor = NamedColor.transparent
                    padding = Padding(12.px, 0.px)
                    focus { outline = None.none }
                }
                autoFocus = true
                placeholder = "Search questions across all rooms…"
                value = query
                onChange = { ev -> query = ev.target.value }
            }
            if (query.isNotEmpty()) {
                button {
                    css {
                        border = None.none
                        background = None.none
                        cursor = Cursor.pointer
                        color = Color("#999999")
                        fontSize = 18.px
                        lineHeight = 18.px
                        padding = 2.px
                        flexShrink = number(0.0)
                    }
                    title = "Clear search"
                    +"✕"
                    onClick = { query = "" }
                }
            }
        }

        // Scope toggles
        Stack {
            direction = FlexDirection.row
            css {
                flexWrap = FlexWrap.wrap
                gap = 6.px
                alignItems = AlignItems.center
            }
            span {
                css {
                    fontFamily = sansSerif
                    fontSize = 12.px
                    fontWeight = integer(600)
                    color = Color("#999999")
                    marginRight = 2.px
                }
                +"Search in:"
            }
            SearchScope.entries.forEach { entry ->
                ScopeChip {
                    key = entry.id
                    this.scope = entry
                    selected = entry in scope
                    onToggle = {
                        // Never let the user switch everything off - that could only ever
                        // produce an empty result list.
                        val next = if (entry in scope) scope - entry else scope + entry
                        if (next.isNotEmpty()) scope = next
                    }
                }
            }
        }
    }

    Stack {
        css {
            alignItems = AlignItems.stretch
            flexGrow = number(1.0)
            width = 100.pct
            maxWidth = layoutMode.contentWidth
            marginLeft = Auto.auto
            marginRight = Auto.auto
            padding = if (layoutMode >= LayoutMode.TABLET) Padding(15.px, 0.px) else 15.px
            gap = 15.px
        }

        if (results.terms.isEmpty()) {
            SearchMessage {
                +"Search questions and rooms across your whole workspace."
            }
        } else if (results.isEmpty) {
            SearchMessage {
                +"Nothing found for “$query”."
                if (scope != SearchScope.default) {
                    div {
                        css { paddingTop = 6.px; fontSize = 14.px }
                        +"Try turning on more of the “Search in” options above."
                    }
                }
            }
        } else {
            if (results.questions.isNotEmpty()) {
                title("Questions (${results.questions.size})", 0.px)
                results.questions.forEach { hit ->
                    QuestionItem {
                        key = hit.question.id
                        question = hit.question
                        room = hit.room
                        href = hit.room.urlPrefix + hit.question.urlPrefix
                        hit.descriptionSnippet?.let { snip ->
                            subtitle = SnippetView.create {
                                snippet = snip
                                terms = results.terms
                            }
                        }
                    }
                }
            }

            if (results.rooms.isNotEmpty()) {
                title("Rooms (${results.rooms.size})", 0.px)
                results.rooms.forEach { hit ->
                    RoomResult {
                        key = hit.room.id
                        this.hit = hit
                        terms = results.terms
                    }
                }
            }
        }
    }
}

private val SearchMessage = FC<PropsWithChildren> { props ->
    div {
        css {
            fontFamily = sansSerif
            fontSize = 15.px
            color = Color("#888888")
            textAlign = TextAlign.center
            padding = Padding(30.px, 15.px)
        }
        +props.children
    }
}

private external interface RoomResultProps : Props {
    var hit: RoomHit
    var terms: List<String>
}

private val RoomResult = FC<RoomResultProps> { props ->
    val room = props.hit.room
    Link {
        to = room.urlPrefix
        css(LinkUnstyled) {
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.center
            gap = 12.px
            padding = Padding(12.px, 16.px)
            backgroundColor = NamedColor.white
            borderRadius = 10.px
            hover {
                boxShadow = BoxShadow(0.px, 0.px, 5.px, Color("#CCCCCC"))
            }
        }
        div {
            css {
                backgroundColor = room.color.palette.color
                borderRadius = 8.px
                width = 40.px
                height = 40.px
                flexShrink = number(0.0)
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                padding = 6.px
            }
            room.icon?.let {
                div {
                    css {
                        mask = url("/static/icons/$it")
                        maskSize = MaskSize.contain
                        backgroundColor = room.color.palette.text.color
                        width = 100.pct
                        height = 100.pct
                    }
                }
            }
        }
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                gap = 3.px
                minWidth = 0.px
            }
            div {
                css {
                    fontFamily = sansSerif
                    fontWeight = integer(700)
                    fontSize = 17.px
                    color = Color("#000000")
                }
                HighlightedText {
                    text = room.name
                    terms = props.terms
                }
            }
            props.hit.descriptionSnippet?.let { snip ->
                div {
                    css {
                        fontFamily = sansSerif
                        fontSize = 14.px
                        lineHeight = 18.px
                        color = Color("#666666")
                    }
                    SnippetView {
                        snippet = snip
                        terms = props.terms
                    }
                }
            }
        }
    }
}
