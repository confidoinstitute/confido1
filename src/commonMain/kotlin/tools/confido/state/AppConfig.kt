package tools.confido.state

import kotlinx.serialization.Serializable

enum class FeatureFlag {
    UPDATE_HISTORY,
    QUESTION_WRITER_ROLE,
}

@Serializable
data class AppConfig(
    val devMode: Boolean = false,
    val demoMode: Boolean = false,
    val betaIndicator: Boolean = false,
    val featureFlags: Set<FeatureFlag> = setOf(),
)