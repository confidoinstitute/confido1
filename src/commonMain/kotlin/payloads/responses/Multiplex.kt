package payloads.responses

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonElement

/**
 * Frames of the multiplexed state websocket (see PERSISTENCE_SYNC_REDESIGN.md §4.1).
 *
 * The client subscribes to topics identified by path-style strings (the URL paths of
 * the former one-socket-per-subscription endpoints, e.g. `/state/rooms/{rID}/comments`).
 * Commands carry batches: subscriptions issued in the same client tick travel as one
 * [TopicCommand], letting the server compute and deliver their initial payloads in a
 * single consistent frame.
 *
 * The server pushes [TopicFrame]s carrying one or more [TopicUpdate]s whose
 * [TopicUpdate.data] is the topic's payload encoded as `WSResponse<T>` — the same
 * envelope the dedicated sockets used to carry. A frame is applied atomically by the
 * client (all topic caches updated before any listener runs), so updates that the
 * server groups into one frame can never be observed half-applied.
 */
@Serializable
data class TopicCommand(val sub: List<String> = emptyList(), val unsub: List<String> = emptyList())

@Serializable
data class TopicUpdate(val topic: String, val data: JsonElement? = null)

@Serializable
data class TopicFrame(val updates: List<TopicUpdate> = emptyList())
