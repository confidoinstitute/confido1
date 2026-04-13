package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineDatabase

/**
 * Migrates question group data from the `question_groups` extension
 * (stored in `extensionData.question_groups`) to the new core `tags` field.
 *
 * For each question document:
 *   - If `extensionData.question_groups` exists (as a BSON array of strings),
 *     copy the values to the top-level `tags` field.
 *   - Remove the `question_groups` key from `extensionData`.
 */
object QuestionGroupsToTagsMigration : Migration {
    override val id = "002_question_groups_to_tags"
    override val description = "Migrate question_groups extension data to core tags field"

    override suspend fun run(db: CoroutineDatabase) {
        val questions = db.getCollection<Document>("questions")

        questions.find().toList().forEach { doc ->
            var modified = false

            val extensionData = doc.get("extensionData") as? Document
            if (extensionData != null) {
                val groups = extensionData.getList("question_groups", String::class.java)
                if (groups != null && groups.isNotEmpty()) {
                    // Write to top-level tags field
                    doc["tags"] = groups
                    modified = true
                }
                // Remove the extension data key regardless (clean up even if empty)
                if (extensionData.containsKey("question_groups")) {
                    extensionData.remove("question_groups")
                    modified = true
                }
            }

            if (modified) {
                questions.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
            }
        }
    }
}
