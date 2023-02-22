package components.redesign.questions

import react.FC
import react.Props
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.spaces.NumericSpace


external interface NumericPredInputProps : Props {
    var space: NumericSpace
    var dist: ContinuousProbabilityDistribution?
}

val NumericPredInput = FC<NumericPredInputProps> { props->
    NumericPredGraph {
        space = props.space
        dist = props.dist
    }

}