package components.redesign.questions.predictions

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
import tools.confido.refs.ref
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

val GraphExactPredictionButton = FC<GraphExactPredictionButtonProps> { props ->
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

external interface GraphButtonsProps : Props, BasePredictionGraphProps {
    override var dist: ProbabilityDistribution?
    override var space: Space
}

val GraphButtons = FC<GraphButtonsProps>("GraphButtons") { props->
    val layoutMode = useContext(LayoutModeContext)
    GraphButtonContainer {
        if (props.isGroup && layoutMode >= LayoutMode.TABLET && props.dist != null) {
            props.question?.let { GraphPresenterButton { question = it } }
        }
        if (props.isInput ?: false && props.dist is BinaryDistribution) {
            // TODO: Check if edit possible
            props.question?.let { GraphExactPredictionButton { question = it } }
        }
    }
}