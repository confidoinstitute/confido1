package tools.confido.web.components.rooms

import tools.confido.web.components.useMyUser
import tools.confido.web.components.useRooms
import tools.confido.web.components.basic.sansSerif
import tools.confido.web.components.questions.QuestionContext
import web.cssom.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.rooms.AttributionLevel
import tools.confido.rooms.Room

/**
 * Disclosure line under a comment input: what identity the post will carry, per
 * the effective policy of the surrounding question (QuestionContext; room default
 * for room-level discussion). An honest promise needs the visibility caveat too,
 * so the moderator-access state rides along in the hover title.
 */
val PostingAsCue = FC<Props> {
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
