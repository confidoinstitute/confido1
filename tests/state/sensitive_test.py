#!/usr/bin/env python3
"""Sensitive questions: individual predictions are off-limits to EVERYONE (admins with
VIEW_INDIVIDUAL_PREDICTIONS included), aggregates (group_pred, histogram) stay hidden
until the anonymity threshold (SENSITIVE_MIN_PREDICTORS = 5) is reached, the histogram
uses coarse bins and never carries per-bin user lists, prediction-attached comments are
refused, and sensitivity cannot be revoked once answers exist."""
import asyncio
import json
import urllib.error
import urllib.request

from confido_test import *

THRESHOLD = 5  # keep in sync with SENSITIVE_MIN_PREDICTORS


def post_status(path, body, cookie):
    """Like post(), but returns the HTTP status instead of raising on 4xx."""
    try:
        post(path, body, cookie)
        return 200
    except urllib.error.HTTPError as e:
        return e.code


def get_status(path, cookie):
    req = urllib.request.Request(BASE + path, headers={"Cookie": cookie})
    try:
        return urllib.request.urlopen(req).status
    except urllib.error.HTTPError as e:
        return e.code


def make_guest(link_token, nick):
    """Accept the invite as a fresh transient guest; returns its Cookie header."""
    cj, op = session_opener()
    op.open(BASE + "/")
    req = urllib.request.Request(
        BASE + "/rooms/testroom/invite/accept_newuser",
        data=json.dumps({"inviteToken": link_token, "userNick": nick,
                         "email": None, "validity": "TRANSIENT"}).encode(),
        headers={"Content-Type": "application/json"})
    op.open(req)
    return cookie_str(cj)


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
    # EVERYONE group-pred visibility so the only thing gating aggregates is sensitivity.
    post("/rooms/testroom/questions/add",
         {"_id": "", "name": "Sensitive test q", "answerSpace": {"type": "binary"},
          "groupPredVisible": True, "sensitive": True}, admin)

    async with await connect_ws(admin) as ws:
        await ws.send(json.dumps({"sub": ["room/testroom/questions"]}))
        ups = await recv_updates(ws)
        qstate = next(u["data"]["data"] for u in ups if u["topic"] == "room/testroom/questions")
        qid = next(q for q, qq in qstate.items() if qq["name"] == "Sensitive test q")
        qjson = qstate[qid]

        # Two predictors: well below the threshold.
        post(f"/questions/{qid}/predict", {"type": "binary", "yesProb": 0.7}, admin)
        post(f"/questions/{qid}/predict", {"type": "binary", "yesProb": 0.3}, member)

        gp = f"question/{qid}/group_pred"
        hist = f"question/{qid}/histogram"
        await ws.send(json.dumps({"sub": [gp, hist]}))
        d = by_topic(await recv_updates(ws))
        t.check(d.get(gp, {}).get("type") == "err"
                and d[gp].get("errType") == "UNAUTHORIZED",
                "TEST-A", "group_pred hidden below threshold even for admin",
                str(d.get(gp))[:200])
        t.check(d.get(hist, {}).get("type") == "err",
                "TEST-B", "histogram hidden below threshold even for admin",
                str(d.get(hist))[:200])

        # Individual predictions: refused regardless of predictor count, admin included.
        t.check(post_status(f"/questions/{qid}/user_preds_at",
                            {"ts1": None, "ts2": None}, admin) == 403,
                "TEST-C", "user_preds_at (compare view) refused for admin")

        # CSV: individual export refused; group export refused below the threshold.
        t.check(get_status(f"/export.csv?questions={qid}&what=predictions&group=false"
                           "&history=LAST", admin) == 401,
                "TEST-D", "individual CSV export refused for admin")
        t.check(get_status(f"/export.csv?questions={qid}&what=predictions&group=true"
                           "&history=LAST", admin) == 401,
                "TEST-E", "group CSV export refused below threshold")

        # Prediction-attached comments are refused (the attachment would be an
        # individual prediction visible to comment readers).
        t.check(post_status(f"/questions/{qid}/comments/add",
                            {"content": "look at my prediction",
                             "attachPrediction": True}, member) == 400,
                "TEST-F", "attaching a prediction to a comment refused")

        # Un-marking as sensitive after answers exist is refused (server-side guard).
        unmarked = dict(qjson, sensitive=False)
        t.check(post_status(f"/questions/{qid}/edit",
                            {"type": "complete", "question": unmarked}, admin) == 400,
                "TEST-G", "cannot unmark sensitive once predictions exist")

        # Cross the threshold with transient guests; aggregates must unlock on the
        # SAME live subscriptions.
        link = json.loads(post("/rooms/testroom/invites/create",
                               {"description": "sensitive test guests",
                                "role": "forecaster"}, admin))
        for i in range(THRESHOLD - 2):
            guest = make_guest(link["token"], f"sens-guest-{i}")
            post(f"/questions/{qid}/predict",
                 {"type": "binary", "yesProb": 0.4 + 0.1 * i}, guest)

        d = by_topic(await recv_updates(ws))
        t.check(d.get(gp, {}).get("type") == "ok",
                "TEST-H", "group_pred unlocked at the threshold", str(d.get(gp))[:200])
        hd = d.get(hist, {})
        t.check(hd.get("type") == "ok",
                "TEST-I", "histogram unlocked at the threshold", str(hd)[:200])
        bins = (hd.get("data") or {}).get("bins") or []
        t.check(len(bins) == 5 and all(b.get("users") is None for b in bins),
                "TEST-J", "histogram uses 5 coarse bins and no per-bin user lists",
                f"bins={len(bins)} users={[b.get('users') for b in bins][:3]}")

        # Still no individual access above the threshold.
        t.check(post_status(f"/questions/{qid}/user_preds_at",
                            {"ts1": None, "ts2": None}, admin) == 403,
                "TEST-K", "user_preds_at still refused above threshold")

    t.finish()


asyncio.run(main())
