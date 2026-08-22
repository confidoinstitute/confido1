package tools.confido.web.components.redesign.rooms

import tools.confido.web.components.*
import tools.confido.web.components.redesign.*
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.layout.LayoutMode
import tools.confido.web.components.redesign.layout.LayoutModeContext
import tools.confido.web.components.redesign.questions.*
import tools.confido.web.components.redesign.questions.dialog.*
import web.cssom.*
import js.objects.unsafeJso as jso
import emotion.react.*
import tools.confido.web.hooks.*
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.rooms.*
import tools.confido.question.*
import tools.confido.refs.ref
import tools.confido.state.Topics
import tools.confido.utils.pluralize
import tools.confido.payloads.responses.WSData

external interface QuestionListProps : Props {
    var questions: List<Question>
    var showHiddenQuestions: Boolean
}

val QuestionList = FC<QuestionListProps> { props ->
    val room = use(RoomContext)!!
    val roomCounts = (useSubscription(Topics.roomQuestionCounts(room.ref)) as? WSData)?.data ?: emptyMap()
    val groupPredsWS = useSubscription(Topics.roomGroupPred(room.ref))
    val groupPreds = groupPredsWS.data ?: emptyMap()
    val layoutMode = use(LayoutModeContext)!!

    val questions = props.questions.reversed()
    val allVisibleQuestions = if (props.showHiddenQuestions) questions else questions.filter { it.visible }

    // Tag filtering
    var selectedTags by useState(emptySet<String>())
    val allTags: Set<String> = useMemo(allVisibleQuestions) {
        allVisibleQuestions.flatMap { it.tags }.distinct().sorted().toSet()
    }
    val visibleQuestions = if (selectedTags.isEmpty()) allVisibleQuestions
        else allVisibleQuestions.filter { q -> q.tags.any { it in selectedTags } }

    var preset by useState(QuestionPreset.NONE)
    var presetOpen by useState(false)

    val editQuestionDialog = useEditDialog(EditQuestionDialog, jso {
        this.preset = preset
    })

    fun addQuestion() {
        presetOpen = true
    }

    AddQuestionPresetDialog {
        open = presetOpen
        onClose = {presetOpen = false}
        onPreset = {preset = it; presetOpen = false; editQuestionDialog(null)}
        importHref = "import"
    }

    // Toolbar layout convention: view controls (sort, filter, view mode) on the
    // left, content actions on the right. The bar also serves as the visual cap
    // of the tab strip, so it is always rendered; the count label guarantees it
    // is never empty.
    RoomHeader {
        div {
            css {
                fontFamily = sansSerif
                fontSize = 13.px
                lineHeight = 16.px
                fontWeight = integer(600)
                color = Color("#888888")
                alignSelf = AlignSelf.center
            }
            +if (selectedTags.isEmpty())
                pluralize("question", allVisibleQuestions.size, includeCount = true)
            else
                "${visibleQuestions.size} of ${pluralize("question", allVisibleQuestions.size, includeCount = true)}"
        }
        if (usePermission(room, RoomPermission.ADD_QUESTION)) {
            RoomHeaderButton {
                +"Create a question"
                onClick = { addQuestion() }
            }
        }
    }

    // Tag filter bar
    if (allTags.isNotEmpty()) {
        Stack {
            direction = FlexDirection.row
            css {
                flexWrap = FlexWrap.wrap
                gap = 6.px
                padding = Padding(8.px, 16.px)
                maxWidth = layoutMode.wideContentWidth
                marginLeft = Auto.auto
                marginRight = Auto.auto
                width = 100.pct
                alignItems = AlignItems.center
            }
            react.dom.html.ReactHTML.span {
                css {
                    fontFamily = sansSerif
                    fontSize = 12.px
                    fontWeight = integer(600)
                    color = Color("#999999")
                    marginRight = 2.px
                }
                +"Filter:"
            }
            for (tag in allTags) {
                val isSelected = tag in selectedTags
                react.dom.html.ReactHTML.button {
                    css(ChipCSS) {
                        cursor = Cursor.pointer
                        if (isSelected) {
                            backgroundColor = MainPalette.primary.color
                            color = NamedColor.white
                        } else {
                            backgroundColor = Color("#F0F0F0")
                            color = Color("#666666")
                            border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
                        }
                    }
                    +tag
                    onClick = {
                        selectedTags = if (isSelected) selectedTags - setOf(tag) else selectedTags + setOf(tag)
                    }
                }
            }
            if (selectedTags.isNotEmpty()) {
                react.dom.html.ReactHTML.button {
                    css(ChipCSS) {
                        cursor = Cursor.pointer
                        backgroundColor = NamedColor.transparent
                        color = Color("#999999")
                        textDecoration = TextDecoration.underline
                    }
                    +"Clear"
                    onClick = { selectedTags = emptySet() }
                }
            }
        }
    }

    div {
        css {
            flexGrow = number(1.0)

            if (layoutMode >= LayoutMode.TABLET) {
                // Responsive tile grid: as many ~350px columns as fit into the wide
                // content area. Grid (not flex) so tiles in a row get equal heights.
                display = Display.grid
                gridTemplateColumns = "repeat(auto-fill, minmax(330px, 1fr))".unsafeCast<GridTemplateColumns>()
                // The container grows to fill the page; without this, grid's default
                // align-content: stretch inflates the row tracks (and thus tiles) to
                // fill the leftover height.
                alignContent = AlignContent.start
                padding = Padding(15.px, 0.px)
            } else {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.stretch
                padding = 15.px
            }
            width = 100.pct
            gap = 15.px
            maxWidth = layoutMode.wideContentWidth
            marginLeft = Auto.auto
            marginRight = Auto.auto
            position = Position.relative
            backgroundColor = Color("#f2f2f2")
        }

        visibleQuestions.map { question ->
            QuestionItem {
                this.key = question.id
                this.question = question
                // When I am the only one prediction, show it as "your prediction" instead of "group prediction",
                // which is confusing.
                if (!(useMyPredictions()[question.ref] != null && roomCounts[question.ref]?.predictors == 1)) {
                    this.groupPred = groupPreds[question.ref]
                }
                this.href = room.urlPrefix+question.urlPrefix
            }
        }
    }
}
