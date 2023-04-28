package tools.confido.application

import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import rooms.ExportHistory
import tools.confido.distributions.*
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.question.QuestionComment
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.spaces.NumericSpace
import tools.confido.state.serverState
import tools.confido.utils.fromUnix
import tools.confido.utils.toFixed
import tools.confido.utils.utcFromUnix
import users.User

typealias CsvRecord = Map<String, String>

sealed class CsvExport {

    private fun escapeCSVField(field: String) =
        if (field.contains(Regex("[\"\r\n,]"))) {
            "\"" + field.replace("\"","\"\"") + "\""
        } else field

    abstract suspend fun export(): Flow<CsvRecord>

    suspend fun exportCSV() = flow {
        var headers = emptySet<String>()
        export().collect {
            headers = headers.union(it.keys)
        }

        emit(headers.joinToString(","))
        export().collect {field ->
            val line = headers.map {header ->
                escapeCSVField(field[header] ?: "")
            }.joinToString((","))
            emit(line)
        }
    }

    fun exportValue(space: NumericSpace, value: Double) =
        if (space.representsDays) {
            LocalDate.utcFromUnix(value.toInt()).toString()
        } else {
            value.toFixed(2)
        }
    fun exportDiff(space: NumericSpace, diff: Double) =
        if (space.representsDays) {
            (diff / (24.0 * 3600.0)).toFixed(2)
        } else {
            diff.toFixed(2)
        }

    fun exportDist(dist: ProbabilityDistribution): CsvRecord {
        val ret = mutableMapOf<String, String>()
        when (dist) {
            is BinaryDistribution -> ret["probability"] = dist.yesProb.toString()
            is ContinuousProbabilityDistribution -> {
                ret["mean"] = exportValue(dist.space, dist.mean)
                ret["stdev"] = exportDiff(dist.space, dist.stdev)
                if (dist is TruncatedNormalDistribution) {
                    ret["pseudo_mean"] = exportValue(dist.space, dist.pseudoMean)
                    ret["pseudo_stdev"] = exportDiff(dist.space, dist.pseudoStdev)
                }
            }
        }
        return ret

    }
}

class PredictionExport  (
    val questions: List<Question>,
    val group: Boolean,
    val history: ExportHistory,
    val buckets: Int,
) : CsvExport() {
    private val addQuestionCols = questions.size > 1

    private val bucketNames = run {
        val numericQuestions = questions.filter { it.answerSpace is NumericSpace }
        if (numericQuestions.size == 1) {
            val space = (numericQuestions[0].answerSpace as NumericSpace).copy(bins = buckets)
            space.binner.binRanges.mapIndexed { idx,it -> "bucket${idx+1}_${exportValue(space,it.start)}-${exportValue(space,it.endExclusive)}" }
        } else {
            (1..buckets).map { "bucket$it" }
        }
    }

    private fun predictionRow(question: Question, user: User?, prediction: Prediction): CsvRecord {
        val ret = mutableMapOf<String, String>()

        if (addQuestionCols) {
            ret["question_id"] = question.id
            ret["question"] = question.name
        }

        user?.let {
            ret["user_id"] = it.id
            ret["nick"] = it.nick ?: ""
            ret["email"] = it.email?.lowercase() ?: ""
        }

        val dt = Instant.fromEpochSeconds(prediction.ts.toLong()).toLocalDateTime(TimeZone.UTC)
        ret["timestamp"] = prediction.ts.toString()
        ret["date"] = dt.date.toString()
        ret["time"] = dt.time.toString()

        ret += exportDist(prediction.dist)

        // For group prediction, always add buckets, even when there is only one prediction
        // and we get the original TruncatedNormalDistribution
        if (prediction.dist is ContinuousProbabilityDistribution && (user == null || prediction.dist !is TruncatedNormalDistribution)) {
            val discretized = prediction.dist.discretize(buckets)
            bucketNames.zip(discretized.binProbs).map { (name, prob) ->
                ret[name] = prob.toString()
            }
        }

        return ret.toMap()
    }

    private fun getQuestionLastPrediction(question: Question, user: User?) =
        if (user == null) {
            serverState.groupPred[question.ref]
        } else {
            serverState.userPred[question.ref]?.get(user.ref)
        }

    private suspend fun getQuestionAllPredictions(question: Question, user: User?) =
        if (user == null) {
            serverState.groupPredHistManager.query(question.ref)
        } else {
            serverState.userPredHistManager.query(question.ref, user.ref)
        }

    private suspend fun exportQuestion(question: Question, user: User?): Flow<CsvRecord> = flow {
        when(history) {
            ExportHistory.LAST -> getQuestionLastPrediction(question, user)?.let {emit(predictionRow(question, user, it))}
            ExportHistory.DAILY -> {
                val dayMap: MutableMap<LocalDate, Prediction> = mutableMapOf()
                getQuestionAllPredictions(question, user).collect {
                    val date = LocalDate.fromUnix(it.ts)
                    dayMap[date] = it
                }
                dayMap.values.map { emit(predictionRow(question, user, it)) }
            }
            ExportHistory.FULL -> getQuestionAllPredictions(question, user).collect {emit(predictionRow(question, user, it))}
        }
    }

    private suspend fun exportQuestion(question: Question): Flow<CsvRecord> =
        if (group)
            exportQuestion(question, null)
        else
            flow {
                serverState.userPred[question.ref]?.keys?.map {
                    emitAll(exportQuestion(question, it.deref()))
                }
            }

    override suspend fun export() = flow {
        questions.map {
            emitAll(exportQuestion(it))
        }
    }

}
class CommentExport  (
    val questions: List<Question>,
) : CsvExport() {

    fun exportComment(q: Question, c: QuestionComment): CsvRecord {
        val dt = Instant.fromEpochSeconds(c.timestamp.toLong()).toLocalDateTime(TimeZone.UTC)
        val user = c.user.deref()
        val ret =  mutableMapOf(
            "question_id" to q.id,
            "question" to q.name,
            "user_id" to c.user.id,
            "nick" to (user?.nick ?: ""),
            "email" to (user?.email?.lowercase() ?: ""),
            "timestamp" to c.timestamp.toString(),
            "date" to dt.date.toString(),
            "time" to dt.time.toString(),
            "comment" to c.content,
            "num_likes" to (serverState.commentLikeManager.numLikes[c.ref] ?: 0).toString(),
        )
        c.prediction?.let {pred->
            ret += exportDist(pred.dist)
        }
        return ret
    }
    suspend fun exportQuestion(q: Question) = flow {
        (serverState.questionComments[q.ref]?: emptyMap()).values.asFlow().collect {
            emit(exportComment(q, it))
        }
    }
    override suspend fun export() = flow {
        questions.map {
            emitAll(exportQuestion(it))
        }
    }

}
