# State-sync test suite

Runtime tests for the persistence/state-sync redesign (see
`PERSISTENCE_SYNC_REDESIGN.md`): the multiplexed `/state` websocket, topic
censoring, versioned-view dedup, and the client bundle end-to-end. They grew
alongside Phase 2/3 of the redesign and encode its behavioral contracts.

## Running

```bash
tests/state/run_all.sh            # protocol tests (backend only)
tests/state/run_all.sh --browser  # + headless-chromium end-to-end tests
tests/state/run_all.sh --native   # scratch server = GraalVM native image
                                  # (gradle nativeCompile first; combinable with --browser)
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
| `permission_test.py` | invite-link DISABLED_FULL revocation (live revoke + restore), hidden questions in the group-pred family and on write routes (predict/comment 404), error recovery to a value-equal payload, multidelete dangling refs, member-only presentation follow, owner not deposable |
| `sensitive_test.py` | sensitive questions: individual predictions refused for everyone (compare API, individual CSV, comment attachment), aggregates gated by the predictor threshold (group_pred/histogram WSError below, unlock on the live subscription at 5), coarse nameless histogram bins, sensitivity not revocable once answered |
| `presentation_test.py` | presentation flows: edit validation (cross-room/unset question refs incl. inside views, duplicate item ids, slide bounds), slide-order normalization (in-place re-sort + dedup), sticky per-step view overrides (setview survives away-and-back, revertview restores the script) |
| `group_revoke_test.py` | DISABLED_FULL on a group-room link revokes implied child-room access |
| `profile_test.py` | profile self-service: nick sanitization, password set/change (current required, other sessions ended), e-mail change via verification (pending on the self slice, only the newest link valid, cancel, verify logged out, duplicate refused), reset links choosing a new password logged out (sessions + other reset links ended), e-mail-less guests, admin e-mail validation (link tokens read from the devmode mail log) |
| `group_browser_test.py` | (browser) sidebar group hierarchy, hover-+, create-new menu, group Rooms tab, ?group= preselect |
| `browser_test.py` | real bundle end-to-end (admin): slices apply, live room/self updates, members tab + admin directory render from their topics, console-error sweep |
| `member_browser_test.py` | ordinary-member view: no Members tab, no foreign identity data, no errors on presentation-only codepaths |
| `profile_browser_test.py` | (browser) profile UI as a fresh e-mail-link user: nickname section save round-trip, first password, reset link → choose-a-new-password page, no console errors |
| `presenter_multi_browser_test.py` | (browser) multi-question presenter: numeric tile renders the canvas group graph (NumericPredGraph) with estimate + resolution flags, no Plotly DOM, console-error sweep |
| `dialog_stack_browser_test.py` | nested dialogs: per-layer backdrops (0.5 page / 0.25 inter-dialog), open-order stacking beats portal DOM order, backdrop click closes only the topmost dialog |
| `prediction_browser_test.py` | question-page prediction submission: a slow older send neither cancels nor overtakes a newer commit (sends one at a time, in order), a commit made right before leaving the page is saved (keepalive flush on pagehide/visibilitychange), touchcancel mid-drag reverts the thumb without committing |
| `zoom_browser_test.py` | (browser) graph zoom UX: plain wheel scrolls the page and Ctrl+wheel zooms (numeric graph + binary slider), zoom pill range + ✕ reset, loupe-armed one-shot select-to-zoom with slip guards and Escape, binary slider settles after a wheel burst (no zoom-state echo oscillation), Ctrl+wheel over the numeric input slider drives the graph, conditional Zoom to fit, cursor-anchored ctrl+scroll hint only for wheel gestures starting over a graph, intact/distinct tick labels (unzoomed + zoomed, numeric and binary), comparison plot zoom (view carried group graph ⇄ comparison, Ctrl+wheel, ✕, Zoom to fit over both states, hover readout under the gesture handlers, cursor line under CSS zoom), zoom carried to the presenter (group graph via its presenter button incl. the dialog, comparison via `ComparePV.view`) |
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
