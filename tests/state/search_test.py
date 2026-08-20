#!/usr/bin/env python3
"""The server-side workspace search (POST /search): matching over the viewer-censored
rooms/questions (member must not see hits from rooms they are not in), scope
narrowing, and description snippets with echoed terms for highlighting."""
import asyncio
import json

from confido_test import *


def search(query, cookie, scope=None):
    body = {"query": query}
    if scope is not None:
        body["scope"] = scope
    return json.loads(post("/search", body, cookie))


async def main():
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    t = Suite()

    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
    post("/rooms/testroom/questions/add",
         {"_id": "", "name": "Xyzzy public question",
          "description": "A description mentioning the plugh keyword somewhere inside.",
          "answerSpace": {"type": "binary"}}, admin)
    roomb = post("/rooms/add", {"name": "Xyzzy hidden room", "description": "", "color": "RED", "icon": None},
                 admin).strip('"')
    post(f"/rooms/{roomb}/questions/add",
         {"_id": "", "name": "Xyzzy secret question", "answerSpace": {"type": "binary"}}, admin)

    # Member: hits only from rooms they can see.
    r = search("Xyzzy", member)
    qnames = [h["question"]["name"] for h in r["questions"]]
    rnames = [h["room"] for h in r["rooms"]]
    t.check("Xyzzy public question" in qnames and "Xyzzy secret question" not in qnames,
            "TEST-1", "member's question hits censored to their rooms", json.dumps(qnames))
    t.check(roomb not in [rr for rr in rnames],
            "TEST-2", "member's room hits censored to their rooms", json.dumps(r["rooms"])[:200])

    # Admin sees both, and room hits resolve by ref.
    r2 = search("Xyzzy", admin)
    q2 = [h["question"]["name"] for h in r2["questions"]]
    t.check("Xyzzy public question" in q2 and "Xyzzy secret question" in q2
            and any(h["room"] == roomb for h in r2["rooms"]),
            "TEST-3", "admin sees hits from all their rooms (incl. room hit by ref)",
            json.dumps({"q": q2, "rooms": r2["rooms"]})[:300])

    # Description search produces a snippet and echoes terms for highlighting.
    r3 = search("plugh", member)
    hit = next((h for h in r3["questions"] if h["question"]["name"] == "Xyzzy public question"), None)
    t.check(r3["terms"] == ["plugh"] and hit is not None
            and "plugh" in (hit.get("descriptionSnippet") or {}).get("text", ""),
            "TEST-4", "description match returns a snippet and echoed terms", json.dumps(hit)[:300])

    # Scope narrowing: room-only scope returns no question hits.
    r4 = search("Xyzzy", member, scope=["ROOM"])
    t.check(r4["questions"] == [] , "TEST-5",
            "room-only scope suppresses question hits", json.dumps(r4)[:200])

    t.finish()


asyncio.run(main())
