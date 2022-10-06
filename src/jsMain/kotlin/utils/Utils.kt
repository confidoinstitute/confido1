package utils

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.window
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.datetime.*
import kotlin.js.Date

inline fun jsObject(init: dynamic.() -> Unit): dynamic {
    val o = js("{}")
    init(o)
    return o
}

fun Double.format(digits: Int): String = asDynamic().toFixed(digits)

fun Number.toISODay(): String = Date(this.toDouble() * 1000).toISOString().slice(0..9)
fun Number.toDateTime(): String = Date(this.toDouble() * 1000).toLocaleString()
fun String.toTimestamp(): Double = Date(this).getTime() / 1000

fun now() = Date().getTime() / 1000

fun linearSpace(first: Double, last: Double, step: Double) = sequence {
    var current = first
    while (current <= last) {
        yield(current)
        current += step
    }
}

fun roundNumbers() = sequence {
    var base = 1
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
            yearSpacing(timestampToDate(start), timestampToDate(end), yearStep).map {it.toEpochDays() * 86400.0 }
        }
    }

    return dates
}

fun durationAgo(difference: Double) = when(difference) {
    in 0.0..10.0 -> "now"
    in 10.0..120.0 -> "${floor(difference)} s"
    in 120.0..7200.0 -> "${floor(difference / 60)} min"
    in 7200.0..172800.0 -> "${floor(difference / 3600)} h"
    else -> "${floor(difference / 86400)} days"
}

suspend inline fun <reified T> HttpClient.postJson(urlString: String, payload: T, block: HttpRequestBuilder.() -> Unit) =
    this.post(urlString) {
        contentType(ContentType.Application.Json.withParameter("charset", "utf-8"))
        setBody(payload)
        block.invoke(this)
    }

fun webSocketUrl(path: String): String {
    val location = window.location
    val protocol = when(location.protocol) {
        "https:" -> "wss:"
        else -> "ws:"
    }
    return protocol + "//" + location.host + path
}