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
cp -r ./src/jvmMain/resources/* "$tmpd/static/" || exit 1

gzip -k "$tmpd/static/confido1.js" || exit 1

git rev-parse HEAD > "$tmpd/COMMIT_ID"
if git diff HEAD | grep -q .; then git diff HEAD >"$tmpd/LOCAL_CHANGES.diff"; fi

ssh=root@${server:-prod1.confido.tools}
rsync -rlvc --del "$tmpd/" $ssh:/usr/local/lib/confido/"$slot"/

ssh $ssh 'for fn in $(grep -Fxl CONFIDO_SLOT='"$slot"' /etc/confido/*.env) ; do name="confido@$(basename "$fn" .env)"; echo "Restarting $name"; systemctl restart $name; done'
