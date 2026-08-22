package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import org.bson.Document
import tools.confido.utils.generateId

/**
 * Moves the update_scatter_plot extension's *public* snapshots
 * (`extensionData.snapshots`, entries with `public: true`) into the core
 * `timelineMarks` field on rooms and questions. Private snapshots stay in the
 * extension data — they remain an extension-local personal feature (core
 * timeline marks are visible to everyone who sees the owning entity, so
 * owner-only visibility cannot be expressed there).
 *
 * Snapshot -> mark field mapping: title -> name; id is kept when present
 * (older snapshots may lack one). Shape-conditional: a re-run over migrated
 * data is a no-op (no public snapshots remain in extensionData).
 */
object PublicSnapshotsToTimelineMarksMigration : Migration {
    override val id = "005_public_snapshots_to_timeline_marks"
    override val description = "Move public update_scatter_plot snapshots into core timelineMarks"
    override val collections = listOf("rooms", "questions")

    private fun migrateDoc(doc: Document): Boolean {
        val extensionData = doc.get("extensionData") as? Document ?: return false
        val snaps = extensionData.getList("snapshots", Document::class.java) ?: return false
        val (public, private) = snaps.partition { it.getBoolean("public", false) }
        if (public.isEmpty()) return false

        val marks = (doc.getList("timelineMarks", Document::class.java) ?: emptyList()) +
            public.map { s ->
                Document().apply {
                    put("id", s.getString("id") ?: generateId())
                    put("ts", s.getInteger("ts"))
                    put("name", s.getString("title"))
                    put("owner", s.getString("owner"))
                }
            }
        doc["timelineMarks"] = marks.sortedBy { it.getInteger("ts") }
        if (private.isEmpty()) extensionData.remove("snapshots")
        else extensionData["snapshots"] = private
        return true
    }

    override suspend fun run(ctx: MigrationContext) {
        for (name in collections) {
            val coll = ctx.collection(name)
            coll.find().toList().forEach { doc ->
                if (migrateDoc(doc)) {
                    coll.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
                }
            }
        }
    }
}
