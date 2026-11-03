#!/bin/sh
# Rebuild the Magisk module zip from module/.
# 1) Build the companion app (signing keys are NOT in this repo; pass your own with -P flags):
#      cd companion-app && ./gradlew assembleRelease -PBBC_STORE_FILE=... -PBBC_STORE_PASSWORD=... \
#        -PBBC_KEY_ALIAS=... -PBBC_KEY_PASSWORD=...
# 2) Run this script. It copies the APK and the latest bb_capture.py into module/ and zips it.
set -e
cd "$(dirname "$0")"
cp bb_capture.py module/bb_capture.py
cp companion-app/app/build/outputs/apk/release/app-release.apk module/BackboneCapture.apk
VER=$(sed -n 's/^version=//p' module/module.prop)
rm -f "backbone-capture-magisk-$VER.zip"
(cd module && zip -r -X "../backbone-capture-magisk-$VER.zip" . -x '*__pycache__*')
echo "built backbone-capture-magisk-$VER.zip"
