package websocket

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import payloads.responses.TopicCommand
import payloads.responses.TopicFrame
import payloads.responses.TopicUpdate
import tools.confido.serialization.confidoJSON
import utils.webSocketUrl
import web.events.EventHandler
import web.location.location
import web.messaging.MessageEvent
import web.sockets.CloseEvent
import web.sockets.WebSocket
import web.timers.Timeout
import web.timers.clearTimeout
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

    /**
     * Debounced "the connection has been down long enough to surface in the UI"
     * (PERSISTENCE_SYNC_REDESIGN.md client endgame): mutation affordances disable on
     * it, the reconnect banner shows on it, and Client's send helpers refuse on it.
     * The debounce keeps brief blips (instant reconnects) from ever flashing the UI;
     * reconnection clears it immediately. Lives here rather than in React state so
     * non-React code (Client) shares the exact same notion of "disconnected".
     */
    var disconnected = false
        private set
    private var hasConnected = false
    private var disconnectTimer: Timeout? = null
    private val connectionListeners = mutableSetOf<(Boolean) -> Unit>()
    private const val DISCONNECT_DEBOUNCE_MS = 1500

    /** Listen for [disconnected] changes; called immediately with the current value. */
    fun onConnectionChange(listener: (Boolean) -> Unit): () -> Unit {
        connectionListeners.add(listener)
        listener(disconnected)
        return { connectionListeners.remove(listener) }
    }

    private fun setDisconnected(value: Boolean) {
        if (disconnected == value) return
        disconnected = value
        connectionListeners.toList().forEach { it(value) }
    }

    private fun connectionUp() {
        disconnectTimer?.let { clearTimeout(it) }
        disconnectTimer = null
        hasConnected = true
        setDisconnected(false)
    }

    private fun connectionDown() {
        // Before the first successful connect the app is still on its loading screen;
        // the debounced flag is about losing a connection we had.
        if (!hasConnected || disconnectTimer != null || disconnected) return
        disconnectTimer = setTimeout({
            disconnectTimer = null
            if (!connected) setDisconnected(true)
        }, DISCONNECT_DEBOUNCE_MS)
    }

    private class Entry {
        var count = 0
        var last: JsonElement? = null
        // The typed decoded-payload cache: each frame is decoded ONCE per topic (not
        // once per listener) and the decoded value is cached, so late subscribers get
        // it synchronously and shared subscriptions cost nothing extra. The topic
        // determines the payload type, so the first subscriber's decoder serves all.
        var decoder: ((JsonElement) -> Any)? = null
        var decoded: Any? = null
        val listeners = mutableSetOf<(Any?, Boolean) -> Unit>()
    }

    private val subs = mutableMapOf<String, Entry>()

    // Sub/unsub commands issued in the same tick are coalesced into one TopicCommand
    // (a resubscribe cancels a pending unsubscribe outright, keeping the cached
    // payload). Batched subscriptions let the server answer with the initial payloads
    // of the whole set in one consistent frame.
    private val pendingSub = mutableSetOf<String>()
    private val pendingUnsub = mutableSetOf<String>()
    private var flushScheduled = false

    private fun scheduleFlush() {
        if (flushScheduled) return
        flushScheduled = true
        setTimeout({
            flushScheduled = false
            flush()
        }, 0)
    }

    private fun flush() {
        // Entries linger until their unsubscribe is actually sent, so a quick
        // unsubscribe/resubscribe (e.g. a route transition remounting components
        // sharing a topic) never drops the cache or churns the server subscription.
        pendingUnsub.forEach { topic ->
            subs[topic]?.takeIf { it.count <= 0 }?.let { subs.remove(topic) }
        }
        if (connected && (pendingSub.isNotEmpty() || pendingUnsub.isNotEmpty()))
            sendCommand(TopicCommand(sub = pendingSub.toList(), unsub = pendingUnsub.toList()))
        pendingSub.clear()
        pendingUnsub.clear()
    }

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
            connectionUp()
            // Resolve pending unsubscribes locally (nothing to tell the new connection
            // about them) and resubscribe everything live in one command.
            pendingUnsub.forEach { topic ->
                subs[topic]?.takeIf { it.count <= 0 }?.let { subs.remove(topic) }
            }
            pendingSub.clear()
            pendingUnsub.clear()
            if (subs.isNotEmpty()) sendCommand(TopicCommand(sub = subs.keys.toList()))
        }
        socket.onmessage = EventHandler { event: MessageEvent<*> ->
            val frame = try {
                confidoJSON.decodeFromString<TopicFrame>(event.data.toString())
            } catch (e: Exception) {
                console.error("Malformed websocket frame", e.toString())
                return@EventHandler
            }
            // Apply the frame atomically: all topic caches are updated (including the
            // decoded values) before any listener runs, so listeners (and anything they
            // render synchronously) never observe a multi-topic update half-applied.
            val applied = frame.updates.mapNotNull { update: TopicUpdate ->
                // Devtools/test surface: the last raw payload per topic, as plain JS.
                try {
                    if (window.asDynamic().curTopics == null) window.asDynamic().curTopics = js("({})")
                    window.asDynamic().curTopics[update.topic] =
                        update.data?.let { JSON.parse<dynamic>(it.toString()) }
                } catch (e: Exception) {}
                subs[update.topic]?.let { entry ->
                    entry.last = update.data
                    entry.decoded = update.data?.let { entry.decoder?.invoke(it) }
                    entry
                }
            }
            applied.forEach { entry ->
                entry.listeners.toList().forEach { it(entry.decoded, false) }
            }
        }
        socket.onclose = EventHandler { event: CloseEvent ->
            ws = null
            connected = false
            connectionDown()
            when (event.code.toInt()) {
                4001 -> {
                    // Incompatible frontend version; a reload gets the fresh bundle.
                    location.reload()
                    return@EventHandler
                }
                3000 -> onUnauthorized?.invoke()
            }
            // Everything we have is now stale; keep last values so UIs can show stale data.
            subs.values.forEach { entry -> entry.listeners.toList().forEach { it(entry.decoded, true) } }
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
     * Subscribe [listener] to [topic], receiving payloads decoded by [decode] (null
     * before the first payload arrives) plus a staleness flag; it is called
     * immediately with the current cached state, on every payload, and on disconnect.
     * Returns an unsubscribe function. Subscriptions to the same topic share one
     * server-side subscription and one decoded cache; all subscribers of a topic must
     * therefore use the same payload type (the first subscriber's [decode] serves
     * all). [decode] must not throw — map failures to an error value.
     */
    fun <T : Any> subscribe(topic: String, decode: (JsonElement) -> T, listener: (T?, Boolean) -> Unit): () -> Unit {
        val entry = subs.getOrPut(topic) { Entry() }
        if (entry.decoder == null) {
            entry.decoder = decode
            entry.last?.let { entry.decoded = decode(it) }
        }
        entry.count += 1
        @Suppress("UNCHECKED_CAST")
        val listener = listener as (Any?, Boolean) -> Unit
        entry.listeners.add(listener)
        if (entry.count == 1 && !pendingUnsub.remove(topic)) {
            // (If the topic was pending-unsub, the server subscription is still live
            // and the cached payload still valid — cancelling the unsub is all it takes.)
            pendingSub.add(topic)
            scheduleFlush()
        }
        listener(entry.decoded, !connected)
        return {
            entry.count -= 1
            entry.listeners.remove(listener)
            if (entry.count <= 0) {
                if (pendingSub.remove(topic)) {
                    // The subscribe command was never sent; nothing to undo server-side.
                    subs.remove(topic)
                } else {
                    pendingUnsub.add(topic)
                    scheduleFlush()
                }
            }
        }
    }
}
