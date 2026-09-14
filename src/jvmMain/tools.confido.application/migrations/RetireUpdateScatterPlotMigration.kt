package tools.confido.application.migrations

import kotlinx.coroutines.flow.toList
import com.mongodb.client.model.Filters
import org.bson.Document
import tools.confido.rooms.RoomPermission
import tools.confido.state.appConfig
import tools.confido.utils.generateId

/**
 * Retires the update_scatter_plot extension (replaced by the core compare /
 * timeline-marker features):
 *
 * - Snapshots (`extensionData.snapshots`) become core `timelineMarks`: public
 *   ones and private ones owned by someone who can MANAGE_QUESTIONS in the room
 *   (or by an admin) are converted; other private snapshots are dropped. The
 *   `snapshots` and `update_reference_question` extension keys are removed.
 * - Stored presentations lose `updatePlot` presenter-view steps (the extension's
 *   PV class no longer deserializes) and `createSnapshot` flow actions become
 *   the field-compatible core `createMark`.
 * - Persisted sessions holding an `updatePlot` presenter view have it cleared.
 *
 * Replaces the never-deployed 005_public_snapshots_to_timeline_marks (whose
 * public-snapshot conversion this subsumes); shape-conditional throughout, so a
 * re-run — including over data that migration already touched — is a no-op.
 */
object RetireUpdateScatterPlotMigration : Migration {
    override val id = "005_retire_update_scatter_plot"
    override val description = "Move update_scatter_plot snapshots to core timelineMarks, scrub its stored types"
    override val collections = listOf("rooms", "questions", "presentations", "userSessions", "users")

    /** Convert this doc's snapshots to marks, keeping those owned by [keepOwners]
     *  (or public ones); scrub the extension's keys. */
    private fun migrateEntity(doc: Document, keepOwners: Set<String>): Boolean {
        val extensionData = doc.get("extensionData") as? Document ?: return false
        var changed = extensionData.remove("update_reference_question") != null
        val snaps = extensionData.getList("snapshots", Document::class.java)
        if (snaps != null) {
            val kept = snaps.filter {
                it.getBoolean("public", false) || it.getString("owner") in keepOwners
            }
            if (kept.isNotEmpty()) {
                val marks = (doc.getList("timelineMarks", Document::class.java) ?: emptyList()) +
                    kept.map { s ->
                        Document().apply {
                            put("id", s.getString("id") ?: generateId())
                            put("ts", s.getInteger("ts"))
                            put("name", s.getString("title"))
                            put("owner", s.getString("owner"))
                        }
                    }
                doc["timelineMarks"] = marks.sortedBy { it.getInteger("ts") }
            }
            extensionData.remove("snapshots")
            changed = true
        }
        return changed
    }

    private fun isUpdatePlotView(view: Any?) = (view as? Document)?.getString("___type") == "updatePlot"

    /** Returns the transformed action, or null when the action must be dropped. */
    private fun transformAction(action: Document): Document? = when (action.getString("___type")) {
        "createSnapshot" -> action.apply { put("___type", "createMark") }
        "setPresenterView" -> if (isUpdatePlotView(action.get("view"))) null else action
        "composite" -> action.apply {
            put("actions", (getList("actions", Document::class.java) ?: emptyList())
                .mapNotNull { transformAction(it) })
        }
        else -> action
    }

    private fun migratePresentation(doc: Document): Boolean {
        val items = doc.getList("items", Document::class.java) ?: return false
        var changed = false
        val newItems = items.mapNotNull { item ->
            when (item.getString("___type")) {
                "presenterView" ->
                    if (isUpdatePlotView(item.get("view"))) { changed = true; null } else item
                "action" -> {
                    val action = item.get("action") as? Document ?: return@mapNotNull item
                    val before = action.toJson()
                    val transformed = transformAction(action)
                    if (transformed == null) { changed = true; null }
                    else {
                        if (transformed.toJson() != before) changed = true
                        item.apply { put("action", transformed) }
                    }
                }
                else -> item
            }
        }
        if (changed) doc["items"] = newItems
        return changed
    }

    override suspend fun run(ctx: MigrationContext) {
        // Who counts as a moderator: admins everywhere, plus members whose room
        // role (resolved via the current config) grants MANAGE_QUESTIONS.
        val admins = ctx.collection("users").find(Filters.eq("type", "ADMIN"))
            .toList().map { it.getString("_id") }.toSet()
        val roomDocs = ctx.collection("rooms").find().toList()
        val roomModerators = roomDocs.associate { room ->
            room.getString("_id") to admins + (room.getList("members", Document::class.java) ?: emptyList())
                .filter { m ->
                    appConfig.roleById(m.getString("role") ?: "")
                        ?.hasPermission(RoomPermission.MANAGE_QUESTIONS) == true
                }
                .mapNotNull { it.getString("user") }
        }
        val questionRoom = roomDocs.flatMap { room ->
            (room.getList("questions", String::class.java) ?: emptyList())
                .map { it to room.getString("_id") }
        }.toMap()

        val roomColl = ctx.collection("rooms")
        roomDocs.forEach { doc ->
            if (migrateEntity(doc, roomModerators[doc.getString("_id")] ?: admins))
                roomColl.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
        }
        val questionColl = ctx.collection("questions")
        questionColl.find().toList().forEach { doc ->
            val mods = questionRoom[doc.getString("_id")]?.let { roomModerators[it] } ?: admins
            if (migrateEntity(doc, mods))
                questionColl.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
        }
        val presColl = ctx.collection("presentations")
        presColl.find().toList().forEach { doc ->
            if (migratePresentation(doc))
                presColl.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
        }
        val sessionColl = ctx.collection("userSessions")
        sessionColl.find().toList().forEach { doc ->
            val info = doc.get("presenterInfo") as? Document ?: return@forEach
            if (isUpdatePlotView(info.get("view"))) {
                doc["presenterInfo"] = null
                sessionColl.replaceOne(Filters.eq("_id", doc.get("_id")), doc)
            }
        }
    }
}
