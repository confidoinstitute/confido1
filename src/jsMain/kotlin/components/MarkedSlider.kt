package components

import csstype.Position
import csstype.px
import hooks.useElementSize
import icons.EditIcon
import kotlinx.js.Object
import kotlinx.js.delete
import mui.material.*
import mui.system.sx
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLSpanElement
import org.w3c.xhr.FormData
import react.*
import react.dom.html.ButtonType
import react.dom.html.InputType
import react.dom.html.ReactHTML.form
import utils.jsObject

fun mark(value: Number, label: String?) = jsObject {
    this.value = value
    this.label = label
}

external interface MarkedSliderProps : SliderProps {
    override var valueLabelFormat: ((value: Number) -> String)?
    var widthToMarks: ((width: Double) -> List<Number>)?
    var madePrediction: Boolean
    var unit: String
    var preciseInputForm: FC<PreciseInputFormProps>
}

val MarkedSlider = FC<MarkedSliderProps> { props ->
    val sliderSize = useElementSize<HTMLSpanElement>()

    val marks = useMemo(sliderSize.width, props.min, props.max, props.madePrediction) {
        (props.widthToMarks?.invoke(sliderSize.width) ?: utils.markSpacing(
            sliderSize.width,
            props.min?.toDouble() ?: 0.0,
            props.max?.toDouble() ?: 0.0,
            props.valueLabelFormat
        )).map { value ->
            mark(value, props.valueLabelFormat?.invoke(value) ?: value.toString())
        }.toTypedArray()
    }

    val valueLabelFormat = useMemo(props.madePrediction) {
        if (props.madePrediction) props.valueLabelFormat else { _ -> "Make your prediction" }
    }

    var preciseEditOpen by useState(false)
    fun setTemporaryValue(value: Double) {
        props.onChange?.invoke(null.asDynamic(), value, 0)
        props.onChangeCommitted?.invoke(null.asDynamic(), value)
        preciseEditOpen = false
    }

    Dialog {
        open = preciseEditOpen
        onClose = {_, _ -> preciseEditOpen = false}
        DialogTitle {
            +"Exact prediction"
        }

        props.preciseInputForm {
            this.min = props.min
            this.max = props.max
            this.step = props.step
            this.value = props.value
            this.unit = props.unit
            this.onCancel = {preciseEditOpen = false}
            this.onSubmit = ::setTemporaryValue
        }
    }

    Slider {
        Object.assign(this, props)
        delete(this.asDynamic().widthToMarks)
        delete(this.asDynamic().madePrediction)
        delete(this.asDynamic().unit)
        delete(this.asDynamic().preciseInputForm)
        this.valueLabelFormat = valueLabelFormat
        this.track = if (props.madePrediction) "normal" else false.asDynamic()

        ref = sliderSize.ref
        this.marks = marks
    }
    IconButton {
        sx {
            position = Position.absolute
            right = 0.px
            marginRight = 2.px
        }
        size = Size.small
        onClick = {preciseEditOpen = true}
        EditIcon {
            sx {
                width = 18.px
                height = 18.px
            }
        }
    }
}