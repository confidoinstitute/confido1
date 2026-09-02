package tools.confido.web.components.redesign.presentation

import emotion.react.css
import react.FC
import react.use
import react.useEffect
import react.dom.html.ReactHTML.div
import tools.confido.extensions.FlowItemEditorProps
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.rooms.Room
import tools.confido.state.FlowAction
import tools.confido.state.PresenterView
import tools.confido.state.SetPresenterViewAction
import tools.confido.web.components.presenter.ExpectedWorldContext
import tools.confido.web.components.presenter.PresenterControlsType
import tools.confido.web.components.presenter.getPresenterControlsComponent
import tools.confido.web.components.redesign.basic.sansSerif
import tools.confido.web.components.redesign.forms.QuestionPicker
import tools.confido.web.components.redesign.rooms.RoomContext
import web.cssom.Color
import web.cssom.px

/**
 * How a presenter view names its subject. The flow editor renders only the
 * subject picker; every other parameter UI is delegated to the view's shared
 * presenter control (see [pvFlowEditor]) — pages and pickers choose the subject,
 * controls tune the parameters.
 */
sealed interface PVSubject {
    /** The view has no configurable subject (e.g. a blank screen). */
    data object None : PVSubject
    class OfQuestion(
        val filter: (Question, Room) -> Boolean = { _, _ -> true },
        val get: (PresenterView) -> Ref<Question>,
        val set: (PresenterView, Ref<Question>) -> PresenterView,
    ) : PVSubject
    /** The subject is implicitly the room being edited: backfilled from
     *  [RoomContext] (createDefault seeds a placeholder `Ref("")`), no picker. */
    class OfRoom(
        val hint: String? = null,
        val get: (PresenterView) -> Ref<Room>,
        val set: (PresenterView, Room) -> PresenterView,
    ) : PVSubject
}

inline fun <reified V : PresenterView> questionSubject(
    noinline filter: (Question, Room) -> Boolean = { _, _ -> true },
    crossinline get: (V) -> Ref<Question>,
    crossinline set: (V, Ref<Question>) -> V,
): PVSubject = PVSubject.OfQuestion(filter, { get(it as V) }, { v, q -> set(v as V, q) })

inline fun <reified V : PresenterView> roomSubject(
    hint: String? = null,
    crossinline get: (V) -> Ref<Room>,
    crossinline set: (V, Room) -> V,
): PVSubject = PVSubject.OfRoom(hint, { get(it as V) }, { v, r -> set(v as V, r) })

/** Extract the inner [PresenterView] from a [SetPresenterViewAction], or null. */
fun FlowAction.innerView(): PresenterView? = (this as? SetPresenterViewAction)?.view

/** Wrap a [PresenterView] change back into the [SetPresenterViewAction]. */
fun wrapViewChange(onChange: (FlowAction) -> Unit): (PresenterView) -> Unit =
    { view -> onChange(SetPresenterViewAction(view)) }

/**
 * The flow editor for a view-type step: the [subject] picker composed with the
 * view's shared presenter control, so each parameter UI exists exactly once —
 * the same component serves the live control panel and authoring (which the
 * surrounding `ExpectedWorldContext` provider switches into permissive
 * expected-world rendering). Views without registered controls (no parameters)
 * get just the picker.
 *
 * [extraControls] renders *authoring-only* parameters — properties of the flow
 * step that make no sense to flip live and therefore stay out of `view2controls`
 * (e.g. the invite link's joiners-follow toggle). Same props contract as a
 * presenter control, mounted after the shared one.
 */
fun pvFlowEditor(
    subject: PVSubject,
    extraControls: PresenterControlsType? = null,
): FC<FlowItemEditorProps> = FC { props ->
    val room = use(RoomContext)!!
    val questions = useRoomQuestions()
    val world = use(ExpectedWorldContext)
    val view = props.action.innerView()
    val onViewChange = wrapViewChange(props.onChange)
    // Backfill the implicit room subject once; the effect stays above the early
    // return (stable hook order), the null guard lives inside.
    useEffect(room.id) {
        if (subject is PVSubject.OfRoom && view != null && subject.get(view).id != room.id)
            onViewChange(subject.set(view, room))
    }
    if (view == null) return@FC

    when (subject) {
        is PVSubject.OfQuestion -> {
            FieldLabel { +"Question" }
            QuestionPicker {
                this.questions = questions.filter { subject.filter(it, room) }
                selectedId = subject.get(view).id
                currentQuestionOption = world?.loopCandidates != null
                onChange = { onViewChange(subject.set(view, Ref(it))) }
            }
        }
        is PVSubject.OfRoom -> subject.hint?.let { hint ->
            div {
                css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#888"); marginTop = 4.px }
                +hint
            }
        }
        PVSubject.None -> {}
    }

    getPresenterControlsComponent(view)?.let { comp ->
        comp {
            // The mounted control must always match the view class it receives
            // (see the hook-order note in PresenterController.kt) — key by class
            // so a type change remounts instead of re-rendering.
            key = view::class.simpleName
            this.view = view
            this.disabled = false
            this.onChange = onViewChange
        }
    }
    extraControls?.let { comp ->
        comp {
            key = "extra-" + view::class.simpleName
            this.view = view
            this.disabled = false
            this.onChange = onViewChange
        }
    }
}
