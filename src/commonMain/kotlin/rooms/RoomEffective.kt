package rooms

/**
 * The effective member list for this room, including implied (e.g. inherited) members.
 *
 * On the server, this is computed via [ServerExtension.effectiveMembers] hooks.
 * On the client, this reads [Room._effectiveMembers] which was populated by the server.
 * Falls back to [Room.members] if no extension overrides or no pre-computed value is available.
 */
expect val Room.effectiveMembers: List<RoomMembership>

/**
 * The effective permissions for a specific membership in this room.
 *
 * On the server, this is computed via [ServerExtension.effectivePermissions] hooks.
 * On the client, this reads [RoomMembership.effectivePermissions] which was populated by the server.
 * Falls back to [RoomMembership.role]'s effective permissions if no override is available.
 */
expect fun Room.memberEffectivePermissions(membership: RoomMembership): Set<RoomPermission>
