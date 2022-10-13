#!/bin/bash

slot="$1"
[ -n "$slot" ] || exit 1
./gradlew clean || exit 1
./gradlew build || exit 1
tmpd=$(mktemp -d)
chmod a+rx "$tmpd" # permissions get copied to server, mktemp does 0700 by default
tar xvf build/distributions/confido1-1.0-SNAPSHOT.tar --xform='s#^confido1-1.0-SNAPSHOT/##' --show-transformed-names -C "$tmpd" || exit 1
mkdir "$tmpd/static"
cp build/distributions/confido1.js "$tmpd/static" || exit 1
gzip -k "$tmpd/static/confido1.js" || exit 1

rsync -rlvc "$tmpd/" root@prod1.confido.tools:/usr/local/lib/confido/"$slot"/


