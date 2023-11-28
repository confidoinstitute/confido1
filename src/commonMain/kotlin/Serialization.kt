package tools.confido.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import tools.confido.distributions.distributionsSM
import tools.confido.utils.List2
import tools.confido.utils.endpoints


class RangeSerializer<T: Comparable<T>>(private val elementSerializer: KSerializer<T>) : KSerializer<ClosedRange<T>> {
    override val descriptor: SerialDescriptor = ListSerializer(elementSerializer).descriptor
    override fun serialize(encoder: Encoder, value: ClosedRange<T>) {
        encoder.encodeSerializableValue(ListSerializer(elementSerializer), value.endpoints)
    }
    override fun deserialize(decoder: Decoder): ClosedRange<T> {
        val lst = decoder.decodeSerializableValue(ListSerializer(elementSerializer))
        return lst[0]..lst[1]
    }
}

val confidoSM = distributionsSM // here we can add other SMs


val confidoJSON = Json {
    allowSpecialFloatingPointValues = true // support e.g. infinite ranges
    serializersModule = confidoSM
    ignoreUnknownKeys = true
}