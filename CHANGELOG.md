# Changelog

## v1.1 (2026-11-02)
- **Notifications you can see.** You now get "Screenshot saved", "Recording started" and "Recording saved" notifications, each showing the file name.
- **Notifications stay in the pull-down shade.** Each saved screenshot or recording gets its own entry. A recording's "Recording started" entry turns into "Recording saved" when you stop.
- **Tap to open.** Tapping a "saved" notification opens the screenshot or video. It opens through the media library and falls back to the file path.
- Notifications are posted in the background as the Android shell user, with a fallback to root. If posting fails, nothing breaks.
- README: Home button support is now confirmed. Button Mapper sees it as `BUTTON_MODE`, keycode 110, so you can map it to open your game launcher.

## v1.0 (2026-11-02)
- First release. Capture button: press = screenshot, hold = start/stop screen recording. No Backbone app needed.
- Magisk module with a boot service. It starts the listener when the controller is plugged in and stops it cleanly when the controller is unplugged.
