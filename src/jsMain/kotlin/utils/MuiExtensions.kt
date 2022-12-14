package utils

import csstype.Width
import kotlinx.js.Object
import kotlinx.js.delete
import dom.html.HTMLDivElement
import dom.html.HTMLInputElement
import react.*
import react.dom.events.ChangeEvent
import react.dom.events.FormEvent
import react.dom.html.ReactHTML.br

fun FormEvent<HTMLDivElement>.eventValue(): String = this.asDynamic().target.value

fun FormEvent<HTMLDivElement>.eventNumberValue(): Double {
    val event = (this as ChangeEvent<HTMLInputElement>)
    return event.target.valueAsNumber
}

fun <T> themed(value: Int): T {
    return value.asDynamic() as T
}

fun <T> byTheme(key: String): T {
    return key.asDynamic() as T
}

fun <T> T.except(vararg except: String): T  where T: Props {
    var newProps = jsObject {  }
    Object.assign(newProps, this)
    except.forEach { delete(newProps[it]) }
    return newProps as T
}

val WIDTH_AUTO get() = "auto".asDynamic() as Width

fun breakLines(text: String): ReactNode =
    Fragment.create {
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) br()
            +line
        }
    }