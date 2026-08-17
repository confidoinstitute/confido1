#!/usr/bin/env python3
"""Rooms peel: directory vs detail topics, membership stripping, detail gate recovery
on join, and renames staying isolated to the rooms topics."""
import asyncio
import json

from confido_test import *


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)

    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": ["questions", "rooms", "self"]}))
        d = by_topic(await recv_updates(ws))

        t.check(d.get("questions", {}).get("type") == "ok",
                "TEST-1", "questions slice delivered (monolith fully decomposed)",
                str(d.get("questions"))[:200])

        dirroom = d["rooms"]["data"]["testroom"]
        members = dirroom.get("members", [])
        eff = dirroom.get("_effectiveMembers") or []
        links = dirroom.get("inviteLinks", [])
        t.check(len(members) == 1 and members[0]["user"] == "debugmember" and len(eff) == 1 and not links,
                "TEST-2", "directory carries own membership only, no links",
                f"members={len(members)} eff={len(eff)} links={len(links)}")

        await ws.send(json.dumps({"sub": ["room/testroom"]}))
        d2 = by_topic(await recv_updates(ws))
        detail = d2["room/testroom"]["data"]
        deff = detail.get("_effectiveMembers") or []
        # Member lists moved to room/<id>/members; the detail room carries own membership only.
        t.check(len(deff) == 1 and deff[0]["user"] == "debugmember",
                "TEST-3", "detail carries only the viewer's own effective membership", str(deff))

        # Detail gate on a room the member cannot see; recovery on join
        newroom = post("/rooms/add", {"name": "Second room", "description": "", "color": "BLUE", "icon": None},
                       admin).strip('"')
        await ws.send(json.dumps({"sub": [f"room/{newroom}"]}))
        d3 = by_topic(await recv_updates(ws, 2.0))
        resp = d3.get(f"room/{newroom}", {})
        t.check(resp.get("type") == "err" and resp.get("errType") == "UNAUTHORIZED",
                "TEST-4", "detail gated for non-member", str(resp))

        post(f"/rooms/{newroom}/members/add",
             {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
        d4 = by_topic(await recv_updates(ws))
        got_detail = d4.get(f"room/{newroom}", {}).get("type") == "ok"
        got_dir = newroom in (d4.get("rooms", {}).get("data") or {})
        t.check(got_detail and got_dir,
                "TEST-5", "join unlocked detail and directory on live subscriptions",
                f"detail={got_detail} directory_gained_room={got_dir}")

        # A rename must update the rooms topics and leave questions/self silent
        post("/rooms/testroom/edit",
             {"id": "testroom", "name": "Peeled room", "description": "x", "icon": None, "color": "GRAY"}, admin)
        topics = sorted(set(u["topic"] for u in await recv_updates(ws, 2.0)))
        t.check("rooms" in topics and "room/testroom" in topics
                and not any(x in topics for x in ("questions", "self", "users")),
                "TEST-6", "rename only touches the rooms topics", str(topics))

    t.finish()


asyncio.run(main())
