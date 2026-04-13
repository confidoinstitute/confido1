package tools.confido.extensions

import io.ktor.server.routing.*
import rooms.EffectiveQuestion
import rooms.Room
import rooms.RoomMembership
import rooms.RoomPermission
import tools.confido.application.UserPresentationContext
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.state.FlowAction
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

    /** Compute the effective member list for a room (e.g. with members inherited from a parent group).
     *  Return null to use room.members as-is (default). */
    fun effectiveMembers(room: Room): List<RoomMembership>? = null

    /** Override effective permissions for a specific membership in a room.
     *  This can be used to restrict or augment permissions based on the room context
     *  (e.g. stripping question-related permissions from group rooms).
     *  Return null to use membership.role.effectivePermissions as-is (default). */
    fun effectivePermissions(room: Room, membership: RoomMembership): Set<RoomPermission>? = null

    /** Return an effective question list for this room (e.g. aggregated from child rooms).
     *  Each entry carries flags controlling display and scoring behavior.
     *  Return null to use room.questions as-is (default). */
    fun effectiveQuestions(room: Room): List<EffectiveQuestion>? = null

    /** Execute a flow action defined by this extension. Return true if handled. */
    open suspend fun executeFlowAction(action: FlowAction, room: Room): Boolean = false

    companion object {
        val enabled get() = Extension.enabled.map{ it as ServerExtension }
        inline fun forEach(f: (ServerExtension)->Unit) {  enabled.forEach(f) }
    }
}