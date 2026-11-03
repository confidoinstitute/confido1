#!/usr/bin/env python3
"""Creating a presentation from the room menu must land on a working tab
without a reload. Regression guard: RoomLayout memoized its route list on the
room *id*, so a room update that added the presentation (or scoring, or a
permission change) refreshed the tab strip but not the routes — the new tab
matched nothing and rendered empty until a page reload. Also covers the
auto-navigate to the presentation tab after creation."""
import asyncio
import sys

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE


async def main():
    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)
        # Desktop viewport: the navbar menu button must have a nonzero rect.
        await cdp.cmd("Emulation.setDeviceMetricsOverride",
                      {"width": 1600, "height": 1000, "deviceScaleFactor": 1, "mobile": False},
                      session=s)
        code = await login_page(cdp, s, ADMIN_EMAIL)
        if code != 200:
            print("FAIL: login ->", code); sys.exit(1)

        # A fresh room (no presentation yet) so the menu offers "Create presentation".
        rid = await evaluate(cdp, s, """
            fetch('/rooms/add', {method:'POST', headers:{'Content-Type':'application/json'},
              body: JSON.stringify({name:'Pres create '+Date.now(), description:'',
                                    color:'BLUE', icon:null})})
              .then(r => r.text()).then(t => t.replace(/"/g, ''))""", await_promise=True)
        await cdp.cmd("Page.navigate", {"url": BASE + f"/rooms/{rid}"}, session=s)
        await wait_for(cdp, s, "document.body.innerText.includes('Pres create')", desc="room loaded")
        if 'Presentation' in (await evaluate(cdp, s, "document.body.innerText")):
            print("FAIL: fresh room already shows a Presentation tab"); sys.exit(1)

        # Open the room menu (rightmost top-row button) and pick "Create presentation".
        left = await evaluate(cdp, s, """(() => {
            const bs = [...document.querySelectorAll('button')].map(b => [b, b.getBoundingClientRect()])
                .filter(([b, r]) => r.top < 60 && r.width > 0 && r.left > 800);
            if (!bs.length) return null;
            bs.sort((a, b) => b[1].left - a[1].left); bs[0][0].click(); return bs[0][1].left; })()""")
        if left is None:
            print("FAIL: room menu button not found"); sys.exit(1)
        item = "[...document.querySelectorAll('*')].find(e => e.childElementCount === 0 && e.textContent.trim() === 'Create presentation')"
        await wait_for(cdp, s, f"!!{item}", desc="Create presentation menu item")
        await evaluate(cdp, s, f"{item}.click()")

        # Auto-navigate lands on the tab, and the tab renders the editor toolbar
        # straight away (no reload): the route list picked up the room change.
        await wait_for(cdp, s, "location.pathname.endsWith('/presentation')", desc="navigated to the presentation tab")
        await wait_for(cdp, s, "document.body.innerText.includes('Upload slides')",
                       desc="presentation editor toolbar rendered without reload")
        print("PASS: create -> auto-navigate -> editor renders without a reload")

        errs = collect_errors(cdp)
        if errs:
            print("FAIL: console errors:", errs); sys.exit(1)
        print("PASS: no console errors")


asyncio.run(main())
