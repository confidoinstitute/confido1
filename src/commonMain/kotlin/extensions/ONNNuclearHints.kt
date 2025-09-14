package extensions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.extensions.Extension
import tools.confido.extensions.ExtensionDataKey
import tools.confido.extensions.ExtensionDataKeyWithDefault
import tools.confido.extensions.ExtensionDataType
import tools.confido.extensions.add
import tools.confido.extensions.get
import tools.confido.refs.Ref
import tools.confido.state.appConfig

abstract class ONNNuclearHintsExtension: Extension {
    override val extensionId = "onn_nuclear_hints"
    val nuclearRoom: Ref<Room>? by lazy { appConfig.extraData["nuclear_room"]?.let { Ref(it) } }
}
