package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineDatabase

/**
 * Converts RoomRole fields from the old sealed-class polymorphic format
 * to plain string IDs.
 *
 * Old format (embedded BSON document):
 *   `{ "type": "rooms.Viewer" }`
 *   `{ "type": "rooms.Forecaster" }`
 *   `{ "type": "question_writer" }`
 *   `{ "type": "rooms.Moderator" }`
 *   `{ "type": "rooms.Owner" }`
 *
 * New format (plain string):
 *   `"viewer"`, `"forecaster"`, `"question_writer"`, `"moderator"`, `"owner"`
 *
 * Affected fields in the `rooms` collection:
 *   - `members[].role`
 *   - `inviteLinks[].role`
 */
object RoleFormatMigration : Migration {
    override val id = "001_role_format"
    override val description = "Convert RoomRole from sealed-class object to string ID"

    private val typeToId = mapOf(
        "rooms.Viewer" to "viewer",
        "rooms.Forecaster" to "forecaster",
        "question_writer" to "question_writer",
        "rooms.Moderator" to "moderator",
        "rooms.Owner" to "owner",
    )

    override suspend fun run(db: CoroutineDatabase) {
        val rooms = db.getCollection<Document>("rooms")

        rooms.find().toList().forEach { doc ->
            var modified = false

            // Migrate members[].role
            val members = doc.getList("members", Document::class.java)
            if (members != null) {
                for (member in members) {
                    if (migrateRoleField(member)) modified = true
                }
            }

            // Migrate inviteLinks[].role
            val links = doc.getList("inviteLinks", Document::class.java)
            if (links != null) {
                for (link in links) {
                    if (migrateRoleField(link)) modified = true
                }
            }

            if (modified) {
                rooms.replaceOne(Filters.eq("_id", doc.getString("_id")), doc)
            }
        }
    }

    /**
     * If the "role" field of [doc] is an embedded Document with a "type" field,
     * replace it with the corresponding string ID. Returns true if modified.
     */
    private fun migrateRoleField(doc: Document): Boolean {
        val role = doc.get("role")
        if (role is Document) {
            val typeName = role.getString("type") ?: role.getString("___type") ?: return false
            doc["role"] = typeToId[typeName] ?: typeName
            return true
        }
        return false
    }
}
