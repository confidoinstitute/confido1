package components.redesign.basic

import csstype.ClassName
import csstype.PropertiesBuilder
import dom.html.HTMLDivElement
import emotion.react.css
import emotion.css.ClassName
import hooks.useElementSize
import react.FC
import react.Props
import react.PropsWithClassName
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.div
import react.useRef

inline fun PropsWithClassName.css(
    vararg classNames: ClassName?,
    override: ClassName?,
    crossinline block: PropertiesBuilder.() -> Unit,
) = css(*classNames, ClassName(block), override) {}
inline fun PropsWithClassName.css(
    vararg classNames: ClassName?,
    override: PropsWithClassName,
    crossinline block: PropertiesBuilder.() -> Unit,
) = css(*classNames, override=override.className, block=block)

external interface PropsWithElementSize: Props {
    var elementWidth: Double
    var elementHeight: Double
}

external interface ElementSizeWrapperProps : PropsWithClassName, HTMLAttributes<HTMLDivElement> {
    var comp: FC<*>
    var props: Props
    var filler: FC<*>?
}

fun <P: PropsWithElementSize> elementSizeWrapper(component: FC<P>, className: ClassName?=null): FC<P> {
    return FC {props->
        val elementSize = useElementSize<HTMLDivElement>()
        div {
            //className = props.className
            this.ref = elementSize.ref
            this.className = className
            if (elementSize.known) {
                component {
                    key = "comp"
                    +props
                    elementWidth = elementSize.width
                    elementHeight = elementSize.height
                }
            }
        }
    }
}

