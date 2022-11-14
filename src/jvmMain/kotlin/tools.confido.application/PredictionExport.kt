package tools.confido.application

import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import rooms.ExportHistory
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.spaces.NumericSpace
import tools.confido.state.serverState
import tools.confido.utils.fromUnix
import users.User

typealias CsvField = Map<String, String>

class PredictionExport (
    val questions: List<Question>,
    val group: Boolean,
    val history: ExportHistory,
    val buckets: Int,
) {
    private val addQuestionCols = questions.size > 1

    private val bucketNames = run {
        val numericQuestions = questions.filter { it.answerSpace is NumericSpace }
        if (numericQuestions.size == 1) {
            val space = (numericQuestions[0].answerSpace as NumericSpace).copy(bins = buckets)
            space.binner.binRanges.map { "bucket${it.start}-${it.endExclusive}" }
        } else {
            (1..buckets).map { "bucket$it" }
        }
    }

    private fun predictionRow(question: Question, user: User?, prediction: Prediction): CsvField {
        val ret = mutableMapOf<String, String>()

        if (addQuestionCols) {
            ret["question_id"] = question.id
            ret["question"] = question.name
        }

        user?.let {
            ret["user_id"] = it.id
            ret["nick"] = it.nick ?: ""
            ret["email"] = it.email ?: ""
        }

        if (history == ExportHistory.DAILY) {
            ret["day"] = LocalDate.fromUnix(prediction.ts).toString()
        }
        ret["timestamp"] = prediction.ts.toString()

        when(val dist = prediction.dist) {
            is BinaryDistribution -> ret["estimate"] = dist.yesProb.toString()
            is ContinuousProbabilityDistribution -> {
                ret["mean"] = dist.mean.toString()
                ret["stdDev"] = dist.stdev.toString()
                bucketNames.zip(dist.discretize(buckets).binProbs).map {(name, prob) ->
                    ret[name] = prob.toString()
                }
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

    private suspend fun exportQuestion(question: Question, user: User?): Flow<CsvField> = flow {
        when(history) {
            ExportHistory.LAST -> getQuestionLastPrediction(question, user)?.let {emit(predictionRow(question, user, it))}
            ExportHistory.DAILY -> {
                val dayMap: MutableMap<LocalDate, Prediction> = mutableMapOf()
                getQuestionAllPredictions(question, user).map {
                    val date = LocalDate.fromUnix(it.ts)
                    dayMap[date] = it
                }
                dayMap.values.map { emit(predictionRow(question, user, it)) }
            }
            ExportHistory.FULL -> getQuestionAllPredictions(question, user).map {emit(predictionRow(question, user, it))}
        }
    }

    private suspend fun exportQuestion(question: Question): Flow<CsvField> =
        if (group)
            exportQuestion(question, null)
        else
            flow {
                serverState.userPred[question.ref]?.keys?.map {
                    emitAll(exportQuestion(question, it.deref()))
                }
            }

    suspend fun export() = flow {
        questions.map {
            emitAll(exportQuestion(it))
        }
    }

    private fun escapeCSVField(field: String) =
        if (field.contains(Regex("[\"\r\n,]"))) {
            "\"" + field.replace("\"","\"\"") + "\""
        } else field

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
}