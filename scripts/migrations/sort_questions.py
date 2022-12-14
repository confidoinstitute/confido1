#!/usr/bin/python3

import sys
from pymongo import MongoClient

dbname = sys.argv[1]

client = MongoClient()

if dbname not in client.list_database_names():
    raise Exception("Database does not exist")

db = client[dbname]

rooms = { r['_id']: r for r in db['rooms'].find() }
questions = { q['_id']: q for q in db['questions'].find() }


for room in rooms.values():
    qids = room['questions']
    qids_sorted = [ qid for (_, qid) in  sorted(( (questions[qid]['name'], qid) for qid in qids  ), reverse=True) ]
    print(qids)
    print(qids_sorted)
    db['rooms'].update_one({'_id': room['_id']}, {"$set": {"questions": qids_sorted}})


