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

suspend fun getScoredPrediction(q: Question, u: Ref<User>? = null): Prediction? {
    val scoreTS = q.effectiveSchedule.score ?: return null
    val pred = if (u == null) groupPredictionLog.at(q.ref, scoreTS)
                else userPredictionLog.at(q.ref, u, scoreTS)
    return pred
}

suspend fun getDbCalibration(q: Question, u: Ref<User>?=null): CalibrationVector {
    return getCalibration(q, getScoredPrediction(q, u) ?: return CalibrationVector())
}
