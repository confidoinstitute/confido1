package components.presenter

import emotion.react.css
import react.FC
import react.dom.html.ReactHTML.img
import tools.confido.state.SlidePV
import web.cssom.*

val SlidePP = FC<PresenterPageProps<SlidePV>> { props ->
    val view = props.view
    img {
        src = view.slideUrl()
        css {
            width = 100.vw
            height = 100.vh
            objectFit = ObjectFit.contain
            backgroundColor = NamedColor.white
        }
    }
}
