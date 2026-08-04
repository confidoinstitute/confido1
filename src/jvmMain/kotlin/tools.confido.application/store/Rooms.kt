package tools.confido.store

import rooms.Room
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.serverState

object roomStore : MongoStore<Room>(
    serverState.database.getCollection("rooms"),
    { e, id -> e.copy(id = id) },
) {
    /** Which room owns each question — maintained as a fold over room changes. */
    val questionRoom: MutableMap<Ref<Question>, Ref<Room>> = mutableMapOf()

    init {
        // Strip computed fields before persisting to DB (safety net against accidental
        // persistence). Goes away with the wire-only fields themselves (design doc §5.2).
        beforeEntitySave { _, room ->
            room.copy(
                _effectiveMembers = null,
                _effectiveQuestions = null,
                _presentationRunning = null,
                members = room.members.map {
                    it.copy(implied = false, impliedFrom = null, effectivePermissions = null)
                }
            )
        }
        onChange { change ->
            when (change) {
                is Change.Added ->
                    change.entity.questions.forEach { questionRoom[it] = change.entity.ref }
                is Change.Updated -> {
                    val added = change.new.questions.toSet() - change.old.questions.toSet()
                    val removed = change.old.questions.toSet() - change.new.questions.toSet()
                    added.forEach { questionRoom[it] = change.new.ref }
                    removed.forEach { if (questionRoom[it] == change.new.ref) questionRoom.remove(it) }
                }
                is Change.Removed ->
                    change.entity.questions.forEach { if (questionRoom[it] == change.entity.ref) questionRoom.remove(it) }
            }
        }
    }
}

/** Register the room store. Called once from server startup. */
fun registerRoomStores() {
    Stores.register(roomStore)
    Stores.resident[Room::class] = roomStore
}
