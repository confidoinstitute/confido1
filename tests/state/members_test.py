#!/usr/bin/env python3
"""room/<id>/members topic (§5.3): presentation-only payloads for ordinary members,
userId/email granted to MANAGE_MEMBERS viewers, live updates on membership churn,
payload-level dedup keeping renames off the topic, detail room own-membership-only."""
import asyncio
import json

from confido_test import *


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)

    # --- Member view: presentations only, no joinable identity ---
    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": ["room/testroom/members"]}))
        d = by_topic(await recv_updates(ws))
        entries = d.get("room/testroom/members", {}).get("data") or []
        leaked = [e for e in entries if e["user"].get("userId") or e.get("email")]
        own = [e for e in entries if e["user"]["isMyself"]]
        implied_admin = [e for e in entries if e.get("implied") and e.get("impliedFrom") == "Admin"]
        t.check(entries and not leaked and len(own) == 1 and own[0]["role"] == "forecaster"
                and own[0]["user"]["displayName"] == "debugmember" and implied_admin,
                "TEST-1", "member sees presentations only (no userId/email), own row + implied admin",
                json.dumps(entries)[:400])
        keys = [e["rowKey"] for e in entries]
        t.check(len(keys) == len(set(keys)) and all(keys), "TEST-2", "rowKeys present and unique", str(keys))

        await ws.send(json.dumps({"sub": ["room/testroom"]}))
        d2 = by_topic(await recv_updates(ws))
        detail = d2["room/testroom"]["data"]
        eff = detail.get("_effectiveMembers") or []
        mem = detail.get("members") or []
        t.check(len(eff) == 1 and eff[0]["user"] == "debugmember" and len(mem) == 1,
                "TEST-3", "detail room carries only the viewer's own membership", f"eff={eff} members={mem}")

        post("/rooms/testroom/edit",
             {"id": "testroom", "name": "Members test room", "description": "x", "icon": None, "color": "GRAY"},
             admin)
        topics = sorted(set(u["topic"] for u in await recv_updates(ws, 2.0)))
        t.check("room/testroom/members" not in topics and "room/testroom" in topics,
                "TEST-4", "rename does not frame the members topic", str(topics))

        post("/rooms/testroom/members/add",
             {"type": "existingMember", "user": "debugmember", "role": "moderator"}, admin)
        d5 = by_topic(await recv_updates(ws))
        own5 = [e for e in (d5.get("room/testroom/members", {}).get("data") or []) if e["user"]["isMyself"]]
        t.check(bool(own5) and own5[0]["role"] == "moderator",
                "TEST-5", "role change framed the members topic (own role now moderator)")
        post("/rooms/testroom/members/add",
             {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)

    # --- Moderator/admin view: userId + email granted ---
    async with await connect_ws(admin) as ws:
        await ws.send(json.dumps({"sub": ["room/testroom/members"]}))
        d = by_topic(await recv_updates(ws))
        entries = d.get("room/testroom/members", {}).get("data") or []
        target = [e for e in entries if e["user"].get("userId") == "debugmember"]
        t.check(bool(target) and target[0].get("email") == MEMBER_EMAIL
                and target[0]["user"]["displayName"] == "debugmember" and target[0]["isProperUser"],
                "TEST-6", "admin sees userId + email on member entries", json.dumps(entries)[:400])

    t.finish()


asyncio.run(main())
