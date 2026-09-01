#!/usr/bin/env python3
"""Presentation flow hardening (2026-08-30 polish): presentationEdit validates
items (cross-room question refs, unset refs, duplicate item ids, slide index
bounds) and canonicalizes the slide-order invariant (slides ascending, deduped,
non-slide items untouched); the run state keeps sticky per-step view overrides
(setview survives navigating away and back, revertview restores the script)."""
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


def make_pdf(pages):
    """Build a minimal well-formed PDF with `pages` blank pages."""
    objs = []
    kids = " ".join(f"{3 + i} 0 R" for i in range(pages))
    objs.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objs.append(f"<< /Type /Pages /Kids [{kids}] /Count {pages} >>".encode())
    for _ in range(pages):
        objs.append(b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 150] >>")
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, body in enumerate(objs, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n".encode()
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += (f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\n"
            f"startxref\n{xref}\n%%EOF\n").encode()
    return bytes(out)


def upload_pdf(rid, pdf, cookie):
    boundary = "confidotestboundary"
    body = (f"--{boundary}\r\n"
            "Content-Disposition: form-data; name=\"file\"; filename=\"t.pdf\"\r\n"
            "Content-Type: application/pdf\r\n\r\n").encode() + pdf + \
           f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        BASE + f"/rooms/{rid}/presentation/upload-slides", data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "Cookie": cookie},
        method="POST")
    return urllib.request.urlopen(req)


def note(nid, text=""):
    return {"type": "note", "id": nid, "text": text, "warning": False}


def slide(sid, idx):
    return {"type": "slide", "id": sid, "slideIndex": idx}


def set_state(aid, qid, state="OPEN"):
    return {"type": "action", "id": aid,
            "action": {"type": "setQuestionState", "question": qid, "newState": state}}


def set_view(aid, view):
    return {"type": "action", "id": aid, "action": {"type": "setPresenterView", "view": view}}


async def find_question(ws, rid, name):
    await ws.send(json.dumps({"sub": [f"room/{rid}/questions"]}))
    d = by_topic(await recv_updates(ws))
    qs = d.get(f"room/{rid}/questions", {}).get("data") or {}
    return next((k for k, v in qs.items() if v["name"] == name), None)


async def main():
    admin = login(ADMIN_EMAIL)
    t = Suite()

    # Two rooms selected by name (shared scratch DB; seed idempotently).
    def ensure_room(name):
        return post("/rooms/add", {"name": name, "description": "", "color": "RED", "icon": None},
                    admin).strip('"')

    async with await connect_ws(admin) as ws0:
        await ws0.send(json.dumps({"sub": ["rooms"]}))
        rooms = by_topic(await recv_updates(ws0)).get("rooms", {}).get("data") or {}
        ra = next((k for k, v in rooms.items() if v["name"] == "Pres flow room"), None) or \
            ensure_room("Pres flow room")
        rb = next((k for k, v in rooms.items() if v["name"] == "Pres foreign room"), None) or \
            ensure_room("Pres foreign room")

    async with await connect_ws(admin) as ws0:
        qa = await find_question(ws0, ra, "Pres q A")
        if qa is None:
            post(f"/rooms/{ra}/questions/add",
                 {"_id": "", "name": "Pres q A", "answerSpace": {"type": "binary"}}, admin)
        qb = await find_question(ws0, rb, "Pres q B")
        if qb is None:
            post(f"/rooms/{rb}/questions/add",
                 {"_id": "", "name": "Pres q B", "answerSpace": {"type": "binary"}}, admin)
    async with await connect_ws(admin) as ws0:
        qa = qa or await find_question(ws0, ra, "Pres q A")
        qb = qb or await find_question(ws0, rb, "Pres q B")

    if post_status(f"/rooms/{ra}/presentation/create", {"name": "T"}, admin) not in (200, 400):
        t.check(False, "TEST-0", "presentation create (or already exists)")

    # --- Edit validation ---
    t.check(post_status(f"/rooms/{ra}/presentation/edit",
                        {"items": [set_state("i1", qb)]}, admin) == 400,
            "TEST-1", "cross-room question ref in an action is rejected")
    t.check(post_status(f"/rooms/{ra}/presentation/edit",
                        {"items": [set_state("i1", "")]}, admin) == 400,
            "TEST-2", "unset (blank) question ref is rejected")
    t.check(post_status(f"/rooms/{ra}/presentation/edit",
                        {"items": [set_view("i1", {"type": "compare", "question": qb,
                                                   "tp1": {"type": "mark", "name": "m"}})]},
                        admin) == 400,
            "TEST-3", "cross-room question ref inside a presenter view is rejected")
    t.check(post_status(f"/rooms/{ra}/presentation/edit",
                        {"items": [note("dup"), note("dup")]}, admin) == 400,
            "TEST-4", "duplicate flow item ids are rejected")
    t.check(post_status(f"/rooms/{ra}/presentation/edit",
                        {"items": [slide("s1", 0)]}, admin) == 400,
            "TEST-5", "slide index out of range (no slides uploaded) is rejected")
    t.check(post_status(f"/rooms/{ra}/presentation/edit",
                        {"items": [note("n1"), set_state("a1", qa)]}, admin) == 200,
            "TEST-6", "valid items save")

    # --- Slide-order normalization (needs real slides) ---
    upload_pdf(ra, make_pdf(3), admin)
    status = post_status(f"/rooms/{ra}/presentation/edit",
                         {"items": [slide("s2", 2), note("n1"), slide("s0", 0), slide("s0b", 0)]},
                         admin)
    async with await connect_ws(admin) as ws:
        await ws.send(json.dumps({"sub": [f"room/{ra}/presentation"]}))
        d = by_topic(await recv_updates(ws))
        pres = (d.get(f"room/{ra}/presentation", {}).get("data") or {}).get("presentation") or {}
        items = pres.get("items") or []
        kinds = [(i.get("type"), i.get("slideIndex")) for i in items]
        t.check(status == 200 and kinds == [("slide", 0), ("note", None), ("slide", 2)],
                "TEST-7", "slides are re-sorted in place and deduped; the note keeps its slot",
                json.dumps(kinds))

        # Slide re-upload must bump the cache-busting version (URLs are otherwise
        # identical across a replace, leaving stale/broken thumbnails cached).
        v1 = pres.get("slidesVersion", 0)
        upload_pdf(ra, make_pdf(3), admin)
        d = by_topic(await recv_updates(ws))
        pres2 = (d.get(f"room/{ra}/presentation", {}).get("data") or {}).get("presentation") or {}
        t.check(v1 >= 1 and pres2.get("slidesVersion", 0) == v1 + 1,
                "TEST-7b", "slide re-upload bumps slidesVersion",
                f"v1={v1} v2={pres2.get('slidesVersion')}")

        # --- Run-state: sticky per-step view overrides ---
        post(f"/rooms/{ra}/presentation/edit",
             {"items": [set_view("v1", {"type": "question", "question": qa}), note("n2", "hello")]},
             admin)
        post(f"/rooms/{ra}/presentation/start", {}, admin)
        d = by_topic(await recv_updates(ws))
        rs = (d.get(f"room/{ra}/presentation", {}).get("data") or {}).get("runState") or {}
        t.check(rs.get("currentIndex", 0) == 0 and (rs.get("currentView") or {}).get("type") == "question",
                "TEST-8", "run starts on the scripted view", json.dumps(rs)[:300])

        post(f"/rooms/{ra}/presentation/setview",
             {"type": "groupPred", "question": qa}, admin)
        d = by_topic(await recv_updates(ws))
        rs = (d.get(f"room/{ra}/presentation", {}).get("data") or {}).get("runState") or {}
        t.check((rs.get("currentView") or {}).get("type") == "groupPred"
                and "v1" in (rs.get("viewOverrides") or {}),
                "TEST-9", "setview overrides the current step and records a per-step override",
                json.dumps(rs)[:300])

        post(f"/rooms/{ra}/presentation/navigate", {"direction": "NEXT", "index": None}, admin)
        post(f"/rooms/{ra}/presentation/navigate", {"direction": "PREV", "index": None}, admin)
        d = by_topic(await recv_updates(ws))
        rs = (d.get(f"room/{ra}/presentation", {}).get("data") or {}).get("runState") or {}
        t.check(rs.get("currentIndex", 0) == 0 and (rs.get("currentView") or {}).get("type") == "groupPred",
                "TEST-10", "the override is sticky: navigating away and back re-applies it",
                json.dumps(rs)[:300])

        post(f"/rooms/{ra}/presentation/revertview", {}, admin)
        d = by_topic(await recv_updates(ws))
        rs = (d.get(f"room/{ra}/presentation", {}).get("data") or {}).get("runState") or {}
        t.check((rs.get("currentView") or {}).get("type") == "question"
                and "v1" not in (rs.get("viewOverrides") or {}),
                "TEST-11", "revertview restores the scripted view and drops the override",
                json.dumps(rs)[:300])

        post(f"/rooms/{ra}/presentation/stop", {}, admin)

        # --- Foreach loops ---
        def foreach(fid, body, all=False, tags=None, questions=None):
            d = {"type": "forEach", "id": fid, "all": all, "body": body}
            if tags is not None:
                d["tags"] = tags
            if questions is not None:
                d["questions"] = questions
            return d

        t.check(post_status(f"/rooms/{ra}/presentation/edit",
                            {"items": [foreach("l1", [slide("ls", 0)], all=True)]}, admin) == 400,
                "TEST-12", "slides in a loop body are rejected")
        t.check(post_status(f"/rooms/{ra}/presentation/edit",
                            {"items": [foreach("l1", [foreach("l2", [])], all=True)]}, admin) == 400,
                "TEST-13", "nested loops are rejected")
        t.check(post_status(f"/rooms/{ra}/presentation/edit",
                            {"items": [set_state("i1", "~current~")]}, admin) == 400,
                "TEST-14", "the current-question sentinel outside a loop is rejected")

        # Expansion: one substituted body copy per question; steps inapplicable to
        # a question (binary histogram x numeric question) are skipped.
        qn = await find_question(ws, ra, "Pres q N")
        if qn is None:
            post(f"/rooms/{ra}/questions/add",
                 {"_id": "", "name": "Pres q N",
                  "answerSpace": {"type": "numeric", "min": 0.0, "max": 100.0}}, admin)
        body = [set_state("b1", "~current~", "OPEN"),
                set_view("b2", {"type": "binaryHistogram", "question": "~current~"})]
        t.check(post_status(f"/rooms/{ra}/presentation/edit",
                            {"items": [foreach("l1", body, all=True)]}, admin) == 200,
                "TEST-15", "a loop with sentinel-referencing body steps saves")
        post(f"/rooms/{ra}/presentation/start", {}, admin)
        d = by_topic(await recv_updates(ws))
        rs = (d.get(f"room/{ra}/presentation", {}).get("data") or {}).get("runState") or {}
        expanded = rs.get("expandedItems") or []
        blob = json.dumps(expanded)
        n_state = sum(1 for i in expanded
                      if i.get("type") == "action" and i["action"].get("type") == "setQuestionState")
        n_hist = sum(1 for i in expanded
                     if i.get("type") == "action" and i["action"].get("type") == "setPresenterView")
        ids = [i.get("id") for i in expanded]
        # Two questions (binary qa + numeric) -> 2 state changes; histogram only
        # for the binary one -> 1 view step; fresh unique ids; sentinel gone.
        t.check(n_state == 2 and n_hist == 1 and len(ids) == len(set(ids))
                and "~current~" not in blob,
                "TEST-16", "loop expands per question, skips inapplicable steps, substitutes the sentinel",
                blob[:400])
        post(f"/rooms/{ra}/presentation/stop", {}, admin)

        # Custom subset: explicit question list beats the tag; cross-room subsets rejected.
        t.check(post_status(f"/rooms/{ra}/presentation/edit",
                            {"items": [foreach("l1", [set_state("b1", "~current~", "OPEN")],
                                               questions=[qb])]}, admin) == 400,
                "TEST-17", "cross-room question in a loop subset is rejected")
        t.check(post_status(f"/rooms/{ra}/presentation/edit",
                            {"items": [foreach("l1", [set_state("b1", "~current~", "OPEN")],
                                               questions=[qa])]}, admin) == 200,
                "TEST-18", "custom-subset loop saves")
        post(f"/rooms/{ra}/presentation/start", {}, admin)
        d = by_topic(await recv_updates(ws))
        rs = (d.get(f"room/{ra}/presentation", {}).get("data") or {}).get("runState") or {}
        expanded = rs.get("expandedItems") or []
        subs = [i["action"].get("question") for i in expanded
                if i.get("type") == "action" and i["action"].get("type") == "setQuestionState"]
        t.check(subs == [qa],
                "TEST-19", "subset loop iterates exactly the selected questions",
                json.dumps(expanded)[:300])
        post(f"/rooms/{ra}/presentation/stop", {}, admin)

    t.finish()


asyncio.run(main())
