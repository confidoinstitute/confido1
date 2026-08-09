package tools.confido.application.sessions

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// A monotonic counter rather than a toggled flag: StateFlow is conflated and
// equality-based, so a value that flips away and back while a collector is busy
// would be dropped, leaving that client stale until the next unrelated mutation.
val globalWebsocketRefresh = MutableStateFlow(0L)
/**
 * Short-lived session data. Will be lost upon server restart.
 */
class TransientData {
    private val _websocketRefreshChannel: MutableStateFlow<Long> = MutableStateFlow(0L)
    private val websocketRefreshFlow: StateFlow<Long> = _websocketRefreshChannel.asStateFlow()
    private val _activePresenterWindows = MutableStateFlow(0)
    /** Snapshot flow of the presenter-window count; topic derivations subscribe here. */
    val activePresenterWindowsState: StateFlow<Int> get() = _activePresenterWindows
    var activePresenterWindows by _activePresenterWindows::value

    fun refreshSessionWebsockets() {
        _websocketRefreshChannel.update { it + 1 }
    }

    companion object {
        fun refreshAllWebsockets() {
            globalWebsocketRefresh.update { it + 1 }
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