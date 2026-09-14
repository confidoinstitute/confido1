package tools.confido.application.migrations

import kotlinx.coroutines.flow.toList
import com.mongodb.client.model.Filters
import org.bson.Document

/**
 * Retires the `room_groups` extension: its extension-data keys become core Room
 * fields — `room_group.is_group` moves to `isGroup`, `room_group.parent` (a room
 * id string; Refs serialize as plain strings) moves to `parentRoom`.
 *
 * The EDT keys are removed either way — the extension is gone, and orphaned keys
 * would linger in every future save. Shape-conditional: re-running over migrated
 * documents is a no-op.
 */
object RoomGroupsEdtToCoreMigration : Migration {
    override val id = "010_room_groups_edt_to_core"
    override val description = "Move room_groups extension data to core Room fields"
    override val collections = listOf("rooms")

    override suspend fun run(ctx: MigrationContext) {
        val rooms = ctx.collection("rooms")

        rooms.find().toList().forEach { doc ->
            val edt = doc.get("extensionData") as? Document ?: return@forEach
            if (!edt.containsKey("room_group.is_group") && !edt.containsKey("room_group.parent"))
                return@forEach

            if (edt.getBoolean("room_group.is_group", false))
                doc["isGroup"] = true
            // The extension sometimes stored an explicit null (unparenting on group
            // deletion used with(key, null) rather than without(key)) — skip those.
            edt.getString("room_group.parent")?.let { doc["parentRoom"] = it }
            edt.remove("room_group.is_group")
            edt.remove("room_group.parent")

            rooms.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
        }
    }
}
