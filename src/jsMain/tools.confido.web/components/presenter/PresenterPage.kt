package tools.confido.web.components.presenter

import react.FC
import react.Props
import tools.confido.extensions.ClientExtension
import tools.confido.state.*
import kotlin.reflect.KClass

external interface PresenterPageProps<V: PresenterView> : Props {
    var view: V
}

val EmptyPP = FC<PresenterPageProps<EmptyPV>> {

}

typealias PresenterPageType = FC<PresenterPageProps<PresenterView>>

inline fun <reified  V: PresenterView> presenterPageMap(pp: FC<PresenterPageProps<V>>) =
    V::class as KClass<out PresenterView> to pp.unsafeCast<PresenterPageType>()

val view2page by lazy { mapOf(
    presenterPageMap(EmptyPP),
    presenterPageMap(QuestionPP),
    presenterPageMap(GroupPredPP),
    presenterPageMap(BinaryHistogramPP),
    presenterPageMap(InviteLinkPP),
    presenterPageMap(CalibrationReqPP),
    presenterPageMap(SlidePP),
    presenterPageMap(PresentationPP),
    presenterPageMap(MultiQuestionPP),
    presenterPageMap(TimelinePP),
    presenterPageMap(ComparePP),
) + ClientExtension.enabled.map { it.registerPresenterPages() }.fold(emptyMap()) { a, b -> a+b } }

/** Look up the presenter page component for a given view type. */
fun getPresenterPageComponent(view: PresenterView): PresenterPageType =
    view2page[view::class] ?: EmptyPP.unsafeCast<PresenterPageType>()

/** React key for a view's page: *what* it shows (type + questions), not *how*.
 *  Switching to another question remounts, so the page can't flash the previous
 *  question's data under the new title; display toggles (showResolution, ...)
 *  update in place instead of remounting and resubscribing everything. */
fun PresenterView.pageKey() = this::class.simpleName + ":" + questionRefs().joinToString(",") { it.id }

val PresenterPage = FC<PresenterPageProps<PresenterView>> { props->
    val comp = getPresenterPageComponent(props.view)
    comp {
        key = props.view.pageKey()
        this.view = props.view
    }
}

