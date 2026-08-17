package tools.confido.application.sessions

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tools.confido.store.TransientCell

/**
 * Short-lived session data. Will be lost upon server restart.
 *
 * (The manual websocket-refresh machinery that used to live here — the global and
 * per-session refresh signals — is gone: topics are driven by store/View change
 * signals; see PERSISTENCE_SYNC_REDESIGN.md §4.2 and the Phase 3 notes in §7.)
 */
class TransientData {
    private val _activePresenterWindows = TransientCell(0)
    /** Snapshot flow of the presenter-window count; topic derivations subscribe here. */
    val activePresenterWindowsState: StateFlow<Int> get() = _activePresenterWindows.state
    val activePresenterWindows: Int get() = _activePresenterWindows.value
    fun addPresenterWindows(delta: Int) = _activePresenterWindows.update { it + delta }
}