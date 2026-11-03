#!/system/bin/sh
# Runs when the module is removed in Magisk. Stops the script, deletes its log and
# uninstalls the companion app (once Android has finished booting).
pkill -f backbone_capture/bb_capture.py 2>/dev/null
rm -f /data/local/tmp/backbone_capture.log /data/local/tmp/backbone_capture.log.1
(
  until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 3; done
  pm uninstall com.rattlehead.backbonecapture >/dev/null 2>&1
) &
