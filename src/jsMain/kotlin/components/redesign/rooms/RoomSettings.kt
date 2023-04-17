package components.redesign.rooms

import components.AppStateContext
import components.redesign.basic.Stack
import components.redesign.forms.*
import csstype.*
import emotion.react.css
import payloads.requests.BaseRoomInformation
import react.*
import react.dom.html.ButtonType
import rooms.Room
import utils.roomPalette

external interface RoomSettingsProps : Props {
    var room: Room?
    var onChange: ((BaseRoomInformation?) -> Unit)?
    var onSubmit: (() -> Unit)?
}

val RoomSettings = FC<RoomSettingsProps> { props ->
    val (appState, stale) = useContext(AppStateContext)

    var name by useState(props.room?.name ?: "")
    var description by useState(props.room?.description ?: "")

    useEffect(name, description) {
        if (name.isEmpty())
            props.onChange?.invoke(null)
        else
            props.onChange?.invoke(BaseRoomInformation(name, description))
    }

    Form {
        onSubmit = {
            props.onSubmit?.invoke()
        }
        FormSection {
            FormField {
                title = "Room name"
                required = true
                TextInput {
                    placeholder = "Enter the room name"
                    value = name
                    onChange = {name = it.target.value}
                }
            }
            FormField {
                title = "Description"
                MultilineTextInput {
                    placeholder = "Explain what the questions in this room will be about"
                    value = description
                    onChange = {description = it.target.value}
                }
                comment = "The description should contain all resolution criteria."
            }
            RoomColorChooser {
                palette = props.room?.id?.let {roomPalette(it)}
            }
            Stack {
                Button {
                    type = ButtonType.submit
                    css {
                        display = Display.block
                        margin = Margin(20.px, 20.px, 10.px)
                        fontWeight = integer(500)
                    }
                    if (props.room != null)
                        +"Save"
                    else
                        +"Create room"
                    disabled = (stale || name.isEmpty())
                }
            }
        }
    }
}

