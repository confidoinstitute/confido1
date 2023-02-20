package components.redesign

import react.FC
import react.PropsWithClassName
import react.dom.svg.FillRule
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.StrokeLinecap
import react.dom.svg.StrokeLinejoin

/*
TODO: This needs to be revamped to be easier to use.

Inclusion could be handled either by
- using ComponentType<PropsWithClassName> (or ComponentType<Props> if we can get that to work)
- ReactNode if we can control sizes somehow
In either case, we also need to solve how to overwrite colors
regardless of whether fills or strokes are used.
 */

val ReplyIcon = FC<PropsWithClassName> { props ->
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

val UpvoteIcon = FC<PropsWithClassName> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 16.0
        height = 17.0
        viewBox = "0 0 16 17"
        fill = "none"
        path {
            d = "M15 9V9.5C15.1962 9.5 15.3742 9.38526 15.4553 9.20661C15.5364 9.02795 15.5055 8.81839 15.3763 8.67075L15 9ZM11 9V8.5C10.7239 8.5 10.5 8.72386 10.5 9H11ZM8 1L8.37629 0.670748C8.28134 0.56224 8.14418 0.5 8 0.5C7.85582 0.5 7.71866 0.56224 7.62371 0.670748L8 1ZM1 9L0.623712 8.67075C0.494521 8.81839 0.463615 9.02795 0.544684 9.20661C0.625752 9.38526 0.803812 9.5 1 9.5L1 9ZM5 9H5.5C5.5 8.72386 5.27614 8.5 5 8.5V9ZM5 16H4.5C4.5 16.2761 4.72386 16.5 5 16.5V16ZM11 16V16.5C11.2761 16.5 11.5 16.2761 11.5 16H11ZM15 8.5H11V9.5H15V8.5ZM7.62371 1.32925L14.6237 9.32925L15.3763 8.67075L8.37629 0.670748L7.62371 1.32925ZM1.37629 9.32925L8.37629 1.32925L7.62371 0.670748L0.623712 8.67075L1.37629 9.32925ZM5 8.5H1V9.5H5V8.5ZM5.5 16V9H4.5V16H5.5ZM11 15.5H5V16.5H11V15.5ZM10.5 9V16H11.5V9H10.5Z"
            fill = color
        }
    }
}

val UpvoteActiveIcon = FC<PropsWithClassName> { props ->
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
            d = "M15 9V9.5C15.1962 9.5 15.3742 9.38526 15.4553 9.20661C15.5364 9.02795 15.5055 8.81839 15.3763 8.67075L15 9ZM11 9V8.5C10.7239 8.5 10.5 8.72386 10.5 9H11ZM8 1L8.37629 0.670748C8.28134 0.56224 8.14418 0.5 8 0.5C7.85582 0.5 7.71866 0.56224 7.62371 0.670748L8 1ZM1 9L0.623712 8.67075C0.494521 8.81839 0.463615 9.02795 0.544684 9.20661C0.625752 9.38526 0.803812 9.5 1 9.5L1 9ZM5 9H5.5C5.5 8.72386 5.27614 8.5 5 8.5V9ZM5 16H4.5C4.5 16.2761 4.72386 16.5 5 16.5V16ZM11 16V16.5C11.2761 16.5 11.5 16.2761 11.5 16H11ZM15 8.5H11V9.5H15V8.5ZM7.62371 1.32925L14.6237 9.32925L15.3763 8.67075L8.37629 0.670748L7.62371 1.32925ZM1.37629 9.32925L8.37629 1.32925L7.62371 0.670748L0.623712 8.67075L1.37629 9.32925ZM5 8.5H1V9.5H5V8.5ZM5.5 16V9H4.5V16H5.5ZM11 15.5H5V16.5H11V15.5ZM10.5 9V16H11.5V9H10.5Z"
            fill = color
        }
    }
}

val DownvoteIcon = FC<PropsWithClassName> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 16.0
        height = 17.0
        viewBox = "0 0 16 17"
        fill = "none"
        path {
            d = "M0.999999 8L0.999999 7.5C0.803811 7.5 0.625751 7.61474 0.544683 7.79339C0.463615 7.97205 0.49452 8.18161 0.623711 8.32925L0.999999 8ZM5 8L5 8.5C5.27614 8.5 5.5 8.27614 5.5 8L5 8ZM8 16L7.62371 16.3293C7.71866 16.4378 7.85582 16.5 8 16.5C8.14418 16.5 8.28134 16.4378 8.37629 16.3293L8 16ZM15 8L15.3763 8.32925C15.5055 8.18161 15.5364 7.97205 15.4553 7.79339C15.3742 7.61474 15.1962 7.5 15 7.5L15 8ZM11 8L10.5 8C10.5 8.27614 10.7239 8.5 11 8.5L11 8ZM11 1L11.5 1C11.5 0.723858 11.2761 0.5 11 0.5L11 1ZM5 1L5 0.500001C4.86739 0.500001 4.74021 0.552679 4.64644 0.646447C4.55268 0.740215 4.5 0.867393 4.5 1L5 1ZM0.999999 8.5L5 8.5L5 7.5L0.999999 7.5L0.999999 8.5ZM8.37629 15.6707L1.37629 7.67075L0.623711 8.32925L7.62371 16.3293L8.37629 15.6707ZM14.6237 7.67075L7.62371 15.6707L8.37629 16.3293L15.3763 8.32925L14.6237 7.67075ZM11 8.5L15 8.5L15 7.5L11 7.5L11 8.5ZM10.5 1L10.5 8L11.5 8L11.5 1L10.5 1ZM5 1.5L11 1.5L11 0.5L5 0.500001L5 1.5ZM5.5 8L5.5 1L4.5 1L4.5 8L5.5 8Z"
            fill = color
        }
    }
}

val DownvoteActiveIcon = FC<PropsWithClassName> { props ->
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
            d = "M0.999999 8L0.999999 7.5C0.803811 7.5 0.625751 7.61474 0.544683 7.79339C0.463615 7.97205 0.49452 8.18161 0.623711 8.32925L0.999999 8ZM5 8L5 8.5C5.27614 8.5 5.5 8.27614 5.5 8L5 8ZM8 16L7.62371 16.3293C7.71866 16.4378 7.85582 16.5 8 16.5C8.14418 16.5 8.28134 16.4378 8.37629 16.3293L8 16ZM15 8L15.3763 8.32925C15.5055 8.18161 15.5364 7.97205 15.4553 7.79339C15.3742 7.61474 15.1962 7.5 15 7.5L15 8ZM11 8L10.5 8C10.5 8.13261 10.5527 8.25979 10.6464 8.35355C10.7402 8.44732 10.8674 8.5 11 8.5L11 8ZM11 1L11.5 1C11.5 0.723858 11.2761 0.5 11 0.5L11 1ZM5 1L5 0.500001C4.72386 0.500001 4.5 0.723859 4.5 1L5 1ZM0.999999 8.5L5 8.5L5 7.5L0.999999 7.5L0.999999 8.5ZM8.37629 15.6707L1.37629 7.67075L0.623711 8.32925L7.62371 16.3293L8.37629 15.6707ZM14.6237 7.67075L7.62371 15.6707L8.37629 16.3293L15.3763 8.32925L14.6237 7.67075ZM11 8.5L15 8.5L15 7.5L11 7.5L11 8.5ZM10.5 1L10.5 8L11.5 8L11.5 1L10.5 1ZM5 1.5L11 1.5L11 0.5L5 0.500001L5 1.5ZM5.5 8L5.5 1L4.5 1L4.5 8L5.5 8Z"
            fill = color
        }
    }
}

val EditIcon = FC<PropsWithClassName> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 18.0
        height = 18.0
        viewBox = "0 0 18 18"
        fill = "none"
        path {
            d = "M5 15.5L5.16483 16.0769C5.26288 16.0489 5.35216 15.9964 5.42426 15.9243L5 15.5ZM2.5 13L2.07574 12.5757C2.00363 12.6478 1.9511 12.7371 1.92309 12.8352L2.5 13ZM1.5 16.5L0.923086 16.3352C0.863224 16.5447 0.921657 16.7702 1.07574 16.9243C1.22981 17.0783 1.45532 17.1368 1.66483 17.0769L1.5 16.5ZM16.2929 2.79289L15.8686 3.21716L16.2929 2.79289ZM14.7828 2.13137L15.8686 3.21716L16.7172 2.36863L15.6314 1.28284L14.7828 2.13137ZM2.92426 13.4243L13.4243 2.92426L12.5757 2.07574L2.07574 12.5757L2.92426 13.4243ZM13.4243 2.92426L14.2172 2.13137L13.3686 1.28284L12.5757 2.07574L13.4243 2.92426ZM15.8686 3.78284L15.0757 4.57574L15.9243 5.42426L16.7172 4.63137L15.8686 3.78284ZM15.0757 4.57574L4.57574 15.0757L5.42426 15.9243L15.9243 5.42426L15.0757 4.57574ZM12.5757 2.92426L15.0757 5.42426L15.9243 4.57574L13.4243 2.07574L12.5757 2.92426ZM4.83517 14.9231L1.33517 15.9231L1.66483 17.0769L5.16483 16.0769L4.83517 14.9231ZM2.07691 16.6648L3.07691 13.1648L1.92309 12.8352L0.923086 16.3352L2.07691 16.6648ZM2.07574 13.4243L4.57574 15.9243L5.42426 15.0757L2.92426 12.5757L2.07574 13.4243ZM15.8686 3.21716C16.0248 3.37337 16.0248 3.62663 15.8686 3.78284L16.7172 4.63137C17.342 4.00653 17.342 2.99347 16.7172 2.36863L15.8686 3.21716ZM15.6314 1.28284C15.0065 0.658004 13.9935 0.658005 13.3686 1.28284L14.2172 2.13137C14.3734 1.97516 14.6266 1.97516 14.7828 2.13137L15.6314 1.28284Z"
            fill = color
        }
    }
}

val BinIcon = FC<PropsWithClassName> { props ->
    val color = "#FF0000"
    svg {
        className = props.className
        width = 16.0
        height = 19.0
        viewBox = "0 0 16 19"
        fill = "none"
        path {
            d = "M1 4H2M15 4H14M2 4L2.90049 16.6069C2.95656 17.3918 3.60972 18 4.39668 18H11.6033C12.3903 18 13.0434 17.3918 13.0995 16.6069L14 4M2 4H5M14 4H11M5 6.5L5.5 15.5M8 6.5V15.5M11 6.5L10.5 15.5M5 4V2C5 1.44772 5.44772 1 6 1H10C10.5523 1 11 1.44772 11 2V4M5 4H11"
            stroke = color
            strokeWidth = 1.2
            strokeLinecap = StrokeLinecap.round
        }
    }
}

val SortIcon = FC<PropsWithClassName> { props ->
    val color = "#888888"
    svg {
        className = props.className
        width = 15.0
        height = 15.0
        viewBox = "0 0 15 15"
        fill = "none"
        path {
            d = "M5 10V2H3V10H0.83225C0.496847 10 0.310378 10.388 0.519903 10.6499L3.60957 14.512C3.80973 14.7622 4.19027 14.7622 4.39043 14.512L7.4801 10.6499C7.68962 10.388 7.50315 10 7.16775 10H5Z"
            fill = color
        }
        path {
            d = "M10 5L10 13L12 13L12 5L14.1677 5C14.5032 5 14.6896 4.61203 14.4801 4.35012L11.3904 0.488043C11.1903 0.23784 10.8097 0.23784 10.6096 0.488043L7.5199 4.35012C7.31038 4.61203 7.49685 5 7.83225 5L10 5Z"
            fill = color
        }
    }
}
