package extensions

import components.presenter.PresenterPageProps
import components.presenter.presenterPageMap
import components.redesign.basic.Stack
import components.redesign.basic.rowStyleTableCSS
import components.redesign.basic.sansSerif
import components.redesign.forms.*
import components.redesign.layout.LayoutModeContext
import components.redesign.presenter.PresenterContext
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.redesign.questions.predictions.PredictionInputProps
import components.redesign.questions.predictions.SIDE_PAD
import csstype.*
import dom.html.HTML.div
import emotion.react.css
import hooks.useWebSocket
import icons.ProjectorScreenOutlineIcon
import payloads.responses.WSData
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import rooms.Room
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionContextPlace
import tools.confido.extensions.get
import tools.confido.extensions.with
import tools.confido.question.Prediction
import tools.confido.question.PredictionTerminology
import tools.confido.question.Question
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.spaces.NumericSpace
import utils.questionUrl

val PointEstimatePP = FC<PresenterPageProps<PointEstimatePV>> { props ->
    val view = props.view
    val question = view.question.deref() ?: return@FC

    val predictions = useWebSocket<List<PointEstimateWithUser>>("/api${questionUrl(question.id)}/ext/point_estimate/predictions.ws")
    if (predictions !is WSData) return@FC

    Stack {
        css {
            alignItems = AlignItems.center
            maxWidth = 100.vw
            width = 100.vw
            maxHeight = 100.vh
            height = 100.vh
            padding = 20.px
        }

        h1 {
            css {
                fontSize = 52.px
            }
            +question.name
        }

        table {
            css {
                fontSize = 32.px
                borderCollapse = BorderCollapse.collapse
                "tr" {
                    borderTop = Border(1.px, LineStyle.solid, NamedColor.black)
                    borderBottom = Border(1.px, LineStyle.solid, NamedColor.black)
                }
                "th, td" {
                    padding = Padding(8.px, 16.px)
                }
                "th" {
                    textAlign = TextAlign.left
                }
            }
            thead {
                tr {
                    th { +"User" }
                    th { +"Estimate" }
                }
            }
            tbody {
                predictions.data.sortedBy { it.value }.forEach { pred ->
                    tr {
                        css {
                            if (pred.isSpecial) {
                                fontWeight = FontWeight.bold
                                backgroundColor = Color("#e3f2fd")
                            }
                        }
                        td { +pred.nickname }
                        td { +"${pred.value}" }
                    }
                }
            }
        }
    }
}

private val PointEstimateInput = FC<PredictionInputProps>("PointEstimateInput") { props ->
    val terminology = props.question?.predictionTerminology ?:   PredictionTerminology.PREDICTION
    val space = props.space as NumericSpace
    val propDist = props.dist as? PointEstimateContinuousDistribution?
    var value by useState(propDist?.value)

    useEffect(props.dist?.identify()) {
        if (propDist != null && propDist.value != value) {
            value = propDist.value
        }
    }

    Stack {
        css {
            overflowX = Overflow.hidden
            gap = 10.px
            padding = Padding(10.px, 0.px)
        }

        PointEstimateSlider {
            this.space = space
            this.value = value
            this.disabled = props.disabled
            this.question = props.question
            this.onChange = { newValue, isCommit ->
                value = newValue
                val dist = PointEstimateContinuousDistribution(space, newValue)
                props.onChange?.invoke(dist, isCommit)
            }
        }

        Stack {
            css {
                padding = Padding(0.px, SIDE_PAD.px)
                alignItems = AlignItems.baseline
            }
            direction = FlexDirection.row
            div {
                +"Or enter ${terminology.term} manually: "
                css {
                    whiteSpace = WhiteSpace.nowrap
                    fontFamily = sansSerif
                    fontWeight = integer(600)
                    color = Color("#999")
                }
            }
            NumericInput {
                css {
                    flexGrow = number(1.0)
                    flexShrink = number(1.0)
                }
                this.value = value
                min = space.min
                max = space.max
                step = 0.1
                disabled = props.disabled
                placeholder = "Enter value"
                onChange = { newValue, error ->
                    if (error == null) {
                        value = newValue
                        newValue?.let { v ->
                            val dist = PointEstimateContinuousDistribution(space, v)
                            props.onChange?.invoke(dist, true)
                        }
                    }
                }
            }
        }
    }
}

object PointEstimateCE : ClientExtension, PointEstimateExtension() {
    override fun questionManagementExtra(room: Room, cb: ChildrenBuilder) {
        val presenterCtl = useContext(PresenterContext)
        val groupPredsWS = useWebSocket<Map<String, Prediction?>>("/state${room.urlPrefix}/group_pred")
        val groupPreds = groupPredsWS.data ?: emptyMap()

        val questionsWithPointEst = room.questions.reversed().mapNotNull { qRef ->
            qRef.deref()?.let { q ->
                if (q.extensionData[PointEstimateKey] && q.answerSpace is NumericSpace) q else null
            }
        }

        if (questionsWithPointEst.isNotEmpty()) {
            cb.apply {
                table {
                    css(ClassName("qmgmt-tab"), rowStyleTableCSS) {
                    }
                    thead {
                        tr {
                            th { +"Question" }
                            th { +"Group Estimate" }
                            th { +"# Predictors" }
                            th { +"Actions" }
                        }
                    }
                    tbody {
                        questionsWithPointEst.forEach { q ->
                            tr {
                                td { +q.name }
                                td {
                                    val groupPred = groupPreds[q.id]?.dist as? MultiPointEstimateContinuousDistribution
                                    groupPred?.let {
                                        +"${it.mean}"
                                    } ?: +"-"
                                }
                                td {
                                    +"${q.numPredictors}"
                                }
                                td {
                                    IconButton {
                                        ProjectorScreenOutlineIcon {}
                                        onClick = {
                                            presenterCtl.offer(PointEstimatePV(q.ref))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun registerPresenterPages() =
        mapOf(
            presenterPageMap(PointEstimatePP)
        )

    override fun renderPredictionInput(props: PredictionInputProps): ReactNode? {
        val question = props.question ?: return null
        if (!question.extensionData[PointEstimateKey]) return null
        if (props.space !is NumericSpace) return null
        return PointEstimateInput.create { +props }
    }

    @Suppress("UNCHECKED_CAST")
    override fun editQuestionDialogExtra(props: EditQuestionDialogProps, cb: ChildrenBuilder) {
        var pointEstimate by useContextState(
            ExtensionContextPlace.EDIT_QUESTION_DIALOG,
            "point_estimate",
            props.entity?.extensionData?.get(PointEstimateKey) ?: false
        )

        cb.apply {
            FormSection {
                title = "Point Estimate"
                FormField {
                    title = "Allow only point estimates"
                    comment = "If enabled, users can only input single values instead of probability distributions."
                    FormSwitch {
                        checked = pointEstimate
                        onChange = { e -> pointEstimate = e.target.checked }
                    }
                }
            }
        }
    }

    override fun assembleQuestion(q: Question, states: Map<String, dynamic>): Question {
        val pointEstimate = states["point_estimate"]
        return q.copy(extensionData = q.extensionData.with(PointEstimateKey, pointEstimate))
    }
}
