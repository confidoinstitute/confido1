#!/usr/bin/env python3
"""Profile UI in the real bundle, as a fresh password-less user logged in by e-mail
link: the nickname section saves and reports back (a save handler once read a context
accessor outside render and failed silently), the password section sets a first
password, a requested reset link opens the choose-a-new-password page and sets it, the
password can be removed again, an e-mail change's revert link reads as a success even
though it logs this very tab out, and no console errors."""
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

        # Remove it again from the profile (the reset kept this tab's session).
        await cdp.cmd("Page.navigate", {"url": BASE + "/profile"}, session=s)
        await wait_for(cdp, s, "!!document.querySelector('input[name=\"current_password\"]')", desc="change form")
        await evaluate(cdp, s, f"({SET_INPUT})('current_password', 'second-password')")
        await evaluate(cdp, s, click_button("Remove password"))
        await wait_for(cdp, s, "document.body.innerText.includes('Password removed')", desc="password removed")
        await wait_for(cdp, s, "!document.querySelector('input[name=\"current_password\"]')", desc="back to set form")
        print("PASS: password section removes the password")

        # E-mail change, then its "not you?" revert link opened in this logged-in tab: the
        # revert ends this very session, the app drops to the logged-out layout and the
        # page remounts — it must still read as a success, not a spent link.
        before = mail_tokens("email_verify")
        await evaluate(cdp, s, """fetch('/profile/email/start_verification', {method:'POST',
            headers:{'Content-Type':'application/json'},
            body: JSON.stringify({email:'profilebrowser2@confido.example'})}).then(r => r.status)""", await_promise=True)
        token = new_mail_token("email_verify", before)
        before = mail_tokens("email_revert")
        call("/profile/email/verify", {"token": token})
        token = new_mail_token("email_revert", before)
        await cdp.cmd("Page.navigate", {"url": f"{BASE}/email_revert?t={token}"}, session=s)
        await wait_for(cdp, s, "document.body.innerText.includes('Your e-mail address was restored')", desc="revert done")
        await asyncio.sleep(4)  # the session close lands; the logged-out layout remounts the page
        body = await evaluate(cdp, s, "document.body.innerText")
        if "Your e-mail address was restored" not in body or "Could not restore" in body:
            print("FAIL: revert page after logout remount:", body[:300]); sys.exit(1)
        print("PASS: revert page stays a success across the logged-out remount")

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
