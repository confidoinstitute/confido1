package tools.confido.web.components.redesign.rooms

import tools.confido.web.Client
import tools.confido.web.components.redesign.ConfirmDialog
import tools.confido.web.components.redesign.basic.sansSerif
import tools.confido.web.utils.runCoroutine
import tools.confido.api.Api
import tools.confido.refs.Ref
import tools.confido.rooms.Room
import tools.confido.web.components.redesign.forms.*
import web.cssom.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.rooms.ATTRIBUTION_MIN_PREDICTORS
import tools.confido.rooms.AttributionLevel
import tools.confido.rooms.AttributionPolicy

val AttributionLevel.label get() = when (this) {
    AttributionLevel.OPEN -> "Open — everyone participates under their own name"
    AttributionLevel.PSEUDONYM -> "Pseudonymous — activity appears under stable aliases"
    AttributionLevel.ANONYMOUS -> "Anonymous — activity is not attributed to anyone"
    AttributionLevel.AGGREGATE_ONLY -> "Aggregate only — no individual answers are shown at all"
}

private val AttributionLevel.explanation get() = when (this) {
    AttributionLevel.OPEN -> null
    AttributionLevel.PSEUDONYM ->
        "Each member gets a stable alias (e.g. “Amber Fox”) shown on their answers and comments."
    AttributionLevel.ANONYMOUS ->
        "Answers and comments carry no name and cannot be linked to each other. Individual answers " +
        "appear only once at least $ATTRIBUTION_MIN_PREDICTORS people have answered."
    AttributionLevel.AGGREGATE_ONLY ->
        "Everything Anonymous does, plus: individual answers are never shown to anyone in any " +
        "form — only a coarse group summary, once at least $ATTRIBUTION_MIN_PREDICTORS people " +
        "have answered. Comments are anonymous."
}

external interface AttributionSectionProps : Props {
    var value: AttributionPolicy
    /** isError mirrors the schedule editor convention: true while the selection
     *  violates the ratchet (the server would refuse it anyway). */
    var onChange: ((AttributionPolicy, isError: Boolean) -> Unit)?
    /** The persisted policy existing activity was made under; null = weakening
     *  is unrestricted (nothing was authored under a promise yet). */
    var ratchetFloor: AttributionPolicy?
    /** What the ratchet warning should say the activity is, e.g. "3 questions
     *  with activity follow this setting". */
    var ratchetWhat: String?
    /** The currently saved policy (regardless of activity); a selection stricter
     *  than this gets the loud one-way warning. Null = creation flow, where any
     *  non-default selection warns. */
    var persisted: AttributionPolicy?
    var disabled: Boolean?
}

/** Level + moderator-access control for an [AttributionPolicy]; shared between the
 *  room dialog (room default) and the question dialog (per-question override). */
val AttributionSection = FC<AttributionSectionProps> { props ->
    val policy = props.value
    val violation = props.ratchetFloor?.let { policy.revealsMoreThan(it) } ?: false

    fun emit(new: AttributionPolicy) {
        val err = props.ratchetFloor?.let { new.revealsMoreThan(it) } ?: false
        props.onChange?.invoke(new, err)
    }

    RadioGroup<AttributionLevel>()() {
        title = "Attribution"
        options = AttributionLevel.values().map { it to it.label }
        value = policy.level
        disabled = props.disabled ?: false
        onChange = { emit(policy.copy(level = it)) }
    }
    policy.level.explanation?.let {
        div {
            css { fontFamily = sansSerif; fontSize = 12.px; color = Color("#888888"); marginTop = 4.px }
            +it
        }
    }
    if (policy.level.pierceable) {
        FormSwitch {
            label = "Moderators can see real identities"
            checked = policy.moderatorAccess
            disabled = props.disabled ?: false
            comment = if (policy.moderatorAccess)
                "Members with the “see real identities” permission (moderators, owners) " +
                "see who is behind each alias, shown alongside it. Participants are told this."
            else
                "Sealed: nobody — moderators and owners included — can see who is behind " +
                "masked activity. Moderators can still remove content but cannot attribute it. " +
                "This cannot be undone once people have participated."
            onChange = { e -> emit(policy.copy(moderatorAccess = e.target.checked)) }
        }
    }
    if (violation) {
        div {
            css { fontFamily = sansSerif; fontSize = 12.px; color = Color("#F35454"); marginTop = 4.px }
            +"This would reveal more about answers or comments already made under the current promise"
            props.ratchetWhat?.let { +" ($it)" }
            +". Attribution can only be made stricter once there is activity."
        }
    }
    // Restricting is a one-way door: the moment anyone answers or comments under
    // the stricter setting, the ratchet makes it permanent. Say so loudly BEFORE
    // the user commits, not via a rejection after.
    val restricting = props.persisted?.revealsMoreThan(policy)
        ?: (policy != AttributionPolicy())
    // The mirror of the one-way warning: a permitted *loosening* looks like a bug
    // to anyone who knows about the ratchet, so say why it is allowed — the promise
    // only locks once someone participates under it.
    val loosening = props.persisted?.let { policy.revealsMoreThan(it) } ?: false
    if (loosening && !violation) {
        div {
            css { fontFamily = sansSerif; fontSize = 12.px; color = Color("#888888"); marginTop = 4.px }
            +"This weakens the current setting — still possible only because nothing has "
            +"been posted or predicted under it yet. Any activity makes it permanent."
        }
    }
    if (restricting && !violation) {
        div {
            css {
                fontFamily = sansSerif
                fontSize = 12.px
                fontWeight = integer(600)
                color = Color("#E58C00")
                marginTop = 6.px
                padding = Padding(6.px, 8.px)
                border = Border(1.px, LineStyle.solid, Color("#E58C00"))
                borderRadius = 4.px
            }
            +"⚠ This restriction is one-way: once anyone answers or comments under it, "
            +"it can never be weakened again — not even by room owners."
        }
    }
}

external interface PseudonymPoolSectionProps : Props {
    var pool: List<String>?
    var onChange: ((List<String>?) -> Unit)?
    var memberCount: Int
    /** When set (edit context), offers the immediate "re-draw all" action. */
    var roomRef: Ref<Room>?
}

/** Default-hidden custom alias pool editor (room dialog only). null pool = the
 *  built-in generator; a short custom pool is topped up from the generator. */
val PseudonymPoolSection = FC<PseudonymPoolSectionProps> { props ->
    var show by useState(!props.pool.isNullOrEmpty())
    var rawText by useState(props.pool.orEmpty().joinToString("\n"))
    var confirmRedraw by useState(false)

    if (!show) {
        a {
            href = "#"
            +"Configure a custom pseudonym list"
            onClick = { show = true; it.preventDefault() }
        }
        return@FC
    }
    FormField {
        title = "Custom pseudonym list"
        comment = "One per line; leave empty to use the built-in generated aliases. " +
            "A list shorter than the member count is topped up with generated aliases. " +
            "The list only affects members who don't have an alias yet — aliases " +
            "already given out never change on their own; use “Re-draw all " +
            "pseudonyms” below to apply the list to everyone."
        MultilineTextInput {
            placeholder = "e.g.\nAlpha\nBravo\nCharlie"
            value = rawText
            autoHeight = true
            onChange = { event ->
                rawText = event.target.value
                val parsed = event.target.value.lines().map { it.trim() }.filter { it.isNotEmpty() }
                props.onChange?.invoke(parsed.ifEmpty { null })
            }
        }
    }
    val count = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().size
    div {
        css { fontSize = 12.px; fontFamily = sansSerif; lineHeight = 14.px }
        span {
            css { color = Color("#AAAAAA") }
            +"$count custom pseudonym${if (count != 1) "s" else ""}, ${props.memberCount} member${if (props.memberCount != 1) "s" else ""}"
        }
        if (count in 1 until props.memberCount) {
            span {
                css { color = Color("#AAAAAA") }
                +" — the rest will get generated aliases"
            }
        }
    }
    div {
        a {
            href = "#"
            +"Use generated aliases only"
            onClick = {
                rawText = ""
                show = false
                props.onChange?.invoke(null)
                it.preventDefault()
            }
        }
    }
    props.roomRef?.let { rRef ->
        ConfirmDialog {
            open = confirmRedraw
            onClose = { confirmRedraw = false }
            onConfirm = {
                runCoroutine { Client.call(Api.roomPseudonymsReset(rRef), Unit) }
                confirmRedraw = false
            }
            title = "Re-draw all pseudonyms?"
            confirmText = "Re-draw"
            +("Every member gets a new alias drawn from the current list, applied " +
                "immediately (rooms sharing this room's aliases included). Existing " +
                "answers and comments re-label to the new aliases consistently — " +
                "nothing is revealed — but anything referring to the old names " +
                "(comment text, exports, memory) will no longer match.")
        }
        div {
            a {
                href = "#"
                +"Re-draw all pseudonyms…"
                onClick = { confirmRedraw = true; it.preventDefault() }
            }
        }
    }
}
