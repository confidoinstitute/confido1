package tools.confido.store

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tools.confido.application.routes.badRequest
import tools.confido.rooms.Room
import tools.confido.rooms.childRooms
import tools.confido.rooms.ensurePseudonymAssignments
import tools.confido.rooms.resolvedDefaultAttribution
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.serverState

object roomStore : MongoStore<Room>(
    serverState.database.getCollection("rooms"),
    { e, id -> e.copy(id = id) },
) {
    /** Which room owns each question — maintained as a fold over room changes. */
    private val _questionRoom = MutableStateFlow<PersistentMap<Ref<Question>, Ref<Room>>>(persistentMapOf())
    /** Snapshot flow of the question→room index; topic derivations subscribe here. */
    val questionRoomState: StateFlow<PersistentMap<Ref<Question>, Ref<Room>>> get() = _questionRoom
    private var questionRoomMap by _questionRoom::value
    val questionRoom: Map<Ref<Question>, Ref<Room>> get() = questionRoomMap

    init {
        // Strip computed fields before persisting to DB (safety net against accidental
        // persistence). Goes away with the wire-only fields themselves (design doc §5.2).
        beforeEntitySave { _, room ->
            room.copy(
                _effectiveMembers = null,
                _effectiveQuestions = null,
                _presentationRunning = null,
                _effectiveDefaultAttribution = null,
                members = room.members.map {
                    it.copy(implied = false, impliedFrom = null, effectivePermissions = null)
                }
            )
        }
        // Alias maintenance: every save of a room that needs pseudonyms tops up its
        // stable assignments (member churn, pool edits and policy changes all pass
        // through room saves; question-level policy changes trigger a room touch
        // from the question routes).
        beforeEntitySave { _, room -> ensurePseudonymAssignments(room) }
        // Room-group structural invariants (a strict two-level hierarchy). Routes
        // additionally require the editor's consent standing in the target group;
        // this hook is the defense-in-depth backstop for any other save path.
        beforeEntitySave { old, new ->
            if (new.parentRoom != null && new.parentRoom != old?.parentRoom) {
                val group = serverState.rooms[new.parentRoom.id]
                    ?: badRequest("Group room not found.")
                if (!group.isGroup) badRequest("Target room is not a group.")
                if (new.isGroup) badRequest("A group room cannot be assigned to another group.")
            }
            new
        }
        // Pin-on-unparent/reparent: severing or switching the parent link must never
        // silently loosen an INHERITING room's effective attribution — the old
        // inherited policy is materialized as the room's own first (observationally
        // a no-op at pin time). Group deletion funnels through here too (children
        // are unparented via modify below). Explicitly-set policies are left alone:
        // the promise-floor ratchet in the edit route governs those.
        beforeEntitySave { old, new ->
            if (old == null || new.parentRoom == old.parentRoom || new.defaultAttribution != null) new
            else {
                val oldEff = old.resolvedDefaultAttribution
                val newEff = new.effectiveDefaultAttributionIn(new.parentRoom?.let { serverState.rooms[it.id] })
                if (newEff.revealsMoreThan(oldEff)) new.copy(defaultAttribution = oldEff) else new
            }
        }
        // When a group room is deleted, unparent all its children
        beforeEntityDelete { room ->
            if (room.isGroup) {
                room.childRooms.forEach { child ->
                    modify(child.id) { it.copy(parentRoom = null) }
                }
            }
        }
        onChange { change ->
            when (change) {
                is Change.Added ->
                    questionRoomMap = questionRoomMap.putAll(change.entity.questions.associateWith { change.entity.ref })
                is Change.Updated -> {
                    val added = change.new.questions.toSet() - change.old.questions.toSet()
                    val removed = change.old.questions.toSet() - change.new.questions.toSet()
                    var m = questionRoomMap.putAll(added.associateWith { change.new.ref })
                    removed.forEach { if (m[it] == change.new.ref) m = m.remove(it) }
                    questionRoomMap = m
                }
                is Change.Removed -> {
                    var m = questionRoomMap
                    change.entity.questions.forEach { if (m[it] == change.entity.ref) m = m.remove(it) }
                    questionRoomMap = m
                }
            }
        }
    }
}

/** Register the room store. Called once from server startup. */
fun registerRoomStores() {
    Stores.register(roomStore)
    Stores.resident[Room::class] = roomStore
}
