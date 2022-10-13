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


client = MongoClient()
db = client[dbname]

users = db['users']

pw = generate_id()
print("Password:",pw)
pwh = argon2.hash(pw)
users.insert_one(
         dict(id=generate_id(), type='ADMIN', email=email, emailVerified=True, nick="", password=pwh, createdAt=now(), lastLoginAt=now()),
    )


print("Server needs to be restarted in order to see the new user")