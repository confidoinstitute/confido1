package tools.confido.rooms

import kotlinx.serialization.Serializable
import tools.confido.refs.Ref
import tools.confido.users.User

/**
 * A named moment on the prediction timeline ("Q2 review", "announcement", ...),
 * stored embedded on its owning [Room] or [tools.confido.question.Question] —
 * marks are few, live and die with their owner, and are referenced by name, so
 * they are a property rather than an entity. All marks are visible to whoever
 * can see the owning entity; the update_scatter_plot extension's private
 * snapshots remain a separate, extension-local feature.
 */
@Serializable
data class TimelineMark(
    /** Stable id for edit/delete targeting within the list. */
    val id: String,
    val ts: Int,
    val name: String,
    val owner: Ref<User>? = null,
)
