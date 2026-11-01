#!/usr/bin/env python3
"""Profile page save round-trip in the real bundle: editing the nickname and pressing
"Edit profile" reaches the server and reports back (the save handler once read a
context accessor outside render, threw, and the lock swallowed it into the console),
with no console errors."""
import asyncio
import sys

from cdp_test import *
from confido_test import MEMBER_EMAIL

SET_INPUT = """(name, value) => {
    const el = document.querySelector(`input[name="${name}"]`);
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, value);
    el.dispatchEvent(new Event('input', {bubbles: true}));
}"""


async def save_nick(cdp, s, nick):
    await evaluate(cdp, s, f"({SET_INPUT})('nick', {nick!r})")
    await evaluate(cdp, s, "[...document.querySelectorAll('button')].find(b => b.innerText.trim() == 'Edit profile').click()")
    await wait_for(cdp, s, "document.body.innerText.includes('Nickname was set.')", desc="nick saved")
    await wait_for(cdp, s, f"window.curTopics.self.data.user.nick == {nick!r}", desc="self slice nick")


async def main():
    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)
        await evaluate(cdp, s, "fetch('/logout', {method:'POST'}).catch(() => null)", await_promise=True)
        if await login_page(cdp, s, MEMBER_EMAIL) != 200:
            print("FAIL: login"); sys.exit(1)

        await cdp.cmd("Page.navigate", {"url": BASE + "/profile"}, session=s)
        await wait_for(cdp, s, "!!(window.curTopics && window.curTopics.self && window.curTopics.self.data)", desc="app state")
        await wait_for(cdp, s, "!!document.querySelector('input[name=\"nick\"]')", desc="profile form")
        original = await evaluate(cdp, s, "document.querySelector('input[name=\"nick\"]').value")

        await save_nick(cdp, s, original + " (edited)")
        print("PASS: profile save reaches the server and reports the nick change")
        await save_nick(cdp, s, original)  # restore for other tests

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
