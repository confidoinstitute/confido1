#!/bin/bash

mkdir -p bak

for fn in /etc/confido/*.env; do
    CONFIDO_DB_NAME=""
    . "$fn"

    iname="$(basename "$fn" .env)"
    sname="confido@$iname.service"

    echo $sname $CONFIDO_DB_NAME

    mongoexport --db "$CONFIDO_DB_NAME" -c rooms > bak/"$iname-rooms".json
    mongoexport --db "$CONFIDO_DB_NAME" -c questions > bak/"$iname-questions".json

    systemctl stop "$sname"
    ./sort_questions.py "$CONFIDO_DB_NAME"
    systemctl start "$sname"

done

