"""Delete cascades: removing a question or a room must remove every dependent
document (predictions in the head view and both logs, comments, room comments)
and unlink the question from its room — checked directly in Mongo, since the
API can only report "no such question" for orphans and live entities alike.

Covers the single-question delete route, the multidelete route and room delete."""
import asyncio, json, os, urllib.request
import pymongo
from confido_test import *


def delete(path, cookie):
    req = urllib.request.Request(BASE + path, headers={"Cookie": cookie}, method="DELETE")
    return urllib.request.urlopen(req)

MONGO_URL = os.environ.get("CONFIDO_MONGODB_URL", "mongodb://localhost/?replicaSet=rs01")
DB_NAME = os.environ.get("CONFIDO_DB_NAME", "confido_statetest")
db = pymongo.MongoClient(MONGO_URL)[DB_NAME]


def question_docs(qid):
    """Per-collection document counts for a question (all must be 0 after deletion)."""
    return {
        "questions": db.questions.count_documents({"_id": qid}),
        "userPred": db.userPred.count_documents({"question": qid}),
        "userPredHist": db.userPredHist.count_documents({"question": qid}),
        "groupPredHist": db.groupPredHist.count_documents({"question": qid}),
        "questionComments": db.questionComments.count_documents({"question": qid}),
    }


def room_docs(rid):
    return {
        "rooms": db.rooms.count_documents({"_id": rid}),
        "roomComments": db.roomComments.count_documents({"room": rid}),
        "presentations": db.presentations.count_documents({"room": rid}),
    }


def room_lists(rid, qid):
    return qid in (db.rooms.find_one({"_id": rid}) or {}).get("questions", [])


async def main():
    t = Suite()
    admin = login(ADMIN_EMAIL)
    member = login(MEMBER_EMAIL)
    rid = post("/rooms/add", {"name": "Cascade room", "description": "", "color": "RED", "icon": None},
               admin).strip('"')
    post(f"/rooms/{rid}/members/add", {"type": "existingMember", "user": "debugmember", "role": "forecaster"}, admin)
    names = ["Cascade q1", "Cascade q2", "Cascade q3"]
    for n in names:
        post(f"/rooms/{rid}/questions/add", {"_id": "", "name": n, "answerSpace": {"type": "binary"}}, admin)
    ws = await connect_ws(admin)
    await ws.send(json.dumps({"sub": [f"room/{rid}/questions"]}))
    qstate = next(u["data"]["data"] for u in await recv_updates(ws) if u["topic"] == f"room/{rid}/questions")
    await ws.close()
    qids = [next(q for q, qq in qstate.items() if qq["name"] == n) for n in names]
    q1, q2, q3 = qids

    # Populate every dependent collection for each question, plus room-level data.
    for q in qids:
        post(f"/questions/{q}/predict", {"type": "binary", "yesProb": 0.6}, admin)
        post(f"/questions/{q}/predict", {"type": "binary", "yesProb": 0.3}, member)
        post(f"/questions/{q}/comments/add", {"content": f"comment on {q}", "attachPrediction": True}, admin)
    post(f"/rooms/{rid}/comments/add", {"content": "room comment"}, admin)
    post(f"/rooms/{rid}/presentation/create", {"name": "Cascade pres"}, admin)

    before = question_docs(q1)
    t.check(all(v > 0 for v in before.values()) and room_lists(rid, q1),
            "TEST-0", "fixture populated every dependent collection", str(before))

    # Single delete route.
    delete(f"/questions/{q1}", admin)
    after = question_docs(q1)
    t.check(all(v == 0 for v in after.values()), "TEST-1",
            "question delete removes the question, head + log predictions and comments", str(after))
    t.check(not room_lists(rid, q1), "TEST-2", "question delete unlinks the question from its room")
    intact = question_docs(q2)
    t.check(intact == before, "TEST-3", "sibling question's data is untouched", f"{intact} vs {before}")

    # Multidelete route.
    post("/questions/multidelete", {"questions": [q2]}, admin)
    after = question_docs(q2)
    t.check(all(v == 0 for v in after.values()) and not room_lists(rid, q2), "TEST-4",
            "multidelete cascades and unlinks like single delete", str(after))

    # Room delete: remaining question, room comments (and the presentation) go with it.
    rb = room_docs(rid)
    t.check(rb["roomComments"] > 0 and rb["presentations"] > 0, "TEST-5",
            "room fixture has a room comment and a presentation", str(rb))
    delete(f"/rooms/{rid}", admin)
    after_q = question_docs(q3)
    after_r = room_docs(rid)
    t.check(all(v == 0 for v in after_q.values()), "TEST-6",
            "room delete cascades to its questions' predictions and comments", str(after_q))
    t.check(after_r["rooms"] == 0 and after_r["roomComments"] == 0, "TEST-7",
            "room delete removes the room and its room comments", str(after_r))
    t.check(after_r["presentations"] == 0, "TEST-8",
            "room delete removes the room's presentation", str(after_r))
    t.finish()


asyncio.run(main())
