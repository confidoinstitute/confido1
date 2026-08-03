package websocket

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import payloads.responses.TopicCommand
import payloads.responses.TopicFrame
import tools.confido.serialization.confidoJSON
import utils.webSocketUrl
import web.events.EventHandler
import web.location.location
import web.messaging.MessageEvent
import web.sockets.CloseEvent
import web.sockets.WebSocket
import web.timers.Timeout
import web.timers.setTimeout
import web.window.window

/**
 * The single multiplexed state websocket connection (PERSISTENCE_SYNC_REDESIGN.md §4.1).
 *
 * All server subscriptions share this one connection; components subscribe to topics
 * (the URL paths of the former dedicated websocket endpoints) via [subscribe] or the
 * `useSubscription` hook. Reconnection with capped exponential backoff, the
 * bundle-version reload (close code 4001) and the unauthorized handling (3000) all
 * live here, replacing the three divergent reconnect implementations this codebase
 * used to carry.
 */
object MultiplexWS {
    private var ws: WebSocket? = null
    private var started = false
    private var connected = false
    private var attempts = 0
    private var reconnectTimer: Timeout? = null

    /** Invoked when the server closes with code 3000 (unauthorized). */
    var onUnauthorized: (() -> Unit)? = null

    private class Entry {
        var count = 0
        var last: JsonElement? = null
        val listeners = mutableSetOf<(JsonElement?, Boolean) -> Unit>()
    }

    private val subs = mutableMapOf<String, Entry>()

    /** Idempotent; the connection is kept alive (with reconnects) once started. */
    fun start() {
        if (started) return
        started = true
        connect()
    }

    private fun connect() {
        val bundleVer = window.asDynamic().bundleVer as String
        val appConfigVer = window.asDynamic().appConfigVer as String
        val socket = WebSocket(webSocketUrl("/state?bundleVer=${bundleVer}&appConfigVer=${appConfigVer}"))
        ws = socket
        socket.onopen = EventHandler {
            attempts = 0
            connected = true
            subs.keys.forEach { topic -> sendCommand(TopicCommand(sub = topic)) }
        }
        socket.onmessage = EventHandler { event: MessageEvent<*> ->
            val frame = try {
                confidoJSON.decodeFromString<TopicFrame>(event.data.toString())
            } catch (e: Exception) {
                console.error("Malformed websocket frame", e.toString())
                return@EventHandler
            }
            subs[frame.topic]?.let { entry ->
                entry.last = frame.data
                entry.listeners.toList().forEach { it(frame.data, false) }
            }
        }
        socket.onclose = EventHandler { event: CloseEvent ->
            ws = null
            connected = false
            when (event.code.toInt()) {
                4001 -> {
                    // Incompatible frontend version; a reload gets the fresh bundle.
                    location.reload()
                    return@EventHandler
                }
                3000 -> onUnauthorized?.invoke()
            }
            // Everything we have is now stale; keep last values so UIs can show stale data.
            subs.values.forEach { entry -> entry.listeners.toList().forEach { it(entry.last, true) } }
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!started || reconnectTimer != null) return
        val delay = minOf(15_000, 1000 * (1 shl minOf(attempts, 4)))
        attempts += 1
        reconnectTimer = setTimeout({
            reconnectTimer = null
            if (started) connect()
        }, delay)
    }

    private fun sendCommand(cmd: TopicCommand) {
        if (connected) ws?.send(confidoJSON.encodeToString(cmd))
    }

    /**
     * Subscribe [listener] to [topic]. The listener receives the topic's last known
     * payload (an encoded `WSResponse<T>`, null before the first one arrives) and a
     * staleness flag; it is called immediately with the current state, on every
     * payload, and on disconnect. Returns an unsubscribe function. Subscriptions to
     * the same topic share one server-side subscription.
     */
    fun subscribe(topic: String, listener: (JsonElement?, Boolean) -> Unit): () -> Unit {
        val entry = subs.getOrPut(topic) { Entry() }
        entry.count += 1
        entry.listeners.add(listener)
        if (entry.count == 1 && connected) sendCommand(TopicCommand(sub = topic))
        listener(entry.last, !connected)
        return {
            entry.count -= 1
            entry.listeners.remove(listener)
            if (entry.count <= 0) {
                subs.remove(topic)
                sendCommand(TopicCommand(unsub = topic))
            }
        }
    }
}
