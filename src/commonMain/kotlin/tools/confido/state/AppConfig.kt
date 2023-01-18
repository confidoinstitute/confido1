package tools.confido.state

import kotlinx.serialization.Serializable

enum class FeatureFlag {
    UPDATE_HISTORY,
    QUESTION_WRITER_ROLE,
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
)

expect val appConfig: AppConfig