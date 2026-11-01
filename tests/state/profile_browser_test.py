#!/usr/bin/env python3
"""Profile UI in the real bundle, as a fresh password-less user logged in by e-mail
link: the nickname section saves and reports back (a save handler once read a context
accessor outside render and failed silently), the password section sets a first
password, a requested reset link opens the choose-a-new-password page and sets it, and
no console errors."""
import asyncio
import sys

from cdp_test import *
from confido_test import ADMIN_EMAIL, call, login, mail_tokens, new_mail_token

SET_INPUT = """(name, value) => {
    const el = document.querySelector(`input[name="${name}"]`);
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, value);
    el.dispatchEvent(new Event('input', {bubbles: true}));
}"""


def click_button(text):
    return f"[...document.querySelectorAll('button')].find(b => b.innerText.trim() == {text!r}).click()"


async def save_nick(cdp, s, nick):
    await evaluate(cdp, s, f"({SET_INPUT})('nick', {nick!r})")
    await evaluate(cdp, s, click_button("Save nickname"))
    await wait_for(cdp, s, "document.body.innerText.includes('Nickname saved.')", desc="nick saved")
    await wait_for(cdp, s, f"window.curTopics.self.data.user.nick == {nick!r}", desc="self slice nick")


USER_EMAIL = "profilebrowser@confido.example"


async def main():
    admin = login(ADMIN_EMAIL)
    call("/users/add", {"_id": "", "type": "MEMBER", "email": USER_EMAIL, "nick": "Browser Prof"}, admin)

    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)
        await cdp.cmd("Page.navigate", {"url": BASE + "/"}, session=s)
        await asyncio.sleep(2)
        await evaluate(cdp, s, "fetch('/logout', {method:'POST'}).catch(() => null)", await_promise=True)

        # Log in through a real e-mail link, as a password-less user would.
        before = mail_tokens("email_login")
        call("/login_email/create", {"email": USER_EMAIL, "url": "/profile", "validity": "TRANSIENT"})
        token = new_mail_token("email_login", before)
        await cdp.cmd("Page.navigate", {"url": f"{BASE}/email_login?t={token}"}, session=s)
        await wait_for(cdp, s, "!!document.querySelector('input[name=\"nick\"]')", desc="profile form after e-mail login")
        await wait_for(cdp, s, "!!(window.curTopics && window.curTopics.self && window.curTopics.self.data)", desc="app state")

        await save_nick(cdp, s, "Browser Prof (edited)")
        print("PASS: nickname section saves and reports the change")

        await evaluate(cdp, s, f"({SET_INPUT})('new_password', 'first-password')")
        await evaluate(cdp, s, f"({SET_INPUT})('confirm_password', 'first-password')")
        await evaluate(cdp, s, click_button("Set password"))
        await wait_for(cdp, s, "document.body.innerText.includes('Password saved.')", desc="password set")
        await wait_for(cdp, s, "!!document.querySelector('input[name=\"current_password\"]')", desc="change form")
        print("PASS: password section sets a first password")

        # Reset link → choose-a-new-password page (opened in this logged-in tab: the
        # opener's own session survives the reset).
        before = mail_tokens("password_reset")
        await evaluate(cdp, s, click_button("Forgot your current password? Reset it by e-mail"))
        await wait_for(cdp, s, "document.body.innerText.includes('It lets you choose a new password')", desc="reset mailed")
        token = new_mail_token("password_reset", before)
        await cdp.cmd("Page.navigate", {"url": f"{BASE}/password_reset?t={token}"}, session=s)
        await wait_for(cdp, s, "!!document.querySelector('input[name=\"confirm_password\"]')", desc="reset form")
        await evaluate(cdp, s, f"({SET_INPUT})('new_password', 'second-password')")
        await evaluate(cdp, s, f"({SET_INPUT})('confirm_password', 'second-password')")
        await evaluate(cdp, s, click_button("Set new password"))
        await wait_for(cdp, s, "document.body.innerText.includes('Your password was changed')", desc="reset done")
        st, _ = call("/login", {"email": USER_EMAIL, "password": "second-password", "validity": "TRANSIENT"})
        if st != 200:
            print("FAIL: new password does not log in", st); sys.exit(1)
        print("PASS: reset link page sets a new password")

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
