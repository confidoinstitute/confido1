package extensions.pseudonyms

import kotlinx.serialization.*
import tools.confido.extensions.*

val PseudonymsListKey = ExtensionDataKeyWithDefault<List<String>>("pseudonyms_list", emptyList())
val PseudonymAssignmentsKey = ExtensionDataKeyWithDefault<Map<String, String>>("pseudonym_assignments", emptyMap())

open class PseudonymsExtension : Extension {
    override val extensionId = "pseudonyms"
    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "RoomEDT" -> {
                edt.add(PseudonymsListKey)
                edt.add(PseudonymAssignmentsKey)
            }
        }
    }
}
