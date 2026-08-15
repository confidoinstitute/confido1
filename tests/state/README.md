# State-sync test suite

Runtime tests for the persistence/state-sync redesign (see
`PERSISTENCE_SYNC_REDESIGN.md`): the multiplexed `/state` websocket, topic
censoring, versioned-view dedup, and the client bundle end-to-end. They grew
alongside Phase 2/3 of the redesign and encode its behavioral contracts.

## Running

```bash
tests/state/run_all.sh            # protocol tests (backend only)
tests/state/run_all.sh --browser  # + headless-chromium end-to-end tests
```

The runner starts its own **scratch server** (default port 8099, scratch Mongo DB
`confido_statetest` — dropped on every run) and never touches a normally-running
dev instance; it refuses to start if the port is taken. Browser tests need a built
production bundle (`gradle jsBrowserProductionWebpack -x kotlinStoreYarnLock`) and
`chromium`; protocol tests need only `python3` with the `websockets` package and a
local MongoDB replica set.

Individual tests can be run directly against an already-running scratch server:

```bash
CONFIDO_TEST_BASE=http://localhost:8099 python3 tests/state/users_test.py
```

They assume a devmode server (`CONFIDO_DEVMODE=1`) whose `GET /init/` seeds
`debugadmin` / `debugmember` (password `pass`) and `testroom`.

## What covers what

| Test | Covers |
|---|---|
| `users_test.py` | users-topic narrowing (proper users vs guests), self-slice shape (own user, projected `presenterInfo`, **no** raw session), users-topic wake-set isolation, guest invite-accept flow |
| `members_test.py` | `room/<id>/members` presentation payloads (§5.3): per-viewer-class censoring, rowKeys, membership-churn framing vs rename dedup, detail room own-membership-only |
| `rooms_test.py` | rooms directory vs per-room detail topics, detail gate + recovery on join, rename topic isolation |
| `ws_test.py` | batched subscriptions → one consistent initial frame, retired `/state` monolith topic, comment-archive delivery, no-change dedup, unknown-topic errors |
| `gate_test.py` | permission-gated topics: WSError frames on a live subscription, error-state cutoff, gate unlock on the same subscription |
| `browser_test.py` | real bundle end-to-end (admin): slices apply, live room/self updates, members tab + admin directory render from their topics, console-error sweep |
| `member_browser_test.py` | ordinary-member view: no Members tab, no foreign identity data, no errors on presentation-only codepaths |
| `connection_browser_test.py` | disconnect UX: kills the scratch server under a live session (banner + immediate-commit controls disable after the debounce), restarts it (recovery + live updates) |

Shared plumbing: `confido_test.py` (HTTP/websocket protocol helpers, `Suite`
pass/fail accounting) and `cdp_test.py` (Chrome DevTools Protocol driving).

## Conventions

- Tests share the scratch DB and seed idempotently; each selects its own
  entities **by name** (never "first in the map" — other tests seed too).
- Protocol assertions work on raw topic frames; `by_topic` keeps the *last*
  update per topic, mirroring the client cache.
- `kill` servers by PID from `ss -tlnp`, never `pkill -f` (which can match the
  invoking shell itself).
