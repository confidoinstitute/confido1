package tools.confido.question

import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.spaces.*
import tools.confido.utils.binRanges

@Serializable
data class Question(
    @SerialName("_id")
    val id: String,
    val name: String,
    val answerSpace: Space,
    var visible: Boolean = true,
    var enabled: Boolean = true,
    var predictionsVisible: Boolean = false,
    var resolved: Boolean = false,
)

