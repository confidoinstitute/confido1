package tools.confido.web.components.redesign.layout

import tools.confido.web.components.*
import tools.confido.web.components.redesign.basic.*
import web.cssom.*
import emotion.css.*
import emotion.react.*
import tools.confido.web.icons.*
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
