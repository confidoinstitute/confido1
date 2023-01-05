package utils

import csstype.AutoLength
import csstype.Width
import kotlinx.js.Object
import kotlinx.js.delete
import dom.html.HTMLDivElement
import dom.html.HTMLInputElement
import kotlinx.js.jso
import mui.material.InputBaseComponentProps
import react.*
import react.dom.events.ChangeEvent
import react.dom.events.FormEvent
import react.dom.html.InputHTMLAttributes
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
    var newProps = jso<dynamic> {  }
    Object.assign(newProps, this)
    except.forEach { delete(newProps[it]) }
    return newProps as T
}

val AUTO get() = "auto".asDynamic() as AutoLength

fun breakLines(text: String): ReactNode =
    Fragment.create {
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) br()
            +line
        }
    }

fun numericInputProps(min: Double?, max: Double?, step: Double?) = jso<InputHTMLAttributes<HTMLInputElement>> {
    this.min = min
    this.max = max
    this.step = step
}.unsafeCast<InputBaseComponentProps>()

