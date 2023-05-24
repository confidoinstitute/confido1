package components.redesign.questions.predictions

import components.redesign.AboutIcon
import components.redesign.ExactPredictionIcon
import components.redesign.PresenterIcon
import components.redesign.basic.MainPalette
import components.redesign.basic.Stack
import components.redesign.basic.withStyle
import components.redesign.forms.Button
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.presenter.PresenterContext
import components.redesign.questions.dialog.ExactEstimateDialog
import csstype.*
import emotion.react.css
import react.*
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.Space
import tools.confido.state.GroupPredPV

val GraphButton = Button.withStyle {
    width = 30.px
    height = 30.px
    display = Display.flex
    padding = 0.px
    margin = 0.px
    justifyContent = JustifyContent.center
    alignItems = AlignItems.center
}

val GraphButtonContainer = FC<PropsWithChildren> { props->
    Stack {
        direction = FlexDirection.row
        css {
            position = Position.absolute
            top = 8.px
            right = 8.px
            height = 30.px
            gap = 4.px
            zIndex = integer(100)
        }
        +props.children
    }
}

external interface GraphPresenterButtonProps: Props {
    var question: Question
}

val GraphPresenterButton = FC<GraphPresenterButtonProps> { props->
    val presenterCtl = useContext(PresenterContext)
    GraphButton {
        this.palette = MainPalette.secondary
        PresenterIcon {}
        onClick = {
            presenterCtl.offer(GroupPredPV(props.question.ref, false))
        }
    }
}

external interface GraphExactPredictionButtonProps: Props {
    var question: Question
}

val GraphExactPredictionButton: FC<GraphExactPredictionButtonProps> = FC { props ->
    var open by useState(false)
    ExactEstimateDialog {
        this.open = open
        onClose = { open = false }
        question = props.question
    }

    GraphButton {
        this.palette = MainPalette.primary
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
        // TODO icon
        AboutIcon {}
        onClick = { props.onClick?.invoke() }
    }
}

external interface GraphButtonsProps : Props, BasePredictionGraphProps {
    override var dist: ProbabilityDistribution?
    override var space: Space
    var onHistogramClick: (() -> Unit)?
}

val GraphButtons = FC<GraphButtonsProps>("GraphButtons") { props->
    val layoutMode = useContext(LayoutModeContext)
    GraphButtonContainer {
        if (props.isGroup && layoutMode >= LayoutMode.TABLET && props.dist != null) {
            props.question?.let { GraphPresenterButton { question = it } }
        }
        if (props.isInput == true && props.question?.state == QuestionState.OPEN) {
            props.question?.let { GraphExactPredictionButton { question = it } }
        }
        if (props.isGroup && props.space is BinarySpace) {
            props.onHistogramClick?.let { GraphHistogramButton { onClick = it } }
        }
    }
}




















