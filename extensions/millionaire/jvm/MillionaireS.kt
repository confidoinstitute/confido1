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
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tools.confido.application.getUserPresentation
import tools.confido.application.UserPresentationContext
import tools.confido.application.routes.*
import tools.confido.store.*
import tools.confido.application.sessions.TransientData
import tools.confido.application.singleThreadContext
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.state.*
import tools.confido.utils.generateId
import tools.confido.utils.mapDeref
import users.User

object MillionaireSE : ServerExtension, MillionaireExt() {

    // --- Active game config per room (set during presentation expansion, cleared on stop) ---

    private val _activeConfigs = tools.confido.store.TransientCell<PersistentMap<String, MillionaireGameAction>>(persistentMapOf())
    val activeConfigsState: StateFlow<PersistentMap<String, MillionaireGameAction>> get() = _activeConfigs.state
    val activeConfigs: Map<String, MillionaireGameAction> get() = _activeConfigs.value

    // --- Timer state (in-memory, lost on restart) ---

    private val _timerStates = tools.confido.store.TransientCell<PersistentMap<String, TimerState>>(persistentMapOf())
    val timerStatesState: StateFlow<PersistentMap<String, TimerState>> get() = _timerStates.state
    private val timerStates: Map<String, TimerState> get() = _timerStates.value   // roomId -> TimerState
    private val timerTasks = mutableMapOf<String, java.util.TimerTask>()
    private val scheduler = java.util.Timer("millionaire-timer", true)

    private suspend fun startTimer(room: Room, durationSeconds: Int) {
        cancelTimer(room)
        val state = TimerState(
            startedAt = System.currentTimeMillis().toDouble(),
            durationMs = durationSeconds * 1000,
            running = true,
        )
        _timerStates.update { it.put(room.id, state) }

        val task = object : java.util.TimerTask() {
            override fun run() {
                @Suppress("OPT_IN_USAGE")
                GlobalScope.launch(singleThreadContext) {
                    _timerStates.update { it.remove(room.id) }
                    timerTasks.remove(room.id)
                    // Re-read room state; auto-close current question if still asking
                    val freshRoom = serverState.rooms[room.id] ?: return@launch
                    val config = activeConfigs[room.id] ?: return@launch
                    val currentState = getState(freshRoom.questions.mapDeref(), config.tag)
                    if (currentState.type == MillionaireStateType.ASKING) {
                        setState(freshRoom, config, MillionaireState(MillionaireStateType.RESOLVED, currentState.curIndex, null))
                    }
                }
            }
        }
        timerTasks[room.id] = task
        scheduler.schedule(task, durationSeconds * 1000L)
    }

    private suspend fun cancelTimer(room: Room) {
        timerTasks.remove(room.id)?.cancel()
        _timerStates.update { it.remove(room.id) }
    }

    // --- Scoring ---

    fun userScore(room: Room, user: Ref<User>, config: MillionaireGameAction): Double? {
        val qs = questions(room.questions.mapDeref(), config.tag)
        return when (config.mode) {
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
                val ladder = config.prizeLadderForCount(qs.size)
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

    private suspend fun setState(room: Room, config: MillionaireGameAction, state: MillionaireState) {
        tools.confido.store.transact {
            questions(room.questions.mapDeref(), config.tag).forEachIndexed { idx, q ->
                tools.confido.store.questionStore.modify(q.id) {
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
                val tc = config.timer
                if (tc is TimerConfig.Fixed) {
                    startTimer(room, tc.seconds)
                } else {
                    cancelTimer(room)
                }
            }
            else -> cancelTimer(room)
        }
    }

    // --- Expandable flow item: produce the millionaire game sequence ---

    override suspend fun expandFlowItem(action: FlowAction, room: Room): List<FlowItem>? {
        val config = action as? MillionaireGameAction ?: return null

        // Store active config for this room (used by WS endpoints and timer)
        _activeConfigs.update { it.put(room.id, config) }

        val qs = questions(room.questions.mapDeref(), config.tag)
        if (qs.isEmpty()) return emptyList()

        return buildList {
            add(NoteItem(id = generateId(), text = "Millionaire game start (${qs.size} questions)", warning = false))

            qs.forEachIndexed { idx, q ->
                val qName = q.name

                // Open question
                val askingPV: PresenterView = when (config.mode) {
                    MillionaireMode.LADDER -> MillionaireLadderQuestionPV(room.ref)
                    MillionaireMode.MULTIPLICATIVE -> {
                        QuestionPV(q.ref)
                    }
                }
                add(ActionItem(
                    id = generateId(),
                    action = MillionaireSetStateAction(
                        stateType = MillionaireStateType.ASKING,
                        curIndex = idx,
                        tag = config.tag,
                        questionName = qName,
                        presenterView = askingPV,
                        questionRef = q.ref,
                    ),
                ))

                // Manual countdown timer trigger (if configured)
                if (config.timer is TimerConfig.Countdown) {
                    add(ActionItem(
                        id = generateId(),
                        action = MillionaireStartTimerAction(
                            seconds = (config.timer as TimerConfig.Countdown).seconds,
                        ),
                    ))
                }

                // Close question
                val resolvedPV: PresenterView? = if (config.showScoreboardAfterEach) {
                    MillionaireScoreboardPV(room.ref)
                } else null
                add(ActionItem(
                    id = generateId(),
                    action = MillionaireSetStateAction(
                        stateType = MillionaireStateType.RESOLVED,
                        curIndex = idx,
                        tag = config.tag,
                        questionName = qName,
                        presenterView = resolvedPV,
                        questionRef = q.ref,
                    ),
                ))
            }

            // Final scoreboard if not already shown
            if (!config.showScoreboardAfterEach) {
                add(ActionItem(
                    id = generateId(),
                    action = SetPresenterViewAction(MillionaireScoreboardPV(room.ref)),
                ))
            }

            add(NoteItem(id = generateId(), text = "Millionaire game end", warning = false))
        }
    }

    // --- Execute per-step actions ---

    override suspend fun executeFlowAction(action: FlowAction, room: Room): Boolean {
        if (action is MillionaireStartTimerAction) {
            startTimer(room, action.seconds)
            return true
        }
        val setStateAction = action as? MillionaireSetStateAction ?: return false
        val config = activeConfigs[room.id] ?: return false
        setState(room, config, MillionaireState(setStateAction.stateType, setStateAction.curIndex, null))
        return true
    }

    // --- Cleanup on presentation stop ---

    override suspend fun onPresentationStopped(room: Room) {
        _activeConfigs.update { it.remove(room.id) }
        cancelTimer(room)
    }

    // --- Routes ---

    override fun initRoutes(r: Routing) {
        // Active game config (WebSocket) — consumed by clients for theme/scoring
        r.viewWS(millionaireConfigTopic, gate = { rRef ->
            val rId = rRef.id
            combineViews(roomStore.state.asView().at(rId), activeConfigsState.asView().at(rId))
        }) { rRef, _ ->
            call.withRoom(rRef) {
                assertPermission(RoomPermission.VIEW_QUESTIONS, "You cannot view this room.")
                activeConfigs[room.id]
            }
        }

        // (my_score.ws deleted: it had no client consumers.)

        // Scoreboard for all players (WebSocket, requires permission)
        r.viewWS(millionaireScoreboardTopic, gate = { rRef ->
            val rId = rRef.id
            combineViews(
                roomStore.state.asView().at(rId),
                questionStore.state.asView(),
                userStore.state.asView(),
                latestPredictions.state.asView(),
                activeConfigsState.asView().at(rId),
            )
        }) { rRef, _ ->
            call.withRoom(rRef) {
                assertPermission(RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS)
                val config = activeConfigs[room.id] ?: return@withRoom emptyList<MillionaireScoreEntry>()
                room.members.mapNotNull { m ->
                    val score = userScore(room, m.user, config) ?: return@mapNotNull null
                    MillionaireScoreEntry(
                        getUserPresentation(m.user.deref(), user, UserPresentationContext.SCOREBOARD, room),
                        score,
                    )
                }.sortedByDescending { it.score }
            }
        }

        // Timer state (WebSocket)
        r.viewWS(millionaireTimerTopic, gate = { rRef ->
            val rId = rRef.id
            combineViews(roomStore.state.asView().at(rId), timerStatesState.asView().at(rId))
        }) { rRef, _ ->
            call.withRoom(rRef) {
                assertPermission(RoomPermission.VIEW_QUESTIONS, "You cannot view this room.")
                timerStates[room.id]
            }
        }

        // Timer control (start/stop)
        r.postST("/api/$roomUrl/ext/millionaire/timer") {
            call.withRoom {
                assertPermission(RoomPermission.MANAGE_QUESTIONS)
                val config = activeConfigs[room.id] ?: badRequest("No active millionaire game")
                val action = call.receiveText().trim()
                when (action) {
                    "start" -> {
                        val tc = config.timer
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
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
