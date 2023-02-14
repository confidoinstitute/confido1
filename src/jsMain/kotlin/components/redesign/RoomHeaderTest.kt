package components.redesign

import components.redesign.basic.*
import csstype.*
import csstype.FlexDirection.Companion.row
import csstype.FontFamily.Companion.sansSerif
import dom.ScrollBehavior
import dom.html.HTMLDivElement
import emotion.react.*
import hooks.*
import kotlinx.js.*
import react.*
import react.dom.html.ReactHTML.div
import kotlin.js.console

fun PropertiesBuilder.bigQuestionTitle() {
    fontSize = 26.px
    lineHeight = 31.px
    fontWeight = FontWeight.bold
}

fun PropertiesBuilder.navQuestionTitle() {
    fontSize = 17.px
    lineHeight = 21.px
    fontWeight = FontWeight.bold
}

val RoomHeaderTest = FC<Props> {
    val size = useElementSize<HTMLDivElement>()
    val tabRef = useRef<HTMLDivElement>()
    val palette = RoomPalette.red
    val questionTitle = "Curious questions"

    var scrollY by useState(0.0)
    val cutoff = 60 + size.height/2
    console.log(size.height, cutoff)

    Paper {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            height = 500.px
            position = Position.relative
        }
        div {
            css {
                backgroundColor = palette.color
                flexBasis = 44.px
                flexShrink = number(0.0)
                display = Display.flex
                flexDirection = row
                height = 44.px
                top = 0.px
                width = 100.pct
            }
            div {
                css {
                    flexGrow = number(1.0)
                }
            }
            div {
                css {
                    position = Position.relative
                    flexShrink = number(1.0)
                    color = palette.text.color
                    padding = 12.px
                    navQuestionTitle()
                    if (scrollY <= cutoff)
                        visibility = Visibility.hidden
                }
                +questionTitle
            }
            div {
                css {
                    flexGrow = number(1.0)
                }
            }
        }

        div {
            onScroll = { event ->
                scrollY = event.currentTarget.scrollTop
            }
            css {
                flexGrow = number(1.0)
                position = Position.relative
                overflow = "auto".asDynamic()
            }

            div {
                css {
                    backgroundColor = palette.color
                    position = Position.relative
                }
                div {
                    css {
                        top = 0.px
                        position = Position.sticky
                        width = 100.pct
                        height = 30.px
                        background = linearGradient(stop(palette.color, 0.pct), stop(rgba(0, 0, 0, 0.0), 100.pct))
                        zIndex = (1.0).asDynamic()
                    }
                }
                div {
                    ref = size.ref
                    css {
                        fontFamily = sansSerif
                        paddingTop = 30.px
                        width = 100.pct
                        textAlign = TextAlign.center
                        color = palette.text.color
                        bigQuestionTitle()
                        if (scrollY > cutoff)
                            visibility = Visibility.hidden
                    }
                    +questionTitle
                }
                div {
                    css {
                        paddingTop = 18.px
                        paddingBottom = 100.px
                        paddingLeft = 60.px
                        paddingRight = 60.px
                        textAlign = TextAlign.center
                        color = palette.text.color
                        fontFamily = sansSerif
                        fontSize = 16.px
                        lineHeight = 19.px
                    }
                    +"Welcome to curious questions! Join the estimating game and snare your insights with our community of forecasters."
                }
            }

            div {
                ref = tabRef
            }
            RoomTabs {
                css {
                    position = Position.sticky
                    top = 0.px
                }
                onChange = {
                    tabRef.current?.apply {
                        scrollIntoView(jso {
                            if (scrollY < offsetTop)
                                behavior = ScrollBehavior.smooth
                        })
                    }
                }
            }

            div {
                css {
                    height = 1000.px
                }
                +"Content start"
            }
        }
    }
}