#!/usr/bin/env python3
"""Profile editing: nick sanitization + honest change reporting, e-mail change and
verification (no session needed, duplicate addresses refused, no re-send on unchanged
saves), password set/change ending other sessions, the password-reset link working
logged out and ending sessions, and e-mail-less guests saving their profile.

Tokens are read from the devmode mailer's log (run_all.sh exports CONFIDO_TEST_LOG)."""
import json
import os
import re
import time
import urllib.error
import urllib.request

from confido_test import *

LOG = os.environ.get("CONFIDO_TEST_LOG", os.path.join(os.environ.get("TMPDIR", "/tmp"), "confido_test_server.log"))
USER_EMAIL = "profiletest@confido.example"


def log_tokens(kind):
    """All tokens of one link kind (email_login / email_verify / password_reset) logged so far."""
    with open(LOG, errors="replace") as f:
        return re.findall(kind + r"\?t=([A-Za-z0-9_-]+)", f.read())


def new_token(kind, before):
    for _ in range(20):
        toks = log_tokens(kind)
        if len(toks) > len(before):
            return toks[-1]
        time.sleep(0.2)
    return None


def call(path, body, cookie=""):
    """POST returning (status, body) instead of raising on HTTP errors."""
    req = urllib.request.Request(
        BASE + path, data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "Cookie": cookie}, method="POST")
    try:
        r = urllib.request.urlopen(req)
        return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def email_login(email):
    before = log_tokens("email_login")
    call("/login_email/create", {"email": email, "url": "/", "validity": "TRANSIENT"})
    token = new_token("email_login", before)
    cj, op = session_opener()
    op.open(BASE + "/")
    op.open(urllib.request.Request(
        BASE + "/login_email", data=json.dumps({"token": token}).encode(),
        headers={"Content-Type": "application/json"}))
    return cookie_str(cj)


def alive(cookie):
    return call("/profile/ui_setting", {"key": "profileTestProbe", "value": None}, cookie)[0] == 200


def edit(cookie, nick, email, current=None, new=None):
    status, body = call("/profile/edit", {"nick": nick, "email": email,
                                          "currentPassword": current, "newPassword": new}, cookie)
    return status, (json.loads(body) if status == 200 else body)


def main():
    admin = login(ADMIN_EMAIL)
    t = Suite()

    call("/users/add", {"_id": "", "type": "MEMBER", "email": USER_EMAIL,
                        "emailVerified": True, "nick": "Prof"}, admin)
    a = email_login(USER_EMAIL)
    b = email_login(USER_EMAIL)
    t.check(alive(a) and alive(b), "TEST-1", "two e-mail-link sessions for the test user")

    # --- Nick: sanitized, no phantom change; first password needs no current one ---
    verify_before = log_tokens("email_verify")
    reset_before = log_tokens("password_reset")
    st, r = edit(a, "  Prof \t Two ", USER_EMAIL, None, "password1")
    t.check(st == 200 and r.get("nickChanged") and r.get("passwordChanged") and not r.get("emailChanged"),
            "TEST-2", "nick + first password set; unchanged e-mail sends nothing", str(r))
    st, r = edit(a, "Prof Two", USER_EMAIL)
    t.check(st == 200 and not r.get("nickChanged"),
            "TEST-3", "sanitized nick is stored, so re-saving it reports no change", str(r))
    t.check(log_tokens("email_verify") == verify_before,
            "TEST-4", "no verification e-mail for saves that keep the address")
    t.check(alive(a) and not alive(b),
            "TEST-5", "setting the password ends other sessions, keeps the current one")
    undo_token = new_token("password_reset", reset_before)

    st, r = edit(a, "Prof Two", USER_EMAIL, "wrong-password", "password2")
    t.check(st == 200 and r.get("passwordError"),
            "TEST-6", "changing the password requires the current one", str(r))

    # --- E-mail change: verify link works without any session ---
    new_email = "profiletest2@confido.example"
    st, r = edit(a, "Prof Two", new_email)
    token = new_token("email_verify", verify_before)
    t.check(st == 200 and r.get("emailChanged") and token, "TEST-7", "e-mail change sends a verification link", str(r))
    st, body = call("/profile/email/verify", {"token": token})
    t.check(st == 200, "TEST-8", "verification link works logged out", f"{st} {body}")
    st, _ = call("/login", {"email": new_email, "password": "password1", "validity": "TRANSIENT"})
    t.check(st == 200, "TEST-9", "password login works with the verified new address", str(st))

    # --- Duplicate address refused at verification time ---
    before = log_tokens("email_verify")
    edit(a, "Prof Two", MEMBER_EMAIL)
    token = new_token("email_verify", before)
    st, body = call("/profile/email/verify", {"token": token}, a)
    t.check(st == 400 and "already used" in body,
            "TEST-10", "verifying an address owned by another account is refused", f"{st} {body}")

    # --- Password undo link: works logged out, ends all sessions, removes the password ---
    st, body = call("/profile/password/reset/finish", {"token": undo_token})
    t.check(st == 200, "TEST-11", "password-changed undo link works logged out", f"{st} {body}")
    t.check(not alive(a), "TEST-12", "undo link ends the user's sessions")
    st, _ = call("/login", {"email": new_email, "password": "password1", "validity": "TRANSIENT"})
    t.check(st != 200, "TEST-13", "password is removed after undo", str(st))

    # --- E-mail-less guest can save their profile ---
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
    st, r = edit(guest, "profguest2", "")
    t.check(st == 200 and r.get("nickChanged") and not r.get("emailError"),
            "TEST-14", "e-mail-less guest renames without adding an e-mail", str(r))

    t.finish()


main()
