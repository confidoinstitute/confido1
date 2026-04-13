package payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.question.CommentVisibility
import tools.confido.spaces.Space
import tools.confido.spaces.Value

@Serializable
enum class ColumnMapping {
    TITLE,
    DESCRIPTION,
    RESOLUTION,
    MIN,
    MAX,
    UNIT,
    DECIMALS,
    EXTERNAL_ID,
    TAGS,
    SKIP,
}

@Serializable
data class CsvParseResponse(
    val rows: List<List<String>>,
    /** Heuristic guess whether the first row is a header. */
    val detectedHeader: Boolean,
    /** Suggested column mappings based on header name matching (null if no header detected). */
    val suggestedMappings: List<ColumnMapping>? = null,
)

@Serializable
data class ImportedQuestion(
    val name: String,
    val description: String = "",
    val answerSpace: Space,
    val resolution: Value? = null,
    val externalId: String? = null,
    val tags: Set<String> = emptySet(),
    /** Tracks which fields were explicitly mapped from the CSV (for partial updates). */
    val mappedFields: Set<ColumnMapping> = emptySet(),
)

@Serializable
data class ImportQuestionOptions(
    /** false = DRAFT (not visible), true requires open to determine OPEN vs CLOSED */
    val visible: Boolean = false,
    val open: Boolean = false,
    val groupPredVisible: Boolean = false,
    val groupPredRequirePrediction: Boolean = false,
    val allowComments: Boolean = true,
    val commentVisibility: CommentVisibility = CommentVisibility.EVERYONE,
    /** Tags to add to all imported questions. */
    val tags: Set<String> = emptySet(),
)

@Serializable
data class ImportQuestions(
    val questions: List<ImportedQuestion>,
    val options: ImportQuestionOptions = ImportQuestionOptions(),
)

@Serializable
data class ImportResult(
    val created: Int,
    val updated: Int,
    val unchanged: Int,
)
