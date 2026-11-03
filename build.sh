#!/bin/sh
# Rebuild the Magisk module zip from module/ (copies the latest bb_capture.py in first).
set -e
cd "$(dirname "$0")"
cp bb_capture.py module/bb_capture.py
VER=$(sed -n 's/^version=//p' module/module.prop)
rm -f "backbone-capture-magisk-$VER.zip"
(cd module && zip -r -X "../backbone-capture-magisk-$VER.zip" . -x '*__pycache__*')
echo "built backbone-capture-magisk-$VER.zip"
