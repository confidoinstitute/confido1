#!/usr/bin/env python3
"""Comment topics over resident partitions: one partition load serves all
subscribers and all subsequent changes (no per-subscriber refetch — the
load:questionComments diag counter stays flat across churn); comment payloads are
frame-consistent with the count folds (a comment add frames the comments topic and
question/<id>/counts in ONE frame); per-viewer censoring (likedByMe) still differs
per subscriber over the shared cache; edits/deletes propagate from the change
stream; unsub+resub within the grace period reuses the cache."""
import asyncio
import json
import urllib.request

from confido_test import *


def loads():
    return get_json("/diag/topic_computes").get("load:questionComments", 0)


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "payloads.requests.AddedExistingMember", "user": "debugmember", "role": "forecaster"}, admin)
    post("/rooms/testroom/questions/add",
         {"_id": "", "name": "Comments test q", "answerSpace": {"type": "binary"}}, admin)

    async with await connect_ws(admin) as wsa, await connect_ws(member) as wsm:
        await wsa.send(json.dumps({"sub": ["room/testroom/questions"]}))
        da = by_topic(await recv_updates(wsa, 2.0))
        qid = next(k for k, v in (da.get("room/testroom/questions", {}).get("data") or {}).items()
                   if v["name"] == "Comments test q")
        ctopic = f"question/{qid}/comments"

        c0 = loads()
        await wsa.send(json.dumps({"sub": [ctopic]}))
        await wsm.send(json.dumps({"sub": [ctopic, f"question/{qid}/counts"]}))
        da = by_topic(await recv_updates(wsa, 2.0))
        dm = by_topic(await recv_updates(wsm, 2.0))
        t.check(da.get(ctopic, {}).get("type") == "ok" and dm.get(ctopic, {}).get("type") == "ok"
                and loads() == c0 + 1,
                "TEST-1", f"one partition load serves both subscribers ({c0}->{loads()})")

        # A new comment reaches both subscribers from the change stream — no refetch.
        post(f"/questions/{qid}/comments/add", {"content": "hello cache", "attachPrediction": False}, admin)
        fa = await recv_frames(wsa, 2.0)
        fm = await recv_frames(wsm, 2.0)
        ua = by_topic([u for f in fa for u in f])
        um = by_topic([u for f in fm for u in f])
        contents_m = [c["content"] for c in (um.get(ctopic, {}).get("data") or [])]
        t.check("hello cache" in contents_m
                and "hello cache" in [c["content"] for c in (ua.get(ctopic, {}).get("data") or [])]
                and loads() == c0 + 1,
                "TEST-2", f"comment add propagates via cache, zero refetches ({loads() - c0} loads total)")

        # Frame consistency: the member's frame carrying the comment also carries the
        # bumped comment count — both are ViewTopics computed in one flush pass now.
        frame_with_comments = next((f for f in fm if any(u["topic"] == ctopic for u in f)), None)
        frame_topics = sorted(u["topic"] for u in (frame_with_comments or []))
        counts_in_frame = next((u["data"].get("data") or {} for u in (frame_with_comments or [])
                                if u["topic"] == f"question/{qid}/counts"), {})
        t.check(f"question/{qid}/counts" in frame_topics and counts_in_frame.get("comments", 0) >= 1,
                "TEST-3", f"comment and its count arrive in ONE frame (topics: {frame_topics})")

        # Per-viewer censoring over the shared cache: member likes admin's comment.
        cid = next(c["commentId"] for c in (um.get(ctopic, {}).get("data") or [])
                   if c["content"] == "hello cache")
        post(f"/questions/{qid}/comments/{cid}/like", True, member)
        la = by_topic(await recv_updates(wsa, 2.0)).get(ctopic, {}).get("data") or []
        lm = by_topic(await recv_updates(wsm, 2.0)).get(ctopic, {}).get("data") or []
        ca = next(c for c in la if c["commentId"] == cid)
        cm = next(c for c in lm if c["commentId"] == cid)
        # Note: kotlinx omits default-valued fields — absent likedByMe/likeCount = false/0.
        t.check(cm.get("likedByMe", False) is True and ca.get("likedByMe", False) is False
                and cm.get("likeCount", 0) == 1 and ca.get("likeCount", 0) == 1,
                "TEST-4", "likedByMe differs per viewer over the shared partition",
                f"admin={ca.get('likedByMe')}/{ca.get('likeCount')} member={cm.get('likedByMe')}/{cm.get('likeCount')}")

        # Edit propagates (typed ApiSpec route: the content is a JSON string).
        post(f"/questions/{qid}/comments/{cid}/edit", "hello cache EDITED", admin)
        em = by_topic(await recv_updates(wsm, 2.0)).get(ctopic, {}).get("data") or []
        t.check(any(c["content"] == "hello cache EDITED" for c in em) and loads() == c0 + 1,
                "TEST-5", "edit propagates from the change stream, still zero refetches")

        # Unsub both, resub within the grace period: the cache lingered, no new load.
        await wsa.send(json.dumps({"unsub": [ctopic]}))
        await wsm.send(json.dumps({"unsub": [ctopic]}))
        await asyncio.sleep(0.5)
        await wsm.send(json.dumps({"sub": [ctopic]}))
        rm = by_topic(await recv_updates(wsm, 2.0)).get(ctopic, {}).get("data") or []
        t.check(any(c["content"] == "hello cache EDITED" for c in rm) and loads() == c0 + 1,
                "TEST-6", f"resub within grace reuses the lingering cache ({c0}->{loads()})")

        # Delete propagates to the live (resubscribed) subscription.
        urllib.request.urlopen(urllib.request.Request(
            f"{BASE}/questions/{qid}/comments/{cid}", headers={"Cookie": admin}, method="DELETE"))
        dm2 = by_topic(await recv_updates(wsm, 2.0)).get(ctopic, {}).get("data")
        t.check(dm2 is not None and all(c["commentId"] != cid for c in dm2),
                "TEST-7", "delete propagates from the change stream", str(dm2))

    t.finish()


asyncio.run(main())
