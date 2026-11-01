package tools.confido.application.calibration

import tools.confido.calibration.CalibrationVector
import tools.confido.calibration.getCalibration
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.store.groupPredictionLog
import tools.confido.store.userPredictionLog
import tools.confido.users.User
import tools.confido.question.effectiveSchedule

/**
 * The prediction used for scoring and calibration: the last one submitted before the
 * question's score time, or the current (latest) one when no score time is set.
 */
suspend fun getScoredPrediction(q: Question, u: Ref<User>? = null): Prediction? {
    val scoreTS = q.effectiveSchedule.score
    val pred = if (u == null) groupPredictionLog.at(q.ref, scoreTS)
                else userPredictionLog.at(q.ref, u, scoreTS)
    return pred
}

suspend fun getDbCalibration(q: Question, u: Ref<User>?=null): CalibrationVector {
    return getCalibration(q, getScoredPrediction(q, u) ?: return CalibrationVector())
}
