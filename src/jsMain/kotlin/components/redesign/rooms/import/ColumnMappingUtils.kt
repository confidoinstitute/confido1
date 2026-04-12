package components.redesign.rooms.import

import payloads.requests.ColumnMapping

/** Display labels for column mapping options. */
val ColumnMapping.displayLabel: String get() = when (this) {
    ColumnMapping.TITLE -> "Question title"
    ColumnMapping.DESCRIPTION -> "Description"
    ColumnMapping.RESOLUTION -> "Resolution"
    ColumnMapping.MIN -> "Min"
    ColumnMapping.MAX -> "Max"
    ColumnMapping.UNIT -> "Unit"
    ColumnMapping.DECIMALS -> "Decimals"
    ColumnMapping.SKIP -> "(skip)"
}

/** Column mappings relevant for binary questions. */
val BINARY_MAPPINGS = setOf(
    ColumnMapping.TITLE,
    ColumnMapping.DESCRIPTION,
    ColumnMapping.RESOLUTION,
    ColumnMapping.SKIP,
)

/** Column mappings relevant for numeric questions. */
val NUMERIC_MAPPINGS = ColumnMapping.entries.toSet()

/** Column mappings relevant for date questions. */
val DATE_MAPPINGS = setOf(
    ColumnMapping.TITLE,
    ColumnMapping.DESCRIPTION,
    ColumnMapping.RESOLUTION,
    ColumnMapping.MIN,
    ColumnMapping.MAX,
    ColumnMapping.SKIP,
)

/** Get the set of allowed column mappings for a given question type. */
fun allowedMappings(questionType: QuestionType): Set<ColumnMapping> = when (questionType) {
    QuestionType.BINARY -> BINARY_MAPPINGS
    QuestionType.NUMERIC -> NUMERIC_MAPPINGS
    QuestionType.DATE -> DATE_MAPPINGS
}

/** The question type enum used in the import wizard. */
enum class QuestionType(val label: String) {
    BINARY("Yes / No"),
    NUMERIC("Number"),
    DATE("Date"),
}
