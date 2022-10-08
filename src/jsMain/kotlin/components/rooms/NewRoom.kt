package components.rooms

import csstype.number
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.*
import react.dom.onChange
import utils.byTheme
import utils.eventValue

val NewRoom = FC<Props> {
    var name by useState("New room…")
    var inputName by useState("")
    var inputDescription by useState("")
    var inputPrivate by useState(false)
    useEffect(inputName) {
        name = inputName.ifEmpty { "New room…" }
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
        label = ReactNode("Room name")
        size = Size.medium
        this.onChange = {
            inputName = it.eventValue()
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
        size = Size.medium
        this.onChange = {
            inputDescription = it.eventValue()
        }
    }

    FormGroup {
        FormControlLabel {
            label = ReactNode("Private room")
            control = Checkbox.create {
                checked = inputPrivate
            }
            onChange = { _, value -> inputPrivate = value }
        }
        Button {
            +"Create"
            color = ButtonColor.primary
            variant = ButtonVariant.contained
        }
    }
}
