#!/usr/bin/env python3
"""Backbone capture button -> screenshot (short press) / screen record toggle (hold).

Run as root, with the Backbone app stopped:
  su -c "am force-stop com.playbackbone.android; /data/data/com.termux/files/usr/bin/python /sdcard/Download/bb_capture.py"
Options: --no-debug (hide raw messages), --no-hid, --no-app-status, --no-iap2-off, --hold-ms N (default 500)
Exits cleanly (releases the USB interface, saves any running recording) when the controller is unplugged.
Made by Rattlehead. Unofficial; not affiliated with Backbone.
Debug mode (default) prints every vendor USB message (except the repeating iAP2 detect packet)
and every changed HID report from the controller's /dev/hidraw nodes.
Plain Python 3 stdlib only. Talks to the controller's vendor USB interface via usbfs.
"""
import os, sys, glob, time, ctypes, signal, threading, subprocess, queue

VID = '358a'
HOLD_MS = 500
DEBUG = '--no-debug' not in sys.argv
if '--hold-ms' in sys.argv:
    HOLD_MS = int(sys.argv[sys.argv.index('--hold-ms') + 1])
SHOT_DIR = '/sdcard/Pictures/Screenshots'
VID_DIR = '/sdcard/Movies'

# ---------- raw usbfs ----------
libc = ctypes.CDLL('libc.so', use_errno=True)
libc.ioctl.argtypes = [ctypes.c_int, ctypes.c_ulong, ctypes.c_void_p]
class Bulk(ctypes.Structure):
    _fields_ = [('ep', ctypes.c_uint), ('len', ctypes.c_uint),
                ('timeout', ctypes.c_uint), ('data', ctypes.c_void_p)]
USBDEVFS_BULK = 0xC0185502
USBDEVFS_CLAIMINTERFACE = 0x8004550F
USBDEVFS_RELEASEINTERFACE = 0x80045510

def log(*a):
    print(time.strftime('%H:%M:%S'), *a, flush=True)

def rd(p):
    try:
        return open(p).read().strip()
    except OSError:
        return None

USBDEVFS_SETINTERFACE = 0x80085504   # _IOR('U',4,struct{uint iface; uint alt})
class SetIf(ctypes.Structure):
    _fields_ = [('interface', ctypes.c_uint), ('altsetting', ctypes.c_uint)]

IAP2_DETECT = bytes.fromhex('ff55 0200 ee10')
fd = None
claimed = []          # interface numbers we claimed (released on exit)
out_ep = None         # command channel OUT (same choice the Backbone app makes)
listen_eps = []       # (label, endpoint) vendor IN endpoints we read
usb_dev = None

def parse_descriptors(path):
    """Every interface *alt setting* in the active config, like Android's UsbDevice list."""
    raw = open(path, 'rb').read()
    out, cur, i = [], None, 0
    while i + 2 <= len(raw):
        ln, ty = raw[i], raw[i + 1]
        if ln < 2:
            break
        d = raw[i:i + ln]
        if ty == 4 and ln >= 9:
            cur = {'num': d[2], 'alt': d[3], 'cls': d[5], 'sub': d[6], 'proto': d[7], 'eps': []}
            out.append(cur)
        elif ty == 5 and ln >= 7 and cur is not None:
            cur['eps'].append((d[2], d[3] & 3))   # (address, 2=bulk 3=interrupt)
        i += ln
    return out

def ep_str(eps):
    return ' '.join('%s-%s %#04x' % ({2: 'bulk', 3: 'intr', 1: 'iso', 0: 'ctrl'}[t],
                                     'in' if a & 0x80 else 'out', a) for a, t in eps) or '(no endpoints)'

def claim(num):
    if num in claimed:
        return True
    if libc.ioctl(fd, USBDEVFS_CLAIMINTERFACE, ctypes.byref(ctypes.c_uint(num))) < 0:
        log('claim interface %d failed errno %d' % (num, ctypes.get_errno()))
        return False
    claimed.append(num)
    return True

def open_controller():
    global fd, out_ep, usb_dev, listen_eps
    devs = [d for d in glob.glob('/sys/bus/usb/devices/*') if rd(d + '/idVendor') == VID]
    if not devs:
        sys.exit('Backbone controller (USB vendor 358a) not found. Is it plugged in?')
    dev = usb_dev = devs[0]
    log('controller', os.path.basename(dev), 'product id', rd(dev + '/idProduct'))
    fd = os.open('/dev/bus/usb/%03d/%03d' % (int(rd(dev + '/busnum')), int(rd(dev + '/devnum'))), os.O_RDWR)
    alts = parse_descriptors(dev + '/descriptors')
    # Same selection the official app makes: walk every alt setting;
    # class ff/f0 proto 0|1 (or ff/02/00) entries with bulk endpoints; the LAST one wins.
    chosen, intr_in = None, None
    for a in alts:
        log('interface %d alt %d class=%02x sub=%02x proto=%02x  %s'
            % (a['num'], a['alt'], a['cls'], a['sub'], a['proto'], ep_str(a['eps'])))
        vend = a['cls'] == 0xFF and ((a['sub'] == 0xF0 and a['proto'] in (0, 1)) or
                                     (a['sub'] == 0x02 and a['proto'] == 0))
        if not vend:
            continue
        for addr, t in a['eps']:
            if t == 2:
                chosen = a
            elif t == 3 and a['sub'] == 0x02 and addr & 0x80:
                intr_in = (a, addr)
    if not chosen:
        sys.exit('No Backbone vendor command interface found (see interface list above).')
    num, alt = chosen['num'], chosen['alt']
    if not claim(num):
        sys.exit('Could not claim interface %d. Is the Backbone app still running? '
                 'Run: am force-stop com.playbackbone.android' % num)
    if alt != 0 or chosen['proto'] != 0:     # protocol 1 alt settings must be selected first
        r = libc.ioctl(fd, USBDEVFS_SETINTERFACE, ctypes.byref(SetIf(num, alt)))
        log('set interface %d alt %d -> %s' % (num, alt, 'ok' if r >= 0 else 'errno %d' % ctypes.get_errno()))
    log('command channel: interface %d alt %d  %s' % (num, alt, ep_str(chosen['eps'])))
    out_ep = next((a for a, t in chosen['eps'] if t == 2 and not a & 0x80), None)
    bin_ep = next((a for a, t in chosen['eps'] if t == 2 and a & 0x80), None)
    if intr_in:
        claim(intr_in[0]['num'])
        listen_eps.append(('if%d-intr' % intr_in[0]['num'], intr_in[1]))
    elif bin_ep:
        listen_eps.append(('if%d' % num, bin_ep))
    # Also listen on any other active vendor interface IN endpoint (e.g. the iAP2 one).
    for a in alts:
        if a['alt'] == 0 and a['cls'] == 0xFF and a['num'] != num and a['num'] not in [x[0]['num'] for x in [intr_in] if x]:
            ins = [e for e, t in a['eps'] if e & 0x80 and t in (2, 3)]
            if ins and claim(a['num']):
                listen_eps += [('if%d' % a['num'], e) for e in ins]
    if chosen['sub'] == 0xF0 and out_ep and '--no-iap2-off' not in sys.argv:
        r = xfer(out_ep, [0xFF, 0x55, 0x00, 0x00], timeout=1000)   # app: "Disabling IAP2"
        log('sent FF 55 00 00 (disable iAP2):', 'ok' if r is not None else 'failed errno %d' % ctypes.get_errno())
    if out_ep and '--no-app-status' not in sys.argv:              # app sends this on connect
        r = xfer(out_ep, [1, 0x43, 0x82, 0, 0, 0, 0x20, 0x02, 4, 0, 0, 0, 1, 0, 0x00, 0x80], timeout=1000)
        log('sent ReportAppStatus(active):', 'ok' if r is not None else 'failed errno %d' % ctypes.get_errno())
    log('listening on', ', '.join('%s ep %#04x' % x for x in listen_eps),
        '(hold threshold %d ms). Ctrl-C to quit.' % HOLD_MS)

def xfer(ep, data=None, size=4160, timeout=0):
    buf = ctypes.create_string_buffer(bytes(data) if data else size)
    b = Bulk(ep, len(data) if data else size, timeout, ctypes.addressof(buf))
    r = libc.ioctl(fd, USBDEVFS_BULK, ctypes.byref(b))
    return buf.raw[:r] if r >= 0 else None

def release():
    if fd is not None:
        for n in claimed:
            libc.ioctl(fd, USBDEVFS_RELEASEINTERFACE, ctypes.byref(ctypes.c_uint(n)))

def hidraw_nodes():
    """hidraw nodes that belong to the Backbone (owned by Android's usbhid driver)."""
    name = os.path.basename(usb_dev) + ':'
    out = []
    for h in sorted(glob.glob('/sys/class/hidraw/hidraw*')):
        p = os.path.realpath(h + '/device')
        if ('/' + name) in p:
            out.append(('/dev/' + os.path.basename(h), p.split('/' + name)[1].split('/')[0]))
    return out

# ---------- actions ----------
def quiet(cmd, timeout=3):
    try:
        return subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=timeout).returncode
    except Exception:
        return -1

def scan(path):
    quiet(['am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE', '-d', 'file://' + path], 10)

def vibrate(ms=80):
    if quiet(['cmd', 'vibrator_manager', 'synced', 'oneshot', str(ms)]) != 0:
        quiet(['cmd', 'vibrator', 'vibrate', str(ms)])

APP_PKG = 'com.rattlehead.backbonecapture'      # companion app (bundled in the Magisk module)

def have_app():
    try:
        out = subprocess.run(['pm', 'path', APP_PKG], capture_output=True, text=True, timeout=10).stdout
        return 'package:' in out
    except Exception:
        return False

def app_cmd(args, what):
    """Run an am command aimed at the companion app; log the result. True on success."""
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=15)
        out = (r.stdout + r.stderr).strip().replace('\n', ' | ')
        ok = r.returncode == 0 and 'Error' not in out and 'Exception' not in out
        log('notify: %s -> %s (rc %d) %s' % (what, 'ok' if ok else 'FAILED', r.returncode, out[:300]))
        return ok
    except Exception as e:
        log('notify: %s -> FAILED %s' % (what, e))
        return False

def shell_notify(title, text, tag):
    """Fallback when the companion app is missing: plain shell notification (no pop-up)."""
    def go():
        rc = quiet(['cmd', 'notification', 'post', '-S', 'bigtext', '-t', title, tag, text], 10)
        log('notify: shell "%s" rc %d' % (title, rc))
    run_async(go)

def stamp():
    return time.strftime('%Y%m%d_%H%M%S')

def screenshot():
    os.makedirs(SHOT_DIR, exist_ok=True)
    path = '%s/Backbone_%s.png' % (SHOT_DIR, stamp())
    ok = quiet(['screencap', '-p', path], 15) == 0 and os.path.exists(path) and os.path.getsize(path) > 0
    if ok:
        log('screenshot saved', path)
        if not (have_app() and app_cmd(['am', 'broadcast', '-f', '0x20', '-n', APP_PKG + '/.EventReceiver',
                                        '-a', APP_PKG + '.SHOT_SAVED', '--es', 'path', path],
                                       'screenshot saved')):
            scan(path)
            shell_notify('Screenshot saved', os.path.basename(path), 'bb_shot_' + stamp())
    else:
        log('screencap failed, falling back to keyevent 120')
        quiet(['input', 'keyevent', '120'])

rec_lock = threading.Lock()
rec_proc = None
rec_path = None
rec_on = False
rec_tag = 'bb_rec'
time_limit_ok = True

def start_segment():
    global rec_proc, rec_path, time_limit_ok
    os.makedirs(VID_DIR, exist_ok=True)
    rec_path = '%s/Backbone_%s.mp4' % (VID_DIR, stamp())
    base = ['screenrecord', '--bit-rate', '8000000']
    if time_limit_ok:
        rec_proc = subprocess.Popen(base + ['--time-limit', '1800', rec_path],
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(1.0)
        if rec_proc.poll() is not None and rec_proc.returncode != 0:
            log('screenrecord rejected --time-limit 1800; using default limit (auto-restarts segments)')
            time_limit_ok = False
    if not time_limit_ok:
        rec_proc = subprocess.Popen(base + [rec_path], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    log('recording ->', rec_path)

def stop_segment():
    global rec_proc
    p, path = rec_proc, rec_path
    rec_proc = None
    if p is None:
        return
    if p.poll() is None:
        p.send_signal(signal.SIGINT)        # lets screenrecord finalize the mp4
        try:
            p.wait(8)
        except subprocess.TimeoutExpired:
            p.terminate()
    log('saved', path)
    scan(path)

def toggle_recording():
    global rec_on, rec_tag
    with rec_lock:
        if not rec_on:
            rec_on = True
            vibrate()
            start_segment()
            rec_tag = 'bb_rec_' + stamp()        # this tag is replaced by "Recording saved" later
            shell_notify('Recording started', 'Hold the capture button again to stop', rec_tag)
        else:
            rec_on = False
            path = rec_path
            stop_segment()
            vibrate(160)
            shell_notify('Recording saved', os.path.basename(path), rec_tag)

def watchdog():
    with rec_lock:
        if rec_on and rec_proc is not None and rec_proc.poll() is not None:
            log('screenrecord ended (time limit?), starting next segment')
            stop_segment()
            start_segment()

def run_async(fn):
    threading.Thread(target=fn, daemon=True).start()

# ---------- button logic ----------
def parse_capture(b):
    """Return (state, duration_ms or None) for a capture push, else None."""
    if len(b) < 12 or (b[2] & 0x3F) != 7:
        return None
    if not ((b[1] == 0x43 and b[6] == 0x20 and b[7] == 0x17) or   # AvConfigApp NOTIFY_FUNCTION_BUTTON
            (b[1] == 0x64 and b[6] == 0x11 and b[7] == 0x01)):    # NijiAppButtons NOTIFY_FUNCTION
        return None
    state = b[12] if len(b) > 12 else 0
    dur = (b[13] | (b[14] << 8)) if len(b) > 14 else None
    return state, (dur or None)

press_t = None        # time of press message (timing fallback)
long_fired = False    # long action already fired for the current hold

def on_short():
    log('SHORT press -> screenshot')
    run_async(screenshot)

def app_toggle():
    """Recording with sound through the companion app; old screenrecord path if the app is missing."""
    if not rec_on and have_app():
        vibrate()
        if app_cmd(['am', 'start', '-n', APP_PKG + '/.RecordActivity', '-a', APP_PKG + '.TOGGLE'],
                   'toggle recording (app)'):
            return
        log('companion app failed; using screenrecord (no audio)')
    toggle_recording()

def on_long():
    log('LONG hold -> toggle recording')
    run_async(app_toggle)

def handle(state, dur):
    global press_t, long_fired
    if dur is not None:                       # firmware reports duration (on release)
        if long_fired:
            log('release after hold (%d ms), already handled' % dur)
        elif dur >= HOLD_MS:
            on_long()
        else:
            on_short()
        press_t, long_fired = None, False
    elif state != 0:                          # press, no duration -> time it ourselves
        if press_t is None:
            press_t, long_fired = time.monotonic(), False
    else:                                     # release, no duration
        if press_t is not None and not long_fired:
            ms = (time.monotonic() - press_t) * 1000
            log('release after %d ms (timed)' % ms)
            on_long() if ms >= HOLD_MS else on_short()
        press_t, long_fired = None, False

def check_hold():
    """Fire the long action as soon as the threshold passes while still held."""
    global long_fired, press_t
    if press_t is not None and not long_fired and (time.monotonic() - press_t) * 1000 >= HOLD_MS:
        long_fired = True
        on_long()
    if press_t is not None and time.monotonic() - press_t > 30:   # lost release message, reset
        press_t, long_fired = None, False

def usb_reader(q, label, ep):
    fails = 0
    while True:
        b = xfer(ep, timeout=1000)
        if b is None:
            err = ctypes.get_errno()
            if err in (110, 0):            # ETIMEDOUT: idle
                continue
            if err in (19, 108):           # ENODEV / ESHUTDOWN: unplugged
                q.put(('err', label, err))
                return
            fails += 1
            log('%s ep %#04x read error errno %d' % (label, ep, err))
            if fails > 5:
                log('%s: giving up on this endpoint' % label)
                return
            time.sleep(1)
            continue
        if b:
            q.put(('usb', label, b))

def hid_reader(q, node, label):
    try:
        f = os.open(node, os.O_RDONLY)
    except OSError as e:
        log('cannot open', node, e)
        return
    while True:
        try:
            b = os.read(f, 4096)
        except OSError as e:
            log(node, 'read error', e)
            return
        if b:
            q.put(('hid', '%s(%s)' % (os.path.basename(node), label), b))

# ---------- main ----------
def cleanup(*_, code=0):
    global rec_on
    got = rec_lock.acquire(timeout=10)
    try:
        if rec_on:
            rec_on = False
            stop_segment()
    finally:
        if got:
            rec_lock.release()
    release()
    log('bye')
    os._exit(code)

def main():
    signal.signal(signal.SIGINT, cleanup)
    signal.signal(signal.SIGTERM, cleanup)
    signal.signal(signal.SIGHUP, cleanup)
    open_controller()
    q = queue.Queue()
    readers = []
    for label, ep in listen_eps:
        t = threading.Thread(target=usb_reader, args=(q, label, ep), daemon=True)
        t.start()
        readers.append(t)
    hids = hidraw_nodes() if '--no-hid' not in sys.argv else []
    for node, label in hids:
        log('also reading', node, '(interface %s)' % label)
        threading.Thread(target=hid_reader, args=(q, node, label), daemon=True).start()
    if not hids and '--no-hid' not in sys.argv:
        log('no /dev/hidraw node found for the controller')
    last_hid, detects, shown, t_sum = {}, 0, 0, time.monotonic()
    t_alive = time.monotonic()
    while True:
        try:
            kind, label, val = q.get(timeout=0.05)
        except queue.Empty:
            kind = None
        if kind == 'err':
            log('%s: USB error errno %d (controller unplugged?)' % (label, val))
            cleanup()
        elif kind == 'usb':
            if val == IAP2_DETECT:
                detects += 1
            else:
                if DEBUG:
                    log('RX %s [%d] %s' % (label, len(val), val.hex(' ')))
                cap = parse_capture(val)
                if cap:
                    log('capture msg: state=%d duration=%s' % (cap[0], cap[1]))
                    handle(*cap)
        elif kind == 'hid' and DEBUG:
            prev = last_hid.get(label)
            if val != prev:                   # only print when the report changes
                diff = ''
                if prev and len(prev) == len(val):
                    diff = '  changed bytes: ' + ', '.join('[%d] %02x->%02x' % (i, p, c)
                                                         for i, (p, c) in enumerate(zip(prev, val)) if p != c)
                log('HID %s [%d] %s%s' % (label, len(val), val.hex(' '), diff))
                last_hid[label] = val
        if DEBUG and time.monotonic() - t_sum > 30 and detects != shown:
            log('(ignored %d repeating iAP2 detect packets ff 55 02 00 ee 10 so far)' % detects)
            shown, t_sum = detects, time.monotonic()
        check_hold()
        watchdog()
        if time.monotonic() - t_alive > 2:    # controller unplugged / all readers gone -> exit cleanly
            t_alive = time.monotonic()
            if not os.path.exists(usb_dev + '/idVendor') or rd(usb_dev + '/idVendor') != VID:
                log('controller disconnected, exiting')
                cleanup()
            if readers and not any(t.is_alive() for t in readers):
                log('all USB readers stopped, exiting')
                cleanup(code=1)

if __name__ == '__main__':
    try:
        main()
    except SystemExit:
        release()
        raise
    except Exception as e:
        log('error:', e)
        cleanup()
