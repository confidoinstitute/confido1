package tools.confido.application.sessions

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

val globalWebsocketRefresh = MutableStateFlow(false)
/**
 * Short-lived session data. Will be lost upon server restart.
 */
class TransientData {
    private val _websocketRefreshChannel: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val websocketRefreshFlow: StateFlow<Boolean> = _websocketRefreshChannel.asStateFlow()

    fun refreshSessionWebsockets() {
        _websocketRefreshChannel.update { !it }
    }

    companion object {
        fun refreshAllWebsockets() {
            globalWebsocketRefresh.update { !it }
        }
    }

    /**
     * Suspends a block that is run every time [refreshSessionWebsockets] is called.
     * The [cancelNotifier] may be used to signal this loop to stop by emitting true.
     */
    suspend fun runRefreshable(cancelNotifier: Flow<Boolean>, block: suspend () -> Unit) {
        try {
            coroutineScope {
                launch {
                    cancelNotifier.takeWhile { it -> !it }.collect()
                    this@coroutineScope.cancel()
                }

                websocketRefreshFlow.combine(globalWebsocketRefresh) { a,b -> a to b }.collect {
                    block()
                }
            }
        } catch (e: CancellationException) {
            // Ignored
        }
    }
}