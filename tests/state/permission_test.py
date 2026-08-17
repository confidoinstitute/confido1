#!/usr/bin/env python3
"""Permission regressions (2026-08-16 review): invite-link revocation
(DISABLED_FULL) revokes class-gated topic access on live subscriptions and
re-enabling restores it; hidden questions read as nonexistent to the group-pred
family and stay out of the room group-pred payload keys; a gate error recovers
on the same subscription even when the payload comes back value-equal (the
error-state cutoff exemption); /questions/multidelete leaves no dangling refs
on the room; presentation follow is member-only."""
import asyncio
import json
import urllib.error
import urllib.request

from confido_test import *


def request(path, cookie, method="DELETE", body=None):
    req = urllib.request.Request(
        BASE + path, data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json", "Cookie": cookie}, method=method)
    return urllib.request.urlopen(req)


def post_status(path, body, cookie):
    try:
        post(path, body, cookie)
        return 200
    except urllib.error.HTTPError as e:
        return e.code


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)

    # --- Invite-link revocation: DISABLED_FULL must revoke, live ---
    rid = post("/rooms/add", {"name": "Perm revoke room", "description": "", "color": "RED", "icon": None},
               admin).strip('"')
    post(f"/rooms/{rid}/questions/add",
         {"_id": "", "name": "Revoke q", "answerSpace": {"type": "binary"}}, admin)
    link = json.loads(post(f"/rooms/{rid}/invites/create",
                           {"description": "revocable", "role": "forecaster"}, admin))
    cj, op = session_opener()
    op.open(BASE + "/")
    req = urllib.request.Request(
        BASE + f"/rooms/{rid}/invite/accept_newuser",
        data=json.dumps({"inviteToken": link["token"], "userNick": "revokee",
                         "email": None, "validity": "TRANSIENT"}).encode(),
        headers={"Content-Type": "application/json"})
    op.open(req)
    revokee = cookie_str(cj)

    async with await connect_ws(revokee) as ws:
        await ws.send(json.dumps({"sub": ["rooms", f"room/{rid}", f"room/{rid}/questions"]}))
        d = by_topic(await recv_updates(ws))
        t.check(d.get(f"room/{rid}", {}).get("type") == "ok"
                and rid in (d.get("rooms", {}).get("data") or {})
                and d.get(f"room/{rid}/questions", {}).get("type") == "ok",
                "TEST-1", "invitee sees the room topics before revocation",
                json.dumps({k: v.get("type") for k, v in d.items()}))

        link_off = dict(link, state="DISABLED_FULL")
        post(f"/rooms/{rid}/invites/edit", link_off, admin)
        d2 = by_topic(await recv_updates(ws))
        t.check(d2.get(f"room/{rid}", {}).get("type") == "err"
                and d2.get(f"room/{rid}/questions", {}).get("type") == "err"
                and "rooms" in d2 and rid not in (d2.get("rooms", {}).get("data") or {}),
                "TEST-2", "DISABLED_FULL revokes detail/questions/directory on live subscriptions",
                json.dumps({k: (v.get("type"), v.get("errType")) for k, v in d2.items()}))

        link_on = dict(link, state="ENABLED")
        post(f"/rooms/{rid}/invites/edit", link_on, admin)
        d3 = by_topic(await recv_updates(ws))
        t.check(d3.get(f"room/{rid}", {}).get("type") == "ok"
                and rid in (d3.get("rooms", {}).get("data") or {})
                and d3.get(f"room/{rid}/questions", {}).get("type") == "ok",
                "TEST-3", "re-enabling the link restores access on the same subscriptions",
                json.dumps({k: v.get("type") for k, v in d3.items()}))

    # --- Error-state recovery to a value-equal payload (remove/re-add member) ---
    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": ["room/testroom"]}))
        d = by_topic(await recv_updates(ws))
        t.check(d.get("room/testroom", {}).get("type") == "ok",
                "TEST-4", "member sees the testroom detail", str(d.get("room/testroom"))[:200])

        request("/rooms/testroom/members/debugmember", admin)
        ups = await recv_updates(ws)
        t.check(any(u["topic"] == "room/testroom" and u["data"].get("type") == "err" for u in ups),
                "TEST-5", "member removal closes the detail gate live",
                str([u["data"].get("type") for u in ups if u["topic"] == "room/testroom"]))

        post("/rooms/testroom/members/add",
             {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
        ups = await recv_updates(ws)
        t.check(any(u["topic"] == "room/testroom" and u["data"].get("type") == "ok" for u in ups),
                "TEST-6", "re-adding with the same role restores data on the same subscription "
                          "(recover-to-EQUAL payload: error-state cutoff exemption)",
                str([u["data"].get("type") for u in ups if u["topic"] == "room/testroom"]))

    # --- Hidden questions: group-pred family + hide/unhide recovery ---
    post("/rooms/testroom/questions/add",
         {"_id": "", "name": "Hide test q", "answerSpace": {"type": "binary"},
          "groupPredVisible": True}, admin)
    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": ["questions"]}))
        qstate = next(u["data"]["data"] for u in await recv_updates(ws) if u["topic"] == "questions")
        qid = next(q for q, qq in qstate.items() if qq["name"] == "Hide test q")
        post(f"/questions/{qid}/predict", {"type": "binary", "yesProb": 0.6}, admin)

        gp = f"question/{qid}/group_pred"
        await ws.send(json.dumps({"sub": [f"question/{qid}", gp, "room/testroom/group_pred"]}))
        d = by_topic(await recv_updates(ws))
        qdata = d.get(f"question/{qid}", {}).get("data") or {}
        room_gp = d.get("room/testroom/group_pred", {}).get("data") or {}
        t.check(qdata.get("name") == "Hide test q" and d.get(gp, {}).get("type") == "ok"
                and qid in room_gp,
                "TEST-7", "visible question: entity, group_pred and room group_pred key present",
                json.dumps({k: v.get("type") for k, v in d.items()}))

        hidden = dict(qdata, visible=False)
        post(f"/questions/{qid}/edit",
             {"type": "complete", "question": hidden}, admin)
        d2 = by_topic(await recv_updates(ws))
        t.check(d2.get(f"question/{qid}", {}).get("type") == "err"
                and d2.get(gp, {}).get("type") == "err"
                and d2.get(gp, {}).get("errType") == "NOT_FOUND"
                and "room/testroom/group_pred" in d2
                and qid not in (d2.get("room/testroom/group_pred", {}).get("data") or {}),
                "TEST-8", "hidden question reads as nonexistent: entity err, group_pred NOT_FOUND, "
                          "room group_pred key gone",
                json.dumps({k: (v.get("type"), v.get("errType")) for k, v in d2.items()}))

        # Write-side parity: while hidden, the question reads as nonexistent to
        # non-VIEW_HIDDEN members for writes too (predict, comment).
        t.check(post_status(f"/questions/{qid}/predict",
                            {"type": "binary", "yesProb": 0.5}, member) == 404,
                "TEST-8b", "predicting on a hidden question 404s for members")
        t.check(post_status(f"/questions/{qid}/comments/add",
                            {"content": "sneaky", "attachPrediction": False}, member) == 404,
                "TEST-8c", "commenting on a hidden question 404s for members")

        visible = dict(qdata, visible=True)
        post(f"/questions/{qid}/edit",
             {"type": "complete", "question": visible}, admin)
        d3 = by_topic(await recv_updates(ws))
        t.check(d3.get(f"question/{qid}", {}).get("type") == "ok"
                and d3.get(gp, {}).get("type") == "ok"
                and qid in (d3.get("room/testroom/group_pred", {}).get("data") or {}),
                "TEST-9", "unhiding recovers all three on the same subscriptions",
                json.dumps({k: v.get("type") for k, v in d3.items()}))

    # --- multidelete: no dangling question refs on the room ---
    ridm = post("/rooms/add", {"name": "Perm multidelete room", "description": "", "color": "RED", "icon": None},
                admin).strip('"')
    post(f"/rooms/{ridm}/questions/add", {"_id": "", "name": "MD q1", "answerSpace": {"type": "binary"}}, admin)
    post(f"/rooms/{ridm}/questions/add", {"_id": "", "name": "MD q2", "answerSpace": {"type": "binary"}}, admin)
    async with await connect_ws(admin) as ws:
        await ws.send(json.dumps({"sub": [f"room/{ridm}"]}))
        d = by_topic(await recv_updates(ws))
        qrefs = (d.get(f"room/{ridm}", {}).get("data") or {}).get("questions") or []
        t.check(len(qrefs) == 2, "TEST-10", "multidelete room seeded with two questions", str(qrefs))

        post("/questions/multidelete", {"questions": qrefs}, admin)
        d2 = by_topic(await recv_updates(ws))
        detail2 = d2.get(f"room/{ridm}", {})
        # kotlinx omits default values on the wire, so an empty questions list is absent.
        remaining = (detail2.get("data") or {}).get("questions") or []
        t.check(detail2.get("type") == "ok" and remaining == [],
                "TEST-11", "multidelete of same-room questions leaves no dangling refs",
                f"frame={detail2.get('type')} remaining={remaining}")

    # --- Presentation follow is member-only ---
    t.check(post_status(f"/rooms/{ridm}/presentation/follow", {}, member) in (401, 403),
            "TEST-12", "non-member cannot follow a room's presentation")
    t.check(post_status("/rooms/testroom/presentation/follow", {}, member) == 200,
            "TEST-13", "member can follow their room's presentation")
    post("/rooms/testroom/presentation/unfollow", {}, member)

    # --- Role change checks the target's CURRENT role too (owner not deposable) ---
    # debugmember creates a room (owner); a proper MEMBER joins via invite and is
    # promoted to moderator; the moderator must NOT be able to demote the owner.
    import time
    ridd = post("/rooms/add", {"name": "Perm depose room", "description": "", "color": "RED", "icon": None},
                member).strip('"')
    dlink = json.loads(post(f"/rooms/{ridd}/invites/create",
                            {"description": "m", "role": "forecaster", "targetUserType": "MEMBER"}, member))
    cj, op = session_opener()
    op.open(BASE + "/")
    req = urllib.request.Request(
        BASE + f"/rooms/{ridd}/invite/accept_newuser",
        data=json.dumps({"inviteToken": dlink["token"], "userNick": "mallory",
                         "email": f"mallory+{int(time.time())}@confido.example",
                         "validity": "TRANSIENT"}).encode(),
        headers={"Content-Type": "application/json"})
    op.open(req)
    mallory = cookie_str(cj)

    async with await connect_ws(mallory) as ws:
        await ws.send(json.dumps({"sub": ["self"]}))
        d = by_topic(await recv_updates(ws))
        mal_id = ((d.get("self", {}).get("data") or {}).get("user") or {}).get("_id")
    t.check(bool(mal_id), "TEST-14", "proper-member invitee created", str(mal_id))

    post(f"/rooms/{ridd}/members/add",
         {"type": "existingMember", "user": mal_id, "role": "moderator"}, member)
    status = post_status(f"/rooms/{ridd}/members/add",
                         {"type": "existingMember",
                          "user": "debugmember", "role": "viewer"}, mallory)
    async with await connect_ws(member) as ws:
        await ws.send(json.dumps({"sub": [f"room/{ridd}"]}))
        d = by_topic(await recv_updates(ws))
        own_role = ((d.get(f"room/{ridd}", {}).get("data") or {}).get("members") or [{}])[0].get("role")
    t.check(status != 200 and own_role == "owner",
            "TEST-15", "moderator cannot demote the room owner (current-role check)",
            f"status={status} owner_role={own_role}")

    # The new check must not break stepping down from your own role.
    t.check(post_status(f"/rooms/{ridd}/members/add",
                        {"type": "existingMember",
                         "user": mal_id, "role": "viewer"}, mallory) == 200,
            "TEST-16", "moderator can still demote themselves (step down)")

    t.finish()


asyncio.run(main())
