package components.rooms

import components.AppStateContext
import components.ClientAppState
import csstype.AlignItems
import csstype.JustifyContent
import csstype.Margin
import mui.material.*
import mui.system.responsive
import mui.system.sx
import payloads.requests.BaseRoomInformation
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.form
import react.dom.onChange
import rooms.Room
import utils.byTheme
import utils.eventValue
import utils.themed

external interface RoomInfoFormProps : Props {
    var room: Room?
    var editMode: Boolean
    var disabled: Boolean
    var onSubmit: ((BaseRoomInformation) -> Unit)?
    var onCancel: (() -> Unit)?
}

val RoomInfoForm = FC<RoomInfoFormProps> { props ->
    val stale = useContext(AppStateContext).stale

    val roomName = props.room?.name ?: "New room…"
    val editMode = props.room != null

    var errorName by useState(false)
    var inputName by useState(props.room?.name ?: "")
    var inputDescription by useState(props.room?.description ?: "")

    form {
        onSubmit = {
            it.preventDefault()
            if (!editMode && inputName.isEmpty()) {
                errorName = true
            } else {
                props.onSubmit?.invoke(BaseRoomInformation(inputName, inputDescription))
            }
        }

        TextField {
            sx {
                ".MuiInput-input,.MuiInputLabel-root" {
                    asDynamic().typography = byTheme("h2")
                }
                ".MuiInputLabel-shrink" {
                    asDynamic().typography = byTheme("body1")
                }
            }
            variant = FormControlVariant.standard
            fullWidth = true
            value = inputName
            if (props.room == null)
                label = ReactNode("Room name")
            placeholder = roomName
            disabled = props.disabled
            error = errorName
            size = Size.medium
            this.onChange = {
                inputName = it.eventValue()
                errorName = false
            }
        }
        TextField {
            variant = FormControlVariant.standard
            fullWidth = true
            value = inputDescription
            label = ReactNode("Description")
            multiline = true
            maxRows = 5
            rows = 5
            disabled = props.disabled
            size = Size.medium
            this.onChange = {
                inputDescription = it.eventValue()
            }
            margin = FormControlMargin.normal
        }
        if (editMode) {
            Stack {
                direction = responsive(StackDirection.row)
                spacing = responsive(1)
                sx {
                    justifyContent = JustifyContent.flexEnd
                }
                Button {
                    onClick = { props.onCancel?.invoke() }
                    disabled = props.disabled
                    +"Cancel"
                }
                Button {
                    type = ButtonType.submit
                    disabled = props.disabled || stale
                    +"Confirm"
                }
            }
        } else {
            FormGroup {
                Button {
                    type = ButtonType.submit
                    color = ButtonColor.primary
                    variant = ButtonVariant.contained
                    disabled = props.disabled || stale
                    +"Create"
                }
            }
        }
    }
}