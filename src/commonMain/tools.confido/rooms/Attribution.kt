package tools.confido.rooms

import kotlinx.serialization.Serializable
import tools.confido.users.User

/**
 * Minimum number of predictors before disclosure surfaces of an unattributed
 * ([AttributionLevel.ANONYMOUS] or stricter) question are shown. Below this a
 * disclosure is trivially invertible: any participant can subtract their own answer,
 * so with two predictors the group mean (or the unattributed answer that is not
 * mine) *is* the other person's prediction.
 */
const val ATTRIBUTION_MIN_PREDICTORS = 5

/**
 * How activity (predictions, comments) on a question is attributed to its authors.
 *
 * Declaration order is meaningful: each level discloses strictly less than the
 * previous one, and ratchet checks ([AttributionPolicy.revealsMoreThan]) compare
 * this ordering.
 */
@Serializable
enum class AttributionLevel {
    /** Real (nick)names everywhere, the default. */
    OPEN,

    /** A stable room-scoped alias replaces the author's name on all activity. */
    PSEUDONYM,

    /**
     * No attribution and no linkage at all. Individual answers are shown
     * unattributed, and only once [ATTRIBUTION_MIN_PREDICTORS] is reached.
     */
    ANONYMOUS,

    /**
     * Strictly stricter than [ANONYMOUS]: everything it masks (comments are
     * anonymous here too) plus no individual prediction data of any kind is
     * disclosed to anyone; only coarse aggregates, gated at
     * [ATTRIBUTION_MIN_PREDICTORS]. (Formerly the `sensitive` question flag,
     * which showed comment authors by name — tightened so the ladder is a
     * strict total order.)
     */
    AGGREGATE_ONLY;

    /**
     * Whether identities are masked but the underlying activity still exists as
     * individual, potentially piercable data ([RoomPermission.VIEW_REAL_IDENTITIES]).
     * False only for [OPEN]: even [AGGREGATE_ONLY] has individual *comments*,
     * masked like [ANONYMOUS] ones.
     */
    val pierceable get() = this != OPEN
}

/**
 * The attribution promise for a question: the masking [level] plus whether room
 * roles holding [RoomPermission.VIEW_REAL_IDENTITIES] may see the real identities
 * behind masked activity ([moderatorAccess]; moot for the non-[AttributionLevel.pierceable]
 * levels). Lives on [Room.defaultAttribution] with an optional per-question
 * override ([tools.confido.question.Question.attribution]).
 */
@Serializable
data class AttributionPolicy(
    val level: AttributionLevel = AttributionLevel.OPEN,
    val moderatorAccess: Boolean = true,
) {
    /**
     * Ratchet check: would replacing [other] by this policy disclose more about
     * content authored under [other]? Once such content exists the switch must be
     * refused — that is what makes the promise durable despite room owners
     * controlling settings.
     *
     * Two clauses: the level ladder (a strict total order — each level masks
     * everything the previous one does, in data AND author identity) and
     * moderator piercing. The ladder being total is load-bearing here: it is
     * what lets e.g. ANONYMOUS → AGGREGATE_ONLY pass as a pure tightening.
     */
    fun revealsMoreThan(other: AttributionPolicy): Boolean =
        level < other.level ||
        (moderatorAccess && !other.moderatorAccess && other.level.pierceable)

    /**
     * Whether aggregate views (group prediction, its history, the histogram) may
     * be shown given how many users have predicted. Unattributed levels stay
     * hidden below [ATTRIBUTION_MIN_PREDICTORS]; for [AttributionLevel.OPEN] and
     * [AttributionLevel.PSEUDONYM] individual answers are visible under some name
     * anyway, so aggregates disclose nothing extra.
     */
    fun aggregatesVisible(predictorCount: Int): Boolean =
        level < AttributionLevel.ANONYMOUS || predictorCount >= ATTRIBUTION_MIN_PREDICTORS

    /**
     * Whether [observer] may see the real identities behind activity masked by
     * this policy in [room]: the policy grants moderator access AND the observer
     * holds [RoomPermission.VIEW_REAL_IDENTITIES] there. Always false for
     * non-[AttributionLevel.pierceable] levels (nothing to pierce).
     */
    fun pierceableBy(observer: User?, room: Room?): Boolean =
        level.pierceable && moderatorAccess &&
        room?.hasPermission(observer, RoomPermission.VIEW_REAL_IDENTITIES) == true
}
