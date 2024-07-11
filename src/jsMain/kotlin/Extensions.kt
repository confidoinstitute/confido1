package tools.confido.extensions

import components.presenter.PresenterPageType
import components.redesign.layout.LayoutMode
import components.redesign.questions.dialog.EditQuestionDialogProps
import components.redesign.questions.dialog.QuestionQuickSettingsDialogProps
import components.redesign.questions.predictions.MyPredictionDescriptionProps
import components.rooms.RoomListProps
import react.ChildrenBuilder
import react.FC
import react.Props
import rooms.Room
import tools.confido.question.Question
import tools.confido.state.PresenterView
import tools.confido.state.SentState
import tools.confido.state.appConfig
import kotlin.reflect.KClass

enum class ExtensionContextPlace {
    QUESTION_PAGE,
    EDIT_QUESTION_DIALOG,
}

interface ClientExtension : Extension {


    companion object {

        val enabled get() = Extension.enabled.map { it as ClientExtension }

        inline fun forEach(f: (ClientExtension)->Unit) {  enabled.forEach(f) }

        val contexts: Map<ExtensionContextPlace, react.Context<Map<String, Any>>> by lazy {
            ExtensionContextPlace.entries.map {
                it to react.createContext<Map<String,dynamic>>()
            }.toMap()
        }

        fun getContextValues(place: ExtensionContextPlace) = enabled.map { it.extensionId to it.getContextValue(place) }.toMap()
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
    fun useExtContext(place: ExtensionContextPlace)  = react.useContext(contexts[place]!!)[extensionId]
    fun getContextValue(place: ExtensionContextPlace) = react.useState<Map<String, dynamic>>(emptyMap())
    fun <T> useContextState(place: ExtensionContextPlace, key: String = "", default: T? = null): react.StateInstance<T> {
        var ctxS by (useExtContext(place).unsafeCast<react.StateInstance<Map<String, dynamic>>>())
        var ctxVal = ctxS
        val sVal = ctxVal[key] ?: default
        fun setter(newVal: T) {
            val newCtxS = ctxVal + mapOf(key to newVal)
            ctxVal = newCtxS
            ctxS = newCtxS
        }
        return arrayOf<dynamic>(sVal, ::setter).unsafeCast<react.StateInstance<T>>()
    }
    fun registerPresenterPages(): Map<KClass<out PresenterView>, PresenterPageType> = emptyMap()
    fun questionManagementExtra(room: Room, cb: ChildrenBuilder) {}
    fun roomTabsExtra(room: Room, appState: SentState, layoutMode: LayoutMode): List<Pair<String, String>>  = emptyList()
    fun roomRoutesExtra(room: Room, cb: ChildrenBuilder) {}
    fun rootLayoutStartHook() {}
}

external interface ExtensionContextProviderProps: Props {
    var place: ExtensionContextPlace
}

val ExtensionContextProvider = FC<ExtensionContextProviderProps> {

}