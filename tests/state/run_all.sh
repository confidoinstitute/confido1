#!/bin/bash
# Run the state-sync test suite against a self-managed scratch server.
#
#   tests/state/run_all.sh            # protocol tests only
#   tests/state/run_all.sh --browser  # + headless-chromium browser tests
#   tests/state/run_all.sh --native   # run the GraalVM native-image server
#                                     # (build/native/nativeCompile/confido-server;
#                                     #  combinable with --browser)
#
# Environment overrides (defaults in parentheses):
#   CONFIDO_TEST_PORT   (8099)   scratch server port — never your dev instance's
#   CONFIDO_TEST_DB     (confido_statetest)  scratch Mongo DB, DROPPED on start
#   CONFIDO_TEST_STATIC (build/kotlin-webpack/js/productionExecutable)  JS bundle dir (first CONFIDO_STATIC_PATH entry)
#   CONFIDO_TEST_CDP    (9222)   chromium remote-debugging port (--browser only)
#   CONFIDO_EXTENSIONS  (unset)  forwarded to the server if set
#
# Prerequisites: mongosh + a running replica set; python3 with `websockets`;
# for --browser: chromium and a built bundle (gradle jsBrowserProductionWebpack).
set -u
cd "$(dirname "$0")"
REPO_ROOT="$(cd ../.. && pwd)"

PORT="${CONFIDO_TEST_PORT:-8099}"
DB="${CONFIDO_TEST_DB:-confido_statetest}"
STATIC="${CONFIDO_TEST_STATIC:-$REPO_ROOT/build/kotlin-webpack/js/productionExecutable}"
CDP="${CONFIDO_TEST_CDP:-9222}"
BROWSER=0
NATIVE=0
for arg in "$@"; do
    case "$arg" in
        --browser) BROWSER=1 ;;
        --native) NATIVE=1 ;;
        *) echo "ERROR: unknown argument $arg" >&2; exit 1 ;;
    esac
done

NATIVE_BIN="$REPO_ROOT/build/native/nativeCompile/confido-server"
if [ "$NATIVE" = 1 ] && [ ! -x "$NATIVE_BIN" ]; then
    echo "ERROR: no native binary at $NATIVE_BIN (run: gradle nativeCompile)" >&2
    exit 1
fi

port_pid() { ss -tlnp 2>/dev/null | grep ":$1 " | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2; }

if [ -n "$(port_pid "$PORT")" ]; then
    echo "ERROR: port $PORT is already in use — refusing to touch a running instance." >&2
    exit 1
fi
if [ ! -f "$STATIC/confido1.js" ]; then
    if [ "$BROWSER" = 1 ]; then
        echo "ERROR: no bundle at $STATIC (run: gradle jsBrowserProductionWebpack -x kotlinStoreYarnLock)" >&2
        exit 1
    fi
    # Backend-only runs just need the file to exist for the boot-time bundle hash.
    mkdir -p "$STATIC" && touch "$STATIC/confido1.js"
fi

echo "== scratch DB $DB (dropping)"
mongosh --quiet --eval "db.getSiblingDB('$DB').dropDatabase()" >/dev/null

CHROME_PID=""
cleanup() {
    local pid; pid="$(port_pid "$PORT")"
    [ -n "$pid" ] && kill "$pid" 2>/dev/null
    [ -n "$CHROME_PID" ] && kill "$CHROME_PID" 2>/dev/null
}
trap cleanup EXIT

echo "== starting scratch server on :$PORT$([ "$NATIVE" = 1 ] && echo ' (native image)')"
export CONFIDO_DB_NAME="$DB" CONFIDO_DEVMODE=1 CONFIDO_HTTP_PORT="$PORT" CONFIDO_STATIC_PATH="$STATIC:$REPO_ROOT/static"
if [ "$NATIVE" = 1 ]; then
    "$NATIVE_BIN" >"${TMPDIR:-/tmp}/confido_test_server.log" 2>&1 &
else
    (cd "$REPO_ROOT" && gradle run -x kotlinStoreYarnLock >"${TMPDIR:-/tmp}/confido_test_server.log" 2>&1) &
fi
for _ in $(seq 1 120); do [ -n "$(port_pid "$PORT")" ] && break; sleep 2; done
if [ -z "$(port_pid "$PORT")" ]; then
    echo "ERROR: server did not come up (see ${TMPDIR:-/tmp}/confido_test_server.log)" >&2
    exit 1
fi

export CONFIDO_TEST_BASE="http://localhost:$PORT" CONFIDO_TEST_CDP="$CDP"
# Lets connection_browser_test.py restart the same kind of server it killed.
[ "$NATIVE" = 1 ] && export CONFIDO_TEST_SERVER_CMD="$NATIVE_BIN"

if [ "$BROWSER" = 1 ] && ! curl -s --max-time 3 "localhost:$CDP/json/version" >/dev/null; then
    echo "== starting headless chromium (CDP :$CDP)"
    PROFILE="$(mktemp -d)"
    chromium --headless=new --remote-debugging-port="$CDP" --user-data-dir="$PROFILE" \
        --no-sandbox --disable-gpu >/dev/null 2>&1 &
    CHROME_PID=$!
    sleep 4
fi

FAILED=0
run() {
    echo "== $1"
    if python3 "$1"; then echo; else echo "** $1 FAILED"; echo; FAILED=1; fi
}

# Protocol tests. Order matters mildly: they share the scratch DB and seed
# idempotently, but each selects its own entities by name.
run users_test.py
run members_test.py
run rooms_test.py
run questions_test.py
run shared_test.py
run comments_test.py
run ws_test.py
run gate_test.py
run permission_test.py
run cascade_test.py
run sensitive_test.py
run pseudonym_test.py
run presentation_test.py
run dashboard_test.py
run search_test.py
run group_revoke_test.py

if [ "$BROWSER" = 1 ]; then
    run browser_test.py
    run member_browser_test.py
    run group_browser_test.py
    run presentation_browser_test.py
    run presentation_create_browser_test.py
    run presenter_multi_browser_test.py
    run dialog_stack_browser_test.py
    run connection_browser_test.py   # kills + restarts the scratch server itself
fi

[ "$FAILED" = 0 ] && echo "SUITE: ALL PASS" || echo "SUITE: FAILURES"
exit $FAILED
