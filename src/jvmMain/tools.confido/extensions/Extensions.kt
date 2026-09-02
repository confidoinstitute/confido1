package tools.confido.extensions

import io.ktor.server.routing.*
import tools.confido.rooms.EffectiveQuestion
import tools.confido.rooms.Room
import tools.confido.rooms.RoomMembership
import tools.confido.rooms.RoomPermission
import tools.confido.application.PermClass
import tools.confido.application.UserPresentationContext
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.state.FlowAction
import tools.confido.state.FlowItem
import tools.confido.users.User
import tools.confido.users.UserPresentation

interface ServerExtension: Extension {
    suspend fun onPrediction(q: Question, p: Prediction) {

    }

    suspend fun serverInit() {

    }

    fun calculateGroupPred(q: Question, pred: Collection<Prediction>): Prediction? = null

    fun initRoutes(r: Routing) {

    }

    /** Override how [user] is presented to [observer] in [context]; null = default
     *  presentation. Named so it does not shadow the core `getUserPresentation`
     *  function inside extension objects (that shadowing once made update_scatter_plot
     *  silently call this default and ship null presentations). */
    fun overrideUserPresentation(user: User?, observer: User, context: UserPresentationContext, room: Room?,
                                 question: Question? = null): UserPresentation? = null

    /** Censor room data before sending to a client. Called after the built-in censoring. */
    fun censorRoom(room: Room, user: User?): Room = room

    /** Censor question data before sending to a client. Called after the built-in
     *  censoring. Question topics are *shared* per permission class (§4.3), so this
     *  hook receives the viewer's [PermClass] rather than a user — it must be a pure
     *  function of it (deliberate: a per-viewer read here would leak between
     *  same-class viewers). Genuinely per-viewer data belongs in an extension-owned
     *  per-viewer topic instead. */
    fun censorQuestion(question: Question, room: Room, cls: PermClass): Question = question

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

    /**
     * Expand an expandable (meta) flow action into a flat list of concrete flow items.
     * Called at presentation start for each [ActionItem]. Return null if this extension
     * does not handle the given action. Return a list of [FlowItem]s to replace the
     * original action item in the expanded flow.
     */
    open fun expandFlowItem(action: FlowAction, room: Room): List<FlowItem>? = null

    /** Called when a presentation run is stopped, allowing extensions to clean up transient state. */
    open fun onPresentationStopped(room: Room) {}

    companion object {
        val enabled get() = Extension.enabled.map{ it as ServerExtension }
        inline fun forEach(f: (ServerExtension)->Unit) {  enabled.forEach(f) }
    }
}