# Backbone Capture companion app

A small Android app used by the Backbone Capture Button Magisk module. It shows toasts and notifications and records the screen with sound through MediaProjection. It uses no third-party libraries.

The root script talks to it with:
- `am broadcast -f 0x20 -n com.rattlehead.backbonecapture/.EventReceiver -a com.rattlehead.backbonecapture.SHOT_SAVED --es path <png>`
- `am start -n com.rattlehead.backbonecapture/.RecordActivity -a com.rattlehead.backbonecapture.TOGGLE`

Both components require `android.permission.DUMP`. Only shell and root hold it, so other apps can't trigger them.

Build it with your own key. No keys are stored in this repo:

    ./gradlew assembleRelease -PBBC_STORE_FILE=/path/key.jks -PBBC_STORE_PASSWORD=... -PBBC_KEY_ALIAS=... -PBBC_KEY_PASSWORD=...
