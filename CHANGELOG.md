# Changelog

## v1.2 (2026-11-02)
- **New companion app "Backbone Capture"**, bundled in the module and installed automatically after boot. Source in `companion-app/`.
- **Pop-ups (toasts)** for "Screenshot saved", "Recording started" and "Recording saved".
- **Heads-up notifications** that stay in the shade for all three events. "Screenshot saved" shows a preview, and tapping it opens the image. "Recording started" is an ongoing notification with a timer and a **Stop** button. Tapping "Recording saved" opens the video.
- **Recordings now have sound.** Game/phone audio is recorded by default (Android 10+). You can switch to Microphone or No audio in the app.
- The module pre-approves the app's permissions with root, so there's no "Start recording?" prompt. You can see all of them under Settings → Apps → Backbone Capture.
- Every notification attempt is logged (`notify:` lines in the log).
- If the app is missing, the module falls back to the v1.1 behavior.
- Fixed: in v1.1 the "Screenshot saved" and "Recording saved" notifications never showed up, and nothing popped up on screen.
- Removing the module in Magisk also uninstalls the companion app.

## v1.1 (2026-11-02)
- **Notifications you can see.** You now get "Screenshot saved", "Recording started" and "Recording saved" notifications, each showing the file name.
- **Notifications stay in the pull-down shade.** Each saved screenshot or recording gets its own entry. A recording's "Recording started" entry turns into "Recording saved" when you stop.
- **Tap to open.** Tapping a "saved" notification opens the screenshot or video. It opens through the media library and falls back to the file path.
- Notifications are posted in the background as the Android shell user, with a fallback to root. If posting fails, nothing breaks.
- README: Home button support is now confirmed. Button Mapper sees it as `BUTTON_MODE`, keycode 110, so you can map it to open your game launcher.

## v1.0 (2026-11-02)
- First release. Capture button: press = screenshot, hold = start/stop screen recording. No Backbone app needed.
- Magisk module with a boot service. It starts the listener when the controller is plugged in and stops it cleanly when the controller is unplugged.
