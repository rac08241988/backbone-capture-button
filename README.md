# Backbone Capture Button

**Turn your Android phone + Backbone controller into an Xbox-like console. No Backbone app, no ads, no bloat.**

- 🏠 **Home button opens your game dashboard.** Point it at a game launcher using Button Mapper.
- 📸 **Capture button works again.** Press it to take a screenshot. Hold it to start or stop screen recording **with game sound**. A pop-up and a notification confirm every capture.
- 🚫 **No Backbone app needed.** You don't need its account, ads, or anything running in the background.

Pick up the controller, press Home to get to your games, and press Capture to save a moment, the same way you would on an Xbox.

---

## Why this exists

The Backbone's **capture button doesn't send a normal Android key**. Android never sees it, so apps like Button Mapper can't map it. Only the official Backbone app knows about it, because it reads the controller's own private USB channel.

This project reads that same private USB channel on its own. When you press the capture button, it takes a screenshot. When you hold it, it starts or stops a screen recording. It does this without the Backbone app and runs quietly in the background after boot, through a small Magisk module.

| Capture button | What happens |
|---|---|
| Quick press | Screenshot is saved to `Pictures/Screenshots/Backbone_<date>.png`. A **"Screenshot saved"** pop-up and notification appear (with a preview) |
| Hold (about ½ second) | Screen recording **starts** with sound, with a short buzz, a **"Recording started"** pop-up and an ongoing notification with a **Stop** button and timer |
| Hold again | Recording **stops** and is saved to `Movies/Backbone_<date>.mp4`. A **"Recording saved"** pop-up and notification appear |

### The companion app (new in v1.2)

The module now includes a tiny app called **Backbone Capture** (`com.rattlehead.backbonecapture`). It is installed automatically after the first reboot and shows up in your app drawer. It:

- shows the on-screen pop-ups (toasts) and the heads-up notifications. Every saved screenshot and recording gets its own notification that stays in the shade; tap it to open the file.
- records the screen **with audio** using Android's screen recording API. By default it records the **game/phone sound** (Android 10+). Open the app to switch to **Microphone** or **No audio**.

Because the module runs as root, it pre-approves the app's permissions (microphone/audio capture, notifications and screen capture), so you don't get a "Start recording?" prompt every time. You can see and revoke all of this in **Settings → Apps → Backbone Capture**. If the app is missing or fails, the module falls back to the old behavior (silent `screenrecord` + plain notifications).

Note: some games mark their sound as "do not capture". In those games the video has no game sound; pick **Microphone** in the app if you want sound anyway.

## The "Xbox experience" setup

1. **Capture button:** install this module (steps below).
2. **Home button to game dashboard:**
   - Install a game launcher or dashboard app you like (for example Daijishō, ES-DE, Beacon, or any launcher that shows your games).
   - Install **Button Mapper** and give it the accessibility permission it asks for.
   - In Button Mapper, pick the Backbone's **Home** button and set its action to **Launch app → your game launcher**. Android sees it as a normal gamepad key (`BUTTON_MODE`, keycode 110), so Button Mapper detects it right away. This is confirmed on a Backbone with product ID 0x0204.
3. You no longer need the Backbone app, so feel free to uninstall it. If you keep it installed, this module closes it when the controller is plugged in. Only one app at a time can use the controller's private channel.

## Requirements

- **Rooted Android phone with Magisk** (Magisk v20.4 or newer)
- **Termux** with Python. Get Termux from F-Droid or its GitHub page, not the old Play Store version. Then run `pkg install python`.
  The module uses Termux's Python at `/data/data/com.termux/files/usr/bin/python`. It doesn't include its own Python.
- **Backbone USB-C controller.** Tested on product ID `0x0204`. Other USB-C Backbone models (vendor ID `0x358A`) will probably work but haven't been tested.

## Install

1. Install **Termux**, open it, and run:
   ```
   pkg install python
   ```
2. Download `backbone-capture-magisk-v1.2.zip` from the [Releases page](../../releases).
3. Open **Magisk → Modules → Install from storage** and pick the zip.
4. **Reboot.** After boot the module installs the **Backbone Capture** companion app by itself (give it about 10 seconds).
5. Plug in the Backbone and press the capture button. You'll find the screenshot in your gallery.

## Test it manually (without the module)

Plug in the controller, open Termux, and run this (all on one line). Put the script in your Download folder first:

```
su -c "am force-stop com.playbackbone.android; /data/data/com.termux/files/usr/bin/python /sdcard/Download/bb_capture.py"
```

It prints what it finds. (The companion app must already be installed for pop-ups and recording with sound.) Press the capture button and you'll see `capture msg` lines. Press **Ctrl-C** to stop. In Termux, that's Volume Down + C.

Options: `--no-debug` (less output), `--hold-ms 800` (change the hold time).

## Where files go

- Screenshots: `/sdcard/Pictures/Screenshots/Backbone_YYYYMMDD_HHMMSS.png`
- Recordings: `/sdcard/Movies/Backbone_YYYYMMDD_HHMMSS.mp4`
- Log: `/data/local/tmp/backbone_capture.log`. Every notification attempt is logged as a `notify:` line. It stays under about 512 KB, and one older copy is kept as `.log.1`.

## How the module behaves

- After the phone finishes booting, it checks about every 3 seconds whether a Backbone (USB vendor `358a`) is plugged in.
- When you plug the controller in, it closes the Backbone app **once** and starts the script.
- After boot it installs or updates the companion app and re-applies its permissions.
- When you unplug the controller, the script exits. A recording made by the companion app keeps going until you tap **Stop** in its notification. It starts again the next time you plug in.
- If the script keeps crashing right after it starts, the module waits longer between tries. After a few crashes it stops trying until you replug the controller.
- If you turn the module off in Magisk, it stops right away.

## Troubleshooting

- **No pop-up or notification.** Open the **Backbone Capture** app and tap **Test notification**. If nothing appears, allow notifications in Settings → Apps → Backbone Capture → Notifications. Then check the `notify:` lines in the log.
- **Recording has no sound.** Open the app and check the audio setting. Some games block capturing their sound; choose **Microphone** for those. The "Recording started" pop-up says "no audio: ..." when audio could not be started.
- **A "Start recording?" prompt appears.** Tap **Start now** once. To pre-approve again, run `su -c appops set com.rattlehead.backbonecapture PROJECT_MEDIA allow`.
- **Companion app not installed.** Check the log for `installing companion app`. You can also install it by hand from the zip (`BackboneCapture.apk`).
- **Nothing happens when I press the button.** Check the log:
  `su -c cat /data/local/tmp/backbone_capture.log`
- **"Termux python not found" in the log.** Install Termux and run `pkg install python`, then replug the controller.
- **"Could not claim interface".** Something else is using the controller, usually the Backbone app. Close it, or uninstall it.
- **Still nothing.** Turn the module off in Magisk, then run the manual test above and look at the output.
- **I want to use the Backbone app again.** Turn the module off in Magisk first. The two can't use the controller at the same time.

## Uninstall

Open **Magisk → Modules → Backbone Capture Button → Remove**, then reboot. That removes everything, including the log and the companion app (it is uninstalled after the next boot). Nothing else on your phone is changed.

## What's in this repo

- `bb_capture.py`: the listener script, written from scratch in Python using only the standard library. It talks to the controller through Linux usbfs.
- `module/`: the Magisk module source (`module.prop`, `service.sh`, `customize.sh`, `uninstall.sh`, `META-INF`).
- `companion-app/`: source of the Backbone Capture app (Kotlin, no third-party libraries). Signing keys are not in the repo; build with your own (see `build.sh`).
- `build.sh`: rebuilds the module zip.
- `CHANGELOG.md`: version history.

## Disclaimer

This is an **unofficial** community project. It is **not affiliated with, endorsed by, or supported by Backbone Labs**. "Backbone" is a trademark of its owner. It contains no Backbone code or files. Use it at your own risk.

---

Made by **Rattlehead**
