package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import kotlinx.coroutines.flow.toList
import org.bson.Document
import kotlin.math.roundToLong

/**
 * Repairs numeric spaces with a degenerate bin count, so that `NumericSpace` can
 * require `bins >= 1`.
 *
 * Until 159e9871 (2022-10-27), `NumericSpace.fromDates` took the bin count from
 * `(maxDate - minDate).days` — the *days component* of a months+days DatePeriod, so
 * e.g. a whole-year range got 0 bins and 2022-10-27..2023-10-27 got 1. A 0-bin group
 * distribution has empty `binProbs` and crashes every client that renders it.
 *
 * Every persisted copy of a space is rewritten by the same rule, so they stay equal.
 * The places are enumerated from the entity types, not searched for:
 *   - `questions`: `answerSpace`, and `resolution.space` (a `NumericValue`; `Question`
 *     requires it to equal `answerSpace`);
 *   - `userPred`, `userPredHist`, `groupPredHist`: `dist.space` (every numeric
 *     distribution has one; none persists a nested distribution);
 *   - `questionComments`: `prediction.dist.space`.
 * Old (KMongo-era) documents tag these spaces with `___type: "numeric"`, newer ones
 * do not (the static type there is `NumericSpace`), so both shapes are accepted.
 * The rule:
 *   - date spaces with fewer bins than days get the current `fromDates` count,
 *     `min(days + 1, 1000)`;
 *   - any other space with `bins < 1` gets `NumericSpace.DEFAULT_BINS`.
 * `binProbs` lists are left alone (a discretized distribution sizes its binner from
 * them, not from its space). Group-history entries with empty `binProbs` carry no
 * information and cannot be decoded any more, so they are deleted.
 */
object DateSpaceBinsMigration : Migration {
    override val id = "012_date_space_bins"
    override val description = "Repair numeric spaces with degenerate bin counts (pre-2022-10-27 fromDates bug)"
    override val collections = listOf("questions", "userPred", "userPredHist", "groupPredHist", "questionComments")

    private const val DEFAULT_BINS = 1000  // NumericSpace.DEFAULT_BINS; frozen here on purpose
    private const val MAX_DATE_BINS = 1000

    override suspend fun run(ctx: MigrationContext) {
        val groupHist = ctx.collection("groupPredHist")
        val dropped = groupHist.deleteMany(Filters.and(
            Filters.eq("dist.___type", "discretizedContinuous"),
            Filters.size("dist.binProbs", 0),
        )).deletedCount
        if (dropped > 0) println("  groupPredHist: deleted $dropped entr(y/ies) with empty binProbs")

        spacePaths.forEach { (name, paths) ->
            val coll = ctx.collection(name)
            var fixed = 0
            coll.find().toList().forEach { doc ->
                var modified = false
                for (path in paths) {
                    val space = spaceAt(doc, path) ?: continue
                    repairedBins(space)?.let { space["bins"] = it; modified = true }
                }
                if (modified) {
                    coll.replaceOne(Filters.eq("_id", doc["_id"]), doc)
                    fixed++
                }
            }
            if (fixed > 0) println("  $name: repaired bin count in $fixed document(s)")
        }
    }

    private val spacePaths = mapOf(
        "questions" to listOf(listOf("answerSpace"), listOf("resolution", "space")),
        "userPred" to listOf(listOf("dist", "space")),
        "userPredHist" to listOf(listOf("dist", "space")),
        "groupPredHist" to listOf(listOf("dist", "space")),
        "questionComments" to listOf(listOf("prediction", "dist", "space")),
    )

    /** The numeric space at [path], or null (absent, binary, or not space-shaped). */
    private fun spaceAt(doc: Document, path: List<String>): Document? {
        var node: Any? = doc
        for (key in path) node = (node as? Document)?.get(key)
        val space = node as? Document ?: return null
        return space.takeIf {
            (it["___type"] ?: "numeric") == "numeric"
                && it["min"] is Number && it["max"] is Number && it["bins"] is Number
        }
    }

    private fun repairedBins(space: Document): Int? {
        val bins = (space["bins"] as? Number)?.toInt() ?: return null
        val min = (space["min"] as? Number)?.toDouble()
        val max = (space["max"] as? Number)?.toDouble()
        if (space["representsDays"] == true && min != null && max != null
                && min.isFinite() && max.isFinite() && max > min) {
            val days = ((max - min) / 86400).roundToLong()
            if (bins < minOf(days, MAX_DATE_BINS.toLong())) return minOf(days + 1, MAX_DATE_BINS.toLong()).toInt()
        }
        return if (bins < 1) DEFAULT_BINS else null
    }
}
