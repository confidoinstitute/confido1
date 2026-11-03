#!/usr/bin/env python3
"""Multiplexed-websocket core: batched subscriptions answered in ONE consistent frame,
the retired /state monolith topic, comment archive delivery, topic-scoped invalidation,
no-change dedup, and unknown-topic errors."""
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
        # One batched command with the root slices + a page topic: the initial
        # payloads of the whole set must arrive in ONE frame (flush-time recompute).
        subs = ["room/testroom/questions", "users", "rooms", "self", "room/testroom/presentation"]
        await ws.send(json.dumps({"sub": subs}))
        frames = await recv_frames(ws)
        frame_topics = [[u["topic"] for u in f] for f in frames]
        t.check(len(frames) == 1 and sorted(frame_topics[0]) == sorted(subs),
                "TEST-1", "all five initial payloads in one frame", str(frame_topics))

        # The "/state" monolith topic no longer exists (the ws URL keeps the name).
        await ws.send(json.dumps({"sub": ["/state"]}))
        d = by_topic(await recv_updates(ws, 2.0))
        t.check(d.get("/state", {}).get("type") == "err",
                "TEST-1b", "/state monolith topic no longer exists", str(d.get("/state")))

        # Comment archive topic delivers (gate + suspending fetch shape).
        await ws.send(json.dumps({"sub": ["room/testroom/comments"]}))
        d = by_topic(await recv_updates(ws))
        t.check(d.get("room/testroom/comments", {}).get("type") == "ok",
                "TEST-2", "comments topic delivers", str(d.get("room/testroom/comments"))[:200])

        # Semantic invalidation: a rename frames the rooms topics only.
        post("/rooms/testroom/edit",
             {"id": "testroom", "name": "WS renamed", "description": "x", "icon": None, "color": "GRAY"}, admin)
        topics = sorted(set(u["topic"] for u in await recv_updates(ws, 2.0)))
        t.check("rooms" in topics and not any(x in topics for x in ("room/testroom/questions", "users", "self")),
                "TEST-3", "rename isolated to rooms topics", str(topics))

        # A no-change mutation (re-adding a member with the same role) must be fully
        # deduped by the versioned-view cutoffs: zero frames.
        post("/rooms/testroom/members/add",
             {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
        ups = await recv_updates(ws, 2.0)
        t.check(not ups, "TEST-4", "no-change mutation fully deduped",
                str(sorted(set(u["topic"] for u in ups))))

        # Unknown topics error rather than silently pending.
        await ws.send(json.dumps({"sub": ["no/such/topic"]}))
        d = by_topic(await recv_updates(ws, 2.0))
        t.check(d.get("no/such/topic", {}).get("type") == "err",
                "TEST-5", "unknown topic errors", str(d.get("no/such/topic")))

    t.finish()


asyncio.run(main())
