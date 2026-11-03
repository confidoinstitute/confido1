package tools.confido.web.components.questions.predictions

import web.html.HTMLElement
import react.*
import tools.confido.distributions.*
import tools.confido.question.Question
import tools.confido.spaces.*

external interface PredictionInputProps : Props {
    var space: Space
    var dist: ProbabilityDistribution?
    var resolution: Value?
    var question: Question?
    var onChange: ((ProbabilityDistribution, Boolean) -> Unit)?
    var disabled: Boolean?
}

val PredictionInput = FC<PredictionInputProps> { props->
    // Try extensions first
    tools.confido.extensions.ClientExtension.forEach { ext ->
        ext.renderPredictionInput(props)?.let {
            +it
            return@FC

        }
    }
    val space = props.space
    when (space) {
        is NumericSpace -> NumericPredInput {
                +props
            }
        is BinarySpace -> BinaryPredInput {
            +props
        }
        else -> +"Not implemented"
    }
}

external interface BasePredictionGraphProps : PropsWithRef<HTMLElement>, PropsWithClassName {
    // HACK: Need 'val' here to allow subclasses to narrow down the type
    val space: Space
    val dist: ProbabilityDistribution?
    val resolution: Value?
    var isGroup: Boolean
    var isInput: Boolean?
    var question: Question?
    var interactive: Boolean? // =true
    var onHistogramButtonClick: (() -> Unit)?
    /** A historical distribution drawn as an outline over the current one (the
     *  timeline's scrub ghost), labeled by [ghostLabel] ("as of <date>"). Only the
     *  numeric graph renders it. */
    var ghostDist: ProbabilityDistribution?
    var ghostLabel: String?
    /** When set, the ghost chip shows a dismiss button (pinned ghosts only —
     *  transient hover ghosts dismiss themselves). */
    var onGhostDismiss: (() -> Unit)?
    /** Value-axis view (numeric graphs): a content range to start zoomed to, and a
     *  report of the visible range while zoomed (null at full range). */
    var initialView: ClosedFloatingPointRange<Double>?
    var onViewChange: ((ClosedFloatingPointRange<Double>?) -> Unit)?
}

external interface PredictionGraphProps : Props, BasePredictionGraphProps {
    override var space: Space
    override var dist: ProbabilityDistribution?
    override var resolution: Value?
}

val PredictionGraph = FC<PredictionGraphProps>("PredictionGraph") { props->
    val space = props.space
    when (space) {
        // AGGREGATE_ONLY aggregates arrive as quantile summaries (no density);
        // they get the interval renderer instead of the density graph.
        is NumericSpace -> (props.dist as? QuantileSummaryDistribution)?.let { qsd ->
            QuantileSummaryGraph {
                this.space = space
                this.dist = qsd
                this.resolution = props.resolution as? NumericValue
            }
        } ?: NumericPredGraph {
            +props
            this.preferredCICenter = this.dist?.median
        }
        is BinarySpace -> BinaryPrediction {
            +props
        }
    }
}
