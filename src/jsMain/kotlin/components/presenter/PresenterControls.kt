package components.presenter

import components.redesign.forms.FormSwitch
import react.FC
import react.Props
import tools.confido.extensions.ClientExtension
import tools.confido.refs.deref
import tools.confido.state.*
import kotlin.reflect.KClass

external interface PresenterControlsProps<V: PresenterView> : Props {
    var view: V
    /** Called with an updated copy of the view; the control panel persists it via /presenter/set. */
    var onChange: (PresenterView) -> Unit
    var disabled: Boolean
}

typealias PresenterControlsType = FC<PresenterControlsProps<PresenterView>>

inline fun <reified V: PresenterView> presenterControlsMap(pc: FC<PresenterControlsProps<V>>) =
    V::class as KClass<out PresenterView> to pc.unsafeCast<PresenterControlsType>()

/** Controls for the group prediction view: toggle resolution display. */
val GroupPredPC = FC<PresenterControlsProps<GroupPredPV>> { props ->
    val question = props.view.question.deref() ?: return@FC
    FormSwitch {
        label = "Show resolution"
        disabled = props.disabled || question.resolution == null
        checked = props.view.showResolution && question.resolution != null
        onChange = { e -> props.onChange(props.view.copy(showResolution = e.target.checked)) }
        if (question.resolution == null)
            comment = "This question has no resolution yet."
    }
}

/** Controls for the binary histogram view: toggle showing predictor nicknames. */
val BinaryHistogramPC = FC<PresenterControlsProps<BinaryHistogramPV>> { props ->
    FormSwitch {
        label = "Show nicknames"
        disabled = props.disabled
        checked = props.view.showNames
        onChange = { e -> props.onChange(props.view.copy(showNames = e.target.checked)) }
        comment = "Shows who predicted what. Available only if you may view individual predictions."
    }
}

/** Controls for the multi-question view: toggle group prediction, histogram, and resolution. */
val MultiQuestionPC = FC<PresenterControlsProps<MultiQuestionPV>> { props ->
    FormSwitch {
        label = "Show group predictions"
        disabled = props.disabled
        checked = props.view.showGroupPred
        onChange = { e -> props.onChange(props.view.copy(showGroupPred = e.target.checked)) }
    }
    FormSwitch {
        label = "Show prediction histogram"
        disabled = props.disabled
        checked = props.view.showHistogram
        onChange = { e -> props.onChange(props.view.copy(showHistogram = e.target.checked)) }
        comment = "Histogram of individual predictions (binary questions only)."
    }
    FormSwitch {
        label = "Show resolution"
        disabled = props.disabled
        checked = props.view.showResolution
        onChange = { e -> props.onChange(props.view.copy(showResolution = e.target.checked)) }
    }
}

val view2controls by lazy { mapOf(
    presenterControlsMap(GroupPredPC),
    presenterControlsMap(BinaryHistogramPC),
    presenterControlsMap(components.redesign.presentation.PresentationPC),
    presenterControlsMap(MultiQuestionPC),
) + ClientExtension.enabled.map { it.registerPresenterControls() }.fold(emptyMap()) { a, b -> a+b } }

/** Look up the control panel component for a given view type (null if the view has no controls). */
fun getPresenterControlsComponent(view: PresenterView): PresenterControlsType? = view2controls[view::class]
