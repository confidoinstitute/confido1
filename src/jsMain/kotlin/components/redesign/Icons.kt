package components.redesign

import components.redesign.basic.css
import csstype.px
import dom.svg.SVGElement
import dom.svg.SVGSVGElement
import kotlinx.js.jso
import react.*
import react.dom.aria.AriaRole
import react.dom.svg.*
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.rect
import react.dom.svg.ReactSVG.svg
import tools.confido.utils.List2
import utils.except

/*
TODO: This needs to be revamped to be easier to use.

Inclusion could be handled either by
- using ComponentType<PropsWithClassName> (or ComponentType<Props> if we can get that to work)
- ReactNode if we can control sizes somehow
In either case, we also need to solve how to overwrite colors
regardless of whether fills or strokes are used.

The icons also have mildly varying sizes.
 */

external interface IconProps: PropsWithClassName, SVGAttributes<SVGSVGElement> {
    var size: Int?
}


fun createIcon(width: Int = 30, height: Int = width, boxSize: List2<Int> = List2(width, height), svgAttrs: SVGAttributes<SVGSVGElement> = jso{}, builder: ChildrenBuilder.()->Unit) =
    FC<IconProps> { props->
        svg {
            css(override = props.className) {
                (props.size ?: height).let {this.height = it.px}
            }
            viewBox = "0 0 " + boxSize.joinToString(" ")
            fill = "none"
            +svgAttrs
            +props.except("className", "size")
            builder()
        }
    }
fun createIcon(d: String, width: Int = 30, height: Int = width, boxSize: List2<Int> = List2(width, height)) =
    createIcon(width=width, height=height, boxSize=boxSize) {
        ReactSVG.path {
            this.d = d
            fill = "currentColor"
        }
    }

val ReplyIcon = createIcon(17) {
    path {
        d = "M16 16V13C16 9.5 13.5 7 10 7H1"
        strokeLinecap = StrokeLinecap.round
        strokeLinejoin = StrokeLinejoin.round
        stroke = "currentColor"
    }
    path {
        d = "M7 1L1 7L7 13"
        strokeLinecap = StrokeLinecap.round
        strokeLinejoin = StrokeLinejoin.round
        stroke = "currentColor"
    }
}

val UpvoteIcon = createIcon(16,17) {
    path {
        d = "M15 9V9.5C15.1962 9.5 15.3742 9.38526 15.4553 9.20661C15.5364 9.02795 15.5055 8.81839 15.3763 8.67075L15 9ZM11 9V8.5C10.7239 8.5 10.5 8.72386 10.5 9H11ZM8 1L8.37629 0.670748C8.28134 0.56224 8.14418 0.5 8 0.5C7.85582 0.5 7.71866 0.56224 7.62371 0.670748L8 1ZM1 9L0.623712 8.67075C0.494521 8.81839 0.463615 9.02795 0.544684 9.20661C0.625752 9.38526 0.803812 9.5 1 9.5L1 9ZM5 9H5.5C5.5 8.72386 5.27614 8.5 5 8.5V9ZM5 16H4.5C4.5 16.2761 4.72386 16.5 5 16.5V16ZM11 16V16.5C11.2761 16.5 11.5 16.2761 11.5 16H11ZM15 8.5H11V9.5H15V8.5ZM7.62371 1.32925L14.6237 9.32925L15.3763 8.67075L8.37629 0.670748L7.62371 1.32925ZM1.37629 9.32925L8.37629 1.32925L7.62371 0.670748L0.623712 8.67075L1.37629 9.32925ZM5 8.5H1V9.5H5V8.5ZM5.5 16V9H4.5V16H5.5ZM11 15.5H5V16.5H11V15.5ZM10.5 9V16H11.5V9H10.5Z"
        fill = "currentColor"
    }
}

val UpvoteActiveIcon = FC<IconProps> { props ->
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

val DownvoteIcon = FC<IconProps> { props ->
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

val DownvoteActiveIcon = FC<IconProps> { props ->
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

val ExactPredictionIcon = FC<IconProps> { props ->
    svg {
        className = props.className
        width = 19.0
        height = 19.0
        viewBox = "0 0 19 19"
        fill = "none"
        path {
            d = "M0.202113 18.1915L0.865643 15.5374C0.953559 15.1858 1.1354 14.8646 1.39171 14.6083L13 3L16 6L4.39171 17.6083C4.1354 17.8646 3.81424 18.0464 3.46257 18.1344L0.808452 18.7979C0.442262 18.8894 0.110565 18.5577 0.202113 18.1915Z"
            fill = "currentcolor"
        }
        path {
            d = "M17 5L14 2L14.9393 1.06066C15.5251 0.474874 16.4749 0.474874 17.0607 1.06066L17.9393 1.93934C18.5251 2.52513 18.5251 3.47487 17.9393 4.06066L17 5Z"
            fill = "currentcolor"
        }
    }
}

val EditIcon = createIcon(18) {
    path {
        d = "M5 15.5L5.16483 16.0769C5.26288 16.0489 5.35216 15.9964 5.42426 15.9243L5 15.5ZM2.5 13L2.07574 12.5757C2.00363 12.6478 1.9511 12.7371 1.92309 12.8352L2.5 13ZM1.5 16.5L0.923086 16.3352C0.863224 16.5447 0.921657 16.7702 1.07574 16.9243C1.22981 17.0783 1.45532 17.1368 1.66483 17.0769L1.5 16.5ZM16.2929 2.79289L15.8686 3.21716L16.2929 2.79289ZM14.7828 2.13137L15.8686 3.21716L16.7172 2.36863L15.6314 1.28284L14.7828 2.13137ZM2.92426 13.4243L13.4243 2.92426L12.5757 2.07574L2.07574 12.5757L2.92426 13.4243ZM13.4243 2.92426L14.2172 2.13137L13.3686 1.28284L12.5757 2.07574L13.4243 2.92426ZM15.8686 3.78284L15.0757 4.57574L15.9243 5.42426L16.7172 4.63137L15.8686 3.78284ZM15.0757 4.57574L4.57574 15.0757L5.42426 15.9243L15.9243 5.42426L15.0757 4.57574ZM12.5757 2.92426L15.0757 5.42426L15.9243 4.57574L13.4243 2.07574L12.5757 2.92426ZM4.83517 14.9231L1.33517 15.9231L1.66483 17.0769L5.16483 16.0769L4.83517 14.9231ZM2.07691 16.6648L3.07691 13.1648L1.92309 12.8352L0.923086 16.3352L2.07691 16.6648ZM2.07574 13.4243L4.57574 15.9243L5.42426 15.0757L2.92426 12.5757L2.07574 13.4243ZM15.8686 3.21716C16.0248 3.37337 16.0248 3.62663 15.8686 3.78284L16.7172 4.63137C17.342 4.00653 17.342 2.99347 16.7172 2.36863L15.8686 3.21716ZM15.6314 1.28284C15.0065 0.658004 13.9935 0.658005 13.3686 1.28284L14.2172 2.13137C14.3734 1.97516 14.6266 1.97516 14.7828 2.13137L15.6314 1.28284Z"
        fill = "currentcolor"
    }
}

val BinIcon = FC<IconProps> { props ->
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

val SortIcon = FC<IconProps> { props ->
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

val CloseIcon = createIcon(17,16) {
    path {
        d = "M1 15L15.1642 1"
        strokeWidth = 2.0
        strokeLinecap = StrokeLinecap.round
    }
    path {
        d = "M0.99984 1L15.1641 15"
        strokeWidth = 2.0
        strokeLinecap = StrokeLinecap.round
    }
}

val CirclesIcon = FC<IconProps> { props ->
    svg {
        className = props.className
        width = 18.0
        height = 9.0
        viewBox = "0 0 18 9"
        fill = "none"
        circle {
            cx = 3.5
            cy = 4.5
            r = 3.5
            fill = "currentcolor"
        }
        circle {
            cx = 13.5
            cy = 4.5
            r = 4.5
            fill = "currentcolor"
        }
    }
}

val SymmetricGaussIcon = FC<IconProps> { props ->
    svg {
        className = props.className
        width = 20.0
        height = 11.0
        viewBox = "0 0 20 11"
        fill = "none"
        path {
            d = "M10 0C11.2543 0 12.0668 0.908928 12.8893 2.18446C13.2771 2.78589 13.6506 3.46193 14.0354 4.15867C15.4462 6.71274 17.0107 9.54501 20 10V11H0V10C2.98934 9.54501 4.55381 6.71273 5.96459 4.15867C6.34945 3.46193 6.72287 2.78589 7.11067 2.18446C7.93317 0.908928 8.74567 0 10 0Z"
            fill = "currentColor"
        }
    }
}

val AsymmetricGaussIcon = FC<IconProps> { props ->
    val color = "#555555"
    svg {
        className = props.className
        width = 20.0
        height = 11.0
        viewBox = "0 0 20 11"
        fill = "none"
        path {
            d = "M4.87167 0.713226C3.47733 2.46897 3.34067 9.20824 0 9.93548V11H20V9.93548C11.3793 8.35592 8.4215 0 6.05033 0C5.65483 0 5.30433 0.168371 4.87167 0.713226Z"
            fill = "currentColor"
        }
    }
}

val BackIcon = FC<IconProps> { props ->
    svg {
        className = props.className
        width = 10.0
        height = 18.0
        viewBox = "-1 -1 10 18"
        strokeLinecap = StrokeLinecap.round
        strokeWidth = 2.0
        ReactSVG.path {
            fill = "transparent"
            d = "M8,0 L0,8 L8,16"
        }
    }
}

val NavMenuIcon = createIcon(19, 4) {
    listOf(2.0, 9.5, 17.0).map {
        circle {
            cx = it
            cy = 2.0
            r = 2.0
            strokeWidth = 0.0
            fill = "currentColor"
        }
    }
}

val LogoutIcon = FC<IconProps> { props ->
    val color = "#FF0000"
    svg {
        className = props.className
        width = 19.0
        height = 18.0
        viewBox = "0 0 19 18"
        fill = "none"
        path {
            d = "M17.5 9L7 9M17.5 9L14.5 6M17.5 9L14.5 12M11.5 4.5L11.5 3C11.5 2.17157 10.8284 1.5 10 1.5L3 1.5C2.17157 1.5 1.5 2.17157 1.5 3L1.5 15C1.5 15.8284 2.17157 16.5 3 16.5L10 16.5C10.8284 16.5 11.5 15.8284 11.5 15L11.5 13.5"
            stroke = color
            strokeWidth = 1.2
            strokeLinecap = StrokeLinecap.round
            strokeLinejoin = StrokeLinejoin.round
        }
    }
}

val ResolveIcon = FC<IconProps> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 12.0
        height = 14.0
        viewBox = "0 0 12 14"
        fill = "none"
        path {
            d = "M1 8L4 12.5L10.5 1.5"
            stroke = color
            strokeWidth = 1.4
            strokeLinecap = StrokeLinecap.round
            strokeLinejoin = StrokeLinejoin.round
        }
    }
}

val SettingsIcon = FC<IconProps> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 16.0
        height = 18.0
        viewBox = "0 0 16 18"
        fill = "none"
        path {
            d = "M10.1752 3.34234L9.58616 3.4564C9.62659 3.66519 9.77453 3.83699 9.97501 3.90795L10.1752 3.34234ZM9.85518 1.68946L10.4442 1.5754C10.3991 1.34226 10.2207 1.15768 9.98923 1.10463L9.85518 1.68946ZM12.0693 4.43746L11.6795 4.89353C11.8412 5.03177 12.064 5.07404 12.2651 5.00463L12.0693 4.43746ZM13.6608 3.88819L14.0998 3.47915C13.9381 3.30561 13.6893 3.24363 13.4651 3.32102L13.6608 3.88819ZM15.3453 6.8016L15.7386 7.25471C15.9177 7.09922 15.9885 6.85279 15.919 6.62597L15.3453 6.8016ZM14.0731 7.90592L13.6798 7.45281C13.5194 7.59205 13.4445 7.80573 13.483 8.01463L14.0731 7.90592ZM14.0731 10.0941L13.483 9.98537C13.4445 10.1943 13.5194 10.408 13.6798 10.5472L14.0731 10.0941ZM15.3453 11.1984L15.919 11.374C15.9885 11.1472 15.9177 10.9008 15.7386 10.7453L15.3453 11.1984ZM13.6608 14.1118L13.4651 14.679C13.6893 14.7564 13.9381 14.6944 14.0998 14.5209L13.6608 14.1118ZM12.0693 13.5625L12.2651 12.9954C12.064 12.926 11.8412 12.9682 11.6795 13.1065L12.0693 13.5625ZM10.1752 14.6577L9.97501 14.092C9.77453 14.163 9.62659 14.3348 9.58616 14.5436L10.1752 14.6577ZM9.85518 16.3105L9.98923 16.8954C10.2207 16.8423 10.3991 16.6577 10.4442 16.4246L9.85518 16.3105ZM6.4901 16.3105L5.90104 16.4246C5.94619 16.6577 6.12458 16.8423 6.35605 16.8954L6.4901 16.3105ZM6.17006 14.6577L6.75912 14.5436C6.71869 14.3348 6.57075 14.163 6.37027 14.0921L6.17006 14.6577ZM4.27588 13.5626L4.66576 13.1065C4.50405 12.9683 4.28123 12.926 4.08013 12.9954L4.27588 13.5626ZM2.6845 14.1118L2.24553 14.5208C2.40725 14.6944 2.65602 14.7564 2.88025 14.679L2.6845 14.1118ZM1 11.1984L0.606692 10.7453C0.427558 10.9008 0.356847 11.1472 0.426279 11.374L1 11.1984ZM2.27213 10.0941L2.66543 10.5473C2.82585 10.408 2.90068 10.1943 2.86219 9.98542L2.27213 10.0941ZM2.27213 7.90585L2.86219 8.01457C2.90068 7.80567 2.82585 7.59199 2.66543 7.45274L2.27213 7.90585ZM1 6.80162L0.426279 6.626C0.356847 6.85281 0.427559 7.09924 0.606692 7.25474L1 6.80162ZM2.6845 3.8882L2.88025 3.32103C2.65602 3.24364 2.40725 3.30562 2.24553 3.47916L2.6845 3.8882ZM4.27588 4.43744L4.08013 5.00461C4.28123 5.07402 4.50405 5.03175 4.66576 4.89351L4.27588 4.43744ZM6.17006 3.34231L6.37027 3.90792C6.57075 3.83696 6.71869 3.66516 6.75912 3.45637L6.17006 3.34231ZM6.4901 1.68947L6.35605 1.10464C6.12458 1.15769 5.94619 1.34227 5.90104 1.57541L6.4901 1.68947ZM10.7643 3.22828L10.4442 1.5754L9.26612 1.80352L9.58616 3.4564L10.7643 3.22828ZM12.4592 3.9814C11.8508 3.4613 11.1463 3.04959 10.3754 2.77673L9.97501 3.90795C10.6046 4.13081 11.181 4.46744 11.6795 4.89353L12.4592 3.9814ZM13.4651 3.32102L11.8736 3.87029L12.2651 5.00463L13.8566 4.45536L13.4651 3.32102ZM13.2218 4.29723C13.926 5.05285 14.462 5.966 14.7716 6.97723L15.919 6.62597C15.5551 5.43702 14.9253 4.36508 14.0998 3.47915L13.2218 4.29723ZM14.4664 8.35903L15.7386 7.25471L14.952 6.34849L13.6798 7.45281L14.4664 8.35903ZM14.7726 9C14.7726 8.58971 14.7351 8.18764 14.6631 7.7972L13.483 8.01463C13.5418 8.33369 13.5726 8.66298 13.5726 9H14.7726ZM14.6631 10.2028C14.7351 9.81236 14.7726 9.41029 14.7726 9H13.5726C13.5726 9.33702 13.5418 9.66631 13.483 9.98537L14.6631 10.2028ZM15.7386 10.7453L14.4664 9.64097L13.6798 10.5472L14.952 11.6515L15.7386 10.7453ZM14.7716 11.0228C14.462 12.034 13.926 12.9471 13.2218 13.7028L14.0998 14.5209C14.9253 13.6349 15.5551 12.563 15.919 11.374L14.7716 11.0228ZM11.8736 14.1297L13.4651 14.679L13.8566 13.5446L12.2651 12.9954L11.8736 14.1297ZM10.3754 15.2233C11.1463 14.9504 11.8508 14.5387 12.4592 14.0186L11.6795 13.1065C11.181 13.5326 10.6046 13.8692 9.97501 14.092L10.3754 15.2233ZM10.4442 16.4246L10.7643 14.7717L9.58616 14.5436L9.26612 16.1965L10.4442 16.4246ZM9.72113 15.7257C9.22392 15.8397 8.70571 15.9 8.17266 15.9V17.1C8.79655 17.1 9.40469 17.0294 9.98923 16.8954L9.72113 15.7257ZM8.17266 15.9C7.6396 15.9 7.12137 15.8397 6.62415 15.7257L6.35605 16.8954C6.9406 17.0293 7.54875 17.1 8.17266 17.1V15.9ZM5.58101 14.7718L5.90104 16.4246L7.07916 16.1965L6.75912 14.5436L5.58101 14.7718ZM3.886 14.0186C4.4944 14.5387 5.199 14.9505 5.96986 15.2233L6.37027 14.0921C5.74064 13.8692 5.16419 13.5326 4.66576 13.1065L3.886 14.0186ZM2.88025 14.679L4.47163 14.1297L4.08013 12.9954L2.48874 13.5446L2.88025 14.679ZM3.12346 13.7028C2.41934 12.9471 1.88328 12.034 1.57372 11.0227L0.426279 11.374C0.790243 12.563 1.41999 13.6349 2.24553 14.5208L3.12346 13.7028ZM1.87882 9.64104L0.606692 10.7453L1.39331 11.6515L2.66543 10.5473L1.87882 9.64104ZM1.5726 9C1.5726 9.41032 1.61011 9.81241 1.68206 10.2029L2.86219 9.98542C2.8034 9.66635 2.7726 9.33704 2.7726 9H1.5726ZM1.68206 7.79713C1.61011 8.18759 1.5726 8.58968 1.5726 9H2.7726C2.7726 8.66296 2.8034 8.33365 2.86219 8.01457L1.68206 7.79713ZM0.606692 7.25474L1.87882 8.35896L2.66543 7.45274L1.39331 6.34851L0.606692 7.25474ZM1.57372 6.97725C1.88328 5.96601 2.41934 5.05286 3.12346 4.29724L2.24553 3.47916C1.41999 4.36509 0.790242 5.43704 0.426279 6.626L1.57372 6.97725ZM4.47163 3.87027L2.88025 3.32103L2.48874 4.45537L4.08013 5.00461L4.47163 3.87027ZM5.96986 2.77669C5.199 3.04955 4.4944 3.46127 3.886 3.98138L4.66576 4.89351C5.16419 4.46741 5.74064 4.13078 6.37027 3.90792L5.96986 2.77669ZM5.90104 1.57541L5.58101 3.22825L6.75912 3.45637L7.07916 1.80353L5.90104 1.57541ZM6.62415 2.2743C7.12137 2.16034 7.6396 2.1 8.17266 2.1V0.9C7.54875 0.9 6.9406 0.970651 6.35605 1.10464L6.62415 2.2743ZM8.17266 2.1C8.70571 2.1 9.22392 2.16033 9.72113 2.2743L9.98923 1.10463C9.40469 0.970648 8.79655 0.9 8.17266 0.9V2.1Z"
            fill = color
        }

        circle {
            cx = 8.17261
            cy = 9.0
            r = 3.0
            stroke = color
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
        }
    }
}

val HideIcon = FC<IconProps> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"
        path {
            d = "M15 9.59998C11 9.59998 7.59998 12 5.59998 15C7.59998 18 11 20.4 15 20.4C19 20.4 22.4 18 24.4 15C22.4 12 19 9.59998 15 9.59998Z"
            stroke = color
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
        }

        circle {
            cx = 15.0
            cy = 15.0
            r = 3.5
            stroke = color
            strokeWidth = 1.2
        }

        path {
            d = "M8.5 8.5L21.5 21.5"
            stroke = color
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
            strokeLinecap = StrokeLinecap.round
        }
    }
}

val UnhideIcon = FC<IconProps> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"
        path {
            d = "M15 9.59998C11 9.59998 7.59998 12 5.59998 15C7.59998 18 11 20.4 15 20.4C19 20.4 22.4 18 24.4 15C22.4 12 19 9.59998 15 9.59998Z"
            stroke = color
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
        }

        circle {
            cx = 15.0
            cy = 15.0
            r = 3.5
            stroke = color
            strokeWidth = 1.2
        }
    }
}

val LockIcon = FC<IconProps> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"

        path {
            d = "M17.5 12.5V10.5C17.5 9.11929 16.3807 8 15 8C13.6193 8 12.5 9.11929 12.5 10.5V12.5"
            stroke = color
            strokeWidth = 1.2
        }
        path {
            d = "M10 13C10 12.7239 10.2239 12.5 10.5 12.5H19.5C19.7761 12.5 20 12.7239 20 13V20C20 20.2761 19.7761 20.5 19.5 20.5H10.5C10.2239 20.5 10 20.2761 10 20V13Z"
            stroke = color
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
        }
    }
}

val UnlockIcon = FC<IconProps> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"

        path {
            d = "M11.9 10C11.9 10.3314 12.1686 10.6 12.5 10.6C12.8314 10.6 13.1 10.3314 13.1 10H11.9ZM13.1 10C13.1 8.95066 13.9507 8.1 15 8.1V6.9C13.2879 6.9 11.9 8.28792 11.9 10H13.1ZM15 8.1C16.0493 8.1 16.9 8.95066 16.9 10H18.1C18.1 8.28792 16.7121 6.9 15 6.9V8.1ZM16.9 10V12.5H18.1V10H16.9Z"
            fill = color
        }
        path {
            d = "M10 13C10 12.7239 10.2239 12.5 10.5 12.5H19.5C19.7761 12.5 20 12.7239 20 13V20C20 20.2761 19.7761 20.5 19.5 20.5H10.5C10.2239 20.5 10 20.2761 10 20V13Z"
            stroke = color
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
        }
    }
}

val InviteLinkIcon = FC<IconProps> { props ->
    val color = "white"
    svg {
        className = props.className
        width = 32.0
        height = 32.0
        viewBox = "0 0 32 32"

        path {
            d = "M 10 11 C 7.2504084 11 5 13.250451 5 16 C 5 18.749549 7.2504084 21 10 21 L 14 21 L 14 19 L 10 19 C 8.3313116 19 7 17.668651 7 16 C 7 14.331349 8.3313116 13 10 13 L 14 13 L 14 11 L 10 11 z M 18 11 L 18 13 L 22 13 C 23.668639 13 25 14.331361 25 16 C 25 17.668639 23.668639 19 22 19 L 18 19 L 18 21 L 22 21 C 24.749561 21 27 18.749561 27 16 C 27 13.250439 24.749561 11 22 11 L 18 11 z M 11 15 L 11 17 L 21 17 L 21 15 L 11 15 z "
            fill = color
        }
    }
}

val FeedbackIcon = FC<IconProps> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"

        path {
            d = "M11.5 23.0343V20.5H10C8.89543 20.5 8 19.6046 8 18.5V11.5C8 10.3954 8.89543 9.5 10 9.5H20C21.1046 9.5 22 10.3954 22 11.5V18.5C22 19.6046 21.1046 20.5 20 20.5H15L12.1828 23.3172C11.9309 23.5691 11.5 23.3907 11.5 23.0343Z"
            stroke = color
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
        }
    }
}

val MailIcon = FC<IconProps> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"
        rect {
            x = 6.0
            y = 9.0
            width = 18.0
            height = 12.0
            rx = 1.0
            stroke = color
            strokeWidth = 1.2
        }

        path {
            d = "M6.5 9.5L13.972 16.5324C14.5495 17.076 15.4505 17.076 16.028 16.5324L23.5 9.5"
            stroke = color
            strokeWidth = 1.2
        }
        path {
            d = "M6.5 20.5L12.5 15"
            stroke = color
            strokeWidth = 1.2
        }
        path {
            d = "M23.5 20.5L17.5 15"
            stroke = color
            strokeWidth = 1.2
        }
    }
}

val CopyIcon = FC<IconProps> { props ->
    val color = "black"
    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"
        path {
            d = "M11 9.5C11 9.22386 11.2239 9 11.5 9H20.5C20.7761 9 21 9.22386 21 9.5V22.5C21 22.7761 20.7761 23 20.5 23H11.5C11.2239 23 11 22.7761 11 22.5V9.5Z"
            stroke = color
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
        }
        path {
            d = "M17.5 6.5H9C8.72386 6.5 8.5 6.72386 8.5 7V19.5"
            stroke = color
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
            strokeLinecap = StrokeLinecap.round
        }
    }
}

val SidebarIcon = createIcon(30, svgAttrs = jso {
    shapeRendering = "crispEdges"
}) {
    for (y in listOf(8.0, 14.0, 20.0)) {
        rect {
            x = 5.0
            this.y = y
            width = 20.0
            height = 2.0
            fill = "currentColor"
            stroke = "currentColor"
        }
    }
}

val PresenterIcon = FC<IconProps> { props ->
    svg {
        className = props.className
        width = 26.0
        height = 26.0
        viewBox = "0 0 24 24"
        fill = "currentcolor"
        path {
            d = "M20 2H4C3.45 2 3 2.45 3 3V4C3 4.55 3.45 5 4 5H5V14H11V16.59L6.79 20.79L8.21 22.21L11 19.41V22H13V19.41L15.79 22.21L17.21 20.79L13 16.59V14H19V5H20C20.55 5 21 4.55 21 4V3C21 2.45 20.55 2 20 2M17 12H7V5H17V12Z"
        }
    }
}

val ExportIcon = FC<IconProps> { props ->
    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"
        path {
            d = "M15 6.5L15 17M15 6.5L12 9.5M15 6.5L18 9.5M12.5 12.5L9.5 12.5L9.5 22.5L20.5 22.5L20.5 12.5L17.5 12.5"
            stroke = "currentcolor"
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
            strokeLinecap = StrokeLinecap.round
        }
    }
}

val AboutIcon = FC<IconProps> { props ->
    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"
        path {
            d = "M15 18.5V14H14M15 18.5H14M15 18.5H16"
            stroke = "currentcolor"
            strokeWidth = 1.2
            strokeLinejoin = StrokeLinejoin.round
            strokeLinecap = StrokeLinecap.round
        }
        circle {
            cx = 15.0
            cy = 11.25
            r = 0.9
            fill="currentcolor"
        }
        rect {
            x = 8.5
            y = 8.5
            width = 13.0
            height = 13.0
            rx = 3.0
            stroke = "currentcolor"
        }
    }
}

val HistogramIcon = FC<IconProps> { props ->
    val binWidth = 3.0

    svg {
        className = props.className
        width = 30.0
        height = 30.0
        viewBox = "0 0 30 30"
        fill = "none"
        listOf(5.0, 15.0, 11.0, 4.0).mapIndexed { i, binHeight ->
            rect {
                width = binWidth
                x = 6 + i * (binWidth + 2)
                y = 24 - binHeight
                rx = 1.0
                height = binHeight
                fill = "currentcolor"
            }
        }
    }
}


val DragIndicatorIcon =
    createIcon("M11 18c0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2 2 .9 2 2zm-2-8c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0-6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm6 4c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z", boxSize=List2(24,24))
val GroupsIcon =
    createIcon("M12 12.75c1.63 0 3.07.39 4.24.9 1.08.48 1.76 1.56 1.76 2.73V18H6v-1.61c0-1.18.68-2.26 1.76-2.73 1.17-.52 2.61-.91 4.24-.91zM4 13c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm1.13 1.1c-.37-.06-.74-.1-1.13-.1-.99 0-1.93.21-2.78.58C.48 14.9 0 15.62 0 16.43V18h4.5v-1.61c0-.83.23-1.61.63-2.29zM20 13c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm4 3.43c0-.81-.48-1.53-1.22-1.85-.85-.37-1.79-.58-2.78-.58-.39 0-.76.04-1.13.1.4.68.63 1.46.63 2.29V18H24v-1.57zM12 6c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3z", boxSize = List2(24,24))
val TimelineIcon =
    createIcon("M23 8c0 1.1-.9 2-2 2-.18 0-.35-.02-.51-.07l-3.56 3.55c.05.16.07.34.07.52 0 1.1-.9 2-2 2s-2-.9-2-2c0-.18.02-.36.07-.52l-2.55-2.55c-.16.05-.34.07-.52.07s-.36-.02-.52-.07l-4.55 4.56c.05.16.07.33.07.51 0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2c.18 0 .35.02.51.07l4.56-4.55C8.02 9.36 8 9.18 8 9c0-1.1.9-2 2-2s2 .9 2 2c0 .18-.02.36-.07.52l2.55 2.55c.16-.05.34-.07.52-.07s.36.02.52.07l3.55-3.56C19.02 8.35 19 8.18 19 8c0-1.1.9-2 2-2s2 .9 2 2z", boxSize = List2(24,24))

val HelpIcon = createIcon(30) {
    path {
        d="M15 16.5C15 15.3 15.7614 14.7342 16.2 14.3C16.6386 13.8658 17.2 13.35 17.2 12.5C17.2 11.4456 16.3037 10.5 15.1 10.5C13.95 10.5 13.0514 11.4476 13 12.6"
        stroke="currentColor"
        fill = "none"
        strokeWidth=1.2
        strokeLinecap=StrokeLinecap.round
    }
    circle { cx=15.0; cy=19.0; r=0.9; fill="currentColor"; stroke="none"; }
    circle { cx=15.0; cy=15.0; r=7.5; stroke="currentColor"; fill="none"; strokeWidth=1.2; }
}