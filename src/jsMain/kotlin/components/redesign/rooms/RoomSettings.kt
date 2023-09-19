package components.redesign.rooms

import components.AppStateContext
import components.redesign.basic.LinkUnstyled
import components.redesign.basic.Stack
import components.redesign.forms.*
import components.redesign.questions.dialog.EditQuestionDialogSchedule
import csstype.*
import emotion.react.css
import payloads.requests.BaseRoomInformation
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.p
import rooms.Room
import rooms.RoomColor
import tools.confido.question.QuestionSchedule

external interface RoomSettingsProps : Props {
    var room: Room?
    var onChange: ((BaseRoomInformation?) -> Unit)?
    var onSubmit: (() -> Unit)?
    var openSchedule: Boolean?
}

val RoomSettings = FC<RoomSettingsProps> { props ->
    val (appState, stale) = useContext(AppStateContext)

    var name by useState(props.room?.name ?: "")
    var description by useState(props.room?.description ?: "")
    var color by useState(props.room?.color ?: RoomColor.values().random())
    var icon by useState(props.room?.icon)
    var defaultSchedule by useState(props.room?.defaultSchedule ?: QuestionSchedule())
    var showSchedule by useState(props.openSchedule ?: false)
    var scheduleValid by useState(true)
    val valid = !name.isEmpty() && scheduleValid

    useEffect(name, description, color, icon, defaultSchedule.identify()) {
        if (valid)
            props.onChange?.invoke(BaseRoomInformation(name, description, color, icon, defaultSchedule))
        else
            props.onChange?.invoke(null)
    }

    Form {
        onSubmit = {
            if (valid) props.onSubmit?.invoke()
        }
        FormSection {
            FormField {
                title = "Room name"
                required = true
                TextInput {
                    placeholder = "Enter the room name"
                    value = name
                    onChange = { name = it.target.value }
                }
            }
            FormField {
                title = "Description"
                MultilineTextInput {
                    placeholder = "Explain what the questions in this room will be about"
                    value = description
                    onChange = { description = it.target.value }
                }
            }
            RoomColorChooser {
                this.color = color
                this.onChange = { color = it }
            }
            RoomIconChooser {
                this.color = color
                this.icon = icon
                this.onChange = {
                    icon = it
                }
            }
        }
        FormSection {
            if (defaultSchedule == QuestionSchedule() && !showSchedule) {
               a {
                   href = "#"
                   +"Configure default question schedule"
                   onClick = {
                       showSchedule = true
                       it.preventDefault()
                   }

               }
            } else {
                title = "Default question schedule (optional)"
                EditQuestionDialogSchedule {
                    this.schedule = defaultSchedule
                    onChange = { newSched, isError ->
                        defaultSchedule = newSched
                        scheduleValid = !isError
                    }
                }
            }
        }
        FormSection {
            Stack {
                Button {
                    type = ButtonType.submit
                    css {
                        fontWeight = integer(500)
                    }
                    if (props.room != null)
                        +"Save"
                    else
                        +"Create room"
                    disabled = (stale || !valid)
                }
            }
        }
    }
}

