# AGENTS.md - Coding Agent Guidelines for Confido

## Project Overview

Confido is a **Kotlin Multiplatform** collaborative forecasting/prediction web application.

- **Backend**: Kotlin/JVM with Ktor 3.3.1, MongoDB via KMongo
- **Frontend**: Kotlin/JS with React (via kotlin-wrappers), Emotion for CSS-in-JS
- **Shared**: Common code in `src/commonMain/` for models, serialization, utilities

## Build & Run Commands

```bash
./gradlew build                              # Build JVM + JS targets
./gradlew run                                # Run backend at http://localhost:8080/
./gradlew jsBrowserDevelopmentRun --continuous  # Frontend hot reload at :8081
./gradlew clean                              # Clean build artifacts
```

- **JVM target**: Java 17, main class `tools.confido.application.ServerKt`
- **JS target**: ES2015 with IR compiler, Webpack bundling
- **Testing**: No testing framework configured
- **Database**: MongoDB with replica set required; init demo data via `GET /init`

## Directory Structure

```
src/
├── commonMain/kotlin/          # Shared models, utils, serialization
├── jvmMain/kotlin/             # Backend: routes/, sessions/, actions/
└── jsMain/kotlin/              # Frontend: components/, hooks/, utils/
extensions/<name>/              # Plugins with common/, js/, jvm/ subdirs
```

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
        TransientData.refreshAllWebsockets()
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
| KMongo | 4.7.2 | MongoDB driver |
| kotlinx-serialization | 1.9.0 | JSON/CBOR serialization |
| kotlin-wrappers | 2025.10.8 | React, Emotion, Router |
| PlotlyKt | 0.7.1.1 | Charting library |

## Common Patterns

### State Management
- Server maintains full state in memory, syncs to MongoDB via managers
- Client receives censored state snapshots via WebSocket subscriptions
- Use `TransientData.refreshAllWebsockets()` after mutations

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
