#!/system/bin/sh
# Runs when the module is removed in Magisk. Stops the script and deletes its log.
pkill -f backbone_capture/bb_capture.py 2>/dev/null
rm -f /data/local/tmp/backbone_capture.log /data/local/tmp/backbone_capture.log.1
