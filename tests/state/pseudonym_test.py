#!/usr/bin/env python3
"""Pseudonymous attribution: a PSEUDONYM room masks comment authors behind stable
aliases; piercing moderators (VIEW_REAL_IDENTITIES + moderatorAccess) get the real
identity as an annotation, never as the display name; the alias map is censored to
the viewer's own entry for non-piercing viewers and for EVERYONE once any sealed
pierceable policy exists in the room; aliases stay stable across membership churn;
weakening the room default under existing activity is refused (ratchet)."""
import asyncio
import json
import urllib.error
import urllib.request

from confido_test import *


def post_status(path, body, cookie):
    try:
        post(path, body, cookie)
        return 200
    except urllib.error.HTTPError as e:
        return e.code


def make_guest(rid, link_token, nick):
    cj, op = session_opener()
    op.open(BASE + "/")
    req = urllib.request.Request(
        BASE + f"/rooms/{rid}/invite/accept_newuser",
        data=json.dumps({"inviteToken": link_token, "userNick": nick,
                         "email": None, "validity": "TRANSIENT"}).encode(),
        headers={"Content-Type": "application/json"})
    op.open(req)
    return cookie_str(cj)


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    rid = json.loads(post("/rooms/add",
        {"name": "Pseudo room", "description": "", "color": "RED", "icon": None,
         "defaultAttribution": {"level": "PSEUDONYM"}}, admin))
    post(f"/rooms/{rid}/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
    post(f"/rooms/{rid}/questions/add",
         {"_id": "", "name": "Pseudo q", "answerSpace": {"type": "binary"},
          "groupPredVisible": True}, admin)

    rtopic = f"room/{rid}"
    qtopic = f"room/{rid}/questions"

    async with await connect_ws(admin) as ws:
        # Latest frame per topic, merged across all receive batches (a topic only
        # re-frames when its payload changes, so single-batch reads are racy).
        seen = {}
        async def drain():
            seen.update({t: f for t, f in by_topic(await recv_updates(ws)).items()
                         if isinstance(f, dict) and "data" in f})

        await ws.send(json.dumps({"sub": [rtopic, qtopic]}))
        await drain()
        qid = next(iter(seen[qtopic]["data"]))

        post(f"/questions/{qid}/comments/add",
             {"content": "member speaking", "attachPrediction": False}, member)
        post(f"/questions/{qid}/comments/add",
             {"content": "admin speaking", "attachPrediction": False}, admin)
        ctopic = f"question/{qid}/comments"
        await ws.send(json.dumps({"sub": [ctopic]}))
        await drain()

        assigns = (seen[rtopic]["data"] or {}).get("pseudonymAssignments") or {}
        t.check(len(assigns) == 2 and "debugmember" in assigns,
                "TEST-1", "piercing owner sees the full alias map", str(assigns))
        member_alias = assigns.get("debugmember")

        comments = seen.get(ctopic, {}).get("data") or []
        mc = next((c for c in comments if c["content"] == "member speaking"), {})
        mu = mc.get("user") or {}
        t.check(member_alias and mu.get("displayName") == member_alias,
                "TEST-2", "comment author displays the alias, not the name", str(mu))
        t.check(bool(mu.get("realIdentity")),
                "TEST-3", "piercing owner gets realIdentity annotation", str(mu))

    async with await connect_ws(member) as ws:
        seen = {}
        await ws.send(json.dumps({"sub": [rtopic, f"question/{qid}/comments"]}))
        seen.update(by_topic(await recv_updates(ws)))
        assigns_m = (seen[rtopic]["data"] or {}).get("pseudonymAssignments") or {}
        t.check(list(assigns_m.keys()) == ["debugmember"],
                "TEST-4", "non-piercing member sees only their own alias entry", str(assigns_m))
        comments = seen.get(f"question/{qid}/comments", {}).get("data") or []
        ac = next((c for c in comments if c["content"] == "admin speaking"), {})
        au = ac.get("user") or {}
        mc = next((c for c in comments if c["content"] == "member speaking"), {})
        t.check(au.get("displayName") and "debugadmin" not in au["displayName"]
                and not au.get("realIdentity"),
                "TEST-5", "member sees admin's alias with no realIdentity", str(au))
        t.check(mc.get("user", {}).get("isMyself") is True
                and mc["user"].get("displayName") == member_alias,
                "TEST-6", "own comment: isMyself with own alias", str(mc.get("user")))

    # Alias stability across membership churn: a new joiner extends the map,
    # existing assignments stay bit-identical.
    link = json.loads(post(f"/rooms/{rid}/invites/create",
                           {"description": "pseudo guests", "role": "forecaster"}, admin))
    make_guest(rid, link["token"], "pseudo-guest")

    async with await connect_ws(admin) as ws:
        seen = {}
        async def drain():
            seen.update({t: f for t, f in by_topic(await recv_updates(ws)).items()
                         if isinstance(f, dict) and "data" in f})

        await ws.send(json.dumps({"sub": [rtopic, qtopic]}))
        await drain()
        assigns2 = (seen[rtopic]["data"] or {}).get("pseudonymAssignments") or {}
        t.check(len(assigns2) == 3 and assigns2.get("debugmember") == member_alias,
                "TEST-7", "join tops up the map without touching existing aliases", str(assigns2))

        # A sealed pierceable policy anywhere in the room censors the map to
        # own-entry for EVERYONE and suppresses realIdentity on that question.
        post(f"/rooms/{rid}/questions/add",
             {"_id": "", "name": "Sealed q", "answerSpace": {"type": "binary"},
              "groupPredVisible": True,
              "attribution": {"level": "PSEUDONYM", "moderatorAccess": False}}, admin)
        await drain()
        qstate = seen[qtopic]["data"]
        sqid = next(q for q, qq in qstate.items() if qq["name"] == "Sealed q")

        post(f"/questions/{sqid}/comments/add",
             {"content": "sealed whisper", "attachPrediction": False}, member)
        await ws.send(json.dumps({"sub": [f"question/{sqid}/comments"]}))
        await drain()

        assigns3 = (seen[rtopic]["data"] or {}).get("pseudonymAssignments") or {}
        t.check(list(assigns3.keys()) == ["debugadmin"],
                "TEST-8", "sealed policy censors the map to own entry even for the owner", str(assigns3))
        sc = next((c for c in (seen.get(f"question/{sqid}/comments", {}).get("data") or [])
                   if c["content"] == "sealed whisper"), {})
        su = sc.get("user") or {}
        t.check(su.get("displayName") == member_alias and not su.get("realIdentity"),
                "TEST-9", "sealed question: alias shown, realIdentity withheld from owner", str(su))

    # Ratchet: weakening the room default with inheriting activity present is refused.
    t.check(post_status(f"/rooms/{rid}/edit",
        {"name": "Pseudo room", "description": "", "color": "RED", "icon": None,
         "defaultAttribution": {"level": "OPEN"}}, admin) == 400,
            "TEST-10", "cannot weaken room default attribution once activity exists")

    # Identity axis of the ratchet: AGGREGATE_ONLY hides all prediction data but
    # shows comment authors by name, so PSEUDONYM -> AGGREGATE_ONLY would unmask
    # the aliases on existing comments and must be refused too.
    async with await connect_ws(admin) as ws:
        await ws.send(json.dumps({"sub": [qtopic]}))
        seen = by_topic(await recv_updates(ws))
        qjson = seen[qtopic]["data"][qid]
    unmasking = dict(qjson, attribution={"level": "AGGREGATE_ONLY"})
    t.check(post_status(f"/questions/{qid}/edit",
                        {"type": "complete", "question": unmasking}, admin) == 400,
            "TEST-11", "pseudonymous question with comments cannot become aggregate-only (would unmask authors)")

    t.finish()


asyncio.run(main())
