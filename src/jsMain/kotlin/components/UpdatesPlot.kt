package components
import csstype.rgb
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import payloads.responses.DistributionUpdate
import react.*
import space.kscience.dataforge.values.asValue
import space.kscience.plotly.models.*
import tools.confido.question.Question
import tools.confido.spaces.*
import tools.confido.utils.Zdiv
import utils.toIsoDateTime
import utils.transposeForHeatmap
import kotlin.coroutines.EmptyCoroutineContext

external interface UpdatesPlotProps : Props {
    var question: Question
    var annotations: Map<Double, String>
}

val UpdatesPlot = FC<UpdatesPlotProps> {props ->
    var fetchError by useState(false)
    var updates by useState<List<DistributionUpdate>?>(null)
    useEffectOnce {
        CoroutineScope(EmptyCoroutineContext).launch {
            try {
                val response = Client.httpClient.get("/questions/${props.question.id}/updates") {
                    expectSuccess = true
                }
                val content = response.body<ByteArray>()
                updates = Cbor.decodeFromByteArray<List<DistributionUpdate>>(content)
            } catch(e: Throwable) {
                updates = null
                fetchError = true
            }
        }
    }

    val trueUpdates = updates ?: return@FC
    if (trueUpdates.isEmpty()) return@FC

    val xAxis = trueUpdates.map {
        it.ts.toIsoDateTime()
    }

    val space = props.question.answerSpace

    ReactPlotly {
        traces =
            when(space) {
                BinarySpace -> listOf(
                    Scatter {
                        x.set(xAxis)
                        y.set(trueUpdates.map {
                            it.probs[0]
                        })
                        name = "Yes"
                        stackgroup = "values"
                        groupnorm = GroupNorm.percent
                    },
                    Scatter {
                        x.set(xAxis)
                        y.set(trueUpdates.map {
                            1 - it.probs[0]
                        })
                        name = "No"
                        stackgroup = "values"
                    }
                )
                is NumericSpace -> listOf(
                    Scatter {
                        x.set(xAxis)
                        y.set(trueUpdates.map {it.mean})
                        line { 
                            shape = LineShape.hv
                            color("orange")
                        }
                        name = "Mean value"
                    },
                    Heatmap {
                        val zAxis = trueUpdates.map {
                            val maxVal = it.probs.maxOrNull() ?: 1
                            it.probs `Zdiv` maxVal
                        }.transposeForHeatmap()
                        console.log(zAxis)
                        x.set(xAxis)
                        y.set(Binner(space, 50).binBorders)
                        z.set(zAxis)
                        colorscale = "YlGnBu".asValue()
                        name = "Distribution"
                    }
                )
            }
    }
}