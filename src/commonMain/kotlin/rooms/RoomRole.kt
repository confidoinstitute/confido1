package rooms

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tools.confido.state.appConfig

/**
 * A permission that can be checked in authorization logic.
 *
 * Core permissions are registered as constants in the companion object.
 * Extensions can register additional permissions via [register] in their
 * common layer's `registerPermissions()` hook.
 */
class RoomPermission private constructor(val id: String, val description: String = "") {
    companion object {
        private val _registry = linkedMapOf<String, RoomPermission>()

        fun register(id: String, description: String = ""): RoomPermission {
            require(id !in _registry) { "Permission '$id' already registered" }
            return RoomPermission(id, description).also { _registry[id] = it }
        }

        fun byId(id: String): RoomPermission? = _registry[id]
        fun all(): Collection<RoomPermission> = _registry.values

        // Core permissions
        val VIEW_QUESTIONS = register("view_questions", "View questions in the room")
        val VIEW_QUESTION_COMMENTS = register("view_question_comments", "View comments on questions")
        val VIEW_ROOM_COMMENTS = register("view_room_comments", "View room-level comments")
        val POST_QUESTION_COMMENT = register("post_question_comment", "Post comments on questions")
        val POST_ROOM_COMMENT = register("post_room_comment", "Post room-level comments")
        val SUBMIT_PREDICTION = register("submit_prediction", "Submit predictions")
        val ADD_QUESTION = register("add_question", "Add questions to the room")
        val SUGGEST_QUESTION = register("suggest_question", "Suggest questions")
        val VIEW_HIDDEN_QUESTIONS = register("view_hidden_questions", "View hidden/draft questions")
        val VIEW_ALL_INVITE_TOKENS = register("view_all_invite_tokens", "View all invite link tokens")
        val CREATE_INVITE_LINK = register("create_invite_link", "Create invitation links")
        /** See all group predictions regardless of question setting. */
        val VIEW_ALL_GROUP_PREDICTIONS = register("view_all_group_predictions", "View all group predictions regardless of question setting")
        val VIEW_ALL_RESOLUTIONS = register("view_all_resolutions", "View hidden resolutions")
        val VIEW_INDIVIDUAL_PREDICTIONS = register("view_individual_predictions", "View individual predictions")
        val MANAGE_QUESTIONS = register("manage_questions", "Manage questions (edit, delete, reorder)")
        val MANAGE_MEMBERS = register("manage_members", "Manage room members")
        val MANAGE_COMMENTS = register("manage_comments", "Manage (edit/delete) other users' comments")
        val ROOM_OWNER = register("room_owner", "Full room owner privileges")
        val VIEW_GROUP_CALIBRATION = register("view_group_calibration", "View group calibration data")
    }

    override fun equals(other: Any?) = other is RoomPermission && id == other.id
    override fun hashCode() = id.hashCode()
    override fun toString() = "RoomPermission($id)"
}

/**
 * Serializer for [RoomPermission] that writes/reads the permission's string ID.
 */
object RoomPermissionSerializer : KSerializer<RoomPermission> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomPermission", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RoomPermission) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): RoomPermission {
        val id = decoder.decodeString()
        return RoomPermission.byId(id)
            ?: error("Unknown permission: '$id'. Registered permissions: ${RoomPermission.all().map { it.id }}")
    }
}

/**
 * A role definition that bundles a set of permissions under a name.
 *
 * Roles are defined in the TOML configuration file and loaded into [AppConfig.roles].
 * The [allPermissions] flag grants all registered permissions (used for the Owner role).
 */
@Serializable
data class RoomRole(
    val id: String,
    val name: String,
    val permissions: Set<@Serializable(with = RoomPermissionSerializer::class) RoomPermission> = emptySet(),
    val availableToGuests: Boolean = false,
    val allPermissions: Boolean = false,
) {
    /** The effective set of permissions, resolving [allPermissions]. */
    val effectivePermissions: Set<RoomPermission>
        get() = if (allPermissions) RoomPermission.all().toSet() else permissions

    fun hasPermission(permission: RoomPermission): Boolean {
        return permission in effectivePermissions
    }
}

/**
 * Serializer for [RoomRole] used in contexts where only the role ID should be stored
 * (e.g. [RoomMembership.role], [InviteLink.role]).
 *
 * Writes the role's string ID. On deserialization, reads a plain string and resolves
 * the role from [appConfig.roles].
 *
 * Old MongoDB documents that stored roles in the sealed-class format
 * (e.g. `{"type": "rooms.Viewer"}`) must be migrated to string IDs before
 * this serializer is used (see migration 001_role_format).
 */
object RoomRoleRefSerializer : KSerializer<RoomRole> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomRole", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RoomRole) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): RoomRole {
        val roleId = decoder.decodeString()
        return appConfig.roleById(roleId)
            ?: error("Unknown role ID: '$roleId'. Available roles: ${appConfig.roles.map { it.id }}")
    }
}

/**
 * Check whether [myRole] is allowed to assign [targetRole] to a member.
 *
 * Requires MANAGE_MEMBERS permission, and the target role's permissions must be
 * a subset of the assigner's permissions (you cannot grant more than you have).
 */
fun canChangeRole(myRole: RoomRole?, targetRole: RoomRole): Boolean {
    if (myRole == null) return false
    if (!myRole.hasPermission(RoomPermission.MANAGE_MEMBERS)) return false
    // Subset check: can only assign roles whose permissions you fully possess
    return targetRole.effectivePermissions.all { it in myRole.effectivePermissions }
}
