#!/usr/bin/env python3
"""Profile self-service: nick sanitization; password set/change (current one required,
other sessions ended); e-mail change via verification (pending on the self slice, only
the newest link valid, cancel, verify logged out, duplicate address refused); password
reset links (choose a new password logged out, every session and reset link ended);
password removal (current one required, other sessions ended, settable again); the
e-mail-change "not you?" revert chain (all sessions end, earliest link wins, reuse no-op);
up-front duplicate-address errors for proper users only;
e-mail-less guests; admin input held to the same e-mail rules.

Link tokens are read from the devmode mailer's log (see confido_test.mail_tokens)."""
import asyncio
import json
import urllib.request

from confido_test import *

USER_EMAIL = "profiletest@confido.example"
NEW_EMAIL = "profiletest2@confido.example"


def email_login(email):
    before = mail_tokens("email_login")
    call("/login_email/create", {"email": email, "url": "/", "validity": "TRANSIENT"})
    token = new_mail_token("email_login", before)
    cj, op = session_opener()
    op.open(BASE + "/")
    op.open(urllib.request.Request(
        BASE + "/login_email", data=json.dumps({"token": token}).encode(),
        headers={"Content-Type": "application/json"}))
    return cookie_str(cj)


def alive(cookie):
    return call("/profile/ui_setting", {"key": "profileTestProbe", "value": None}, cookie)[0] == 200


def password_login(email, password):
    return call("/login", {"email": email, "password": password, "validity": "TRANSIENT"})[0] == 200


def self_data(cookie):
    async def fetch():
        async with await connect_ws(cookie) as ws:
            await ws.send(json.dumps({"sub": ["self"]}))
            return by_topic(await recv_updates(ws, 1.5)).get("self", {}).get("data") or {}
    return asyncio.run(fetch())


def start_verification(cookie, email):
    before = mail_tokens("email_verify")
    st, body = call("/profile/email/start_verification", {"email": email}, cookie)
    return st, body, (new_mail_token("email_verify", before) if st == 200 else None)


def main():
    admin = login(ADMIN_EMAIL)
    t = Suite()

    call("/users/add", {"_id": "", "type": "MEMBER", "email": USER_EMAIL, "nick": "Prof"}, admin)
    a = email_login(USER_EMAIL)
    b = email_login(USER_EMAIL)
    t.check(alive(a) and alive(b), "TEST-1", "two e-mail-link sessions for the test user")

    # --- Nickname ---
    st, _ = call("/profile/nick", {"name": "  Prof \t Two "}, a)
    nick = (self_data(a).get("user") or {}).get("nick")
    t.check(st == 200 and nick == "Prof Two", "TEST-2", "nick is sanitized on save", f"{st} {nick!r}")

    # --- Password: first one needs no current password; ends other sessions ---
    reset_before = mail_tokens("password_reset")
    st, body = call("/profile/password", {"currentPassword": None, "newPassword": "password1"}, a)
    t.check(st == 200 and self_data(a).get("myPasswordIsSet"), "TEST-3", "first password set", f"{st} {body}")
    t.check(alive(a) and not alive(b), "TEST-4", "setting the password ends other sessions, keeps the current one")
    undo_token = new_mail_token("password_reset", reset_before)
    t.check(undo_token is not None, "TEST-5", "password change mails a 'not you?' reset link")
    st, body = call("/profile/password", {"currentPassword": "wrong-password", "newPassword": "password2"}, a)
    t.check(st == 400 and "incorrect" in body, "TEST-6", "changing the password requires the current one", f"{st} {body}")
    st, body = call("/profile/password", {"currentPassword": "password1", "newPassword": "short"}, a)
    t.check(st == 400 and "at least" in body, "TEST-7", "too-short password refused", f"{st} {body}")

    # --- E-mail change ---
    st, body, _ = start_verification(a, "not-an-address")
    t.check(st == 400, "TEST-8", "invalid address refused", f"{st} {body}")
    st, _, old_token = start_verification(a, NEW_EMAIL)
    pending = self_data(a).get("myPendingEmail")
    t.check(st == 200 and old_token and pending == NEW_EMAIL, "TEST-9", "change is pending on the self slice", repr(pending))
    _, _, token = start_verification(a, NEW_EMAIL.upper())
    st, body = call("/profile/email/verify", {"token": old_token})
    t.check(st == 401, "TEST-10", "re-sending invalidates the older link", f"{st} {body}")
    revert_before = mail_tokens("email_revert")
    st, body = call("/profile/email/verify", {"token": token})
    owner_revert = new_mail_token("email_revert", revert_before)
    me = self_data(a)
    t.check(st == 200 and (me.get("user") or {}).get("email") == NEW_EMAIL and not me.get("myPendingEmail"),
            "TEST-11", "verification works logged out, applies the (lowercased) address, clears pending",
            f"{st} {body} {json.dumps(me.get('user'))[:200]}")
    t.check(password_login(NEW_EMAIL, "password1") and owner_revert,
            "TEST-12", "password login works with the new address; the old one got a revert link")

    _, _, token = start_verification(a, "profiletest3@confido.example")
    st, _ = call("/profile/email/cancel", None, a)
    st2, _ = call("/profile/email/verify", {"token": token})
    t.check(st == 200 and not self_data(a).get("myPendingEmail") and st2 == 401,
            "TEST-13", "cancel withdraws the pending change and its link", f"{st} {st2}")

    st, body, _ = start_verification(a, MEMBER_EMAIL)
    t.check(st == 400 and "already used" in body,
            "TEST-14", "proper member is told up front that an address is taken", f"{st} {body}")

    # --- Password reset: choose a new password logged out ---
    before = mail_tokens("password_reset")
    st, _ = call("/profile/password/reset", None, a)
    token = new_mail_token("password_reset", before)
    st, body = call("/profile/password/reset/finish", {"token": token, "newPassword": "short"})
    t.check(st == 400, "TEST-15", "reset refuses a too-short password", f"{st} {body}")
    st, body = call("/profile/password/reset/finish", {"token": token, "newPassword": "password3"})
    t.check(st == 200, "TEST-16", "reset link works logged out (and survived the refused attempt)", f"{st} {body}")
    t.check(not alive(a), "TEST-17", "reset ends the user's sessions")
    t.check(password_login(NEW_EMAIL, "password3") and not password_login(NEW_EMAIL, "password1"),
            "TEST-18", "the new password replaces the old one")
    st, _ = call("/profile/password/reset/finish", {"token": undo_token, "newPassword": "password4"})
    t.check(st == 401, "TEST-19", "a reset consumes the user's other reset links", str(st))

    # --- Removing the password: back to e-mail-link login ---
    c = email_login(NEW_EMAIL)
    d = email_login(NEW_EMAIL)
    st, body = call("/profile/password/remove", {"currentPassword": "wrong-password"}, c)
    t.check(st == 400 and "incorrect" in body, "TEST-20", "removal requires the current password", f"{st} {body}")
    reset_before = mail_tokens("password_reset")
    st, body = call("/profile/password/remove", {"currentPassword": "password3"}, c)
    t.check(st == 200 and not self_data(c).get("myPasswordIsSet") and not password_login(NEW_EMAIL, "password3"),
            "TEST-21", "password removed; password login no longer works", f"{st} {body}")
    t.check(alive(c) and not alive(d), "TEST-22", "removal ends other sessions, keeps the current one")
    t.check(new_mail_token("password_reset", reset_before) is not None,
            "TEST-23", "removal mails a 'not you?' reset link")
    st, body = call("/profile/password/remove", {"currentPassword": "password3"}, c)
    t.check(st == 400, "TEST-24", "nothing to remove without a password", f"{st} {body}")
    st, body = call("/profile/password", {"currentPassword": None, "newPassword": "password5"}, c)
    t.check(st == 200 and password_login(NEW_EMAIL, "password5"),
            "TEST-25", "a password can be set again without a current one", f"{st} {body}")

    # --- E-mail change "not you?" chain: the earliest revert link always wins ---
    def change(cookie, email):
        _, _, tok = start_verification(cookie, email)
        before = mail_tokens("email_revert")
        call("/profile/email/verify", {"token": tok})
        return new_mail_token("email_revert", before)
    r1 = change(c, "evil1@confido.example")   # restores NEW_EMAIL, mailed to the owner's inbox
    r2 = change(c, "evil2@confido.example")   # restores evil1, mailed to the intruder's inbox
    st, _ = call("/profile/email/revert", {"token": r2})
    t.check(st == 200 and not alive(c), "TEST-26", "revert ends every session, the opener's included", str(st))
    st, _ = call("/profile/email/revert", {"token": r1})
    st2, _ = call("/profile/email/revert", {"token": r1})
    st3, _ = call("/profile/email/revert", {"token": r2})
    t.check(st == 200 and st2 == 200 and st3 == 401 and not password_login(NEW_EMAIL, "password5"),
            "TEST-27", "an earlier link still works after a later one was used and cancels it; "
            "reopening the used link is a harmless no-op", f"{st} {st2} {st3}")
    e = email_login(NEW_EMAIL)
    t.check(alive(e), "TEST-28", "the owner's address is back (e-mail login works)")
    st, _ = call("/profile/email/revert", {"token": owner_revert})
    t.check(st == 200 and not alive(e), "TEST-29", "the earliest link restores the original address", str(st))
    e = email_login(USER_EMAIL)
    t.check(alive(e) and (self_data(e).get("user") or {}).get("email") == USER_EMAIL,
            "TEST-30", "logged in again by e-mail link at the original address")

    # --- E-mail-less guest ---
    link = json.loads(post("/rooms/testroom/invites/create",
                           {"description": "profile guests", "role": "forecaster"}, admin))
    cj, op = session_opener()
    op.open(BASE + "/")
    op.open(urllib.request.Request(
        BASE + "/rooms/testroom/invite/accept_newuser",
        data=json.dumps({"inviteToken": link["token"], "userNick": "profguest",
                         "email": None, "validity": "TRANSIENT"}).encode(),
        headers={"Content-Type": "application/json"}))
    guest = cookie_str(cj)
    st, _ = call("/profile/nick", {"name": "profguest2"}, guest)
    t.check(st == 200, "TEST-31", "e-mail-less guest can rename", str(st))
    st, body = call("/profile/password", {"currentPassword": None, "newPassword": "password1"}, guest)
    t.check(st == 400 and "e-mail address first" in body, "TEST-32", "password needs an e-mail first", f"{st} {body}")
    st, body, token = start_verification(guest, MEMBER_EMAIL)
    st2, body2 = call("/profile/email/verify", {"token": token})
    t.check(st == 200 and st2 == 400 and "already used" in body2,
            "TEST-33", "guests learn an address is taken only at verification (no directory to leak from)",
            f"{st} {body} / {st2} {body2}")

    # --- Admin input: same e-mail rules ---
    st, body = call("/users/add", {"_id": "", "type": "MEMBER", "email": "not-an-address"}, admin)
    t.check(st == 400, "TEST-34", "admin add refuses an invalid address", f"{st} {body}")

    t.finish()


main()
