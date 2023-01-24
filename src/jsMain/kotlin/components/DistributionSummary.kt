package components

import components.presenter.PresenterButton
import csstype.pct
import csstype.px
import hooks.useWebSocket
import icons.BarChart
import icons.GroupsIcon
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.Box
import mui.system.sx
import payloads.responses.WSData
import payloads.responses.WSError
import react.*
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.span
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.state.GroupPredPV

external interface DistributionSummaryProps : Props {
    var distribution: ProbabilityDistribution?
    var allowPlotDialog: Boolean
}

val DistributionSummary = FC<DistributionSummaryProps> {props ->
    var open by useState(false)

    props.distribution?.let {
        +props.distribution!!.description
        if (props.allowPlotDialog && props.distribution != null && props.distribution !is BinaryDistribution) {
            IconButton {
                size = Size.small
                onClick = {open = true}
                BarChart {}
            }
        }
    } ?: +"(no prediction)"

    if (props.allowPlotDialog && props.distribution != null && props.distribution !is BinaryDistribution) {
        Dialog {
            this.open = open
            this.onClose = {_, _ -> open = false}
            Box {
                sx {
                    width = 500.px
                    height = 500.px
                    maxHeight = 500.px
                    maxWidth = 500.px
                }
                DistributionPlot {
                    distribution = props.distribution!!
                }
            }
        }
    }
}

external interface GroupPredButtonProps : Props {
    var count: Int
    var disabled: Boolean
    var question: Question
}

val GroupPredButton = FC<GroupPredButtonProps> { props ->
    var open by useState(false)

    val groupPredContent = useMemo(props.question) { FC<Props> {
        val response = useWebSocket<Prediction?>("/state/questions/${props.question.id}/group_pred")

        DialogContent {
            if (response is WSData) {
                val dist = response.data?.dist
                DialogContentText {
                    +(dist?.description ?: "(no predictions)")
                }
                dist?.let {
                    Box {
                        sx {
                            width = 500.px
                            height = 500.px
                            maxHeight = 500.px
                            maxWidth = 500.px
                        }
                        DistributionPlot {
                            distribution = it
                        }
                    }
                }
            } else {
                if (response is WSError) {
                    Alert {
                        severity = AlertColor.error
                        +response.prettyMessage
                    }
                }
                Typography {
                    variant = TypographyVariant.body1
                    Skeleton { sx { height = 100.pct } }
                }
                Skeleton {
                    variant = SkeletonVariant.rectangular
                    width = 500
                    height = 500
                }
            }
        }
    } }

    Tooltip {
        //placement = TooltipPlacement.top
        title = if (props.count > 0)
            Fragment.create {
                + "${props.count} ${if (props.count==1) "person" else "people"} predicted."
                if(!props.disabled) {
                    br()
                    + "Click to show group prediction."
                }
            }
        else
            ReactNode("Nobody predicted yet")
        arrow = true
        span {
            IconButton {
                disabled = props.disabled || props.count == 0
                onClick = { open = true }
                Badge {
                    badgeContent = if (props.count > 0) ReactNode(props.count.toString()) else null
                    color = BadgeColor.secondary
                    GroupsIcon {}
                }
            }
        }
    }

    Dialog {
        this.open = open
        this.onClose = {_, _ -> open = false}
        DialogTitle {
            +"Group prediction"
            DialogCloseButton {
                onClose = { open = false }
            }
        }
        groupPredContent {}
        DialogActions {
            PresenterButton {
                view = GroupPredPV(props.question.ref)
            }
        }
    }
}