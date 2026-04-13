package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.utils.io.toByteArray
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import payloads.requests.*
import rooms.RoomPermission
import tools.confido.application.sessions.TransientData
import tools.confido.extensions.ServerExtension
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.*
import java.io.File
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

            // Convert PDF pages to PNG images
            var numSlides = 0
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
                }
            } catch (e: Exception) {
                badRequest("Failed to process PDF: ${e.message}")
            }

            // Update presentation entity
            val defaultLabels = (1..numSlides).map { "Slide $it" }
            serverState.presentationManager.modifyEntity(pres.id) { p ->
                p.copy(numSlides = numSlides, slideLabels = defaultLabels)
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

    // WebSocket endpoint for live presentation data
    getWS("/state$roomUrl/presentation") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_QUESTIONS, "Cannot view presentation.")
            val pres = getPresentation(room) ?: notFound("No presentation for this room.")
            val runState = serverState.presentationRunStates[room.ref]
            PresentationWSData(presentation = pres, runState = runState)
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
                it.withState(action.newState)
            }
        }
        is SetTaggedQuestionsStateAction -> {
            // Find all questions in this room that have the matching tag
            val roomQuestions = room.questions.mapNotNull { serverState.questions[it.id] }
            roomQuestions.filter { action.tag in it.tags }.forEach { q ->
                serverState.questionManager.modifyEntity(q.id) {
                    it.withState(action.newState)
                }
            }
        }
        is SetPresenterViewAction -> {
            // When used in a PresenterViewItem, the view is set by processCurrentItem.
            // When used inside a CompositeAction, this is a no-op at the action level;
            // the view override would need to be handled at a higher level.
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
