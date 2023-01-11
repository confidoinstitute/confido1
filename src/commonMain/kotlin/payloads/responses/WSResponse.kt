package payloads.responses

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

class WSResponseSerializer<T>(private val dataSerializer: KSerializer<T>) : KSerializer<WSResponse<T>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WSResponse", dataSerializer.descriptor) {
        element<String>("type", isOptional = true)
        element("data", dataSerializer.descriptor, isOptional = true)
        element<String>("errType", isOptional = true)
        element<String>("message", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: WSResponse<T>) = encoder.encodeStructure(descriptor) {
        when(value) {
            is WSData<T> -> {
                encodeStringElement(descriptor, 0, "ok")
                encodeSerializableElement(descriptor, 1, dataSerializer, value.data)
            }
            is WSError<T> -> {
                encodeStringElement(descriptor, 0, "err")
                encodeStringElement(descriptor, 2, value.errType.name)
                encodeStringElement(descriptor, 3, value.message)
            }
            is WSLoading<T> -> {
            }
        }
    }

    override fun deserialize(decoder: Decoder): WSResponse<T> = decoder.decodeStructure(descriptor) {
        var type: String? = null
        var data: T? = null
        var errType: WSErrorType? = null
        var message: String? = null
        while (true) {
            when(val index = decodeElementIndex(descriptor)) {
                0 -> type = decodeStringElement(descriptor, 0)
                1 -> data = decodeSerializableElement(descriptor, 1, dataSerializer)
                2 -> errType = WSErrorType.valueOf(decodeStringElement(descriptor, 2))
                3 -> message = decodeStringElement(descriptor, 3)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected index: $index")
            }
        }
        when(type) {
            "ok" -> WSData(data!!)
            "err" -> WSError(errType!!, message!!)
            null -> WSLoading()
            else -> error("Unknown type for WSResponse")
        }
    }
}

@Serializable(with = WSResponseSerializer::class)
sealed class WSResponse<out T> {
    abstract val data: T?
}

@Serializable
class WSLoading<out T> : WSResponse<T>() {
    override val data: T? = null
}

@Serializable
class WSData<out T>(override val data: T, val stale: Boolean = false) : WSResponse<T>()

@Serializable
enum class WSErrorType {
    UNAUTHORIZED,
    NOT_FOUND,
    BAD_REQUEST,
    DISCONNECTED,
    INTERNAL_ERROR;

    val prettyName get() = when(this) {
        UNAUTHORIZED -> "Not Authorized"
        NOT_FOUND -> "Not Found"
        BAD_REQUEST -> "Bad Request"
        INTERNAL_ERROR -> "Internal Error"
        DISCONNECTED -> "Disconnected"
    }
}

@Serializable
class WSError<out T>(val errType: WSErrorType, val message: String) : WSResponse<T>() {
    val prettyMessage get() = "${errType.prettyName}: $message"
    override val data: T? = null
}