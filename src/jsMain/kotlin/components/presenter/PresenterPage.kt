package components.presenter

import react.FC
import react.Props
import tools.confido.state.*

external interface PresenterPageProps<V: PresenterView> : Props {
    var view: V
}

val EmptyPP = FC<PresenterPageProps<EmptyPV>> {

}

typealias PresenterPageType = FC<PresenterPageProps<PresenterView>>

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
val view2page = mapOf(
    EmptyPV::class to EmptyPP as PresenterPageType,
    InviteLinkPV::class to InviteLinkPP as PresenterPageType,
    QuestionPV::class to QuestionPP as PresenterPageType,
    GroupPredPV::class to GroupPredPP as PresenterPageType,
)

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
val PresenterPage = FC<PresenterPageProps<PresenterView>> { props->
    val comp : PresenterPageType = (view2page[props.view::class] ?: EmptyPP as PresenterPageType)
    comp { this.view = props.view }
}

