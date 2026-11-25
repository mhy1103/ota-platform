#!/usr/bin/env bash
# cap.sh <cdp-json> <name> : decode CDP screenshot JSON to shots/<name>.jpg
python3 /tmp/ota-e2e/shots/dec.py "$1" "/tmp/ota-e2e/shots/$2.jpg"
