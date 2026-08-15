package components

import react.createContext
import react.use

/**
 * Connection state of the multiplexed websocket, as a context separate from the data
 * contexts (the former `stale` member of AppStateContext). The value is the *debounced*
 * disconnected flag from [websocket.MultiplexWS] — brief blips never surface.
 *
 * Systematic gating policy (agreed 2026-08-15):
 *  - dialog action buttons disable automatically (DialogHeader folds this in);
 *  - *immediate-commit* controls — things that send on interaction (role selects,
 *    quick-settings actions, presenter controls, non-dialog submits) — opt in via
 *    [useDisconnected];
 *  - typing/local edits and navigation are never gated (browsing cached data while
 *    disconnected is a feature of the topic cache);
 *  - Client's send helpers refuse with a clear error while disconnected, so anything
 *    the UI sweep missed degrades to an explicit message, not a hung request;
 *  - the [components.redesign.basic.ConnectionBanner] names the state globally.
 */
val ConnectionContext = createContext<Boolean>()

/** True when the connection has been down long enough to gate mutation affordances. */
fun useDisconnected(): Boolean = use(ConnectionContext) ?: false
