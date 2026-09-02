package tools.confido.web.components.redesign.rooms

import tools.confido.web.components.useDisconnected
import tools.confido.web.components.useMyUser
import tools.confido.web.components.useRooms
import tools.confido.web.components.redesign.basic.LinkUnstyled
import tools.confido.web.components.redesign.basic.Stack
import tools.confido.web.components.redesign.forms.*
import tools.confido.web.components.redesign.questions.dialog.EditQuestionDialogSchedule
import web.cssom.*
import web.html.ButtonType
import emotion.react.css
import tools.confido.payloads.requests.BaseRoomInformation
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.abbr
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import tools.confido.rooms.*
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionData
import tools.confido.question.QuestionSchedule
import web.html.submit
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.responses.WSData
import tools.confido.state.Topics
import tools.confido.refs.Ref
import tools.confido.refs.ref

external interface RoomSettingsProps : Props {
    var room: Room?
    /** When creating (room == null): create a group container instead of a room.
     *  For an existing room the kind comes from the room itself (immutable). */
    var isGroup: Boolean?
    /** When creating: preselect this parent group (e.g. from a create-in-group
     *  entry point). The user can still change or clear it. */
    var initialParentRoom: Ref<Room>?
    var onChange: ((BaseRoomInformation?) -> Unit)?
    var onSubmit: (() -> Unit)?
    var openSchedule: Boolean?
}

val RoomSettings = FC<RoomSettingsProps> { props ->
    val roomQuestions = (useSubscription(props.room?.let { Topics.roomQuestions(it.ref) }) as? WSData)?.data ?: emptyMap()
    val allRooms = useRooms()
    val myUser = useMyUser()
    val isGroup = props.room?.isGroup ?: props.isGroup ?: false

    var name by useState(props.room?.name ?: "")
    var description by useState(props.room?.description ?: "")
    // A room created inside a group starts with the group's color, so the sidebar
    // hierarchy reads visually without any user action.
    var color by useState(props.room?.color
        ?: props.initialParentRoom?.let { allRooms[it.id]?.color }
        ?: RoomColor.values().random())
    var icon by useState(props.room?.icon)
    var parentRoom by useState(props.room?.parentRoom ?: props.initialParentRoom)
    var defaultSchedule by useState(props.room?.defaultSchedule ?: QuestionSchedule())
    var showSchedule by useState(props.openSchedule ?: false)
    var scoringConfig by useState(props.room?.scoring)
    var showScore by useState(scoringConfig != null)
    var scheduleValid by useState(true)
    var commentTemplate by useState(props.room?.commentTemplate ?: "")
    var defaultAttribution by useState(props.room?.defaultAttribution ?: AttributionPolicy())
    var pseudonymPool by useState(props.room?.pseudonymPool)
    var attributionValid by useState(true)
    var extensionData by useState(props.room?.extensionData ?: ExtensionData(RoomEDT))
    val onExtensionDataChange: ((ExtensionData) -> ExtensionData) -> Unit = { transform ->
        extensionData = transform(extensionData)
    }
    val valid = !name.isEmpty() && scheduleValid && attributionValid

    useEffect(name, description, color, icon, defaultSchedule.identify(), scoringConfig?.identify(), commentTemplate,
              extensionData, defaultAttribution, pseudonymPool?.joinToString("\n"), attributionValid, parentRoom?.id) {
        if (valid)
            props.onChange?.invoke(BaseRoomInformation(name, description, color, icon, defaultSchedule, scoringConfig, commentTemplate, extensionData,
                defaultAttribution = defaultAttribution, pseudonymPool = pseudonymPool,
                isGroup = isGroup, parentRoom = parentRoom))
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
            // Groups the user can attach this room to. Attaching needs the group
            // owner's consent (enforced server-side as ROOM_OWNER there); the current
            // parent stays listed regardless so the selection is representable.
            val availableGroups = if (isGroup) emptyList() else allRooms.values.filter {
                it.isGroup && (it.hasPermission(myUser, RoomPermission.ROOM_OWNER) || it.id == parentRoom?.id)
            }
            if (availableGroups.isNotEmpty()) FormField {
                title = "Group"
                comment = "Rooms in a group share the group's members and appear in its combined scoreboard."
                Select {
                    css {
                        width = 100.pct
                    }
                    value = parentRoom?.id ?: ""
                    onChange = { event ->
                        parentRoom = event.target.value.ifEmpty { null }?.let { Ref<Room>(it) }
                    }
                    option {
                        value = ""
                        +"(No group)"
                    }
                    availableGroups.sortedBy { it.name }.forEach { group ->
                        option {
                            value = group.id
                            +group.name
                        }
                    }
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
        if (!isGroup) FormSection {
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
                    val affectedQuestions = room.questions.mapNotNull { roomQuestions[it.id] }.filter{ it.schedule == null }
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
            title = "Anonymity"
            // Ratchet floor: the meet of the room's own promise floor (room comments)
            // and the floors of questions inheriting the default (server-enforced;
            // violated iff any component is).
            val floorComponents = props.room?.let { room ->
                listOfNotNull(room.attributionFloor) +
                    room.questions.mapNotNull { roomQuestions[it.id] }
                        .filter { it.attribution == null }
                        .mapNotNull { it.attributionFloor }
            } ?: emptyList()
            AttributionSection {
                value = defaultAttribution
                persisted = props.room?.defaultAttribution
                ratchetFloor = floorComponents.reduceOrNull(::strictestOf)
                ratchetWhat = "activity following this setting was made under the current promise"
                onChange = { policy, isError ->
                    defaultAttribution = policy
                    attributionValid = !isError
                }
            }
            if (defaultAttribution.level == AttributionLevel.PSEUDONYM) {
                PseudonymPoolSection {
                    pool = pseudonymPool
                    onChange = { pseudonymPool = it }
                    memberCount = props.room?.members?.size ?: 0
                }
            }
        }
        if (!isGroup) FormSection {
            title = "Comment"
            FormField {

                title = "Comment template"
                MultilineTextInput {
                    autoHeight= true
                    placeholder = "A template for the first comment made by a given user on a given question. Here you can e.g. put some section headers or prompts to encourage the user to structure their answer accordingly."
                    value = commentTemplate
                    onChange = { commentTemplate = it.target.value }
                }

            }
        }
        ClientExtension.forEach { it.editRoomSettingsExtra(props.room, onExtensionDataChange, this) }
        FormSection {
            Stack {
                Button {
                    type = ButtonType.submit
                    css {
                        fontWeight = integer(500)
                    }
                    if (props.room != null)
                        +"Save"
                    else if (isGroup)
                        +"Create group"
                    else
                        +"Create room"
                    disabled = useDisconnected() || !valid
                }
            }
        }
    }
}

