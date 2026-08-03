package payloads.responses

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonElement

/**
 * Frames of the multiplexed state websocket (see PERSISTENCE_SYNC_REDESIGN.md §4.1).
 *
 * The client subscribes to topics identified by path-style strings (the URL paths of
 * the former one-socket-per-subscription endpoints, e.g. `/state/rooms/{rID}/comments`).
 * The server pushes [TopicFrame]s whose [TopicFrame.data] is the topic's payload encoded
 * as `WSResponse<T>` — the same envelope the dedicated sockets used to carry.
 */
@Serializable
data class TopicCommand(val sub: String? = null, val unsub: String? = null)

@Serializable
data class TopicFrame(val topic: String, val data: JsonElement? = null)
