package components.redesign.layout

import components.NoAppStateProps
import components.redesign.basic.GlobalCss
import components.redesign.basic.MainPalette
import components.redesign.basic.Stack
import csstype.*
import emotion.css.keyframes
import emotion.react.css
import icons.smallLogo
import react.FC
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main

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
    GlobalCss {}
    main {
        css {
            width = 100.vw
            height = 100.vh
            overflow = Auto.auto
            color = palette.text.color
            backgroundColor = palette.color
        }
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
