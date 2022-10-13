package tools.confido.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import tools.confido.distributions.distributionsSM


val confidoSM = distributionsSM // here we can add other SMs


val confidoJSON = Json {
    allowSpecialFloatingPointValues = true // support e.g. infinite ranges
    serializersModule = confidoSM
}