package components

import csstype.px
import icons.BarChart
import mui.material.Button
import mui.material.Dialog
import mui.material.IconButton
import mui.material.Size
import mui.system.Box
import mui.system.sx
import react.*
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
