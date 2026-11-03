# shellcheck shell=sh
# Magisk sources this file during install. MODPATH is set by Magisk.
PY=/data/data/com.termux/files/usr/bin/python

ui_print "- Backbone Capture Button v1.2 by Rattlehead"
ui_print "- Capture button: press = screenshot, hold = start/stop recording"
if [ -x "$PY" ]; then
  ui_print "- Found Termux python: $PY"
else
  ui_print "! Termux python NOT found at $PY"
  ui_print "! Install Termux (F-Droid/GitHub), open it and run:"
  ui_print "!   pkg install python"
  ui_print "! The module installs anyway and starts working once python exists."
fi
ui_print "- Companion app \"Backbone Capture\" is installed after reboot"
ui_print "  (pop-ups, notifications, recording with sound)"
ui_print "- Log file: /data/local/tmp/backbone_capture.log"

set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/BackboneCapture.apk" 0 0 0644
