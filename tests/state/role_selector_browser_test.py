#!/usr/bin/env python3
"""RoleSelector browser pass: the rich role menu on the members tab (descriptions,
current role selected + focused on open, keyboard pick with focus returning to the
trigger, type-ahead, Escape), disabled options with their reasons (guest row;
moderator viewer lacking permissions), the "About roles…" help dialog, and the
guest-roles-only variant in the Add members dialog."""
import asyncio
import sys
import urllib.request

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE, MEMBER_EMAIL, login, post

GUEST_EMAIL = "roleselector-guest@confido.example"

MEMBERS = "window.curTopics['room/testroom/members'].data"
ROLE_OF = MEMBERS + ".find(m => (m.user.displayName || '') === '%s' || m.email === '%s')?.role"

# The changeable trigger on the row containing `text` (name or e-mail).
TRIGGER = """
    [...document.querySelectorAll('[aria-haspopup=listbox]')]
      .find(b => { for (let el = b.parentElement; el; el = el.parentElement)
                     if (el.innerText && el.innerText.includes('%s')) return el.querySelectorAll('[aria-haspopup=listbox]').length === 1;
                   return false; })"""

OPTIONS = """
    [...document.querySelectorAll('[role=listbox] [role=option]')].map(o => ({
        id: o.dataset.roleId, sel: o.getAttribute('aria-selected'),
        dis: o.getAttribute('aria-disabled'), text: o.innerText,
        focused: o === document.activeElement }))"""


async def key(cdp, s, k, code=None):
    params = {"key": k, "code": code or k}
    if len(k) == 1:
        params["text"] = k
    await cdp.cmd("Input.dispatchKeyEvent", {"type": "keyDown", **params}, session=s)
    await cdp.cmd("Input.dispatchKeyEvent", {"type": "keyUp", **params}, session=s)
    await asyncio.sleep(0.15)


async def main():
    admin = login(ADMIN_EMAIL)
    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
    post("/rooms/testroom/members/add",
         {"type": "newMember", "email": GUEST_EMAIL, "role": "forecaster"}, admin)

    fails = []
    def check(ok, name, desc, detail=""):
        print(("PASS" if ok else "FAIL") + f": {desc}" + ("" if ok else f"  [{detail}]"))
        if not ok:
            fails.append(name)

    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)
        await cdp.cmd("Emulation.setDeviceMetricsOverride",
                      {"width": 1400, "height": 900, "deviceScaleFactor": 1, "mobile": False}, session=s)
        if await login_page(cdp, s, ADMIN_EMAIL) != 200:
            print("FAIL: login"); sys.exit(1)

        await cdp.cmd("Page.navigate", {"url": BASE + "/room/testroom/members"}, session=s)
        await wait_for(cdp, s, f"document.body.innerText.includes('{GUEST_EMAIL}') && document.body.innerText.includes('debugmember')",
                       desc="members tab rows")

        # R1: the member's trigger opens a listbox with descriptions; the current
        # role is selected and focused.
        await evaluate(cdp, s, f"({TRIGGER % MEMBER_EMAIL}).focus()")
        await evaluate(cdp, s, f"({TRIGGER % MEMBER_EMAIL}).click()")
        await wait_for(cdp, s, "!!document.querySelector('[role=listbox]')", desc="listbox open")
        await asyncio.sleep(0.2)
        opts = await evaluate(cdp, s, OPTIONS)
        cur = next((o for o in opts if o["id"] == "forecaster"), {})
        check(cur.get("sel") == "true" and cur.get("focused"), "R1a",
              "current role selected and focused on open", opts)
        check(all(o["dis"] == "false" for o in opts) and "Can submit their own predictions" in cur.get("text", ""),
              "R1b", "admin: all roles pickable, descriptions shown", opts)

        # R2: ArrowUp to Observer + Enter picks it; menu closes, focus back on trigger.
        await key(cdp, s, "ArrowUp")
        await key(cdp, s, "Enter")
        await wait_for(cdp, s, f"{ROLE_OF % ('debugmember', MEMBER_EMAIL)} === 'viewer'", desc="role -> viewer")
        state = await evaluate(cdp, s, f"""({{ open: !!document.querySelector('[role=listbox]'),
            focus: document.activeElement === ({TRIGGER % MEMBER_EMAIL}),
            label: ({TRIGGER % MEMBER_EMAIL}).innerText.trim() }})""")
        check(state == {"open": False, "focus": True, "label": "Observer"}, "R2",
              "keyboard pick applies, closes, refocuses trigger", state)

        # R3: ArrowDown on the trigger opens; type-ahead 'm' jumps to Moderator; Escape closes.
        await key(cdp, s, "ArrowDown")
        await wait_for(cdp, s, "!!document.querySelector('[role=listbox]')", desc="listbox reopen")
        await asyncio.sleep(0.2)
        await key(cdp, s, "m", "KeyM")
        focused = await evaluate(cdp, s, "document.activeElement?.dataset?.roleId")
        await key(cdp, s, "Escape")
        await asyncio.sleep(0.3)
        closed = await evaluate(cdp, s, "!document.querySelector('[role=listbox]')")
        role_kept = await evaluate(cdp, s, ROLE_OF % ("debugmember", MEMBER_EMAIL))
        check(focused == "moderator" and closed and role_kept == "viewer", "R3",
              "arrow opens, type-ahead focuses, Escape closes without picking",
              f"focused={focused} closed={closed} role={role_kept}")

        # R4: guest row: non-guest roles disabled with the reason.
        await evaluate(cdp, s, f"({TRIGGER % GUEST_EMAIL}).click()")
        await wait_for(cdp, s, "!!document.querySelector('[role=listbox]')", desc="guest listbox")
        opts = await evaluate(cdp, s, OPTIONS)
        bad = {o["id"]: o for o in opts if o["dis"] == "true"}
        check(set(bad) == {"moderator", "owner"} and all("Not available to guests" in o["text"] for o in bad.values()),
              "R4", "guest row: moderator/owner disabled as not available to guests", opts)

        # R5: "About roles…" opens the help dialog over the page.
        await evaluate(cdp, s, "[...document.querySelectorAll('button')].find(b => b.innerText.includes('About roles')).click()")
        await wait_for(cdp, s, "document.body.innerText.includes('Room roles')", desc="help dialog")
        body = await evaluate(cdp, s, "document.body.innerText")
        check("Has full control over the room" in body and not await evaluate(cdp, s, "!!document.querySelector('[role=listbox]')"),
              "R5", "About roles opens the role help dialog (menu closed)")
        await cdp.cmd("Page.navigate", {"url": BASE + "/room/testroom/members"}, session=s)
        await wait_for(cdp, s, "[...document.querySelectorAll('button')].some(b => b.innerText.trim() === 'Add member')",
                       desc="members tab reloaded")

        # R6: Add members dialog offers guest roles only.
        await evaluate(cdp, s, "[...document.querySelectorAll('button')].find(b => b.innerText.trim() === 'Add member').click()")
        await wait_for(cdp, s, "document.body.innerText.includes('Added member role')", desc="add member dialog")
        await asyncio.sleep(0.5)
        await evaluate(cdp, s, f"({TRIGGER % 'Added member role'}).click()")
        await wait_for(cdp, s, "!!document.querySelector('[role=listbox]')", desc="add-member listbox")
        ids = await evaluate(cdp, s, f"{OPTIONS}.map(o => o.id)")
        check("moderator" not in ids and "owner" not in ids and "forecaster" in ids, "R6",
              "Add members role menu lists guest roles only", ids)

        guest_id = await evaluate(cdp, s, f"""(u => typeof u === 'string' ? u : u && u.id)(
            {MEMBERS}.find(m => m.email === '{GUEST_EMAIL}')?.user?.userId)""")

        # R7: a moderator viewer: owner is out of reach on another proper member's row.
        post("/rooms/testroom/members/add",
             {"type": "existingMember", "user": "debugmember", "role": "moderator"}, admin)
        post("/rooms/testroom/members/add",
             {"type": "existingMember", "user": "debugadmin", "role": "forecaster"}, admin)
        s2 = await new_page(cdp)
        await cdp.cmd("Emulation.setDeviceMetricsOverride",
                      {"width": 1400, "height": 900, "deviceScaleFactor": 1, "mobile": False}, session=s2)
        if await login_page(cdp, s2, MEMBER_EMAIL) != 200:
            print("FAIL: member login"); sys.exit(1)
        await cdp.cmd("Page.navigate", {"url": BASE + "/room/testroom/members"}, session=s2)
        await wait_for(cdp, s2, f"document.body.innerText.includes('{ADMIN_EMAIL}')", desc="moderator members tab")
        await evaluate(cdp, s2, f"({TRIGGER % ADMIN_EMAIL}).click()")
        await wait_for(cdp, s2, "!!document.querySelector('[role=listbox]')", desc="moderator listbox")
        opts = await evaluate(cdp, s2, OPTIONS)
        owner = next((o for o in opts if o["id"] == "owner"), {})
        others = [o for o in opts if o["id"] != "owner"]
        check(owner.get("dis") == "true" and "Grants permissions you do not have" in owner.get("text", "")
              and all(o["dis"] == "false" for o in others), "R7",
              "moderator: owner disabled with reason, other roles pickable", opts)
        await cdp.drain(1.0)
        errors = collect_errors(cdp)

    # Restore the shared fixture for later tests.
    post("/rooms/testroom/members/add",
         {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
    for uid in ("debugadmin", guest_id):
        if uid:
            urllib.request.urlopen(urllib.request.Request(
                f"{BASE}/rooms/testroom/members/{uid}", headers={"Cookie": admin}, method="DELETE"))

    if errors:
        print("Console/JS errors:")
        for e in errors:
            print(" -", e)
        fails.append("console")
    else:
        print("no console errors")
    print("OVERALL:", "FAIL" if fails else "PASS")
    sys.exit(1 if fails else 0)


asyncio.run(main())
