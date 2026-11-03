#!/usr/bin/env python3
"""Ordinary-member browser view: no Members tab (MANAGE_MEMBERS-gated UI), no foreign
identity data anywhere on the page, no console errors on the presentation-only
codepaths. (Member-view payload censoring itself is covered by members_test.py.)"""
import asyncio
import sys

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE, MEMBER_EMAIL


async def main():
    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)

        # Fresh page context; make sure we are the member, not a leftover admin login.
        await cdp.cmd("Page.navigate", {"url": BASE + "/"}, session=s)
        await asyncio.sleep(2)
        code = await evaluate(cdp, s, f"""
            fetch('/logout', {{method:'POST'}}).catch(() => null).then(() =>
            fetch('/login', {{method:'POST', headers:{{'Content-Type':'application/json'}},
                body: JSON.stringify({{email:'{MEMBER_EMAIL}', password:'pass', validity:'TRANSIENT'}})}})
              .then(r => r.status))""", await_promise=True)
        print("member login status:", code)
        if code != 200:
            print("FAIL: login"); sys.exit(1)

        await cdp.cmd("Page.navigate", {"url": BASE + "/room/testroom/members"}, session=s)
        await wait_for(cdp, s, "!!(window.curTopics && window.curTopics.rooms && window.curTopics.rooms.data && window.curTopics && window.curTopics.self && window.curTopics.self.data)", desc="app state")
        await wait_for(cdp, s, "document.body.innerText.includes('Questions')", desc="room page")
        body = await evaluate(cdp, s, "document.body.innerText")
        if "Members" in body:
            print("FAIL: ordinary member sees a Members tab"); sys.exit(1)
        if ADMIN_EMAIL in body or "debugadmin" in body:
            print("FAIL: foreign identity data in ordinary member's page"); sys.exit(1)
        print("PASS: no members tab / no foreign identity for ordinary member")

        await cdp.drain(1.5)
        errors = collect_errors(cdp)
        if errors:
            print("Console/JS errors:")
            for e in errors:
                print(" -", e)
            print("OVERALL: FAIL"); sys.exit(1)
        print("no console errors")
        print("OVERALL: PASS")


asyncio.run(main())
