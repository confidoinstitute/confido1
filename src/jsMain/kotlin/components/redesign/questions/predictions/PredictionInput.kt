package components.redesign.questions.predictions

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

external interface BasePredictionGraphProps {
    // HACK: Need 'val' here to allow subclasses to narrow down the type
    val space: Space
    val dist: ProbabilityDistribution?
    val resolution: Value?
    var isGroup: Boolean
    var isInput: Boolean?
    var question: Question?
    var interactive: Boolean? // =true
    var onHistogramButtonClick: (() -> Unit)?
}

external interface PredictionGraphProps : Props, BasePredictionGraphProps {
    override var space: Space
    override var dist: ProbabilityDistribution?
    override var resolution: Value?
}

val PredictionGraph = FC<PredictionGraphProps>("PredictionGraph") { props->
    val space = props.space
    when (space) {
        is NumericSpace -> NumericPredGraph {
            +props
            this.preferredCICenter = this.dist?.median
        }
        is BinarySpace -> BinaryPrediction {
            +props
        }
    }
}