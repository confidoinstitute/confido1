#!/usr/bin/env python3
"""Question topics: room/<id>/questions (entity slice, membership gate),
room/<id>/questions/counts (collection counts sibling) and question/<id> /
question/<id>/counts; invalidation scoping — count churn frames ONLY the counts
topics (never the entity slice or entity topic), entity churn frames the entity
topics and leaves the counts topics silent (equality cutoff), cross-room churn
frames neither."""
import asyncio
import json

from confido_test import *


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
    post("/rooms/testroom/questions/add",
         {"_id": "", "name": "Topic test q", "answerSpace": {"type": "binary"}}, admin)
    # A second, admin-only room with its own question (for gate + isolation tests).
    roomb = post("/rooms/add", {"name": "Qtopics room B", "description": "", "color": "RED", "icon": None},
                 admin).strip('"')
    post(f"/rooms/{roomb}/questions/add",
         {"_id": "", "name": "Secret q", "answerSpace": {"type": "binary"}}, admin)

    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": ["room/testroom/questions", "room/testroom/questions/counts"]}))
        d = by_topic(await recv_updates(ws))
        qs = d.get("room/testroom/questions", {}).get("data") or {}
        qid = next((k for k, v in qs.items() if v["name"] == "Topic test q"), None)
        t.check(qid is not None and "Secret q" not in [v["name"] for v in qs.values()],
                "TEST-1", "room questions slice: own questions (bare map), no foreign rooms",
                json.dumps(qs)[:300])
        room_counts = d.get("room/testroom/questions/counts", {}).get("data") or {}
        t.check(set(room_counts) <= set(qs) and qid in room_counts,
                "TEST-1b", "room counts sibling covers exactly the visible questions",
                json.dumps(room_counts)[:300])
        # Relative baseline: standalone reruns share the scratch DB with earlier runs.
        base = room_counts.get(qid, {}).get("comments", 0)

        # Single-question topic (+ its counts sibling, used by cutoff tests below)
        await ws.send(json.dumps({"sub": [f"question/{qid}", f"question/{qid}/counts"]}))
        d2 = by_topic(await recv_updates(ws))
        q = d2.get(f"question/{qid}", {}).get("data") or {}
        t.check(q.get("name") == "Topic test q",
                "TEST-2", "question/<id> delivers the censored question", str(q)[:200])

        # Gate: member is not in room B (slice and counts sibling alike)
        await ws.send(json.dumps({"sub": [f"room/{roomb}/questions", f"room/{roomb}/questions/counts"]}))
        d3 = by_topic(await recv_updates(ws, 2.0))
        resp = d3.get(f"room/{roomb}/questions", {})
        respc = d3.get(f"room/{roomb}/questions/counts", {})
        t.check(resp.get("type") == "err" and resp.get("errType") == "UNAUTHORIZED"
                and respc.get("type") == "err" and respc.get("errType") == "UNAUTHORIZED",
                "TEST-3", "room questions + counts gated for non-members", f"{resp} {respc}")

        # Cross-room isolation: churn in room B must not frame testroom's slices
        post(f"/rooms/{roomb}/questions/add",
             {"_id": "", "name": "Secret q 2", "answerSpace": {"type": "binary"}}, admin)
        ups = await recv_updates(ws, 2.0)
        topics = sorted(set(u["topic"] for u in ups if u["data"].get("type") == "ok"))
        t.check(not any(x in topics for x in
                        ("room/testroom/questions", "room/testroom/questions/counts",
                         f"question/{qid}", f"question/{qid}/counts")),
                "TEST-4", f"cross-room churn does not frame this room's slices (framed: {topics})")

        # Count churn frames ONLY the counts topics — the entity slice and the
        # entity topic stay silent (the collection-scale cutoff).
        post(f"/questions/{qid}/comments/add", {"content": "count bump", "attachPrediction": False}, admin)
        d5 = by_topic(await recv_updates(ws, 2.0))
        rc = d5.get("room/testroom/questions/counts", {}).get("data") or {}
        qc = d5.get(f"question/{qid}/counts", {}).get("data") or {}
        t.check("room/testroom/questions" not in d5 and f"question/{qid}" not in d5
                and rc.get(qid, {}).get("comments") == base + 1 and qc.get("comments") == base + 1,
                "TEST-5", f"count churn frames counts topics only (framed: {sorted(d5)})",
                f"room_counts={rc.get(qid)} q_counts={qc}")

        # Entity change frames both entity topics with the fresh value — and the
        # counts topics stay silent (recompute-but-equal, equality cutoff).
        edit = dict(q); edit["name"] = "Topic test q RENAMED"
        post(f"/questions/{qid}/edit",
             {"type": "complete", "question": edit}, admin)
        d6 = by_topic(await recv_updates(ws))
        room_q = (d6.get("room/testroom/questions", {}).get("data") or {}).get(qid, {})
        single_q = d6.get(f"question/{qid}", {}).get("data") or {}
        t.check(room_q.get("name") == "Topic test q RENAMED" and single_q.get("name") == "Topic test q RENAMED"
                and "room/testroom/questions/counts" not in d6 and f"question/{qid}/counts" not in d6,
                "TEST-6", "entity change frames entity topics only, with the fresh value",
                f"room={room_q.get('name')} single={single_q.get('name')} framed={sorted(d6)}")

        # Counts keep incrementing on the live subscriptions.
        post(f"/questions/{qid}/comments/add", {"content": "count bump 2", "attachPrediction": False}, admin)
        d7 = by_topic(await recv_updates(ws, 2.0))
        counts2 = d7.get(f"question/{qid}/counts", {}).get("data") or {}
        rc2 = d7.get("room/testroom/questions/counts", {}).get("data") or {}
        t.check(counts2.get("comments") == base + 2 and rc2.get(qid, {}).get("comments") == base + 2
                and f"question/{qid}" not in d7,
                "TEST-7", "second count bump increments both counts topics, entity silent",
                f"q_counts={counts2} room_counts={rc2.get(qid)} framed={sorted(d7)}")

    t.finish()


asyncio.run(main())
