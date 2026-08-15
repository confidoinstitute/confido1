package payloads.responses

import kotlinx.serialization.Serializable
import rooms.RoomRole
import rooms.RoomRoleRefSerializer
import users.UserPresentation

/**
 * One entry of the `room/<id>/members` topic payload: a room membership as presented
 * to a given viewer (PERSISTENCE_SYNC_REDESIGN.md §5.3 — identity is granted, not
 * leaked). Ordinary members receive only the [user] presentation (name, color,
 * isMyself); `user.userId` and [email] are populated only for viewers with
 * MANAGE_MEMBERS, whose management actions (role change, kick, mailto) need them.
 */
@Serializable
data class MembershipPayload(
    val user: UserPresentation,
    /** Stable room-scoped identifier for list reconciliation (React keys) — a digest,
     *  deliberately not joinable to anything outside this room's member list. */
    val rowKey: String,
    val email: String? = null,
    /** Whether the member is a full workspace user (vs. an invited guest) — drives
     *  the internal/guest grouping and guest-role restrictions in the member list. */
    val isProperUser: Boolean,
    @Serializable(with = RoomRoleRefSerializer::class)
    val role: RoomRole,
    val invitedVia: String? = null,
    val implied: Boolean = false,
    val impliedFrom: String? = null,
)
