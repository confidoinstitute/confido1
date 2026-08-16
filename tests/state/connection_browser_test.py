#!/usr/bin/env python3
"""Disconnect UX end-to-end: kill the server under a live browser session, assert the
banner appears and immediate-commit controls gray out after the debounce; restart the
server, assert reconnect (banner clears, controls re-enable, live updates flow).

Note: this test restarts the scratch server itself (mirroring the env it was started
with — see SERVER_ENV_KEYS); run_all.sh's cleanup kills whatever holds the port.
"""
import asyncio
import os
import pathlib
import subprocess
import sys
import time
import urllib.parse

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
PORT = urllib.parse.urlparse(BASE).port or 80
SERVER_ENV_KEYS = ["CONFIDO_DB_NAME", "CONFIDO_DEVMODE", "CONFIDO_HTTP_PORT",
                   "CONFIDO_STATIC_PATH", "CONFIDO_EXTENSIONS", "CONFIDO_ROLES_FILE"]


def server_pid():
    out = subprocess.run(["ss", "-tlnp"], capture_output=True, text=True).stdout
    for line in out.splitlines():
        if f":{PORT}" in line and "pid=" in line:
            return int(line.split("pid=")[1].split(",")[0])
    return None


def wait_port(present, timeout=120):
    for _ in range(timeout * 2):
        if (server_pid() is not None) == present:
            return True
        time.sleep(0.5)
    return False


async def main():
    assert server_pid(), f"server must be running on port {PORT}"
    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)

        code = await login_page(cdp, s, ADMIN_EMAIL)
        if code != 200:
            print("FAIL: login ->", code); sys.exit(1)
        await evaluate(cdp, s, """
            fetch('/rooms/testroom/members/add', {method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({type:'payloads.requests.AddedExistingMember',
                                      user:'debugmember', role:'forecaster'})}).then(r => r.status)""",
            await_promise=True)

        # Open the members tab: MemberRoleSelect is an immediate-commit control.
        await cdp.cmd("Page.navigate", {"url": BASE + "/room/testroom/members"}, session=s)
        await wait_for(cdp, s, "!!(window.curTopics && window.curTopics.rooms && window.curTopics.rooms.data && window.curTopics && window.curTopics.self && window.curTopics.self.data)", desc="app state")
        await wait_for(cdp, s, "document.body.innerText.includes('debugmember')", desc="member rows")
        if await evaluate(cdp, s, "document.body.innerText.includes('Connection lost')"):
            print("FAIL: banner shown while connected"); sys.exit(1)
        if not await evaluate(cdp, s, "document.querySelectorAll('select:not(:disabled)').length"):
            print("FAIL: no enabled role selects while connected"); sys.exit(1)
        print("PASS: connected baseline (no banner, role selects enabled)")

        # --- Kill the server: banner + gating must appear after the debounce ---
        os.kill(server_pid(), 15)
        wait_port(False)
        await wait_for(cdp, s, "document.body.innerText.includes('Connection lost')",
                       timeout=15, desc="disconnect banner")
        await wait_for(cdp, s,
            "document.querySelectorAll('select').length > 0 && document.querySelectorAll('select:not(:disabled)').length === 0",
            timeout=10, desc="role selects disabled while disconnected")
        print("PASS: banner shown and immediate-commit controls disabled after disconnect")

        # --- Restart the server: reconnect must clear the banner and re-enable ---
        env = dict(os.environ)
        env.setdefault("CONFIDO_HTTP_PORT", str(PORT))
        missing = [k for k in ("CONFIDO_DB_NAME", "CONFIDO_STATIC_PATH") if k not in env]
        if missing:
            print("FAIL: cannot restart server, env not set:", missing); sys.exit(1)
        subprocess.Popen(["gradle", "run", "-x", "kotlinStoreYarnLock"],
                         cwd=REPO_ROOT, env=env,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if not wait_port(True):
            print("FAIL: server did not come back"); sys.exit(1)
        await wait_for(cdp, s, "!document.body.innerText.includes('Connection lost')",
                       timeout=30, desc="banner cleared after reconnect")
        await wait_for(cdp, s, "document.querySelectorAll('select:not(:disabled)').length > 0",
                       timeout=10, desc="role selects re-enabled")
        print("PASS: reconnect clears banner and re-enables controls")

        # Live updates flow again on the resubscribed connection
        st = await evaluate(cdp, s, """
            fetch('/rooms/testroom/edit', {method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({id:'testroom', name:'Reconnected room', description:'x',
                                      icon:null, color:'GRAY'})}).then(r => r.status)""", await_promise=True)
        if st != 200:
            print("FAIL: post-reconnect edit ->", st); sys.exit(1)
        await wait_for(cdp, s, "window.curTopics.rooms.data.testroom.name === 'Reconnected room'",
                       desc="live update after reconnect")
        print("PASS: live updates flow after reconnect")
        print("OVERALL: PASS")


asyncio.run(main())
