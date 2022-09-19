package components

import hooks.useElementSize
import kotlinext.js.asJsObject
import kotlinx.js.Object
import kotlinx.js.delete
import mui.material.Slider
import mui.material.SliderProps
import org.w3c.dom.HTMLSpanElement
import react.FC
import react.useEffect
import react.useMemo
import react.useState
import utils.jsObject

fun mark(value: Number, label: String?) = jsObject {
    this.value = value
    this.label = label
}

external interface MarkedSliderProps : SliderProps {
    override var valueLabelFormat: ((value: Number) -> String)?
    var widthToMarks: ((width: Double) -> List<Number>)?
}

val MarkedSlider = FC<MarkedSliderProps> {props ->
    val sliderSize = useElementSize<HTMLSpanElement>()

    val marks = useMemo(sliderSize.width, props.min, props.max) {
       (props.widthToMarks?.invoke(sliderSize.width) ?: utils.markSpacing(sliderSize.width, props.min?.toDouble() ?: 0.0, props.max?.toDouble() ?: 0.0)).map {
            value ->
                mark(value, props.valueLabelFormat?.invoke(value) ?: value.toString())
            }.toTypedArray()
    }

    Slider {
        Object.assign(this, props)
        delete(this.asDynamic().widthToMarks)

        ref = sliderSize.ref
        this.marks = marks
    }
}