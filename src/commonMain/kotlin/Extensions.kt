package tools.confido.extensions

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.serializer
import tools.confido.state.PresenterView
import tools.confido.state.appConfig
import kotlin.reflect.KType
import kotlin.reflect.typeOf

expect val registeredExtensions: List<Extension>

interface IExtensionDataKey {
    val id: String
}
open class ExtensionDataKey<V>(override  val id: String) : IExtensionDataKey {
}

class ExtensionDataType(val name: String) {
    val valueSerializers: MutableMap<IExtensionDataKey, KSerializer<Any?>> = mutableMapOf()
    init {
        Extension.forEach {
            it.registerEdtKeys(this)
        }
    }
}
inline fun <K: ExtensionDataKey<V>, reified V> ExtensionDataType.add(k: K) {
    valueSerializers[k] = serializer(typeOf<V>())
}

class ExtensionData(val type: ExtensionDataType, val data: Map<IExtensionDataKey, Any?> = emptyMap()) {

}
inline fun <V> ExtensionData.with(k: ExtensionDataKey<V>, v: V) = ExtensionData(type, data + mapOf(k to v))
operator inline fun <V> ExtensionData.get(k: ExtensionDataKey<V>): V? = data[k] as? V?

abstract class  ExtensionDataSerializer(val type: ExtensionDataType): KSerializer<ExtensionData> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("ExtensionData:${type.name}") {
            type.valueSerializers.entries.sortedBy { it.key.id  }.forEach {
                element(it.key.id, it.value.descriptor)
            }
        }

    override fun serialize(encoder: Encoder, value: ExtensionData) {
        if (value.type != type) throw Exception("EDT mismatch")
        encoder.encodeStructure(descriptor) {
            val keys = type.valueSerializers.keys.sortedBy { it.id }
            value.data.entries.forEach { (k,v) ->
                val serializer = type.valueSerializers[k] ?: throw  Exception("Key ${k.id} not registered for EDT ${type.name} ${type.valueSerializers.size}")
                encodeSerializableElement(descriptor, keys.indexOf(k), serializer, v)
            }
        }
    }

    override fun deserialize(decoder: Decoder): ExtensionData {
        val m = mutableMapOf<IExtensionDataKey, Any?>()
        decoder.decodeStructure(descriptor) {
            while (true) {
                val index = decodeElementIndex(descriptor)
                val keys = type.valueSerializers.keys.sortedBy { it.id }

                if (index == CompositeDecoder.DECODE_DONE) break
                else if (index >= keys.size)  error("Unexpected index: $index")
                else m[keys[index]] = decodeSerializableElement(descriptor, 0, type.valueSerializers[keys[index]]!!)
            }
        }
        return ExtensionData(type, m)
    }
}


interface Extension {
    fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
    }

    fun registerEdtKeys(edt: ExtensionDataType) {}

    val extensionId: String
    companion object {
        val enabled by lazy { registeredExtensions.filter { it.extensionId in appConfig.enabledExtensionIds } }
        inline fun forEach(f: (Extension)->Unit) {  enabled.forEach(f) }
    }
}
