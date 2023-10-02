package tools.confido.application.calibration

import tools.confido.calibration.CalibrationVector
import tools.confido.calibration.getCalibration
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.serverState
import users.User


suspend fun getGroupCalibration(q: Question): CalibrationVector {
    val E = CalibrationVector()
    val scoreTS = q.effectiveSchedule.score ?: return CalibrationVector()
    val pred = serverState.groupPredHistManager.at(q.ref, scoreTS) ?: return CalibrationVector()
    return getCalibration(q, pred)
}
suspend fun getUserCalibration(q: Question, u: Ref<User>): CalibrationVector {
    val E = CalibrationVector()
    val scoreTS = q.effectiveSchedule.score ?: return CalibrationVector()
    val pred = serverState.userPredHistManager.at(q.ref, u, scoreTS) ?: return CalibrationVector()
    return getCalibration(q, pred)
}
