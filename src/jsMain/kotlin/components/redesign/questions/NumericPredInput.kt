package components.redesign.questions

import react.FC
import react.Props
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.spaces.NumericSpace


external interface NumericPredInputProps : Props {
    var space: NumericSpace
    var dist: ContinuousProbabilityDistribution?
}

external interface NumericPredSliderProps : NumericPredInputProps {
    var xZoomFactor: Double?
    var xPan: Double?
}

val NumericPredSlider = FC<NumericPredSliderProps> {

}

val NumericPredInput = FC<NumericPredInputProps> { props->
    NumericPredGraph {
        space = props.space
        dist = props.dist
    }

}