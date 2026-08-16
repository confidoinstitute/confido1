#!/usr/bin/env python3
"""Topic gates + versioned-view cutoffs: a permission-gated topic yields a WSError
frame but stays live (no resubscribe needed); unrelated mutations do not re-send the
error (equality cutoff); satisfying the gate unlocks data on the same subscription."""
import asyncio
import json

from confido_test import *


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    # Setup: member joins testroom; an ANSWERED-gated binary question exists
    # (group prediction only visible after you predict yourself).
    post("/rooms/testroom/members/add",
         {"type": "payloads.requests.AddedExistingMember", "user": "debugmember", "role": "forecaster"}, admin)
    post("/rooms/testroom/questions/add",
         {"_id": "", "name": "Gate test q", "answerSpace": {"type": "binary"},
          "groupPredVisible": True, "groupPredRequirePrediction": True}, admin)

    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": ["questions"]}))
        ups = await recv_updates(ws)
        qstate = next(u["data"]["data"] for u in ups if u["topic"] == "questions")
        # Select our own question by name — other suites seed questions too.
        qid = next(q for q, qq in qstate.items() if qq["name"] == "Gate test q")

        gp = f"question/{qid}/group_pred"
        await ws.send(json.dumps({"sub": [gp]}))
        gpu = [u for u in await recv_updates(ws) if u["topic"] == gp]
        t.check(bool(gpu) and gpu[-1]["data"].get("type") == "err"
                and gpu[-1]["data"].get("errType") == "UNAUTHORIZED",
                "TEST-A", "gate closed -> WSError, subscription live", str(gpu)[:300])

        # An unrelated mutation must not re-send the error frame (error-state cutoff).
        post("/rooms/testroom/edit",
             {"id": "testroom", "name": "Gate renamed", "description": "x", "icon": None, "color": "GRAY"}, admin)
        ups = await recv_updates(ws, 2.0)
        t.check(not [u for u in ups if u["topic"] == gp],
                "TEST-B", f"error state cut off (post-rename updates: {sorted(set(u['topic'] for u in ups))})")

        # Predicting satisfies the gate; data must flow on the SAME subscription.
        post(f"/questions/{qid}/predict", {"type": "binary", "yesProb": 0.7}, member)
        gpu = [u["data"].get("type") for u in await recv_updates(ws) if u["topic"] == gp]
        t.check("ok" in gpu, "TEST-C", "predict unlocked the gate, data flowed", str(gpu))

    t.finish()


asyncio.run(main())
