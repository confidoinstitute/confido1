#!/usr/bin/env python3
"""room_groups × invite revocation: a DISABLED_FULL link in the GROUP room must
revoke the implied child-room memberships too — the link check happens at the
implication site against the parent (checked in the child it would fail open,
since the parent's link id resolves in no child room).

SKIPS (exit 0) when the room_groups extension is not enabled — the probe checks
whether the is_group extension-data key survived the round-trip onto the stored
room (unregistered keys are silently dropped on decode)."""
import asyncio
import json
import sys
import urllib.error
import urllib.request

from confido_test import *


async def main():
    admin = login(ADMIN_EMAIL)
    t = Suite()

    gid = post("/rooms/add",
               {"name": "Group revoke G", "description": "", "color": "RED", "icon": None,
                "extensionData": {"room_group.is_group": True}}, admin).strip('"')

    async with await connect_ws(admin) as ws:
        await ws.send(json.dumps({"sub": ["rooms"]}))
        rooms = next(u["data"]["data"] for u in await recv_updates(ws) if u["topic"] == "rooms")
        if not (rooms.get(gid, {}).get("extensionData") or {}).get("room_group.is_group"):
            print("SKIP: room_groups extension not enabled")
            return

    cid = post("/rooms/add",
               {"name": "Group revoke child", "description": "", "color": "RED", "icon": None,
                "extensionData": {"room_group.parent": gid}}, admin).strip('"')

    link = json.loads(post(f"/rooms/{gid}/invites/create",
                           {"description": "group invite", "role": "forecaster"}, admin))
    cj, op = session_opener()
    op.open(BASE + "/")
    req = urllib.request.Request(
        BASE + f"/rooms/{gid}/invite/accept_newuser",
        data=json.dumps({"inviteToken": link["token"], "userNick": "groupguest",
                         "email": None, "validity": "TRANSIENT"}).encode(),
        headers={"Content-Type": "application/json"})
    op.open(req)
    guest = cookie_str(cj)

    async with await connect_ws(guest) as ws:
        await ws.send(json.dumps({"sub": ["rooms", f"room/{cid}"]}))
        d = by_topic(await recv_updates(ws))
        t.check(d.get(f"room/{cid}", {}).get("type") == "ok"
                and cid in (d.get("rooms", {}).get("data") or {})
                and gid in (d.get("rooms", {}).get("data") or {}),
                "TEST-1", "group invitee sees the child room via implied membership",
                json.dumps({k: v.get("type") for k, v in d.items()}))

        post(f"/rooms/{gid}/invites/edit", dict(link, state="DISABLED_FULL"), admin)
        d2 = by_topic(await recv_updates(ws))
        rooms_now = d2.get("rooms", {}).get("data")
        t.check(d2.get(f"room/{cid}", {}).get("type") == "err"
                and rooms_now is not None and cid not in rooms_now and gid not in rooms_now,
                "TEST-2", "DISABLED_FULL on the GROUP link revokes the implied child access live",
                json.dumps({k: (v.get("type"), v.get("errType")) for k, v in d2.items()}))

        post(f"/rooms/{gid}/invites/edit", dict(link, state="ENABLED"), admin)
        d3 = by_topic(await recv_updates(ws))
        t.check(d3.get(f"room/{cid}", {}).get("type") == "ok"
                and cid in (d3.get("rooms", {}).get("data") or {}),
                "TEST-3", "re-enabling the group link restores child access live",
                json.dumps({k: v.get("type") for k, v in d3.items()}))

    t.finish()


asyncio.run(main())
