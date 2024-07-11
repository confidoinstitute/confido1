package tools.confido.extensions

import extensions.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import rooms.Room
import rooms.RoomPermission
import tools.confido.application.routes.*
import tools.confido.application.sessions.TransientData
import tools.confido.application.sessions.modifyUserSession
import tools.confido.application.sessions.userSession
import tools.confido.question.Question
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

object MillionaireServerExtension : ServerExtension, MillionaireExt() {
    fun userScore(room: Room, user: Ref<User>): Double? {
        var score: Double? = null
        questions(room).forEach {
            val q = serverState.questions[it.id] ?: return@forEach
            println("${q.id}, ${q.resolution!=null}, ${q.open} ${user} ${score}")
            if (q.resolution != null && q.state == QuestionState.RESOLVED) {
                computeScore(serverState.userPred[q.ref]?.get(user), q.resolution)?.let {
                    score = (score ?: INITIAL_SCORE) * it
                }
            }
            println("${q.id}, ${q.resolution!=null}, ${q.open} ${user} ${score}")
        }
        return score
    }
    override fun initRoutes(r: Routing) {
        r.getWS("/api$roomUrl/ext/millionaire/my_score.ws") {
            withRoom {
                withUser {
                    userScore(room, user.ref) ?: INITIAL_SCORE
                }
            }
        }
        r.getWS("/api$roomUrl/ext/millionaire/scoreboard.ws") {
            withRoom {
                assertPermission(RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS)
                room.members.mapNotNull {
                    it.user to (userScore(room, it.user) ?: return@mapNotNull null)
                }.toMap()
            }
        }

        suspend fun setState(room: Room, state: MillionaireState) {

            serverState.withTransaction {
                questions(room).forEachIndexed { idx, q ->
                    serverState.questionManager.modifyEntity(q.id) {
                        val newState =
                            if (idx < state.curIndex) QuestionState.RESOLVED
                            else if (idx == state.curIndex) {
                                when (state.type) {
                                    MillionaireStateType.ASKING-> QuestionState.OPEN
                                    MillionaireStateType.RESOLVED -> QuestionState.RESOLVED
                                    MillionaireStateType.BEFORE_START -> QuestionState.DRAFT
                                }
                            } else {
                                QuestionState.DRAFT
                            }
                        it.withState(newState).copy(groupPredVisible = (newState==QuestionState.RESOLVED), groupPredRequirePrediction = false)
                    }
                }
            }
        }

        r.postST("/api/$roomUrl/ext/millionaire/state") {
            withRoom {
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
