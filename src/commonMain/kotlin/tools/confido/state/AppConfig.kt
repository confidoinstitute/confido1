package tools.confido.state

import kotlinx.serialization.Serializable
import rooms.RoomRole

enum class FeatureFlag {
    UPDATE_HISTORY,
    ENCOURAGE_COMMENTS,
}

val FeatureFlag.enabled : Boolean
    get() = this in appConfig.featureFlags

val DEFAULT_FEATURE_FLAGS = setOf(FeatureFlag.UPDATE_HISTORY)

@Serializable
data class AppConfig(
    val devMode: Boolean = false,
    val demoMode: Boolean = false,
    val betaIndicator: Boolean = false,
    val featureFlags: Set<FeatureFlag> = setOf(),
    val privacyPolicyUrl: String? = null,
    val enabledExtensionIds: Set<String> = setOf(),
    val predictionCoalesceInterval: Int = 60,
    val extraData: Map<String,String> = emptyMap(),
    val commentSpoiler: Boolean = false,
    /** Role definitions loaded from the roles config file. Order determines UI display order. */
    val roles: List<RoomRole> = emptyList(),
) {
    /** Look up a role by its string ID. */
    fun roleById(id: String): RoomRole? = roles.find { it.id == id }
}

expect val appConfig: AppConfig
