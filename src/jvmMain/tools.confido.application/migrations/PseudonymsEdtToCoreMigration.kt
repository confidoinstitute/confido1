package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import org.bson.Document

/**
 * Retires the `pseudonyms` extension: rooms that had it configured (a non-empty
 * `extensionData.pseudonyms_list`) become core PSEUDONYM rooms — the curated list
 * moves to `pseudonymPool`, the stable assignments to `pseudonymAssignments`, and
 * `defaultAttribution` is set to pseudonymous with moderator access (the extension
 * let MANAGE_MEMBERS holders see the mapping, which is what moderatorAccess=true +
 * the moderator role's VIEW_REAL_IDENTITIES now expresses).
 *
 * The EDT keys are removed either way — the extension is gone, and orphaned keys
 * would linger in every future save. Shape-conditional: re-running over migrated
 * documents is a no-op.
 */
object PseudonymsEdtToCoreMigration : Migration {
    override val id = "008_pseudonyms_edt_to_core"
    override val description = "Move pseudonyms extension data to core attribution fields"
    override val collections = listOf("rooms")

    override suspend fun run(ctx: MigrationContext) {
        val rooms = ctx.collection("rooms")

        rooms.find().toList().forEach { doc ->
            val edt = doc.get("extensionData") as? Document ?: return@forEach
            if (!edt.containsKey("pseudonyms_list") && !edt.containsKey("pseudonym_assignments"))
                return@forEach

            val pool = edt.getList("pseudonyms_list", String::class.java) ?: emptyList()
            val assignments = edt.get("pseudonym_assignments") as? Document
            if (pool.isNotEmpty()) {
                doc["pseudonymPool"] = pool
                doc["defaultAttribution"] = Document("level", "PSEUDONYM")
            }
            if (assignments != null && assignments.isNotEmpty())
                doc["pseudonymAssignments"] = assignments
            edt.remove("pseudonyms_list")
            edt.remove("pseudonym_assignments")

            rooms.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
        }
    }
}
