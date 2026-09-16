#!/usr/bin/env python3
"""Exercise as many Confido server code paths as possible (for native-image tracing agent)."""
import asyncio, json, socket, sys, threading, time
import urllib.request, urllib.error, http.cookiejar
import websockets

BASE = "http://127.0.0.1:8123"
MAIL_SINK_PORT = 8125

def start_mail_sink(port=MAIL_SINK_PORT):
    """Minimal SMTP sink. Run the server with CONFIDO_SMTP_HOST=127.0.0.1
    CONFIDO_SMTP_PORT=8125 (NOT CONFIDO_MAIL_DEBUG=1) so real mail sending is
    exercised — debug mode short-circuits before the Jakarta Mail transport is
    reflectively instantiated, which is exactly what the tracing agent must see."""
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", port))
    srv.listen(5)
    def serve():
        while True:
            c, _ = srv.accept()
            try:
                c.sendall(b"220 sink ESMTP\r\n")
                f = c.makefile("rb")
                for line in f:
                    u = line.decode(errors="replace").strip().upper()
                    if u.startswith("EHLO"): c.sendall(b"250-sink\r\n250 8BITMIME\r\n")
                    elif u.startswith("HELO"): c.sendall(b"250 sink\r\n")
                    elif u.startswith("DATA"):
                        c.sendall(b"354 go\r\n")
                        for _ in iter(f.readline, b".\r\n"): pass
                        c.sendall(b"250 ok\r\n")
                        print("  mail sink: received a message")
                    elif u.startswith("QUIT"):
                        c.sendall(b"221 bye\r\n"); break
                    else: c.sendall(b"250 ok\r\n")
            except OSError:
                pass
            finally:
                c.close()
    threading.Thread(target=serve, daemon=True).start()

class Client:
    def __init__(self):
        self.cj = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cj))

    def req(self, method, path, body=None, ctype="application/json", raw=False, expect_ok=True):
        url = BASE + path
        data = None
        if body is not None:
            data = body if isinstance(body, bytes) else json.dumps(body).encode()
        r = urllib.request.Request(url, data=data, method=method)
        if body is not None:
            r.add_header("Content-Type", ctype)
        try:
            with self.opener.open(r, timeout=30) as resp:
                out = resp.read()
                print(f"  {method} {path} -> {resp.status}")
                return out
        except urllib.error.HTTPError as e:
            print(f"  {method} {path} -> {e.code} {'(EXPECTED-ISH)' if not expect_ok else '!!! ' + e.read().decode(errors='replace')[:200]}")
            return None

    def get(self, path, **kw): return self.req("GET", path, **kw)
    def post(self, path, body=None, **kw): return self.req("POST", path, body, **kw)
    def delete(self, path, body=None, **kw): return self.req("DELETE", path, body, **kw)

    def cookie_header(self):
        return "; ".join(f"{c.name}={c.value}" for c in self.cj)

async def ws_state(client, label, nmsgs=1, timeout=15):
    try:
        async with websockets.connect(BASE.replace("http", "ws") + "/state",
                                      additional_headers={"Cookie": client.cookie_header()}) as ws:
            for i in range(nmsgs):
                msg = await asyncio.wait_for(ws.recv(), timeout=timeout)
            print(f"  WS /state ({label}): got {nmsgs} state frame(s), last {len(msg)} bytes")
            return json.loads(msg)
    except Exception as e:
        print(f"  WS /state ({label}) FAILED: {e}")
        return None

def main():
    start_mail_sink()
    admin = Client()
    member = Client()

    print("== login ==")
    admin.post("/login", {"email": "admin@confido.example", "password": "pass", "validity": "PERMANENT"})
    member.post("/login", {"email": "member@confido.example", "password": "pass", "validity": "TRANSIENT"})
    # exercise failed login too
    admin_bad = Client()
    admin_bad.post("/login", {"email": "admin@confido.example", "password": "wrong", "validity": "PERMANENT"}, expect_ok=False)
    # devmode username login
    dev = Client()
    dev.get("/login_users")
    dev.post("/login_users", {"username": "debugmember", "validity": "TRANSIENT"})
    # email link login — actually sends mail (to the sink); a 503 here means the
    # SMTP transport path is broken (e.g. missing native-image reflection entries)
    Client().post("/login_email/create", {"email": "member@confido.example", "url": "/", "validity": "PERMANENT"})

    print("== static/front ==")
    admin.get("/")
    admin.get("/rooms/icons", expect_ok=False)

    print("== room create/edit ==")
    rid = admin.post("/rooms/add", {
        "name": "Graal Test Room", "description": "native image test", "color": "BLUE", "icon": None,
    })
    rid = rid.decode().strip().strip('"') if rid else "testroom"
    print(f"  room id: {rid}")
    admin.post(f"/rooms/{rid}/edit", {
        "name": "Graal Test Room", "description": "edited", "color": "GREEN", "icon": None,
        "scoring": {"scoreboardMode": "PUBLIC"},
    })

    print("== members / invites ==")
    admin.post(f"/rooms/{rid}/members/add", {"type": "payloads.requests.AddedExistingMember",
                                             "user": "debugmember", "role": "forecaster"}, expect_ok=False)
    inv = admin.post(f"/rooms/{rid}/invites/create", {"description": "link", "role": "forecaster",
        "nicknamePolicy": "OPTIONAL", "emailPolicy": "OPTIONAL", "targetUserType": "GUEST",
        "preventDuplicateNicknames": False})
    token = None
    if inv:
        try:
            j = json.loads(inv)
            token = j if isinstance(j, str) else j.get("token")
        except Exception:
            token = inv.decode().strip('"')
    print(f"  invite token: {token}")
    if token:
        guest = Client()
        guest.get(f"/join/{token}/check")
        guest.post(f"/rooms/{rid}/invite/accept_newuser",
                   {"inviteToken": token, "userNick": "GraalGuest", "email": None,
                    "validity": "PERMANENT", "followPresentation": False}, expect_ok=False)

    print("== questions ==")
    qb = admin.post(f"/rooms/{rid}/questions/add",
                    {"_id": "", "name": "Will the native image work?", "answerSpace": {"type": "binary"},
                     "description": "binary q"})
    qbid = json.loads(qb) if qb else None
    qbid = qbid if isinstance(qbid, str) else (qbid or {}).get("_id", "")
    qn = admin.post(f"/rooms/{rid}/questions/add",
                    {"_id": "", "name": "How many MB RSS?", "answerSpace":
                        {"type": "numeric", "min": 0.0, "max": 1000.0, "unit": "MB"},
                     "description": "numeric q"})
    qnid = json.loads(qn) if qn else None
    qnid = qnid if isinstance(qnid, str) else (qnid or {}).get("_id", "")
    qd = admin.post(f"/rooms/{rid}/questions/add",
                    {"_id": "", "name": "When shipped?", "answerSpace":
                        {"type": "numeric", "min": 1735689600.0, "max": 1798761600.0, "representsDays": True},
                     "description": "date q"})
    print(f"  question ids: {qbid} {qnid}")

    print("== state ws (admin + member) ==")
    st = asyncio.run(ws_state(admin, "admin"))
    if st and qbid == "":
        # fetch ids from state if add didn't return them
        rooms = st.get("rooms", {})
        room = rooms.get(rid, {})
        qs = room.get("questions", [])
        if len(qs) >= 2:
            qbid, qnid = qs[0], qs[1]
            print(f"  ids from state: {qbid} {qnid}")
    asyncio.run(ws_state(member, "member"))

    print("== predictions ==")
    for c, p in ((admin, 0.8), (member, 0.3), (dev, 0.5)):
        c.post(f"/questions/{qbid}/predict", {"type": "binary", "yesProb": p})
    for c, m in ((admin, 200.0), (member, 400.0)):
        c.post(f"/questions/{qnid}/predict",
               {"type": "truncatedNormal", "space": {"type": "numeric", "min": 0.0, "max": 1000.0, "unit": "MB"},
                "pseudoMean": m, "pseudoStdev": 50.0})

    print("== comments ==")
    admin.post(f"/questions/{qbid}/comments/add", {"content": "admin comment", "attachPrediction": True})
    member.post(f"/questions/{qbid}/comments/add", {"content": "member comment **md**", "attachPrediction": False})
    admin.post(f"/rooms/{rid}/comments/add", {"content": "room-level comment", "attachPrediction": False})

    print("== question state / edit / resolve ==")
    admin.post(f"/questions/{qbid}/state", b'"CLOSED"', ctype="application/json")
    admin.post(f"/questions/{qbid}/state", b'"OPEN"', ctype="application/json")
    admin.post(f"/questions/{qbid}/edit", {"type": "payloads.requests.EditQuestionFlag",
                                           "fieldType": "GROUP_PRED_VISIBLE", "value": True})
    admin.post(f"/questions/{qbid}/edit", {"type": "payloads.requests.EditQuestionResolution",
                                           "resolution": {"type": "binary", "value": True}})
    admin.post(f"/questions/{qbid}/edit", {"type": "payloads.requests.EditQuestionFlag",
                                           "fieldType": "RESOLUTION_VISIBLE", "value": True})
    admin.post(f"/questions/{qnid}/edit", {"type": "payloads.requests.EditQuestionResolution",
        "resolution": {"type": "numeric", "space": {"type": "numeric", "min": 0.0, "max": 1000.0, "unit": "MB"},
                       "value": 250.0}}, expect_ok=False)

    print("== scores / exports / calibration ==")
    admin.get(f"/questions/{qbid}/my_score", expect_ok=False)
    admin.get(f"/rooms/{rid}/scoreboard.api", expect_ok=False)
    admin.get(f"/rooms/{rid}/my_rank", expect_ok=False)
    admin.get("/scores/my")
    admin.get(f"/export.csv?questions={qbid},{qnid}&what=predictions&group=false&history=LAST")
    admin.get(f"/export.csv?questions={qbid},{qnid}&what=predictions&group=true&buckets=16")
    admin.get(f"/export.csv?questions={qbid}&what=comments")
    admin.post("/calibration", {})
    admin.post("/calibration", {"who": {"type": "payloads.requests.Everyone"}}, expect_ok=False)
    admin.post("/calibration/detail", {}, expect_ok=False)

    print("== profile / misc ==")
    member.post("/profile/nick", {"name": "GraalMember"})
    member.post("/profile/password", {"currentPassword": "pass", "newPassword": "pass2"}, expect_ok=False)
    admin.post("/feedback?url=/rooms/x", b"native image test feedback", ctype="text/plain")
    admin.get(f"/questions/{qbid}/updates", expect_ok=False)

    print("== snapshots / update plot (extension) ==")
    admin.post(f"/api/questions/{qbid}/snapshots/add", {"ts": 0, "title": "Baseline", "owner": None, "public": True}, expect_ok=False)
    admin.post(f"/api/questions/{qbid}/update_plot",
               {"question": qbid, "timepoint1": {"type": "now"}, "timepoint2": {"type": "now"},
                "referenceQuestion": None}, expect_ok=False)

    print("== training (extension) ==")
    admin.get("/api/training/next", expect_ok=False)
    admin.post("/api/training/submit", {"questionId": "tq1", "yesProb": 0.7}, expect_ok=False)
    admin.get("/api/training/stats", expect_ok=False)
    admin.post("/api/training/reset", b"", ctype="application/json", expect_ok=False)

    print("== presentation ==")
    admin.post(f"/rooms/{rid}/presentation/create", {"name": "Graal preso"}, expect_ok=False)
    # upload a tiny PDF (exercises pdfbox rendering)
    try:
        import zlib
        pdf = (b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
               b"2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
               b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n"
               b"xref\n0 4\n0000000000 65535 f \n0000000009 00000 n \n0000000055 00000 n \n0000000105 00000 n \n"
               b"trailer<</Size 4/Root 1 0 R>>\nstartxref\n170\n%%EOF\n")
        boundary = "----graalpdf"
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.pdf\"\r\n"
                f"Content-Type: application/pdf\r\n\r\n").encode() + pdf + f"\r\n--{boundary}--\r\n".encode()
        admin.post(f"/rooms/{rid}/presentation/upload-slides", body,
                   ctype=f"multipart/form-data; boundary={boundary}", expect_ok=False)
        admin.post(f"/rooms/{rid}/presentation/start", {}, expect_ok=False)
        admin.post(f"/rooms/{rid}/presentation/navigate", {"direction": "NEXT"}, expect_ok=False)
        admin.post(f"/rooms/{rid}/presentation/stop", {}, expect_ok=False)
    except Exception as e:
        print("  presentation exercise failed:", e)

    print("== multiedit / multidelete ==")
    admin.post("/questions/multiedit", {"questions": [qnid], "edit":
        {"type": "payloads.requests.EditQuestionFlag", "fieldType": "VISIBLE", "value": True}}, expect_ok=False)

    print("== final ws + logout ==")
    asyncio.run(ws_state(admin, "final"))
    member.post("/logout", {})
    admin.post("/logout", {})
    print("DONE")

if __name__ == "__main__":
    main()
