#!/usr/bin/env python3
"""Presentation editor end-to-end (admin): the flow list renders its rows and the
hover-revealed affordances are actually wired — regression guard for the emotion
class-merging bug where `className =` and the `css {}` prop clobbered each other,
leaving the insert pills and drag handles permanently invisible. Verifies idle
opacity 0, hover reveal via real CDP mouse events, the insert menu opening from
a pill, and sweeps for console errors."""
import asyncio
import json
import sys

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE


async def main():
    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)

        code = await login_page(cdp, s, ADMIN_EMAIL)
        if code != 200:
            print("FAIL: login ->", code); sys.exit(1)

        # Seed: a presentation with two note items in testroom (idempotent).
        st = await evaluate(cdp, s, """
            fetch('/rooms/testroom/presentation/create', {method:'POST',
                headers:{'Content-Type':'application/json'}, body: JSON.stringify({name:'T'})})
              .then(r => r.status)""", await_promise=True)
        if st not in (200, 400):
            print("FAIL: presentation create ->", st); sys.exit(1)
        st = await evaluate(cdp, s, """
            fetch('/rooms/testroom/presentation/edit', {method:'POST',
                headers:{'Content-Type':'application/json'},
                body: JSON.stringify({items:[
                    {type:'note', id:'bn1', text:'first note', warning:false},
                    {type:'note', id:'bn2', text:'second note', warning:false}]})})
              .then(r => r.status)""", await_promise=True)
        if st != 200:
            print("FAIL: presentation edit ->", st); sys.exit(1)

        await cdp.cmd("Page.navigate", {"url": BASE + "/room/testroom/presentation"}, session=s)

        # Marker classes present: one hover zone above the list + one per row; a
        # drag handle per row. Their absence = the class-merging regression.
        pills = await wait_for(cdp, s, "document.querySelectorAll('.insert-pill').length || false",
                               desc="insert pills mounted")
        handles = await evaluate(cdp, s, "document.querySelectorAll('.drag-handle').length")
        if pills < 3 or handles < 2:
            print(f"FAIL: expected >=3 pills / >=2 handles, got {pills} / {handles}"); sys.exit(1)
        print(f"PASS: affordances mounted ({pills} pills, {handles} drag handles)")

        op_idle = await evaluate(cdp, s,
            "getComputedStyle(document.querySelectorAll('.insert-pill')[1]).opacity")
        if op_idle != "0":
            print("FAIL: pill visible at idle (opacity", op_idle, ")"); sys.exit(1)

        # Hover the gap between the rows -> its pill fades in.
        r = json.loads(await evaluate(cdp, s,
            "JSON.stringify(document.querySelectorAll('.insert-pill')[1].parentElement.getBoundingClientRect())"))
        x, y = r["x"] + r["width"] / 2, r["y"] + r["height"] / 2
        await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": x, "y": y}, session=s)
        await wait_for(cdp, s,
            "getComputedStyle(document.querySelectorAll('.insert-pill')[1]).opacity === '1' ? '1' : false",
            desc="insert pill revealed on gap hover")
        print("PASS: insert pill hidden at idle, revealed on hover")

        # Hover a row -> its drag handle fades in.
        rr = json.loads(await evaluate(cdp, s,
            "JSON.stringify(document.querySelectorAll('.drag-handle')[0].parentElement.getBoundingClientRect())"))
        await cdp.cmd("Input.dispatchMouseEvent",
                      {"type": "mouseMoved", "x": rr["x"] + rr["width"] / 2, "y": rr["y"] + rr["height"] / 2},
                      session=s)
        await wait_for(cdp, s,
            "getComputedStyle(document.querySelectorAll('.drag-handle')[0]).opacity === '1' ? '1' : false",
            desc="drag handle revealed on row hover")
        print("PASS: drag handle revealed on row hover")

        # Drag the first note below the second by its handle. Mid-drag, rows must
        # carry a live transform (regression: the transform was written as an
        # ignored `style` key inside the emotion builder -> no preview at all);
        # on drop the notes swap locally.
        h = json.loads(await evaluate(cdp, s,
            "JSON.stringify(document.querySelectorAll('.drag-handle')[0].getBoundingClientRect())"))
        hx, hy = h["x"] + h["width"] / 2, h["y"] + h["height"] / 2
        await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": hx, "y": hy}, session=s)
        await cdp.cmd("Input.dispatchMouseEvent",
                      {"type": "mousePressed", "x": hx, "y": hy, "button": "left", "buttons": 1,
                       "clickCount": 1}, session=s)
        for dy in range(5, 51, 5):
            await cdp.cmd("Input.dispatchMouseEvent",
                          {"type": "mouseMoved", "x": hx, "y": hy + dy, "buttons": 1}, session=s)
        await wait_for(cdp, s,
            "[...document.querySelectorAll('.drag-handle')].some(h =>"
            " getComputedStyle(h.parentElement).transform !== 'none') ? true : false",
            desc="live transform on rows mid-drag")
        print("PASS: live reorder preview (transform applied mid-drag)")
        await cdp.cmd("Input.dispatchMouseEvent",
                      {"type": "mouseReleased", "x": hx, "y": hy + 50, "button": "left",
                       "clickCount": 1}, session=s)
        await wait_for(cdp, s, """(function() {
            const vals = [...document.querySelectorAll('input')].map(i => i.value)
                .filter(v => v === 'first note' || v === 'second note');
            return vals.length === 2 && vals[0] === 'second note';
        })()""", desc="drop swapped the two notes")
        print("PASS: drop reordered the notes")

        # Click a gap's pill -> straight into the add dialog (no intermediate
        # menu) with the sectioned type chips. dnd-kit suppresses clicks arriving
        # right after a drop (intended, protects real users from accidental
        # activations), so give it a beat and retry.
        await asyncio.sleep(1.0)
        r = json.loads(await evaluate(cdp, s,
            "JSON.stringify(document.querySelectorAll('.insert-pill')[1].parentElement.getBoundingClientRect())"))
        x, y = r["x"] + r["width"] / 2, r["y"] + r["height"] / 2
        for attempt in range(3):
            await cdp.cmd("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": x, "y": y}, session=s)
            for t in ("mousePressed", "mouseReleased"):
                await cdp.cmd("Input.dispatchMouseEvent",
                              {"type": t, "x": x, "y": y, "button": "left", "clickCount": 1}, session=s)
            try:
                await wait_for(cdp, s, "document.body.innerText.includes('Show on projector')",
                               timeout=3.0, desc="add dialog opened from the pill")
                break
            except RuntimeError:
                if attempt == 2:
                    raise
        print("PASS: gap pill opens the add dialog with sectioned type chips")

        # Insert a note via its chip + OK -> the editor becomes dirty; an in-app
        # tab switch must then hit the router blocker's confirm dialog, and
        # "Stay" must keep us on the editor with the changes intact. (Row
        # warning-toggles also say "Note" — take the dialog's chip, i.e. the
        # last match in DOM order.)
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')]"
            ".filter(b => b.textContent.trim() === 'Note').at(-1).click()")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')]"
            ".filter(b => b.textContent.trim() === 'OK').at(-1).click()")
        await wait_for(cdp, s, "document.body.innerText.includes('Unsaved changes')",
                       desc="editor dirty after dialog insert")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('a')].find(a => a.textContent.trim() === 'Questions').click()")
        await wait_for(cdp, s, "document.body.innerText.includes('Leave without saving?')",
                       desc="leave-guard dialog on in-app navigation")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'Stay').click()")
        await wait_for(cdp, s,
            "!document.body.innerText.includes('Leave without saving?')"
            " && document.querySelectorAll('.insert-pill').length > 0 ? true : false",
            desc="Stay keeps the editor open with changes intact")
        print("PASS: unsaved-changes guard blocks navigation; Stay returns to the editor")

        # The question picker's suggestions must overlay the config dialog (a
        # body-level react-select portal), not scroll inside it. Open the dialog
        # via the bottom "+ Add item", pick a type with a question picker,
        # pop the menu with a real ArrowDown, and check where it rendered.
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')].find(b => b.textContent.trim() === '+ Add item').click()")
        await wait_for(cdp, s, "document.body.innerText.includes('Show on projector')",
                       desc="add dialog from the bottom add button")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')]"
            ".filter(b => b.textContent.trim() === 'Set question state').at(-1).click()")
        await wait_for(cdp, s,
            "document.querySelectorAll('input[role=combobox]').length > 0 ? true : false",
            desc="question picker after selecting a type chip")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('input[role=combobox]')].at(-1).focus()")
        for t in ("keyDown", "keyUp"):
            await cdp.cmd("Input.dispatchKeyEvent",
                          {"type": t, "key": "ArrowDown", "code": "ArrowDown",
                           "windowsVirtualKeyCode": 40}, session=s)
        # The listbox must render OUTSIDE the dialog's subtree, in a body-level
        # portal. (Do not match emotion class suffixes like "-menuPortal" — those
        # labels exist only in dev builds.)
        await wait_for(cdp, s, """(function() {
            const lb = document.querySelector('[id$="-listbox"]');
            const input = [...document.querySelectorAll('input[role=combobox]')].at(-1);
            if (!lb || !input) return false;
            return document.body.contains(lb) && !input.closest('body > *').contains(lb);
        })()""", desc="picker menu portaled outside the dialog subtree")
        print("PASS: question picker menu overlays as a body-level portal")

        # --- Foreach loop: create -> auto-drill-in -> add a step (defaults to the
        # current question) -> Back -> loop row previews the body. First close the
        # open picker menu + dialog via backdrop clicks (Escape is unreliable while
        # react-select's input holds focus).
        for _ in range(3):
            for tp in ("mousePressed", "mouseReleased"):
                await cdp.cmd("Input.dispatchMouseEvent",
                              {"type": tp, "x": 5, "y": 5, "button": "left", "clickCount": 1},
                              session=s)
            await asyncio.sleep(0.3)
        await wait_for(cdp, s, "!document.body.innerText.includes('change type') ? true : false",
                       desc="config dialog closed via backdrop")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')].find(b => b.textContent.trim() === '+ Add item').click()")
        await wait_for(cdp, s, "document.body.innerText.includes('Show on projector')",
                       desc="add dialog for the loop")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')]"
            ".filter(b => b.textContent.includes('Repeat over questions')).at(-1).click()")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')]"
            ".filter(b => b.textContent.trim() === 'OK').at(-1).click()")
        await wait_for(cdp, s, "document.body.innerText.includes('Repeated steps')",
                       desc="loop body dialog auto-opens after creating a loop")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')]"
            ".find(b => b.textContent.includes('Add first item')).click()")
        await wait_for(cdp, s, "document.body.innerText.includes('Show on projector')",
                       desc="add dialog inside the loop body")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')]"
            ".filter(b => b.textContent.trim() === 'Question text').at(-1).click()")
        await wait_for(cdp, s,
            "document.querySelectorAll('input[role=combobox]').length > 0 ? true : false",
            desc="question picker in the body editor")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')]"
            ".filter(b => b.textContent.trim() === 'OK').at(-1).click()")
        await wait_for(cdp, s, "document.body.innerText.includes('the current question')",
                       desc="body step defaults to the current question")
        await evaluate(cdp, s,
            "[...document.querySelectorAll('button')]"
            ".filter(b => b.textContent.trim() === 'Done').at(-1).click()")
        await wait_for(cdp, s,
            "!document.body.innerText.includes('Repeated steps')"
            " && document.body.innerText.includes('· Show') ? true : false",
            desc="loop row previews its body after Done")
        print("PASS: foreach loop — create, body dialog, current-question step, preview")

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
