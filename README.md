# Backbone Capture Button

**Turn your Android phone + Backbone controller into an Xbox-like console. No Backbone app, no ads, no bloat.**

- 🏠 **Home button opens your game dashboard.** Point it at a game launcher using Button Mapper.
- 📸 **Capture button works again.** Press it to take a screenshot. Hold it to start or stop screen recording.
- 🚫 **No Backbone app needed.** You don't need its account, ads, or anything running in the background.

Pick up the controller, press Home to get to your games, and press Capture to save a moment, the same way you would on an Xbox.

---

## Why this exists

The Backbone's **capture button doesn't send a normal Android key**. Android never sees it, so apps like Button Mapper can't map it. Only the official Backbone app knows about it, because it reads the controller's own private USB channel.

This project reads that same private USB channel on its own. When you press the capture button, it takes a screenshot. When you hold it, it starts or stops a screen recording. It does this without the Backbone app and runs quietly in the background after boot, through a small Magisk module.

| Capture button | What happens |
|---|---|
| Quick press | Screenshot is saved to `Pictures/Screenshots/Backbone_<date>.png` |
| Hold (about ½ second) | Screen recording **starts**, with a short buzz and a notification |
| Hold again | Recording **stops** and is saved to `Movies/Backbone_<date>.mp4` |

Long recordings are saved in 30-minute parts automatically.

## The "Xbox experience" setup

1. **Capture button:** install this module (steps below).
2. **Home button to game dashboard:**
   - Install a game launcher or dashboard app you like (for example Daijishō, ES-DE, Beacon, or any launcher that shows your games).
   - Install **Button Mapper** and give it the accessibility permission it asks for.
   - In Button Mapper, find your Backbone's Home button. If it shows up as a key on your phone, set its action to **Launch app → your game launcher**.
   - Note: Button Mapper can only map buttons that Android sees as normal keys. If the Home button doesn't show up there, Android isn't seeing it as a key on your model.
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
2. Download `backbone-capture-magisk-v1.0.zip` from the [Releases page](../../releases).
3. Open **Magisk → Modules → Install from storage** and pick the zip.
4. **Reboot.**
5. Plug in the Backbone and press the capture button. You'll find the screenshot in your gallery.

## Test it manually (without the module)

Plug in the controller, open Termux, and run this (all on one line). Put the script in your Download folder first:

```
su -c "am force-stop com.playbackbone.android; /data/data/com.termux/files/usr/bin/python /sdcard/Download/bb_capture.py"
```

It prints what it finds. Press the capture button and you'll see `capture msg` lines. Press **Ctrl-C** to stop. In Termux, that's Volume Down + C.

Options: `--no-debug` (less output), `--hold-ms 800` (change the hold time).

## Where files go

- Screenshots: `/sdcard/Pictures/Screenshots/Backbone_YYYYMMDD_HHMMSS.png`
- Recordings: `/sdcard/Movies/Backbone_YYYYMMDD_HHMMSS.mp4`
- Log: `/data/local/tmp/backbone_capture.log`. It stays under about 512 KB, and one older copy is kept as `.log.1`.

## How the module behaves

- After the phone finishes booting, it checks about every 3 seconds whether a Backbone (USB vendor `358a`) is plugged in.
- When you plug the controller in, it closes the Backbone app **once** and starts the script.
- When you unplug the controller, the script saves any running recording and exits. It starts again the next time you plug in.
- If the script keeps crashing right after it starts, the module waits longer between tries. After a few crashes it stops trying until you replug the controller.
- If you turn the module off in Magisk, it stops right away.

## Troubleshooting

- **Nothing happens when I press the button.** Check the log:
  `su -c cat /data/local/tmp/backbone_capture.log`
- **"Termux python not found" in the log.** Install Termux and run `pkg install python`, then replug the controller.
- **"Could not claim interface".** Something else is using the controller, usually the Backbone app. Close it, or uninstall it.
- **Still nothing.** Turn the module off in Magisk, then run the manual test above and look at the output.
- **I want to use the Backbone app again.** Turn the module off in Magisk first. The two can't use the controller at the same time.

## Uninstall

Open **Magisk → Modules → Backbone Capture Button → Remove**, then reboot. That removes everything, including the log. Nothing else on your phone is changed.

## What's in this repo

- `bb_capture.py`: the listener script, written from scratch in Python using only the standard library. It talks to the controller through Linux usbfs.
- `module/`: the Magisk module source (`module.prop`, `service.sh`, `customize.sh`, `uninstall.sh`, `META-INF`).
- `build.sh`: rebuilds the module zip.

## Disclaimer

This is an **unofficial** community project. It is **not affiliated with, endorsed by, or supported by Backbone Labs**. "Backbone" is a trademark of its owner. It contains no Backbone code or files. Use it at your own risk.

---

Made by **Rattlehead**
