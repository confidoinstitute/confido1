package components.redesign.basic

import csstype.ClassName
import csstype.PropertiesBuilder
import emotion.react.css
import emotion.css.ClassName
import react.PropsWithClassName

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
