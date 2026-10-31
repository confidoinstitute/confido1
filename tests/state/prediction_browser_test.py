#!/usr/bin/env python3
"""Prediction submission robustness on the question page (binary slider):

1. Stale completion: a slow send of an older commit must not cancel a newer commit
   made while it is in flight; sends stay one at a time, in commit order.
2. Leaving the page right after a commit (well inside the 1 s debounce) still saves it.
3. touchcancel mid-drag commits nothing, reverts the thumb, and leaves the slider usable.
"""
import asyncio
import json
import sys

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE

THUMB = "document.querySelector('.prediction-box .slider-thumb-center')"


async def next_event(cdp, method, timeout, seen):
    """The next `method` event not yet in `seen` (ids of consumed events), or None."""
    deadline = asyncio.get_event_loop().time() + timeout
    while True:
        for i, e in enumerate(cdp.events):
            if e.get("method") == method and i not in seen:
                seen.add(i)
                return e["params"]
        left = deadline - asyncio.get_event_loop().time()
        if left <= 0:
            return None
        await cdp.drain(min(left, 0.2))


async def thumb_center(cdp, s):
    return await evaluate(cdp, s, f"""(() => {{ const r = {THUMB}.getBoundingClientRect();
        return [r.left + r.width / 2, r.top + r.height / 2]; }})()""")


async def mouse_drag(cdp, s, dx):
    x, y = await thumb_center(cdp, s)
    await cdp.cmd("Input.dispatchMouseEvent", {"type": "mousePressed", "x": x, "y": y, "button": "left", "clickCount": 1}, s)
    for i in range(1, 6):
        await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": x + dx * i / 5, "y": y, "button": "left"}, s)
    await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseReleased", "x": x + dx, "y": y, "button": "left", "clickCount": 1}, s)


async def open_question(cdp, s, qid):
    await cdp.cmd("Page.navigate", {"url": f"{BASE}/room/testroom/questions/{qid}"}, session=s)
    await wait_for(cdp, s, f"!!{THUMB}", desc="prediction slider thumb")


async def main():
    failed = False

    def check(cond, what):
        nonlocal failed
        print(("PASS: " if cond else "FAIL: ") + what)
        failed |= not cond

    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)
        if await login_page(cdp, s, ADMIN_EMAIL) != 200:
            print("FAIL: login"); sys.exit(1)
        st = await evaluate(cdp, s, """
            fetch('/rooms/testroom/questions/add', {method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({_id:'', name:'Prediction robustness q', answerSpace:{type:'binary'}})})
            .then(r => r.status)""", await_promise=True)
        if st != 200:
            print("FAIL: questions/add ->", st); sys.exit(1)
        await evaluate(cdp, s, "window.history.pushState({},'','/room/testroom'); window.dispatchEvent(new PopStateEvent('popstate'))")
        qid = await wait_for(cdp, s,
            "(Object.entries(((window.curTopics||{})['room/testroom/questions']||{}).data||{})"
            ".filter(([k,v])=>v.name==='Prediction robustness q').map(([k])=>k)[0]) || false",
            desc="new question in room questions slice")
        st = await evaluate(cdp, s, f"""
            fetch('/questions/{qid}/state', {{method:'POST', headers:{{'Content-Type':'application/json'}},
                body: JSON.stringify('OPEN')}}).then(r => r.status)""", await_promise=True)
        st2 = await evaluate(cdp, s, f"""
            fetch('/questions/{qid}/predict', {{method:'POST', headers:{{'Content-Type':'application/json'}},
                body: JSON.stringify({{type:'binary', yesProb:0.5}})}}).then(r => r.status)""", await_promise=True)
        if st != 200 or st2 != 200:
            print("FAIL: open/seed prediction ->", st, st2); sys.exit(1)

        # --- 1. Stale completion + ordering --------------------------------------------
        await open_question(cdp, s, qid)
        await wait_for(cdp, s, f"""(() => {{ const p = ((window.curTopics.self||{{}}).data||{{}}).myPredictions;
            return p && p['{qid}'] && p['{qid}'].dist.yesProb === 0.5; }})()""", desc="seeded prediction")
        seen = set()
        await cdp.cmd("Fetch.enable", {"patterns": [{"urlPattern": "*/predict", "requestStage": "Request"}]}, s)

        async def held_then_newer(dx_x, dx_y, hold_after_y):
            """Commit X, hold its send (slow network), commit Y, release X after
            `hold_after_y` s. Returns (X request, Y request or None, Y sent concurrently)."""
            await mouse_drag(cdp, s, dx_x)
            x_req = await next_event(cdp, "Fetch.requestPaused", 3, seen)
            await mouse_drag(cdp, s, dx_y)
            early = await next_event(cdp, "Fetch.requestPaused", hold_after_y, seen)
            if x_req:
                await cdp.cmd("Fetch.continueRequest", {"requestId": x_req["requestId"]}, s)
            y_req = early or await next_event(cdp, "Fetch.requestPaused", 3, seen)
            if y_req:
                await cdp.cmd("Fetch.continueRequest", {"requestId": y_req["requestId"]}, s)
            return x_req, y_req, early is not None

        def prob(req):
            return json.loads(req["request"]["postData"])["yesProb"]

        async def server_holds(p):
            try:
                await wait_for(cdp, s, f"""(() => {{ const q = window.curTopics.self.data.myPredictions['{qid}'];
                    return q && q.dist.yesProb === {p}; }})()""", timeout=5)
                return True
            except RuntimeError:
                return False

        # a) X completes inside Y's debounce window: X's completion must not cancel Y.
        x_req, y_req, _ = await held_then_newer(80, -160, 0.3)
        check(x_req is not None, "first commit is sent after the debounce")
        check(y_req is not None, "a newer commit is not cancelled by an older send completing")
        check(y_req is not None and await server_holds(prob(y_req)), "server holds the newest commit")

        # b) X held past Y's debounce: Y waits for X (no concurrent send), then follows in order.
        x_req, y_req, concurrent = await held_then_newer(160, -80, 2.0)
        check(not concurrent, "newer commit waits for the send in flight (no concurrent send)")
        check(x_req is not None and y_req is not None and prob(y_req) < prob(x_req),
              "sends in commit order" + (f" (X={prob(x_req):.3f}, Y={prob(y_req):.3f})" if x_req and y_req else ""))
        check(y_req is not None and await server_holds(prob(y_req)), "server holds the newest commit after the wait")
        await cdp.cmd("Fetch.disable", {}, s)

        # --- 2. Leaving right after a commit --------------------------------------------
        # (Checked on the server's side: a keepalive send during unload is not reported
        # to the page's CDP session.)
        my_prob = f"((((window.curTopics||{{}}).self||{{}}).data||{{}}).myPredictions||{{}})['{qid}']"
        before = await evaluate(cdp, s, f"{my_prob}.dist.yesProb")
        await mouse_drag(cdp, s, 100)
        await asyncio.sleep(0.15)                                      # well inside the 1 s debounce
        await cdp.cmd("Page.navigate", {"url": "about:blank"}, session=s)
        await asyncio.sleep(1.5)
        await open_question(cdp, s, qid)
        try:
            after = await wait_for(cdp, s, f"{my_prob} && {my_prob}.dist.yesProb !== {before} && {my_prob}.dist.yesProb",
                                   timeout=5, desc="changed prediction")
        except RuntimeError:
            after = None
        check(after is not None and after > before, f"commit made just before leaving is saved ({before} -> {after})")
        await cdp.cmd("Network.enable", {}, s)

        # --- 3. touchcancel ---------------------------------------------------------------
        await cdp.cmd("Emulation.setTouchEmulationEnabled", {"enabled": True, "maxTouchPoints": 1}, s)
        n_before = sum(1 for e in cdp.events if e.get("method") == "Network.requestWillBeSent"
                       and e["params"]["request"]["url"].endswith("/predict"))
        x, y = await thumb_center(cdp, s)
        await cdp.cmd("Input.dispatchTouchEvent", {"type": "touchStart", "touchPoints": [{"x": x, "y": y, "id": 1}]}, s)
        for i in range(1, 6):
            await cdp.cmd("Input.dispatchTouchEvent", {"type": "touchMove", "touchPoints": [{"x": x - 20 * i, "y": y, "id": 1}]}, s)
        moved_x, _ = await thumb_center(cdp, s)
        await cdp.cmd("Input.dispatchTouchEvent", {"type": "touchCancel", "touchPoints": []}, s)
        await cdp.drain(2.0)
        after_x, _ = await thumb_center(cdp, s)
        n_after = sum(1 for e in cdp.events if e.get("method") == "Network.requestWillBeSent"
                      and e["params"]["request"]["url"].endswith("/predict"))
        check(abs(moved_x - x) > 30, f"touch drag moved the thumb ({x:.0f} -> {moved_x:.0f})")
        check(abs(after_x - x) < 2, f"touchcancel reverts the thumb ({after_x:.0f} vs {x:.0f})")
        check(n_after == n_before, "touchcancel commits nothing")
        await cdp.cmd("Emulation.setTouchEmulationEnabled", {"enabled": False}, s)
        await mouse_drag(cdp, s, 60)
        await cdp.drain(2.0)
        n_final = sum(1 for e in cdp.events if e.get("method") == "Network.requestWillBeSent"
                      and e["params"]["request"]["url"].endswith("/predict"))
        check(n_final == n_after + 1, "slider still works after a cancelled touch")

        errors = collect_errors(cdp)
        check(not errors, f"no page errors {errors[:3]}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    asyncio.run(main())
