package extensions.pseudonyms

import rooms.Room
import rooms.RoomPermission
import tools.confido.application.UserPresentationContext
import tools.confido.extensions.*
import tools.confido.state.serverState
import tools.confido.utils.stringToColor
import users.User
import users.UserPresentation
import kotlin.random.Random

object PseudonymsSE : ServerExtension, PseudonymsExtension() {
    override suspend fun serverInit() {
        serverState.roomManager.beforeEntitySave { _, new ->
            recomputeAssignments(new)
        }
    }

    private fun recomputeAssignments(room: Room): Room {
        val rawList = room.extensionData[PseudonymsListKey]
        // Clean the list: trim, remove empty, deduplicate, sort alphabetically
        val cleanedList = rawList
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        if (cleanedList.isEmpty()) {
            // No pseudonyms configured — clear everything
            return room.copy(
                extensionData = room.extensionData
                    .without(PseudonymsListKey)
                    .without(PseudonymAssignmentsKey)
            )
        }

        val cleanedSet = cleanedList.toSet()
        val memberIds = room.members.map { it.user.id }.toSet()
        val oldAssignments = room.extensionData[PseudonymAssignmentsKey]

        // Keep existing assignments where user is still a member AND pseudonym is still in the list
        val keptAssignments = oldAssignments.filter { (userId, pseudonym) ->
            userId in memberIds && pseudonym in cleanedSet
        }

        val assignedPseudonyms = keptAssignments.values.toSet()
        val availablePseudonyms = cleanedList.filter { it !in assignedPseudonyms }.toMutableList()
        val unassignedMembers = memberIds.filter { it !in keptAssignments }.sorted()

        // Shuffle available pseudonyms with a deterministic seed based on room ID
        availablePseudonyms.shuffle(Random(room.id.hashCode()))

        // Assign first-come-first-served
        val newAssignments = keptAssignments.toMutableMap()
        for ((i, userId) in unassignedMembers.withIndex()) {
            if (i >= availablePseudonyms.size) break
            newAssignments[userId] = availablePseudonyms[i]
        }

        return room.copy(
            extensionData = room.extensionData
                .with(PseudonymsListKey, cleanedList)
                .with(PseudonymAssignmentsKey, newAssignments.toMap())
        )
    }

    override fun censorRoom(room: Room, user: User?): Room {
        // Only moderators/owners can see the userId-to-pseudonym assignment mapping
        if (room.hasPermission(user, RoomPermission.MANAGE_MEMBERS)) return room
        return room.copy(
            extensionData = room.extensionData.without(PseudonymAssignmentsKey)
        )
    }

    override fun getUserPresentation(
        user: User?,
        observer: User,
        context: UserPresentationContext,
        room: Room?,
    ): UserPresentation? {
        if (room == null || user == null) return null
        val pseudonymList = room.extensionData[PseudonymsListKey]
        if (pseudonymList.isEmpty()) return null

        val assignments = room.extensionData[PseudonymAssignmentsKey]
        val pseudonym = assignments[user.id]

        return if (pseudonym != null) {
            UserPresentation(
                isMyself = user.id == observer.id,
                displayName = pseudonym,
                color = stringToColor(pseudonym),
            )
        } else {
            // User has no pseudonym assigned (not enough pseudonyms) — show generic anonymous
            // to avoid leaking their real name
            UserPresentation(
                isMyself = user.id == observer.id,
                displayName = "Anonymous participant",
                color = stringToColor(room.id + user.id),
            )
        }
    }
}
