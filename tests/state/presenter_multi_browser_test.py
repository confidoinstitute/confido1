#!/usr/bin/env python3
"""Multi-question presenter view (admin): a numeric question's tile renders the
in-house canvas group graph (NumericPredGraph) with its "group estimate" and
"correct answer" flags — the tile used to go through a Plotly wrapper that was
dropped together with the plotly.js dependency. Also sweeps for console errors
and leaves a screenshot in $TMPDIR for eyeballing the layout."""
import asyncio
import base64
import json
import os
import sys
import urllib.error

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE, connect_ws, login, post, recv_updates

QNAME = "Multi tile numeric q"
SPACE = {"type": "numeric", "min": 0.0, "max": 100.0}


async def seed():
    admin = login(ADMIN_EMAIL)
    ws = await connect_ws(admin)
    await ws.send(json.dumps({"sub": ["room/testroom/questions"]}))
    qstate = next(u["data"]["data"] for u in await recv_updates(ws)
                  if u["topic"] == "room/testroom/questions")
    qid = next((q for q, qq in qstate.items() if qq["name"] == QNAME), None)
    if qid is None:
        post("/rooms/testroom/questions/add",
             {"_id": "", "name": QNAME, "answerSpace": SPACE}, admin)
        qstate = next(u["data"]["data"] for u in await recv_updates(ws)
                      if u["topic"] == "room/testroom/questions")
        qid = next(q for q, qq in qstate.items() if qq["name"] == QNAME)
    q = qstate[qid]
    await ws.close()

    post(f"/questions/{qid}/predict",
         {"type": "truncatedNormal", "space": SPACE, "pseudoMean": 40.0, "pseudoStdev": 12.0}, admin)
    if q.get("resolution") is None:
        edit = dict(q)
        edit["resolution"] = {"type": "numeric", "space": SPACE, "value": 62.0}
        post(f"/questions/{qid}/edit", {"type": "complete", "question": edit}, admin)
    return qid


async def main():
    qid = await seed()

    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)
        # Presenter tiles are a min-400px grid; a desktop viewport keeps one tile wide.
        await cdp.cmd("Emulation.setDeviceMetricsOverride",
                      {"width": 1600, "height": 1000, "deviceScaleFactor": 1, "mobile": False},
                      session=s)

        code = await login_page(cdp, s, ADMIN_EMAIL)
        if code != 200:
            print("FAIL: login ->", code); sys.exit(1)

        # The presenter view lives in the browser's session, so set it from there.
        view = {"type": "multiQuestion", "questions": [qid],
                "showGroupPred": True, "showHistogram": False, "showResolution": True}
        st = await evaluate(cdp, s, f"""
            fetch('/presenter/set', {{method:'POST',
                headers:{{'Content-Type':'application/json'}}, body: JSON.stringify({json.dumps(view)})}})
              .then(r => r.status)""", await_promise=True)
        if st != 200:
            print("FAIL: presenter/set ->", st); sys.exit(1)

        await cdp.cmd("Page.navigate", {"url": BASE + "/presenter"}, session=s)
        await wait_for(cdp, s, f"document.body.innerText.includes({json.dumps(QNAME)})",
                       desc="multi-question tile mounted")
        # The numeric tile draws onto a canvas with a nonzero rect (no Plotly SVG
        # anymore) and overlays the group-mode flags.
        await wait_for(cdp, s, """(function() {
            const c = [...document.querySelectorAll('canvas')]
                .find(c => c.getBoundingClientRect().width > 50 && c.getBoundingClientRect().height > 20);
            return c ? true : false;
        })()""", desc="canvas group graph rendered in the tile")
        txt = await evaluate(cdp, s, "document.body.innerText")
        for needle in ("group estimate", "correct answer", "people answered"):
            if needle not in txt:
                print(f"FAIL: tile text lacks '{needle}'"); sys.exit(1)
        if await evaluate(cdp, s, "document.querySelectorAll('.js-plotly-plot, .plotly').length"):
            print("FAIL: Plotly DOM still present"); sys.exit(1)
        print("PASS: numeric tile renders the canvas group graph with estimate + resolution flags")

        shot = await cdp.cmd("Page.captureScreenshot", {"format": "png"}, session=s)
        path = os.path.join(os.environ.get("TMPDIR", "/tmp"), "confido_presenter_multi.png")
        with open(path, "wb") as f:
            f.write(base64.b64decode(shot["data"]))
        print("screenshot:", path)

        await cdp.drain(1.0)
        errors = collect_errors(cdp)
        if errors:
            print("Console/JS errors:")
            for e in errors:
                print(" -", e)
            print("OVERALL: FAIL"); sys.exit(1)
        print("no console errors")
        print("OVERALL: PASS")


asyncio.run(main())
