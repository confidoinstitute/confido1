package tools.confido.extensions

import components.presenter.PresenterPageType
import components.redesign.layout.LayoutMode
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.redesign.questions.dialog.QuestionQuickSettingsDialogProps
import components.redesign.questions.predictions.BinaryPredInputProps
import components.redesign.questions.predictions.MyPredictionDescriptionProps
import components.redesign.rooms.RoomListProps
import react.ChildrenBuilder
import react.FC
import react.Props
import react.createContext
import react.router.RouteObject
import rooms.Room
import rooms.RoomMembership
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.extensions.ExtensionData
import tools.confido.question.Question
import tools.confido.state.FlowAction
import tools.confido.state.PresenterView
import tools.confido.state.SentState
import tools.confido.state.appConfig
import react.PropsWithChildren
import users.User
import utils.panzoom1d.PZState
import kotlin.reflect.KClass

enum class ExtensionContextPlace {
    QUESTION_PAGE,
    EDIT_QUESTION_DIALOG,
}

enum class SidebarPlace {
    WORKSPACE_TOP,      // Before existing workspace items (Calibration stats, etc.)
    WORKSPACE_BOTTOM,   // After existing workspace items
    ROOMS_TOP,          // Before room list
    ROOMS_BOTTOM,       // After room list
    ACTIONS_TOP,        // Before "Give feedback"
    ACTIONS_BOTTOM,     // After "Privacy policy"
}

interface ClientExtension : Extension {

    companion object {

        val enabled get() = Extension.enabled.map { it as ClientExtension }

        inline fun forEach(f: (ClientExtension)->Unit) {  enabled.forEach(f) }

        val contexts: Map<ExtensionContextPlace, react.Context<Map<String, Any>>> by lazy {
            ExtensionContextPlace.entries.associate {
                it to createContext<Map<String, dynamic>>(emptyMap())
            }
        }

        fun getContextValues(place: ExtensionContextPlace) =
            enabled.associate { it.extensionId to it.getContextValue(place) }
    }

    fun rootRoutes(cb: ChildrenBuilder) {}
    fun clientInit() {}
    fun roomListExtra(props: RoomListProps, cb: ChildrenBuilder) {}

    fun questionQuickSettingsExtra(props: QuestionQuickSettingsDialogProps, cb: ChildrenBuilder, onClose: ()->Unit) {}
    enum class QuestionPagePlace {
        QUESTION_PAGE_END
    }
    fun questionPageExtra(q: Question, place: QuestionPagePlace, cb: ChildrenBuilder) {}
    fun editQuestionDialogExtra(props: EditQuestionDialogProps, cb: ChildrenBuilder) {}
    fun assembleQuestion(q: Question, states: Map<String, dynamic>) = q

    fun myPredictionDescriptionExtra(props: MyPredictionDescriptionProps, cb: ChildrenBuilder) {}
    fun useExtContext(place: ExtensionContextPlace)  = react.use(contexts[place]!!)[extensionId]
    fun getContextValue(place: ExtensionContextPlace) = react.useState<Map<String, dynamic>>(emptyMap())
    fun <T> useContextState(place: ExtensionContextPlace, key: String = "", default: T? = null): react.StateInstance<T> {
        var ctxS by (useExtContext(place).unsafeCast<react.StateInstance<Map<String, dynamic>>>())
        var ctxVal = ctxS
        fun setter(newVal: T) {
            val newCtxS = ctxVal + mapOf(key to newVal)
            ctxVal = newCtxS
            ctxS = newCtxS
        }
        if (key !in ctxVal && default != null) setter(default)
        val sVal = ctxVal[key] ?: default
        return arrayOf<dynamic>(sVal, ::setter).unsafeCast<react.StateInstance<T>>()
    }
    fun registerPresenterPages(): Map<KClass<out PresenterView>, PresenterPageType> = emptyMap()
    fun editRoomSettingsExtra(room: Room?, onExtensionDataChange: ((ExtensionData) -> ExtensionData) -> Unit, cb: ChildrenBuilder) {}
    fun roomMemberExtra(room: Room, membership: RoomMembership, user: User, cb: ChildrenBuilder) {}
    fun commentFieldExtra(room: Room, appState: SentState, cb: ChildrenBuilder) {}
    fun roomInfoExtra(room: Room, appState: SentState, cb: ChildrenBuilder) {}
    fun questionManagementExtra(room: Room, cb: ChildrenBuilder) {}
    fun roomTabsExtra(room: Room, appState: SentState, layoutMode: LayoutMode): List<Pair<String, String>>  = emptyList()
    fun roomRoutesExtra(room: Room): Array<RouteObject> = emptyArray()
    fun rootLayoutStartHook() {}
    fun renderPredictionInput(props: components.redesign.questions.predictions.PredictionInputProps): react.ReactNode? = null
    fun  hideDefaultPredictionDesc(dist: ProbabilityDistribution?, question: Question?, b: Boolean) = false
    fun binarySliderExtra(props: BinaryPredInputProps, zoom: PZState?, cb: ChildrenBuilder) {}
    fun sidebarExtra(place: SidebarPlace, small: Boolean, cb: ChildrenBuilder) {}

    /** Override room entries rendering in the sidebar (the room links portion, not the "Create room" link).
     *  Return true if the extension handled rendering (default entries are skipped).
     *  Return false to fall back to default rendering.
     *  The extension can reuse [components.redesign.rooms.RoomListEntries] for rendering room entries. */
    fun renderSidebarRoomEntries(rooms: Map<String, Room>, small: Boolean, cb: ChildrenBuilder): Boolean = false

    fun rootRoutesExtra(): List<RouteObject> = emptyList()

    /**
     * Register custom flow action types for the presentation editor.
     * Each entry is a [FlowActionTypeEntry] providing the type key, label,
     * default factory, editor component, and a predicate to match existing actions.
     */
    fun registerFlowActionTypes(): List<FlowActionTypeEntry> = emptyList()

    /**
     * Register custom presenter view types for the presentation editor.
     * Each entry is a [PresenterViewTypeEntry] providing the type key, label,
     * default factory, editor component (optional), and a predicate to match existing views.
     */
    fun registerPresenterViewTypes(): List<PresenterViewTypeEntry> = emptyList()
}

// ---- Flow Action Type Registry ----

/** Props for a flow action editor component. The editor reads room/questions via contexts. */
external interface FlowActionEditorProps : Props {
    var action: FlowAction
    var onChange: (FlowAction) -> Unit
}

/** Describes a flow action type for the presentation editor UI. */
data class FlowActionTypeEntry(
    val key: String,
    val label: String,
    val createDefault: () -> FlowAction,
    val editor: FC<FlowActionEditorProps>,
    /** Predicate to identify which existing actions this entry handles. */
    val matches: (FlowAction) -> Boolean,
)

// ---- Presenter View Type Registry ----

/** Props for a presenter view editor component. The editor reads room/questions via contexts. */
external interface PresenterViewEditorProps : Props {
    var view: PresenterView
    var onChange: (PresenterView) -> Unit
}

/** Describes a presenter view type for the presentation editor UI. */
data class PresenterViewTypeEntry(
    val key: String,
    val label: String,
    val createDefault: () -> PresenterView,
    /** Editor component, or null if no configuration is needed (e.g. EmptyPV). */
    val editor: FC<PresenterViewEditorProps>?,
    /** Predicate to identify which existing views this entry handles. */
    val matches: (PresenterView) -> Boolean,
)

external interface ExtensionContextProviderProps: Props {
    var place: ExtensionContextPlace
}

val ExtensionContextProvider = FC<ExtensionContextProviderProps> {

}

