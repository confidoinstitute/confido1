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
) + ClientExtension.enabled.map { it.registerPresenterPages() }.reduce{ a,b -> a+b } }

val PresenterPage = FC<PresenterPageProps<PresenterView>> { props->
    val comp : PresenterPageType = (view2page[props.view::class] ?: EmptyPP.unsafeCast<PresenterPageType>())
    comp { this.view = props.view }
}

