package utils

import csstype.*
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import react.dom.events.ChangeEvent
import react.dom.events.FormEvent

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