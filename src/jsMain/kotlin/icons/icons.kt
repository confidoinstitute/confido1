package icons

import kotlinx.js.Object
import mui.material.SvgIcon
import mui.material.SvgIconProps
import react.FC
import react.ReactNode
import react.create
import react.dom.svg.ReactSVG
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg

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