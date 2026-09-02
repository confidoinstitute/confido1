package tools.confido.question

import tools.confido.rooms.AttributionPolicy
import tools.confido.rooms.Room
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.state.serverState

// Server-side conveniences over the resident folds. These used to be commonMain
// properties reading globalState; the client now gets these facts from topic
// payloads (question counts topic, room questions slice, RoomContext) instead,
// so the reverse lookups live only where the folds do.

val Question.room: Room? get() = serverState.questionRoom[ref]?.deref()
val Question.numPredictions: Int get() = serverState.predictionCount[ref] ?: 0
val Question.numPredictors: Int get() = serverState.predictorCount[ref] ?: 0
val Question.effectiveSchedule: QuestionSchedule get() = effectiveScheduleIn(room)
val Question.effectiveAttribution: AttributionPolicy get() = effectiveAttributionIn(room)
