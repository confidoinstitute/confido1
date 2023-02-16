package components.redesign

import csstype.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.FillRule
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.StrokeLinecap
import react.dom.svg.StrokeLinejoin

enum class VoteState {
    NO_VOTE, UPVOTED, DOWNVOTED,
}

external interface CommentProps : Props {
    var text: String
    var authorName: String
    var timeAgo: String
    var score: Int
    var voteState: VoteState
}

val Comment = FC<CommentProps> { props ->
    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            background = Color("#FFFFFF")
            fontFamily = FontFamily.sansSerif
        }

        // Comment author header
        div {
            css {
                padding = Padding(15.px, 15.px, 10.px)
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
                gap = 8.px
            }

            // Avatar
            // TODO: Proper avatar
            Circle {
                color = Color("#45AFEB")
                size = 32.px
            }

            // Name and time
            div {
                val nameColor = Color("#777777")
                css {
                    padding = Padding(15.px, 15.px, 10.px)
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    alignItems = AlignItems.center
                    padding = 0.px
                    gap = 5.px
                    color = nameColor
                    fontSize = 12.px
                    lineHeight = 14.px
                }
                span {
                    css {
                        fontWeight = 600.unsafeCast<FontWeight>()
                    }
                    +props.authorName
                }
                Circle {
                    color = nameColor
                    size = 3.px
                }
                span {
                    +props.timeAgo
                }
            }
        }

        // Contents
        div {
            css {
                padding = Padding(0.px, 15.px)
                color = Color("#000000")
                fontSize = 15.px
                lineHeight = 18.px
            }
            +props.text
        }

        // Actions
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                justifyContent = JustifyContent.flexEnd
                fontWeight = 500.unsafeCast<FontWeight>()
                padding = Padding(0.px, 5.px)
                gap = 10.px
                fontSize = 12.px
                lineHeight = 14.px
            }
            FooterAction {
                icon = MoreIcon
            }
            FooterAction {
                icon = ReplyIcon
                text = "Reply"
            }
            FooterAction {
                icon = UpvoteIcon
                text = "Upvote"
            }
            FooterAction {
                icon = DownvoteIcon
                text = "Downvote"
            }
            // Voting
            div {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    justifyContent = JustifyContent.center
                    alignItems = AlignItems.center
                    padding = 10.px
                    gap = 12.px
                }
                if (props.voteState == VoteState.UPVOTED) {
                    UpvoteActiveIcon {
                        css {
                            width = 14.px
                            height = 15.px
                        }
                    }
                } else {
                    UpvoteIcon {
                        css {
                            width = 14.px
                            height = 15.px
                        }
                    }
                }
                +"${props.score}"
                if (props.voteState == VoteState.DOWNVOTED) {
                    DownvoteActiveIcon {
                        css {
                            width = 14.px
                            height = 15.px
                        }
                    }
                } else {
                    DownvoteIcon {
                        css {
                            width = 14.px
                            height = 15.px
                        }
                    }
                }
            }
        }
    }
}

external interface CircleProps : PropsWithClassName {
    /** The size of the circle. Defaults to 16px. */
    var size: Length?

    /** The color of the circle. Defaults to #000000. */
    var color: Color?
}

private val Circle = FC<CircleProps> { props ->
    val size = props.size ?: 16.px
    val color = props.color ?: Color("#000000")

    div {
        this.className = props.className
        css {
            width = size
            height = size
            backgroundColor = color
            borderRadius = 50.pct
            flex = None.none
        }
    }
}

private val MoreIcon = FC<Props> { props ->
    div {
        css {
            width = 15.px
            height = 15.px
            display = Display.flex
            alignItems = AlignItems.center
            gap = 3.px
        }
        Circle {
            size = 3.px
        }
        Circle {
            size = 3.px
        }
        Circle {
            size = 3.px
        }
    }
}

private val ReplyIcon = FC<PropsWithClassName> { props ->
    svg {
        className = props.className
        width = 17.0
        height = 17.0
        viewBox = "0 0 17 17"
        fill = "none"
        path {
            d = "M16 16V13C16 9.5 13.5 7 10 7H1"
            strokeLinecap = StrokeLinecap.round
            strokeLinejoin = StrokeLinejoin.round
            stroke = "black"
        }
        path {
            d = "M7 1L1 7L7 13"
            strokeLinecap = StrokeLinecap.round
            strokeLinejoin = StrokeLinejoin.round
            stroke = "black"
        }
    }
}

private val UpvoteIcon = FC<PropsWithClassName> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 16.0
        height = 17.0
        viewBox = "0 0 16 17"
        fill = "none"
        path {
            d =
                "M15 9V9.5C15.1962 9.5 15.3742 9.38526 15.4553 9.20661C15.5364 9.02795 15.5055 8.81839 15.3763 8.67075L15 9ZM11 9V8.5C10.7239 8.5 10.5 8.72386 10.5 9H11ZM8 1L8.37629 0.670748C8.28134 0.56224 8.14418 0.5 8 0.5C7.85582 0.5 7.71866 0.56224 7.62371 0.670748L8 1ZM1 9L0.623712 8.67075C0.494521 8.81839 0.463615 9.02795 0.544684 9.20661C0.625752 9.38526 0.803812 9.5 1 9.5L1 9ZM5 9H5.5C5.5 8.72386 5.27614 8.5 5 8.5V9ZM5 16H4.5C4.5 16.2761 4.72386 16.5 5 16.5V16ZM11 16V16.5C11.2761 16.5 11.5 16.2761 11.5 16H11ZM15 8.5H11V9.5H15V8.5ZM7.62371 1.32925L14.6237 9.32925L15.3763 8.67075L8.37629 0.670748L7.62371 1.32925ZM1.37629 9.32925L8.37629 1.32925L7.62371 0.670748L0.623712 8.67075L1.37629 9.32925ZM5 8.5H1V9.5H5V8.5ZM5.5 16V9H4.5V16H5.5ZM11 15.5H5V16.5H11V15.5ZM10.5 9V16H11.5V9H10.5Z"
            fill = color
        }
    }
}

private val UpvoteActiveIcon = FC<PropsWithClassName> { props ->
    val color = "#0088FF"
    svg {
        className = props.className
        width = 16.0
        height = 17.0
        viewBox = "0 0 16 17"
        fill = "none"
        path {
            fillRule = FillRule.evenodd
            clipRule = "evenodd"
            d = "M11 9H15L8 1L1 9H5V16H11V9Z"
            fill = color
        }
        path {
            d =
                "M15 9V9.5C15.1962 9.5 15.3742 9.38526 15.4553 9.20661C15.5364 9.02795 15.5055 8.81839 15.3763 8.67075L15 9ZM11 9V8.5C10.7239 8.5 10.5 8.72386 10.5 9H11ZM8 1L8.37629 0.670748C8.28134 0.56224 8.14418 0.5 8 0.5C7.85582 0.5 7.71866 0.56224 7.62371 0.670748L8 1ZM1 9L0.623712 8.67075C0.494521 8.81839 0.463615 9.02795 0.544684 9.20661C0.625752 9.38526 0.803812 9.5 1 9.5L1 9ZM5 9H5.5C5.5 8.72386 5.27614 8.5 5 8.5V9ZM5 16H4.5C4.5 16.2761 4.72386 16.5 5 16.5V16ZM11 16V16.5C11.2761 16.5 11.5 16.2761 11.5 16H11ZM15 8.5H11V9.5H15V8.5ZM7.62371 1.32925L14.6237 9.32925L15.3763 8.67075L8.37629 0.670748L7.62371 1.32925ZM1.37629 9.32925L8.37629 1.32925L7.62371 0.670748L0.623712 8.67075L1.37629 9.32925ZM5 8.5H1V9.5H5V8.5ZM5.5 16V9H4.5V16H5.5ZM11 15.5H5V16.5H11V15.5ZM10.5 9V16H11.5V9H10.5Z"
            fill = color
        }
    }
}

private val DownvoteIcon = FC<PropsWithClassName> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 16.0
        height = 17.0
        viewBox = "0 0 16 17"
        fill = "none"
        path {
            d =
                "M0.999999 8L0.999999 7.5C0.803811 7.5 0.625751 7.61474 0.544683 7.79339C0.463615 7.97205 0.49452 8.18161 0.623711 8.32925L0.999999 8ZM5 8L5 8.5C5.27614 8.5 5.5 8.27614 5.5 8L5 8ZM8 16L7.62371 16.3293C7.71866 16.4378 7.85582 16.5 8 16.5C8.14418 16.5 8.28134 16.4378 8.37629 16.3293L8 16ZM15 8L15.3763 8.32925C15.5055 8.18161 15.5364 7.97205 15.4553 7.79339C15.3742 7.61474 15.1962 7.5 15 7.5L15 8ZM11 8L10.5 8C10.5 8.27614 10.7239 8.5 11 8.5L11 8ZM11 1L11.5 1C11.5 0.723858 11.2761 0.5 11 0.5L11 1ZM5 1L5 0.500001C4.86739 0.500001 4.74021 0.552679 4.64644 0.646447C4.55268 0.740215 4.5 0.867393 4.5 1L5 1ZM0.999999 8.5L5 8.5L5 7.5L0.999999 7.5L0.999999 8.5ZM8.37629 15.6707L1.37629 7.67075L0.623711 8.32925L7.62371 16.3293L8.37629 15.6707ZM14.6237 7.67075L7.62371 15.6707L8.37629 16.3293L15.3763 8.32925L14.6237 7.67075ZM11 8.5L15 8.5L15 7.5L11 7.5L11 8.5ZM10.5 1L10.5 8L11.5 8L11.5 1L10.5 1ZM5 1.5L11 1.5L11 0.5L5 0.500001L5 1.5ZM5.5 8L5.5 1L4.5 1L4.5 8L5.5 8Z"
            fill = color
        }
    }
}

private val DownvoteActiveIcon = FC<PropsWithClassName> { props ->
    val color = "#0088FF"
    svg {
        className = props.className
        width = 16.0
        height = 17.0
        viewBox = "0 0 16 17"
        fill = "none"
        path {
            fillRule = FillRule.evenodd
            clipRule = "evenodd"
            d = "M5 8L0.999999 8L8 16L15 8L11 8L11 1L5 1L5 8Z"
            fill = color
        }
        path {
            d =
                "M0.999999 8L0.999999 7.5C0.803811 7.5 0.625751 7.61474 0.544683 7.79339C0.463615 7.97205 0.49452 8.18161 0.623711 8.32925L0.999999 8ZM5 8L5 8.5C5.27614 8.5 5.5 8.27614 5.5 8L5 8ZM8 16L7.62371 16.3293C7.71866 16.4378 7.85582 16.5 8 16.5C8.14418 16.5 8.28134 16.4378 8.37629 16.3293L8 16ZM15 8L15.3763 8.32925C15.5055 8.18161 15.5364 7.97205 15.4553 7.79339C15.3742 7.61474 15.1962 7.5 15 7.5L15 8ZM11 8L10.5 8C10.5 8.13261 10.5527 8.25979 10.6464 8.35355C10.7402 8.44732 10.8674 8.5 11 8.5L11 8ZM11 1L11.5 1C11.5 0.723858 11.2761 0.5 11 0.5L11 1ZM5 1L5 0.500001C4.72386 0.500001 4.5 0.723859 4.5 1L5 1ZM0.999999 8.5L5 8.5L5 7.5L0.999999 7.5L0.999999 8.5ZM8.37629 15.6707L1.37629 7.67075L0.623711 8.32925L7.62371 16.3293L8.37629 15.6707ZM14.6237 7.67075L7.62371 15.6707L8.37629 16.3293L15.3763 8.32925L14.6237 7.67075ZM11 8.5L15 8.5L15 7.5L11 7.5L11 8.5ZM10.5 1L10.5 8L11.5 8L11.5 1L10.5 1ZM5 1.5L11 1.5L11 0.5L5 0.500001L5 1.5ZM5.5 8L5.5 1L4.5 1L4.5 8L5.5 8Z"
            fill = color
        }
    }
}


external interface FooterActionProps : Props {
    var text: String?
    var icon: ComponentType<PropsWithClassName>?
}

private val FooterAction = FC<FooterActionProps> { props ->
    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.center
            gap = 8.px
            padding = 10.px
        }
        props.icon?.let {
            +it.create {
                css {
                    width = 15.px
                    height = 15.px
                }
            }
        }
        props.text?.let {
            +it
        }
    }
}

