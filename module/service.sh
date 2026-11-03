#!/system/bin/sh
# Backbone Capture Button - boot service (runs as root via Magisk).
# Starts bb_capture.py whenever a Backbone controller (USB vendor 358a) is plugged in.
MODDIR=${0%/*}
PY=/data/data/com.termux/files/usr/bin/python
SCRIPT="$MODDIR/bb_capture.py"
LOG=/data/local/tmp/backbone_capture.log
MAX_LOG=524288          # 512 KB, one old copy kept as .log.1
APP=com.playbackbone.android
CAP=com.rattlehead.backbonecapture       # companion app (toasts, notifications, recording with sound)
CAP_APK="$MODDIR/BackboneCapture.apk"
CAP_VER=3                                # versionCode of the bundled APK

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') [service] $*" >> "$LOG"; }

cap_log() {
  [ -f "$LOG" ] || return 0
  size=$(wc -c < "$LOG" 2>/dev/null)
  if [ "${size:-0}" -gt "$MAX_LOG" ]; then
    cp -f "$LOG" "$LOG.1"
    : > "$LOG"          # truncate in place (python appends with >>)
  fi
}

present() {
  for d in /sys/bus/usb/devices/*; do
    [ "$(cat "$d/idVendor" 2>/dev/null)" = "358a" ] && return 0
  done
  return 1
}

# Install/update the bundled companion app, then give it what it needs (all visible in
# Settings > Apps > Backbone Capture). Errors are logged and ignored.
install_app() {
  [ -f "$CAP_APK" ] || return 0
  have=$(dumpsys package "$CAP" 2>/dev/null | grep -m1 versionCode= | sed 's/.*versionCode=\([0-9]*\).*/\1/')
  if [ -z "$have" ] || [ "$have" -lt "$CAP_VER" ]; then
    cp -f "$CAP_APK" /data/local/tmp/BackboneCapture.apk
    chmod 644 /data/local/tmp/BackboneCapture.apk
    log "installing companion app (installed: ${have:-none}, bundled: $CAP_VER): $(pm install -r /data/local/tmp/BackboneCapture.apk 2>&1)"
    rm -f /data/local/tmp/BackboneCapture.apk
  fi
  for p in RECORD_AUDIO POST_NOTIFICATIONS READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE; do
    pm grant "$CAP" "android.permission.$p" >/dev/null 2>&1
  done
  appops set "$CAP" PROJECT_MEDIA allow >/dev/null 2>&1        # no "start recording?" prompt
  appops set "$CAP" POST_NOTIFICATION allow >/dev/null 2>&1
  if pm path "$CAP" >/dev/null 2>&1; then
    log "companion app ready ($CAP)"
  else
    log "companion app not installed; falling back to screenrecord (no audio) and shell notifications"
  fi
}

running() { [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; }

stop_script() {
  if running; then
    kill -TERM "$pid" 2>/dev/null
    i=0
    while running && [ $i -lt 10 ]; do sleep 1; i=$((i + 1)); done
    running && kill -KILL "$pid" 2>/dev/null
  fi
  pid=""
}

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 3; done
sleep 5
mkdir -p /data/local/tmp
cap_log
log "started (module dir $MODDIR)"
install_app

pid=""
fails=0          # quick crashes in a row while the controller stays plugged in
started=0
py_warned=0
trap 'stop_script; exit 0' TERM INT

while true; do
  # Module disabled or marked for removal in the Magisk app: stop and idle.
  if [ -f "$MODDIR/disable" ] || [ -f "$MODDIR/remove" ]; then
    stop_script
    sleep 3
    continue
  fi

  if present; then
    if ! running; then
      if [ -n "$pid" ] || [ "$started" -gt 0 ]; then
        now=$(date +%s)
        if [ $((now - started)) -lt 15 ]; then fails=$((fails + 1)); else fails=0; fi
        pid=""
        started=0
      fi
      if [ ! -x "$PY" ]; then
        if [ "$py_warned" = 0 ]; then
          log "ERROR: Termux python not found at $PY. Install Termux and run: pkg install python"
          py_warned=1
        fi
      elif [ "$fails" -ge 5 ]; then
        : # gave up for this plug-in; waits for unplug/replug (logged below once)
      else
        py_warned=0
        log "controller detected; stopping $APP and starting bb_capture.py"
        am force-stop "$APP" >/dev/null 2>&1
        sleep 1
        "$PY" "$SCRIPT" --no-debug >> "$LOG" 2>&1 &
        pid=$!
        started=$(date +%s)
        [ "$fails" -gt 0 ] && sleep $((fails * 5))   # back off after quick crashes
      fi
      if [ "$fails" -eq 5 ]; then
        log "bb_capture.py keeps exiting right after start; giving up until the controller is replugged"
        fails=6
      fi
    fi
  else
    # Unplugged: the script exits on its own; make sure it is gone.
    if running; then
      sleep 3
      running && { log "controller gone, stopping script"; stop_script; }
    fi
    pid=""
    started=0
    fails=0
  fi
  cap_log
  sleep 3
done
