#!/usr/bin/env python3
"""End-to-end browser test (admin): real bundle + MultiplexWS against the scratch
server — slices apply, live updates flow, members tab and admin directory render
from their topics, no console errors."""
import asyncio
import sys

from cdp_test import *
from confido_test import ADMIN_EMAIL, BASE, MEMBER_EMAIL


async def main():
    async with await browser_ws() as ws:
        cdp = CDP(ws)
        s = await new_page(cdp)

        code = await login_page(cdp, s, ADMIN_EMAIL)
        print("login status:", code)
        if code != 200:
            print("FAIL: login"); sys.exit(1)

        await cdp.cmd("Page.navigate", {"url": BASE + "/"}, session=s)
        await wait_for(cdp, s, "!!(window.curRooms && window.curSelf && window.curSelf.user)",
                       desc="app state via MultiplexWS (self slice carries own user)")
        print("PASS: app connected, topic slices applied")

        # Live update: rename the room, watch the rooms slice follow
        st = await evaluate(cdp, s, """
            fetch('/rooms/testroom/edit', {method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({id:'testroom', name:'Browser renamed', description:'x',
                                      icon:null, color:'GRAY'})}).then(r => r.status)""", await_promise=True)
        if st != 200:
            print("FAIL: room edit ->", st); sys.exit(1)
        await wait_for(cdp, s, "window.curRooms.testroom.name === 'Browser renamed'",
                       desc="live room rename via websocket")
        print("PASS: live update applied (room renamed)")

        # Live self update: presenterInfo is a projected top-level SelfState field
        st = await evaluate(cdp, s, """
            fetch('/presenter/set', {method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({type:'empty'})}).then(r => r.status)""", await_promise=True)
        if st != 200:
            print("FAIL: presenter/set ->", st); sys.exit(1)
        await wait_for(cdp, s, "!!(window.curSelf && window.curSelf.presenterInfo)",
                       desc="live self update (presenterInfo projected field)")
        print("PASS: live self-topic update (presenterInfo arrived)")

        # Rich client-side describer: present a question, expand the floating panel,
        # expect the question NAME in the label (PresenterViewDescription + useQuestion).
        st = await evaluate(cdp, s, """
            fetch('/rooms/testroom/questions/add', {method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({_id:'', name:'Presented q', answerSpace:{type:'binary'}})})
              .then(r => r.status)""", await_promise=True)
        if st != 200:
            print("FAIL: questions/add ->", st); sys.exit(1)
        qid = await wait_for(cdp, s,
            "(Object.entries((window.curQuestions||{}).questions||{}).filter(([k,v])=>v.name==='Presented q').map(([k])=>k)[0]) || false",
            desc="new question in questions slice")
        st = await evaluate(cdp, s, f"""
            fetch('/presenter/set', {{method:'POST', headers:{{'Content-Type':'application/json'}},
                body: JSON.stringify({{type:'question', question:'{qid}'}})}}).then(r => r.status)""",
            await_promise=True)
        if st != 200:
            print("FAIL: presenter/set question ->", st); sys.exit(1)
        await wait_for(cdp, s, "!!document.querySelector('button[title=\"Presentation controls\"]')",
                       desc="floating presenter pill")
        await evaluate(cdp, s, "document.querySelector('button[title=\"Presentation controls\"]').click()")
        await wait_for(cdp, s,
            "document.body.innerText.includes('Presenting:') && document.body.innerText.includes('Presented q')",
            desc="rich description with question name in floating panel")
        print("PASS: floating panel shows rich client-side description (question name)")

        # Members tab renders from the members topic
        st = await evaluate(cdp, s, """
            fetch('/rooms/testroom/members/add', {method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({type:'payloads.requests.AddedExistingMember',
                                      user:'debugmember', role:'forecaster'})}).then(r => r.status)""",
            await_promise=True)
        if st != 200:
            print("FAIL: members/add ->", st); sys.exit(1)
        await evaluate(cdp, s, "window.history.pushState({},'','/room/testroom/members'); window.dispatchEvent(new PopStateEvent('popstate'))")
        await wait_for(cdp, s, "document.body.innerText.includes('debugmember')",
                       desc="member row rendered from members topic")
        if not await evaluate(cdp, s, f"document.body.innerText.includes('{MEMBER_EMAIL}')"):
            print("FAIL: admin does not see member email in members tab"); sys.exit(1)
        print("PASS: members tab renders from the members topic (name + email for admin)")

        # Admin user directory renders from the on-demand users subscription
        await evaluate(cdp, s, "window.history.pushState({},'','/admin/users'); window.dispatchEvent(new PopStateEvent('popstate'))")
        await wait_for(cdp, s, f"document.body.innerText.includes('{MEMBER_EMAIL}')",
                       desc="admin directory rendered from on-demand users subscription")
        print("PASS: admin user directory renders from on-demand subscription")

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
