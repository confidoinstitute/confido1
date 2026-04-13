package components.redesign.presentation

import Client
import components.AppStateContext
import components.redesign.*
import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.rooms.RoomContext
import dndkit.applyListeners
import dndkit.core.*
import dndkit.modifiers.restrictToVerticalAxis
import dndkit.modifiers.restrictToWindowEdges
import dndkit.sortable.*
import dndkit.utilities.closestCenter
import emotion.react.css
import hooks.useCoroutineLock
import io.ktor.client.request.forms.*
import io.ktor.http.*
import js.objects.unsafeJso as jso
import kotlinx.coroutines.await
import payloads.requests.EditPresentation
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import tools.confido.state.*
import tools.confido.utils.generateId
import web.cssom.*
import web.html.InputType
import web.html.file
import web.html.number

import web.html.text

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

val PresentationEditor = FC<PresentationEditorProps> { props ->
    val (appState, stale) = use(AppStateContext)!!
    val room = use(RoomContext)!!
    val submitLock = useCoroutineLock()
    val pres = props.data.presentation

    // Local mutable copy of items for editing + DnD
    var items by useState(pres.items)
    var slideLabels by useState(pres.slideLabels)
    var dirty by useState(false)
    var uploading by useState(false)

    // Action config dialog state
    var actionDialogOpen by useState(false)
    var editingActionItemId by useState<String?>(null) // null = inserting new, non-null = editing existing
    var actionDialogInsertAfter by useState(-1) // insertion position for new actions
    var actionDialogInitial by useState<FlowAction?>(null) // initial action for editing

    // Presenter view config dialog state
    var pvDialogOpen by useState(false)
    var editingPVItemId by useState<String?>(null)
    var pvDialogInsertAfter by useState(-1)
    var pvDialogInitial by useState<PresenterView?>(null)

    // Sync from server when presentation changes (e.g. after slide upload)
    useEffect(pres.items, pres.slideLabels) {
        if (!dirty) {
            items = pres.items
            slideLabels = pres.slideLabels
        }
    }

    val mouseSensor = useSensor<MouseSensorOptions>(MouseSensor)
    val sensors = useSensors(mouseSensor)

    val itemIds = items.map { it.id }.toTypedArray()

    fun save() {
        submitLock {
            Client.sendData("${room.urlPrefix}/presentation/edit",
                EditPresentation(items = items, slideLabels = slideLabels),
                onError = { showError(it) }) {
                dirty = false
            }
        }
    }

    fun addSlideItems() {
        val newItems = items.toMutableList()
        for (i in 0 until pres.numSlides) {
            // Don't add duplicates
            if (newItems.none { it is SlideItem && it.slideIndex == i }) {
                newItems.add(SlideItem(id = generateId(), slideIndex = i))
            }
        }
        items = newItems
        dirty = true
    }

    fun insertItem(item: FlowItem, afterIndex: Int = items.lastIndex) {
        val newItems = items.toMutableList()
        newItems.add(afterIndex + 1, item)
        items = newItems
        dirty = true
    }

    fun removeItem(id: String) {
        items = items.filter { it.id != id }
        dirty = true
    }

    fun updateItem(id: String, updater: (FlowItem) -> FlowItem) {
        items = items.map { if (it.id == id) updater(it) else it }
        dirty = true
    }

    // Action configuration dialog
    FlowActionConfigDialog {
        open = actionDialogOpen
        action = actionDialogInitial
        onClose = { actionDialogOpen = false; editingActionItemId = null }
        onConfirm = { configuredAction ->
            val existingId = editingActionItemId
            if (existingId != null) {
                // Editing existing action item
                items = items.map { item ->
                    if (item.id == existingId && item is ActionItem)
                        item.copy(action = configuredAction)
                    else item
                }
            } else {
                // Inserting new action item
                val newItem = ActionItem(id = generateId(), action = configuredAction)
                val newItems = items.toMutableList()
                newItems.add(actionDialogInsertAfter + 1, newItem)
                items = newItems
            }
            dirty = true
            editingActionItemId = null
        }
    }

    // Presenter view configuration dialog
    PresenterViewConfigDialog {
        open = pvDialogOpen
        view = pvDialogInitial
        onClose = { pvDialogOpen = false; editingPVItemId = null }
        onConfirm = { configuredView ->
            val existingId = editingPVItemId
            if (existingId != null) {
                // Editing existing presenter view item
                items = items.map { item ->
                    if (item.id == existingId && item is PresenterViewItem)
                        item.copy(view = configuredView)
                    else item
                }
            } else {
                // Inserting new presenter view item
                val newItem = PresenterViewItem(id = generateId(), view = configuredView)
                val newItems = items.toMutableList()
                newItems.add(pvDialogInsertAfter + 1, newItem)
                items = newItems
            }
            dirty = true
            editingPVItemId = null
        }
    }

    Stack {
        css {
            padding = 15.px
            gap = 10.px
        }

        // Upload slides bar
        Stack {
            direction = FlexDirection.row
            css {
                gap = 10.px
                alignItems = AlignItems.center
                flexWrap = FlexWrap.wrap
            }

            // PDF upload
            div {
                css {
                    position = Position.relative
                    display = Display.inlineBlock
                }
                Button {
                    css {
                        margin = 0.px
                        padding = Padding(6.px, 16.px)
                        fontSize = 13.px
                    }
                    disabled = uploading
                    +(if (pres.numSlides > 0) "Re-upload slides (PDF)" else "Upload slides (PDF)")
                }
                input {
                    type = InputType.file
                    accept = ".pdf"
                    css {
                        position = Position.absolute
                        top = 0.px
                        left = 0.px
                        width = 100.pct
                        height = 100.pct
                        opacity = number(0.0)
                        cursor = Cursor.pointer
                    }
                    disabled = uploading
                    onChange = onChange@{ event ->
                        val target = event.target.asDynamic()
                        val files = target.files ?: return@onChange
                        val file = files[0] ?: return@onChange

                        submitLock {
                            uploading = true
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
                                if (!resp.status.isSuccess()) {
                                    showError("Upload failed")
                                }
                            } catch (e: Exception) {
                                showError(e.message ?: "Upload failed")
                            } finally {
                                uploading = false
                                // Reset dirty so we pick up new slides
                                dirty = false
                            }
                        }
                    }
                }
            }

            if (pres.numSlides > 0) {
                Button {
                    css {
                        margin = 0.px
                        padding = Padding(6.px, 16.px)
                        fontSize = 13.px
                        backgroundColor = Color("#E8E0F5")
                        color = Color("#6319FF")
                    }
                    +"Auto-populate slides"
                    onClick = { addSlideItems() }
                }
            }

            div { css { flexGrow = number(1.0) } }

            if (dirty) {
                Button {
                    css {
                        margin = 0.px
                        padding = Padding(6.px, 16.px)
                        fontSize = 13.px
                        backgroundColor = Color("#4caf50")
                    }
                    +"Save"
                    onClick = { save() }
                }
                span {
                    css {
                        fontSize = 12.px
                        color = Color("#e65100")
                        fontFamily = sansSerif
                    }
                    +"Unsaved changes"
                }
            }
        }

        // Insert item buttons (top)
        InsertItemBar {
            numSlides = pres.numSlides
            onInsert = { item -> insertItem(item, -1) }
            onInsertAction = {
                actionDialogInsertAfter = -1
                editingActionItemId = null
                actionDialogInitial = null
                actionDialogOpen = true
            }
            onInsertPresenterView = {
                pvDialogInsertAfter = -1
                editingPVItemId = null
                pvDialogInitial = null
                pvDialogOpen = true
            }
        }

        // Sortable flow item list
        DndContext {
            collisionDetection = closestCenter
            modifiers = arrayOf(restrictToVerticalAxis, restrictToWindowEdges)
            this.sensors = sensors
            onDragEnd = { event ->
                val over = event.over
                if (over != null && event.active.id != over.id) {
                    val oldIndex = items.indexOfFirst { it.id == event.active.id }
                    val newIndex = items.indexOfFirst { it.id == over.id }
                    if (oldIndex >= 0 && newIndex >= 0) {
                        items = arrayMove(items.toTypedArray(), oldIndex, newIndex).toList()
                        dirty = true
                    }
                }
            }

            SortableContext {
                this.items = itemIds
                strategy = verticalListSortingStrategy

                items.forEachIndexed { index, item ->
                    FlowItemRow {
                        key = item.id
                        this.item = item
                        this.presentation = pres
                        this.slideLabels = slideLabels
                        this.onRemove = { removeItem(item.id) }
                        this.onUpdate = { updated -> updateItem(item.id) { updated } }
                        this.onEditAction = { actionItem ->
                            editingActionItemId = actionItem.id
                            actionDialogInitial = actionItem.action
                            actionDialogOpen = true
                        }
                        this.onEditPresenterView = { pvItem ->
                            editingPVItemId = pvItem.id
                            pvDialogInitial = pvItem.view
                            pvDialogOpen = true
                        }
                        this.onUpdateSlideLabel = { slideIdx, label ->
                            val newLabels = slideLabels.toMutableList()
                            while (newLabels.size <= slideIdx) newLabels.add("Slide ${newLabels.size + 1}")
                            newLabels[slideIdx] = label
                            slideLabels = newLabels
                            dirty = true
                        }
                    }

                    // Insert bar after each item
                    InsertItemBar {
                        key = "insert-after-${item.id}"
                        numSlides = pres.numSlides
                        onInsert = { newItem -> insertItem(newItem, index) }
                        onInsertAction = {
                            actionDialogInsertAfter = index
                            editingActionItemId = null
                            actionDialogInitial = null
                            actionDialogOpen = true
                        }
                        onInsertPresenterView = {
                            pvDialogInsertAfter = index
                            editingPVItemId = null
                            pvDialogInitial = null
                            pvDialogOpen = true
                        }
                    }
                }
            }
        }
    }
}

// A compact row for inserting new items
external interface InsertItemBarProps : Props {
    var numSlides: Int
    var onInsert: (FlowItem) -> Unit
    /** Called when user wants to insert an action (opens config dialog). */
    var onInsertAction: (() -> Unit)?
    /** Called when user wants to insert a presenter view (opens config dialog). */
    var onInsertPresenterView: (() -> Unit)?
}

val InsertItemBar = FC<InsertItemBarProps> { props ->
    var menuOpen by useState(false)

    Stack {
        direction = FlexDirection.row
        css {
            justifyContent = JustifyContent.center
            padding = Padding(2.px, 0.px)
            gap = 5.px
        }
        ButtonUnstyled {
            css {
                fontSize = 11.px
                color = Color("#aaa")
                fontFamily = sansSerif
                padding = Padding(2.px, 8.px)
                borderRadius = 3.px
                border = Border(1.px, LineStyle.dashed, Color("#ddd"))
                cursor = Cursor.pointer
                "&:hover" {
                    borderColor = Color("#999")
                    color = Color("#666")
                }
            }
            +"+ Add item"
            onClick = { menuOpen = !menuOpen }
        }
    }

    if (menuOpen) {
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.center
                gap = 5.px
                padding = Padding(4.px, 0.px)
            }
            fun addButton(label: String, onClick: () -> Unit) {
                ButtonUnstyled {
                    css {
                        fontSize = 12.px
                        fontFamily = sansSerif
                        padding = Padding(4.px, 10.px)
                        borderRadius = 4.px
                        backgroundColor = Color("#f0f0f0")
                        cursor = Cursor.pointer
                        "&:hover" { backgroundColor = Color("#e0e0e0") }
                    }
                    +label
                    this.onClick = {
                        onClick()
                        menuOpen = false
                    }
                }
            }
            if (props.numSlides > 0) {
                addButton("Slide") {
                    props.onInsert(SlideItem(id = generateId(), slideIndex = 0))
                }
            }
            addButton("Note") {
                props.onInsert(NoteItem(id = generateId(), text = "", warning = false))
            }
            addButton("Action") {
                props.onInsertAction?.invoke()
            }
            addButton("Presenter View") {
                props.onInsertPresenterView?.invoke()
            }
        }
    }
}

// A single row in the flow item list (sortable)
external interface FlowItemRowProps : Props {
    var item: FlowItem
    var presentation: Presentation
    var slideLabels: List<String>
    var onRemove: () -> Unit
    var onUpdate: (FlowItem) -> Unit
    var onEditAction: ((ActionItem) -> Unit)?
    var onEditPresenterView: ((PresenterViewItem) -> Unit)?
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
            padding = Padding(6.px, 10.px)
            borderRadius = 4.px
            backgroundColor = when (item) {
                is SlideItem -> Color("#f5f5f5")
                is ActionItem -> Color("#fff3e0")
                is NoteItem -> if (item.warning) Color("#ffebee") else Color("#e8f5e9")
                is PresenterViewItem -> Color("#e3f2fd")
            }
            border = Border(1.px, LineStyle.solid, Color("#e0e0e0"))
            fontFamily = sansSerif
            fontSize = 13.px
            sortable.transform?.let { transform ->
                asDynamic().style = jso<dynamic> {
                    this.transform = "translate3d(${transform.x}px, ${transform.y}px, 0)"
                }
            }
        }

        // Drag handle
        DragIndicatorIcon {
            css {
                cursor = Cursor.grab
                color = Color("#999")
                flexShrink = number(0.0)
            }
            applyListeners(sortable.listeners)
        }

        // Type badge
        span {
            css {
                fontSize = 10.px
                fontWeight = integer(600)
                color = NamedColor.white
                backgroundColor = when (item) {
                    is SlideItem -> Color("#757575")
                    is ActionItem -> Color("#e65100")
                    is NoteItem -> if (item.warning) Color("#c62828") else Color("#2e7d32")
                    is PresenterViewItem -> Color("#1565c0")
                }
                padding = Padding(1.px, 6.px)
                borderRadius = 3.px
                flexShrink = number(0.0)
            }
            +when (item) {
                is SlideItem -> "SLIDE"
                is ActionItem -> "ACTION"
                is NoteItem -> if (item.warning) "WARNING" else "NOTE"
                is PresenterViewItem -> "VIEW"
            }
        }

        // Content
        div {
            css {
                flexGrow = number(1.0)
                minWidth = 0.px
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                whiteSpace = WhiteSpace.nowrap
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
                    // Tiny slide thumbnail
                    if (props.presentation.numSlides > 0) {
                        img {
                            src = props.presentation.slideUrl(item.slideIndex)
                            css {
                                height = 40.px
                                borderRadius = 2.px
                                marginLeft = 8.px
                                border = Border(1.px, LineStyle.solid, Color("#ddd"))
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
                            "&:hover" { color = Color("#e65100") }
                        }
                        +item.action.describe()
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
                is PresenterViewItem -> {
                    ButtonUnstyled {
                        css {
                            display = Display.inlineFlex
                            alignItems = AlignItems.center
                            gap = 4.px
                            cursor = Cursor.pointer
                            color = Color("#333")
                            "&:hover" { color = Color("#1565c0") }
                        }
                        +item.view.describe()
                        SettingsIcon {
                            css { height = 14.px; width = 14.px }
                        }
                        onClick = { props.onEditPresenterView?.invoke(item) }
                    }
                }
            }
        }

        // Slide index selector for SlideItem
        if (item is SlideItem && props.presentation.numSlides > 1) {
            span {
                css { fontSize = 11.px; color = Color("#888") }
                +"#"
            }
            input {
                type = InputType.number
                value = (item.slideIndex + 1).toString()
                min = 1.0
                max = props.presentation.numSlides.toDouble()
                css {
                    width = 45.px
                    fontSize = 12.px
                    padding = Padding(2.px, 4.px)
                    border = Border(1.px, LineStyle.solid, Color("#ccc"))
                    borderRadius = 3.px
                }
                onChange = { e ->
                    val newIdx = (e.target.value.toIntOrNull() ?: 1) - 1
                    if (newIdx in 0 until props.presentation.numSlides) {
                        props.onUpdate(item.copy(slideIndex = newIdx))
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
