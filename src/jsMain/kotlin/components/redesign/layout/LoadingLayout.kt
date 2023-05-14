package components.redesign.layout

import components.*
import components.redesign.basic.*
import csstype.*
import emotion.css.*
import emotion.react.*
import icons.*
import react.*
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
    val palette = MainPalette.login
    GlobalCss {
        backgroundColor = palette.color
    }
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
