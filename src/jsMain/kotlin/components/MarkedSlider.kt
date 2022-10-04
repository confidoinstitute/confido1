package components

import hooks.useElementSize
import kotlinx.js.Object
import kotlinx.js.delete
import mui.material.Slider
import mui.material.SliderProps
import org.w3c.dom.HTMLSpanElement
import react.FC
import react.useMemo
import utils.jsObject

fun mark(value: Number, label: String?) = jsObject {
    this.value = value
    this.label = label
}

external interface MarkedSliderProps : SliderProps {
    override var valueLabelFormat: ((value: Number) -> String)?
    var widthToMarks: ((width: Double) -> List<Number>)?
    var madePrediction: Boolean
}

val MarkedSlider = FC<MarkedSliderProps> {props ->
    val sliderSize = useElementSize<HTMLSpanElement>()

    val marks = useMemo(sliderSize.width, props.min, props.max, props.madePrediction) {
       (props.widthToMarks?.invoke(sliderSize.width) ?: utils.markSpacing(sliderSize.width, props.min?.toDouble() ?: 0.0, props.max?.toDouble() ?: 0.0, props.valueLabelFormat)).map {
            value ->
                mark(value, props.valueLabelFormat?.invoke(value) ?: value.toString())
            }.toTypedArray()
    }

    val valueLabelFormat = useMemo(props.madePrediction) {
        if (props.madePrediction) props.valueLabelFormat else { _ -> "Make your prediction" }
    }

    Slider {
        Object.assign(this, props)
        delete(this.asDynamic().widthToMarks)
        delete(this.asDynamic().madePrediction)
        this.valueLabelFormat = valueLabelFormat
        this.track = if (props.madePrediction) "normal" else false.asDynamic()

        ref = sliderSize.ref
        this.marks = marks
    }
}