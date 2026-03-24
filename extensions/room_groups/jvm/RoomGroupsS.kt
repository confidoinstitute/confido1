package extensions.room_groups

import rooms.*
import tools.confido.application.routes.*
import tools.confido.extensions.*
import tools.confido.refs.ref
import tools.confido.state.*

object RoomGroupsSE : ServerExtension, RoomGroupsExtension() {

    /** Find all rooms that are children of the given group room. */
    private fun childrenOf(groupId: String): List<Room> =
        serverState.rooms.values.filter { it.parentRoomId == groupId }

    // --- Extension hooks ---

    override fun effectiveMembers(room: Room): List<RoomMembership>? {
        val parentId = room.parentRoomId ?: return null
        val parent = serverState.rooms[parentId] ?: return null

        val directUserIds = room.members.map { it.user.id }.toSet()
        val impliedMembers = parent.members
            .filter { it.user.id !in directUserIds }
            .map { it.copy(implied = true, impliedFrom = parent.name) }

        return room.members + impliedMembers
    }

    override fun effectivePermissions(room: Room, membership: RoomMembership): Set<RoomPermission>? {
        if (!room.isGroup) return null

        // Group rooms don't have questions -- strip all question-related permissions
        val stripped = setOf(
            RoomPermission.VIEW_QUESTIONS,
            RoomPermission.VIEW_HIDDEN_QUESTIONS,
            RoomPermission.VIEW_QUESTION_COMMENTS,
            RoomPermission.POST_QUESTION_COMMENT,
            RoomPermission.ADD_QUESTION,
            RoomPermission.SUGGEST_QUESTION,
            RoomPermission.SUBMIT_PREDICTION,
            RoomPermission.MANAGE_QUESTIONS,
            RoomPermission.VIEW_ALL_GROUP_PREDICTIONS,
            RoomPermission.VIEW_ALL_RESOLUTIONS,
            RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS,
            RoomPermission.VIEW_GROUP_CALIBRATION,
        )
        return membership.role.effectivePermissions - stripped
    }

    override fun effectiveQuestions(room: Room): List<EffectiveQuestion>? {
        if (!room.isGroup) return null

        return childrenOf(room.id).flatMap { child ->
            child.questions.map { qRef ->
                EffectiveQuestion(
                    question = qRef,
                    sourceRoom = child.ref,
                    visibleInList = false,       // don't show child questions in group's question list
                    includedInScoring = true,    // include in aggregated group scoreboard
                )
            }
        }
    }

    // --- Lifecycle ---

    override suspend fun serverInit() {
        // Validate ParentRoomKey changes before saving
        serverState.roomManager.beforeEntitySave { old, new ->
            val oldParent = old?.extensionData?.get(ParentRoomKey)
            val newParent = new.extensionData.get(ParentRoomKey)
            if (oldParent != newParent && newParent != null) {
                val group = serverState.rooms[newParent.id]
                    ?: badRequest("Group room not found.")
                if (!group.isGroup)
                    badRequest("Target room is not a group.")
                if (new.isGroup)
                    badRequest("A group room cannot be assigned to another group.")
            }
            new
        }

        // When a group room is deleted, unparent all its children
        serverState.roomManager.beforeEntityDelete { room ->
            if (room.isGroup) {
                for (child in childrenOf(room.id)) {
                    serverState.roomManager.modifyEntity(child.id) {
                        it.copy(extensionData = it.extensionData.with(ParentRoomKey, null))
                    }
                }
            }
        }
    }
}
