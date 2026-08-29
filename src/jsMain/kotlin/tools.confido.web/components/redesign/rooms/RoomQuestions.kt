package tools.confido.web.components.redesign.rooms

import tools.confido.web.components.*
import tools.confido.web.components.redesign.*
import tools.confido.web.components.redesign.basic.*
import tools.confido.api.Api
import tools.confido.payloads.requests.SetUiSetting
import tools.confido.web.Client
import tools.confido.web.components.redesign.forms.Select
import tools.confido.web.components.redesign.forms.Switch
import tools.confido.web.components.redesign.layout.LayoutMode
import tools.confido.web.components.redesign.layout.LayoutModeContext
import tools.confido.web.utils.runCoroutine
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

    // Spoiler toggle (the forecaster lens, v1): users who both predict and hold
    // VIEW_ALL_GROUP_PREDICTIONS can hide the group's answers on this ambient
    // surface — value text and PDF background alike — so scanning the room does
    // not anchor their own updates. Focused surfaces (the question page's group
    // tab) stay self-opting: clicking through is the deliberate peek. Per-room,
    // defaults to the forecaster view (anchoring is prospective); pointless in
    // rooms where the viewer is the only predictor.
    val isModcaster = usePermission(room, RoomPermission.SUBMIT_PREDICTION) &&
        usePermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
    // Member roles travel on their own topic (Room.members is censored down to
    // the viewer's own membership), subscribed only for modcaster candidates.
    val members = (useSubscription(
        if (isModcaster) Topics.roomMembers(room.ref) else null) as? WSData)?.data
    val canToggleSpoilers = isModcaster &&
        (members?.count { it.role.hasPermission(RoomPermission.SUBMIT_PREDICTION) } ?: 0) >= 2
    // Persisted per (user × room) on the user record (User.uiSettings, synced
    // back via the self slice); absence = the default, the forecaster view.
    val spoilerKey = "roomSpoilers:${room.id}"
    val serverShown = useMyUser()?.uiSettings?.get(spoilerKey) == "shown"
    // The switch flips immediately; the overlay clears when the server confirms.
    var optimisticShown by useState<Boolean?>(null)
    useEffect(serverShown) { optimisticShown = null }
    val spoilersShown = optimisticShown ?: serverShown
    val maskGroupPreds = canToggleSpoilers && !spoilersShown

    // Sort: "room" = the room's own order (as arranged by moderators, newest
    // first), "activity" = by the latest group-prediction update (available
    // from the already-subscribed roomGroupPred topic; no backend involved).
    var sortMode by useState("room")
    var filterMenuOpen by useState(false)

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
        // View controls (left): count, sort, tag filter.
        Stack {
            direction = FlexDirection.row
            css {
                gap = 10.px
                alignItems = AlignItems.center
                alignSelf = AlignSelf.center
            }
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 13.px
                    lineHeight = 16.px
                    fontWeight = integer(600)
                    color = Color("#888888")
                }
                +if (selectedTags.isEmpty())
                    pluralize("question", allVisibleQuestions.size, includeCount = true)
                else
                    "${visibleQuestions.size} of ${pluralize("question", allVisibleQuestions.size, includeCount = true)}"
            }
            if (allVisibleQuestions.size > 1) {
                Select {
                    css {
                        fontSize = 13.px
                        lineHeight = 15.px
                        fontWeight = integer(600)
                        color = Color("#666666")
                        padding = Padding(5.px, 8.px)
                        paddingLeft = 22.px
                        backgroundPositionX = 8.px
                        backgroundPositionY = 11.px
                    }
                    value = sortMode
                    onChange = { e -> sortMode = e.target.value }
                    react.dom.html.ReactHTML.option { value = "room"; +"Default order" }
                    react.dom.html.ReactHTML.option { value = "activity"; +"Recently active" }
                }
            }
            if (allTags.isNotEmpty()) {
                react.dom.html.ReactHTML.button {
                    css(ChipCSS) {
                        cursor = Cursor.pointer
                        if (selectedTags.isNotEmpty()) {
                            backgroundColor = MainPalette.primary.color
                            color = NamedColor.white
                        } else {
                            backgroundColor = Color("#F0F0F0")
                            color = Color("#666666")
                            border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
                        }
                    }
                    +if (selectedTags.isEmpty()) "Filter ▾" else "Filter: ${selectedTags.size} ▾"
                    onClick = { filterMenuOpen = true }
                }
                DialogMenu {
                    open = filterMenuOpen
                    onClose = { filterMenuOpen = false }
                    DialogMenuHeader { text = "Filter by tag" }
                    for (tag in allTags) {
                        DialogMenuItem {
                            text = (if (tag in selectedTags) "✓ " else "") + tag
                            keepOpen = true
                            onClick = {
                                selectedTags =
                                    if (tag in selectedTags) selectedTags - setOf(tag)
                                    else selectedTags + setOf(tag)
                            }
                        }
                    }
                    if (selectedTags.isNotEmpty()) {
                        DialogMenuItem {
                            text = "Clear filter"
                            onClick = { selectedTags = emptySet() }
                        }
                    }
                }
            }
        }
        // Actions and the spoiler lens (right corner).
        Stack {
            direction = FlexDirection.row
            css {
                gap = 12.px
                alignItems = AlignItems.center
            }
            if (canToggleSpoilers) {
                react.dom.html.ReactHTML.span {
                    title = if (spoilersShown)
                        "The tiles show the group's answers. Switch off to hide them while you form your own view."
                    else
                        "The group's answers are hidden on the tiles so they cannot anchor you. Switch on to show them."
                    css { display = Display.flex; alignItems = AlignItems.center }
                    Switch {
                        switchHeight = 26.0
                        switchWidth = 50.0
                        noColor = true
                        onIcon = EyeIcon.create()
                        offIcon = EyeOffIcon.create()
                        checked = spoilersShown
                        onChange = { e ->
                            val newShown = e.target.checked
                            optimisticShown = newShown
                            runCoroutine {
                                Client.call(Api.profileUiSetting(),
                                    SetUiSetting(spoilerKey, if (newShown) "shown" else null))
                            }
                        }
                    }
                }
            }
            if (usePermission(room, RoomPermission.ADD_QUESTION)) {
                RoomHeaderButton {
                    +"Create a question"
                    onClick = { addQuestion() }
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

        val sortedQuestions = when (sortMode) {
            // Latest group-prediction update first; untouched (or GP-invisible)
            // questions sink to the end.
            "activity" -> visibleQuestions.sortedByDescending { groupPreds[it.ref]?.ts ?: 0 }
            else -> visibleQuestions
        }
        sortedQuestions.map { question ->
            QuestionItem {
                this.key = question.id
                this.question = question
                // When I am the only one prediction, show it as "your prediction" instead of "group prediction",
                // which is confusing.
                if (!maskGroupPreds &&
                    !(useMyPredictions()[question.ref] != null && roomCounts[question.ref]?.predictors == 1)) {
                    this.groupPred = groupPreds[question.ref]
                }
                this.href = room.urlPrefix+question.urlPrefix
            }
        }
    }
}
