package tools.confido.web.components.rooms

import tools.confido.web.components.useMyUser
import tools.confido.web.components.useRooms
import tools.confido.web.components.basic.BaseDialogProps
import tools.confido.web.components.basic.Dialog
import tools.confido.web.components.basic.sansSerif
import tools.confido.web.components.forms.InlineHelpButton
import tools.confido.web.components.questions.QuestionContext
import web.cssom.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import tools.confido.rooms.AttributionLevel
import tools.confido.rooms.Room

external interface PseudonymHelpDialogProps : BaseDialogProps {
    var alias: String?
    var moderatorAccess: Boolean
}

/** Answers "where does my nickname come from, and can I change it?" — opened from
 *  the (?) next to the viewer's alias in the attribution cues. */
val PseudonymHelpDialog = FC<PseudonymHelpDialogProps> { props ->
    Dialog {
        title = "Your pseudonym"
        open = props.open
        onClose = props.onClose
        p {
            +"A moderator has chosen to show answers and comments here under pseudonyms instead of real names"
            props.alias?.let {
                +"; yours is "
                b { +it }
            }
            +"."
        }
        p {
            +"Pseudonyms are assigned automatically, one per person, and stay the same over time, so others "
            +"can follow who said what without learning who is who. They cannot be chosen or changed."
        }
        p {
            if (props.moderatorAccess)
                +"Moderators can see who is behind each pseudonym; other participants cannot."
            else
                +"Identities are sealed: nobody, moderators included, can see who is behind a pseudonym."
        }
    }
}

/**
 * Disclosure line under a comment input: what identity the post will carry, per
 * the effective policy of the surrounding question (QuestionContext; room default
 * for room-level discussion). An honest promise needs the visibility caveat too,
 * so the moderator-access state rides along in the hover title.
 */
val PostingAsCue = FC<Props> {
    var helpOpen by useState(false)
    val room = use(RoomContext)
    val question = use(QuestionContext)
    val myUser = useMyUser()
    val rooms = useRooms()
    room ?: return@FC
    val policy = question?.effectiveAttributionIn(room) ?: room.effectiveDefaultAttribution
    if (!policy.level.pierceable) return@FC
    val parent = room.parentRoom?.let { rooms[it.id] }
    val alias = myUser?.id?.let { room.aliasFor(it, parent) }

    span {
        css {
            fontFamily = sansSerif
            fontSize = 13.px
            lineHeight = 18.px
            color = Color("#888888")
        }
        title = if (policy.moderatorAccess)
            "Moderators can see who is behind masked identities."
        else
            "Sealed: nobody, moderators included, can see who is behind masked identities."
        when (policy.level) {
            AttributionLevel.PSEUDONYM -> {
                +"Posting as "
                b { +(alias ?: "a pseudonym") }
                InlineHelpButton { onClick = { helpOpen = true } }
                PseudonymHelpDialog {
                    open = helpOpen
                    onClose = { helpOpen = false }
                    this.alias = alias
                    moderatorAccess = policy.moderatorAccess
                }
            }
            AttributionLevel.ANONYMOUS, AttributionLevel.AGGREGATE_ONLY -> +"Posting anonymously"
            else -> {}
        }
    }
}

external interface RoomAttributionInfoProps : Props {
    var room: Room
}

/** Room-info panel disclosure: the viewer's own alias and, crucially, who can see
 *  through it. Shown whenever the room default masks identities. */
val RoomAttributionInfo = FC<RoomAttributionInfoProps> { props ->
    var helpOpen by useState(false)
    val myUser = useMyUser()
    val rooms = useRooms()
    val room = props.room
    val policy = room.effectiveDefaultAttribution
    if (!policy.level.pierceable) return@FC
    val parent = room.parentRoom?.let { rooms[it.id] }
    val alias = myUser?.id?.let { room.aliasFor(it, parent) }

    div {
        css {
            textAlign = TextAlign.center
            fontFamily = sansSerif
            fontSize = 14.px
            lineHeight = 17.px
            opacity = number(0.8)
            paddingTop = 12.px
        }
        when (policy.level) {
            AttributionLevel.PSEUDONYM -> {
                +"You appear as "
                b { +(alias ?: "a pseudonym") }
                +" in this room."
                InlineHelpButton {
                    // Sits on the room's palette color: follow the header text
                    // instead of the default gray (CSS beats the icon's color attr).
                    css {
                        color = Color("inherit")
                        "& svg" { color = Color("inherit") }
                    }
                    onClick = { helpOpen = true }
                }
                PseudonymHelpDialog {
                    open = helpOpen
                    onClose = { helpOpen = false }
                    this.alias = alias
                    moderatorAccess = policy.moderatorAccess
                }
            }
            AttributionLevel.ANONYMOUS -> +"Answers and comments in this room are anonymous."
            AttributionLevel.AGGREGATE_ONLY ->
                +"Comments in this room are anonymous; individual answers are never shown."
            else -> {}
        }
        div {
            css { fontSize = 12.px; opacity = number(0.8); paddingTop = 2.px }
            if (policy.moderatorAccess)
                +"Moderators can see real identities."
            else
                +"Identities are sealed — nobody, moderators included, can see them."
        }
    }
}
