package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import org.bson.Document

/**
 * Converts legacy `presenterView` flow items in stored presentations to the
 * equivalent `action` items wrapping a `setPresenterView` action, allowing the
 * PresenterViewItem class to be deleted (new view items have long been authored
 * as actions; the class was kept only so old documents deserialize).
 * Shape-conditional: a re-run over migrated data is a no-op.
 */
object PresenterViewItemMigration : Migration {
    override val id = "006_presenter_view_items"
    override val description = "Convert legacy presenterView flow items to setPresenterView actions"
    override val collections = listOf("presentations")

    override suspend fun run(ctx: MigrationContext) {
        val coll = ctx.collection("presentations")
        coll.find().toList().forEach { doc ->
            val items = doc.getList("items", Document::class.java) ?: return@forEach
            var changed = false
            val newItems = items.map { item ->
                if (item.getString("___type") != "presenterView") item
                else Document(mapOf(
                    "___type" to "action",
                    "id" to (item.getString("id") ?: ""),
                    "action" to Document(mapOf(
                        "___type" to "setPresenterView",
                        "view" to item.get("view"),
                    )),
                )).also { changed = true }
            }
            if (changed) {
                doc["items"] = newItems
                coll.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
            }
        }
    }
}
