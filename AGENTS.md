# AGENTS.md - Coding Agent Guidelines for Confido

## Project Overview

Confido is a **Kotlin Multiplatform** collaborative forecasting/prediction web application.

- **Backend**: Kotlin/JVM with Ktor 3.3.1, MongoDB via the official Kotlin coroutine driver (`bson-kotlinx` codec)
- **Frontend**: Kotlin/JS with React (via kotlin-wrappers), Emotion for CSS-in-JS
- **Shared**: Common code in `src/commonMain/` for models, serialization, utilities

## Build & Run Commands

```bash
./gradlew build                              # Build JVM + JS targets
./gradlew run                                # Run backend at http://localhost:8080/
./gradlew jsBrowserDevelopmentRun --continuous  # Frontend hot reload at :8081
./gradlew clean                              # Clean build artifacts
./gradlew releaseJvm                         # Self-contained tree: build/release/jvm
./gradlew releaseNative                      # Self-contained tree: build/release/native
```

- **JVM target**: Java 17, main class `tools.confido.application.ServerKt`
- **JS target**: ES2015 with IR compiler, Webpack bundling
- **Testing**: runtime state-sync suite in `tests/state/` (`run_all.sh [--browser]` — self-managed scratch server; see its README); no unit-test framework
- **Database**: MongoDB with replica set required; init demo data via `GET /init`

## Directory Structure

Everything lives under the root package `tools.confido`. Source trees use a
"dotted root" convention: a flat directory named after the common package prefix
absorbs it, and subdirectories below match the remaining package segments
(so `tools.confido/payloads/requests/` holds `tools.confido.payloads.requests`).

```
src/
├── commonMain/tools.confido/              # Shared models: question/, rooms/, users/,
│                                          #   payloads/, state/, spaces/, utils/, ...
├── jvmMain/
│   ├── tools.confido.application/         # Backend: routes/, sessions/, actions/, migrations/
│   ├── tools.confido.store/               # Persistence stores
│   └── tools.confido/                     # jvm actuals for shared packages
└── jsMain/
    ├── tools.confido.web/                 # Frontend: components/, hooks/, utils/, ext/
    └── tools.confido/                     # js actuals for shared packages
extensions/<name>/                         # Plugins with common/, js/, jvm/ subdirs
                                           #   (package tools.confido.extensions.<name>)
static/                                    # Checked-in web assets, served under /static
```

Source roots are configured as `src/<sourceSet>/` directly (no `kotlin/` level) —
see `kotlin.setSrcDirs` in build.gradle.kts.

Polymorphic `@Serializable` classes must carry an explicit class-level
`@SerialName` — without one the discriminator defaults to the FQN, which breaks
stored data when a class moves packages.

## Code Style Guidelines

### Imports
- Star imports allowed: `import kotlinx.serialization.*`
- Import aliases used: `import js.objects.unsafeJso as jso`
- Loose grouping by source (not strictly alphabetized)

### Naming Conventions
| Element | Convention | Example |
|---------|------------|---------|
| Classes/Interfaces | PascalCase | `Question`, `RoomContext` |
| Functions/Properties | camelCase | `withState()`, `groupPredVisible` |
| Enum values | SCREAMING_SNAKE_CASE | `QuestionState.DRAFT` |
| Files | PascalCase matching class | `Question.kt`, `Room.kt` |

### Type Annotations
Prefer inference; use explicit types for interfaces, serialization, and complex generics:
```kotlin
val room get() = globalState.rooms.values.firstOrNull { ref in it.questions }  // inferred
suspend fun <T> ApplicationCall.withRoom(body: suspend RoomContext.() -> T): T // explicit
```

### Error Handling
Use helper functions that throw `ResponseError` with HTTP status codes:
```kotlin
val room = rRef.deref() ?: notFound("No such room.")
if (!room.hasPermission(user, permission)) unauthorized("Missing permission.")
```
Available: `notFound()`, `unauthorized()`, `forbidden()`, `badRequest()`, `serviceUnavailable()`

Use `require()` in init blocks: `require(yesProb in 0.0..1.0)`

### Nullability
Use safe calls, elvis operator, and early returns:
```kotlin
val numPredictions get() = globalState.predictionCount[ref] ?: 0
fun userRole(user: User?): RoomRole? {
    if (user == null) return null
    return members.find { user eqid it.user }?.role
}
props.icon?.let { +it.create() }
```

### File Organization
Order: package → imports → top-level vals → interfaces → enums → data classes → main classes (companion objects last)

### Comments
- KDoc (`/** */`) for public APIs
- `// TODO:` for future work
- Inline comments for implementation notes

## Architecture Patterns

### React Components (jsMain)
```kotlin
external interface QuestionProps : Props {
    var openResolve: Boolean?
}
val Question = FC<QuestionProps> { props ->
    val (appState, stale) = use(AppStateContext)!!
    val roomCtx = use(RoomContext)!!
}
```
CSS with Emotion: `css { padding = Padding(6.px, 15.px) }`

### Ktor Routes (jvmMain)
Use `getST`, `postST`, `deleteST`, `webSocketST` for single-threaded dispatch:
```kotlin
fun roomRoutes(routing: Routing) = routing.apply {
    postST("/rooms/add") {
        call.withUser { /* create room */ }
        call.respond(HttpStatusCode.OK, room.id)
    }
}
```
Use `withRoom` and `withUser` context helpers for authorization.

### Serialization
```kotlin
@Serializable
data class Question(
    @SerialName("_id") override val id: String,
    val name: String,
)
```

### Extension System
Extensions in `extensions/<name>/` with `common/`, `js/`, `jvm/` subdirs. Registries auto-generated during build. Enable via `appConfig.enabledExtensionIds`.

## Configuration

Environment variables (see `config.example`):
- `CONFIDO_HTTP_HOST`, `CONFIDO_HTTP_PORT` - Server binding
- `CONFIDO_MONGODB_URL`, `CONFIDO_DB_NAME` - Database
- `CONFIDO_SMTP_HOST`, `CONFIDO_SMTP_PORT` - Email
- `CONFIDO_DEVMODE=1` - Development endpoints
- `CONFIDO_DEMO=1` - Demo mode

## Key Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| Ktor | 3.3.1 | HTTP server + client |
| mongodb-driver-kotlin-coroutine + bson-kotlinx | 5.11.1 | MongoDB driver + kotlinx-serialization codec |
| kotlinx-serialization | 1.9.0 | JSON/CBOR serialization |
| kotlin-wrappers | 2025.10.8 | React, Emotion, Router |
| PlotlyKt | 0.7.1.1 | Charting library |

## Common Patterns

### State Management
- Server maintains full state in memory, syncs to MongoDB via stores (`tools.confido.store`)
- Client receives censored state snapshots via topics on one multiplexed WebSocket
- Store mutations need no notification calls: topics are `View` compositions
  (`viewWS`/`flowWS`) that recompute when the state they read changes. Only *transient*
  (non-store) server state needs a `StateFlow` surface to drive topics (see
  `presentationRunStates` for the pattern)

### Authorization Flow
```kotlin
call.withRoom {
    assertPermission(RoomPermission.ROOM_OWNER, "Cannot edit.")
    serverState.roomManager.modifyEntity(room.ref) { it.copy(...) }
}
```

### Entities and Refs

Entities are the core data objects (Room, User, Question, etc.) stored in MongoDB. They implement the `Entity` interface and have a unique string `id`.

**Ref<T>** is a lightweight wrapper holding just the entity ID, used for references between entities:
```kotlin
@Serializable
data class Room(
    @SerialName("_id") override val id: String,
    val questions: List<Ref<Question>>,  // References to questions
    val members: List<RoomMembership>,
)
```

**Dereferencing** retrieves the actual entity from global state:
```kotlin
val room = roomRef.deref()           // Returns Room? (null if not found)
val room = roomRef.deref() ?: notFound("No such room.")
```

**Entity comparison** uses the `eqid` infix operator (compares by ID):
```kotlin
if (user eqid membership.user) { ... }  // Compare User to Ref<User>
members.find { user eqid it.user }      // Find by ID match
```

**Entity managers** (server-side) handle persistence and modification:
```kotlin
// Insert new entity (ID auto-generated if empty)
serverState.roomManager.insertEntity(room)

// Modify existing entity (immutable - uses copy())
serverState.roomManager.modifyEntity(room.ref) { it.copy(name = newName) }

// Delete entity
serverState.roomManager.deleteEntity(room.ref)
```

### Data Classes
- Immutable by default (use `copy()` for modifications)
- IDs stored as strings with `@SerialName("_id")` for MongoDB
- Use `entity.ref` to get a `Ref<T>` from an entity instance
