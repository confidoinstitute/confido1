package extensions.update_scatter_plot

import emotion.react.css
import react.FC
import react.dom.html.ReactHTML
import tools.confido.distributions.BinaryDistribution
import tools.confido.question.Prediction
import tools.confido.utils.List2
import web.cssom.Border
import web.cssom.BorderCollapse
import web.cssom.Color
import web.cssom.LineStyle
import web.cssom.TextAlign
import web.cssom.px

val UpdateScatterTable = FC<UpdateScatterPlotProps> { props->
    val preds = props.preds.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>>()
    val dists = preds.map { it.map { it.dist as? BinaryDistribution } }.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<BinaryDistribution>>>()
    val probs = dists.map { it.map { it.yesProb } }
    val diffs = probs.map { it[1] - it[0] }
    ReactHTML.table {
        css {
            fontSize = 22.px
            "td, th" {
                border = Border(1.px, LineStyle.solid, Color("#666"))
                padding = 4.px
            }
            "tbody td" {
                textAlign = TextAlign.right
            }
            "tbody th" {
                textAlign = TextAlign.left
            }
            borderCollapse = BorderCollapse.collapse
        }
        ReactHTML.thead {
            ReactHTML.tr {
                ReactHTML.th {
                    rowSpan = 2
                    +"Update size"
                }
                ReactHTML.th {
                    colSpan = 3
                    +"Update direction"
                }
            }
            ReactHTML.tr {
                ReactHTML.th { +"Up" }
                ReactHTML.th { +"Down" }
                ReactHTML.th { +"Total" }
            }
        }
        ReactHTML.tbody {
            ReactHTML.tr {
                ReactHTML.th { +"No change" }
                ReactHTML.td {}
                ReactHTML.td {}
                ReactHTML.td { +diffs.filter{ it == 0.0 }.size.toString() }
            }
            updateSizes.forEach { sz ->
                ReactHTML.tr {
                    ReactHTML.th { +sz.name }
                    val sizeGroup = diffs.filter{ it in sz }
                    val upGroup = sizeGroup.filter { it > 0.0 }
                    val downGroup = sizeGroup.filter { it < 0.0 }
                    ReactHTML.td {
                        css {
                            backgroundColor = Color(sz.colors[0])
                        }
                        +upGroup.size.toString()
                    }
                    ReactHTML.td {
                        css {
                            backgroundColor = Color(sz.colors[1])
                        }
                        +downGroup.size.toString()
                    }
                    ReactHTML.td { +sizeGroup.size.toString() }
                }
            }
            ReactHTML.tr {
                ReactHTML.th { +"Total" }
                val upGroup = diffs.filter { it > 0.0 }
                val downGroup = diffs.filter { it < 0.0 }
                ReactHTML.td { +upGroup.size.toString() }
                ReactHTML.td { +downGroup.size.toString() }
                ReactHTML.td { +diffs.size.toString() }
            }
        }
    }
}
