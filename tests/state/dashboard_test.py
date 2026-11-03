#!/usr/bin/env python3
"""Dashboard section topics (dashboard/waiting|revisit|resolved|moderation):
per-user candidate lists with self-contained display data. Covers section
membership transitions (predict moves a question waiting->revisit, resolve moves
it to resolved), the moderation permission scoping (member sees an empty list,
moderator sees drafts and resolution-due candidates), candidate semantics
(resolveDue delivered even when in the future; cutoffs are the client's job)
and invalidation scoping (comment churn frames no dashboard topic)."""
import asyncio
import json

from confido_test import *

DASH_TOPICS = ["dashboard/waiting", "dashboard/revisit", "dashboard/resolved", "dashboard/moderation"]


def names(items):
    return [i["questionName"] for i in (items or [])]


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)

    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": DASH_TOPICS}))
        d = by_topic(await recv_updates(ws))
        t.check(all(d.get(x, {}).get("type") == "ok" for x in DASH_TOPICS),
                "TEST-1", "all four dashboard topics deliver initial frames",
                json.dumps({k: d.get(k, {}).get("type") for k in DASH_TOPICS}))
        t.check(d.get("dashboard/moderation", {}).get("data") == [],
                "TEST-2", "non-moderator's moderation section is empty",
                json.dumps(d.get("dashboard/moderation", {}).get("data"))[:200])

        # A new open question frames the member's waiting section, with the item
        # carrying its own display data (no workspace question map needed).
        post("/rooms/testroom/questions/add",
             {"_id": "dashq1", "name": "Dash waiting q", "answerSpace": {"type": "binary"}}, admin)
        d2 = by_topic(await recv_updates(ws))
        waiting = d2.get("dashboard/waiting", {}).get("data") or []
        item = next((i for i in waiting if i["questionName"] == "Dash waiting q"), None)
        # roomName is whatever earlier suite tests left it as (shared scratch DB) —
        # assert presence, not value.
        t.check(item is not None and item.get("roomName") and "roomColor" in item,
                "TEST-3", "new open question frames waiting with self-contained display data",
                json.dumps(item)[:300])

        # Predicting moves it waiting -> revisit (with the prediction timestamp).
        post("/questions/dashq1/predict", {"type": "binary", "yesProb": 0.7}, member)
        d3 = by_topic(await recv_updates(ws))
        w3 = names(d3.get("dashboard/waiting", {}).get("data"))
        r3 = d3.get("dashboard/revisit", {}).get("data") or []
        rev = next((i for i in r3 if i["questionName"] == "Dash waiting q"), None)
        t.check("Dash waiting q" not in w3 and rev is not None and rev.get("predictionTs"),
                "TEST-4", "own prediction moves the question from waiting to revisit (with ts)",
                f"waiting={w3} revisit={json.dumps(rev)[:200]}")

        # Resolving moves it to the resolved section with a formatted resolution.
        post("/questions/dashq1/edit",
             {"type": "complete", "question": {
                 "_id": "dashq1", "name": "Dash waiting q", "answerSpace": {"type": "binary"},
                 "resolution": {"type": "binary", "value": True}}}, admin)
        post("/questions/dashq1/state", "RESOLVED", admin)
        d4 = by_topic(await recv_updates(ws))
        res = next((i for i in (d4.get("dashboard/resolved", {}).get("data") or [])
                    if i["questionName"] == "Dash waiting q"), None)
        r4 = d4.get("dashboard/revisit", {})
        r4names = names(r4.get("data")) if r4 else names(d3.get("dashboard/revisit", {}).get("data"))
        t.check(res is not None and res.get("resolution") == "Yes" and res.get("resolvedAt")
                and "Dash waiting q" not in r4names,
                "TEST-5", "resolving moves the question to resolved with formatted resolution",
                json.dumps(res)[:300])

        # Comment churn must not frame any dashboard topic (wake set excludes counts).
        post("/rooms/testroom/questions/add",
             {"_id": "dashq2", "name": "Dash noise q", "answerSpace": {"type": "binary"}}, admin)
        await recv_updates(ws)  # drain the frames for dashq2 itself
        post("/questions/dashq2/comments/add", {"content": "noise", "attachPrediction": False}, admin)
        ups = await recv_updates(ws, 2.0)
        framed = sorted(set(u["topic"] for u in ups if u["data"].get("type") == "ok"))
        t.check(not any(x in framed for x in DASH_TOPICS),
                "TEST-6", f"comment churn frames no dashboard topic (framed: {framed})")

    # Moderator view: a draft and a scheduled-resolution candidate. The candidate is
    # delivered with resolveDue even though it lies in the future — overdue cutoffs
    # are the client's job (candidate-list semantics).
    post("/rooms/testroom/questions/add",
         {"_id": "dashdraft", "name": "Dash draft q", "answerSpace": {"type": "binary"},
          "visible": False}, admin)
    post("/rooms/testroom/questions/add",
         {"_id": "dashdue", "name": "Dash due q", "answerSpace": {"type": "binary"},
          "schedule": {"resolve": "2099-01-01T00:00:00Z"}}, admin)
    async with await connect_ws(admin) as ws:
        await ws.send(json.dumps({"sub": ["dashboard/moderation"]}))
        d = by_topic(await recv_updates(ws))
        mod = d.get("dashboard/moderation", {}).get("data") or []
        draft = next((i for i in mod if i["questionName"] == "Dash draft q"), None)
        due = next((i for i in mod if i["questionName"] == "Dash due q"), None)
        t.check(draft is not None and draft.get("moderation") == "DRAFT"
                and due is not None and due.get("moderation") == "RESOLUTION_DUE" and due.get("resolveDue"),
                "TEST-7", "moderator sees draft and future resolution-due candidates",
                json.dumps({"draft": draft, "due": due})[:400])

    t.finish()


asyncio.run(main())
