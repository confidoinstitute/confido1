package tools.confido.application.routes

import tools.confido.api.Api
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.utils.io.toByteArray
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import tools.confido.payloads.requests.*
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import java.security.MessageDigest
import tools.confido.application.generateToken
import tools.confido.utils.generateId
import tools.confido.application.sessions.TransientData
import tools.confido.application.sessions.modifyUserSession
import tools.confido.application.sessions.userSession
import tools.confido.extensions.ServerExtension
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.*
import tools.confido.store.*
import java.io.File
import java.io.StringWriter
import javax.imageio.ImageIO
import tools.confido.state.Topics
import tools.confido.state.ServerEntityLookup

val presentationDir = File(System.getenv("CONFIDO_PRESENTATION_PATH") ?: "presentations").canonicalFile

/** Resolve a room's presentation from the in-memory manager. */
private suspend fun getPresentation(room: tools.confido.rooms.Room): Presentation? {
    val ref = room.presentation ?: return null
    return presentationStore.get(ref.id)
}

fun presentationRoutes(routing: Routing) = routing.apply {
    // Create a new presentation for a room; responds with its id
    apiST(Api.presentationCreate) { rRef, payload ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot manage presentations.")
            if (room.presentation != null) badRequest("Room already has a presentation.")

            val pres = presentationStore.insert(
                Presentation(room = room.ref, name = payload.name, fileToken = generateToken())
            )
            tools.confido.store.roomStore.modify(room.ref) {
                it.copy(presentation = pres.ref)
            }
            pres.id
        }
    }

    // Upload slides (PDF) for a presentation
    postST("$roomUrl/presentation/upload-slides") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot manage presentations.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")

            val multipart = call.receiveMultipart()
            var fileBytes: ByteArray? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    fileBytes = part.provider().toByteArray()
                }
                part.dispose()
            }

            val bytes = fileBytes ?: badRequest("No file uploaded.")
            if (bytes.isEmpty()) badRequest("Uploaded file is empty.")

            // Render into a staging directory and swap it in only when complete:
            // overwriting the live slides in place would let a concurrently
            // fetched thumbnail observe a half-written PNG (and cache it broken).
            val slideDir = File(presentationDir, "${pres.id}/slides")
            val stagingDir = File(presentationDir, "${pres.id}/slides_staging")
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            // Save original PDF
            File(presentationDir, "${pres.id}/orig.pdf").writeBytes(bytes)

            // Convert PDF pages to PNG images and extract slide titles
            var numSlides = 0
            var slideLabels = emptyList<String>()
            try {
                val doc = Loader.loadPDF(bytes)
                doc.use { pdf ->
                    val renderer = PDFRenderer(pdf)
                    numSlides = pdf.numberOfPages
                    for (i in 0 until numSlides) {
                        val image = renderer.renderImageWithDPI(i, 150f)
                        val filename = (i + 1).toString().padStart(4, '0') + ".png"
                        ImageIO.write(image, "PNG", File(stagingDir, filename))
                    }

                    // Heuristically extract slide titles from PDF text
                    slideLabels = (0 until numSlides).map { i ->
                        val title = extractSlideTitle(pdf, i)
                        if (title != null) "Slide ${i + 1}: $title" else "Slide ${i + 1}"
                    }
                }
            } catch (e: Exception) {
                stagingDir.deleteRecursively()
                badRequest("Failed to process PDF: ${e.message}")
            }
            slideDir.deleteRecursively()
            if (!stagingDir.renameTo(slideDir)) badRequest("Failed to store slide images.")

            // Update presentation entity. A re-upload may shrink the slide count;
            // drop now-out-of-range slide items so stored flows stay valid. The
            // version bump changes slide URLs, forcing browsers to refetch
            // (file names are identical across a replace).
            presentationStore.modify(pres.id) { p ->
                p.copy(numSlides = numSlides, slideLabels = slideLabels,
                    slidesVersion = p.slidesVersion + 1,
                    items = p.items.filter { it !is SlideItem || it.slideIndex < numSlides })
            }

            call.respond(HttpStatusCode.OK, numSlides.toString())
        }
    }

    // Edit presentation (items, labels, name)
    apiST(Api.presentationEdit) { rRef, edit ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot manage presentations.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")

            val validatedItems = edit.items?.let { validateFlowItems(it, pres, room) }
            presentationStore.modify(pres.id) { p ->
                var updated = p
                edit.name?.let { updated = updated.copy(name = it) }
                validatedItems?.let { updated = updated.copy(items = it) }
                edit.slideLabels?.let { updated = updated.copy(slideLabels = it) }
                updated
            }
        }
    }

    // Delete presentation
    apiST(Api.presentationDelete) { rRef, _ ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot manage presentations.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")

            // Stop run if active
            if (serverState.setPresentationRunState(room.ref, null) != null) {
                ServerExtension.forEach { it.onPresentationStopped(room) }
            }

            // Remove reference from room
            tools.confido.store.roomStore.modify(room.ref) {
                it.copy(presentation = null)
            }

            // Delete entity
            presentationStore.delete(pres.id)

            // Delete files from disk
            val presDir = File(presentationDir, pres.id)
            if (presDir.exists()) presDir.deleteRecursively()
        }
    }

    // Serve slide images (with file token for access protection)
    getST("/presentations/{presId}/{fileToken}/slides/{filename}") {
        val presId = call.parameters["presId"] ?: badRequest("Missing presentation ID.")
        val token = call.parameters["fileToken"] ?: badRequest("Missing file token.")
        val filename = call.parameters["filename"] ?: badRequest("Missing filename.")

        // Validate token
        val pres = presentationStore.get(presId) ?: notFound("Presentation not found.")
        // Constant-time compare, and never let a blank stored token authorize anything.
        if (pres.fileToken.isEmpty() ||
            !MessageDigest.isEqual(pres.fileToken.toByteArray(), token.toByteArray())
        ) unauthorized("Invalid file token.")

        // Sanitize filename to prevent path traversal
        if (!filename.matches(Regex("^\\d{4}\\.png$"))) badRequest("Invalid filename.")

        val file = File(presentationDir, "$presId/slides/$filename")
        if (!file.exists()) notFound("Slide not found.")
        call.respondFile(file)
    }

    // Start presentation run
    apiST(Api.presentationStart) { rRef, _ ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot control presentation.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")
            if (pres.items.isEmpty()) badRequest("Presentation has no items.")

            // Expand meta-items into a flat list of concrete items
            val expandedItems = expandPresentationItems(pres.items, room)
            if (expandedItems.isEmpty()) badRequest("Presentation has no items after expansion.")

            // Initialize run state at first item
            var runState = PresentationRunState(currentIndex = 0, expandedItems = expandedItems)

            // Process the first item
            runState = processCurrentItem(pres, runState, room)

            serverState.setPresentationRunState(room.ref, runState)
        }
    }

    // Stop presentation run
    apiST(Api.presentationStop) { rRef, _ ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot control presentation.")
            serverState.setPresentationRunState(room.ref, null)
            ServerExtension.forEach { it.onPresentationStopped(room) }
            // Clear followingPresentation for all sessions following this room
            tools.confido.store.userSessionStore.asMap.values
                .filter { it.followingPresentation?.id == room.id }
                .forEach { session ->
                    tools.confido.store.userSessionStore.modify(session.id) {
                        it.copy(followingPresentation = null)
                    }
                }
        }
    }

    // Navigate within a running presentation
    apiST(Api.presentationNavigate) { rRef, nav ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot control presentation.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")
            val currentState = serverState.presentationRunStates[room.ref]
                ?: badRequest("Presentation is not running.")

            val lastIndex = currentState.expandedItems.lastIndex
            val newIndex = when (nav.direction) {
                NavigateDirection.NEXT -> (currentState.currentIndex + 1).coerceAtMost(lastIndex)
                NavigateDirection.PREV -> (currentState.currentIndex - 1).coerceAtLeast(0)
                NavigateDirection.GOTO -> (nav.index ?: badRequest("Missing index for GOTO."))
                    .coerceIn(0, lastIndex)
            }

            var newState = currentState.copy(currentIndex = newIndex)
            newState = processCurrentItem(pres, newState, room)

            serverState.setPresentationRunState(room.ref, newState)
        }
    }

    // Override the effective presenter view of a running presentation (e.g. tweak
    // options of the currently shown view without leaving the presentation).
    // Overrides are sticky per step: keyed by the current flow item's id, they are
    // re-applied when navigating back to that step and die with the run.
    apiST(Api.presentationSetView) { rRef, view ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot control presentation.")
            if (!view.isValid(ServerEntityLookup)) badRequest("View is not valid")
            val currentState = serverState.presentationRunStates[room.ref]
                ?: badRequest("Presentation is not running.")
            val itemId = currentState.expandedItems.getOrNull(currentState.currentIndex)?.id
            serverState.setPresentationRunState(room.ref, currentState.copy(
                currentView = view,
                viewOverrides = if (itemId == null) currentState.viewOverrides
                                else currentState.viewOverrides + (itemId to view),
            ))
        }
    }

    // Discard the current step's live view override and restore the scripted view.
    apiST(Api.presentationRevertView) { rRef, _ ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot control presentation.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")
            val currentState = serverState.presentationRunStates[room.ref]
                ?: badRequest("Presentation is not running.")
            val itemId = currentState.expandedItems.getOrNull(currentState.currentIndex)?.id
            if (itemId != null && itemId in currentState.viewOverrides) {
                var newState = currentState.copy(viewOverrides = currentState.viewOverrides - itemId)
                newState = processCurrentItem(pres, newState, room)
                serverState.setPresentationRunState(room.ref, newState)
            }
        }
    }

    // Re-execute the current action (even if already executed)
    apiST(Api.presentationReexecute) { rRef, _ ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot control presentation.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")
            val currentState = serverState.presentationRunStates[room.ref]
                ?: badRequest("Presentation is not running.")

            val item = currentState.expandedItems.getOrNull(currentState.currentIndex)
            if (item !is ActionItem) badRequest("Current item is not an action.")

            // Force re-execution by removing from executedActionIds, then reprocessing
            var newState = currentState.copy(
                executedActionIds = currentState.executedActionIds - item.id
            )
            newState = processCurrentItem(pres, newState, room)

            serverState.setPresentationRunState(room.ref, newState)
        }
    }

    // WebSocket endpoint for live presentation data (moderators only)
    viewWS(Topics.presentation) { rRef ->
        val viewerRef = call.userSession?.userRef ?: unauthorized("Not logged in.")
        val roomView = roomStore.state.asView().at(rRef.id)
        val presView = roomView.flatMap { room ->
            room?.presentation?.let { presentationStore.state.asView().at(it.id) } ?: viewOf(null)
        }
        combineViews(
            roomView,
            presView,
            userStore.state.asView().at(viewerRef.id),
            serverState.presentationRunStatesState.asView().at(rRef),
        ) { room, pres, viewer, runState ->
            if (viewer == null) unauthorized("Not logged in.")
            if (room == null) notFound("No such room.")
            if (!room.hasPermission(viewer, RoomPermission.MANAGE_QUESTIONS))
                forbidden("Cannot view presentation.")
            PresentationWSData(
                presentation = pres ?: notFound("No presentation for this room."),
                runState = runState,
            )
        }
    }

    // Follow a room's presentation (set session flag)
    apiST(Api.presentationFollow) { rRef, _ ->
        call.withRoom(rRef) {
            // Any room member can follow — but only members.
            if (room.accessibleMemberships(user).isEmpty()) unauthorized("Not a member of this room.")
            call.modifyUserSession { it.copy(followingPresentation = room.ref) }
        }
    }

    // Stop following a room's presentation (clear session flag)
    apiST(Api.presentationUnfollow) { rRef, _ ->
        call.withRoom(rRef) {
            call.modifyUserSession { it.copy(followingPresentation = null) }
        }
    }

    // WebSocket endpoint for presentation followers: sends currentNavigateUrl.
    // The per-viewer input here is the *session* (followingPresentation), which is
    // store-backed — so even this "personal" topic is an ordinary View composition.
    viewWS<Ref<Room>, String?>(Topics.presentationFollow) { rRef ->
        val sessionId = call.userSession?.id ?: unauthorized("Not logged in.")
        combineViews(
            roomStore.state.asView().at(rRef.id),
            userSessionStore.state.asView().at(sessionId),
            userStore.state.asView(),
            serverState.presentationRunStatesState.asView().at(rRef),
        ) { room, session, users, runState ->
            if (room == null) notFound("No such room.")
            // Membership gates the payload (reactively: a member removed or revoked
            // mid-follow stops receiving navigate URLs too).
            val viewer = session?.userRef?.let { users[it.id] }
            if (viewer == null || room.accessibleMemberships(viewer).isEmpty()) null
            // Only send navigate URL if session is following this room's presentation
            else if (session.followingPresentation?.id != room.id) null
            else runState?.currentNavigateUrl
        }
    }
}

/**
 * Validate an edited flow item list against its room and presentation and return
 * it normalized (slide-order invariant applied — see [normalizeFlowItems]), or
 * throw a 400. Guarantees: unique item ids, all question refs (recursively, incl.
 * views) belong to the room, no under-configured actions, embedded views valid,
 * slide indices in range.
 */
private fun validateFlowItems(items: List<FlowItem>, pres: Presentation, room: Room): List<FlowItem> {
    val allIds = items.flatMap { listOf(it.id) + ((it as? ForEachItem)?.body?.map { b -> b.id } ?: emptyList()) }
    if (allIds.size != allIds.toSet().size)
        badRequest("Duplicate flow item ids.")
    val roomQuestions = room.questions.map { it.id }.toSet()

    fun validateAction(item: ActionItem, inLoop: Boolean) {
        if (!inLoop && CURRENT_QUESTION in item.action.questionRefs())
            badRequest("\"Current question\" can only be used inside a repeat loop.")
        item.action.configProblem()?.let {
            badRequest("Item \"${item.shortDescribe(pres)}\" is not fully configured: $it")
        }
        item.action.embeddedViews().forEach { view ->
            // A view referencing the loop sentinel cannot be resolved yet; per-question
            // validity is decided at expansion (inapplicable steps are skipped).
            if (CURRENT_QUESTION !in view.questionRefs() && !view.isValid(ServerEntityLookup))
                badRequest("Item \"${item.shortDescribe(pres)}\" contains an invalid presenter view.")
            val viewRoom = when (view) {
                is InviteLinkPV -> view.room
                is PresentationPV -> view.room
                else -> null
            }
            if (viewRoom != null && viewRoom.id != room.id)
                badRequest("Item \"${item.shortDescribe(pres)}\" references another room.")
        }
    }

    items.forEach { item ->
        item.questionRefs().forEach { ref ->
            if (ref.id !in roomQuestions)
                badRequest("Item \"${item.shortDescribe(pres)}\" references a question outside this room.")
        }
        when (item) {
            is ActionItem -> validateAction(item, inLoop = false)
            is SlideItem ->
                if (item.slideIndex !in 0 until pres.numSlides)
                    badRequest("Slide number out of range.")
            is ForEachItem -> item.body.forEach { b ->
                when (b) {
                    is SlideItem -> badRequest("Slides cannot be repeated in a loop.")
                    is ForEachItem -> badRequest("Loops cannot be nested.")
                    is ActionItem -> validateAction(b, inLoop = true)
                    is NoteItem -> {}
                }
            }
            else -> {}
        }
    }
    return normalizeFlowItems(items)
}

/**
 * Expand presentation items by asking extensions to expand any expandable
 * (meta) actions. Non-expandable items pass through unchanged.
 */
private fun expandPresentationItems(items: List<FlowItem>, room: tools.confido.rooms.Room): List<FlowItem> =
    buildList {
        for (item in items) {
            if (item is ForEachItem) {
                val candidates = room.questions.mapNotNull { serverState.questions[it.id] }
                    .filter { item.selects(it) }
                for (q in candidates) for (bodyItem in item.body) {
                    val step = bodyItem.substituteQuestion(q.ref).withId(generateId())
                    // Skip steps inapplicable to this question (e.g. a binary
                    // histogram on a numeric one) — a shorter iteration beats a
                    // blank projector mid-show.
                    if (step is ActionItem && !step.action.embeddedViews().all { it.isValid(ServerEntityLookup) })
                        continue
                    add(step)
                }
                continue
            }
            if (item is ActionItem) {
                val expanded = ServerExtension.enabled.firstNotNullOfOrNull {
                    it.expandFlowItem(item.action, room)
                }
                if (expanded != null) {
                    addAll(expanded)
                    continue
                }
            }
            add(item)
        }
    }

/**
 * Process the current item in the presentation flow: compute the effective
 * presenter view (the step's live override, if any, else its scripted view)
 * and execute actions if needed.
 */
private suspend fun processCurrentItem(
    pres: Presentation,
    state: PresentationRunState,
    room: tools.confido.rooms.Room,
): PresentationRunState {
    val item = state.expandedItems.getOrNull(state.currentIndex) ?: return state
    // Reset lastExecutedId - will be set if an action is executed this step
    var newState = state.copy(lastExecutedId = null)

    // The view this step defines by script, if any (NoteItem defines none and
    // keeps the previous view showing).
    var scriptedView: PresenterView? = null
    when (item) {
        is SlideItem -> {
            scriptedView = SlidePV(pres.id, item.slideIndex, pres.fileToken, pres.slidesVersion)
        }
        is ActionItem -> {
            // Only execute side effects if not already executed in this run
            if (item.id !in state.executedActionIds) {
                executeFlowAction(item.action, room)
                newState = newState.copy(
                    executedActionIds = newState.executedActionIds + item.id,
                    lastExecutedId = item.id,
                )
            }
            // The view (below) and navigate URL re-apply on every visit (idempotent),
            // which is what makes stepping backwards safe.
            scriptedView = item.action.presenterView
            val navUrl = item.action.getNavigateUrl(room, ServerEntityLookup)
            if (navUrl != null) {
                newState = newState.copy(currentNavigateUrl = navUrl)
            }
        }
        is NoteItem -> {}
        // Loops never survive expansion; defensive no-op.
        is ForEachItem -> {}
    }

    // Sticky per-step override wins over the scripted view (see presentationSetView).
    (state.viewOverrides[item.id] ?: scriptedView)?.let {
        newState = newState.copy(currentView = it)
    }

    return newState
}

private fun Room.ownsQuestion(ref: Ref<Question>) = questions.any { it.id == ref.id }

/** Defense in depth against flows touching foreign rooms' questions: authored
 *  flows are validated on save ([validateFlowItems]), but legacy or hand-crafted
 *  data may still violate the invariant — skip rather than blow up mid-run. */
private fun warnForeignQuestion(room: Room, ref: Ref<Question>) =
    println("Presentation flow in room ${room.id} references foreign question ${ref.id}; skipping action.")

/**
 * Execute a flow action server-side.
 */
private suspend fun executeFlowAction(action: FlowAction, room: tools.confido.rooms.Room) {
    when (action) {
        is SetQuestionStateAction -> {
            if (!room.ownsQuestion(action.question)) { warnForeignQuestion(room, action.question); return }
            tools.confido.store.questionStore.modify(action.question.id) {
                var q = it.withState(action.newState)
                action.setGroupPredVisibility?.let { vis -> q = vis.apply(q) }
                q
            }
        }
        is SetTaggedQuestionsStateAction -> {
            // Find all questions in this room
            val roomQuestions = room.questions.mapNotNull { serverState.questions[it.id] }
            // Set matching questions to the target state
            roomQuestions.filter { action.tag in it.tags }.forEach { q ->
                tools.confido.store.questionStore.modify(q.id) {
                    var modified = it.withState(action.newState)
                    action.setGroupPredVisibility?.let { vis -> modified = vis.apply(modified) }
                    modified
                }
            }
            // Hide all non-matching questions if requested
            if (action.hideOtherQuestions) {
                roomQuestions.filter { action.tag !in it.tags }.forEach { q ->
                    tools.confido.store.questionStore.modify(q.id) {
                        it.withState(tools.confido.question.QuestionState.DRAFT)
                    }
                }
            }
        }
        is SetPresenterViewAction -> {
            // No side effects: the view is applied by processCurrentItem via
            // FlowAction.presenterView (which CompositeAction propagates too).
        }
        is NavigateToQuestionAction, is NavigateViewersAction -> {
            // Navigation is handled by processCurrentItem via getNavigateUrl().
            // No server-side side effects needed.
        }
        is CompositeAction -> {
            action.actions.forEach { executeFlowAction(it, room) }
        }
        is CreateMarkAction -> {
            if (action.question != null && !room.ownsQuestion(action.question)) {
                warnForeignQuestion(room, action.question); return
            }
            // Mark "now"; overwrite-by-name so a re-run flow moves the mark.
            if (action.name.isNotBlank()) {
                val mark = tools.confido.rooms.TimelineMark(
                    id = tools.confido.utils.generateId(), ts = tools.confido.utils.unixNow(),
                    name = action.name, owner = null)
                if (action.question != null) {
                    tools.confido.store.questionStore.modify(action.question.id) { q ->
                        q.copy(timelineMarks = (q.timelineMarks.filter { it.name != action.name } + mark).sortedBy { it.ts })
                    }
                } else {
                    tools.confido.store.roomStore.modify(room.id) { r ->
                        r.copy(timelineMarks = (r.timelineMarks.filter { it.name != action.name } + mark).sortedBy { it.ts })
                    }
                }
            }
        }
        else -> {
            // Dispatch to extensions
            ServerExtension.enabled.any { it.executeFlowAction(action, room) }
        }
    }
}

// ---------------------------------------------------------------------------
// Heuristic slide title extraction from PDF text
// ---------------------------------------------------------------------------

/**
 * A text run collected from a single [PDFTextStripper.writeString] call,
 * representing a contiguous piece of text on the page.
 */
private data class TextRun(
    val text: String,
    val fontSize: Float,
    /** Y position in top-down coordinates (0 = top of page). */
    val yPosition: Float,
    val pageHeight: Float,
)

/**
 * Custom [PDFTextStripper] that collects text runs with their font size
 * and position rather than producing plain text output.
 */
private class SlideTextExtractor : PDFTextStripper() {
    val runs = mutableListOf<TextRun>()

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        if (textPositions.isEmpty()) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // Use the dominant (most common) font size across the run's characters
        val fontSize = textPositions
            .groupingBy { it.fontSizeInPt }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: return

        // Use the first character's adjusted Y position (top-down coordinate)
        val yPos = textPositions.first().yDirAdj
        val pageHeight = textPositions.first().pageHeight

        runs.add(TextRun(trimmed, fontSize, yPos, pageHeight))
    }
}

/**
 * A line of text assembled from one or more [TextRun]s that share
 * approximately the same Y position.
 */
private data class TextLine(
    val text: String,
    val fontSize: Float,
    val yPosition: Float,
    val pageHeight: Float,
)

/**
 * Group [TextRun]s into lines based on Y-position proximity and merge
 * their text. Runs within ~2pt vertically are considered the same line.
 */
private fun groupRunsIntoLines(runs: List<TextRun>): List<TextLine> {
    if (runs.isEmpty()) return emptyList()

    val sorted = runs.sortedWith(compareBy({ it.yPosition }, { it.text }))
    val lines = mutableListOf<TextLine>()
    var currentRuns = mutableListOf(sorted.first())

    for (run in sorted.drop(1)) {
        if (kotlin.math.abs(run.yPosition - currentRuns.first().yPosition) <= 2f) {
            currentRuns.add(run)
        } else {
            lines.add(mergeLine(currentRuns))
            currentRuns = mutableListOf(run)
        }
    }
    lines.add(mergeLine(currentRuns))
    return lines
}

/** Merge a group of runs on the same line into a single [TextLine]. */
private fun mergeLine(runs: List<TextRun>): TextLine {
    val text = runs.joinToString(" ") { it.text }
    // Use the maximum font size on the line (title text may be mixed with smaller elements)
    val fontSize = runs.maxOf { it.fontSize }
    val yPosition = runs.first().yPosition
    val pageHeight = runs.first().pageHeight
    return TextLine(text, fontSize, yPosition, pageHeight)
}

private val PAGE_NUMBER_PATTERN = Regex("^\\d{1,4}$")

/**
 * Score a [TextLine] as a potential slide title.
 *
 * Higher scores are better. The heuristic combines:
 * - Font size (60% weight): larger text is more likely a title
 * - Y position (40% weight): text nearer the top of the page is preferred
 */
private fun scoreLine(line: TextLine, maxFontSize: Float): Double {
    val fontSizeScore = if (maxFontSize > 0f) (line.fontSize / maxFontSize).toDouble() else 0.0
    val yPositionScore = if (line.pageHeight > 0f) {
        1.0 - (line.yPosition / line.pageHeight).toDouble().coerceIn(0.0, 1.0)
    } else 0.0
    return 0.6 * fontSizeScore + 0.4 * yPositionScore
}

private const val MAX_TITLE_LENGTH = 80

/**
 * Extract a heuristic slide title from a single page of a PDF document.
 *
 * @param pdf   The loaded PDF document.
 * @param pageIndex  Zero-based page index.
 * @return The extracted title, or `null` if no suitable text was found.
 */
private fun extractSlideTitle(pdf: PDDocument, pageIndex: Int): String? {
    val extractor = SlideTextExtractor().apply {
        startPage = pageIndex + 1  // PDFTextStripper uses 1-based page numbers
        endPage = pageIndex + 1
    }
    try {
        extractor.getText(pdf)
    } catch (_: Exception) {
        return null
    }

    val runs = extractor.runs
    if (runs.isEmpty()) return null

    val lines = groupRunsIntoLines(runs)
    if (lines.isEmpty()) return null

    val maxFontSize = lines.maxOf { it.fontSize }

    val best = lines
        // Filter out very short text and bare page numbers
        .filter { it.text.length >= 2 && !PAGE_NUMBER_PATTERN.matches(it.text.trim()) }
        .maxByOrNull { scoreLine(it, maxFontSize) }
        ?: return null

    // Clean up: collapse whitespace, trim
    val title = best.text.replace(Regex("\\s+"), " ").trim()
    if (title.length < 2) return null

    return if (title.length > MAX_TITLE_LENGTH) {
        title.take(MAX_TITLE_LENGTH - 1).trimEnd() + "…"
    } else {
        title
    }
}
