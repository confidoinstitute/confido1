"""Shared plumbing for the state-sync protocol tests.

The tests talk to a scratch Confido server (see run_all.sh / README.md) over plain
HTTP + the multiplexed /state websocket, asserting on raw topic frames. They assume
a devmode server whose /init/ endpoint seeds debugadmin/debugmember (password
"pass") and testroom.
"""
import asyncio
import http.cookiejar
import json
import os
import urllib.request

BASE = os.environ.get("CONFIDO_TEST_BASE", "http://localhost:8099")
WS_URL = BASE.replace("http", "ws", 1) + "/state"

ADMIN_EMAIL = "admin@confido.example"
MEMBER_EMAIL = "member@confido.example"


def session_opener():
    cj = http.cookiejar.CookieJar()
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    return cj, op


def cookie_str(cj):
    return "; ".join(f"{c.name}={c.value}" for c in cj)


def login(email, password="pass"):
    """Seed the scratch DB if needed and log in; returns a Cookie header value."""
    cj, op = session_opener()
    try:
        op.open(BASE + "/init/")  # devmode seeding; 404s by design, 500s if already seeded
    except urllib.error.HTTPError:
        pass
    op.open(BASE + "/")
    req = urllib.request.Request(
        BASE + "/login",
        data=json.dumps({"email": email, "password": password, "validity": "TRANSIENT"}).encode(),
        headers={"Content-Type": "application/json"})
    op.open(req)
    return cookie_str(cj)


def post(path, body, cookie):
    req = urllib.request.Request(
        BASE + path, data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "Cookie": cookie}, method="POST")
    return urllib.request.urlopen(req).read().decode()


async def connect_ws(cookie):
    import websockets
    return await websockets.connect(WS_URL, additional_headers={"Cookie": cookie}, max_size=2**24)


async def recv_frames(ws, timeout=3.0):
    """Raw frames (list of update-lists) until [timeout] of silence."""
    frames = []
    try:
        while True:
            msg = await asyncio.wait_for(ws.recv(), timeout)
            frames.append(json.loads(msg)["updates"])
    except asyncio.TimeoutError:
        pass
    return frames


async def recv_updates(ws, timeout=3.0):
    """Flattened topic updates until [timeout] of silence."""
    return [u for frame in await recv_frames(ws, timeout) for u in frame]


def by_topic(ups):
    """Last update per topic (later frames win, like the client cache)."""
    return {u["topic"]: u["data"] for u in ups}


class Suite:
    """Tiny PASS/FAIL accounting: suite.check(cond, "TEST-1", "what it asserts")."""

    def __init__(self):
        self.ok = True

    def check(self, cond, name, what, detail=""):
        if cond:
            print(f"{name} PASS: {what}")
        else:
            print(f"{name} FAIL: {what} {detail}")
            self.ok = False
        return cond

    def finish(self):
        import sys
        print("OVERALL:", "PASS" if self.ok else "FAIL")
        sys.exit(0 if self.ok else 1)
