package extensions.flow_automation

import tools.confido.extensions.Extension
import tools.confido.extensions.ExtensionDataKeyWithDefault
import tools.confido.extensions.ExtensionDataType
import tools.confido.extensions.add

val RoomFlowKey = ExtensionDataKeyWithDefault<String?>("flow", null)

open class FlowAutomationExtension: Extension {
    override val extensionId = "flow_automation"
    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "RoomEDT" -> {
                edt.add(RoomFlowKey)
            }
        }
    }
}
