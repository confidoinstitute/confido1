package components

import hooks.useElementSize
import kotlinext.js.asJsObject
import kotlinx.js.Object
import kotlinx.js.delete
import mui.material.Slider
import mui.material.SliderProps
import org.w3c.dom.HTMLSpanElement
import react.FC
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

    val marks = props.widthToMarks?.let {
        it(sliderSize.width).map {value ->
            mark(value, props.valueLabelFormat?.invoke(value) ?: value.toString())
        }.toTypedArray()
    } ?: props.marks

    Slider {
        Object.assign(this, props)
        delete(this.asDynamic().widthToMarks)

        ref = sliderSize.ref
        this.marks = marks
    }
}