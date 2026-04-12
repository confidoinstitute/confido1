package extensions.millionaire

import tools.confido.extensions.ServerExtension
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import rooms.Room
import rooms.RoomPermission
import tools.confido.application.routes.*
import tools.confido.application.sessions.TransientData
import tools.confido.application.sessions.modifyUserSession
import tools.confido.application.sessions.userSession
import tools.confido.application.singleThreadContext
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.EmptyPV
import tools.confido.state.PresenterInfo
import tools.confido.state.QuestionPV
import tools.confido.state.serverState
import tools.confido.utils.mapDeref
import tools.confido.utils.unixNow
import users.User

object MillionaireSE : ServerExtension, MillionaireExt() {

    // --- Timer state (in-memory, lost on restart) ---

    private val timerStates = mutableMapOf<String, TimerState>()   // roomId -> TimerState
    private val timerTasks = mutableMapOf<String, java.util.TimerTask>()
    private val scheduler = java.util.Timer("millionaire-timer", true)

    private fun startTimer(room: Room, durationSeconds: Int) {
        cancelTimer(room)
        val state = TimerState(
            startedAt = System.currentTimeMillis().toDouble(),
            durationMs = durationSeconds * 1000,
            running = true,
        )
        timerStates[room.id] = state

        val task = object : java.util.TimerTask() {
            override fun run() {
                @Suppress("OPT_IN_USAGE")
                GlobalScope.launch(singleThreadContext) {
                    timerStates.remove(room.id)
                    timerTasks.remove(room.id)
                    // Re-read room state; auto-close current question if still asking
                    val freshRoom = serverState.rooms[room.id] ?: return@launch
                    val currentState = getState(freshRoom)
                    if (currentState.type == MillionaireStateType.ASKING) {
                        setState(freshRoom, MillionaireState(MillionaireStateType.RESOLVED, currentState.curIndex, null))
                        TransientData.refreshAllWebsockets()
                    }
                }
            }
        }
        timerTasks[room.id] = task
        scheduler.schedule(task, durationSeconds * 1000L)
    }

    private fun cancelTimer(room: Room) {
        timerTasks.remove(room.id)?.cancel()
        timerStates.remove(room.id)
    }

    // --- Scoring ---

    fun userScore(room: Room, user: Ref<User>): Double? {
        val qs = questions(room)
        return when (MillionaireConfig.mode) {
            MillionaireMode.MULTIPLICATIVE -> {
                var score: Double? = null
                qs.forEach {
                    val q = serverState.questions[it.id] ?: return@forEach
                    if (q.resolution != null && q.state == QuestionState.RESOLVED) {
                        computeScoreMultiplicative(serverState.userPred[q.ref]?.get(user), q.resolution)?.let {
                            score = (score ?: INITIAL_SCORE) * it
                        }
                    }
                }
                score
            }
            MillionaireMode.LADDER -> {
                val ladder = MillionaireConfig.prizeLadderForCount(qs.size)
                var score: Double? = null
                qs.forEachIndexed { idx, it ->
                    val q = serverState.questions[it.id] ?: return@forEachIndexed
                    if (q.resolution != null && q.state == QuestionState.RESOLVED) {
                        val prizeValue = ladder.getOrElse(idx) { ladder.lastOrNull() ?: 0 }
                        computeScoreLadder(serverState.userPred[q.ref]?.get(user), q.resolution, prizeValue)?.let {
                            score = (score ?: 0.0) + it
                        }
                    }
                }
                score
            }
        }
    }

    // --- State transitions ---

    private suspend fun setState(room: Room, state: MillionaireState) {
        serverState.withTransaction {
            questions(room).forEachIndexed { idx, q ->
                serverState.questionManager.modifyEntity(q.id) {
                    val newState =
                        if (idx < state.curIndex) QuestionState.RESOLVED
                        else if (idx == state.curIndex) {
                            when (state.type) {
                                MillionaireStateType.ASKING -> QuestionState.OPEN
                                MillionaireStateType.RESOLVED -> QuestionState.RESOLVED
                                MillionaireStateType.BEFORE_START -> QuestionState.DRAFT
                            }
                        } else {
                            QuestionState.DRAFT
                        }
                    it.withState(newState).copy(
                        groupPredVisible = (newState == QuestionState.RESOLVED),
                        groupPredRequirePrediction = false,
                    )
                }
            }
        }

        // Timer integration: auto-start on ASKING (fixed mode), cancel on non-ASKING
        when (state.type) {
            MillionaireStateType.ASKING -> {
                val tc = MillionaireConfig.timerConfig
                if (tc is TimerConfig.Fixed) {
                    startTimer(room, tc.seconds)
                } else {
                    // Cancel any leftover timer from previous question
                    cancelTimer(room)
                }
            }
            else -> cancelTimer(room)
        }
    }

    // --- Routes ---

    override fun initRoutes(r: Routing) {
        // User's own score (WebSocket)
        r.getWS("/api$roomUrl/ext/millionaire/my_score.ws") {
            call.withRoom {
                call.withUser {
                    userScore(room, user.ref) ?: when (MillionaireConfig.mode) {
                        MillionaireMode.MULTIPLICATIVE -> INITIAL_SCORE
                        MillionaireMode.LADDER -> 0.0
                    }
                }
            }
        }

        // Scoreboard for all players (WebSocket, requires permission)
        r.getWS("/api$roomUrl/ext/millionaire/scoreboard.ws") {
            call.withRoom {
                assertPermission(RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS)
                room.members.mapNotNull {
                    it.user to (userScore(room, it.user) ?: return@mapNotNull null)
                }.toMap()
            }
        }

        // Timer state (WebSocket)
        r.getWS("/api$roomUrl/ext/millionaire/timer.ws") {
            call.withRoom {
                timerStates[room.id]
            }
        }

        // Timer control (start/stop)
        r.postST("/api/$roomUrl/ext/millionaire/timer") {
            call.withRoom {
                assertPermission(RoomPermission.MANAGE_QUESTIONS)
                val action = call.receiveText().trim()
                when (action) {
                    "start" -> {
                        val tc = MillionaireConfig.timerConfig
                        val seconds = when (tc) {
                            is TimerConfig.Fixed -> tc.seconds
                            is TimerConfig.Countdown -> tc.seconds
                            TimerConfig.None -> badRequest("Timer not configured")
                        }
                        startTimer(room, seconds)
                    }
                    "stop" -> cancelTimer(room)
                    else -> badRequest("Unknown timer action: $action")
                }
                TransientData.refreshAllWebsockets()
                call.respond(HttpStatusCode.OK)
            }
        }

        // Game state control
        r.postST("/api/$roomUrl/ext/millionaire/state") {
            call.withRoom {
                assertPermission(RoomPermission.MANAGE_QUESTIONS)
                setState(room, call.receive())
                val st = getState(room)
                if (call.userSession?.presenterInfo?.view !is MillionairePV)
                    call.modifyUserSession {
                        val oldInfo = it.presenterInfo ?: PresenterInfo()
                        val newInfo = oldInfo.copy(view = MillionairePV(room.ref), lastUpdate = unixNow())
                        it.copy(presenterInfo = newInfo)
                    }
                TransientData.refreshAllWebsockets()
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
