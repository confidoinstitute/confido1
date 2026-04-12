package tools.confido.application.routes

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

            // Detect encoding and decode
            val charset = detectEncoding(bytes)
            var content = bytes.toString(charset)
            // Strip BOM if present
            if (content.isNotEmpty() && content[0] == '\uFEFF') {
                content = content.substring(1)
            }

            if (content.isBlank()) badRequest("Uploaded file is empty.")

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

            if (allRows.isEmpty()) badRequest("No data found in file.")

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

            call.respond(HttpStatusCode.OK, CsvParseResponse(
                rows = allRows,
                detectedHeader = detectedHeader,
                suggestedMappings = suggestedMappings,
            ))
        }
    }

    // Import questions endpoint: receives structured JSON, creates Question entities
    postST("$roomUrl/questions/import") {
        val count = call.withRoom {
            assertPermission(RoomPermission.ADD_QUESTION, "You cannot add questions.")

            val payload: ImportQuestions = call.receive()

            if (payload.questions.isEmpty()) badRequest("No questions to import.")

            val now = Clock.System.now()
            val questionsToInsert = payload.questions.map { imported ->
                if (imported.name.isBlank()) badRequest("Question title cannot be empty.")

                imported.resolution?.let { res ->
                    if (res.space != imported.answerSpace) {
                        badRequest("Resolution does not match answer space for question: ${imported.name}")
                    }
                }

                Question(
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
                    author = user.ref,
                    stateHistory = listOf(QuestionStateChange(
                        newState = if (!payload.options.visible) QuestionState.DRAFT
                                   else if (payload.options.open) QuestionState.OPEN
                                   else QuestionState.CLOSED,
                        at = now,
                        user = user.ref,
                    )),
                )
            }

            serverState.withTransaction {
                val insertedRefs = questionsToInsert.map { q ->
                    serverState.questionManager.insertEntity(q).ref
                }
                serverState.roomManager.modifyEntity(room.ref) {
                    it.copy(questions = it.questions + insertedRefs)
                }
            }

            questionsToInsert.size
        }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK, "$count")
    }
}
