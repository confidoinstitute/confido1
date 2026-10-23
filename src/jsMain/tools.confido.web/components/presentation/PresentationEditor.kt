package tools.confido.web.components.presentation

import tools.confido.api.Api
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.rooms.Room
import tools.confido.web.Client
import tools.confido.web.components.presenter.ExpectedWorldContext
import tools.confido.web.components.*
import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.*
import tools.confido.web.components.rooms.RoomContext
import tools.confido.web.ext.dndkit.applyListeners
import tools.confido.web.ext.dndkit.core.*
import tools.confido.web.ext.dndkit.modifiers.restrictToVerticalAxis
import tools.confido.web.ext.dndkit.modifiers.restrictToWindowEdges
import tools.confido.web.ext.dndkit.sortable.*
import tools.confido.web.ext.dndkit.utilities.closestCenter
import emotion.react.css
import tools.confido.web.hooks.useCoroutineLock
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import js.objects.unsafeJso as jso
import kotlinx.coroutines.await
import tools.confido.payloads.requests.EditPresentation
import tools.confido.payloads.requests.ImportUrlRequest
import tools.confido.web.ApiError
import tools.confido.web.components.presenter.FlowActionDescription
import tools.confido.web.components.presenter.openPresenterWindow
import tools.confido.web.components.presenter.setPresenterView
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.span
import react.router.useBlocker
import web.events.Event
import web.events.EventType
import web.events.addEventListener
import web.events.removeEventListener
import web.window.window
import tools.confido.extensions.FlowItemCategory
import tools.confido.state.*
import tools.confido.utils.generateId
import web.cssom.*
import web.html.InputType
import web.html.file
import web.html.text

/**
 * Derive a display category for a flow action, used for item badges and colors.
 * Looks up the matching [FlowItemTypeEntry] to get the semantic category.
 */
internal fun FlowAction.itemCategory(): FlowItemCategory {
    val entry = flowItemTypes.find { it.matches(this) }
    return entry?.category ?: if (presenterView != null) FlowItemCategory.VIEW else FlowItemCategory.SIDE_EFFECT
}

/** Background color for a flow item row based on its type. */
internal fun flowItemBgColor(item: FlowItem): Color = when (item) {
    is SlideItem -> Color("#f5f5f5")
    is ActionItem -> when (item.action.itemCategory()) {
        FlowItemCategory.VIEW -> Color("#e3f2fd")
        FlowItemCategory.EXPANDABLE -> Color("#f3e5f5")
        FlowItemCategory.BOTH -> Color("#ede7f6")
        FlowItemCategory.SIDE_EFFECT -> Color("#fff3e0")
    }
    is NoteItem -> if (item.warning) Color("#ffebee") else Color("#e8f5e9")
    is ForEachItem -> Color("#f3e5f5")
}

/** Badge color for a flow item row. */
internal fun flowItemBadgeColor(item: FlowItem): Color = when (item) {
    is SlideItem -> Color("#757575")
    is ActionItem -> when (item.action.itemCategory()) {
        FlowItemCategory.VIEW -> Color("#1565c0")
        FlowItemCategory.EXPANDABLE -> Color("#7b1fa2")
        FlowItemCategory.BOTH -> Color("#512da8")
        FlowItemCategory.SIDE_EFFECT -> Color("#e65100")
    }
    is NoteItem -> if (item.warning) Color("#c62828") else Color("#2e7d32")
    is ForEachItem -> Color("#7b1fa2")
}

/** Badge text for a flow item row. */
internal fun flowItemBadgeText(item: FlowItem): String = when (item) {
    is SlideItem -> "SLIDE"
    is ActionItem -> when (item.action.itemCategory()) {
        FlowItemCategory.VIEW -> "VIEW"
        FlowItemCategory.EXPANDABLE -> "EXPAND"
        FlowItemCategory.BOTH -> "ACTION+VIEW"
        FlowItemCategory.SIDE_EFFECT -> "ACTION"
    }
    is NoteItem -> if (item.warning) "WARNING" else "NOTE"
    is ForEachItem -> "REPEAT"
}

external interface PresentationEditorProps : Props {
    var data: PresentationWSData
}

private fun readFileAsBytes(file: dynamic): kotlin.js.Promise<ByteArray> = kotlin.js.Promise { resolve, reject ->
    val reader = js("new FileReader()")
    reader.onload = {
        val arrayBuffer = reader.result
        val uint8 = js("new Uint8Array(arrayBuffer)")
        val len: Int = uint8.length as Int
        val bytes = ByteArray(len) { i: Int -> (uint8[i] as Number).toByte() }
        resolve(bytes)
    }
    reader.onerror = {
        reject(Exception("Failed to read file"))
    }
    reader.readAsArrayBuffer(file)
}

/** Check whether a string looks like a Google Slides URL (any of the edit/preview/export forms). */
private fun isGoogleSlidesUrl(url: String): Boolean =
    url.contains("docs.google.com/presentation/")

val PresentationEditor = FC<PresentationEditorProps> { props ->
    val room = use(RoomContext)!!
    val submitLock = useCoroutineLock()
    val pres = props.data.presentation
    val roomQuestions = useRoomQuestions()

    // Local mutable copy of items for editing + DnD. All mutations funnel through
    // setItems -> normalizeFlowItems, upholding the slide-order invariant (slides
    // ascending, no duplicates) no matter how items were dragged or inserted.
    var items by useState(pres.items)
    var slideLabels by useState(pres.slideLabels)
    var dirty by useState(false)
    var uploading by useState(false)
    // Slides dialog: PDF upload or Google Slides URL import (replaces any current deck)
    var slidesDialogOpen by useState(false)
    var importUrl by useState("")
    var slidesError by useState<String?>(null)
    // Drill-in: non-null while a loop's body is open in the sub-editor.
    var editingLoopId by useState<String?>(null)

    // Unified config dialog state
    var dialogOpen by useState(false)
    var editingItemId by useState<String?>(null) // null = inserting new, non-null = editing existing
    var dialogInsertAfter by useState(-1) // insertion position for new items
    var dialogInitial by useState<FlowAction?>(null) // initial action for editing


    // Sync from server when presentation changes (e.g. after slide upload)
    useEffect(pres.items, pres.slideLabels) {
        if (!dirty) {
            items = pres.items
            slideLabels = pres.slideLabels
        }
    }

    fun setItems(newItems: List<FlowItem>) {
        items = normalizeFlowItems(newItems)
        dirty = true
    }

    fun updateItem(id: String, updater: (FlowItem) -> FlowItem) {
        setItems(items.map { if (it.id == id) updater(it) else it })
    }

    val editingLoop = editingLoopId?.let { id -> items.firstOrNull { it.id == id } as? ForEachItem }

    // The list currently shown/edited: a loop's body while drilled in, else the flow.
    fun currentItems(): List<FlowItem> = editingLoop?.body ?: items
    fun setCurrentItems(newItems: List<FlowItem>) {
        val loop = editingLoop
        if (loop != null) updateItem(loop.id) { (it as ForEachItem).copy(body = normalizeFlowItems(newItems)) }
        else setItems(newItems)
    }
    fun insertCurrent(item: FlowItem, afterIndex: Int) {
        val l = currentItems().toMutableList()
        l.add((afterIndex + 1).coerceIn(0, l.size), item)
        setCurrentItems(l)
    }

    // Items that cannot run as configured (unset question, blank marker name, ...);
    // a loop row surfaces its body's first problem. They block saving — the server
    // rejects them anyway (validateFlowItems).
    fun problemsOf(list: List<FlowItem>): Map<String, String> = buildMap {
        list.forEach { item ->
            when (item) {
                is ActionItem -> item.action.configProblem()?.let { put(item.id, it) }
                is ForEachItem -> item.body.firstNotNullOfOrNull { b ->
                    (b as? ActionItem)?.action?.configProblem()
                }?.let { put(item.id, it) }
                else -> {}
            }
        }
    }
    val configProblems = problemsOf(items)

    fun save() {
        submitLock {
            Client.call(Api.presentationEdit(room.ref),
                EditPresentation(items = items, slideLabels = slideLabels))
            dirty = false
        }
    }

    fun missingSlideIndices() = (0 until pres.numSlides)
        .filter { i -> items.none { it is SlideItem && it.slideIndex == i } }

    fun addSlideItems() {
        // Normalization sinks the appended slides into their proper slots.
        setItems(items + missingSlideIndices().map { SlideItem(id = generateId(), slideIndex = it) })
    }

    // Unsaved-changes guards (save is explicit — mid-configuration items are
    // legitimately invalid, so implicit saving is not an option): hard
    // reload/close via beforeunload…
    useEffectWithCleanup(dirty) {
        if (dirty) {
            val handler: (Event) -> Unit = { it.preventDefault() }
            window.addEventListener(EventType<Event>("beforeunload"), handler)
            onCleanup { window.removeEventListener(EventType<Event>("beforeunload"), handler) }
        }
    }
    // …and in-app route changes via the router blocker (the app root is a data
    // router, so this catches tab switches and sidebar navigation).
    val blocker = useBlocker(dirty)
    ConfirmDialog {
        open = blocker.asDynamic().state == "blocked"
        title = "Leave without saving?"
        cancelText = "Stay"
        confirmText = "Discard changes"
        onClose = { blocker.asDynamic().reset() }
        onConfirm = { blocker.asDynamic().proceed() }
        +"The presentation flow has unsaved changes. If you leave now, they will be lost."
    }

    // The world as expected at the edited/inserted step: preceding steps simulated
    // (question states, created marks) so pickers can offer flow-created marks and
    // editors can show effective-at-this-step reference lines. Inside a loop body,
    // the world is "before the loop" + the body prefix (sentinel left in place, so
    // marks created on the current question stay pickable) + the candidate set.
    val editIdx = editingItemId?.let { id -> currentItems().indexOfFirst { it.id == id } } ?: (dialogInsertAfter + 1)
    val expectedWorld = useMemo(items, editIdx, roomQuestions, room, dialogOpen, editingLoopId) {
        if (!dialogOpen) null
        else {
            val lookup = object : EntityLookup {
                override fun question(ref: Ref<Question>) = roomQuestions.firstOrNull { it.id == ref.id }
                override fun room(ref: Ref<Room>) = room.takeIf { it.id == ref.id }
            }
            val loop = editingLoop
            if (loop == null) computeExpectedWorld(items, editIdx, room, lookup)
            else {
                val loopIdx = items.indexOfFirst { it.id == loop.id }
                val base = computeExpectedWorld(items, loopIdx, room, lookup)
                val w = loop.body.take(editIdx.coerceAtLeast(0)).foldIndexed(base) { i, acc, b ->
                    (b as? ActionItem)?.action?.simulate(acc.copy(stepIndex = i)) ?: acc
                }
                w.copy(loopCandidates = base.candidatesFor(loop))
            }
        }
    }

    // A gap "+" (or the end-of-list add button) goes straight into the config
    // dialog — the type chooser lives there (chips), no intermediate menu.
    fun openInsertDialog(afterIndex: Int) {
        dialogInsertAfter = afterIndex
        editingItemId = null
        dialogInitial = null
        dialogOpen = true
    }

    // Both slide sources run the same server pipeline (render, label, auto-populate
    // the flow); on success the editor resyncs from the server (dirty reset).
    fun uploadSlidesPdf(file: dynamic) {
        submitLock {
            uploading = true
            slidesError = null
            try {
                val byteArray = readFileAsBytes(file).await()
                val resp = Client.httpClient.submitFormWithBinaryData(
                    url = "${room.urlPrefix}/presentation/upload-slides",
                    formData = formData {
                        append("file", byteArray, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"presentation.pdf\"")
                            append(HttpHeaders.ContentType, "application/pdf")
                        })
                    }
                )
                if (resp.status.isSuccess()) {
                    slidesDialogOpen = false
                    dirty = false
                } else {
                    slidesError = resp.body<String>().ifBlank { "Upload failed" }
                }
            } catch (e: Exception) {
                slidesError = e.message ?: "Upload failed"
            } finally {
                uploading = false
            }
        }
    }

    fun importFromGoogleSlides() {
        submitLock {
            uploading = true
            slidesError = null
            try {
                Client.call(Api.presentationImportUrl(room.ref), ImportUrlRequest(url = importUrl))
                slidesDialogOpen = false
                importUrl = ""
                dirty = false
            } catch (e: ApiError) {
                slidesError = e.message
            } catch (e: Exception) {
                slidesError = e.message ?: "Import failed"
            } finally {
                uploading = false
            }
        }
    }

    val hasSlides = pres.numSlides > 0
    Dialog {
        open = slidesDialogOpen
        title = if (hasSlides) "Replace slides" else "Upload slides"
        // Each source has its own trigger (file pick / Import), like the CSV import step.
        action = ""
        disabledAction = true
        onClose = { if (!uploading) { slidesDialogOpen = false; slidesError = null } }
        Stack {
            css { gap = 20.px; minWidth = 480.px }
            // FormSection pads its own content; the loose rows pad themselves to match.
            div {
                css { fontFamily = sansSerif; fontSize = 14.px; lineHeight = 20.px; color = Color("#666666"); padding = Padding(0.px, 20.px) }
                +(if (hasSlides) "The new deck replaces the current ${pres.numSlides} slides. "
                  else "")
                +"Every slide is added to the flow automatically; slides already in the flow keep their place. "
                +"Limits: ${MAX_SLIDES_PDF_BYTES / (1024 * 1024)} MB per PDF, $MAX_SLIDES slides."
            }
            if (dirty) div {
                css {
                    fontFamily = sansSerif; fontSize = 13.px; lineHeight = 18.px
                    color = Color("#b26a00"); backgroundColor = Color("#fff3cd")
                    padding = 10.px; borderRadius = 5.px; margin = Margin(0.px, 20.px)
                }
                +"You have unsaved flow changes. They will be discarded when the slides are replaced — close this dialog and save first to keep them."
            }
            FormSection {
                title = "Upload a PDF file"
                div {
                    css {
                        border = Border(2.px, LineStyle.dashed, Color("#CCCCCC"))
                        borderRadius = 8.px
                        padding = 20.px
                        textAlign = TextAlign.center
                        backgroundColor = Color("#FAFAFA")
                        "&:hover" { borderColor = Color("#6319FF"); backgroundColor = Color("#F5F0FF") }
                    }
                    input {
                        type = InputType.file
                        accept = ".pdf"
                        css { width = 100.pct; cursor = Cursor.pointer; fontFamily = sansSerif; fontSize = 15.px }
                        disabled = uploading
                        onChange = onChange@{ event ->
                            val files = event.target.asDynamic().files ?: return@onChange
                            val file = files[0] ?: return@onChange
                            // Same limit the server enforces; refusing here spares the upload.
                            if ((file.size as Number).toDouble() > MAX_SLIDES_PDF_BYTES.toDouble()) {
                                slidesError = "The PDF is larger than ${MAX_SLIDES_PDF_BYTES / (1024 * 1024)} MB."
                                event.target.asDynamic().value = ""
                            } else uploadSlidesPdf(file)
                        }
                    }
                }
            }
            // Divider
            div {
                css {
                    display = Display.flex; alignItems = AlignItems.center; gap = 12.px
                    fontFamily = sansSerif; fontSize = 13.px; color = Color("#999999")
                    padding = Padding(0.px, 20.px)
                }
                div { css { flex = Flex(number(1.0), number(1.0), Auto.auto); height = 1.px; backgroundColor = Color("#DDDDDD") } }
                +"or"
                div { css { flex = Flex(number(1.0), number(1.0), Auto.auto); height = 1.px; backgroundColor = Color("#DDDDDD") } }
            }
            FormSection {
                title = "Import from Google Slides"
                FormField {
                    comment = "Paste a Google Slides sharing link. The presentation must be publicly accessible " +
                        "(\"Anyone with the link\" in sharing settings)."
                    Stack {
                        direction = FlexDirection.row
                        css { gap = 10.px; alignItems = AlignItems.stretch }
                        TextInput {
                            css { flex = Flex(number(1.0), number(1.0), Auto.auto) }
                            placeholder = "https://docs.google.com/presentation/d/..."
                            value = importUrl
                            disabled = uploading
                            onChange = { e -> importUrl = e.target.value; slidesError = null }
                            onKeyDown = { e ->
                                if (e.key == "Enter" && !uploading && isGoogleSlidesUrl(importUrl)) importFromGoogleSlides()
                            }
                        }
                        Button {
                            disabled = uploading || !isGoogleSlidesUrl(importUrl)
                            onClick = { importFromGoogleSlides() }
                            +"Import"
                        }
                    }
                }
            }
            if (uploading) div {
                css { fontFamily = sansSerif; fontSize = 14.px; color = Color("#6319FF"); textAlign = TextAlign.center; padding = Padding(0.px, 20.px) }
                +"Processing slides…"
            }
            slidesError?.let { err ->
                div {
                    css {
                        fontFamily = sansSerif
                        fontSize = 14.px
                        color = Color("#F35454")
                        padding = 10.px
                        backgroundColor = Color("#FFF0F0")
                        borderRadius = 5.px
                        margin = Margin(0.px, 20.px)
                    }
                    +err
                }
            }
        }
    }

    // Unified flow item configuration dialog
    ExpectedWorldContext.Provider {
        value = expectedWorld
        FlowItemConfigDialog {
            open = dialogOpen
            action = dialogInitial
            inLoopBody = editingLoop != null
            onClose = { dialogOpen = false; editingItemId = null }
            onConfirm = { rawItem ->
                // In a loop body, unset question refs default to the current question.
                val newItem = if (editingLoop != null && rawItem is ActionItem)
                    rawItem.copy(action = rawItem.action.mapQuestionRefs {
                        if (it.id.isEmpty()) CURRENT_QUESTION else it
                    })
                else rawItem
                val existingId = editingItemId
                if (existingId != null) {
                    // Editing an existing action item: keep the row, replace the action.
                    setCurrentItems(currentItems().map { item ->
                        if (item.id == existingId && item is ActionItem && newItem is ActionItem)
                            item.copy(action = newItem.action)
                        else item
                    })
                } else {
                    insertCurrent(newItem, dialogInsertAfter)
                    // A freshly created loop is empty — jump straight into its body.
                    if (newItem is ForEachItem) editingLoopId = newItem.id
                }
                editingItemId = null
            }
        }
    }

    // Sticky toolbar (see PresentationHeader): the flow can be long, so the
    // commit affordances must not scroll away with it.
    val missing = missingSlideIndices().size
    PresentationHeader {
        presentation = pres
        if (dirty) span {
            css { fontSize = 12.px; color = Color("#e65100"); fontFamily = sansSerif; fontWeight = integer(400) }
            +(if (configProblems.isEmpty()) "Unsaved changes"
              else "${configProblems.size} ${if (configProblems.size == 1) "item needs" else "items need"} configuration before saving")
        }
        // Slides drop out of the flow only by explicit removal (uploads
        // auto-populate), so the affordance appears just when there is
        // something to add back.
        if (missing > 0) Button {
            css {
                margin = 0.px
                padding = Padding(6.px, 16.px)
                fontSize = 13.px
                backgroundColor = Color("#E8E0F5")
                color = Color("#6319FF")
            }
            title = "$missing ${if (missing == 1) "slide is" else "slides are"} not in the flow"
            +"Add missing slides ($missing)"
            onClick = { addSlideItems() }
        }
        Button {
            css {
                margin = 0.px
                padding = Padding(6.px, 16.px)
                fontSize = 13.px
            }
            disabled = uploading
            +(if (hasSlides) "Replace slides" else "Upload slides")
            onClick = { slidesError = null; slidesDialogOpen = true }
        }
        if (dirty) Button {
            css {
                margin = 0.px
                padding = Padding(6.px, 16.px)
                fontSize = 13.px
                backgroundColor = Color("#4caf50")
            }
            disabled = configProblems.isNotEmpty()
            +"Save"
            onClick = { save() }
        }
        // Run starts the *saved* flow; while there are unsaved edits it would
        // silently present stale content, so it waits for Save.
        if (pres.items.isNotEmpty()) Button {
            css {
                margin = 0.px
                padding = Padding(6.px, 16.px)
                fontSize = 13.px
                backgroundColor = Color("#4caf50")
            }
            disabled = dirty
            title = if (dirty) "Save your changes first" else ""
            +"Run"
            onClick = {
                submitLock {
                    Client.call(Api.presentationStart(room.ref))
                    // Set PresentationPV on user session and open presenter window
                    setPresenterView(PresentationPV(room.ref))
                    openPresenterWindow()
                }
            }
        }
    }

    Stack {
        css {
            padding = 15.px
            gap = 10.px
        }

        FlowItemList {
            this.items = items
            this.presentation = pres
            this.slideLabels = slideLabels
            this.configProblems = configProblems
            this.onItemsChange = { setItems(it) }
            this.onEditAction = { actionItem ->
                editingItemId = actionItem.id
                dialogInitial = actionItem.action
                dialogOpen = true
            }
            this.onOpenLoop = { loop -> editingLoopId = loop.id }
            this.onUpdateSlideLabel = { slideIdx, label ->
                val newLabels = slideLabels.toMutableList()
                while (newLabels.size <= slideIdx) newLabels.add("Slide ${newLabels.size + 1}")
                newLabels[slideIdx] = label
                slideLabels = newLabels
                dirty = true
            }
            this.openInsertDialog = { idx -> openInsertDialog(idx) }
            this.emptyText =
                if (hasSlides) "No items yet. Add the missing slides, or add items one by one."
                else "No items yet. Upload slides (PDF or Google Slides), or add items one by one."
        }
    }

    // Loop body editor: a dialog rather than an in-place drill-down, so the main
    // screen always shows a single context. The step-config dialog stacks on top
    // (per-layer backdrops handle the layering). Edits write through to the flow;
    // "Done" just closes.
    ForEachDialog {
        open = editingLoop != null
        loop = editingLoop
        presentation = pres
        this.slideLabels = slideLabels
        bodyProblems = editingLoop?.let { problemsOf(it.body) } ?: emptyMap()
        tags = roomQuestions.flatMap { it.tags }.distinct().sorted()
        this.roomQuestions = roomQuestions
        onLoopUpdate = { f ->
            editingLoop?.let { l -> updateItem(l.id) { f(it as ForEachItem) } }
        }
        onBodyChange = { setCurrentItems(it) }
        onEditAction = { actionItem ->
            editingItemId = actionItem.id
            dialogInitial = actionItem.action
            dialogOpen = true
        }
        this.openInsertDialog = { idx -> openInsertDialog(idx) }
        onClose = { editingLoopId = null }
    }
}

private external interface ForEachDialogProps : Props {
    var open: Boolean
    var loop: ForEachItem?
    var presentation: Presentation
    var slideLabels: List<String>
    var bodyProblems: Map<String, String>
    var tags: List<String>
    var roomQuestions: List<Question>
    var onLoopUpdate: (((ForEachItem) -> ForEachItem)) -> Unit
    var onBodyChange: (List<FlowItem>) -> Unit
    var onEditAction: (ActionItem) -> Unit
    var openInsertDialog: (Int) -> Unit
    var onClose: () -> Unit
}

private val ForEachDialog = FC<ForEachDialogProps> { props ->
    val loop = props.loop
    Dialog {
        open = props.open
        onClose = { props.onClose() }
        title = "Repeat over questions"
        action = "Done"
        onAction = { props.onClose() }
        if (loop != null) FormSection {
            FormField {
                title = "Repeat over"
                comment = "Each step runs once per question, referencing it as the current question." +
                    if (props.tags.isEmpty())
                        " Tip: give questions tags in the question editor to select whole groups at once."
                    else ""
                LoopSelectionPicker {
                    this.loop = loop
                    this.roomQuestions = props.roomQuestions
                    this.tags = props.tags
                    this.onLoopUpdate = props.onLoopUpdate
                }
            }
            FormField {
                title = "Repeated steps"
                Stack {
                    css { minWidth = 560.px }
                    FlowItemList {
                    this.items = loop.body
                    presentation = props.presentation
                    this.slideLabels = props.slideLabels
                    configProblems = props.bodyProblems
                    onItemsChange = props.onBodyChange
                    onEditAction = props.onEditAction
                    onOpenLoop = null
                    onUpdateSlideLabel = { _, _ -> }
                        this.openInsertDialog = props.openInsertDialog
                        emptyText = "No steps in this loop yet."
                    }
                }
            }
        }
    }
}

/**
 * The loop's question-selection control: a select-look button summarizing the
 * selection, opening a checkbox popover. "All room questions" and per-tag rows
 * are *dynamic* checks (they imply their members' checkmarks and keep including
 * questions added later); question rows are static picks. Unchecking an implied
 * question dissolves the covering dynamic checks into static picks (see
 * [ForEachItem.toggleQuestion]). Plain checkboxes in a popover rather than a
 * react-select: everything here is a toggle, so a combobox bought only fights
 * (decoded clicks, suppressed value rendering, inconsistent selected styling).
 */
private external interface LoopSelectionPickerProps : Props {
    var loop: ForEachItem
    var roomQuestions: List<Question>
    var tags: List<String>
    var onLoopUpdate: (((ForEachItem) -> ForEachItem)) -> Unit
}

private val LoopSelectionPicker = FC<LoopSelectionPickerProps> { props ->
    var open by useState(false)
    var search by useState("")
    val loop = props.loop
    val qs = props.roomQuestions
    val n = qs.count { loop.selects(it) }
    val summary = when {
        loop.all -> "All room questions ($n)"
        loop.tags.isNotEmpty() && loop.questions.isEmpty() ->
            "Questions tagged ${loop.tags.sorted().joinToString(", ") { "\"$it\"" }} ($n)"
        loop.tags.isEmpty() && loop.questions.isEmpty() -> "No questions selected"
        else -> "$n of ${qs.size} questions selected"
    }

    ButtonUnstyled {
        css {
            width = 100.pct
            display = Display.flex
            justifyContent = JustifyContent.spaceBetween
            alignItems = AlignItems.center
            gap = 8.px
            padding = Padding(8.px, 10.px)
            border = Border(1.px, LineStyle.solid, Color("#cccccc"))
            borderRadius = 4.px
            backgroundColor = NamedColor.white
            fontFamily = sansSerif
            fontSize = 14.px
            textAlign = TextAlign.left
            cursor = Cursor.pointer
        }
        span { +summary }
        span { css { color = Color("#888888") }; +"▾" }
        // The popover matches this control's width automatically: MenuPopover
        // never renders narrower than its trigger.
        onClick = { open = true }
    }

    // A row toggles via a single click anywhere on it; the checkbox itself is
    // display-only (its click bubbles to the row) to avoid double toggling.
    fun ChildrenBuilder.checkRow(rowKey: String, checked: Boolean, bold: Boolean,
                                 label: String, onToggle: () -> Unit) {
        Stack {
            key = rowKey
            direction = FlexDirection.row
            css {
                alignItems = AlignItems.center
                gap = 8.px
                padding = Padding(3.px, 15.px)
                cursor = Cursor.pointer
                "&:hover" { backgroundColor = Color("#f5f5f5") }
            }
            Checkbox {
                this.checked = checked
                onChange = {}
                // No <label> wrapper: a label forwards clicks to its input, so the
                // row's onClick would fire twice per checkbox click (= no-op toggle).
                wrapLabel = false
            }
            span {
                css {
                    fontFamily = sansSerif
                    fontSize = 14.px
                    if (bold) fontWeight = integer(600)
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                }
                +label
            }
            onClick = { onToggle() }
        }
    }

    DialogMenu {
        this.open = open
        onClose = { open = false; search = "" }
        DialogMenuHeader { text = "Repeat over" }
        div {
            div {
                css { padding = Padding(0.px, 15.px, 8.px) }
                TextInput {
                    value = search
                    placeholder = "Search questions…"
                    autoFocus = true
                    onChange = { e -> search = e.target.value }
                }
            }
            val filter = search.trim()
            if (filter.isEmpty()) {
                checkRow("//all", loop.all, true, "All room questions (${qs.size})") {
                    props.onLoopUpdate { it.toggleAll() }
                }
                props.tags.forEach { t ->
                    checkRow("//tag:$t", t in loop.tags, true,
                        "Questions tagged \"$t\" (${qs.count { q -> t in q.tags }})") {
                        props.onLoopUpdate { it.toggleTag(t) }
                    }
                }
                div { css { borderTop = Border(1.px, LineStyle.solid, Color("#eeeeee")); margin = Margin(6.px, 0.px) } }
            }
            div {
                css { maxHeight = 260.px; overflowY = Auto.auto }
                qs.filter { filter.isEmpty() || it.name.contains(filter, ignoreCase = true) }.forEach { q ->
                    checkRow(q.id, loop.selects(q), false, q.name) {
                        props.onLoopUpdate { it.toggleQuestion(q, qs) }
                    }
                }
            }
            div {
                css {
                    padding = Padding(6.px, 15.px, 0.px)
                    fontFamily = sansSerif
                    fontSize = 11.px
                    color = Color("#999999")
                }
                +"\"All\" and tag checks keep including questions added later; picked questions are fixed."
            }
        }
    }
}

// The sortable flow list: rows, hover insert zones, end-of-list add affordance and
// empty state. Serves both levels — the main flow and a loop body (drill-in); only
// one instance is mounted at a time. Normalization is the owner's concern
// (onItemsChange receives the raw reordered list).
private external interface FlowItemListProps : Props {
    var items: List<FlowItem>
    var presentation: Presentation
    var slideLabels: List<String>
    var configProblems: Map<String, String>
    var onItemsChange: (List<FlowItem>) -> Unit
    var onEditAction: (ActionItem) -> Unit
    var onOpenLoop: ((ForEachItem) -> Unit)?
    var onUpdateSlideLabel: (Int, String) -> Unit
    var openInsertDialog: (Int) -> Unit
    var emptyText: String
}

private val FlowItemList = FC<FlowItemListProps> { props ->
    val items = props.items
    var dragging by useState(false)
    val mouseSensor = useSensor<MouseSensorOptions>(MouseSensor)
    val sensors = useSensors(mouseSensor)
    val itemIds = items.map { it.id }.toTypedArray()

    // Rows sit close together; the hover zones between them double as insertion
    // points and provide the visual spacing.
    div {
        css { display = Display.flex; flexDirection = FlexDirection.column }
        DndContext {
            collisionDetection = closestCenter
            modifiers = arrayOf(restrictToVerticalAxis, restrictToWindowEdges)
            this.sensors = sensors
            onDragStart = { dragging = true }
            onDragEnd = { event ->
                dragging = false
                val over = event.over
                if (over != null && event.active.id != over.id) {
                    val oldIndex = items.indexOfFirst { it.id == event.active.id }
                    val newIndex = items.indexOfFirst { it.id == over.id }
                    if (oldIndex >= 0 && newIndex >= 0) {
                        props.onItemsChange(arrayMove(items.toTypedArray(), oldIndex, newIndex).toList())
                    }
                }
            }

            SortableContext {
                this.items = itemIds
                strategy = verticalListSortingStrategy

                InsertHoverZone {
                    visible = !dragging && items.isNotEmpty()
                    onClick = { props.openInsertDialog(-1) }
                }
                items.forEachIndexed { index, item ->
                    FlowItemRow {
                        key = item.id
                        this.item = item
                        this.index = index
                        this.presentation = props.presentation
                        this.slideLabels = props.slideLabels
                        this.configProblem = props.configProblems[item.id]
                        this.onRemove = { props.onItemsChange(items.filter { it.id != item.id }) }
                        this.onUpdate = { updated -> props.onItemsChange(items.map { if (it.id == item.id) updated else it }) }
                        this.onEditAction = { props.onEditAction(it) }
                        this.onOpenLoop = props.onOpenLoop
                        this.onUpdateSlideLabel = props.onUpdateSlideLabel
                    }

                    InsertHoverZone {
                        key = "insert-after-${item.id}"
                        visible = !dragging
                        onClick = { props.openInsertDialog(index) }
                    }
                }
            }
        }
    }

    if (items.isEmpty()) {
        Stack {
            css {
                alignItems = AlignItems.center
                gap = 8.px
                padding = Padding(30.px, 20.px)
                border = Border(1.px, LineStyle.dashed, Color("#ccc"))
                borderRadius = 8.px
                fontFamily = sansSerif
                color = Color("#888")
                fontSize = 14.px
            }
            +props.emptyText
            Button {
                css { margin = 0.px; padding = Padding(6.px, 16.px); fontSize = 13.px }
                +"+ Add first item"
                onClick = { props.openInsertDialog(-1) }
            }
        }
    } else {
        // Always-visible add affordance at the end of the list
        div {
            ButtonUnstyled {
                css {
                    fontSize = 12.px
                    color = Color("#888")
                    fontFamily = sansSerif
                    padding = Padding(3.px, 10.px)
                    borderRadius = 4.px
                    border = Border(1.px, LineStyle.dashed, Color("#ccc"))
                    cursor = Cursor.pointer
                    "&:hover" {
                        borderColor = Color("#999")
                        color = Color("#555")
                    }
                }
                +"+ Add item"
                onClick = { props.openInsertDialog(items.lastIndex) }
            }
        }
    }
}

/**
 * A thin hover zone between two rows: invisible until hovered, then reveals a
 * centered "+" pill and a guide line. Overlaps the row edges via negative
 * margins so rows sit close together while the hit target stays comfortable
 * (desktop-only interface — hover is fine).
 */
external interface InsertHoverZoneProps : Props {
    /** Hidden while a drag is in progress (so it can't swallow drop targets). */
    var visible: Boolean
    var onClick: () -> Unit
}

val InsertHoverZone = FC<InsertHoverZoneProps> { props ->
    div {
        css {
            height = 12.px
            margin = Margin((-5).px, 0.px)
            position = Position.relative
            zIndex = integer(2)
            display = if (props.visible) Display.block else None.none
            "&:hover .insert-line" { opacity = number(1.0) }
            "&:hover .insert-pill" { opacity = number(1.0) }
        }
        div {
            css(ClassName("insert-line")) {
                position = Position.absolute
                top = 50.pct
                left = 0.px
                right = 0.px
                borderTop = Border(1.px, LineStyle.dashed, Color("#c9b8ee"))
                opacity = number(0.0)
                transition = "opacity 0.1s".unsafeCast<Transition>()
                pointerEvents = None.none
            }
        }
        ButtonUnstyled {
            css(ClassName("insert-pill")) {
                position = Position.absolute
                top = 50.pct
                left = 50.pct
                transform = translate((-50).pct, (-50).pct)
                opacity = number(0.0)
                transition = "opacity 0.1s".unsafeCast<Transition>()
                fontSize = 11.px
                fontFamily = sansSerif
                whiteSpace = WhiteSpace.nowrap
                padding = Padding(1.px, 10.px)
                borderRadius = 8.px
                backgroundColor = Color("#E8E0F5")
                color = Color("#6319FF")
                cursor = Cursor.pointer
                "&:hover" { backgroundColor = Color("#dccdf3") }
            }
            +"+ Add"
            onClick = { props.onClick() }
        }
    }
}


// A single row in the flow item list (sortable)
external interface FlowItemRowProps : Props {
    var item: FlowItem
    /** Zero-based position in the flow (displayed as a 1-based step number). */
    var index: Int
    var presentation: Presentation
    var slideLabels: List<String>
    /** Non-null when the item is not runnable as configured (see FlowAction.configProblem). */
    var configProblem: String?
    var onRemove: () -> Unit
    var onUpdate: (FlowItem) -> Unit
    var onEditAction: ((ActionItem) -> Unit)?
    /** Open a loop's body in the drill-in sub-editor (null inside body lists). */
    var onOpenLoop: ((ForEachItem) -> Unit)?
    var onUpdateSlideLabel: (Int, String) -> Unit
}

val FlowItemRow = FC<FlowItemRowProps> { props ->
    val item = props.item
    val sortable = useSortable(jso { id = item.id })

    div {
        asDynamic().ref = sortable.setNodeRef
        css {
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.center
            gap = 8.px
            padding = Padding(4.px, 8.px)
            borderRadius = 4.px
            backgroundColor = flowItemBgColor(item)
            border = Border(1.px, LineStyle.solid, Color("#e0e0e0"))
            fontFamily = sansSerif
            fontSize = 13.px
            "&:hover .drag-handle" { opacity = number(1.0) }
            // Live reorder preview: the sortable transform must be set as the CSS
            // `transform` property (as in QuestionRow) — an `asDynamic().style`
            // assignment inside the emotion builder is silently ignored.
            sortable.transform?.let { t ->
                transform = translate(t.x.px, t.y.px)
            }
        }

        // Step number (same column style as the runner's compact list)
        span {
            css {
                width = 30.px
                textAlign = TextAlign.right
                color = Color("#999")
                fontSize = 12.px
                flexShrink = number(0.0)
            }
            +"${props.index + 1}"
        }

        // Drag handle, revealed on row hover (width stays reserved so text doesn't shift).
        // NB the marker class must go through the merging css(ClassName(...)) overload —
        // a separate `className =` assignment and the css prop clobber each other.
        DragIndicatorIcon {
            css(ClassName("drag-handle")) {
                cursor = Cursor.grab
                color = Color("#999")
                flexShrink = number(0.0)
                opacity = number(0.0)
                transition = "opacity 0.1s".unsafeCast<Transition>()
            }
            applyListeners(sortable.listeners)
        }

        // Type badge
        span {
            css {
                fontSize = 10.px
                fontWeight = integer(600)
                color = NamedColor.white
                backgroundColor = flowItemBadgeColor(item)
                padding = Padding(1.px, 6.px)
                borderRadius = 3.px
                flexShrink = number(0.0)
                minWidth = 52.px
                textAlign = TextAlign.center
            }
            +flowItemBadgeText(item)
        }

        // Content
        div {
            css {
                flexGrow = number(1.0)
                minWidth = 0.px
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                whiteSpace = WhiteSpace.nowrap
                if (item is SlideItem) {
                    // The thumbnail makes slide rows taller; flex-center the
                    // label against it (inline flow would baseline-align it
                    // near the image bottom, unlike the vcentered type chip).
                    display = Display.flex
                    alignItems = AlignItems.center
                }
            }
            when (item) {
                is SlideItem -> {
                    val label = props.slideLabels.getOrElse(item.slideIndex) { "Slide ${item.slideIndex + 1}" }
                    // Inline editable label
                    input {
                        asDynamic().type = "text"
                        value = label
                        css {
                            border = None.none
                            backgroundColor = NamedColor.transparent
                            fontFamily = sansSerif
                            fontSize = 13.px
                            padding = 0.px
                            width = Auto.auto
                            minWidth = 80.px
                        }
                        onChange = { e ->
                            props.onUpdateSlideLabel(item.slideIndex, e.target.value)
                        }
                    }
                    // Slide thumbnail with hover preview
                    if (props.presentation.numSlides > 0) {
                        val thumbRef = useRef<web.html.HTMLElement>(null)
                        var previewPos by useState<Pair<Double, Double>?>(null)
                        img {
                            ref = thumbRef
                            src = props.presentation.thumbUrl(item.slideIndex)
                            css {
                                height = 40.px
                                borderRadius = 2.px
                                marginLeft = 8.px
                                border = Border(1.px, LineStyle.solid, Color("#ddd"))
                                cursor = Cursor.pointer
                            }
                            onMouseEnter = {
                                thumbRef.current?.getBoundingClientRect()?.let { rect ->
                                    previewPos = Pair(rect.left, rect.top)
                                }
                            }
                            onMouseLeave = { previewPos = null }
                        }
                        if (previewPos != null) {
                            img {
                                src = props.presentation.thumbUrl(item.slideIndex)
                                css {
                                    position = Position.fixed
                                    left = previewPos!!.first.px
                                    top = previewPos!!.second.px
                                    transform = translatey((-100).pct)
                                    width = 360.px
                                    borderRadius = 4.px
                                    border = Border(2.px, LineStyle.solid, Color("#ccc"))
                                    boxShadow = BoxShadow(0.px, 4.px, 16.px, rgb(0, 0, 0, 0.2))
                                    backgroundColor = NamedColor.white
                                    zIndex = integer(1000)
                                    pointerEvents = None.none
                                    padding = 4.px
                                }
                            }
                        }
                    }
                }
                is ActionItem -> {
                    ButtonUnstyled {
                        css {
                            display = Display.inlineFlex
                            alignItems = AlignItems.center
                            gap = 4.px
                            cursor = Cursor.pointer
                            color = Color("#333")
                            "&:hover" { color = flowItemBadgeColor(item) }
                        }
                        FlowActionDescription { action = item.action }
                        SettingsIcon {
                            css { height = 14.px; width = 14.px }
                        }
                        onClick = { props.onEditAction?.invoke(item) }
                    }
                }
                is NoteItem -> {
                    input {
                        type = InputType.text
                        value = item.text
                        placeholder = "Enter note text..."
                        css {
                            border = None.none
                            backgroundColor = NamedColor.transparent
                            fontFamily = sansSerif
                            fontSize = 13.px
                            padding = 0.px
                            width = 100.pct
                        }
                        onChange = { e ->
                            props.onUpdate(item.copy(text = e.target.value))
                        }
                    }
                }
                is ForEachItem -> {
                    // Title + a short body preview; the whole block drills into the sub-editor.
                    ButtonUnstyled {
                        css {
                            display = Display.block
                            textAlign = TextAlign.left
                            width = 100.pct
                            cursor = Cursor.pointer
                            "&:hover .foreach-title" { color = flowItemBadgeColor(item) }
                        }
                        div {
                            css(ClassName("foreach-title")) {
                                fontWeight = integer(600)
                            }
                            +"Repeat over ${item.describeSelection()}"
                        }
                        item.body.take(3).forEach { b ->
                            div {
                                css {
                                    fontSize = 12.px
                                    color = Color("#777777")
                                    marginTop = 2.px
                                    overflow = Overflow.hidden
                                    textOverflow = TextOverflow.ellipsis
                                    whiteSpace = WhiteSpace.nowrap
                                }
                                +"· ${b.shortDescribe(props.presentation)}"
                            }
                        }
                        if (item.body.size > 3) div {
                            css { fontSize = 12.px; color = Color("#999999"); marginTop = 2.px }
                            +"+ ${item.body.size - 3} more steps"
                        }
                        if (item.body.isEmpty()) div {
                            css { fontSize = 12.px; color = Color("#b26a00"); marginTop = 2.px }
                            +"No steps yet — click to add"
                        }
                        onClick = { props.onOpenLoop?.invoke(item) }
                    }
                }
            }
        }

        // Item not runnable as configured: amber badge, click opens the config dialog
        props.configProblem?.let { problem ->
            ButtonUnstyled {
                css {
                    fontSize = 11.px
                    fontFamily = sansSerif
                    padding = Padding(2.px, 6.px)
                    borderRadius = 3.px
                    backgroundColor = Color("#fff3cd")
                    color = Color("#b26a00")
                    cursor = Cursor.pointer
                    flexShrink = number(0.0)
                }
                +"⚠ $problem"
                onClick = {
                    when (item) {
                        is ActionItem -> props.onEditAction?.invoke(item)
                        is ForEachItem -> props.onOpenLoop?.invoke(item)
                        else -> {}
                    }
                }
            }
        }

        // Warning toggle for NoteItem
        if (item is NoteItem) {
            ButtonUnstyled {
                css {
                    fontSize = 11.px
                    padding = Padding(2.px, 6.px)
                    borderRadius = 3.px
                    backgroundColor = if (item.warning) Color("#c62828") else Color("#e0e0e0")
                    color = if (item.warning) NamedColor.white else Color("#666")
                    cursor = Cursor.pointer
                }
                +(if (item.warning) "⚠ Warning" else "Note")
                onClick = { props.onUpdate(item.copy(warning = !item.warning)) }
            }
        }

        // Delete button
        ButtonUnstyled {
            css {
                padding = Padding(2.px, 6.px)
                cursor = Cursor.pointer
                color = Color("#999")
                "&:hover" { color = Color("#c62828") }
            }
            CloseIcon { size = 12 }
            onClick = { props.onRemove() }
        }
    }
}
