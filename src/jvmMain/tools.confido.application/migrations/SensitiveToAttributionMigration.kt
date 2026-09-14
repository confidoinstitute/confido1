package tools.confido.application.migrations

import kotlinx.coroutines.flow.toList
import com.mongodb.client.model.Filters
import org.bson.Document

/**
 * Folds the boolean `sensitive` question flag into the attribution model:
 * `sensitive: true` becomes `attribution: {level: "AGGREGATE_ONLY"}` (the level's
 * semantics are identical to the old flag). The `sensitive` key is removed either
 * way — the strict verify pass would reject documents with the retired field.
 *
 * Shape-conditional: a re-run over already-migrated documents is a no-op.
 */
object SensitiveToAttributionMigration : Migration {
    override val id = "007_sensitive_to_attribution"
    override val description = "Fold the sensitive question flag into the attribution policy model"
    override val collections = listOf("questions")

    override suspend fun run(ctx: MigrationContext) {
        val questions = ctx.collection("questions")

        questions.find().toList().forEach { doc ->
            if (!doc.containsKey("sensitive")) return@forEach
            if (doc.getBoolean("sensitive", false)) {
                doc["attribution"] = Document("level", "AGGREGATE_ONLY")
            }
            doc.remove("sensitive")
            questions.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
        }
    }
}
