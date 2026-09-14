package tools.confido.application.migrations

import kotlinx.coroutines.flow.toList
import com.mongodb.client.model.Filters
import org.bson.Document

/**
 * Seeds the attribution promise floors: questions and rooms with pre-existing
 * activity (predictions or comments) get their *current* effective policy stamped
 * as [attributionFloor]. Conservative by construction — we cannot know what
 * policy old activity was actually authored under, so the current one stands in
 * (matching the pre-floor ratchet's behavior of locking on any activity). Floors
 * for new activity are stamped exactly at insert time from here on.
 *
 * OPEN effective policies are not stamped: an OPEN floor never binds.
 * Shape-conditional: re-running over migrated documents is a no-op.
 */
object AttributionFloorMigration : Migration {
    override val id = "009_attribution_floors"
    override val description = "Stamp attribution promise floors onto questions/rooms with existing activity"
    override val collections = listOf("questions", "rooms", "userPredHist", "questionComments", "roomComments")

    override suspend fun run(ctx: MigrationContext) {
        val rooms = ctx.collection("rooms")
        val questions = ctx.collection("questions")

        val roomDocs = rooms.find().toList()
        val roomDefault = roomDocs.associate {
            it.getString("_id") to (it.get("defaultAttribution") as? Document)
        }
        val questionRoom = buildMap {
            roomDocs.forEach { r ->
                (r.getList("questions", String::class.java) ?: emptyList()).forEach { q ->
                    put(q, r.getString("_id"))
                }
            }
        }

        fun binding(policy: Document?): Document? =
            policy?.takeIf { it.getString("level") != null && it.getString("level") != "OPEN" }

        val activeQuestions =
            ctx.collection("userPredHist").distinct<String>("question").toList().toSet() +
            ctx.collection("questionComments").distinct<String>("question").toList().toSet()
        questions.find().toList().forEach { doc ->
            if (doc.containsKey("attributionFloor")) return@forEach
            val id = doc.getString("_id")
            if (id !in activeQuestions) return@forEach
            val eff = binding(doc.get("attribution") as? Document ?: roomDefault[questionRoom[id]])
                ?: return@forEach
            doc["attributionFloor"] = Document(eff)
            questions.replaceOne(Filters.eq("_id", id), doc)
        }

        val activeRooms = ctx.collection("roomComments").distinct<String>("room").toList().toSet()
        roomDocs.forEach { doc ->
            if (doc.containsKey("attributionFloor")) return@forEach
            val id = doc.getString("_id")
            if (id !in activeRooms) return@forEach
            val eff = binding(doc.get("defaultAttribution") as? Document) ?: return@forEach
            doc["attributionFloor"] = Document(eff)
            rooms.replaceOne(Filters.eq("_id", id), doc)
        }
    }
}
