package tools.confido.state

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import kotlinx.serialization.Serializable
import rooms.RoomPermission
import rooms.RoomRole
import tools.confido.extensions.Extension
import tools.confido.extensions.registeredExtensions
import java.io.File

/**
 * TOML-level representation of a single role definition.
 * The [inherit] field and [allPermissions] flag are resolved into a flat
 * [RoomRole] after all roles are parsed.
 */
@Serializable
private data class TomlRoleEntry(
    val id: String,
    val name: String? = null,
    val permissions: List<String> = emptyList(),
    val inherit: String? = null,
    val available_to_guests: Boolean? = null,
    val all_permissions: Boolean? = null,
)

/**
 * Top-level TOML document structure: a list of `[[role]]` entries.
 */
@Serializable
private data class TomlRolesConfig(
    val role: List<TomlRoleEntry> = emptyList(),
)

private val toml = Toml(
    inputConfig = TomlInputConfig(
        ignoreUnknownNames = true,
    )
)

/**
 * Load role definitions by merging built-in defaults with a TOML config file.
 *
 * Built-in roles (viewer, forecaster, moderator, owner) are always present.
 * The config file can:
 * - **Override** a built-in role (same `id`) to change its name, permissions, etc.
 * - **Add** new roles (new `id`) which are appended after the built-in ones.
 * - Use `inherit` to reference any role (built-in or previously defined).
 *
 * The file path is determined by the `CONFIDO_ROLES_FILE` environment variable,
 * defaulting to `roles.toml` in the working directory.
 * If the file does not exist, only built-in defaults are used.
 *
 * @param enabledExtensionIds The set of enabled extension IDs, passed directly
 *   to avoid a circular dependency (this is called during [AppConfig] construction,
 *   before [appConfig] is assigned).
 */
fun loadRoles(enabledExtensionIds: Set<String>): List<RoomRole> {
    // Ensure extension permissions are registered before loading roles.
    // We cannot use Extension.forEach here because it reads appConfig.enabledExtensionIds,
    // but appConfig is still being constructed at this point.
    registeredExtensions
        .filter { it.extensionId in enabledExtensionIds }
        .forEach { it.registerPermissions() }

    val builtins = builtinDefaultRoles()

    val filePath = System.getenv("CONFIDO_ROLES_FILE") ?: "roles.toml"
    val file = File(filePath)

    return if (file.exists()) {
        val content = file.readText()
        val config = toml.decodeFromString(TomlRolesConfig.serializer(), content)
        mergeRoles(builtins, config.role)
    } else {
        builtins
    }
}

/**
 * Merge built-in roles with config-defined entries.
 *
 * Config entries with the same [TomlRoleEntry.id] as a built-in role override it.
 * Config entries with new IDs are appended in the order they appear in the config.
 * The `inherit` field can reference any role (built-in or config-defined) that
 * appears before it in the resolved order.
 */
private fun mergeRoles(
    builtins: List<RoomRole>,
    configEntries: List<TomlRoleEntry>,
): List<RoomRole> {
    // Start with built-in roles as the base (preserving their order)
    val resolved = linkedMapOf<String, RoomRole>()
    for (role in builtins) {
        resolved[role.id] = role
    }

    // Track which built-in IDs were overridden
    val overriddenIds = mutableSetOf<String>()

    // Process config entries: override existing or append new
    for (entry in configEntries) {
        val existing = resolved[entry.id]
        val isOverride = existing != null
        if (isOverride) overriddenIds += entry.id

        // Resolve inherited permissions
        val inheritedPermissions = if (entry.inherit != null) {
            val parent = resolved[entry.inherit]
                ?: error("Role '${entry.id}' inherits from '${entry.inherit}', which is not defined before it.")
            parent.effectivePermissions
        } else if (isOverride && entry.permissions.isEmpty() && entry.inherit == null) {
            // When overriding without specifying permissions or inherit,
            // keep the original permissions
            existing!!.permissions
        } else {
            emptySet()
        }

        // Resolve own permissions from string IDs
        val ownPermissions = entry.permissions.map { permId ->
            RoomPermission.byId(permId)
                ?: error("Role '${entry.id}' references unknown permission '$permId'. " +
                        "Registered permissions: ${RoomPermission.all().map { it.id }}")
        }.toSet()

        // For overrides, use existing values as defaults for unspecified fields
        val role = if (isOverride) {
            existing!!.copy(
                name = entry.name ?: existing.name,
                permissions = if (entry.permissions.isNotEmpty() || entry.inherit != null)
                    inheritedPermissions + ownPermissions
                else
                    existing.permissions,
                availableToGuests = entry.available_to_guests ?: existing.availableToGuests,
                allPermissions = entry.all_permissions ?: existing.allPermissions,
            )
        } else {
            RoomRole(
                id = entry.id,
                name = entry.name ?: error("New role '${entry.id}' must specify a name."),
                permissions = inheritedPermissions + ownPermissions,
                availableToGuests = entry.available_to_guests ?: false,
                allPermissions = entry.all_permissions ?: false,
            )
        }

        resolved[entry.id] = role
    }

    return resolved.values.toList()
}

/**
 * Built-in default roles matching the original hardcoded definitions.
 */
fun builtinDefaultRoles(): List<RoomRole> {
    val viewerPerms = setOf(
        RoomPermission.VIEW_QUESTIONS,
        RoomPermission.VIEW_QUESTION_COMMENTS,
        RoomPermission.VIEW_ROOM_COMMENTS,
        RoomPermission.VIEW_ALL_GROUP_PREDICTIONS,
        RoomPermission.VIEW_GROUP_CALIBRATION,
    )
    val forecasterPerms = viewerPerms + setOf(
        RoomPermission.SUBMIT_PREDICTION,
        RoomPermission.POST_ROOM_COMMENT,
        RoomPermission.POST_QUESTION_COMMENT,
    )
    val moderatorPerms = forecasterPerms + setOf(
        RoomPermission.ADD_QUESTION,
        RoomPermission.VIEW_HIDDEN_QUESTIONS,
        RoomPermission.VIEW_ALL_GROUP_PREDICTIONS,
        RoomPermission.VIEW_ALL_RESOLUTIONS,
        RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS,
        RoomPermission.MANAGE_QUESTIONS,
        RoomPermission.MANAGE_MEMBERS,
        RoomPermission.CREATE_INVITE_LINK,
        RoomPermission.VIEW_ALL_INVITE_TOKENS,
        RoomPermission.VIEW_GROUP_CALIBRATION,
    )

    return listOf(
        RoomRole(id = "viewer", name = "Observer", permissions = viewerPerms, availableToGuests = true),
        RoomRole(id = "forecaster", name = "Forecaster", permissions = forecasterPerms, availableToGuests = true),
        RoomRole(id = "moderator", name = "Moderator", permissions = moderatorPerms),
        RoomRole(id = "owner", name = "Owner", allPermissions = true),
    )
}
