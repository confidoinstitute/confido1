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
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.abbr
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import rooms.Room
import rooms.RoomColor
import rooms.ScoreboardMode
import rooms.ScoringConfig
import tools.confido.question.QuestionSchedule
import tools.confido.refs.deref

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
    var scoringConfig by useState(props.room?.scoring)
    var showScore by useState(scoringConfig != null)
    var scheduleValid by useState(true)
    val valid = !name.isEmpty() && scheduleValid

    useEffect(name, description, color, icon, defaultSchedule.identify(), scoringConfig?.identify()) {
        if (valid)
            props.onChange?.invoke(BaseRoomInformation(name, description, color, icon, defaultSchedule, scoringConfig))
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
                    autoHeight= true
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
                props.room?.let { room->
                    val affectedQuestions = room.questions.mapNotNull { it.deref() }.filter{ it.schedule == null }
                    console.log("AQ ${room.questions.size} ${affectedQuestions.size}")

                    if (affectedQuestions.isNotEmpty())
                    div {
                        css {
                            fontSize = 12.px
                            this.color = Color("#AAAAAA")
                        }
                        +"Changing this schedule will affect "
                        abbr {
                            +" ${affectedQuestions.size} existing questions "
                            title = affectedQuestions.reversed().take(5).joinToString("\n"){ it.name } +
                                    if (affectedQuestions.size > 5) "\n..."  else ""
                        }
                        +" that are configured to follow it."
                    }
                }
            }
        }
        FormSection {
            if (scoringConfig != null) {
                title = "Scoring"
                div {
                    css {
                        this.color = Color("#888")
                        fontSize = 90.pct
                        "div + div" {
                            marginTop = 0.2.em
                        }
                    }
                    div {
                        +"Currently, the scoring considers "
                        b { +"yes-no questions" }
                        +" only. "
                    }
                    div {
                        +"In order for a question to be scored, you have to set a "
                        b { +"score time" }
                        +" in the question schedule "
                        +" (either set for each question separately or configure a "
                        if (!showSchedule) {
                            a {
                                //css { this.color = Globals.inherit }
                                +"default schedule"
                                onClick = {
                                    showSchedule = true
                                    it.preventDefault()
                                }
                            }
                        } else {
                            +"default schedule"
                        }
                        +" right here for the whole room)."
                    }
                    div {
                        +"For each user, only the last prediction submitted "
                        +" before the score time is used for scoring."
                    }
                }
                RadioGroup<ScoreboardMode>()() {
                    title = "Scoreboard visible to"
                    options = listOf(
                        ScoreboardMode.NONE to "No one",
                        ScoreboardMode.PRIVATE to "Moderators only",
                        ScoreboardMode.PUBLIC to "All members",
                    )
                    value = scoringConfig!!.scoreboardMode
                    onChange = { scoringConfig = scoringConfig!!.copy(scoreboardMode = it) }
                }
                div {
                    a {
                        href = "#"
                        +"Disable scoring"
                        onClick = {
                            scoringConfig = null
                            it.preventDefault()
                        }
                    }
                }
            } else {
                a {
                    href = "#"
                    +"Configure scoring"
                    onClick = {
                        scoringConfig = ScoringConfig()
                        it.preventDefault()
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

