#!/usr/bin/env bash
# Screenshot the TV to $1 (MediaTek screencap prints log lines before the PNG; strip them).
adb -s "${TV_HOST:?set TV_HOST to the TV IP}:5555" exec-out screencap -p | python3 -c 'import sys;d=sys.stdin.buffer.read();sys.stdout.buffer.write(d[d.find(b"\x89PNG"):])' > "${1:?out.png}"
