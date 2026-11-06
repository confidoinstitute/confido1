#!/usr/bin/env python3
"""Presentation audience modes (2026-11-05): the standard Ask / Reveal steps and
the presentation's audience mode. One question at a time hides the whole room
at Run (snapshotting it on the presentation, never re-snapshotting a pending
snapshot), Ask shows + opens with the group answer hidden, Reveal shows the
group answer and stops answering; Stop keeps what was covered or restores the
snapshot. Guided / free runs leave the questions alone (free also skips device
navigation). Audience steps whose selection is empty at Run are skipped."""
import asyncio
import json
import time

from confido_test import *


def ask(aid, sel, **kw):
    """An ask step; by default without its own reveal (tests pair it with reveal())."""
    return {"type": "action", "id": aid,
            "action": {"type": "ask", "selection": sel, "reveal": "LATER", **kw}}


def reveal(aid, sel, **kw):
    return {"type": "action", "id": aid, "action": {"type": "reveal", "selection": sel, **kw}}


def pick(*qids):
    return {"questions": list(qids)}


async def snapshot(ws, rid):
    """(questions by name, presentation, run state) as currently published."""
    await ws.send(json.dumps({"sub": [f"room/{rid}/questions", f"room/{rid}/presentation"]}))
    d = by_topic(await recv_updates(ws, timeout=1.5))
    await ws.send(json.dumps({"unsub": [f"room/{rid}/questions", f"room/{rid}/presentation"]}))
    await recv_updates(ws, timeout=0.5)
    qs = d.get(f"room/{rid}/questions", {}).get("data") or {}
    pd = d.get(f"room/{rid}/presentation", {}).get("data") or {}
    return {v["name"]: v for v in qs.values()}, pd.get("presentation") or {}, pd.get("runState")


def shown(q):
    return q.get("visible", True)


def answering(q):
    return q.get("open", True)


def gp_everyone(q):
    return q.get("groupPredVisible", False) and not q.get("groupPredRequirePrediction", False)


def gp_hidden(q):
    return not q.get("groupPredVisible", False)


async def main():
    admin = login(ADMIN_EMAIL)
    t = Suite()
    # Fresh room per run: one presentation per room, and the test must be re-runnable.
    rid = post("/rooms/add", {"name": f"Audience room {time.time():.0f}", "description": "",
                              "color": "BLUE", "icon": None}, admin).strip('"')
    for name, extra in [("A1", {}), ("A2", {"tags": ["t"]}), ("A3", {"visible": False, "open": False})]:
        post(f"/rooms/{rid}/questions/add",
             {"_id": "", "name": name, "answerSpace": {"type": "binary"}, **extra}, admin)

    async with await connect_ws(admin) as ws:
        qs, _, _ = await snapshot(ws, rid)
        q1, q2, q3 = (qs[n]["_id"] for n in ("A1", "A2", "A3"))
        post(f"/rooms/{rid}/presentation/create", {"name": "Talk", "audienceMode": "ONE_AT_A_TIME"}, admin)
        post(f"/rooms/{rid}/presentation/edit", {"items": [
            ask("a1", pick(q1)),
            reveal("r1", pick(q1)),
            ask("a2", {"tags": ["t"]}),
            ask("empty", {"tags": ["nobody-has-this"]}),
        ]}, admin)

        # --- Run start: everything hidden, snapshot stored, first Ask applied ---
        post(f"/rooms/{rid}/presentation/start", {}, admin)
        qs, pres, run = await snapshot(ws, rid)
        snap = pres.get("startSnapshot") or {}
        t.check(set(snap) == {q1, q2, q3} and snap[q1]["state"] == "OPEN" and snap[q3]["state"] == "DRAFT",
                "AUD-1", "Run snapshots every room question's state", json.dumps(snap))
        t.check(not shown(qs["A2"]) and not shown(qs["A3"]),
                "AUD-2", "one-at-a-time Run hides questions not yet asked")
        t.check(shown(qs["A1"]) and answering(qs["A1"]) and gp_hidden(qs["A1"]),
                "AUD-3", "Ask shows and opens the question, group answer hidden", json.dumps(qs["A1"])[:300])
        t.check(run and run.get("currentNavigateUrl", "").endswith(f"/questions/{q1}")
                and (run.get("currentView") or {}).get("type") == "question",
                "AUD-4", "Ask points devices at the question and the projector at its text", json.dumps(run)[:300])
        t.check(run and len(run.get("expandedItems") or []) == 3,
                "AUD-5", "an audience step whose selection is empty at Run is skipped")

        # --- Reveal: group answer to everyone, answering stops ---
        post(f"/rooms/{rid}/presentation/navigate", {"direction": "NEXT"}, admin)
        qs, _, run = await snapshot(ws, rid)
        t.check(shown(qs["A1"]) and not answering(qs["A1"]) and gp_everyone(qs["A1"]),
                "AUD-6", "Reveal closes the question and shows the group answer to everyone", json.dumps(qs["A1"])[:300])
        t.check((run.get("currentView") or {}).get("type") == "groupPred"
                and run.get("currentNavigateUrl", "").endswith(f"/questions/{q1}?show=group"),
                "AUD-7", "Reveal puts the group answer on the projector and opens it on devices")

        # --- Ask by tag ---
        post(f"/rooms/{rid}/presentation/navigate", {"direction": "NEXT"}, admin)
        qs, _, _ = await snapshot(ws, rid)
        t.check(shown(qs["A2"]) and answering(qs["A2"]) and not shown(qs["A3"]),
                "AUD-8", "Ask by tag opens the tagged question only")

        # --- A second Run without Stop (e.g. after a server restart) keeps the original snapshot ---
        post(f"/rooms/{rid}/presentation/start", {}, admin)
        _, pres2, _ = await snapshot(ws, rid)
        t.check(pres2.get("startSnapshot") == snap,
                "AUD-9", "a pending snapshot is never replaced by the hidden state")

        # --- Stop, keep what we covered ---
        post(f"/rooms/{rid}/presentation/navigate", {"direction": "GOTO", "index": 1}, admin)
        post(f"/rooms/{rid}/presentation/stop", {}, admin)
        qs, pres, _ = await snapshot(ws, rid)
        t.check(pres.get("startSnapshot") is None, "AUD-10", "Stop clears the snapshot")
        t.check(shown(qs["A1"]) and gp_everyone(qs["A1"]) and not shown(qs["A2"]) and not shown(qs["A3"]),
                "AUD-11", "keep what we covered: revealed stays visible with results, the rest stays hidden",
                json.dumps({n: (shown(q), gp_everyone(q)) for n, q in qs.items()}))

        # --- Stop, put the room back ---
        post(f"/rooms/{rid}/presentation/edit", {"endBehavior": "RESTORE"}, admin)
        before = {n: (shown(q), answering(q), gp_everyone(q)) for n, q in qs.items()}
        post(f"/rooms/{rid}/presentation/start", {}, admin)
        post(f"/rooms/{rid}/presentation/navigate", {"direction": "GOTO", "index": 2}, admin)
        post(f"/rooms/{rid}/presentation/stop", {}, admin)
        qs, _, _ = await snapshot(ws, rid)
        after = {n: (shown(q), answering(q), gp_everyone(q)) for n, q in qs.items()}
        t.check(before == after, "AUD-12", "put the room back restores every question as it was at Run",
                f"{before} != {after}")

        # --- Guided / free leave the questions alone ---
        post(f"/rooms/{rid}/presentation/edit", {"audienceMode": "GUIDED"}, admin)
        post(f"/rooms/{rid}/presentation/start", {}, admin)
        qs, pres, run = await snapshot(ws, rid)
        t.check({n: (shown(q), answering(q), gp_everyone(q)) for n, q in qs.items()} == before
                and pres.get("startSnapshot") is None,
                "AUD-13", "guided Run and Ask change no question")
        t.check(run.get("currentNavigateUrl", "").endswith(f"/questions/{q1}"),
                "AUD-14", "guided Ask still points devices at the question")
        post(f"/rooms/{rid}/presentation/stop", {}, admin)
        post(f"/rooms/{rid}/presentation/edit", {"audienceMode": "FREE"}, admin)
        post(f"/rooms/{rid}/presentation/start", {}, admin)
        _, _, run = await snapshot(ws, rid)
        t.check(run.get("currentNavigateUrl") is None and (run.get("currentView") or {}).get("type") == "question",
                "AUD-15", "free Ask moves only the projector", json.dumps(run)[:300])
        post(f"/rooms/{rid}/presentation/stop", {}, admin)

        # --- One step, several questions: the click sequence it unfolds into ---
        def clicks(run):
            out = []
            for it in run.get("expandedItems") or []:
                a = it.get("action") or {}
                out.append((a.get("type"), len(a.get("resolved") or [])))
            return out

        cases = [
            ({"oneByOne": True, "reveal": "RIGHT_AFTER"},
             [("ask", 1), ("reveal", 1), ("ask", 1), ("reveal", 1)], "one by one, reveal after each"),
            ({"oneByOne": True, "reveal": "AT_END"},
             [("ask", 1), ("ask", 1), ("reveal", 2)], "one by one, reveal all at the end"),
            ({"oneByOne": False, "reveal": "RIGHT_AFTER"},
             [("ask", 2), ("reveal", 2)], "all at once, then reveal"),
            ({"oneByOne": False, "reveal": "LATER"}, [("ask", 2)], "all at once, reveal later"),
        ]
        for i, (opts, want, what) in enumerate(cases):
            post(f"/rooms/{rid}/presentation/edit", {"items": [ask("m", pick(q1, q2), **opts)]}, admin)
            post(f"/rooms/{rid}/presentation/start", {}, admin)
            _, _, run = await snapshot(ws, rid)
            t.check(clicks(run) == want, f"AUD-16{'abcd'[i]}", f"ask several: {what}", json.dumps(clicks(run)))
            post(f"/rooms/{rid}/presentation/stop", {}, admin)
        views = [(it["action"].get("type"), (run.get("currentView") or {}).get("type"))
                 for it in (run.get("expandedItems") or [])[:1]]
        t.check(views == [("ask", "multiQuestion")], "AUD-17",
                "asking several at once shows them side by side on the projector", json.dumps(views))

    t.finish()


asyncio.run(main())
