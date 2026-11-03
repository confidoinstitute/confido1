"""Shared plumbing for the browser (headless-chromium CDP) tests.

Expects a chromium started with --headless=new --remote-debugging-port=$CONFIDO_TEST_CDP
(default 9222); run_all.sh does this. Tests drive the real production bundle served by
the scratch server.
"""
import asyncio
import json
import os
import urllib.request

from confido_test import BASE

CDP_PORT = int(os.environ.get("CONFIDO_TEST_CDP", "9222"))


class CDP:
    def __init__(self, ws):
        self.ws = ws
        self.mid = 0
        self.events = []

    async def cmd(self, method, params=None, session=None):
        self.mid += 1
        msg = {"id": self.mid, "method": method, "params": params or {}}
        if session:
            msg["sessionId"] = session
        await self.ws.send(json.dumps(msg))
        while True:
            m = json.loads(await self.ws.recv())
            if m.get("id") == self.mid:
                if "error" in m:
                    raise RuntimeError(f"{method}: {m['error']}")
                return m.get("result", {})
            else:
                self.events.append(m)

    async def drain(self, seconds):
        try:
            while True:
                m = json.loads(await asyncio.wait_for(self.ws.recv(), seconds))
                self.events.append(m)
        except asyncio.TimeoutError:
            pass


async def browser_ws():
    import websockets
    ver = json.loads(urllib.request.urlopen(f"http://localhost:{CDP_PORT}/json/version").read())
    return await websockets.connect(ver["webSocketDebuggerUrl"], max_size=2**26)


async def new_page(cdp):
    """Create a page target with Runtime/Page/Log enabled; returns the session id."""
    t = await cdp.cmd("Target.createTarget", {"url": "about:blank"})
    s = (await cdp.cmd("Target.attachToTarget", {"targetId": t["targetId"], "flatten": True}))["sessionId"]
    await cdp.cmd("Runtime.enable", session=s)
    await cdp.cmd("Page.enable", session=s)
    await cdp.cmd("Log.enable", session=s)
    return s


async def evaluate(cdp, session, expr, await_promise=False):
    r = await cdp.cmd("Runtime.evaluate", {
        "expression": expr, "awaitPromise": await_promise, "returnByValue": True,
    }, session)
    if r.get("exceptionDetails"):
        raise RuntimeError(json.dumps(r["exceptionDetails"])[:500])
    return r.get("result", {}).get("value")


async def wait_for(cdp, session, expr, timeout=15.0, desc=""):
    """Poll a JS expression until truthy."""
    for _ in range(int(timeout / 0.5)):
        v = await evaluate(cdp, session, expr)
        if v:
            return v
        await asyncio.sleep(0.5)
    raise RuntimeError(f"timeout waiting for: {desc or expr}")


async def login_page(cdp, session, email, password="pass"):
    """Navigate to the app origin, seed + log in via fetch, leaving the session cookie set."""
    await cdp.cmd("Page.navigate", {"url": BASE + "/"}, session=session)
    await asyncio.sleep(2)
    return await evaluate(cdp, session, f"""
        fetch('/init/').catch(() => null).then(() =>
        fetch('/login', {{method:'POST', headers:{{'Content-Type':'application/json'}},
            body: JSON.stringify({{email:'{email}', password:'{password}', validity:'TRANSIENT'}})}})
          .then(r => r.status))""", await_promise=True)


# Benign in the sandboxed test environment: the external polyfill (no network),
# missing manifest/favicon in the dist dir, /init/ 500 on an already-seeded DB.
BENIGN = ("favicon", "polyfill", "manifest", "Manifest", "Failed to load resource")


def collect_errors(cdp):
    errors = []
    for e in cdp.events:
        m = e.get("method")
        p = e.get("params", {})
        if m == "Runtime.exceptionThrown":
            errors.append(str(p.get("exceptionDetails", {}))[:300])
        if m == "Runtime.consoleAPICalled" and p.get("type") == "error":
            txt = str([a.get("value") for a in p.get("args", [])])
            if "Failed to load resource" not in txt:
                errors.append(txt[:300])
        if m == "Log.entryAdded" and p.get("entry", {}).get("level") == "error":
            txt = p["entry"].get("text", "")
            if not any(b in txt for b in BENIGN):
                errors.append(txt[:300])
    return errors
