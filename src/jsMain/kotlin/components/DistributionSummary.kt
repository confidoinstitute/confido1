package components

import csstype.px
import icons.BarChart
import icons.GroupsIcon
import mui.material.*
import mui.system.Box
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.span
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ProbabilityDistribution

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
    var distribution: ProbabilityDistribution?
}

val GroupPredButton = FC<GroupPredButtonProps> { props ->
    var open by useState(false)

    Tooltip {
        //placement = TooltipPlacement.top
        title = if (props.count > 0)
            span.create {
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
                disabled = props.disabled || props.distribution == null
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
        }
        DialogContent {
            DialogContentText {
                +(props.distribution?.description ?: "(no predictions)")
            }
            props.distribution?.let {
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
        }
    }
}
