package components
import components.rooms.RoomContext
import csstype.px
import csstype.rgb
import icons.GroupsIcon
import icons.TimelineIcon
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import mui.material.*
import mui.system.Box
import mui.system.sx
import payloads.responses.DistributionUpdate
import react.*
import react.dom.html.ReactHTML
import rooms.RoomPermission
import space.kscience.dataforge.values.asValue
import space.kscience.plotly.layout
import space.kscience.plotly.models.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Question
import tools.confido.spaces.*
import tools.confido.state.FeatureFlag
import tools.confido.state.appConfig
import tools.confido.state.clientState
import tools.confido.state.havePermission
import tools.confido.utils.Zdiv
import tools.confido.utils.randomString
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
        plotlyInit = {
            it.layout {
                yaxis {
                    if (space is BinarySpace) {
                        this.autorange = false
                        this.range(0.0..100.0)
                    }
                }
            }
        }
        fixupLayout = {
            if (space is BinarySpace) { // zooming on probability range does not make sense
                // FIXME: for some reason, this does not work, even though the parameter IS passed to plotly
                it.yaxis.fixedrange = true
            }
        }
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
                        line {
                            shape = LineShape.hv
                        }
                    },
                    Scatter {
                        x.set(xAxis)
                        y.set(trueUpdates.map {
                            1 - it.probs[0]
                        })
                        name = "No"
                        stackgroup = "values"
                        line {
                            shape = LineShape.hv
                        }
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
                        showlegend = false // the normalized density values have no meaning, no point in showing legend
                        colorscale = "YlGnBu".asValue()
                        name = "Distribution"
                    }
                )
            }
    }
}

external interface UpdatesButtonProps : Props {
    var disabled: Boolean
    var question: Question
}

val UpdatesButton = FC<UpdatesButtonProps> { props ->
    var open by useState(false)
    val room = useContext(RoomContext)
    var dialogKey by useState(randomString(20))

    Tooltip {
        val count = props.question.numPredictions
        title = if (count > 0)
            ReactHTML.span.create {
                +"${count} prediction${if (count>1) "s" else ""} made."
                if (false) { // TODO
                    ReactHTML.br()
                    +"Click to show prediction update history."
                }
            }
        else
            ReactNode("Nobody predicted yet")
        arrow = true
        // span is needed to show tooltip on disabled button (https://mui.com/material-ui/react-tooltip/#disabled-elements)
        ReactHTML.span {
            IconButton {
                disabled = props.disabled || count == 0 ||
                    !(room.havePermission(RoomPermission.VIEW_ALL_GROUP_PREDICTIONS) || props.question.groupPredVisible) ||
                        FeatureFlag.UPDATE_HISTORY !in appConfig.featureFlags // XXX: UX is not finalized, enable only in devmode
                Badge {
                    badgeContent = if (count > 0) ReactNode(count.toString()) else null
                    color = BadgeColor.secondary
                    TimelineIcon {}
                }
                onClick = {
                    dialogKey = randomString(20) // force refresh
                    open = true
                }
            }
        }
    }

    Dialog {
        this.open = open
        this.onClose = {_, _ -> open = false}
        fullWidth = true
        maxWidth = "xl"
        key = dialogKey
        DialogTitleWithCloseButton {
            +"Group prediction history"
            onClose = { open = false }
        }
        DialogContent {
            DialogContentText {
                +props.question.name
            }
            UpdatesPlot {
                this.question = props.question
            }
        }
    }
}
