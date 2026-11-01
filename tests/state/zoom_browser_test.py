#!/usr/bin/env python3
"""Graph zoom UX on the question page (numeric input graph + binary slider):

1. Plain vertical wheel over a graph scrolls the page and never zooms;
   Ctrl+wheel zooms (trackpad pinch arrives as a ctrlKey wheel too).
2. Zoom pill: shows the visible range while zoomed, its ✕ resets to the full range.
3. Select-to-zoom (numeric): a plain mouse drag never zooms; after arming via the
   loupe a drag selects a range and zooms (one-shot), a short twitch is dropped and
   keeps the arming, Escape disarms.
4. Binary slider: a burst of wheel zooms settles instead of oscillating (the slider
   and its parent used to echo each other's lagging copies of the zoom state).
5. Comparison plot: inherits the group graph's zoom on open (and hands its own
   back on close), zooms with Ctrl+wheel, ✕ resets, Zoom to fit covers both states,
   and the hover readout still works under the pan/zoom gesture handlers.
Leaves screenshots of both zoomed widgets in $TMPDIR for eyeballing.
"""
import asyncio
import base64
import json
import os
import sys
import time

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE, MEMBER_EMAIL, login, post

NUM_Q = "Zoom UX numeric q"
BIN_Q = "Zoom UX binary q"
SMALL_Q = "Zoom UX small-range q"
CMP_Q = f"Zoom UX compare q {int(time.time())}"  # fresh per run: history must start empty
# The comparison plot: the parent of the svg drawing the dashed older-state curve.
CMP = "document.querySelector('.prediction-box path[stroke-dasharray=\"5 3\"]').closest('div')"
# Before a comparison opens, the timeline is the only wide svg in the group box.
TIMELINE = "[...document.querySelectorAll('.prediction-box svg')].find(e => e.getBoundingClientRect().width > 300)"
CLOSE_CMP = "[...document.querySelectorAll('.prediction-box button')].find(b => b.textContent === '✕' && !b.title)"
SMALL_SPACE = {"type": "numeric", "min": 0.0, "max": 1.0, "decimals": 3}
NUM_SPACE = {"type": "numeric", "min": 0.0, "max": 1000.0, "unit": "km"}

GRAPH = "document.querySelector('.prediction-box canvas')"
SLIDER = "document.querySelector('.prediction-slider-box')"
RESET = "document.querySelector('.prediction-box [title=\"Show full range\"]')"
# The loupe is the pill's first button (the ZoomIcon path starts with this).
LOUPE = "document.querySelector('.prediction-box path[d^=\"M21 21L15\"]').closest('button')"
HINT = "[...document.querySelectorAll('.prediction-box div')].some(d => d.textContent.startsWith('Drag to select'))"
RANGE = f"({RESET} ? {RESET}.previousElementSibling.textContent : null)"
TOAST = "[...document.querySelectorAll('div')].some(d => d.textContent.endsWith(' + scroll to zoom') && getComputedStyle(d).position === 'fixed')"
FIT = "[...document.querySelectorAll('.prediction-box button')].find(b => b.textContent === 'Zoom to fit')"
# Numeric axis labels: absolutely placed divs vertically centered in the label row.
NUM_TICKS = "[...document.querySelectorAll('.prediction-box div')].filter(d => d.style.top === '50%' && d.style.left).map(d => d.textContent)"
BIN_TICKS = "[...document.querySelectorAll('.prediction-box .slider-tick')].map(d => d.textContent)"


def axis_ok(ticks, lo, hi):
    """Labels parse as numbers, increase strictly and include both range ends
    (catches mangled labels like "3%" for 30%, which distinctness alone does not)."""
    try:
        vals = [float(t.rstrip("%")) for t in ticks]
    except ValueError:
        return False
    return (len(vals) >= 3 and all(a < b for a, b in zip(vals, vals[1:]))
            and lo in vals and hi in vals)


async def center(cdp, s, el):
    return await evaluate(cdp, s, f"""(() => {{ const r = {el}.getBoundingClientRect();
        return [r.left + r.width / 2, r.top + r.height / 2, r.width]; }})()""")


async def wheel(cdp, s, x, y, dy, ctrl=False):
    await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseWheel", "x": x, "y": y,
        "deltaX": 0, "deltaY": dy, "modifiers": 2 if ctrl else 0}, s)


async def drag(cdp, s, x0, x1, y):
    await cdp.cmd("Input.dispatchMouseEvent", {"type": "mousePressed", "x": x0, "y": y, "button": "left", "clickCount": 1}, s)
    for i in range(1, 9):
        await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": x0 + (x1 - x0) * i / 8, "y": y, "button": "left"}, s)
    await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseReleased", "x": x1, "y": y, "button": "left", "clickCount": 1}, s)


async def click(cdp, s, el):
    await evaluate(cdp, s, f"{el}.scrollIntoView({{block: 'center'}})")
    await asyncio.sleep(0.2)
    x, y, _ = await center(cdp, s, el)
    for t in ("mousePressed", "mouseReleased"):
        await cdp.cmd("Input.dispatchMouseEvent", {"type": t, "x": x, "y": y, "button": "left", "clickCount": 1}, s)


async def add_question(cdp, s, name, space, pred):
    st = await evaluate(cdp, s, f"""
        fetch('/rooms/testroom/questions/add', {{method:'POST', headers:{{'Content-Type':'application/json'}},
            body: JSON.stringify({{_id:'', name:{json.dumps(name)}, answerSpace:{json.dumps(space)}}})}})
        .then(r => r.status)""", await_promise=True)
    assert st == 200, f"questions/add -> {st}"
    await evaluate(cdp, s, "window.history.pushState({},'','/room/testroom'); window.dispatchEvent(new PopStateEvent('popstate'))")
    qid = await wait_for(cdp, s,
        "(Object.entries(((window.curTopics||{})['room/testroom/questions']||{}).data||{})"
        f".filter(([k,v])=>v.name==={json.dumps(name)}).map(([k])=>k)[0]) || false",
        desc=f"{name} in room questions slice")
    for path, body in ((f"/questions/{qid}/state", "OPEN"), (f"/questions/{qid}/predict", pred)):
        st = await evaluate(cdp, s, f"""
            fetch('{path}', {{method:'POST', headers:{{'Content-Type':'application/json'}},
                body: JSON.stringify({json.dumps(body)})}}).then(r => r.status)""", await_promise=True)
        assert st == 200, f"{path} -> {st}"
    return qid


async def compare_section(cdp, s, check):
    # Two narrow estimates from two users (one user's updates within a minute are
    # coalesced into one history point): the timeline needs two points.
    sp = NUM_SPACE
    cmp_q = await add_question(cdp, s, CMP_Q, sp,
        {"type": "truncatedNormal", "space": sp, "pseudoMean": 400.0, "pseudoStdev": 20.0})
    await asyncio.sleep(1.5)
    try:
        admin = login(ADMIN_EMAIL)
        try:  # standalone runs: members_test.py normally adds the membership
            post("/rooms/testroom/members/add",
                 {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
        except Exception:
            pass
        post(f"/questions/{cmp_q}/predict",
             {"type": "truncatedNormal", "space": sp, "pseudoMean": 430.0, "pseudoStdev": 25.0}, login(MEMBER_EMAIL))
        ok = True
    except Exception as e:
        print("member predict:", e)
        ok = False
    check(ok, "second (member) estimate for the comparison")
    await cdp.cmd("Page.navigate", {"url": f"{BASE}/room/testroom/questions/{cmp_q}"}, session=s)
    await wait_for(cdp, s, "[...document.querySelectorAll('button')].some(b => b.textContent === 'Group prediction')",
                   desc="group tab")
    await evaluate(cdp, s, "[...document.querySelectorAll('button')].find(b => b.textContent === 'Group prediction').click()")
    await wait_for(cdp, s, f"!!{GRAPH} && !!({TIMELINE})", desc="group graph + timeline")
    await asyncio.sleep(0.5)
    await evaluate(cdp, s, f"{GRAPH}.scrollIntoView({{block: 'start'}})")
    await asyncio.sleep(0.3)

    x, y, w = await center(cdp, s, GRAPH)
    for _ in range(3):
        await wheel(cdp, s, x, y, -100, ctrl=True)
    await asyncio.sleep(0.4)
    group_rng = await evaluate(cdp, s, RANGE)
    check(group_rng is not None, f"group graph zooms ({group_rng!r})")

    # Pinning a moment on the timeline opens "Selection vs now".
    await click_at_fraction(cdp, s, TIMELINE, 0.3)
    try:
        await wait_for(cdp, s, f"!!document.querySelector('.prediction-box path[stroke-dasharray=\"5 3\"]')",
                       timeout=5, desc="comparison plot")
    except RuntimeError:
        check(False, "pinning the timeline opens the comparison"); return
    await asyncio.sleep(0.4)
    rng = await evaluate(cdp, s, RANGE)
    check(rng == group_rng, f"comparison opens at the group graph's view ({rng!r} vs {group_rng!r})")

    # Hover readout, under the pan/zoom handlers (native listeners).
    cx, cy, cw = await center(cdp, s, CMP)
    await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": cx, "y": cy}, s)
    await asyncio.sleep(0.3)
    check(await evaluate(cdp, s, f"/\\d%? to \\d+(\\.\\d+)?% →/.test(({CMP}).textContent)"),
          "comparison hover readout shows P(below) old to new")

    # Under CSS zoom (as on the presenter screen) the cursor line must still sit
    # under the pointer: client px are visual, the chart draws in layout px.
    await evaluate(cdp, s, "document.querySelector('.prediction-box').style.zoom = '2.2'")
    await asyncio.sleep(0.3)
    await evaluate(cdp, s, f"({CMP}).scrollIntoView({{block: 'start'}})")
    await asyncio.sleep(0.3)
    px, py = await evaluate(cdp, s, f"""(() => {{ const r = ({CMP}).getBoundingClientRect();
        return [r.left + r.width * 0.4, r.top + r.height * 0.4]; }})()""")
    await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": px, "y": py}, s)
    await asyncio.sleep(0.3)
    line_x, layout_w = await evaluate(cdp, s, f"""(() => {{ const c = {CMP}, l = c.querySelector('svg line');
        return [l ? +l.getAttribute('x1') : null, c.offsetWidth]; }})()""")
    check(line_x is not None and abs(line_x - 0.4 * layout_w) < 2,
          f"cursor line follows the pointer under CSS zoom (x1={line_x}, expected {0.4 * layout_w:.1f})")
    await evaluate(cdp, s, "document.querySelector('.prediction-box').style.zoom = ''")
    await asyncio.sleep(0.3)

    await click(cdp, s, RESET)
    await asyncio.sleep(0.4)
    check(await evaluate(cdp, s, RANGE) is None, "✕ resets the comparison to the full range")
    cx, cy, cw = await center(cdp, s, CMP)
    await wheel(cdp, s, cx, cy, -150, ctrl=True)
    await asyncio.sleep(0.4)
    check(await evaluate(cdp, s, RANGE) is not None, "ctrl+wheel zooms the comparison")
    await click(cdp, s, RESET)
    await asyncio.sleep(0.3)

    await click(cdp, s, LOUPE)
    await asyncio.sleep(0.3)
    check(await evaluate(cdp, s, f"!!{FIT}"), "comparison offers Zoom to fit (spiky estimates)")
    await click(cdp, s, FIT)
    await asyncio.sleep(0.4)
    fit_rng = await evaluate(cdp, s, RANGE)
    lo, hi = (float(v.replace(" km", "")) for v in fit_rng.split(" – ")) if fit_rng else (0, 0)
    # Both states: 400±20 and 430±25 -> roughly 340..490 before padding.
    check(fit_rng is not None and lo < 360 and hi > 480 and hi - lo < 300,
          f"Zoom to fit covers both states ({fit_rng!r})")
    png = await cdp.cmd("Page.captureScreenshot", {"format": "png"}, s)
    with open(os.path.join(os.environ.get("TMPDIR", "/tmp"), "zoom_compare.png"), "wb") as f:
        f.write(base64.b64decode(png["data"]))

    await click(cdp, s, CLOSE_CMP)
    await wait_for(cdp, s, f"!!{GRAPH}", desc="group graph after closing the comparison")
    await asyncio.sleep(0.4)
    rng = await evaluate(cdp, s, RANGE)
    check(rng == fit_rng, f"closing hands the comparison's view back to the group graph ({rng!r})")


async def click_at_fraction(cdp, s, el, fx):
    r = await evaluate(cdp, s, f"""(() => {{ const r = ({el}).getBoundingClientRect();
        return [r.left + r.width * {fx}, r.top + r.height / 2]; }})()""")
    for t in ("mousePressed", "mouseReleased"):
        await cdp.cmd("Input.dispatchMouseEvent", {"type": t, "x": r[0], "y": r[1], "button": "left", "clickCount": 1}, s)


async def main():
    failed = False

    def check(cond, what):
        nonlocal failed
        print(("PASS: " if cond else "FAIL: ") + what)
        failed |= not cond

    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)
        # Short viewport, so the question page scrolls.
        await cdp.cmd("Emulation.setDeviceMetricsOverride",
                      {"width": 1200, "height": 500, "deviceScaleFactor": 1, "mobile": False}, session=s)
        if await login_page(cdp, s, ADMIN_EMAIL) != 200:
            print("FAIL: login"); sys.exit(1)
        num_q = await add_question(cdp, s, NUM_Q, NUM_SPACE,
            {"type": "truncatedNormal", "space": NUM_SPACE, "pseudoMean": 400.0, "pseudoStdev": 80.0})
        bin_q = await add_question(cdp, s, BIN_Q, {"type": "binary"}, {"type": "binary", "yesProb": 0.3})
        small_q = await add_question(cdp, s, SMALL_Q, SMALL_SPACE,
            {"type": "truncatedNormal", "space": SMALL_SPACE, "pseudoMean": 0.5, "pseudoStdev": 0.2})

        # --- Numeric input graph ---------------------------------------------------------
        await cdp.cmd("Page.navigate", {"url": f"{BASE}/room/testroom/questions/{num_q}"}, session=s)
        await wait_for(cdp, s, f"!!{GRAPH} && !!document.querySelector('.prediction-box path[d^=\"M21 21L15\"]')",
                       desc="numeric graph with zoom pill")
        # The hint is suppressed for good once the user has wheel-zoomed; start fresh.
        await evaluate(cdp, s, "localStorage.removeItem('zoomHintDone')")
        await cdp.cmd("Page.reload", {}, session=s)
        await wait_for(cdp, s, f"!!{GRAPH}", desc="numeric graph after reload")
        await asyncio.sleep(0.5)
        # Bring the graph near the top, leaving room to scroll further.
        await evaluate(cdp, s, f"window.scrollTo(0, {GRAPH}.getBoundingClientRect().top + window.scrollY - 150)")
        await asyncio.sleep(0.3)
        ticks = await evaluate(cdp, s, NUM_TICKS)
        check(axis_ok(ticks, 0, 1000), f"unzoomed numeric tick labels are intact ({ticks})")
        x, y, w = await center(cdp, s, GRAPH)
        # A page scroll that started elsewhere and carries on over the graph: no hint.
        await wheel(cdp, s, x, 60, 40)
        await wheel(cdp, s, x, y, 40)
        await asyncio.sleep(0.3)
        check(not await evaluate(cdp, s, TOAST), "no hint for a scroll gesture that started off the graph")
        await asyncio.sleep(0.6)
        await evaluate(cdp, s, f"window.scrollTo(0, {GRAPH}.getBoundingClientRect().top + window.scrollY - 150)")
        await asyncio.sleep(0.6)
        x, y, w = await center(cdp, s, GRAPH)
        sy0 = await evaluate(cdp, s, "window.scrollY")
        await wheel(cdp, s, x, y, 120)
        await asyncio.sleep(0.5)
        sy1 = await evaluate(cdp, s, "window.scrollY")
        check(sy1 > sy0, f"plain wheel over the graph scrolls the page ({sy0} -> {sy1})")
        check(await evaluate(cdp, s, RANGE) is None, "plain wheel does not zoom the numeric graph")
        check(await evaluate(cdp, s, TOAST), "a wheel gesture starting over the graph shows the ctrl+scroll hint")
        await asyncio.sleep(2.3)
        check(not await evaluate(cdp, s, TOAST), "the hint fades by itself")

        await evaluate(cdp, s, f"window.scrollTo(0, {GRAPH}.getBoundingClientRect().top + window.scrollY - 150)")
        await asyncio.sleep(0.3)
        x, y, w = await center(cdp, s, GRAPH)
        sy0 = await evaluate(cdp, s, "window.scrollY")
        for _ in range(3):
            await wheel(cdp, s, x, y, -100, ctrl=True)
        await asyncio.sleep(0.4)
        rng = await evaluate(cdp, s, RANGE)
        check(rng is not None and "km" in rng, f"ctrl+wheel zooms; pill shows the range ({rng!r})")
        check(await evaluate(cdp, s, "window.scrollY") == sy0, "ctrl+wheel does not scroll the page")
        zoom_dpr = await evaluate(cdp, s, "window.devicePixelRatio")
        check(zoom_dpr == 1, f"ctrl+wheel does not trigger browser page zoom (dpr {zoom_dpr})")

        await click(cdp, s, RESET)
        await asyncio.sleep(0.4)
        check(await evaluate(cdp, s, RANGE) is None, "✕ resets to the full range")

        # Ctrl+wheel over the input slider (below the graph) zooms the graph too.
        sx, sly, _ = await center(cdp, s, "document.querySelector('.prediction-box .slider-thumb-center')")
        await wheel(cdp, s, sx, sly, -150, ctrl=True)
        await asyncio.sleep(0.4)
        check(await evaluate(cdp, s, RANGE) is not None, "ctrl+wheel over the numeric slider zooms the graph")
        await click(cdp, s, RESET)
        await asyncio.sleep(0.4)

        # Zoom to fit: offered for an estimate occupying under half the space.
        await click(cdp, s, LOUPE)
        await asyncio.sleep(0.3)
        check(await evaluate(cdp, s, f"!!{FIT}"), "armed pill offers Zoom to fit")
        await click(cdp, s, FIT)
        await asyncio.sleep(0.4)
        rng = await evaluate(cdp, s, RANGE)
        check(rng is not None and not await evaluate(cdp, s, HINT), f"Zoom to fit zooms and disarms ({rng!r})")
        await click(cdp, s, LOUPE)
        await asyncio.sleep(0.3)
        check(not await evaluate(cdp, s, f"!!{FIT}"), "Zoom to fit is not offered when already fitted")
        await cdp.cmd("Input.dispatchKeyEvent", {"type": "keyDown", "key": "Escape", "code": "Escape",
                                                 "windowsVirtualKeyCode": 27}, s)
        await click(cdp, s, RESET)
        await asyncio.sleep(0.4)
        x, y, w = await center(cdp, s, GRAPH)

        # Plain drag (unarmed): pans, never zooms.
        x, y, w = await center(cdp, s, GRAPH)
        await drag(cdp, s, x - w * 0.3, x + w * 0.1, y)
        await asyncio.sleep(0.4)
        check(await evaluate(cdp, s, RANGE) is None, "unarmed drag does not zoom")

        # Arm, then a short twitch: dropped, stays armed.
        await click(cdp, s, LOUPE)
        await asyncio.sleep(0.3)
        check(await evaluate(cdp, s, HINT), "loupe arms selection (hint shown)")
        await drag(cdp, s, x, x + 10, y)
        await asyncio.sleep(0.3)
        check(await evaluate(cdp, s, RANGE) is None and await evaluate(cdp, s, HINT),
              "a 10px drag is dropped and keeps the arming")
        # Barely-zooming selection (~90% of the view): dropped.
        await drag(cdp, s, x - w * 0.46, x + w * 0.44, y)
        await asyncio.sleep(0.3)
        check(await evaluate(cdp, s, RANGE) is None, "a near-full-width selection is dropped")
        # Real selection: zooms and disarms.
        await drag(cdp, s, x - w * 0.2, x + w * 0.1, y)
        await asyncio.sleep(0.4)
        rng = await evaluate(cdp, s, RANGE)
        check(rng is not None, f"armed drag zooms to the selection ({rng!r})")
        check(not await evaluate(cdp, s, HINT), "selection is one-shot (disarmed after zooming)")
        png = await cdp.cmd("Page.captureScreenshot", {"format": "png"}, s)
        shot = os.path.join(os.environ.get("TMPDIR", "/tmp"), "zoom_numeric.png")
        with open(shot, "wb") as f:
            f.write(base64.b64decode(png["data"]))
        print(f"(screenshot: {shot})")

        await click(cdp, s, LOUPE)
        await asyncio.sleep(0.3)
        await cdp.cmd("Input.dispatchKeyEvent", {"type": "keyDown", "key": "Escape", "code": "Escape",
                                                 "windowsVirtualKeyCode": 27}, s)
        await asyncio.sleep(0.3)
        check(not await evaluate(cdp, s, HINT), "Escape disarms")

        # --- Tick labels on a small-range numeric space, fully zoomed ------------------------
        await cdp.cmd("Page.navigate", {"url": f"{BASE}/room/testroom/questions/{small_q}"}, session=s)
        await wait_for(cdp, s, f"!!{GRAPH}", desc="small-range graph")
        await asyncio.sleep(0.5)
        x, y, w = await center(cdp, s, GRAPH)
        for _ in range(12):
            await wheel(cdp, s, x, y, -100, ctrl=True)
        await asyncio.sleep(0.5)
        ticks = await evaluate(cdp, s, NUM_TICKS)
        check(len(ticks) >= 3 and len(set(ticks)) == len(ticks), f"zoomed numeric tick labels are distinct ({ticks})")

        # --- Comparison plot --------------------------------------------------------------
        await compare_section(cdp, s, check)

        # --- Binary slider -----------------------------------------------------------------
        await cdp.cmd("Page.navigate", {"url": f"{BASE}/room/testroom/questions/{bin_q}"}, session=s)
        await wait_for(cdp, s, f"!!{SLIDER}", desc="binary slider")
        await asyncio.sleep(0.5)
        ticks = await evaluate(cdp, s, BIN_TICKS)
        check(axis_ok(ticks, 0, 100) and len(ticks) <= 11, f"unzoomed binary tick labels are intact ({ticks})")
        await evaluate(cdp, s, f"window.scrollTo(0, {SLIDER}.getBoundingClientRect().top + window.scrollY - 150)")
        await asyncio.sleep(0.3)
        x, y, w = await center(cdp, s, SLIDER)
        await wheel(cdp, s, x - w * 0.4, y, 120)
        await asyncio.sleep(0.4)
        check(await evaluate(cdp, s, RANGE) is None, "plain wheel does not zoom the binary slider")

        await evaluate(cdp, s, f"window.scrollTo(0, {SLIDER}.getBoundingClientRect().top + window.scrollY - 150)")
        await asyncio.sleep(0.3)
        x, y, w = await center(cdp, s, SLIDER)
        # A fast burst, as a real wheel/pinch delivers it.
        for _ in range(15):
            await wheel(cdp, s, x - w * 0.4, y, -60, ctrl=True)
        await asyncio.sleep(0.6)
        samples = []
        for _ in range(6):
            samples.append(await evaluate(cdp, s, RANGE))
            await asyncio.sleep(0.25)
        check(samples[0] is not None, f"ctrl+wheel zooms the binary slider ({samples[0]!r})")
        check(len(set(samples)) == 1, f"zoom settles after a wheel burst (no oscillation: {samples})")
        ticks = await evaluate(cdp, s, BIN_TICKS)
        check(len(ticks) >= 3 and len(set(ticks)) == len(ticks), f"zoomed binary tick labels are distinct ({ticks})")
        png = await cdp.cmd("Page.captureScreenshot", {"format": "png"}, s)
        with open(os.path.join(os.environ.get("TMPDIR", "/tmp"), "zoom_binary.png"), "wb") as f:
            f.write(base64.b64decode(png["data"]))
        await click(cdp, s, RESET)
        await asyncio.sleep(0.4)
        check(await evaluate(cdp, s, RANGE) is None, "binary ✕ resets the zoom")

        errors = collect_errors(cdp)
        check(not errors, f"no page errors {errors[:3]}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    asyncio.run(main())
