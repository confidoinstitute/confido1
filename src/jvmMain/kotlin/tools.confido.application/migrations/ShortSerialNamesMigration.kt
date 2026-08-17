package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import org.bson.Document

/**
 * Rewrites polymorphic discriminators from default fully-qualified serial names
 * to explicit short `@SerialName` values, ahead of the package reorganization
 * (a class's default serial name is its FQN, so it must be frozen before the
 * class moves packages).
 *
 * Old FQN discriminators occur in:
 *   - `userSessions`: `presenterInfo.view` (a PresenterView, incl. nested `req.who`)
 *   - `presentations`: `items[].view`, and views/actions nested inside
 *     `setPresenterView`, `millionaireSetState` and `composite` actions
 *   - `questionComments` / `roomComments`: top-level `___type` (sealed Comment)
 *
 * Because views and actions nest arbitrarily, the rewrite walks whole documents
 * and replaces discriminator values by exact match — a re-run over already
 * migrated data is a no-op.
 */
object ShortSerialNamesMigration : Migration {
    override val id = "004_short_serial_names"
    override val description = "Rewrite polymorphic discriminators from FQNs to short serial names"
    override val collections = listOf("userSessions", "presentations", "questionComments", "roomComments")

    private val renames = mapOf(
        "tools.confido.question.QuestionComment" to "question",
        "tools.confido.question.RoomComment" to "room",
        "payloads.requests.Myself" to "myself",
        "payloads.requests.Everyone" to "everyone",
        "extensions.update_scatter_plot.UpdatePlotPV" to "updatePlot",
        "extensions.millionaire.MillionaireScoreboardPV" to "millionaireScoreboard",
        "extensions.millionaire.MillionaireLadderQuestionPV" to "millionaireLadderQuestion",
    )

    // kbson writes "___type"; "type" is probed too (cf. RoleFormatMigration). Exact
    // value matching keeps this safe: no data field can hold one of these FQNs.
    private val discriminatorKeys = setOf("___type", "type")

    override suspend fun run(ctx: MigrationContext) {
        collections.forEach { name ->
            val coll = ctx.collection(name)
            coll.find().toList().forEach { doc ->
                if (rewrite(doc)) coll.replaceOne(Filters.eq("_id", doc["_id"]), doc)
            }
        }
    }

    /** Rewrites discriminator values anywhere in the tree. Returns true if modified. */
    private fun rewrite(node: Any?): Boolean = when (node) {
        is Document -> {
            var modified = false
            for (key in node.keys.toList()) {
                val value = node[key]
                if (key in discriminatorKeys && value is String && value in renames) {
                    node[key] = renames.getValue(value)
                    modified = true
                } else if (rewrite(value)) {
                    modified = true
                }
            }
            modified
        }
        is List<*> -> node.count { rewrite(it) } > 0
        else -> false
    }
}
