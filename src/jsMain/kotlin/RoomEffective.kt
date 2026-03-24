package rooms

actual val Room.effectiveMembers: List<RoomMembership>
    get() = _effectiveMembers ?: this.members

actual fun Room.memberEffectivePermissions(membership: RoomMembership): Set<RoomPermission> {
    return membership.effectivePermissions ?: membership.role.effectivePermissions
}
