package extensions

import components.presenter.PresenterPageProps
import components.presenter.presenterPageMap
import components.redesign.basic.Stack
import components.redesign.basic.rowStyleTableCSS
import components.redesign.forms.IconButton
import components.redesign.presenter.PresenterContext
import csstype.*
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
import tools.confido.distributions.*
import tools.confido.extensions.ClientExtension
import tools.confido.question.Prediction
import tools.confido.refs.deref
import tools.confido.refs.ref
import utils.questionUrl
import extensions.shared.ValueWithUser

val PredictionShowcasePP = FC<PresenterPageProps<PredictionShowcasePV>> { props ->
    val view = props.view
    val question = view.question.deref() ?: return@FC

    val predictions = useWebSocket<List<ValueWithUser>>("/api${questionUrl(question.id)}/ext/prediction_showcase/predictions.ws")
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
                    th { +"Prediction" }
                }
            }
            tbody {
                predictions.data.forEach { pred ->
                    tr {
                        css {
                            if (pred.isSpecial) {
                                fontWeight = FontWeight.bold
                                backgroundColor = Color("#e3f2fd")
                            }
                        }
                        td { +pred.nickname }
                        td {
                            // Format as percentage if value is between 0 and 1, otherwise as a number
                            if (pred.value in 0.0..1.0) {
                                +"${(pred.value * 100).toInt()}%"
                            } else {
                                +pred.value.toString()
                            }
                        }
                    }
                }
            }
        }
    }
}

object PredictionShowcaseCE : ClientExtension, PredictionShowcaseExtension() {
    override fun questionManagementExtra(room: Room, cb: ChildrenBuilder) {
        val presenterCtl = useContext(PresenterContext)

        cb.apply {
            table {
                css(ClassName("qmgmt-tab"), rowStyleTableCSS) {
                }
                thead {
                    tr {
                        th { +"Question" }
                        th { +"# Predictors" }
                        th { +"Actions" }
                    }
                }
                tbody {
                    room.questions.reversed().forEach { qRef ->
                        val q = qRef.deref() ?: return@forEach
                        tr {
                            td { +q.name }
                            td { +"${q.numPredictors}" }
                            td {
                                IconButton {
                                    ProjectorScreenOutlineIcon {}
                                    onClick = {
                                        presenterCtl.offer(PredictionShowcasePV(q.ref))
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
            presenterPageMap(PredictionShowcasePP)
        )
}
