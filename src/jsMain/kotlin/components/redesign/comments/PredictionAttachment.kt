package components.redesign.comments

import components.redesign.*
import components.redesign.basic.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.span
import tools.confido.question.*
import tools.confido.spaces.*

external interface PredictionAttachmentProps : Props {
    var prediction: Prediction
}

val PredictionAttachment = FC<PredictionAttachmentProps> { props ->
    val icon = when (props.prediction.dist.space) {
        BinarySpace -> CirclesIcon
        // TODO: We also have an asymmetric gauss icon
        is NumericSpace -> SymmetricGaussIcon
    }

    Stack {
        direction = FlexDirection.row
        css {
            alignItems = AlignItems.center
            gap = 5.px
            color = Color("#555555")
        }
        +icon.create()
        span {
            +props.prediction.dist.description
        }
    }
}

