package icons

import kotlinx.js.Object
import mui.material.SvgIcon
import mui.material.SvgIconProps
import org.w3c.dom.svg.SVGSVGElement
import react.ChildrenBuilder
import react.FC
import react.ReactNode
import react.create
import react.dom.svg.ReactSVG
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.SVGAttributes

fun createSvgIcon(inside: ReactNode) = FC<SvgIconProps> {
    SvgIcon {
        Object.assign(this, it)
        this.children = inside
    }
}

val ExpandMore = createSvgIcon(
        svg.create {
            ReactSVG.path {
                d = "M16.59 8.59 12 13.17 7.41 8.59 6 10l6 6 6-6z"
            }
        }
)

val NewReleases = createSvgIcon(
    svg.create {
        ReactSVG.path {
            d = "m23 12-2.44-2.78.34-3.68-3.61-.82-1.89-3.18L12 3 8.6 1.54 6.71 4.72l-3.61.81.34 3.68L1 12l2.44 2.78-.34 3.69 3.61.82 1.89 3.18L12 21l3.4 1.46 1.89-3.18 3.61-.82-.34-3.68L23 12zm-10 5h-2v-2h2v2zm0-4h-2V7h2v6z"
        }
    }
)

val EditIcon = createSvgIcon(
    svg.create {
        ReactSVG.path {
            d = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.9959.9959 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
        }
    }
)

val AddIcon = createSvgIcon(
    svg.create {
        ReactSVG.path {
            d = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
        }
    }
)

val CommentIcon = createSvgIcon(
    svg.create {
        ReactSVG.path {
            d = "M21.99 4c0-1.1-.89-2-1.99-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h14l4 4-.01-18zM18 14H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"
        }
    }
)

val CloseIcon = createSvgIcon(
    svg.create {
        ReactSVG.path {
            d = "M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
        }
    }
)

val BarChart = createSvgIcon(
    svg.create {
        ReactSVG.path {
            d = "M4 9h4v11H4zm12 4h4v7h-4zm-6-9h4v16h-4z"
        }
    }
)

val DeleteIcon = createSvgIcon(
    svg.create {
        ReactSVG.path {
            d = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"
        }
    }
)