#!/usr/bin/env python3
"""Nested-dialog stacking (admin): dialogs opened over other dialogs get their own
backdrop layer — open-order z-indices, a dim veil between stacked panels (0.5 page
dim + 0.25 inter-dialog dim), and a backdrop click dismissing only the topmost
dialog. Drives the user-admin edit dialog + its "User types" help dialog, which
historically relied on a temporaryHide workaround instead of stacking."""
import asyncio
import sys

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE

# Fixed-position direct children of <body> in the modal band: the dialog panels
# (portal Stacks, transparent, with children) and their backdrops (opaque black
# rgba, childless). Sorted by z-index, bottom first.
MODAL_LAYERS = """
    [...document.querySelectorAll('body > *')]
      .map(e => ({ z: +getComputedStyle(e).zIndex || 0,
                   bg: getComputedStyle(e).backgroundColor,
                   backdrop: e.children.length === 0,
                   help: (e.innerText || '').includes('three types of users') }))
      .filter(x => x.z >= 2000 && x.z < 2500 &&
                   (!x.backdrop || x.bg.startsWith('rgba(0, 0, 0')))
      .sort((a, b) => a.z - b.z)
"""


def alpha(bg):
    # "rgba(0, 0, 0, 0.25)" -> 0.25; "rgb(0, 0, 0)" -> 1.0
    return float(bg.split(",")[3].rstrip(") ")) if bg.count(",") == 3 else 1.0


async def main():
    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)
        # Desktop layout: in TABLET mode the overlay sidebar (z 2050) would show
        # up in the modal band and confuse the layer scan.
        await cdp.cmd("Emulation.setDeviceMetricsOverride",
                      {"width": 1400, "height": 900, "deviceScaleFactor": 1, "mobile": False}, session=s)

        code = await login_page(cdp, s, ADMIN_EMAIL)
        if code != 200:
            print("FAIL: login ->", code); sys.exit(1)

        await evaluate(cdp, s, "window.history.pushState({},'','/admin/users'); window.dispatchEvent(new PopStateEvent('popstate'))")
        await wait_for(cdp, s, "[...document.querySelectorAll('button')].some(b => b.innerText.includes('Add user'))",
                       desc="user admin page")

        # One dialog: panel above a single 0.5 page backdrop
        await evaluate(cdp, s, "[...document.querySelectorAll('button')].find(b => b.innerText.includes('Add user')).click()")
        await wait_for(cdp, s, "document.body.innerText.includes('New user')", desc="edit dialog")
        await asyncio.sleep(0.5)  # let the slide-in settle
        layers = await evaluate(cdp, s, MODAL_LAYERS)
        backdrops = [l for l in layers if l["backdrop"]]
        panels = [l for l in layers if not l["backdrop"]]
        if len(backdrops) != 1 or len(panels) != 1 or backdrops[0]["z"] >= panels[0]["z"]:
            print("FAIL: single-dialog layers:", layers); sys.exit(1)
        if alpha(backdrops[0]["bg"]) != 0.5:
            print("FAIL: page backdrop dim:", backdrops[0]); sys.exit(1)
        print("PASS: single dialog = one 0.5 backdrop below the panel")

        # Second dialog on top: opened later but rendered EARLIER in the tree
        # (useDialog before useEditDialog in UserAdmin), so stacking must follow
        # open order, not portal DOM order.
        await evaluate(cdp, s, """
            [...document.querySelectorAll('span')]
              .find(sp => sp.textContent.trim().startsWith('User type') && sp.querySelector('button'))
              .querySelector('button').click()""")
        await wait_for(cdp, s, "document.body.innerText.includes('three types of users')",
                       desc="type-help dialog over edit dialog")
        await asyncio.sleep(0.5)
        layers = await evaluate(cdp, s, MODAL_LAYERS)
        backdrops = [l for l in layers if l["backdrop"]]
        panels = [l for l in layers if not l["backdrop"]]
        if len(backdrops) != 2 or len(panels) != 2:
            print("FAIL: nested-dialog layers:", layers); sys.exit(1)
        # Interleaving bottom-up: page backdrop < edit panel < veil < help panel
        seq = [l["backdrop"] for l in layers]
        if seq != [True, False, True, False]:
            print("FAIL: layer order (want backdrop/panel/backdrop/panel):", layers); sys.exit(1)
        if alpha(backdrops[0]["bg"]) != 0.5 or alpha(backdrops[1]["bg"]) != 0.25:
            print("FAIL: backdrop dims (want 0.5 page, 0.25 inter-dialog):", backdrops); sys.exit(1)
        # ...and the panel on top must be the help dialog (opened later), not the
        # edit dialog: re-render churn must not scramble the registration order.
        if not panels[1]["help"] or panels[0]["help"]:
            print("FAIL: help dialog is not the topmost panel:", panels); sys.exit(1)
        print("PASS: stacked dialogs interleave with a 0.25 veil, in open order")

        # Backdrop click closes only the topmost dialog
        await evaluate(cdp, s, """
            (els => els.sort((a, b) => (+getComputedStyle(b).zIndex || 0) - (+getComputedStyle(a).zIndex || 0))[0].click())
            ([...document.querySelectorAll('body > *')].filter(e => e.children.length === 0 &&
                getComputedStyle(e).backgroundColor.startsWith('rgba(0, 0, 0')))""")
        await asyncio.sleep(1.0)  # close + backdrop fade-out
        if await evaluate(cdp, s, "document.body.innerText.includes('three types of users')"):
            print("FAIL: backdrop click did not close the top dialog"); sys.exit(1)
        if not await evaluate(cdp, s, "document.body.innerText.includes('New user')"):
            print("FAIL: backdrop click closed the underlying dialog too"); sys.exit(1)
        layers = await evaluate(cdp, s, MODAL_LAYERS)
        if [l["backdrop"] for l in layers] != [True, False]:
            print("FAIL: layers after closing the top dialog:", layers); sys.exit(1)
        print("PASS: backdrop click dismissed only the topmost dialog")

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
