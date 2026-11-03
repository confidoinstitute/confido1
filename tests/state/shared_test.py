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
    # A hidden question: visible to the admin class (VIEW_HIDDEN_QUESTIONS), censored
    # out of the member class's slice (TEST-4a).
    post("/rooms/testroom/questions/add",
         {"_id": "", "name": "Shared hidden q", "answerSpace": {"type": "binary"},
          "visible": False}, admin)
    # An admin-only room whose question the member must not see until joining (TEST-5).
    roomc = post("/rooms/add", {"name": "Shared room C", "description": "", "color": "RED", "icon": None},
                 admin).strip('"')
    post(f"/rooms/{roomc}/questions/add",
         {"_id": "", "name": "Shared secret q", "answerSpace": {"type": "binary"}}, admin)

    async with await connect_ws(member1) as ws1, await connect_ws(member2) as ws2, \
               await connect_ws(admin) as ws3:
        QT = "room/testroom/questions"
        CT = "room/testroom/questions/counts"
        await ws1.send(json.dumps({"sub": [QT, CT]}))
        d1 = by_topic(await recv_updates(ws1, 2.0))
        cmid_q = computes("room/{room}/questions")
        cmid_r = computes("room/{room}/questions/counts")

        await ws2.send(json.dumps({"sub": [QT, CT]}))
        d2 = by_topic(await recv_updates(ws2, 2.0))
        t.check(d1.get(QT, {}).get("type") == "ok" and d2.get(QT, {}).get("type") == "ok"
                and computes("room/{room}/questions") == cmid_q
                and computes("room/{room}/questions/counts") == cmid_r,
                "TEST-1", "second same-class subscriber adds zero computes",
                f"q {cmid_q}->{computes('room/{room}/questions')} "
                f"c {cmid_r}->{computes('room/{room}/questions/counts')}")
        t.check(d1.get(QT, {}).get("data") == d2.get(QT, {}).get("data"),
                "TEST-2", "same-class subscribers receive identical payloads")

        # One visibly payload-changing mutation -> exactly one compute per pattern for
        # the (single) subscribed class, frames on both subscribers.
        c0q, c0r = computes("room/{room}/questions"), computes("room/{room}/questions/counts")
        post("/rooms/testroom/questions/add",
             {"_id": "", "name": "Shared test q", "answerSpace": {"type": "binary"}}, admin)
        u1 = by_topic(await recv_updates(ws1, 2.0))
        u2 = by_topic(await recv_updates(ws2, 2.0))
        n1, n2 = qnames(u1, QT), qnames(u2, QT)
        t.check("Shared test q" in n1 and "Shared test q" in n2
                and computes("room/{room}/questions") == c0q + 1
                and computes("room/{room}/questions/counts") == c0r + 1,
                "TEST-3", "mutation: one compute per topic for the class, frames on both subs",
                f"q {c0q}->{computes('room/{room}/questions')} "
                f"c {c0r}->{computes('room/{room}/questions/counts')}")

        # A different class subscribes the same room slice: sees its own censoring
        # (the hidden question); a further mutation now costs one compute per
        # subscribed class (2 for the slice, 1 for the counts sibling the admin
        # does not subscribe).
        await ws3.send(json.dumps({"sub": [QT]}))
        d3 = by_topic(await recv_updates(ws3, 2.0))
        admin_names = qnames(d3, QT)
        t.check("Shared hidden q" in admin_names and "Shared hidden q" not in n1,
                "TEST-4a", "different class sees its own censoring (hidden question)",
                f"admin={admin_names[:6]}...")
        c1q, c1r = computes("room/{room}/questions"), computes("room/{room}/questions/counts")
        post("/rooms/testroom/questions/add",
             {"_id": "", "name": "Shared test q 2", "answerSpace": {"type": "binary"}}, admin)
        m1 = by_topic(await recv_updates(ws1, 2.0))
        m3 = by_topic(await recv_updates(ws3, 2.0))
        await recv_updates(ws2, 1.0)  # drain
        t.check("Shared test q 2" in qnames(m1, QT) and "Shared test q 2" in qnames(m3, QT)
                and computes("room/{room}/questions") == c1q + 2
                and computes("room/{room}/questions/counts") == c1r + 1,
                "TEST-4b", "mutation: one compute per subscribed class",
                f"q {c1q}->{computes('room/{room}/questions')} "
                f"c {c1r}->{computes('room/{room}/questions/counts')}")

        # A room-scoped topic for the projection test: subscribe a testroom question,
        # and the admin-only room's slice (UNAUTHORIZED until the join below).
        qid = next(k for k, v in (m1.get(QT, {}).get("data") or {}).items()
                   if v["name"] == "Shared test q")
        await ws1.send(json.dumps({"sub": [f"question/{qid}", f"room/{roomc}/questions"]}))
        await ws2.send(json.dumps({"sub": [f"room/{roomc}/questions"]}))
        dq = by_topic(await recv_updates(ws1, 2.0))
        await recv_updates(ws2, 1.0)  # drain
        t.check(dq.get(f"question/{qid}", {}).get("type") == "ok"
                and dq.get(f"room/{roomc}/questions", {}).get("errType") == "UNAUTHORIZED",
                "TEST-5a", "question topic ok; foreign room slice gated", str(sorted(dq)))
        cq = computes("question/{question}")

        # Class change on live subscriptions: joining room C changes the member class;
        # both members' gated subs recover to the room's data on the SAME subscription.
        post(f"/rooms/{roomc}/members/add",
             {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
        j1 = by_topic(await recv_updates(ws1, 2.0))
        j2 = by_topic(await recv_updates(ws2, 2.0))
        t.check("Shared secret q" in qnames(j1, f"room/{roomc}/questions")
                and "Shared secret q" in qnames(j2, f"room/{roomc}/questions"),
                "TEST-5b", "class change re-selects: both gated subs recover the joined room's slice",
                f"j1={sorted(j1)}")

        # Class-key projection: the join changed the member's *global* class, but the
        # testroom-scoped keys (question/<id>, room/testroom/questions) project it onto
        # rooms the join didn't touch — no re-selection, no recompute, no frames.
        # (The room/{room}/questions pattern counter is shared with room C's recovery,
        # so the compute invariant is asserted on question/{question} and the
        # no-frame invariant on the testroom topics.)
        t.check(f"question/{qid}" not in j1 and QT not in j1 and CT not in j1
                and computes("question/{question}") == cq,
                "TEST-6", "unrelated-room join: testroom-scoped shared topics neither recompute nor frame",
                f"framed={sorted(j1)} q {cq}->{computes('question/{question}')}")

        # --- Group-prediction topics: shared per (scoped class, answered flag) ---
        post("/rooms/testroom/questions/add",
             {"_id": "", "name": "Shared gp q", "answerSpace": {"type": "binary"},
              "groupPredVisible": True}, admin)
        m = by_topic(await recv_updates(ws1, 2.0))
        await recv_updates(ws2, 1.0)  # drain
        gq = next(k for k, v in (m.get(QT, {}).get("data") or {}).items()
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
