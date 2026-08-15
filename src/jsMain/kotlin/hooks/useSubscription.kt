package hooks

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import payloads.responses.*
import react.useEffectWithCleanup
import react.useState
import tools.confido.serialization.confidoJSON
import websocket.MultiplexWS

/**
 * Decoder for a topic's `WSResponse<T>` payloads, for [MultiplexWS.subscribe]'s typed
 * cache. Never throws — decode failures become [WSError] values.
 */
inline fun <reified T> decodeTopicPayload(topic: String): (JsonElement) -> WSResponse<T> = { element ->
    try {
        // Deliberately decodeFromString, NOT decodeFromJsonElement: the streaming
        // decoder is the path every payload historically went through, while tree
        // decoding exercises less-tested branches of hand-written serializers
        // (it resolves element values by the index passed to
        // decodeSerializableElement, which streaming ignores — see the
        // ExtensionDataSerializer bug this surfaced). Conservative choice; the
        // stringify cost is negligible at these payload sizes.
        confidoJSON.decodeFromString(element.toString())
    } catch (e: Exception) {
        console.error("Failed to decode payload of topic", topic, e.toString())
        WSError(WSErrorType.INTERNAL_ERROR, "Malformed payload")
    }
}

/**
 * Subscribe to a server topic on the multiplexed state websocket and receive its
 * continuously updated payload. Topics are the URL paths of the former dedicated
 * websocket endpoints (e.g. `"/state${'$'}{room.urlPrefix}/comments"`). Subscriptions
 * to the same topic share one server subscription and one decoded payload cache.
 */
inline fun <reified T> useSubscription(topic: String, clearOnStale: Boolean = false): WSResponse<T> {
    var value by useState<WSResponse<T>>(WSLoading())

    useEffectWithCleanup(topic, clearOnStale) {
        val unsubscribe = MultiplexWS.subscribe(topic, decodeTopicPayload<T>(topic)) { decoded, stale ->
            value = when {
                decoded == null -> WSLoading()
                !stale -> decoded
                decoded is WSData && !clearOnStale -> WSData(decoded.data, stale = true)
                else -> WSLoading()
            }
        }
        onCleanup { unsubscribe() }
    }
    return value
}
