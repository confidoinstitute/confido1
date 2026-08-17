package hooks

import kotlinx.serialization.json.JsonElement
import tools.confido.payloads.responses.*
import react.useEffectWithCleanup
import react.useState
import tools.confido.serialization.confidoJSON
import tools.confido.utils.BoundTopic
import websocket.MultiplexWS

/**
 * Decoder for a bound topic's `WSResponse<P>` payloads, for [MultiplexWS.subscribe]'s
 * typed cache — the serializer travels in the [BoundTopic], so nothing is reified.
 * Never throws — decode failures become [WSError] values.
 */
fun <P> decodeTopicPayload(topic: BoundTopic<P>): (JsonElement) -> WSResponse<P> = { element ->
    try {
        // Deliberately decodeFromString, NOT decodeFromJsonElement: the streaming
        // decoder is the path every payload historically went through, while tree
        // decoding exercises less-tested branches of hand-written serializers
        // (it resolves element values by the index passed to
        // decodeSerializableElement, which streaming ignores — see the
        // ExtensionDataSerializer bug this surfaced). Conservative choice; the
        // stringify cost is negligible at these payload sizes.
        confidoJSON.decodeFromString(WSResponseSerializer(topic.serializer), element.toString())
    } catch (e: Exception) {
        console.error("Failed to decode payload of topic", topic.name, e.toString())
        WSError(WSErrorType.INTERNAL_ERROR, "Malformed payload")
    }
}

/**
 * Subscribe to a server topic on the multiplexed state websocket and receive its
 * continuously updated payload. The [BoundTopic] (obtained by invoking a
 * `Topics` spec with entity refs, e.g. `Topics.groupPred(question.ref)`) carries the
 * payload serializer, so the payload type is inferred and cannot diverge from what
 * the server sends. Subscriptions to the same topic share one server subscription
 * and one decoded payload cache.
 */
fun <P> useSubscription(topic: BoundTopic<P>?, clearOnStale: Boolean = false): WSResponse<P> {
    var value by useState<WSResponse<P>>(WSLoading())

    // A null topic subscribes to nothing (stays WSLoading) while keeping the hook
    // order stable — for callers whose subscription is conditional on a maybe-absent
    // ref (e.g. useQuestion(null)).
    useEffectWithCleanup(topic?.name, clearOnStale) {
        if (topic == null) {
            value = WSLoading()
            return@useEffectWithCleanup
        }
        val unsubscribe = MultiplexWS.subscribe(topic.name, decodeTopicPayload(topic)) { decoded, stale ->
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
