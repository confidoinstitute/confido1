package components

import csstype.*
import dom.html.HTMLSpanElement
import hooks.useElementSize
import icons.EditIcon
import kotlinx.js.Object
import kotlinx.js.delete
import kotlinx.js.jso
import mui.material.*
import mui.material.Size
import mui.system.responsive
import mui.system.sx
import react.*

fun mark(value: Number, label: String?) = jso<dynamic> {
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

/**
 * Wrapper for a MUI slider for prediction input. It handles the following in addition to regular slider:
 * - automatically puts marks in reaction to the slider's range and available space
 * - when prediction is not made, tells user to make one
 * - adds a precise input button
 */
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
        preciseEditOpen = false
        if (value == props.value) return
        props.onChange?.invoke(null.asDynamic(), value, 0)
        props.onChangeCommitted?.invoke(null.asDynamic(), value)
    }

    Stack {
        direction = responsive(StackDirection.row)
        sx {
            alignItems = AlignItems.start
            if (props.madePrediction)
            marginRight = -32.px
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
        if (props.madePrediction)
            Tooltip {
                title = ReactNode("Input precise value")
                placement = TooltipPlacement.left
                arrow = true
                IconButton {
                    sx {
                        marginLeft = 4.px
                    }
                    disabled = props.disabled
                    size = Size.small
                    onClick = { preciseEditOpen = true }
                    EditIcon {
                        sx {
                            width = 18.px
                            height = 18.px
                        }
                    }
                }
            }
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
            this.disabled = props.disabled
            this.onCancel = {preciseEditOpen = false}
            this.onSubmit = ::setTemporaryValue
        }
    }
}