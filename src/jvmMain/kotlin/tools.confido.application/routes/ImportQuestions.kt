package tools.confido.application.routes

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray
import kotlinx.datetime.Clock
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.mozilla.universalchardet.UniversalDetector
import payloads.requests.*
import rooms.RoomPermission
import tools.confido.application.sessions.TransientData
import tools.confido.question.*
import tools.confido.refs.ref
import tools.confido.spaces.*
import tools.confido.state.*
import tools.confido.utils.mapDeref
import java.io.StringReader
import java.nio.charset.Charset

/** Column name aliases for auto-mapping (all lowercase, whitespace-normalized). */
private val COLUMN_ALIASES: Map<String, ColumnMapping> = buildMap {
    for ((mapping, aliases) in listOf(
        ColumnMapping.TITLE to listOf("question", "question text", "title", "name", "question name", "question title"),
        ColumnMapping.DESCRIPTION to listOf("description", "desc", "details", "notes", "resolution criteria"),
        ColumnMapping.RESOLUTION to listOf("resolution", "answer", "correct answer", "result", "resolved", "correct"),
        ColumnMapping.MIN to listOf("min", "minimum", "lower", "lower bound", "range min", "from"),
        ColumnMapping.MAX to listOf("max", "maximum", "upper", "upper bound", "range max", "to"),
        ColumnMapping.UNIT to listOf("unit", "units"),
        ColumnMapping.DECIMALS to listOf("decimals", "decimal places", "precision"),
        ColumnMapping.EXTERNAL_ID to listOf("id", "external id", "external_id", "key", "question id", "question_id"),
        ColumnMapping.TAGS to listOf("tags", "tag", "labels", "label", "categories", "category"),
    )) {
        for (alias in aliases) {
            put(alias, mapping)
        }
    }
}

/** Normalize a header cell for alias matching: trim, collapse whitespace, lowercase. */
private fun normalizeHeader(header: String): String =
    header.trim().replace(Regex("\\s+"), " ").lowercase()

/** Match a header cell to a ColumnMapping using the alias table. */
private fun matchColumnName(header: String): ColumnMapping {
    val normalized = normalizeHeader(header)
    return COLUMN_ALIASES[normalized] ?: ColumnMapping.SKIP
}

/**
 * Heuristic to guess whether the first row is a header row.
 * - If any cell matches a known column alias -> likely header
 * - If any cell has 5+ words or contains '?' -> likely data (question text)
 * - Default: true (header is the common case for spreadsheet exports)
 */
private fun guessHasHeader(firstRow: List<String>): Boolean {
    var hasAliasMatch = false
    var hasDataLikeCell = false

    for (cell in firstRow) {
        val normalized = normalizeHeader(cell)
        if (normalized in COLUMN_ALIASES) {
            hasAliasMatch = true
        }
        val wordCount = cell.trim().split(Regex("\\s+")).size
        if (wordCount >= 5 || '?' in cell) {
            hasDataLikeCell = true
        }
    }

    if (hasAliasMatch) return true
    if (hasDataLikeCell) return false
    return true
}

/**
 * Detect the encoding of raw bytes using juniversalchardet.
 * Falls back to UTF-8 if detection fails or returns ASCII.
 */
private fun detectEncoding(bytes: ByteArray): Charset {
    val detector = UniversalDetector()
    detector.handleData(bytes, 0, bytes.size)
    detector.dataEnd()
    val detected = detector.detectedCharset
    detector.reset()

    if (detected == null || detected.equals("US-ASCII", ignoreCase = true)) {
        return Charsets.UTF_8
    }
    return try {
        Charset.forName(detected)
    } catch (e: Exception) {
        Charsets.UTF_8
    }
}

/**
 * Auto-detect delimiter by trying comma and tab, picking the one
 * that gives more consistent (and > 1) column counts across rows.
 */
private fun detectDelimiter(content: String): Char {
    fun tryDelimiter(delim: Char): Pair<Int, Double> {
        val format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
            .setDelimiter(delim)
            .setIgnoreSurroundingSpaces(true)
            .build()
        return try {
            val parser = CSVParser.parse(StringReader(content), format)
            val records = parser.records.take(20)
            if (records.isEmpty()) return 0 to 0.0
            val columnCounts = records.map { it.size() }
            val avgCols = columnCounts.average()
            val consistency = if (columnCounts.all { it == columnCounts[0] }) 1.0 else 0.5
            columnCounts[0] to (avgCols * consistency)
        } catch (e: Exception) {
            0 to 0.0
        }
    }

    val (commaCols, commaScore) = tryDelimiter(',')
    val (tabCols, tabScore) = tryDelimiter('\t')

    if (tabCols >= 2 && tabScore >= commaScore) return '\t'
    if (commaCols >= 2) return ','
    return ','
}

/**
 * Parse raw bytes (CSV/TSV) into a [CsvParseResponse] with rows, header detection,
 * and suggested column mappings. Returns null with an error message if parsing fails.
 */
private fun parseCsvBytes(bytes: ByteArray): CsvParseResponse {
    // Detect encoding and decode
    val charset = detectEncoding(bytes)
    var content = bytes.toString(charset)
    // Strip BOM if present
    if (content.isNotEmpty() && content[0] == '\uFEFF') {
        content = content.substring(1)
    }

    require(content.isNotBlank()) { "Content is empty." }

    // Detect delimiter and parse
    val delimiter = detectDelimiter(content)
    val format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
        .setDelimiter(delimiter)
        .setIgnoreSurroundingSpaces(true)
        .build()

    val parser = CSVParser.parse(StringReader(content), format)
    val allRows = parser.records.map { record ->
        (0 until record.size()).map { record.get(it) }
    }

    require(allRows.isNotEmpty()) { "No data found." }

    val detectedHeader = guessHasHeader(allRows.first())

    val suggestedMappings = if (detectedHeader) {
        val mappings = allRows.first().map { matchColumnName(it) }
        val seen = mutableSetOf<ColumnMapping>()
        mappings.map { mapping ->
            if (mapping == ColumnMapping.SKIP || mapping !in seen) {
                seen.add(mapping)
                mapping
            } else {
                ColumnMapping.SKIP
            }
        }
    } else {
        null
    }

    return CsvParseResponse(
        rows = allRows,
        detectedHeader = detectedHeader,
        suggestedMappings = suggestedMappings,
    )
}

/** Regex to extract the spreadsheet ID from a Google Sheets URL. */
private val GOOGLE_SHEETS_ID_REGEX = Regex(
    """docs\.google\.com/spreadsheets/(?:u/\d+/)?d/([a-zA-Z0-9_-]+)"""
)

/** Extract the sheet gid from a Google Sheets URL fragment or query parameter. */
private fun extractGid(url: String): String? {
    // Check for #gid=... fragment
    val fragmentMatch = Regex("""[#&?]gid=(\d+)""").find(url)
    if (fragmentMatch != null) return fragmentMatch.groupValues[1]
    return null
}

/** Lazy-initialized Ktor HTTP client for fetching external URLs. */
private val fetchClient: HttpClient by lazy {
    HttpClient(CIO) {
        followRedirects = true
    }
}

fun importQuestionRoutes(routing: Routing) = routing.apply {

    // Parse CSV endpoint: receives multipart file, returns parsed rows + header guess + suggested mappings
    postST("$roomUrl/questions/import/parse") {
        call.withRoom {
            assertPermission(RoomPermission.ADD_QUESTION, "You cannot add questions.")

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

            try {
                val response = parseCsvBytes(bytes)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: IllegalArgumentException) {
                badRequest(e.message ?: "Failed to parse file.")
            }
        }
    }

    // Parse Google Sheets URL endpoint: fetches the spreadsheet as TSV and parses it
    postST("$roomUrl/questions/import/parse-url") {
        call.withRoom {
            assertPermission(RoomPermission.ADD_QUESTION, "You cannot add questions.")

            val payload: ImportUrlRequest = call.receive()
            val url = payload.url.trim()

            // Validate and extract spreadsheet ID
            val idMatch = GOOGLE_SHEETS_ID_REGEX.find(url)
                ?: badRequest("Not a valid Google Sheets URL. Please paste a sharing link like: https://docs.google.com/spreadsheets/d/...")

            val spreadsheetId = idMatch.groupValues[1]
            val gid = extractGid(url)

            // Build export URL
            val exportUrl = buildString {
                append("https://docs.google.com/spreadsheets/d/")
                append(spreadsheetId)
                append("/export?format=tsv")
                if (gid != null) {
                    append("&gid=")
                    append(gid)
                }
            }

            // Fetch TSV from Google Sheets
            val httpResponse = try {
                fetchClient.get(exportUrl)
            } catch (e: Exception) {
                badRequest("Failed to fetch spreadsheet: ${e.message}")
            }

            if (!httpResponse.status.isSuccess()) {
                badRequest(
                    "Could not fetch spreadsheet (HTTP ${httpResponse.status.value}). " +
                    "Make sure the spreadsheet is publicly accessible via \"Anyone with the link\"."
                )
            }

            val bytes = httpResponse.bodyAsBytes()
            if (bytes.isEmpty()) badRequest("Fetched spreadsheet is empty.")

            try {
                val response = parseCsvBytes(bytes)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: IllegalArgumentException) {
                badRequest(e.message ?: "Failed to parse spreadsheet data.")
            }
        }
    }

    // Import questions endpoint: receives structured JSON, creates or updates Question entities
    postST("$roomUrl/questions/import") {
        val result = call.withRoom {
            assertPermission(RoomPermission.ADD_QUESTION, "You cannot add questions.")

            val payload: ImportQuestions = call.receive()

            if (payload.questions.isEmpty()) badRequest("No questions to import.")

            // Load existing questions in the room for matching
            val existingQuestions = room.questions.mapDeref()
            val byExternalId: Map<String, Question> = existingQuestions
                .filter { it.externalId != null }
                .associateBy { it.externalId!! }
            val byNormalizedName: Map<String, Question> = existingQuestions
                .associateBy { normalizeHeader(it.name) }

            val now = Clock.System.now()
            var created = 0
            var updated = 0
            var unchanged = 0

            serverState.withTransaction {
                val newRefs = mutableListOf<tools.confido.refs.Ref<Question>>()

                for (imported in payload.questions) {
                    if (imported.name.isBlank()) badRequest("Question title cannot be empty.")

                    imported.resolution?.let { res ->
                        if (res.space != imported.answerSpace) {
                            badRequest("Resolution does not match answer space for question: ${imported.name}")
                        }
                    }

                    // Find matching existing question
                    val match = if (imported.externalId != null) {
                        byExternalId[imported.externalId]
                    } else {
                        byNormalizedName[normalizeHeader(imported.name)]
                    }

                    if (match != null) {
                        // UPDATE existing question — only update fields that were mapped
                        val mapped = imported.mappedFields
                        var changed = false
                        serverState.questionManager.modifyEntity(match.ref) { q ->
                            var updated = q

                            // Always update name (it's the required field)
                            if (updated.name != imported.name) {
                                updated = updated.copy(name = imported.name)
                                changed = true
                            }

                            // Update description only if mapped
                            if (ColumnMapping.DESCRIPTION in mapped && updated.description != imported.description) {
                                updated = updated.copy(description = imported.description)
                                changed = true
                            }

                            // Update answer space if it changed
                            if (updated.answerSpace != imported.answerSpace) {
                                updated = updated.copy(answerSpace = imported.answerSpace)
                                changed = true
                            }

                            // Update resolution only if mapped
                            if (ColumnMapping.RESOLUTION in mapped && updated.resolution != imported.resolution) {
                                updated = updated.copy(resolution = imported.resolution)
                                changed = true
                            }

                            // Update tags if TAGS column was mapped (replace with imported + global)
                            // or if global tags are set (union into existing)
                            if (ColumnMapping.TAGS in mapped) {
                                val newTags = imported.tags + payload.options.tags
                                if (updated.tags != newTags) {
                                    updated = updated.copy(tags = newTags)
                                    changed = true
                                }
                            } else if (payload.options.tags.isNotEmpty()) {
                                val newTags = updated.tags + payload.options.tags
                                if (updated.tags != newTags) {
                                    updated = updated.copy(tags = newTags)
                                    changed = true
                                }
                            }

                            // Set externalId if mapped and not already set
                            if (imported.externalId != null && updated.externalId != imported.externalId) {
                                updated = updated.copy(externalId = imported.externalId)
                                changed = true
                            }

                            updated
                        }
                        if (changed) updated++ else unchanged++
                    } else {
                        // INSERT new question
                        val question = Question(
                            id = "",
                            name = imported.name,
                            description = imported.description,
                            answerSpace = imported.answerSpace,
                            resolution = imported.resolution,
                            visible = payload.options.visible,
                            open = payload.options.open,
                            groupPredVisible = payload.options.groupPredVisible,
                            groupPredRequirePrediction = payload.options.groupPredRequirePrediction,
                            allowComments = payload.options.allowComments,
                            commentVisibility = payload.options.commentVisibility,
                            tags = imported.tags + payload.options.tags,
                            author = user.ref,
                            stateHistory = listOf(QuestionStateChange(
                                newState = if (!payload.options.visible) QuestionState.DRAFT
                                           else if (payload.options.open) QuestionState.OPEN
                                           else QuestionState.CLOSED,
                                at = now,
                                user = user.ref,
                            )),
                            externalId = imported.externalId,
                        )
                        val inserted = serverState.questionManager.insertEntity(question)
                        newRefs.add(inserted.ref)
                        created++
                    }
                }

                // Add newly created questions to the room (reversed so that
                // the original CSV order is preserved in the UI, which
                // displays room.questions.reversed()).
                if (newRefs.isNotEmpty()) {
                    serverState.roomManager.modifyEntity(room.ref) {
                        it.copy(questions = it.questions + newRefs.reversed())
                    }
                }
            }

            ImportResult(created = created, updated = updated, unchanged = unchanged)
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK, result)
    }
}
