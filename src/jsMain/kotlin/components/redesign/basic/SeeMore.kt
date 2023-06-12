package components.redesign.basic

import csstype.*
import dom.html.HTMLDivElement
import emotion.react.css
import hooks.useElementSize
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.useState

external interface SeeMoreProps: PropsWithChildren {
    var lineHeight: Double
    var maxLines: Int
    var backgroundColor: Color
    var linkCss: ClassName?
    var seeMoreText: String?
    var seeLessText: String?
}

val SeeMore = FC<SeeMoreProps> {props->
    val elSize = useElementSize<HTMLDivElement>()
    var expanded by useState(false)
    val tooBig = elSize.height > (props.maxLines.toDouble() + 0.5) * props.lineHeight
    div {
        css {
            lineHeight = props.lineHeight.px
            if (!expanded) {
                maxHeight = (props.maxLines * props.lineHeight).px
                overflow = Overflow.hidden
            }
            position = Position.relative
        }
        div {
            div {
                ref = elSize.ref
                +props.children
            }
            if (tooBig && !expanded) {
                div {
                    css {
                        position = Position.absolute
                        right = 0.px
                        bottom = 0.px
                        paddingLeft = 80.px
                        paddingRight = 15.px
                        height = props.lineHeight.px
                        whiteSpace = WhiteSpace.nowrap
                        background = linearGradient(
                            90.deg,
                            stop(props.backgroundColor.addAlpha("0%"), 0.px),
                            stop(props.backgroundColor, 65.px),
                            stop(props.backgroundColor, 100.pct),
                        )
                    }
                    a {
                        className = props.linkCss
                        href = "#"
                        onClick = {
                            expanded = true
                            it.preventDefault()
                        }
                        +(props.seeMoreText ?: "See more")
                    }
                }
            } else if (tooBig && expanded) {
                div {
                    a {
                        className = props.linkCss
                        href = "#"
                        onClick = {
                            expanded = false
                            it.preventDefault()
                        }
                        +(props.seeLessText ?: "See less")
                    }
                }
            }
        }
    }
}