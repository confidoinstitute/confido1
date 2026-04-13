package components.presenter

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
    presenterPageMap(InviteLinkPP),
    presenterPageMap(CalibrationReqPP),
    presenterPageMap(SlidePP),
    presenterPageMap(PresentationPP),
) + ClientExtension.enabled.map { it.registerPresenterPages() }.fold(emptyMap()) { a, b -> a+b } }

/** Look up the presenter page component for a given view type. */
fun getPresenterPageComponent(view: PresenterView): PresenterPageType =
    view2page[view::class] ?: EmptyPP.unsafeCast<PresenterPageType>()

val PresenterPage = FC<PresenterPageProps<PresenterView>> { props->
    val comp = getPresenterPageComponent(props.view)
    comp { this.view = props.view }
}

