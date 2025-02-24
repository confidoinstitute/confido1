package extensions.flow_automation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import tools.confido.extensions.Extension
import tools.confido.extensions.ExtensionDataKeyWithDefault
import tools.confido.extensions.ExtensionDataType
import tools.confido.extensions.add
import tools.confido.state.PresenterView

val RoomFlowKey = ExtensionDataKeyWithDefault<String?>("flow", null)

@Serializable
@SerialName("html")
data class HTML_PV(val html: String): PresenterView() {
    override fun describe() = "Inline HTML"
}

open class FlowAutomationExtension: Extension {
    override val extensionId = "flow_automation"
    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "RoomEDT" -> {
                edt.add(RoomFlowKey)
            }
        }
    }

    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(HTML_PV::class)
    }
}
