package utils

import components.redesign.basic.RoomPalette
import csstype.Color
import dom.events.Touch
import dom.events.TouchEvent
import dom.events.TouchList
import dom.html.HTMLElement
import dom.html.Window
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.js.WeakMap
import kotlinx.js.get
import kotlinx.js.jso
import react.StateInstance
import react.useEffect
import react.useMemo
import react.useState
import rooms.Room
import tools.confido.question.Question
import tools.confido.utils.*
import web.events.Event
import web.events.LegacyEvent
import web.location.location
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.js.Date
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.reflect.KProperty

// Build an object which does not have properly defined interface
inline fun <T: Any> buildObject(init: dynamic.() -> Unit): T =
    jso(init).unsafeCast<T>()

fun Number.toDateTime(): String = Date(this.toDouble() * 1000).toLocaleString()
fun Number.toIsoDateTime(): String = Date(this.toDouble() * 1000).toISOString()


fun linearSpace(first: Double, last: Double, step: Double) = sequence {
    var current = first
    while (current <= last) {
        yield(current)
        current += step
    }
}

fun roundNumbers() = sequence {
    var base = 1.0
    while (true) {
        yield(base)
        yield(2*base)
        yield(5*base)
        base *= 10
    }
}

fun markSpacing(width: Double, start: Double, end: Double, formatter: ((value: Number) -> String)?): List<Double> {
    val range = end - start
    val unitWidth = width / range

    if (width == 0.0) return emptyList()

    fun strLength(x: Double) = (formatter?:{it.toString()})(x).length

    val markBase = roundNumbers().takeWhile{it <= range}.find {step ->
        val lastMark = floor(end / step) * step
        (width / (range / step) >= strLength(lastMark) * 10 + 10)
    } ?: return emptyList()

    var firstMark = ceil(start / markBase) * markBase
    if (unitWidth * (firstMark - start) < 10 * strLength(firstMark) + 10) firstMark += markBase

    var lastMark = floor(end / markBase) * markBase
    if (unitWidth * (end - lastMark) < 10 * strLength(lastMark) + 10) lastMark -= markBase

    return sequence {
        yield(start)
        var mark = firstMark
        while (mark <= lastMark) {
            yield(mark)
            mark += markBase
        }
        yield(end)

    }.toList()
}

@JsName("monthSpacing")

fun monthSpacing(start: LocalDate, end: LocalDate, monthStep: Int = 1): List<LocalDate> {
    val startMonth = ((start.monthNumber + monthStep - 1) / monthStep) * (monthStep)
    return sequence {
        var date = LocalDate(start.year, startMonth, 1)
        val period = DatePeriod(0, monthStep, 0)
        if (date < start) date += period
        while (date < end) {
            yield(date)
            date += period
        }
    }.toList()
}

fun yearSpacing(start: LocalDate, end: LocalDate, yearStep: Int = 1): List<LocalDate> {
    val startYear = ((start.year) / yearStep) * (yearStep)
    return sequence {
        var date = LocalDate(startYear, 1, 1)
        val period = DatePeriod(yearStep, 0, 0)
        if (date < start) date += period
        while (date < end) {
            yield(date)
            date += period
        }
    }.toList()
}

fun timestampToDate(timestamp: Double): LocalDate {
    val dateTime = Instant.fromEpochSeconds(timestamp.toLong(), 0).toLocalDateTime(TimeZone.UTC)
    return LocalDate(dateTime.year, dateTime.month, dateTime.dayOfMonth)
}

fun dateMarkSpacing(width: Double, start: Double, end: Double): List<Double> {
    val rangeDays = (end - start) / 86400
    val dayWidth = width / rangeDays

    if (width == 0.0) return emptyList()

    val dates: List<Double> = when {
        // Days granularity possible
        (dayWidth >= 100) -> return linearSpace(ceil(start / 86400.0) * 86400.0, end, 86400.0).toList()
        // Month granularity OK
        (dayWidth * 240 >= 100) -> {
            val monthStep = when {
                (dayWidth * 30 >= 100) -> 1
                (dayWidth * 90 >= 100) -> 3
                else -> 6
            }
            monthSpacing(timestampToDate(start), timestampToDate(end), monthStep).map {it.toEpochDays() * 86400.0 }
        }
        // Switch to year granularity
        else -> {
            val yearStep = roundNumbers().takeWhile{it * 365 <= rangeDays}.find { step ->
                dayWidth * 365 * step >= 100
            } ?: return emptyList()
            yearSpacing(timestampToDate(start), timestampToDate(end), yearStep.toInt()).map {it.toEpochDays() * 86400.0 }
        }
    }

    return dates
}

fun durationAgo(difference: Number) = difference.toInt().let {
    when(it) {
        in 0..10 -> "now"
        in 10..120 -> "$it s"
        in 120..7200 -> "${it / 60} min"
        in 7200..172800 -> "${it / 3600} h"
        else -> "${it / 86400} days"
    }
}

fun webSocketUrl(path: String): String {
    val protocol = when(location.protocol) {
        "https:" -> "wss:"
        else -> "ws:"
    }
    return protocol + "//" + location.host + path
}
fun stringToColor(str: String): csstype.Color {
    // 🪄 numbers generated by a random choice. Guaranteed to be random.
    val base = str.fold(0x00a043) { acc, c ->
        (acc * 0x3ca431 + c.code) and 0xffffff
    }
    return Color("#${base.toString(16).padStart(6,'0')}")
}

/** A very simple check for ruling out most common mistakes. */
fun isEmailValid(mail: String): Boolean {
    val regex = Regex(".+@.+\\..+")
    return regex.matches(mail)
}

fun <E> List<List<E>>.transposeForHeatmap(missingElementDefault: E? = null): List<List<E?>> {
    val input = this

    val columnIndices = 0 until input.size-1

    val maxRowSize = input.maxOf { it.size }
    val rowIndices = 0 until maxRowSize

    return rowIndices.map { columnIndex ->
        columnIndices.map { rowIndex ->
            // instead of getting input[column][row], get input[row][column]
            val element = input.getOrNull(rowIndex)?.getOrNull(columnIndex)
            element  ?: missingElementDefault
        }
    }
}

fun <E> Set<E>.xor(element: E) =
    if (element in this) this.minus(element) else this.plus(element)

inline fun runCoroutine(noinline coro: suspend CoroutineScope.() -> Unit) { CoroutineScope(EmptyCoroutineContext).launch(block = coro) }

fun questionUrl(id: String) = Question.urlPrefix(id)
fun roomUrl(id: String) = Room.urlPrefix(id)

private val oidMap = WeakMap<Any, Int>()
private var oidMax = 0

/**
 * Generate unique integer determining object's identity, like python's id() function.
 * https://stackoverflow.com/questions/2020670/javascript-object-id/35306050#35306050
 */
fun objectId(obj: Any) =
    oidMap[obj] ?: run {
        oidMax += 1
        val newId = oidMax
        oidMap[obj] = newId
        newId
    }

/**
 * A variant of the addEventListener function that allows callbacks that take the
 * new web.events.Event types instead of the legacy org.w3c.dom.events.Event
 */
fun Window.addEventListener(type: String, callback: (Event)->Unit, options: dynamic = jso<dynamic>()) =
    addEventListener(type, callback.unsafeCast<(LegacyEvent)->Unit>(), options)
fun Window.removeEventListener(type: String, callback: (Event)->Unit, options: dynamic = jso<dynamic>()) =
    removeEventListener(type, callback.unsafeCast<(LegacyEvent)->Unit>(), options)

// HACK to allow defining class properties as "by useState"
inline operator fun <T, U> StateInstance<T>.getValue(thisRef: U, property: KProperty<*>) : T =
    asDynamic()[0]
inline operator fun <T, U> StateInstance<T>.setValue(
    thisRef: U,
    property: KProperty<*>,
    value: T,
) { asDynamic()[1](value) }
