package components.redesign.layout

import components.NoAppStateProps
import csstype.*
import emotion.css.keyframes
import emotion.react.css
import icons.smallLogo
import react.FC
import react.dom.html.ReactHTML.div

internal val rotatingKeyframes = keyframes {
    from {
        transform = rotate(0.deg)
    }
    to {
        transform = rotate(360.deg)
    }
}

val LoadingLayout = FC<NoAppStateProps> {
    div {
        css {
            position = Position.absolute
            top = 50.pct
            left = 50.pct
            transform = translate((-50).pct, (-50).pct)
        }
        smallLogo {
            css {
                animationName = rotatingKeyframes
                animationDuration = 5.s
                animationTimingFunction = AnimationTimingFunction.linear
                animationIterationCount = AnimationIterationCount.infinite
            }
        }
    }
}
