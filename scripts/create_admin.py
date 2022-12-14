#!/usr/bin/python3

import sys
from passlib.hash import argon2
from pymongo import MongoClient
import random
import time, datetime
import getpass
import string

now = lambda: datetime.datetime.utcnow().isoformat() + 'Z'

def generate_id():
    return ''.join( random.choice(string.ascii_letters + string.digits) for _ in range(16) )

dbname = sys.argv[1]
email  = sys.argv[2]
try: name = sys.argv[3]
except IndexError: name = None



client = MongoClient()

if dbname not in client.list_database_names():
    raise Exception("Database does not exist")

db = client[dbname]

users = db['users']

pw = generate_id()
print("Password:",pw)
pwh = argon2.using(parallelism=1).hash(pw)
users.insert_one(
         dict(_id=generate_id(), type='ADMIN', email=email, emailVerified=True, nick=name, password=pwh, createdAt=now(), lastLoginAt=now()),
    )


print("Server needs to be restarted in order to see the new user")
