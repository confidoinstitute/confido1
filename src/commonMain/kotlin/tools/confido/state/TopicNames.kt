package tools.confido.state

/**
 * Topic names of the multiplexed state websocket (PERSISTENCE_SYNC_REDESIGN.md §4.2).
 *
 * The scheme: root slices are bare plurals (`questions`, `users`, `rooms`) plus `self`;
 * entity-scoped topics live under a singular instance prefix (`room/<id>/…`,
 * `question/<id>/…` — built via `Room.topicPrefix` / `Question.topicPrefix`, which the
 * server also uses with `{rID}`/`{qID}` placeholders as registration patterns);
 * extension topics append `ext/<extensionId>/<name>` to their owning scope. Topic names
 * are a namespace of their own — they no longer mirror the HTTP routes or the retired
 * one-socket-per-subscription URLs.
 */
object TopicNames {
    const val QUESTIONS = "questions"
    const val USERS = "users"
    const val ROOMS = "rooms"
    const val SELF = "self"
    const val PRESENTER_TRACK = "presenter/track"
}
