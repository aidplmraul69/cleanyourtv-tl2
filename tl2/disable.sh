#!/usr/bin/env bash
# Disable every package in a debloat list (one per line, # comments allowed). Undo with restore.sh.
set -euo pipefail
TV="${TV_HOST:?set TV_HOST to the TV IP, e.g. TV_HOST=192.168.1.50}:5555"
adb connect "$TV" >/dev/null
grep -vE '^\s*(#|$)' "${1:?usage: disable.sh <list>}" | sed -E 's/[[:space:]]+#.*$//' | while read -r p; do
  echo -n "$p: "; adb -s "$TV" shell pm disable-user --user 0 "$p" </dev/null
done
