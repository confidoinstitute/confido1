package tools.confido.application.routes

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
import payloads.requests.*
import rooms.RoomPermission
import tools.confido.application.sessions.TransientData
import tools.confido.application.sessions.modifyUserSession
import tools.confido.application.sessions.userSession
import tools.confido.extensions.ServerExtension
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.*
import java.io.File
import java.io.StringWriter
import javax.imageio.ImageIO

val presentationDir = File(System.getenv("CONFIDO_PRESENTATION_PATH") ?: "presentations").canonicalFile

/** Resolve a room's presentation from the in-memory manager. */
private suspend fun getPresentation(room: rooms.Room): Presentation? {
    val ref = room.presentation ?: return null
    return serverState.presentationManager.get(ref.id)
}

fun presentationRoutes(routing: Routing) = routing.apply {
    // Create a new presentation for a room
    postST("$roomUrl/presentation/create") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot manage presentations.")
            if (room.presentation != null) badRequest("Room already has a presentation.")

            val payload = call.receive<CreatePresentation>()
            val pres = serverState.presentationManager.insertEntity(
                Presentation(room = room.ref, name = payload.name)
            )
            serverState.roomManager.modifyEntity(room.ref) {
                it.copy(presentation = pres.ref)
            }
            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK, pres.id)
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

            // Create output directory
            val slideDir = File(presentationDir, "${pres.id}/slides")
            slideDir.mkdirs()

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
                        ImageIO.write(image, "PNG", File(slideDir, filename))
                    }

                    // Heuristically extract slide titles from PDF text
                    slideLabels = (0 until numSlides).map { i ->
                        val title = extractSlideTitle(pdf, i)
                        if (title != null) "Slide ${i + 1}: $title" else "Slide ${i + 1}"
                    }
                }
            } catch (e: Exception) {
                badRequest("Failed to process PDF: ${e.message}")
            }

            // Update presentation entity
            serverState.presentationManager.modifyEntity(pres.id) { p ->
                p.copy(numSlides = numSlides, slideLabels = slideLabels)
            }

            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK, numSlides.toString())
        }
    }

    // Edit presentation (items, labels, name)
    postST("$roomUrl/presentation/edit") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot manage presentations.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")
            val edit = call.receive<EditPresentation>()

            serverState.presentationManager.modifyEntity(pres.id) { p ->
                var updated = p
                edit.name?.let { updated = updated.copy(name = it) }
                edit.items?.let { updated = updated.copy(items = it) }
                edit.slideLabels?.let { updated = updated.copy(slideLabels = it) }
                updated
            }

            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }

    // Delete presentation
    deleteST("$roomUrl/presentation") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot manage presentations.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")

            // Stop run if active
            if (serverState.presentationRunStates.remove(room.ref) != null) {
                ServerExtension.forEach { it.onPresentationStopped(room) }
            }

            // Remove reference from room
            serverState.roomManager.modifyEntity(room.ref) {
                it.copy(presentation = null)
            }

            // Delete entity
            serverState.presentationManager.deleteEntity(pres.id)

            // Delete files from disk
            val presDir = File(presentationDir, pres.id)
            if (presDir.exists()) presDir.deleteRecursively()

            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }

    // Serve slide images (with file token for access protection)
    getST("/presentations/{presId}/{fileToken}/slides/{filename}") {
        val presId = call.parameters["presId"] ?: badRequest("Missing presentation ID.")
        val token = call.parameters["fileToken"] ?: badRequest("Missing file token.")
        val filename = call.parameters["filename"] ?: badRequest("Missing filename.")

        // Validate token
        val pres = serverState.presentationManager.get(presId) ?: notFound("Presentation not found.")
        if (pres.fileToken != token) unauthorized("Invalid file token.")

        // Sanitize filename to prevent path traversal
        if (!filename.matches(Regex("^\\d{4}\\.png$"))) badRequest("Invalid filename.")

        val file = File(presentationDir, "$presId/slides/$filename")
        if (!file.exists()) notFound("Slide not found.")
        call.respondFile(file)
    }

    // Start presentation run
    postST("$roomUrl/presentation/start") {
        call.withRoom {
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

            serverState.presentationRunStates[room.ref] = runState
            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }

    // Stop presentation run
    postST("$roomUrl/presentation/stop") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot control presentation.")
            serverState.presentationRunStates.remove(room.ref)
            ServerExtension.forEach { it.onPresentationStopped(room) }
            // Clear followingPresentation for all sessions following this room
            serverState.userSessionManager.entityMap.values
                .filter { it.followingPresentation?.id == room.id }
                .forEach { session ->
                    serverState.userSessionManager.modifyEntity(session.id) {
                        it.copy(followingPresentation = null)
                    }
                }
            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }

    // Navigate within a running presentation
    postST("$roomUrl/presentation/navigate") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot control presentation.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")
            val nav = call.receive<NavigatePresentation>()
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

            serverState.presentationRunStates[room.ref] = newState
            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }

    // Re-execute the current action (even if already executed)
    postST("$roomUrl/presentation/reexecute") {
        call.withRoom {
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

            serverState.presentationRunStates[room.ref] = newState
            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }

    // WebSocket endpoint for live presentation data (moderators only)
    getWS("/state$roomUrl/presentation") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot view presentation.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")
            val runState = serverState.presentationRunStates[room.ref]
            PresentationWSData(presentation = pres, runState = runState)
        }
    }

    // Follow a room's presentation (set session flag)
    postST("$roomUrl/presentation/follow") {
        call.withRoom {
            // Any room member can follow
            call.modifyUserSession { it.copy(followingPresentation = room.ref) }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    // Stop following a room's presentation (clear session flag)
    postST("$roomUrl/presentation/unfollow") {
        call.withRoom {
            call.modifyUserSession { it.copy(followingPresentation = null) }
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }

    // WebSocket endpoint for presentation followers: sends currentNavigateUrl
    getWS<String?>("/state$roomUrl/presentation/follow") {
        val session = call.userSession
        call.withRoom {
            // Only send navigate URL if session is following this room's presentation
            if (session?.followingPresentation?.id != room.id) return@withRoom null
            val runState = serverState.presentationRunStates[room.ref]
            runState?.currentNavigateUrl
        }
    }
}

/**
 * Expand presentation items by asking extensions to expand any expandable
 * (meta) actions. Non-expandable items pass through unchanged.
 */
private fun expandPresentationItems(items: List<FlowItem>, room: rooms.Room): List<FlowItem> =
    buildList {
        for (item in items) {
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
 * presenter view and execute actions if needed.
 */
private suspend fun processCurrentItem(
    pres: Presentation,
    state: PresentationRunState,
    room: rooms.Room,
): PresentationRunState {
    val item = state.expandedItems.getOrNull(state.currentIndex) ?: return state
    // Reset lastExecutedId - will be set if an action is executed this step
    var newState = state.copy(lastExecutedId = null)

    when (item) {
        is SlideItem -> {
            newState = newState.copy(
                currentView = SlidePV(pres.id, item.slideIndex, pres.fileToken)
            )
        }
        is PresenterViewItem -> {
            newState = newState.copy(currentView = item.view)
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
            // Always update presenter view if the action declares one (idempotent)
            val pv = item.action.presenterView
            if (pv != null) {
                newState = newState.copy(currentView = pv)
            }
            // Always update navigate URL if the action declares one (idempotent)
            val navUrl = item.action.getNavigateUrl(room)
            if (navUrl != null) {
                newState = newState.copy(currentNavigateUrl = navUrl)
            }
        }
        is NoteItem -> {
            // Notes don't change currentView or execute anything
        }
    }

    return newState
}

/**
 * Execute a flow action server-side.
 */
private suspend fun executeFlowAction(action: FlowAction, room: rooms.Room) {
    when (action) {
        is SetQuestionStateAction -> {
            serverState.questionManager.modifyEntity(action.question.id) {
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
                serverState.questionManager.modifyEntity(q.id) {
                    var modified = it.withState(action.newState)
                    action.setGroupPredVisibility?.let { vis -> modified = vis.apply(modified) }
                    modified
                }
            }
            // Hide all non-matching questions if requested
            if (action.hideOtherQuestions) {
                roomQuestions.filter { action.tag !in it.tags }.forEach { q ->
                    serverState.questionManager.modifyEntity(q.id) {
                        it.withState(tools.confido.question.QuestionState.DRAFT)
                    }
                }
            }
        }
        is SetPresenterViewAction -> {
            // When used in a PresenterViewItem, the view is set by processCurrentItem.
            // When used inside a CompositeAction, this is a no-op at the action level;
            // the view override would need to be handled at a higher level.
        }
        is NavigateToQuestionAction, is NavigateViewersAction -> {
            // Navigation is handled by processCurrentItem via getNavigateUrl().
            // No server-side side effects needed.
        }
        is CompositeAction -> {
            action.actions.forEach { executeFlowAction(it, room) }
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
