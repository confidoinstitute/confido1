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
import tools.confido.distributions.ProbabilityDistribution

external interface DistributionSummaryProps : Props {
    var spoiler: Boolean
    var distribution: ProbabilityDistribution?
    var allowPlotDialog: Boolean
}

val DistributionSummary = FC<DistributionSummaryProps> {props ->
    var shown by useState(!props.spoiler)
    var open by useState(false)

    when(shown) {
        false -> Button {
            +"Show"
            size = Size.small
            onClick = { shown = true }
        }
        true -> props.distribution?.let {
            +props.distribution!!.description
            if (props.allowPlotDialog) {
                IconButton {
                    size = Size.small
                    onClick = {open = true}
                    BarChart {}
                }
            }
        } ?: +"(no prediction)"
    }

    if (props.allowPlotDialog && props.distribution != null) {
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
