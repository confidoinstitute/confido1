# Persistence & State-Sync Redesign Proposal

Status: **living document** — distills the design conversation of 2026-08-03. Direction is
agreed; details are expected to evolve as implementation surfaces them. Sections record
intent and rationale, not commitments — update this doc when the code teaches us otherwise.

This proposal covers a ground-up redesign of two intertwined subsystems:

1. the persistence layer (today: `ServerState.kt`'s `EntityManager` hierarchy over KMongo), and
2. client–server state synchronization (today: the global `SentState` snapshot plus ad-hoc
   secondary websockets).

They are treated together because the central idea is that **the persistence layer's change
stream should drive client synchronization** — mutation → change event → topic invalidation,
with no manual notification calls anywhere.

---

## 1. Problems with the current design

Findings from a survey of the current code (file references as of this writing):

**Persistence** (`src/jvmMain/.../ServerState.kt`, ~850 lines):

- One `EntityManager` hierarchy pretends to span two genuinely different access patterns
  (fully-resident vs. queried-on-demand). The seams leak: `suspend get()` that is sometimes a
  map lookup and sometimes a DB round trip, the `Instant.DISTANT_FUTURE` sentinel choosing
  between cache and Mongo (`ServerState.kt:498`), the `is InMemoryEntityManager` type test in
  `derefNonBlocking` (`ServerState.kt:786`), and the `FIXME` on the base class itself
  (`ServerState.kt:299`).
- Secondary indexes are hand-wired callback maps; several `deleteMany` paths bypass the
  callback system and hand-patch caches owned by *other* managers (`ServerState.kt:434,520,601`).
- Cascade deletes are hand-rolled in route handlers and incomplete (deleting a question orphans
  its predictions and comments; `routes/Question.kt:181`).
- `withTransaction` (`ServerState.kt:770`) has no abort path, and the in-memory maps are
  mutated *inside* the transaction — a failed transaction would leave RAM and DB divergent.
  The replica-set requirement (the largest operational burden for small self-hosted
  deployments) buys crash-atomicity at best, imperfectly.
- `userPredManager` is a whole second manager shape (bare `EntityManager`, synthetic composite
  id, filter-based upsert) because "latest prediction per (question, user)" never was an entity
  collection — see §3.4.

**Sync** (`Server.kt:332`, `StateCensor.kt`, `routes/Common.kt`):

- Any of ~56 `refreshAllWebsockets()` call sites wakes **every** socket of **every** client.
  Each `/state` socket then rebuilds the full `SentState` from scratch — including walking
  every comment on the instance just to compute `referencedUsers` (`StateCensor.kt:25-47`) —
  and sends it unconditionally (no dedup on `/state`). Cost per mutation:
  O(whole state × connected clients), all on the single shared thread.
- Manual refresh calls are a convention that is easy to forget and impossible to scope.
- Secondary subscriptions (`getWS` / `useWebSocket`, 17 endpoints, 23 client call sites) are
  socket-per-subscription: a question page holds 4–5 concurrent sockets, a presenter grid
  ~24. This multiplies reconnect logic (three divergent implementations) and is the likely
  culprit for iOS Safari connection-limit misbehavior (unverified; no workaround exists in
  the repo).
- The same data class serves as Mongo document, in-memory object, and wire message, producing
  wire-only fields on entities (`Room._effectiveMembers` etc.) and a `beforeEntitySave`
  scrubber to strip them back out (`ServerState.kt:334`).

**What we keep** (explicit non-goals of the redesign):

- kotlinx.serialization end to end, including polymorphic, evolvable schemas.
- Immutable entities, `Ref<T>` + `eqid` semantics, non-suspending `deref()` in shared code.
- Kotlin Multiplatform with shared `commonMain` models. (A frontend-stack pivot was
  considered and rejected: every pain point is server-side, and the shared-model
  serialization is the architecture's best asset.)
- Single-writer concurrency. At the target scale (tens of users per workspace) this is a
  feature: it makes snapshot semantics and transactions trivially correct.

---

## 2. Design principles

1. **Honest types over transparent caching.** Resident and non-resident data get *different
   store shapes with different signatures*. "Can I deref this synchronously?" becomes a
   compile-time fact, not a runtime property. (The Kodein-DB-style transparent cache was
   considered and rejected: transparency is precisely what hides whether a call is a map read
   or a query.)
2. **Full residency is a contract, not a cache.** For resident stores, memory is the source of
   truth and the backend is a durability log read once at boot. This is what keeps
   `deref()` non-suspending and the backend interface tiny.
3. **Every intermediate state is valid.** Applies to transactions (emit after commit) and to
   migrations (tolerant readers) alike.
4. **Derived state is Flow algebra, not callbacks.** Indexes, counts, aggregates, topics, and
   censored payloads are all views over change streams. Snapshots for reads, deltas for
   maintenance (never "recompute view from snapshot on every emission").
5. **The wire is not the database.** Topic payloads are ephemeral and version-locked (the
   `/state` socket already force-reloads clients on bundle-hash mismatch, close code 4001), so
   the wire format is free to change; the persisted format is sacred and changes only via
   migration.
6. **Identity is granted, not leaked.** Clients receive `UserPresentation` values embedded in
   payloads, never joinable `Ref<User>` ids, except in admin/moderator contexts. See §5.3.

---

## 3. The store layer

### 3.1 Taxonomy

Three shapes cover all fourteen current managers:

| Shape | Contract | Backing | Examples |
|---|---|---|---|
| `Store<E>` (resident) | non-suspending `get`, `StateFlow` snapshots, `Ref.deref()` valid | full collection load at boot, write-through | rooms, questions, users, presentations, links, sessions |
| `Log<E>` / `Archive<E>` (non-resident) | suspend-only; `range()` queries + `tail()` subscription; **no** non-suspending deref, by construction | append-mostly with keyed edits; backend range queries | prediction history, group-pred history, **comments**, calibration training responses |
| `View<T>` (derived) | read-only; incremental fold over change streams | in-memory, or backend-materialized (§3.4) | counts, `questionRoom`, effective permissions, group aggregates, **latest-prediction-per-key** |

**Residency test** — an entity type is resident iff:
(a) it is a `Ref` target used in synchronous shared code,
(b) it is bounded by workspace size, not by activity × time, and
(c) it is needed for synchronous permission/censoring decisions.

**Partition-fetch, not pagination, is the Archive's core primitive.** A hot question holds on
the order of a hundred comments; loading a whole partition on demand is cheap, and the comment
topic is simply `all(question) + tail`. Time-ordered range queries exist only on
`TimeSeriesLog` — the prediction histories genuinely need them (chart windows, calibration,
CSV export); comments do not, and the interface should not imply they might.

Comments fail (b) and — after the censor redesign in §4.3 removes the `referencedUsers` walk —
no longer pass (c); they move to `Archive`. The costs are absorbed: `commentCount` becomes a
maintained fold (rebuilt by one aggregate at boot, like `totalPredictions` today), comment
authors ship inside the comment topic payload as presentations, and likes travel with their
comment's topic. `Ref<Comment>` loses synchronous deref; nothing on a synchronous path needs a
comment body, and the type system now enforces that.

*Amendment (2026-08-16): tier-2 residency — subscription-scoped hot partitions.* The client
eviction retired criterion (a) — no shared code derefs synchronously anymore — and the topic
architecture supplies a natural hotness signal: a subscription *is* the declaration that an
entity is being looked at. `ResidentPartitions` (store package) makes an archive partition
resident while pinned by at least one subscription (plus a grace period covering page
refreshes/reconnects): first pinner loads, the partition is maintained from the archive's
synchronous change dispatch (load race closed by buffering transforms and keyed replay —
idempotent against a fetch that already saw the change), and the entry drops when the last
pin releases and the grace expires. Memory is bounded by connected UI surfaces, not by
activity × time, so this is not §3.1 residency — cold access stays suspend-only on the
archive. Comments are the pilot (see the Phase 3 notes). The same shape is the intended
*future* path for loosening strict residency of workspace-structure entities (questions
partitioned by room) if instance age ever makes full residency heavy; the agreed API for
that day is a *split*, not a change: `suspend load()` anywhere, non-suspending reads only
through a pinned view — preserving both the "synchronous deref is a compile-time fact"
principle and the View-consistency theorem's non-suspending-transform hypothesis. Named
blockers, recorded so they aren't re-derived: the cross-room surfaces (global questions
topic, search-per-keystroke, dashboard, calibration) read all questions by design and would
need server-side endpoints first; the per-topic pin-set functions coincide with the §4.3
class-scope projections; `latestPredictions`' head is the actually-large resident state on
old instances and follows the same partition pattern. No near-term need — questions/rooms
grow with workspace structure, which is slow.

### 3.2 Core interfaces (sketch)

```kotlin
sealed interface Change<E : HasId> {
    data class Added(val entity: E) : Change<E>
    data class Updated(val old: E, val new: E) : Change<E>
    data class Removed(val entity: E) : Change<E>
}

interface Store<E : Entity> {
    val state: StateFlow<PersistentMap<String, E>>  // consistent snapshots (kotlinx.collections.immutable)
    val changes: Flow<Change<E>>                    // emitted strictly after backend commit
    fun get(id: String): E?                         // non-suspending; deref() routes here
}

interface Archive<E : HasId> {
    suspend fun all(partition: PartitionKey): List<E>  // the core primitive: fetch one partition
    fun tail(partition: PartitionKey): Flow<Change<E>> // snapshot-then-subscribe = the topic protocol
    // write path declares append policy, e.g. coalescing (§3.4)
}

interface TimeSeriesLog<E : HasId> : Archive<E> {
    suspend fun range(partition: PartitionKey, from: Instant?, to: Instant?): List<E>
    suspend fun at(partition: PartitionKey, ts: Instant): E?
}

interface Backend {                                  // the ~5 primitives actually used today
    suspend fun loadAll(collection: String): Flow<ByteArray>
    suspend fun commit(batch: WriteBatch)            // atomic: Mongo txn / SQLite txn
    suspend fun append(collection: String, doc: ByteArray)
    suspend fun rangeQuery(collection: String, q: RangeQuery): Flow<ByteArray>
}

suspend fun <R> transact(block: TxnScope.() -> R): R
// Order of operations: stage writes → backend.commit() → swap persistent maps → emit changes.
// A failed commit leaves RAM untouched (fixes the current divergence hazard).
```

**Atomic read-modify-write contract.** `transact` serializes against all other writers (there
is exactly one), so reads *inside* a transact block are guaranteed to see the latest committed
state, and it cannot change before the block commits — RMW atomicity
(`transact { val q = questions.get(id)!!; put(q.copy(...)) }`) falls out for free. Two rules
follow:

- Any read that feeds a write happens **inside** `transact`. Reads outside (via `state`
  snapshots or `get`) are consistent but may be stale by the time you act on them.
- No slow I/O inside `transact` — it holds the writer. The backend commit at the end is the
  only awaited I/O.

`transact` solves *server-side* atomicity only. Human-latency conflicts (two moderators
editing the same form for minutes) remain an API-level concern: entity-version
compare-and-swap, like today's `replaceEntity(compare = ...)` /
`ConcurrentModificationException`, implemented trivially as a compare inside a transact.

Notes:

- The heterogeneous registry (`Map<KClass<*>, Store<*>>`) needs one unchecked cast behind one
  lookup function — same as today's `managers` map; sound by construction.
- Secondary indexes become a small generic `Index<E, K>(keyOf: (E) -> K?)` maintained from
  `changes` — replaces every hand-wired callback map (`byEmail`, `byToken`, `byComment`, …).
- Referential structure is declared explicitly per store
  (`references(Question::class) { it.questions }`), giving a visible cascade graph and
  generic cascade deletes. (Auto-discovery via `SerialDescriptor` walking was considered;
  `Ref<T>`'s target type is erased in the descriptor, so explicit declaration is cleaner.)
- Boot = replaying `loadAll` through the same folds as live changes; no `isLoading` guard.
- Single-writer is stated once, in `transact`; the `mutationMutex` + single-thread-dispatcher
  duplication disappears.

### 3.3 Backends

Actual Mongo usage today is narrow: `findOneById`/`replaceOneById`/`insertOne`/`deleteOneById`,
full collection scans at boot, multi-document transactions, one `$group` count aggregate,
sorted `(question, user, ts)` range queries, `deleteMany` by predicate, and one
partial+collated unique index (user email). Everything fits the `Backend` interface above.

- **MongoBackend**: compatibility with existing deployments. Unique-email enforcement stays a
  backend concern (the existing index) plus a store-level `UniqueIndex` check.
- **SqliteBackend** (optional, later): one `(id TEXT PRIMARY KEY, data BLOB)` table per
  collection (kotlinx-serialized CBOR/JSON — polymorphic serialization unaffected), real
  columns + indexes for log tables. WAL mode + single writer gives genuinely atomic
  multi-entity commits. Removes the replica-set operational burden for small deployments and
  opens the Kotlin/Native door (SQLDelight native driver); Native is a side effect, not a goal.

### 3.4 Predictions: log + materialized view

`userPredHist` is the ground truth — an append-only log of prediction events, with the
existing coalescing rule ("collapse appends within `predictionCoalesceInterval` per key")
becoming a declared append policy of the log rather than 40 lines in `addPrediction`.

**"Latest prediction per (question, user)" is not a store — it is a materialized view of the
log**: conceptually `predictionLog.foldLatestPerKey()`, persisted (today's `userPred`
collection, reinterpreted) only so boot does not replay the full history. A materialized view
has an owner (the log), a definition (the fold), and a deterministic rebuild procedure — which
makes the "deleteMany then hand-patch other managers' maps" bug class unrepresentable.

Downstream views compose: group aggregate = fold over latest-per-key changes;
`predictorCount` = key count per question; histograms = per-question projection, censored per
room policy. "Has predicted" becomes a per-viewer boolean on the question topic payload
(§4.3), not a censor input.

The same head/past split resolves the old sentinel kludge: the *head* of the prediction data
is resident (bounded by users × questions), its *past* is archived — expressed in types, not
in a magic timestamp.

### 3.5 Versioned views and the transaction DAG (conversation of 2026-08-15; core implemented)

The server model sharpens into a DAG: **source nodes mutated only inside transactions,
everything downstream pure**. Two agreed extensions to the as-built View layer (§7 Phase 3),
motivated by the recompute-all cost of the flush pass:

- **Every source mutation is a transaction — including transient state.** `transact` bumps a
  global transaction serial after apply+emit; a transient-only transaction does the same but
  must skip the Mongo session entirely (the single-op fast path stops being an optimization
  and becomes a design requirement). Durability becomes a per-node policy: a `Store` is a
  versioned source cell whose commit path includes the backend, transient state
  (`presentationRunStates`, presenter-window counts, archive version counters) is the same
  cell without one, and a §3.4 materialized view is a derived node with a durable cache.
  The ad-hoc `MutableStateFlow` surfaces unify into one `VersionedCell` shape, and
  cross-kind atomicity falls out (e.g. the presentationStore-listener + run-state pair
  becomes one observable step). Memory-only vs. persisted becomes an implementation detail
  of the node, not of the consumers.
- **Views memoize with two counters** — `computedAt` (txid when the cache was last known
  valid) and `changedAt` (txid when the value last actually differed). `validate()` recurses:
  inputs unchanged since `computedAt` → stamp current without recomputing (input cutoff,
  a chain of Long compares); recomputed-but-equal → keep the old `changedAt` so downstream
  still cuts off (early cutoff — the Incremental/Adapton/Salsa discipline). Single-writer
  single-thread makes validation atomic and the serial a plain Long; the flush pass becomes
  *validate-all* — a synchronous, complete dirtiness check that preserves the strict frame
  consistency of recompute-all while skipping all unaffected work. Shared views then compute
  once per txid regardless of reader count, so the permission-class memoization stage
  reduces to a registry of shared per-(topic, class) views.
- **Partial recompute via keyed combinators, not changed-set lambdas.** For wide keyed views,
  a `mapValuesMemo`-style combinator re-runs the per-entry function only for entries whose
  identity changed (persistent-map structural sharing makes the sweep pointer-cheap) —
  O(changed entities) censoring instead of O(all). Which-inputs-changed information is
  deliberately *not* exposed to arbitrary transforms: branching on it by hand is the manual-
  refresh bug class one level up. The §4.3 discipline rule stands: true delta folds for
  aggregates, memoized recompute (now with cutoffs) for derivations; the transitional
  monolith gets no incrementalization investment — decomposition is its fix.
*As-built notes (core, same day):* `TxClock` is a plain monotone `Long`; `transact` bumps
once after apply+emit (covering store applies and synchronous fold dispatch), and the
out-of-transaction transient surfaces (`presentationRunStates`, presenter-window count,
millionaire game/timer state, calibration response counter) became `TransientCell`s that
bump on set — multiple bumps per logical transaction are deliberately fine (validation
needs monotonicity, not one-tick-per-transaction, and all bumps of one mutation happen in
one non-suspending span). `View.validate()` defaults to conservative "changed now" so
ad-hoc Views stay sound; the combinators memoize via a shared `MemoView` base
(`map`/typed `combineViews`/`flatMap` — the vararg wake-set combine just maxes its
inputs); `asView(StateFlow)` leaves detect changes identity-then-structurally, stamping
at first observation. The `viewWS` wrapper is itself a `MemoView` whose compute encodes
the payload (so equality cutoff deduplicates at the payload level) and maps
`ResponseError` to a `WSError` element — error states forgo cutoff and re-evaluate per
flush, preserving the gate-recovery semantics (verified live: closed ANSWERED gate →
error frame once, unrelated mutations don't re-send it, predicting unlocks data on the
same subscription). The sender's flush pass validates instead of recomputing:
`view.validate() <= sub.sentAt` skips a topic at the cost of a chain of Long compares.
*Amendment (2026-08-17): the first bullet's transient-transaction requirement is
implemented.* `transact` now separates the abstract transaction (writer lock + staged
scope) from the database transaction: the Mongo session opens lazily at flush time,
only when a staged op actually writes the database (`StagedWrite.needsDb`), so a
transient-only transaction never touches Mongo and the legacy `withTransaction`
wrapper is deleted. `TransientCell` writes are transactional: `set`/`update` stage
into the ambient transaction (apply+emit strictly after commit) or apply immediately
as their own tick when none is open. The ambient scope is exposed to *non-suspending*
code through a `ThreadLocal` bridged with `asContextElement` (user-proposed, refining
"only one transaction is globally open" with the fact that *open* is not *ours* during
the transaction's suspensions): the thread-local is set exactly while the transaction
coroutine executes and restored to null across its suspension points, so interleaved
coroutines — timer callbacks, other handlers — correctly see no transaction, and the
post-commit apply phase (change dispatch, `ResidentPartitions`) gets immediate
semantics automatically, with no separate listener-context API and no discipline rule.
`TxnScope` refuses staging after flush, so a coroutine leaked out of a transact block
fails loudly instead of losing writes.
*Amendment (same day): the keyed write set.* Write combinators now compose through a
per-key net-effect slot in the transaction (`TxnScope.slot`): store writes key on
(store, id), a cell is its own key. `modify`/`update` bodies run eagerly, exactly
once, at the call site, against the entity's/cell's *effective* value —
pending-else-committed — so consecutive RMWs of one target compose and return exact
values, `insert`-then-`modify` works, and the lost-update class becomes
unrepresentable (no deferred-lambda contract needed: eager evaluation against a keyed
write set subsumes function staging). Plain reads (`get`/`asMap`/`state`/`value`)
deliberately keep the committed-snapshot contract — the write-set read stays private
to the write combinators, so it can never be paired with a stale derived read
(a public `getStaged` opt-in remains possible if a site ever demands it; the exact
`modify` return value covers cross-entity RMW through ordinary dataflow meanwhile).
Flush and change emission are the transaction's NET effect per key — one `Updated`
spanning committed-old to final-new, an insert cancelled by a delete emits nothing —
i.e. the §3.5 keyed last-writer delta algebra, one level down; `/questions/multidelete`
reverted to its naive per-question loop as the living proof (its suite test now
exercises composition end-to-end). The
presentationStore run-state listener moved from `onChange` to `beforeEntityDelete`,
realizing the bullet's cross-kind-atomicity example: the store delete and the cell
clear are one transaction, so a failed commit no longer leaves the run state cleared
against a surviving presentation.

- **Transaction-aligned collection deltas (future direction, opt-in per topic).** For
  collection-shaped views, one stream type serves store change surface, archive tail, and
  wire format alike: `CollectionUpdate<E> = Full(entities) | Incremental(added, changed,
  removed)`, transaction-tagged, with changed entities carried in full — **no deep/field
  diffs** (whole-entity granularity keeps delta composition a keyed last-writer map:
  associative, coalescable; field patches would forfeit that). This is the third appearance
  of "snapshot for reads, deltas for maintenance" (§2.4): the store `Change<E>` batch of one
  transaction *is* an Incremental; §3.1's `all + tail` *is* Full-then-Incrementals (the
  deferred comment `tail` is the natural first customer, replacing refetch-per-version-bump);
  the wire gains O(changed) updates for large collections. Transform algebra: per-entity
  maps (censoring) thread deltas untouched; filters synthesize Added/Removed on visibility
  transitions (the `Updated(old,new)` shape already carries what they need); aggregations
  stay folds. The outbox's later-wins coalescing generalizes to delta composition (Full
  resets the slot); the client materializes into the typed cache inside the same atomic
  frame apply, so consumers still see plain maps. Duality with versioned views: validation
  answers *whether* since txid N, the delta answers *what* — one node can expose both, and
  deltas make `mapValuesMemo` exact without pointer sweeps. Candidates: comment tails,
  prediction histories, admin user directory, per-room question payloads. Non-candidates:
  small payloads, fold outputs. Reconnect = fresh Full (a last-seen-txid catch-up ring is a
  possible later refinement the txid tags leave room for).
  *(Decision of 2026-08-16: deferred indefinitely — after the counts split and the
  comment-partition residency, no topic pays O(large collection) wire on a hot path;
  every collection payload is bounded-small and changes infrequently, and comments'
  actual multiplier (per-subscriber fetches) was server-side, fixed by residency. The
  `mapValuesMemo` half is likewise deferred: a keyed memo sweep and delta emission fall
  out of one combinator if ever needed. This bullet stays as the design for that day.)*

---

## 4. Sync: one socket, topics, censor pipeline

### 4.1 Multiplexed websocket

One websocket per client. Frames:

```
client → server:  { "sub": [<topics>], "unsub": [<topics>] }   // batched per client tick
server → client:  { "updates": [ { "topic": "<topic>", "data": <payload> }, … ] }
                  // errors are payloads too ({"type":"err",…} envelope on the topic)
```

Frames are **multi-update and atomic**: the client applies all of a frame's topic
payloads to its caches before notifying any listener, so updates the server groups into
one frame can never be observed half-applied. Subscriptions issued in the same client
tick travel as one command, and the server answers with the initial payloads of the
whole set in one consistent frame (see the Phase 3 outbox notes in §7).

- Server side: today's `getWS` bodies become topic handlers; the existing per-socket
  last-payload dedup (`routes/Common.kt:92`) carries over per topic.
- Client side: `useWebSocket(url)` becomes `useSubscription<T>(topic)` with the same signature
  shape — all 23 call sites convert mechanically. One reconnect implementation with backoff
  replaces the current three divergent ones.
- Version locking (bundle-hash close 4001) and session close (3000) move to the connection
  level, unchanged in behavior.
- iOS Safari's suspected connection-limit issue is mooted: one connection total.

### 4.2 Topics from change streams

A topic invalidation is a Flow derivation, e.g.
`store.changes.mapNotNull { topicOf(it) }.conflate()` — any change to a room-owned entity maps
to `RoomTopic(roomRef)`. This **replaces all ~56 manual `refreshAllWebsockets()` calls with
nothing**: correctness by construction, scoping for free, coalescing under bursts via
conflation. *(As-built amendment: payloads recompute from resident snapshots and the change
side only carries wake signals — see the Phase 3 `View` notes in §7.)* Timer-driven refreshes (schedule sweep, millionaire timer) become explicit topic
invalidations.

`SentState` splits into medium-grained topics (deliberately not per-entity — churn without
payoff at this scale):

| Topic | Contents (payload types, not entities — §5) | Personal? |
|---|---|---|
| `self` | session, own user, `myPasswordIsSet`, presenter state | per-user |
| `rooms` | room directory: id, name, icon, role — enough for the room list | per-permission-class |
| `room/<id>` | configuration, member presentations, question refs/order | per-permission-class |
| `room/<id>/questions` | censored question payloads for the room | per-permission-class |
| `question/<id>/mine` | own prediction, `hasPredicted` | per-user |
| `question/<id>/group` | group prediction / histogram (existing secondary channels, retopiced) | per-permission-class |
| `question/<id>/comments`, `room/<id>/comments` | `range + tail` over the comment archive | per-permission-class |
| `admin/users` | full user directory | admin/moderator only |
| extension topics | existing `.ws` endpoints, retopiced | varies |

### 4.3 Censor pipeline

```
store.changes ──▶ topic invalidation (conflated)
      ▼
uncensored topic view            computed ONCE per topic, shared by all subscribers
      ▼
censor: combine(view, viewerPermClass)    memoized per (topic, permission class)
      ▼
per-connection: personal topics merged in; distinctUntilChanged; send
```

Key observation: censoring is almost never a function of the *user*, but of the user's
**permission class** (role in room + a few booleans). Thirty forecasters with identical
permissions share one censored payload, computed once. Only genuinely personal slices
(`self`, `question/<id>/mine`) are per-user, and they are tiny.

Cost model change: from O(whole state × connected clients) per mutation to
O(changed topics × distinct permission classes).

The `StateCensor` `referencedUsers` walk disappears: comment authors are embedded in comment
payloads at censor time (§5.3), so the global snapshot no longer needs to pre-declare users.

Session-scoped oddities (`presenterInfo`, presenter-window tracking) live in the
per-connection stage, where the session is naturally in scope.

**Discipline rule** (worth enforcing in review): views are maintained by incremental folds
over `changes`; `combine`+recompute-from-snapshot is reserved for cheap/rare derivations.
Otherwise the full-recompute cost model sneaks back in with prettier syntax.

---

## 5. Entity modeling and wire payloads

### 5.1 Semantic components for wide entities

Room and Question (the wide, churning entities) are decomposed into **semantic components** —
not by wire policy (which churns) but by domain boundaries that three concerns align on:

- *change rate* (configuration changes rarely; membership on every join),
- *visibility class* (configuration is member-visible; invite links moderator-only),
- *topic granularity* (components are the natural invalidation units).

Sketch (field-by-field assignment is an open modeling question, §8):

```kotlin
@Serializable data class QuestionProperties(  // name, description, answerSpace, schedule, tags, display prefs
    @SerialName("_id") val id: String, val name: String, /* … */)
@Serializable data class QuestionResolution(  // open/resolved/annulled, resolution value
    /* conveniently: also the subtractive-censoring cluster */)

@Serializable data class Question(
    val props: QuestionProperties,
    val resolution: QuestionResolution,
    val extensionData: ExtensionData,          // the in-house precedent for this pattern
) : QuestionShared by props                    // delegation: reads compile unchanged

fun Question.mapProps(f: (QuestionProperties) -> QuestionProperties) = copy(props = f(props))
```

Room decomposes into configuration, membership, invite links, and the question relation.
Membership may later graduate to its own store (shrinking the room topic's invalidation
footprint); components-as-subdocuments within one persisted document is the right first stop —
finer storage granularity would sacrifice trivial single-document atomicity for nothing at
this scale. Topics can be finer than storage.

Persisting the nested component structure is *not* persisting an implementation detail —
components are the domain model, and their boundaries are stable (a UserPresentation-style
change is a censor-layer change, touching no entity structure). Residual fields that fit no
component stay in the entity root; a residual holding only core identity churns slowly by
construction.

Rejected alternatives for the variants problem, for the record: inheritance base classes
(constructor pass-through re-creates the duplication for wide classes), a full DTO per entity
(unpayable churn tax on wide entities), a format-agnostic flattening serializer
("recording encoder", ~250 lines — feasible but clever-fragile; unnecessary once nesting is
semantic), and a JsonElement structural bridge (superseded: with composition, variant interop
is passing a reference).

### 5.2 Wire payloads

Payload types are thin shells over components plus per-viewer extras:

```kotlin
@Serializable data class QuestionPayload(
    val props: QuestionProperties,            // shared component, by reference
    val resolution: QuestionResolution?,      // null unless visible to this perm class
    val hasPredicted: Boolean,                // per-viewer
) : QuestionShared by props
```

- Adding a field to a component: one line, flows to every consumer automatically.
- Structural differences (fields that must never reach the wire) live outside shared
  components — fail-closed by construction, no inventory tests needed.
- Nesting on the wire is free (ephemeral, version-locked); nesting in the DB is wanted
  (semantic). No custom serializers anywhere.
- `Room`'s wire-only computed fields (`_effectiveMembers` etc.) and the `beforeEntitySave`
  scrubber are deleted; computed data lives only in payloads.

### 5.3 UserPresentation as a security boundary

Clients receive **embedded `UserPresentation` values, never joinable `Ref<User>` ids** —
except in admin/moderator contexts (`admin/users` topic). Rationale: stable ids don't just
reveal identity, they make it *joinable* across contexts (comments × histogram bins ×
member lists). Presentations invert the default: linkage is granted per context by the censor
stage.

Consequences and requirements:

- The `users` map leaves the ordinary member's client state entirely; frontend code stops
  expecting a user collection outside admin surfaces.
- Where the UI needs stable-but-unlinkable identity ("same author within this thread"),
  the censor stage generates deterministic per-context pseudonymous keys
  (`hash(userId, contextId)`-style).
- Per-viewer facts ("you liked this", "this is your prediction") arrive as booleans on
  payloads; the client-side `eqid`-against-session habit dies with the users map.
- `CommentPresentation` is the existing precedent; this generalizes it.
- This type is the one place where a dedicated presentation type is mandatory however much
  copying it costs — here the divergence is the point.

### 5.4 Extension data = dynamically registered components

Once semantic components exist, `extensionData` stops being a special mechanism: it is the
same pattern with registration at runtime instead of declaration in source. Unifying them:

- **Wire policy per key, one declaration point.** Each `ExtensionDataKey` declares its persist
  serializer plus an optional wire policy, in three tiers that collapse into one mechanism:
  *absent* → server-only (the default: **fail-closed**, a deliberate change from today's
  send-everything-then-censor-hooks — requires a one-time audit of which keys client code
  reads); *canned visibility* → identity projection gated by permission class; *typed
  projection* → `project(value, permClass) -> W?` with `W`'s own serializer, for the rare key
  whose wire shape differs from its stored shape. Type-wise: `ExtensionDataKey<V, W = V>` with
  identity default, so simple extensions never see the second parameter. (Considered and
  folded in: a boolean transfer attribute is the canned tier; fully separate wire/persist
  data bags are the projection tier with needless duplication.) Per-permission-class
  memoization keeps working since policies are functions of the permission class.
- **Per-user and computed data use topics, not projection.** Extensions needing per-user data
  (e.g. millionaire `my_score`) or computed aggregates (scoreboards) use extension-owned
  topics, as they already do via their `.ws` channels; the projection escape hatch is only for
  stored data whose wire form differs.
- **Topics**: an extension component invalidates its owning entity's topic like any other
  component; extension-owned topics (`room/<id>/ext/<extId>/...`) replace the existing ad-hoc
  extension websockets in the Phase 1 multiplexing.
- **Persistence**: the stored format — a flat name-keyed map
  (`{"room_group.is_group": true}`) — is already right and stays unchanged; no migration.
  Two small hardenings: (1) **unknown-key tolerance** — the current serializer throws on
  unregistered keys when encoding (`Extensions.kt:62`) and its decode loop does not handle
  `UNKNOWN_NAME`, so data written by a later-disabled extension fails to round-trip; preserve
  unregistered keys as raw values (`JsonElement`/`BsonValue`) and re-emit them instead.
  (2) **enforced key prefixes** — registration knows the extension id, so the
  `<extId>.`-prefix collision convention can become a guarantee.

---

## 6. Migration safety

The component splits rewrite most rooms/questions documents. Layered defenses, any one of
which alone prevents an unusable instance (migrations already run at boot before serving, so
crash-midway is the only mechanical threat; per-document updates are atomic; data volume is
thousands of documents, so "too expensive at scale" techniques are free):

1. **Copy–transform–verify–promote envelope** in `MigrationRunner` *(implemented in P0)*:
   migrations declare the collections they modify and receive *working copies*
   (`<name>_mig_<id>`) via a `MigrationContext` — they cannot touch live collections at all
   (undeclared access throws). The transform and the verify pass both run against the
   copies; only then does a two-phase-ledgered promotion (`promoting` → `done`) `$out` each
   verified copy over its live collection (atomic per collection, preserves live indexes).
   Properties: **live data is never modified until the transform is verified**; working
   copies are disposable (a leftover from a crash or failure is dropped and redone — on
   failure it is deliberately kept for inspecting the bad transform); a crash mid-promotion
   is completed on the next boot from the already-verified copies, so **migrations need not
   be idempotent**.
   *(History: an earlier P0 iteration used snapshot-then-restore-in-place, but a restore
   consumed automatically by the runner makes leftover snapshots authoritative — a lost or
   damaged ledger next to a stale snapshot would silently roll live data back in time. The
   copy-aside design inverts that: leftover artifacts are disposable, and a lost ledger
   degrades to a re-transform of current data, gated by the verify pass. The originally
   planned Mongo-transaction envelope was likewise rejected: session-threading through raw
   `Document` ops is invasive and silently void when forgotten. Under a future SQLite
   backend, a real transaction is the native default anyway.)*
2. **Verify pass** *(implemented in P0)*: every working copy is strictly re-decoded with the
   real entity serializers (`serverState.collectionVerifiers()`) *before* promotion; failure
   aborts startup with live data untouched. This targets the actually-likelier failure: a
   transform that completes successfully but is subtly wrong. Additionally, the
   pre-promotion state is kept as `<name>_pre_<id>` for **manual** rollback/forensics —
   written by the runner but never read by it.
3. **Tolerant reader** for the component splits specifically: serializers accept both flat
   legacy and nested component shapes during the transition; "migration" = boot, load
   everything (which the server does anyway), rewrite from memory; every intermediate state
   boots and serves. Drop the tolerant path a release later.
4. **Per-component sequencing**: each migration touches one field cluster of one entity type;
   membership extraction goes first and doubles as the rehearsal.

---

## 7. Roadmap

Phases are separable; each leaves the system working. Old and new machinery bridge via
`changes.collect { notifyEntityUpdated(it) }`-style adapters so `StateCensor` and friends keep
working untouched mid-transition.

- **Phase 0 — quick wins & groundwork** (independent of everything):
  - Add `getWS`-style last-payload dedup to `/state`.
  - Stop recomputing `referencedUsers` from all comments per censor (precompute incrementally).
  - `MigrationRunner` hardening: snapshot-restore envelope + verify pass (§6).
- **Phase 1 — websocket multiplexing** (§4.1) *(implemented)*: mechanical, net-negative LOC,
  kills the iOS question, prerequisite for topics. Topics initially = the existing 17
  endpoints + one `/state` topic carrying today's `SentState`. Implementation notes:
  handlers kept their bodies verbatim via a synthetic `TopicCall` (an `ApplicationCall`
  whose parameters come from the topic string); the two ad-hoc sockets became *stream
  topics* (`streamWS` — a per-subscription coroutine with `emit`), which covers both
  self-timed pushes (invite shortlink rotation) and presence semantics (presenter track,
  side effects on subscribe/cancel). Testing surfaced one design fact worth recording:
  `SentState.session` contains the session expiry, which every authenticated HTTP request
  renews — so the acting user's own `/state` re-pushes on each of their requests despite
  dedup. Argues for splitting session/self into its own small topic early in Phase 3.
  **Serialization lesson (hard-won):** kotlinx's two decoder families differ — streaming
  decoders read element values sequentially and ignore the index argument of
  `decodeSerializableElement`, while tree decoders (`decodeFromJsonElement`) resolve
  members by the descriptor name at that index. Switching the transport to
  JsonElement-embedded payloads exposed a latent hardcoded-index bug in the hand-written
  `ExtensionDataSerializer` (fixed properly — the index now passes through). The client
  decodes topic payloads via `decodeFromString(element.toString())` as a conservative
  choice: the streaming path is what every payload historically exercised, and
  hand-written serializers are demonstrably less tested under tree decoding.
  *(Debugging post-mortem: a presenter-view crash was initially misattributed to a
  JS-specific tree-decoder fault; the real cause was a pre-existing React state-timing
  bug in `PresenterDialog` — the controls component was selected from `props.view` while
  receiving the one-commit-stale `draft` state, so e.g. `GroupPredPC` rendered with
  `EmptyPV` on the first offer after mount. Fixed by deriving both from `draft`. The
  build-mode correlation that suggested a compiler/devserver issue was actually
  presenter-window state: with a window already open, `offer()` skips the dialog
  entirely.)*
- **Phase 2 — store core** *(implemented in full: core + predictions + comments, then the
  mechanical conversion of all remaining resident managers — the legacy `EntityManager`
  hierarchy is deleted; ServerState.kt shrank from ~850 to ~240 lines and holds only the
  facade properties, group-aggregation helpers, the mutex/transaction primitives, and
  transient state)*:
  `Store`/`Archive`/`View` + `transact`. Proven on the two ugliest customers — the
  prediction log + latest-per-key materialized view (§3.4) and comments-as-Archive (§3.1) —
  plus `loginLinkStore` as the resident-`Store`/`UniqueIndex` pilot. Implementation
  decisions that amend earlier sketches:
  - **No separate `Backend` interface yet** (deliberate descope): store implementations in
    `tools.confido.store` use KMongo directly, but all Mongo touchpoints are confined to
    that package, so the §3.3 backend split can be introduced when SQLite actually lands.
    Data formats are byte-identical to the old managers' (verified against live data —
    including the `___type` discriminator KMongo emits for sealed classes like Comment).
  - **Change delivery is synchronous listener dispatch**, not a Flow: the server is
    single-threaded and route handlers read derived views immediately after a mutation; an
    asynchronously collected fold would be one step stale there. The async
    `changes`/`tail()` Flow surface gets added in Phase 3 for change-driven topics.
  - **`transact` bridges over legacy `withTransaction`**: store writes stage themselves
    (via an ambient-coroutine-context `TxnScope`), flush inside the shared Mongo session,
    and apply to RAM strictly after commit — so a failed commit leaves store RAM untouched,
    while unconverted legacy manager ops keep their existing semantics inside the same
    transaction. Nested `transact` joins the ambient one; a `transact` under a legacy
    `withTransaction` fails loudly (those call sites were converted).
  - **The coalescing append is one staged op** that does its targeted lookups at flush
    time inside the transaction (the "newest remaining log entry" needed by the derivative
    group coalesce is not derivable from resident views — it may be the user's own older
    entry). Reads there are deliberately sessionless, as before: under a single writer
    they see pre-transaction committed state, which is what the policy is defined against.
  - **Likes folded onto comment documents** (`likes: List<Ref<User>>`), CommentLike entity
    and collection retired via migration `003_fold_comment_likes` — the first real
    exercise of the §6 envelope, incl. a verified failure path (bad document → verify
    aborts boot, live data untouched, working copies kept). Rationale: resident likes
    under non-resident comments re-created the residency problem one level down, and the
    `byUser` index's only live consumer was user-deletion cleanup (now an `updateMany`
    `$pull`); `getCommentsILike` was dead code. A dedicated N:M concept is deferred until
    a second use case exists.
  - **Cascades as `beforeEntityDelete` hooks** on the still-legacy question/room managers
    (question → predictions + comments, room → room comments): fixes the historical
    orphaning. The declarative `references()` graph of §3.2 becomes worthwhile once the
    owning entities convert.
  - **EntityManager hook roles map onto the store layer** (they are kept, not dropped —
    `pseudonyms` and `room_groups` register them from `serverInit()`): after-commit
    notifications = `ChangeSource.onChange` (strictly better: legacy callbacks fired with
    RAM already mutated *inside* the transaction); before-delete cascade/veto =
    `MongoStore.beforeEntityDelete`, run inside the deleting `transact` so cascade writes
    join the same transaction; before-save transform/validation =
    `MongoStore.beforeEntitySave`, folded before staging, where a throwing validator now
    aborts the whole transaction with RAM untouched. The one consumer meant to die is the
    core `Room` wire-field scrubber, which §5.2 deletes along with the wire-only fields.
  - Comments' `all(partition)` + folds (`commentCount`, `commentAuthors` via one boot
    aggregate each) replaced residency; `range + tail` narrowing per §3.1 stands, with
    `tail` deferred to Phase 3. The user-deletion scan needed one extra archive primitive:
    `byAuthor(user)` (indexed cross-partition query).
  - Incidental finds: `passwordResetLinkManager` was never registered (reset links did not
    survive restarts — fixed first, standalone); `userPredManager` was registered twice;
    `GET $questionUrl/updates` + `DistributionUpdate` appear to have no client callers
    (ported anyway — candidate for removal); `Comment.key()` and `commentList` were dead.
  - **Conversion round details** (the "twelve boring managers", done as follow-up commits):
    all resident stores are `MongoStore<E>` objects in `tools.confido.store` (rooms with
    the `questionRoom` fold + wire-field scrubber, questions, users with `byEmail`
    UniqueIndex + the email-index repair, sessions as `ExpiringMongoStore`, the three token
    link stores, presentations). Store registration order in `main()` is explicit boot
    order (base entities before the stores whose boot folds dereference them). The old
    `replaceEntity` surface turned out to have no compare-CAS users — only plain replace
    (→ `modify`) and one upsert (→ `put`, session tracking). All legacy `withTransaction`
    call sites became `transact` (store ops inside a legacy transaction throw by design).
    Every mutation now runs in a Mongo transaction even when single-op — measured as
    negligible at target scale; a single-op fast path is a possible later optimization.
- **Phase 3 — topics & censor pipeline** (§4.2–4.3): change-stream-driven invalidation
  (delete the 56 manual refresh calls), `SentState` decomposed into the topic table,
  permission-class memoization, UserPresentation rollout + frontend weaning off the users map.
  *(In progress — the core topic machinery landed first, deliberately exploratory and
  piece-by-piece. Decisions so far:)*
  - **`FlowTopic` replaces `StreamTopic`** (`routes/Common.kt`): one topic shape backed by
    `(WSBody) -> Flow<JsonElement>`; both former stream topics converted (shortlink rotator
    = timer flow; presenter track = presence via collection lifetime, cleanup in `finally`).
    Whether a topic's flow is *shared* is the lambda's choice — returning a hot shared flow
    and building a cold per-subscriber one have the same type — so later memoization is a
    combinator, not a new topic kind. The connection loop applies per-subscription
    `distinctUntilChanged().conflate()`; `conflate()` runs the upstream in its own producer
    coroutine, so computation is emitter-driven even while a client's send blocks. `shareIn`
    is deliberately *not* used at this level: per-subscriber upstream lifecycle is
    load-bearing for presence semantics (unsubscribe must cancel into the flow's `finally`).
  - **Payloads recompute from resident snapshots; change signals only say *when*** (amends
    this section's original sketch, which derived payloads from `store.changes`). This
    dissolves the initial-value problem — first evaluation and re-evaluation are the same
    code path against current state — and avoids expressing each computation twice
    (snapshot init + incremental fold). Archives (comments) stay the exception with their
    inherent `all(partition) + tail` shape.
  - **`View<T>`** (`store/View.kt`) — §3.1's View shape formalized: a cheap non-suspending
    always-current `value` plus a `changes` wake flow. The consistency argument: all store
    mutation happens in non-suspending apply/fold code on the single server thread, so any
    set of `value` reads without an intervening suspension observes a transactionally
    consistent snapshot. `combineViews` therefore re-reads all inputs at computation time
    instead of caching per-source values — composed views are immune to the classic
    `Flow.combine` glitch (fresh A with a stale cached B after a multi-store transaction).
    Transforms being non-suspending is the hypothesis of that argument and is enforced by
    the signatures. Combinators: `map` (lazy — runs on read, not on change), `at` (keyed
    cell; the "family of keyed flows" as derivation — whole-map wake + O(1) re-read,
    deduped at the flow boundary), `flatMap` (reactive ref-following), `asFlow` (conflated
    recompute + `distinctUntilChanged`). Derived views keep incremental folds as their
    implementation and surface as `StateFlow`s (`latestPredictions.state`,
    `predictionData.groupPredState`, `roomStore.questionRoomState` so far) — folds preserve
    read-your-writes for route handlers, snapshots serve subscribers.
  - **`viewWS`** registers a topic as a View composition, so what the payload reads and
    what recomputes it are the same declaration. `ResponseError` is caught per computation:
    a gate failure is a `WSError` frame and the subscription stays live, recovering when
    state changes — the ANSWERED-visibility unlock (subscribe while gated → predict → data
    flows) depends on exactly this. Pilot: the question `group_pred` topic, with
    `assertGroupPredictionAccess` inlined as gates over the combined values; verified live
    including the gate transition and question-delete propagation. *(Rejected on the way:
    a `derivedWS(path, vararg sources) { body }` trigger-list form — behaviorally
    identical, but the source list was a manual restatement of what the body reads, i.e.
    the manual-refresh bug class in miniature.)*
  - **All topics converted; the manual-refresh machinery is deleted.** The remaining
    resident-backed topics (histogram, room group_pred, presentation topics) became View
    compositions; comments became the gate+fetch shape (View gate + suspending
    `all(partition)` fetch, woken by a per-partition version counter on the archive —
    `all + tail` with refetch standing in for `tail`); `/state` itself is a transitional
    monolithic View over everything it reads (with `SentState.session.expiryTime` pinned
    at censor time so session renewals dedup away — the acting user no longer re-receives
    the snapshot per request); the five extension topics converted with bodies verbatim
    via gate+fetch (millionaire's transient game/timer state and a version counter on
    calibration responses gained StateFlow surfaces); the two client-dead topics
    (`group_pred_text.ws`, millionaire `my_score.ws`) were deleted. With nothing left
    listening, `refreshAllWebsockets`/`refreshSessionWebsockets` and all **63** call
    sites, `globalWebsocketRefresh`, `runRefreshable`, and `PullTopic`/`topicWS` are
    gone; `FlowTopic` is the sole topic shape and `TransientData` reduced to the
    presenter-window count. The per-tick session-expiry check became a per-connection
    watcher on the session cell (close 3000 on logout/expiry). Cost model note: the
    per-question `mine`/`hasPredicted` split of the §4.2 table is superseded for now by
    per-viewer inputs inside topic gates (own-prediction cells); revisit granularity with
    the permission-class memoization stage.
  - **Wire groundwork for the decomposition: multi-update frames + per-connection
    outbox with flush-time recomputation (strict cross-topic atomicity).** Frames carry
    lists of topic updates and the client applies them atomically (all topic caches
    written before any listener runs). Server side, the invalidation/recomputation
    split is extended to the sender: wake signals carry no data — they only schedule a
    flush — and at flush the sender recomputes *every* subscribed View-backed topic in
    one non-suspending pass, so all payloads of a frame observe the same transactional
    snapshot by the View consistency contract, no matter how raggedly the async wake
    signals arrived. This makes frames strictly consistent *and complete* (recompute-all
    means a frame can't miss a co-changed topic), with guaranteed convergence under
    load: no idle-waiting — each flush is one bounded pass against then-current state,
    frames pace themselves to send throughput, and bursts coalesce in the conflated
    flush signal (no timed debounce needed or wanted). A per-topic diff against the
    last payload actually sent keeps wire traffic proportional to real changes even
    though computation is recompute-all — that O(subscribed topics)-per-poke cost is
    the deliberate v0 tradeoff, narrowed later by permission-class memoization (share
    the computation across same-class connections) and, only if profiling demands,
    synchronous dirty-sets for O(changed). Topic shapes split accordingly
    (`routes/Common.kt`): `ViewTopic` exposes the composed `View` so the connection
    loop can read `value` at flush time; `FlowTopic` remains for suspending fetches
    (comment archives) and self-timed streams — their payloads are staged at emission
    time and ride in the same frames but sit outside the consistency pass. The outbox
    also subsumes the per-subscription `conflate()`/`distinctUntilChanged` (staging
    never suspends, so a slow client skips intermediate payloads instead of
    back-pressuring producers). Client side: sub/unsub commands coalesce per tick —
    a batch of View-topic subscriptions gets its initial payloads in one consistent
    frame, guaranteed by the same flush pass (killing page-load skew between a page's
    topics), and a resubscribe cancels a pending unsubscribe outright, so cache
    entries survive route-transition remounts instead of being dropped and refetched.
  - **Decomposition direction (agreed, discussion of 2026-08-15):** no synthetic
    client-side merged `SentState`. `MultiplexWS`'s refcounted topic registry grows a
    typed decoded-payload cache; `ClientState` becomes a facade over that cache (peeled
    fields read topic caches, the rest reads the shrinking monolith), and components
    drive subscriptions via `useSubscription(topic)` with dedup making redundant
    subscriptions free. Peel full-stack, field by field.
    Client-deref audit result: ~35 call sites in three buckets — room-scoped question
    joins (dissolve into the `room/<id>/questions` topic payload, which ships the join),
    user derefs (die with UserPresentation), and a small tail (presenter views,
    calibration, dashboard) converted per-surface, with the `ClientImmediateDerefEntity`
    marker interface as the compile-time ratchet: peel a bucket, remove the entity from
    the marker, fix what turns red. Endpoint: no global entity maps on the client.
  - **Typed cache + `self` peeled (first slice out of the monolith).** `MultiplexWS`
    decodes each frame once per topic into a cached decoded value (late subscribers get
    it synchronously; the atomic two-phase frame apply covers the decoded cache too);
    `useSubscription` rides on it. `SentState` lost `session`/`myPredictions`/
    `myPasswordIsSet`/`presenterWindowActive` (and its session-reading permission
    helpers) to the new `SelfState` payload of `/state/self` — session cell (which also
    carries `presenterInfo` updates), own predictions, password flag and
    presenter-window transient are its wake set, so per-user churn no longer touches
    the big snapshot, whose payload is now purely a function of the viewer's permission
    class (its combine keeps `latestPredictions` only because the censor's ANSWERED
    comment-visibility gate reads the viewer's own prediction; the `expiryTime` pinning
    moved to `self`). Client side, `AppStateContext` now carries the `ClientState`
    facade (monolith + self slice; permission helpers moved here, `deref()` unchanged)
    assembled in the provider's frame listeners — the global is reassigned before React
    re-renders, and the two root subscriptions issue in one tick so their initial
    payloads arrive in one consistent frame. Next slice: `rooms`/`room/<id>` as the
    component-scoped pilot.
  - **Versioned views implemented (§3.5 core)**: the flush pass validates instead of
    recomputing — unaffected topics cost Long compares, per-user churn no longer
    re-censors the world. Still ahead from §3.5 at this point: shared per-(topic,
    permission class) view registries (a cached view shared by N readers computes
    once per transaction, so sharing subsumes the old `shareIn` keyed-flow-registry
    idea — *implemented later, see the permission-class sharing bullet below*),
    `mapValuesMemo` keyed combinators, collection deltas.
  - **Rooms peeled — the component-scoped pilot.** Two topics: `/state/rooms`
    (root-subscribed directory: the full censor minus per-member data — no other
    users' memberships, no invite links, no presentation state — but keeping the
    viewer's *own* effective membership, so client-side permission checks work
    everywhere, and question refs, which the client-side `Question.room` reverse
    lookup and the cross-room surfaces — dashboard, search, calibration — need) and
    `/state/rooms/{rID}` (the full `censorRoom` output; the gate errors for
    non-members and recovers on join). The room page's `Room` FC subscribes the
    detail topic and feeds `RoomContext` — all room-page components already read
    that context, so one subscription site covers the page — falling back to the
    directory entry while the detail payload is in flight (the page renders with
    configuration data and fills in member detail when it lands); the presenter's
    `InviteLinkPP` subscribes it likewise. `SentState` lost `rooms` *and its
    `BaseState` conformance* — the client facade now implements `BaseState`
    directly, reading rooms from the directory slice and the rest from the shrinking
    monolith (payload down to questions/users/counts). Observed effect of the
    peel + versioned views combined: a room rename produces frames on the two rooms
    topics only — the `/state` monolith stays silent. Membership churn now
    invalidates the room's detail subscribers and the directory, not every user's
    full snapshot.
  - **The monolith is dead — `SentState` deleted.** The last two slices peeled:
    `/state/questions` carries all viewer-visible questions with the three
    per-question count maps riding along (they are per-question facts that only were
    separate maps because the monolith was flat; their fold surfaces are wake inputs
    of their own, since a prediction or comment bumps a count without touching the
    question entity), and `/state/users` carries the censored user directory (its
    wake set includes the comment archives and the viewer's predictions via the
    `referencedUsers` gates). The `"/state"` *topic* no longer exists (the websocket
    URL keeps the name); `SentState` and `StateCensor.censor()` are gone, and the
    client facade assembles `BaseState` from four root slices (questions+counts,
    rooms directory, users, self) — all subscribed in one tick, initial state in one
    consistent frame. §4.2's topic table is now realized at transitional granularity:
    questions and users are still global rather than per-room/admin-scoped — those
    narrowings ride on the UserPresentation rollout and a later per-room questions
    split — and topic invalidation is fully semantic (verified: a room rename frames
    only the rooms topics; questions/users/self stay silent). Remaining: per-room
    `room/<id>/questions` granularity, `admin/users` narrowing via UserPresentation
    rollout / users-map weaning, permission-class sharing.
  - **Topic names systematized** — they no longer mirror the retired per-subscription
    websocket URLs. The scheme (shared `TopicNames` constants + `Room.topicPrefix` /
    `Question.topicPrefix` helpers in commonMain, so client and server cannot drift):
    root slices are bare plurals plus `self` (`questions`, `users`, `rooms`, `self`);
    entity-scoped topics use a singular instance prefix (`room/<id>`,
    `room/<id>/comments`, `question/<id>/group_pred`, …); extension topics append
    `ext/<extensionId>/<name>` to their owning scope, dropping the legacy `/api` prefix
    and `.ws` suffix (`room/<id>/ext/millionaire/timer`,
    `ext/calibration_training/leaderboard`). The websocket *URL* stays `/state` —
    connection endpoint and topic namespace are separate concerns.
  - **Typed path templates** (`tools/confido/utils/PathTemplate.kt` + the `Topics`
    tree in commonMain): one object per topic serves construction (typed refs in,
    string out — `Topics.groupPred(question.ref)`) and matching (server registration;
    handlers receive decoded `Ref`s as typed lambda arguments) from the same
    declaration. Templates compose with `/` — a literal keeps the arity, a `PathParam`
    raises it — so subtopic templates derive from shared prefixes (`Topics.room /
    "comments"`), and extension topics are declared in each extension's common source
    off the same prefixes. The fixed arity family (`PathTemplate0/1/2`, the
    `combineViews` pattern) covers every current path, with the invite-shortlink topic
    the lone 2-ary. This deletes the `topicPrefix`/`TopicNames`/pattern-const layer,
    every `call.parameters["rID"] ?: badRequest(...)` in topic handlers, and the
    registry's stringly `{param}` matching; the 1-ary gate+fetch shape passes the
    typed arg to both stages, and `withRoom`/`withQuestion` gained ref-taking
    overloads for handler bodies that used to re-read path params from the call.
    Param `decode` validates shape only (never derefs) — existence stays a
    gate/handler concern, preserving NOT_FOUND and gate-recovery semantics. Wire
    strings are unchanged; the whole suite passes verbatim.
  - **Payload binding (`TopicSpec`)**: each `Topics` entry now binds its path template
    *and* its payload serializer in one declaration
    (`TopicSpec1(questionPath / "group_pred", serializer<Prediction?>())`), making the
    topic tree the single source of truth for name shape, parameter types and wire
    payload. Server registration takes the spec and type-checks the handler's return
    against it (registration functions stopped being inline/reified — the serializer
    travels in the spec, encoding goes through the explicit `WSResponseSerializer`);
    invoking a spec yields a `BoundTopic` carrying the serializer, so the client side
    is `useSubscription(Topics.groupPred(question.ref))` — no type argument, no
    per-site decoder, and no way to assert a wrong payload type. Converting
    immediately caught a real drift instance: three room-`group_pred` consumers had
    declared `Map<String, Prediction?>` against the server's
    `Map<Ref<Question>, Prediction?>` — wire-compatible only because `Ref` serializes
    as a string, i.e. exactly the silently-fragile case the binding forbids. Extension
    topics bind their payloads in the same common-source declarations. Wire strings
    unchanged; full suite passes.
  - **UserPresentation rollout completed (§5.3) — two slices.** An audit first showed
    the rollout was mostly *already done*: comments, histograms, scoreboards and the
    update-plot extension ship embedded `UserPresentation`s (with `getUserPresentation`
    + extension hook, and `UserPresentation.userId` as the granted-linkage field), and
    the client users map had exactly three consumers — the room member list (the only
    client-side `Ref<User>` derefs), the add-member search, and the admin directory.
    `referencedUsers` existed solely so guests could see comment-author User records
    that no guest surface read anymore. The two slices:
    (1) **Member lists → `room/<id>/members`** carrying `MembershipPayload`s (a
    presentation + role/invitedVia/implied data + a room-scoped `rowKey` digest for
    list reconciliation); `userId`/`email` are granted only to MANAGE_MEMBERS viewers
    (whose management actions need them), guests get their own entry only. `censorRoom`
    keeps just the viewer's own effective membership on Room — what client permission
    checks read — so the detail topic is member-data-free (`censorMembership` died; the
    directory censor reduces to stripping links/presentation). A new
    `UserPresentationContext.MEMBER_LIST` keeps the plain display name (email is its
    own field); the pseudonyms extension opts out of it — member lists show real
    identities, with pseudonym annotations added client-side for viewers who can see
    the assignment map. Note: the members *tab* is MANAGE_MEMBERS-gated in the UI, so
    the ordinary-member presentation tier is currently latent wire capability.
    (2) **Users topic narrowed to proper (admin/member) users** — the visibility set
    `censorUser` already granted, so no capability change — and demoted from root
    slice to a surface-local subscription of its two consumers. `referencedUsers`,
    the `commentAuthors` folds and `RefCountIndex` are deleted; the topic's wake set
    shrank from six inputs to the user store alone. **Load-bearing discovery:**
    `UserSession.user` is a shared-code deref (`userRef?.deref()`), so the self slice
    now carries the viewer's own User and `ClientState.derefNonBlocking` resolves
    `Ref<User>` against it — the only client-derefable user is oneself, by
    construction (`BaseState` lost `users`; the server keeps its own map). The
    planned `ClientImmediateDerefEntity` ratchet for User is therefore *not* applied:
    own-user deref must stay legal, and the remaining audit surface is zero sites.
    Further narrowing to admin-only (the §4.2 `admin/users` endpoint) would need a
    server-side search endpoint for the add-member dialog — deferred until wanted.
    *(Resolved 2026-08-16: admin-only narrowing declared moot, not deferred — the
    permission model boxes it in by design: any full user can create a room, any
    moderator can invite, and inviting needs the directory, so the member-visible
    list is a load-bearing capability, and under the nominal "members of your
    organization" semantics colleague emails are intended to be visible. What the
    audit did surface: the directory over-shipped administrative *fields* no member
    surface consumes — `lastLoginAt` (colleague-activity data), `createdAt`,
    `emailVerified`, `active` — while the add-member search reads only
    id/type/nick/email. `censorUser` is now two-tier (admin full, member stripped
    with the metadata pinned to constants), which the shared users topic gets for
    free since its class key is already the user type.)*
  - **`Ref<User>` made client-opaque** (follow-up, same day): the "ratchet not
    applied" caveat above is resolved by removing `UserSession.user` — the one
    shared-code user deref — instead of accommodating it. The server keeps it
    verbatim as a jvmMain extension in the sessions package; the client reads the
    viewer's own user via `ClientState.user` (from the self slice), and
    `appState.session.user` → `appState.user` everywhere. The permission story
    needed no new machinery: `Room.hasPermission`/`userRole` are already pure
    (id-only `eqid` comparisons — admin powers come from injected implied
    memberships, not `user.type`), and `AppStateContext` already delivers the self
    slice reactively, so the existing facade helpers are the client-side checker
    (a `usePermissionChecker` hook / `PermissionGate` component were considered
    and skipped as redundant with the facade; a gate component remains possible
    sugar). With that, `User` dropped to `ServerImmediateDerefEntity` — client
    deref of a user ref is now a compile error — and the ratchet immediately
    earned its keep: it caught the millionaire scoreboard shipping
    `Map<Ref<User>, Double>` (now `List<MillionaireScoreEntry>` of presentations
    via the SCOREBOARD context, sorted server-side), and fixing that exposed a
    latent bug where the `ServerExtension.getUserPresentation` hook member
    *shadowed* the imported core function inside extension objects —
    update_scatter_plot had been calling the default hook and shipping null
    presentations. The hook is renamed `overrideUserPresentation`, un-shadowing
    the core function (and fixing the scatter plot as a side effect).
  - **Client endgame agreed (discussion of 2026-08-15): dissolve the AppState facade
    and the `globalState` expect; deref becomes server-only.** Components get data
    from subscriptions directly (plus payload-carrying scoped contexts à la
    RoomContext), self/permissions from a narrow SelfContext with accessor hooks,
    connection state from its own context. The facade's frame-consistency service is
    already provided by MultiplexWS's atomic frame apply, so it can dissolve. The
    commonMain deref tail is enumerated and small: dead `GlobalState.get/getRef`;
    `Question.room`/count props → jvm extensions (server should use the questionRoom
    fold) with client uses dissolving into the per-room questions topic;
    `PresenterView.isValid` → server-side validation via an extension hook (a jvm
    sealed-`when` can't cover extension-registered subclasses); `Utils.mapDeref` et
    al. platform-split. **The one genuinely new obligation: `describe()` strings** —
    presenter/flow descriptions rendered client-side deref question names today, so
    they must ship precomputed in the presentation payloads once Question goes
    client-opaque (§5.2 "computed data lives only in payloads", applied once more).
    Order: connection split first, then SelfContext, then per-room questions, then
    cross-room surfaces, then the eviction commit deleting `expect globalState`,
    `BaseState`, `ClientState` and `AppStateContext` together.
  - **Session projection.** `SelfState` replaced `session: UserSession` with the two
    fields client code reads (`presenterInfo`, `followingPresentation`): the session
    is a credential-bearing server entity (its id is the auth key) and shipping it
    whole both leaked that into JS-readable state and required the expiryTime-pinning
    dedup hack — both retired. `UserSession` is `ServerImmediateDerefEntity` now;
    sessions never reach the client in any form.
  - **Connection state split out (endgame step 1).** `stale` left AppStateContext
    (which now carries `ClientState` directly) for a dedicated ConnectionContext.
    Systematic gating policy, by *commit timing* rather than widget type:
    MultiplexWS owns a debounced (1.5s) `disconnected` flag shared by React and
    non-React code; dialog action buttons gate automatically inside DialogHeader
    (every dialog commit covered with zero per-dialog code — the old scattered
    `disabled = stale` plumbing covered maybe half); immediate-commit controls
    (send-on-interaction: prediction input, role selects, quick-settings/presenter
    actions, drag reorder) opt in via `useDisconnected()`; typing/local edits and
    navigation are never gated (browsing cached data while offline is a topic-cache
    feature); `Client.send*` refuses with a clear toast as the backstop for missed
    affordances; a global ConnectionBanner (+ "back online" flash, deliberately no
    stale-data content marks for now) names the state in both layouts. Verified by
    a kill-the-server-under-a-live-browser test: banner + role-select disabling
    after the debounce, clean recovery with flowing updates on restart.
  - **SelfContext with inline accessors (endgame step 2).** Simpler than originally
    sketched, at the user's suggestion: React's `use` is callable anywhere during
    render (conditionals, loops, helper functions), so the permission "checker
    facade" collapses into plain functions — `usePermission(room, perm)` /
    `useMyUser()` / `useIsAdmin()` / `useMyPredictions()` / `usePresenterInfo()` … —
    that read SelfContext internally and are called inline at the point of a check.
    No hook plumbing, no wrapper components; the check itself stays the pure
    commonMain `Room.hasPermission`. ClientState reduced to the two data maps
    backing shared-code deref (questions+counts, rooms directory) plus the self
    slice it feeds SelfContext from. Two `use`-semantics disciplines, now enforced
    in review and documented on the context: capture values *before* `useMemo`
    bodies (a context read inside would not invalidate the memo) and before event
    handlers (not render time) — the sweep hit both for real (route memos in
    RootLayout/RoomLayout/Room; PresenterController's offer(), MemberRoleSelect's
    onChange via a now-pure `canChangeRole(myUser, room, role)`). Remaining for
    the eviction: per-room questions topic, cross-room surfaces, then
    `globalState`/`BaseState`/`ClientState`/`AppStateContext` die together.
  - **Descriptions are client-side components (describe() unblocked).** Three designs
    were weighed: creation-time snapshot (rejected — bakes a wire concern into
    long-lived persisted flows, exactly where staleness shows), server describe +
    payload strings (rejected — every `.describe()` consumer in the codebase is
    client-side; the server never renders one), and the winner: rich descriptions as
    *UI components* (`PresenterViewDescription`/`FlowActionDescription`), which also
    upgraded the labels (question names, live-updating) and unified the saved-flow /
    in-edit / offer-dialog paths. Data flows through the **`useQuestion(ref?)`
    accessor seam**: v0 reads the global questions slice; v1 subscribes
    `question/<id>` (needed anyway — post-eviction presenter pages hold bare
    question refs with no room context); v2, only if profiling demands, subsumption
    from covering collection subscriptions inside the accessor. Overlapping
    subscriptions are deliberately tolerated rather than optimized: dedup makes
    same-topic consumers free, and flush-time recompute into atomic frames makes
    duplicated data *consistent* duplication — bounded bytes, no divergence window.
    The commonMain `describe()` remains as a deref-free generic fallback (the two
    name-bearing FlowAction describes went generic); remaining eviction blockers:
    only the server-only-called `isValid`/`getNavigateUrl`.
  - **Question topics added** (`room/<id>/questions` + `question/<id>`): the §4.2
    per-room slice ships own + effective questions (each censored against its own
    room) with counts riding along; the by-id topic ships entity data only, so
    count churn frames the collection slice but never the entity topic (verified,
    along with cross-room isolation and the membership gate). `useQuestion` flipped
    to v1 (real `question/<id>` subscription; `useSubscription` accepts a null
    topic to keep hook order stable), and the room page's question lists read the
    room slice. The global `questions` slice remains root-subscribed as the
    transitional bridge (initial-load fallback in `useQuestion` and RoomLayout,
    plus all remaining cross-room/deref consumers); it narrows and dies at the
    eviction. Remaining before the eviction: cross-room surfaces (dashboard,
    search, calibration, presenter pages) to direct subscriptions, the
    `isValid`/`getNavigateUrl` server dispatch, then the eviction commit;
    permission-class sharing rides after.
  - **Cross-room surfaces converted to direct subscriptions.** The dashboard, workspace
    search, calibration views, scores page and room list stopped reading the AppState
    facade (and stopped client-side deref): they call `useRooms()` /
    `useWorkspaceQuestions()` — surface-local subscriptions of the existing `rooms` and
    `questions` topics, the users-topic precedent applied again. The *root* questions
    subscription still dies at the eviction, but the topics themselves survive at this
    granularity: a surface that displays cross-room data (dashboard's recently-opened
    feed, search-on-every-keystroke) genuinely needs the cross-room slice, and paying
    for exactly what the surface shows is the honest cost model; per-room fan-out would
    need dynamic multi-subscription machinery for zero censoring benefit (the global
    topics censor per room already). `searchWorkspace` now takes the two maps as
    parameters instead of `ClientState`; `roomOf(question)` (a directory lookup)
    replaces the commonMain `Question.room` reverse-scan on these surfaces. A handful
    of `use(AppStateContext)` reads deleted along the way were already dead — kept
    alive only as accidental re-render triggers for bodies that read `deref()` behind
    React's back; the subscriptions now provide that reactivity honestly.
  - **The globalState-reading `Question` props evicted from commonMain.**
    `Question.numPredictions`/`numPredictors`/`room` (and `effectiveSchedule`, which
    read `room` internally) were the last shared-code properties whose values came
    from global state. They are now jvmMain extensions over the resident folds
    (`room` via the `questionRoom` fold as planned; `effectiveSchedule` delegates to
    a new pure commonMain `effectiveScheduleIn(room)` that both sides share). Client
    consumers split by what they hold:
    *room-scoped surfaces* (question page, list items, dialogs) read counts via the
    new accessor or the room questions slice and get the room from `RoomContext`;
    *bare-ref surfaces* (presenter tiles, incl. the millionaire and
    prediction-showcase extensions) subscribe the new **`question/<id>/counts`**
    topic — a sibling of `question/<id>` rather than a field of it, preserving the
    step-27 cutoff in both directions (count churn never frames the entity topic,
    entity edits never re-send counts; verified by test). Access shapes:
    `useQuestionCounts(ref?)` (same call discipline as `useQuestion`) and a
    `QuestionPredictors` text-node *component* for render-position sites — table
    cells and tile footers inside loops stay hook-safe by construction. Two
    disciplines enforced on the way: real hooks hoisted above extension hook
    methods' early returns (stable hook order — the old `use()`-only reads were
    exempt; subscriptions are not), and `EditQuestionDialog`'s answer-space lock
    became a derived value instead of `useState`-captured, since the counts
    subscription may deliver after first render.
  - **`isValid`/`getNavigateUrl` deref-free via `EntityLookup` (amends the planned
    server hook).** The last commonMain deref users take a *lookup parameter*
    instead of moving to a jvm dispatch: `PresenterView.isValid(lookup)` /
    `FlowAction.getNavigateUrl(room, lookup)` where `EntityLookup` resolves
    question/room refs, and the server (their only caller) passes
    `ServerEntityLookup` (plain resident deref). This solves the same problem the
    step-22 plan's extension hook targeted — a jvm sealed-`when` can't cover
    extension subclasses — without the hook *or* the `when`: overrides stay
    colocated with their classes as pure functions of the lookup, extensions
    override normally (their current `getNavigateUrl` bodies are pure; the one
    deref-y core branch, tag-navigation's first-question scan, goes through the
    lookup). `isValid` also drops its vestigial `suspend` — deref was the only
    reason for it.
  - **The eviction: the client global-state layer is gone.** Landed in three
    passes. (1) *Presenter pages and every remaining client deref* went through
    subscriptions: PP/PC components resolve view refs via `useQuestion`
    (`MultiQuestionPP` tiles take the ref and subscribe per tile), room-scoped
    dialogs and extension surfaces read the room questions slice, presenter room
    refs resolve against the directory, and the millionaire shared helpers take
    resolved question lists instead of deref'ing inside (`useMillionaireConfig`
    accepts a nullable room id so hooks stay unconditional). (2) *The remaining
    facade field-reads* converted — `useRoomQuestions()` now reads the room slice
    and serves all flow-editor components; the three `ClientExtension` hooks that
    carried a `ClientState` parameter dropped it (no implementation used it);
    `conditional_baseline.extractBaseline` takes an optional known-questions map
    (its submit-path caller skips the existence check, which the dialog's own
    render-time validation already surfaces) — after which the ~35 remaining
    `use(AppStateContext)` reads were *all bare*, kept alive only as re-render
    triggers for deref, and were deleted. (3) *The machinery*: `BaseState`,
    `GlobalState`, `expect globalState`, `ClientState`, `AppStateContext`, the
    client deref (`ClientRefs.kt`, the `ClientImmediateDerefEntity` marker) and
    the accessor/RoomLayout transitional fallbacks are gone; `serverState` is a
    plain object; `deref`/`mapDeref`/`forEachDeref` are jvmMain-only over
    `ServerImmediateDerefEntity` (`ImmediateDerefEntity` remains as an alias);
    the provider owns only the websocket lifecycle and the root `self`
    subscription feeding SelfContext. Client entities now flow exclusively
    through topic payloads; the topics a page pays for are exactly the ones its
    surfaces display. Devtools/tests read `window.curTopics` (the last raw
    payload per subscribed topic, maintained in MultiplexWS's frame apply) in
    place of the retired per-slice exports.
    **The eviction's one behavioral lesson (React #310 on cold loads):** with the
    facade, every page's first render already had data (root slices were applied
    before children mounted), so `val x = <data> ?: return@FC` followed by more
    hooks never changed its hook count. Post-eviction a first render *without*
    data is the normal cold-load case, and the moment the subscription delivers,
    such a component renders more hooks than before — a crash the old world
    structurally never exercised. Fixed across the tree by the now-standing
    discipline: *all real hooks precede any data-dependent early return*
    (nullable-topic subscriptions make this cheap), with `Room` split into a
    data-resolving outer component and an inner page that only mounts with the
    room in hand. Two pre-existing latent instances of the same shape
    (`DeleteUserDialog`'s builder-nested `useState` behind `props.entity ?:
    return`, `PredictionFlags`' emptiness return) were left as found — their
    conditions are mount-stable in practice — but the pattern is now on the
    review checklist.
  - **Counts split out of the question collection topics** (2026-08-16; *reverses* the
    question-topics bullet's "counts riding along" — recorded rather than silently
    flipped). Two facts changed the calculus. First, the churn asymmetry: counts bump
    on every prediction/comment, entities change on occasional edits — and since the
    wire diff is whole-payload, bundling meant **every prediction re-sent the entire
    censored entity collection** to every `questions`/`room/<id>/questions`
    subscriber. Second, a consumer audit showed the client had already migrated:
    after the `question/<id>/counts` accessor rollout, count *display* everywhere
    rides the per-question sibling, and exactly two collection-shaped readers
    remained (the question-list "sole predictor" hint, the management table's
    any-predictions column gate). As-built: the collection payloads are bare censored
    entity maps (`QuestionsState` deleted); their wake sets are entity stores only
    (`latestPredictions` dropped as vestigial — no extension overrides
    `censorQuestion`); a new `room/<id>/questions/counts` topic
    (`Map<Ref<Question>, QuestionCounts>`) serves collection-shaped reads, its
    visibility set reusing `censorRoomQuestions` so counts can't leak and a
    visibility transition moves entity and counts in one frame; the memo equality
    cutoff keeps entity edits from framing counts and count churn from framing
    entities (verified both directions, collection and per-question scale). No
    global counts sibling — it would have zero readers. Sequencing note: this went
    *before* permission-class sharing because the two divide the per-mutation cost
    by different factors — sharing by subscribers-per-class, splitting by churn
    ratio — and here the churn ratio is the larger divisor; with count churn no
    longer waking the entity topics, sharing's remaining value is the entity-censor
    recompute under many same-class connections.
  - **Permission-class sharing implemented** (2026-08-16) — the §4.3 censor-memoization
    stage, realized as the §3.5 prediction said: a registry of shared views, no new
    memoization machinery. `PermClass` = user type + per-room effective-permission
    sets (union across direct/implied memberships; entry presence = membership, so
    room-visibility gates read the class too). `ClassCensor` holds the class-pure
    censor family (question entity/collections/counts, users), split from the
    per-viewer `StateCensor`; the `ServerExtension.censorQuestion` hook now takes the
    class instead of a user — class-purity by construction (a per-viewer read there
    would leak between same-class viewers; per-viewer extension data belongs in
    extension-owned per-viewer topics). `sharedViewWS` registrations: the body
    receives the class and no session/call; each subscription resolves its viewer's
    class through a per-session *class view* (shared by all the session's
    subscriptions; memo equality cutoff makes session renewals and unrelated
    membership churn dedup away) and `flatMap`s into a weakly-referenced registry
    keyed (topic pattern, args, class) — all same-class subscribers hold the *same*
    memoized encoded-payload view, so a payload computes and encodes once per
    transaction regardless of subscriber count; a role change re-selects the registry
    entry on the live subscription (verified). A thin per-subscription error boundary
    passes the shared encoded element through by reference and turns class-resolution
    `ResponseError`s into WSError frames. Lifecycle: entries are pinned by their
    subscribers and survive dormancy weakly — sharing works *across time* too: a
    fresh subscription served from a still-valid entry costs zero computes, and the
    §3.5 equality cutoff means even revalidation after unrelated churn skips the
    encode (both observed in testing; the compute counters exposed at the devmode
    `/diag/topic_computes` endpoint count encodes, which is why the test suite's
    sharing assertions are relative to visibly payload-changing mutations).
    Shared tier: `questions`, `room/<id>/questions[/counts]`, `question/<id>[/counts]`,
    `users`. Deliberately per-viewer (decision of 2026-08-16): the rooms family
    (viewer's own membership embedded — and the directory is where the class key is
    *maximally fragmenting* while payload and recompute are smallest), `members`
    (own-row flag), `self`, and the own-prediction-gated topics. The rule of thumb:
    share when payload×churn is large *and* the class key is narrower than the
    subscriber set; where it isn't, per-connection is the honest granularity and the
    `myMemberships`-into-self move remains the escape hatch if the directory ever
    profiles hot.
    *Class-key projection (same day, user-driven):* a global class key fragments to
    near-per-user as soon as membership vectors vary, so per-topic **scope
    projections** narrow the key to the rooms the topic's censor actually reads
    (`PermClass.scopedTo`): question-instance topics key on the question's own room
    (collapsing to user type + one permission set — a handful of values per
    deployment), the room questions/counts topics on the room *plus the rooms of its
    effective questions* (group-room viewers with different child-room permissions
    legitimately differ), and `users` on the user type alone (all proper users share
    one payload). The soundness design: the topic body receives the *projected*
    class — the registry key and the censor's input are the same value — so an
    under-projection reads as "no permissions" and censors too much, visibly,
    rather than leaking between same-key viewers (fail-closed key-narrowing). The
    projection recomputes on room churn as well as class churn (room structure can
    change which rooms are in scope without the class map changing), with the
    equality cutoff ensuring re-selection only on a real projected-class change —
    which also cuts churn: a membership change in an unrelated room no longer
    touches a room-scoped topic at all (verified: an unrelated-room join produces
    zero recomputes and zero frames on the scoped topics while the global
    `questions` slice re-frames). The global `questions` topic deliberately keeps
    the identity key: with fragmented classes identity-sharing degrades to
    per-viewer cost anyway (registry entries are the same order as the sender's
    per-connection lastSent maps), and cohort collisions still share — unsharing
    would buy nothing in either regime. Still ahead from §3.5:
    `mapValuesMemo` keyed combinators, collection deltas.
  - **Comment topics over resident partitions** (2026-08-16) — the last
    O(everything × subscribers)-per-event shape retired, and the §3.1 `all + tail`
    promise honored by *residency* rather than wire deltas (user-driven decision:
    post-split no payload is large, comments' real multiplier was the per-subscriber
    Mongo refetch per version bump — thirty watchers meant thirty partition fetches
    per comment — and that lives server-side; the §3.5 delta bullet is annotated as
    deferred-indefinitely with its design intact). `ResidentPartitions` (§3.1
    amendment: subscription-scoped tier-2 residency) pins a partition while
    subscribed + grace; the comment topics became plain `viewWS` View compositions
    over the pin's cell — the gate+fetch split, the archive `versionView` machinery
    and the per-viewer refetch are gone (gate+fetch remains for extension topics).
    ViewTopics gained the minimal resource protocol this needed:
    `SubscriptionResources` on `WSBody` (`resources.onClose { pin.close() }`),
    closed by the connection loop on unsubscribe/disconnect/build failure. Two
    subtleties worth recording: `stripUserLikes` deliberately emits no change
    events, so it patches live partition caches explicitly in its apply step (the
    old design's users-map-gate refetch covered this implicitly); and the fetch
    hook is an abstract *member function* because a suspend *lambda* whose
    parameter type is a generic bounded by a value class (`Ref`) miscompiles —
    coroutine spilling resumed with a null parameter (hit for real, cost a debug
    round). Verified end-to-end (tests/state/comments_test.py + diag load
    counters): one partition load serves N subscribers and all subsequent churn
    (zero refetches across add/like/edit/delete), a comment and its count arrive
    in ONE frame (the frame-consistency win — previously impossible for a
    FlowTopic), likedByMe stays per-viewer over the shared cache, and
    unsub+resub within the grace period reuses the lingering cache. Payload shape
    unchanged except ordering, now explicitly sorted by timestamp (the cache is a
    map; the old collection-scan order was incidental) — no client changes.
  - **Group-prediction topics shared via extended keys** (2026-08-16) — the
    remaining realtime-event multiplier (tens of participants watching one
    question's aggregate paid one gate+aggregate compute per subscriber per
    prediction). `sharedViewWS` gained its general *keyed* form: the topic declares
    a per-subscription reactive key view (it may read the session and query
    parameters — everything per-viewer must flow into the key), and the body
    receives the key, keeping the leak-proof property (registry key = censor
    input); the class-scoped form is now sugar over it. Key design principle
    (sharpened by the user to its fixed point): **the key is whatever the payload
    is a function of, and nothing more** — for gate-shaped topics whose data is
    observer-independent past the gate, that is the gate *outcome*, not the class.
    `question/<id>/group_pred` keys on a four-valued access outcome
    (visible/hidden/forbidden/not-found): every viewer who may see the aggregate
    shares one entry regardless of permission class (verified: an admin
    subscribing after a member cohort adds zero computes); the whole gate runs in
    the per-subscription key view (cheap booleans), and a first prediction,
    visibility change or deletion re-keys the live subscription — gate_test's
    ANSWERED unlock now exercises exactly the re-selection path.
    `room/<id>/group_pred` keys on the *visible-question set* (null = non-member),
    which fully determines the multi-question payload — same-visibility viewers of
    any class share. Its `?qids=` filter is **removed** (an intermediate keyed
    version briefly carried it in the key): it predated the multiplexed socket, no
    client ever passed it, and selective consumers subscribe per-question topics.
    Conversion fixed a **latent leak**: the room topic had no
    membership gate and served EVERYONE-visibility aggregates of foreign rooms to
    any logged-in user (the per-question topic always checked VIEW_QUESTIONS); it
    now requires VIEW_QUESTIONS, gate-recovering on join. The histogram topic
    deliberately stays per-connection (user decision): its per-bin
    UserPresentations are observer-dependent (isMyself), and an audit confirmed
    every client subscription is display-scoped (QuestionPage mounts it only
    while the histogram is open; presenter PPs subscribe only while rendering) —
    if it ever needs sharing, the keyed form with a viewer-ref key component for
    VIEW_INDIVIDUAL_PREDICTIONS holders (class-pure for ordinary participants)
    is the recorded design.
- **Phase 4 — semantic component splits** (§5.1): membership first, then invite links, then
  Question's resolution cluster; one tolerant-reader migration each.
- **Phase 5 (optional) — SqliteBackend** (§3.3): when the replica-set ops burden or
  Native/GraalVM interest justifies it. The interface is proven by then; Mongo remains for
  existing deployments.

---

## 8. Open questions

- Field-by-field component assignment for Question and Room (domain judgment; propose
  drafting the actual component declarations and arguing about the ~10 ambiguous fields).
- Membership: component-of-Room first vs. own store immediately.
- Pseudonymous-key scheme for UserPresentation — **deferred until a surface needs it**:
  presentations carry displayName + color, and no current UI needs stable-but-unlinkable
  identity across payloads (the members topic's `rowKey` — a room-scoped SHA-256 digest —
  is the first precedent for the shape). Known caveat when it comes up: presentation
  `color` is `stringToColor(user.id)`, a deterministic function of the user id shared
  across all contexts, so colors are already a weak cross-context join key by design
  (consistent avatar colors); a real pseudonymity scheme would have to include color.
- Session store: stays resident-with-expiry (hourly sweep) — is a TTL/expiry concern worth
  building into the store shape, or leave it a background task?
- Topic payload versioning during Phase 3 rollout (old `state` topic and new topics coexist —
  for how long?).
- Rollout of the fail-closed default for extension data keys (§5.4): audit which keys client
  code currently reads before flipping the default; decide whether the canned visibility tier
  needs role granularity finer than permission classes.
