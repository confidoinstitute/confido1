package tools.confido.application.calibration

import tools.confido.calibration.CalibrationVector
import tools.confido.calibration.getCalibration
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.serverState
import users.User

suspend fun getScoredPrediction(q: Question, u: Ref<User>? = null): Prediction? {
    val scoreTS = q.effectiveSchedule.score ?: return null
    val pred = if (u == null) serverState.groupPredHistManager.at(q.ref, scoreTS)
                else serverState.userPredHistManager.at(q.ref, u, scoreTS)
    return pred
}

suspend fun getDbCalibration(q: Question, u: Ref<User>?=null): CalibrationVector {
    return getCalibration(q, getScoredPrediction(q, u) ?: return CalibrationVector())
}
