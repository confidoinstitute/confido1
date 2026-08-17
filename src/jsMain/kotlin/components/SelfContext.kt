package components

import react.createContext
import react.use
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.state.SelfState
import tools.confido.users.User
import tools.confido.users.UserType

/**
 * The viewer's own state (the self topic slice) as its own context, read through the
 * accessor functions below instead of the AppState facade (client endgame step 2,
 * PERSISTENCE_SYNC_REDESIGN.md §7). React's `use` may be called anywhere during
 * render — conditionals, loops, helper functions — so these are plain functions
 * callable inline at the point of a check; no hook plumbing, no wrapper components.
 *
 * Two caveats inherited from `use` semantics:
 *  - inside a `useMemo` body, read the value *before* the memo and capture it (a
 *    context change would not invalidate the memo otherwise);
 *  - never call these from event handlers (not render time) — capture during render.
 */
val SelfContext = createContext<SelfState>()

fun useSelf(): SelfState = use(SelfContext) ?: SelfState()

/** The viewer's own user — the only User object that exists client-side (§5.3). */
fun useMyUser(): User? = useSelf().user

fun useIsAdmin(): Boolean = useMyUser()?.type == UserType.ADMIN

/** Inline permission check: `if (usePermission(room, RoomPermission.MANAGE_MEMBERS)) …`.
 *  The check itself is the pure commonMain `Room.hasPermission`; this just supplies
 *  the viewer. */
fun usePermission(room: Room, permission: RoomPermission): Boolean =
    room.hasPermission(useMyUser(), permission)

fun useMyPredictions(): Map<Ref<Question>, Prediction> = useSelf().myPredictions
fun useMyPrediction(question: Ref<Question>): Prediction? = useSelf().myPredictions[question]
fun usePresenterInfo() = useSelf().presenterInfo
fun useFollowingPresentation() = useSelf().followingPresentation
fun useMyPasswordIsSet() = useSelf().myPasswordIsSet
fun usePresenterWindowActive() = useSelf().presenterWindowActive
