#!/usr/bin/env python3
"""Question topics: room/<id>/questions (room slice + counts, membership gate) and
question/<id> (single censored question); invalidation scoping — count churn frames
the room slice but not the entity topic, cross-room churn frames neither."""
import asyncio
import json

from confido_test import *


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "payloads.requests.AddedExistingMember", "user": "debugmember", "role": "forecaster"}, admin)
    post("/rooms/testroom/questions/add",
         {"_id": "", "name": "Topic test q", "answerSpace": {"type": "binary"}}, admin)
    # A second, admin-only room with its own question (for gate + isolation tests).
    roomb = post("/rooms/add", {"name": "Qtopics room B", "description": "", "color": "RED", "icon": None},
                 admin).strip('"')
    post(f"/rooms/{roomb}/questions/add",
         {"_id": "", "name": "Secret q", "answerSpace": {"type": "binary"}}, admin)

    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": ["room/testroom/questions"]}))
        d = by_topic(await recv_updates(ws))
        state = d.get("room/testroom/questions", {}).get("data") or {}
        qs = state.get("questions", {})
        qid = next((k for k, v in qs.items() if v["name"] == "Topic test q"), None)
        t.check(qid is not None and "Secret q" not in [v["name"] for v in qs.values()]
                and set(state.get("predictionCount", {})) <= set(qs)
                and set(state.get("commentCount", {})) <= set(qs),
                "TEST-1", "room questions slice: own questions + counts, no foreign rooms",
                json.dumps(state)[:300])

        # Single-question topic
        await ws.send(json.dumps({"sub": [f"question/{qid}"]}))
        d2 = by_topic(await recv_updates(ws))
        q = d2.get(f"question/{qid}", {}).get("data") or {}
        t.check(q.get("name") == "Topic test q",
                "TEST-2", "question/<id> delivers the censored question", str(q)[:200])

        # Gate: member is not in room B
        await ws.send(json.dumps({"sub": [f"room/{roomb}/questions"]}))
        d3 = by_topic(await recv_updates(ws, 2.0))
        resp = d3.get(f"room/{roomb}/questions", {})
        t.check(resp.get("type") == "err" and resp.get("errType") == "UNAUTHORIZED",
                "TEST-3", "room questions gated for non-members", str(resp))

        # Cross-room isolation: churn in room B must not frame testroom's slices
        post(f"/rooms/{roomb}/questions/add",
             {"_id": "", "name": "Secret q 2", "answerSpace": {"type": "binary"}}, admin)
        ups = await recv_updates(ws, 2.0)
        topics = sorted(set(u["topic"] for u in ups if u["data"].get("type") == "ok"))
        t.check("room/testroom/questions" not in topics and f"question/{qid}" not in topics,
                "TEST-4", f"cross-room churn does not frame this room's slices (framed: {topics})")

        # Count churn frames the room slice, but NOT the entity topic (cutoff)
        post(f"/questions/{qid}/comments/add", {"content": "count bump", "attachPrediction": False}, admin)
        ups = await recv_updates(ws, 2.0)
        topics = sorted(set(u["topic"] for u in ups))
        got_room = "room/testroom/questions" in topics
        got_entity = f"question/{qid}" in topics
        t.check(got_room and not got_entity,
                "TEST-5", f"count churn frames room slice only (framed: {topics})")

        # Entity change frames both, with the fresh value
        edit = dict(q); edit["name"] = "Topic test q RENAMED"
        post(f"/questions/{qid}/edit",
             {"type": "payloads.requests.EditQuestionComplete", "question": edit}, admin)
        d6 = by_topic(await recv_updates(ws))
        room_q = (d6.get("room/testroom/questions", {}).get("data") or {}).get("questions", {}).get(qid, {})
        single_q = d6.get(f"question/{qid}", {}).get("data") or {}
        t.check(room_q.get("name") == "Topic test q RENAMED" and single_q.get("name") == "Topic test q RENAMED",
                "TEST-6", "entity change frames both topics with the fresh value",
                f"room={room_q.get('name')} single={single_q.get('name')}")

        # question/<id>/counts: the per-question counts sibling topic — count churn
        # frames it (that is its purpose), the entity topic still stays silent.
        await ws.send(json.dumps({"sub": [f"question/{qid}/counts"]}))
        d7 = by_topic(await recv_updates(ws))
        counts = d7.get(f"question/{qid}/counts", {}).get("data") or {}
        before = counts.get("comments", -1)
        t.check(before >= 1,
                "TEST-7a", "counts topic delivers current counts", str(counts))
        post(f"/questions/{qid}/comments/add", {"content": "count bump 2", "attachPrediction": False}, admin)
        d8 = by_topic(await recv_updates(ws, 2.0))
        counts2 = d8.get(f"question/{qid}/counts", {}).get("data") or {}
        t.check(counts2.get("comments") == before + 1 and f"question/{qid}" not in d8,
                "TEST-7b", "count churn frames the counts topic, entity topic silent",
                f"counts={counts2} framed={sorted(d8)}")

    t.finish()


asyncio.run(main())
