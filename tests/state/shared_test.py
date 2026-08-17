#!/usr/bin/env python3
"""Permission-class shared views (§4.3): same-class subscribers share one memoized
payload view — a payload computes/encodes once per transaction per (topic, class),
not per subscriber. Observed via the devmode /diag/topic_computes counters
(TopicStats bumps inside topicPayloadView.compute).

Note the counters count *encodes* (payload computations), which the §3.5 equality
cutoff legitimately skips when a recompute yields an equal payload — and registry
entries survive their subscribers (weakly), so a fresh subscription may be served
entirely from a still-valid entry left by an earlier connection with zero computes.
The robust invariants are therefore relative: a mutation that visibly changes the
payload costs exactly one compute per *subscribed class* however many subscribers
there are, and a second same-class subscriber never adds computes."""
import asyncio
import json

from confido_test import *


def computes(pattern):
    return get_json("/diag/topic_computes").get(pattern, 0)


def qnames(d, topic):
    return sorted(v["name"] for v in (d.get(topic, {}).get("data") or {}).values())


async def main():
    admin = login(ADMIN_EMAIL)
    # Two sessions of the same user = two subscribers in the same permission class.
    member1 = login(MEMBER_EMAIL)
    member2 = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
    # An admin-only room whose question the member must not see until joining (TEST-5).
    roomc = post("/rooms/add", {"name": "Shared room C", "description": "", "color": "RED", "icon": None},
                 admin).strip('"')
    post(f"/rooms/{roomc}/questions/add",
         {"_id": "", "name": "Shared secret q", "answerSpace": {"type": "binary"}}, admin)

    async with await connect_ws(member1) as ws1, await connect_ws(member2) as ws2, \
               await connect_ws(admin) as ws3:
        await ws1.send(json.dumps({"sub": ["questions", "room/testroom/questions"]}))
        d1 = by_topic(await recv_updates(ws1, 2.0))
        cmid_q = computes("questions")
        cmid_r = computes("room/{room}/questions")

        await ws2.send(json.dumps({"sub": ["questions", "room/testroom/questions"]}))
        d2 = by_topic(await recv_updates(ws2, 2.0))
        t.check(d1.get("questions", {}).get("type") == "ok" and d2.get("questions", {}).get("type") == "ok"
                and computes("questions") == cmid_q and computes("room/{room}/questions") == cmid_r,
                "TEST-1", "second same-class subscriber adds zero computes",
                f"q {cmid_q}->{computes('questions')} r {cmid_r}->{computes('room/{room}/questions')}")
        t.check(d1.get("questions", {}).get("data") == d2.get("questions", {}).get("data"),
                "TEST-2", "same-class subscribers receive identical payloads")

        # One visibly payload-changing mutation -> exactly one compute per pattern for
        # the (single) subscribed class, frames on both subscribers.
        c0q, c0r = computes("questions"), computes("room/{room}/questions")
        post("/rooms/testroom/questions/add",
             {"_id": "", "name": "Shared test q", "answerSpace": {"type": "binary"}}, admin)
        u1 = by_topic(await recv_updates(ws1, 2.0))
        u2 = by_topic(await recv_updates(ws2, 2.0))
        n1, n2 = qnames(u1, "questions"), qnames(u2, "questions")
        t.check("Shared test q" in n1 and "Shared test q" in n2
                and computes("questions") == c0q + 1 and computes("room/{room}/questions") == c0r + 1,
                "TEST-3", "mutation: one compute per topic for the class, frames on both subs",
                f"q {c0q}->{computes('questions')} r {c0r}->{computes('room/{room}/questions')}")

        # A different class subscribes: sees its own censoring; a further mutation now
        # costs one compute per subscribed class (2 for questions, 1 for the room
        # slice the admin does not subscribe).
        await ws3.send(json.dumps({"sub": ["questions"]}))
        d3 = by_topic(await recv_updates(ws3, 2.0))
        admin_names = qnames(d3, "questions")
        t.check("Shared secret q" in admin_names and "Shared secret q" not in n1,
                "TEST-4a", "different class sees its own censoring",
                f"admin={admin_names[:6]}...")
        c1q, c1r = computes("questions"), computes("room/{room}/questions")
        post("/rooms/testroom/questions/add",
             {"_id": "", "name": "Shared test q 2", "answerSpace": {"type": "binary"}}, admin)
        m1 = by_topic(await recv_updates(ws1, 2.0))
        m3 = by_topic(await recv_updates(ws3, 2.0))
        await recv_updates(ws2, 1.0)  # drain
        t.check("Shared test q 2" in qnames(m1, "questions") and "Shared test q 2" in qnames(m3, "questions")
                and computes("questions") == c1q + 2 and computes("room/{room}/questions") == c1r + 1,
                "TEST-4b", "mutation: one compute per subscribed class",
                f"q {c1q}->{computes('questions')} r {c1r}->{computes('room/{room}/questions')}")

        # A room-scoped topic for the projection test: subscribe a testroom question.
        qid = next(k for k, v in (m1.get("questions", {}).get("data") or {}).items()
                   if v["name"] == "Shared test q")
        await ws1.send(json.dumps({"sub": [f"question/{qid}"]}))
        dq = by_topic(await recv_updates(ws1, 2.0))
        t.check(dq.get(f"question/{qid}", {}).get("type") == "ok",
                "TEST-5a", "question topic subscribed for projection test")
        cq, cr = computes("question/{question}"), computes("room/{room}/questions")

        # Class change on live subscriptions: joining room C changes the member class,
        # both member subs re-select the new registry entry and see the new question.
        post(f"/rooms/{roomc}/members/add",
             {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
        j1 = by_topic(await recv_updates(ws1, 2.0))
        j2 = by_topic(await recv_updates(ws2, 2.0))
        t.check("Shared secret q" in qnames(j1, "questions") and "Shared secret q" in qnames(j2, "questions"),
                "TEST-5b", "class change re-selects: both live subs now see the joined room's question",
                f"j1={qnames(j1, 'questions')}")

        # Class-key projection: the join changed the member's *global* class, but the
        # testroom-scoped keys (question/<id>, room/<id>/questions) project it onto
        # rooms the join didn't touch — no re-selection, no recompute, no frames.
        t.check(f"question/{qid}" not in j1 and "room/testroom/questions" not in j1
                and computes("question/{question}") == cq and computes("room/{room}/questions") == cr,
                "TEST-6", "unrelated-room join: room-scoped shared topics neither recompute nor frame",
                f"framed={sorted(j1)} q {cq}->{computes('question/{question}')} r {cr}->{computes('room/{room}/questions')}")

        # --- Group-prediction topics: shared per (scoped class, answered flag) ---
        post("/rooms/testroom/questions/add",
             {"_id": "", "name": "Shared gp q", "answerSpace": {"type": "binary"},
              "groupPredVisible": True}, admin)
        m = by_topic(await recv_updates(ws1, 2.0))
        await recv_updates(ws2, 1.0)  # drain
        gq = next(k for k, v in (m.get("questions", {}).get("data") or {}).items()
                  if v["name"] == "Shared gp q")
        gp, rgp = f"question/{gq}/group_pred", "room/testroom/group_pred"
        await ws1.send(json.dumps({"sub": [gp, rgp]}))
        await ws2.send(json.dumps({"sub": [gp, rgp]}))
        await recv_updates(ws1, 2.0); await recv_updates(ws2, 2.0)
        c0gp = computes("question/{question}/group_pred")
        c0rgp = computes("room/{room}/group_pred")

        post(f"/questions/{gq}/predict", {"type": "binary", "yesProb": 0.7}, member1)
        g1 = by_topic(await recv_updates(ws1, 2.0))
        g2 = by_topic(await recv_updates(ws2, 2.0))
        t.check(g1.get(gp, {}).get("type") == "ok" and g1.get(gp) == g2.get(gp)
                and g1.get(rgp, {}).get("data", {}).get(gq) is not None
                and computes("question/{question}/group_pred") == c0gp + 1
                and computes("room/{room}/group_pred") == c0rgp + 1,
                "TEST-7", "group-pred topics: one compute per class serves both subscribers",
                f"gp {c0gp}->{computes('question/{question}/group_pred')} "
                f"rgp {c0rgp}->{computes('room/{room}/group_pred')}")

        # Outcome keys share across permission classes: the admin subscribing the
        # same question's group_pred lands on the members' VISIBLE entry — zero new
        # computes, identical payload.
        cgp = computes("question/{question}/group_pred")
        await ws3.send(json.dumps({"sub": [gp]}))
        g3 = by_topic(await recv_updates(ws3, 2.0))
        t.check(g3.get(gp) == g1.get(gp) and computes("question/{question}/group_pred") == cgp,
                "TEST-7b", f"outcome key shares across classes: admin sub adds zero computes ({cgp})")

        # Membership gate on the room group-pred topic (the fixed latent leak):
        # a room the member cannot see must yield UNAUTHORIZED, not aggregates.
        roomd = post("/rooms/add", {"name": "Shared room D", "description": "", "color": "RED", "icon": None},
                     admin).strip('"')
        await ws1.send(json.dumps({"sub": [f"room/{roomd}/group_pred"]}))
        dresp = by_topic(await recv_updates(ws1, 2.0)).get(f"room/{roomd}/group_pred", {})
        t.check(dresp.get("type") == "err" and dresp.get("errType") == "UNAUTHORIZED",
                "TEST-8", "room group-pred gated for non-members", str(dresp))

    t.finish()


asyncio.run(main())
