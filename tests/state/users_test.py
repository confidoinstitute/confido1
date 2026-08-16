#!/usr/bin/env python3
"""users topic narrowing + self slice shape: proper users keep the directory, guests
get UNAUTHORIZED (identity ships as embedded presentations, §5.3); self carries the
own user + projected session fields and NO raw session; members-topic guest branch."""
import asyncio
import json

from confido_test import *


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "payloads.requests.AddedExistingMember", "user": "debugmember", "role": "forecaster"}, admin)

    # --- Proper member: users directory; self shape ---
    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": ["users", "self", "questions"]}))
        d = by_topic(await recv_updates(ws))
        users = (d.get("users", {}).get("data")) or {}
        t.check("debugadmin" in users and users["debugadmin"].get("password") is None
                and users["debugadmin"].get("email") == ADMIN_EMAIL,
                "TEST-1", "proper member receives the users directory (password scrubbed)",
                json.dumps(users)[:300])
        # Member tier: administrative metadata stripped (lastLoginAt is colleague
        # activity data; createdAt pinned to epoch, emailVerified/active to defaults —
        # kotlinx omits default-valued fields, so they must be absent on the wire).
        ua = users.get("debugadmin", {})
        t.check(ua.get("lastLoginAt") is None and ua.get("createdAt") == "1970-01-01T00:00:00Z"
                and "emailVerified" not in ua and "active" not in ua,
                "TEST-1b", "member-tier directory strips administrative fields",
                json.dumps(ua)[:300])
        selfd = d.get("self", {}).get("data") or {}
        u = selfd.get("user") or {}
        t.check(u.get("_id") == "debugmember" and u.get("password") is None and "session" not in selfd,
                "TEST-2", "self carries own user, no raw session object", json.dumps(selfd)[:300])

        # Comment churn must not frame the users topic (wake set = user store only).
        post("/rooms/testroom/questions/add",
             {"_id": "", "name": "users-test q", "answerSpace": {"type": "binary"}}, admin)
        ups0 = await recv_updates(ws, 2.0)
        qstate = by_topic(ups0).get("questions", d.get("questions", {})).get("data", {})
        qid = next(q for q, qq in qstate.items() if qq["name"] == "users-test q")
        post(f"/questions/{qid}/comments/add", {"content": "hello", "attachPrediction": False}, admin)
        topics = sorted(set(u["topic"] for u in await recv_updates(ws, 2.0)))
        t.check("users" not in topics,
                "TEST-3", f"comment churn does not frame the users topic (topics: {topics})")

    # --- Self projected fields: presenterInfo flows via the session cell ---
    async with await connect_ws(admin) as ws:
        await ws.send(json.dumps({"sub": ["self", "users"]}))
        d0 = by_topic(await recv_updates(ws))
        au = (d0.get("users", {}).get("data") or {}).get("debugadmin", {})
        t.check(au.get("lastLoginAt") is not None,
                "TEST-3b", "admin tier keeps administrative fields (lastLoginAt)",
                json.dumps(au)[:300])
        post("/presenter/set", {"type": "empty"}, admin)
        ups = await recv_updates(ws)
        selfs = [u["data"] for u in ups if u["topic"] == "self"]
        # EmptyPV is a default value, so kotlinx omits the view field -> {} is correct.
        pi = selfs and (selfs[-1].get("data") or {}).get("presenterInfo")
        t.check(isinstance(pi, dict),
                "TEST-4", "presenterInfo projected onto self, updates via session cell",
                str(selfs[-1:] or "no self frame"))

    # --- Guest: users gated, members topic own-entry, self has own user ---
    link = json.loads(post("/rooms/testroom/invites/create",
                           {"description": "test guests", "role": "forecaster"}, admin))
    cj, op = session_opener()
    op.open(BASE + "/")
    req = urllib.request.Request(
        BASE + "/rooms/testroom/invite/accept_newuser",
        data=json.dumps({"inviteToken": link["token"], "userNick": "guesty",
                         "email": None, "validity": "TRANSIENT"}).encode(),
        headers={"Content-Type": "application/json"})
    op.open(req)
    guest = cookie_str(cj)

    async with await connect_ws(guest) as ws:
        await ws.send(json.dumps({"sub": ["users", "self", "room/testroom/members"]}))
        d = by_topic(await recv_updates(ws))
        uresp = d.get("users", {})
        t.check(uresp.get("type") == "err" and uresp.get("errType") == "UNAUTHORIZED",
                "TEST-5", "guest gets UNAUTHORIZED on the users directory", str(uresp))
        selfd = d.get("self", {}).get("data") or {}
        t.check((selfd.get("user") or {}).get("nick") == "guesty",
                "TEST-6", "guest self slice carries own user", json.dumps(selfd)[:300])
        mresp = d.get("room/testroom/members", {})
        entries = mresp.get("data") or []
        own = [e for e in entries if e["user"]["isMyself"]]
        t.check(mresp.get("type") == "ok" and len(entries) == 1 and own
                and not entries[0]["user"].get("userId") and not entries[0].get("email"),
                "TEST-7", "guest sees only their own member entry, presentation-only",
                json.dumps(mresp)[:400])

    t.finish()


asyncio.run(main())
