package tools.confido.extensions

import io.ktor.server.routing.*
import rooms.Room
import tools.confido.application.UserPresentationContext
import tools.confido.question.Prediction
import tools.confido.question.Question
import users.User
import users.UserPresentation

interface ServerExtension: Extension {
    suspend fun onPrediction(q: Question, p: Prediction) {

    }

    suspend fun serverInit() {

    }

    fun calculateGroupPred(q: Question, pred: Collection<Prediction>): Prediction? = null

    fun initRoutes(r: Routing) {

    }

    fun getUserPresentation(user: User?, observer: User, context: UserPresentationContext, room: Room?): UserPresentation? = null

    /** Censor room data before sending to a client. Called after the built-in censoring. */
    fun censorRoom(room: Room, user: User?): Room = room

    /** Censor question data before sending to a client. Called after the built-in censoring. */
    fun censorQuestion(question: Question, room: Room, user: User?): Question = question

    companion object {
        val enabled get() = Extension.enabled.map{ it as ServerExtension }
        inline fun forEach(f: (ServerExtension)->Unit) {  enabled.forEach(f) }
    }
}