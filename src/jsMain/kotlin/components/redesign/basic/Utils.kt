package components.redesign.basic

import csstype.*
import dom.html.*
import emotion.css.*
import emotion.react.*
import hooks.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import utils.*

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
    var element: HTMLDivElement
    var elementRef: RefObject<HTMLDivElement>
}

external interface ElementSizeWrapperProps : PropsWithClassName, HTMLAttributes<HTMLDivElement> {
    var comp: FC<*>
    var props: Props
    var filler: FC<*>?
}

fun <P: PropsWithElementSize> elementSizeWrapper(component: FC<P>,  className: ClassName?=null, displayName: String?=null): FC<P> {
    return FC(displayName ?: component.displayName?.let{ it + "ESW" } ?: "ElementSizeWrapper" ) {props->
        val elementSize = useElementSize<HTMLDivElement>()
        div {
            //className = props.className
            this.ref = elementSize.ref
            this.className = className
            val refCur = elementSize.ref.current
            if (elementSize.known && refCur != null) {
                component {
                    key = "comp"
                    +props
                    elementWidth = elementSize.width
                    elementHeight = elementSize.height
                    element = refCur
                }
            }
        }
    }
}

fun <P: PropsWithClassName> ElementType<P>.withStyle(vararg except: String, block: PropertiesBuilder.(P) -> Unit) = FC<P> {props ->
    this@withStyle {
        +props.except(*except)
        css(override = props) {
            block(props)
        }
    }
}