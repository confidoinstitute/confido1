package components.redesign.questions

import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.elementSizeWrapper
import csstype.Position
import csstype.px
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useState
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.spaces.NumericSpace


external interface NumericPredInputProps : Props {
    var space: NumericSpace
    var dist: ContinuousProbabilityDistribution?
}

external interface NumericPredSliderProps : NumericPredInputProps, PropsWithElementSize {
    var xZoomFactor: Double?
    var xPan: Double?
}

val NumericPredSlider = elementSizeWrapper(FC<NumericPredSliderProps> { props->
    div {
        css {
            height = 40.px
            position = Position.relative
        }
    }
    val zoomManager = SpaceZoomManager(props.space, props.elementWidth, props.xZoomFactor ?: 1.0, props.xPan ?: 0.0)
})

val NumericPredInput = FC<NumericPredInputProps> { props->
    var xZoomFactor by useState(1.0)
    var xPan by useState(0.0)
    NumericPredGraph {
        space = props.space
        dist = props.dist
        onZoomChange = { newZoomFactor, newPan -> xZoomFactor = newZoomFactor; xPan = newPan; }
    }
    NumericPredSlider {
        +props
        this.xZoomFactor = xZoomFactor
        this.xPan = xPan
    }

}