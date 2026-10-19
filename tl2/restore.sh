#!/usr/bin/env bash
# Re-enable every package in a debloat list and make the stock launcher home again.
set -euo pipefail
TV="${TV_HOST:?set TV_HOST to the TV IP, e.g. TV_HOST=192.168.1.50}:5555"
STOCK="${2:-com.google.android.tvlauncher}"
adb connect "$TV" >/dev/null
grep -vE '^\s*(#|$)' "${1:?usage: restore.sh <list> [stock launcher]}" | sed -E 's/[[:space:]]+#.*$//' | while read -r p; do
  echo -n "$p: "; adb -s "$TV" shell pm enable "$p" </dev/null
done
adb -s "$TV" shell pm enable "$STOCK"
adb -s "$TV" shell cmd package set-home-activity --user 0 "$STOCK/.MainActivity" || true
