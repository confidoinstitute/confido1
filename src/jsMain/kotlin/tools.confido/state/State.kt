package tools.confido.state

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import web.window.window

// The client has no global state facade: components read topic subscriptions
// (useSubscription and the accessors built on it) and the narrow contexts
// (SelfContext, RoomContext, ConnectionContext). Entities are never client-derefed —
// they arrive in topic payloads (PERSISTENCE_SYNC_REDESIGN.md client endgame).

@OptIn(ExperimentalSerializationApi::class)
actual val appConfig: AppConfig = Json.decodeFromDynamic(window.asDynamic().appConfig)
