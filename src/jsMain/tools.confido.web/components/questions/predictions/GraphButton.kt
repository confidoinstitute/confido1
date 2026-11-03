package tools.confido.web.components.questions.predictions

import tools.confido.web.components.*
import tools.confido.web.components.basic.MainPalette
import tools.confido.web.components.basic.Stack
import tools.confido.web.components.basic.withStyle
import tools.confido.web.components.forms.Button
import tools.confido.web.components.forms.Switch
import tools.confido.web.components.forms.SwitchProps
import tools.confido.web.components.layout.LayoutMode
import tools.confido.web.components.layout.LayoutModeContext
import tools.confido.web.components.presenter.PresenterContext
import tools.confido.web.components.questions.dialog.ExactEstimateDialog
import web.cssom.*
import web.html.HTMLElement
import emotion.react.css
import tools.confido.web.hooks.useEventListener
import react.*
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.Space
import tools.confido.state.GroupPredPV
import web.pointer.POINTER_DOWN
import web.pointer.PointerEvent

val GraphButton = Button.withStyle {
    width = 30.px
    height = 30.px
    display = Display.flex
    padding = 0.px
    margin = 0.px
    justifyContent = JustifyContent.center
    alignItems = AlignItems.center
}

enum class Side { LEFT, RIGHT}
external interface GraphButtonContainerProps: PropsWithChildren {
    var side: Side?
}

val GraphButtonContainer = FC<GraphButtonContainerProps> { props->
    // Prevent creating estimate when clicking on graph buttons
    val pointerRE = useEventListener<HTMLElement, PointerEvent>(PointerEvent.POINTER_DOWN) { it.stopPropagation() }
    val side = props.side ?: Side.RIGHT
    Stack {
        direction = FlexDirection.row
        css {
            position = Position.absolute
            top = 8.px
            if (side == Side.RIGHT)
                right = 8.px
            else
                left = 8.px
            height = 30.px
            gap = 4.px
            zIndex = integer(10)
        }
        ref = pointerRE
        +props.children
    }
}

external interface GraphPresenterButtonProps: Props {
    var question: Question
    /** The graph's zoomed value range, carried to the presenter screen. */
    var view: ClosedFloatingPointRange<Double>?
}

val GraphPresenterButton = FC<GraphPresenterButtonProps> { props->
    val presenterCtl = use(PresenterContext)!!
    GraphButton {
        this.palette = MainPalette.secondary
        PresenterIcon {}
        onClick = {
            presenterCtl.offer(GroupPredPV(props.question.ref, false, view = props.view))
        }
    }
}

external interface GraphExactPredictionButtonProps: Props {
    var question: Question
    var dist: ProbabilityDistribution?
}

val GraphExactPredictionButton: FC<GraphExactPredictionButtonProps> = FC { props ->
    var open by useState(false)
    ExactEstimateDialog {
        this.open = open
        dist = props.dist
        onClose = { open = false }
        question = props.question
    }

    GraphButton {
        this.palette = MainPalette.primary
        this.className = ClassName("pencil-button")
        ExactPredictionIcon {}
        onClick = { open = true }
    }
}

external interface GraphHistogramButtonProps: Props {
    var onClick: (() -> Unit)?
}

val GraphHistogramButton: FC<GraphHistogramButtonProps> = FC { props ->
    GraphButton {
        this.palette = MainPalette.primary
        HistogramIcon {}
        onClick = { props.onClick?.invoke() }
    }
}

external interface GraphButtonsProps : Props, BasePredictionGraphProps {
    override var dist: ProbabilityDistribution?
    override var space: Space
    var onHistogramClick: (() -> Unit)?
    /** Zoomed value range of the graph, for the presenter button to carry over. */
    var presenterView: ClosedFloatingPointRange<Double>?
    /** Rendered before the standard buttons, e.g. a zoom pill that grows leftwards. */
    var leading: ReactNode?
}

val GraphButtons = FC<GraphButtonsProps>("GraphButtons") { props->
    val layoutMode = use(LayoutModeContext)!!
    GraphButtonContainer {
        props.leading?.let { +it }
        if (props.isGroup && layoutMode >= LayoutMode.TABLET && props.dist != null) {
            props.question?.let { GraphPresenterButton { question = it; view = props.presenterView } }
        }
        if (props.isInput == true && props.question?.state == QuestionState.OPEN) {
            props.question?.let { GraphExactPredictionButton { question = it; dist = props.dist } }
        }
        if (props.isGroup && props.space is BinarySpace) {
            props.onHistogramClick?.let { GraphHistogramButton { onClick = it } }
        }
    }
}


val SymmetrySwitch = FC<SwitchProps> { props->
    Switch {
        onIcon = AsymmetricGaussIcon.create()
        offIcon = SymmetricGaussIcon.create()
        switchHeight = 30.0
        switchWidth = 58.0
        noColor = true
        +props
    }
}


















