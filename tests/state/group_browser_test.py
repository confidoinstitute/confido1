#!/usr/bin/env python3
"""Room-groups browser pass: sidebar hierarchy (child indented under its group),
the hover-revealed create-in-group "+", the rich two-option "Create new…" menu,
the /new_group creation form subset, the group's Rooms tab with its create entry,
and the ?group= preselect on room creation."""
import asyncio
import sys

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE, login, post


async def main():
    admin = login(ADMIN_EMAIL)
    gid = post("/rooms/add", {"name": "UI test group", "description": "", "color": "BLUE",
                              "icon": None, "isGroup": True}, admin).strip('"')
    post("/rooms/add", {"name": "UI test child", "description": "", "color": "BLUE",
                        "icon": None, "parentRoom": gid}, admin).strip('"')

    fails = []
    def check(ok, name, desc, detail=""):
        print(("PASS" if ok else "FAIL") + f": {desc}" + ("" if ok else f"  [{detail}]"))
        if not ok:
            fails.append(name)

    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)
        # Desktop viewport: at headless-default width the sidebar collapses to
        # zero width and the sidebar hover/hierarchy checks would probe nothing.
        await cdp.cmd("Emulation.setDeviceMetricsOverride",
                      {"width": 1600, "height": 1000, "deviceScaleFactor": 1,
                       "mobile": False}, session=s)
        code = await login_page(cdp, s, ADMIN_EMAIL)
        if code != 200:
            print("FAIL: login")
            sys.exit(1)

        await cdp.cmd("Page.navigate", {"url": BASE + "/"}, session=s)
        await wait_for(cdp, s, "document.body.innerText.includes('UI test group')",
                       desc="sidebar shows group")

        # T1: the child renders inside an indented (padding-left: 20px) container
        # that does NOT contain the group entry itself.
        indented = await evaluate(cdp, s, """
            (() => {
              const links = [...document.querySelectorAll('a')];
              const child = links.find(a => a.textContent.trim() === 'UI test child');
              const group = links.find(a => a.textContent.trim() === 'UI test group');
              if (!child || !group) return 'missing link';
              for (let el = child.parentElement; el && el !== document.body; el = el.parentElement) {
                if (el.contains(group)) break;
                if (getComputedStyle(el).paddingLeft === '20px') return true;
              }
              return false;
            })()""")
        check(indented is True, "T1", "child room indented under its group", f"got {indented}")

        # T2: the create-in-group "+" is hidden until the group entry is hovered.
        # Probe the visible sidebar copy (RoomList also mounts in collapsed,
        # zero-width containers) and hover the group-entry container itself.
        rect = await evaluate(cdp, s, """
            (() => {
              const plus = [...document.querySelectorAll('.create-in-group')]
                .find(p => p.parentElement.getBoundingClientRect().width > 0);
              if (!plus) return null;
              plus.parentElement.scrollIntoView({block: 'center'});
              const r = plus.parentElement.getBoundingClientRect();
              return {x: r.x + r.width / 2, y: r.y + r.height / 2};
            })()""")
        get_opacity = """
            (() => {
              const plus = [...document.querySelectorAll('.create-in-group')]
                .find(p => p.parentElement.getBoundingClientRect().width > 0);
              return plus && getComputedStyle(plus).opacity;
            })()"""
        plus_before = await evaluate(cdp, s, get_opacity)
        await cdp.cmd("Input.dispatchMouseEvent",
                      {"type": "mouseMoved", "x": rect["x"], "y": rect["y"]}, session=s)
        await asyncio.sleep(0.4)
        plus_after = await evaluate(cdp, s, get_opacity)
        check(plus_before == "0" and plus_after == "1", "T2",
              "create-in-group + revealed on group-entry hover",
              f"opacity {plus_before} -> {plus_after}")

        # T3: "Create new…" opens the rich menu with described Room/Group options.
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')].find(b => b.textContent.includes('Create new')).click()")
        await asyncio.sleep(0.6)
        menu = await evaluate(cdp, s, "document.body.innerText")
        check("A container of rooms" in menu and "predicts and discusses" in menu, "T3",
              "create-new menu shows rich Room/Group items", menu[:200])

        # T4: the Group option leads to the group-creation subset form.
        await cdp.cmd("Page.navigate", {"url": BASE + "/new_group"}, session=s)
        await wait_for(cdp, s, "document.body.innerText.includes('Create a new group')",
                       desc="new_group page")
        body = await evaluate(cdp, s, "document.body.innerText")
        check("Create group" in body and "question schedule" not in body
              and "Comment template" not in body, "T4",
              "group creation form hides question-scoped sections", body[:200])

        # T5: group page has a Rooms tab (no Questions), child list and create entry.
        await cdp.cmd("Page.navigate", {"url": BASE + f"/rooms/{gid}"}, session=s)
        await wait_for(cdp, s, "document.body.innerText.includes('UI test child')",
                       desc="group rooms tab")
        tabs = await evaluate(cdp, s, """
            [...document.querySelectorAll('a,button')].map(e => e.textContent.trim())
              .filter(t => t === 'Rooms' || t === 'Questions').join(',')""")
        body = await evaluate(cdp, s, "document.body.innerText")
        check("Rooms" in tabs and "Questions" not in tabs
              and "Create a room in this group" in body, "T5",
              "group page: Rooms tab in the Questions slot with create entry",
              f"tabs={tabs}")

        # T6: create-in-group entry points preselect the group in the settings form.
        await cdp.cmd("Page.navigate", {"url": BASE + f"/new_room?group={gid}"}, session=s)
        await wait_for(cdp, s, "document.body.innerText.includes('Create a new room')",
                       desc="new_room page")
        sel = await evaluate(cdp, s, "document.querySelector('select') && document.querySelector('select').value")
        check(sel == gid, "T6", "?group= preselects the parent group", f"select value {sel}")

        await cdp.drain(1.5)
        errors = collect_errors(cdp)
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
