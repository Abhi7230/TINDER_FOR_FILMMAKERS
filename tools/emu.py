#!/usr/bin/env python3
"""Drive the Android emulator for screen checks and QA. Holds a machine-wide 'emulator' lock for the whole
command, so parallel agents take turns.

  tools/emu.py shot  --persona MEERA --route discover --out /tmp/x.png [--reset] [--speed INSTANT] [--wait 3] [--apk path]
  tools/emu.py run   steps.txt [--apk path]          # a script of steps (below), one per line
  tools/emu.py steps "launch MEERA discover" "wait 2" "tapText Interested" "shot /tmp/a.png"

Steps:
  install [apk]                 install the APK (default app/build/outputs/apk/debug/app-debug.apk)
  launch PERSONA ROUTE [reset] [speed=INSTANT]   start the app via the debug QA intent (PERSONA: MEERA|ROHAN|KABIR|DANA|NEW)
  deeplink URI                  open a deep link (calltime://m/m_001)
  wait SECONDS
  tap X Y | swipe X1 Y1 X2 Y2 [ms] | key BACK|HOME|ENTER | text "some text"
  tapText TEXT [index]          tap the node whose text or content-desc contains TEXT (case-insensitive)
  waitText TEXT [timeout]       wait until TEXT is on screen (default 15 s); fails if not
  assertText TEXT | assertNoText TEXT
  scrollDown | scrollUp         swipe the middle of the screen
  dump [file]                   print visible texts (from uiautomator)
  shot FILE                     screenshot PNG
  home                          press HOME (backgrounds the app)
Exit code 1 on the first failed assert/waitText, printing what is on screen.

Two emulators: set ANDROID_SERIAL (e.g. emulator-5556) to target one; each device has its own lock.
"""
import fcntl, os, re, subprocess, sys, time, xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SDK = os.path.expanduser("~/Library/Android/sdk")
ADB = os.path.join(SDK, "platform-tools", "adb")
DEFAULT_APK = os.path.join(ROOT, "app/build/outputs/apk/debug/app-debug.apk")
PKG = "com.tff.app"


def adb(*args, check=True, capture=True):
    r = subprocess.run([ADB, *args], capture_output=capture, text=True)
    if check and r.returncode != 0:
        raise SystemExit(f"adb {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout if capture else ""


SERIAL = os.environ.get("ANDROID_SERIAL")
LOCK = f"/tmp/tff-emulator{'-' + SERIAL if SERIAL else ''}.lock"


def ensure_device():
    out = adb("devices")
    devices = [l.split("\t")[0] for l in out.splitlines()[1:] if l.endswith("\tdevice")]
    if not devices:
        raise SystemExit("No emulator running. Start it: ~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 &")
    if SERIAL and SERIAL not in devices:
        raise SystemExit(f"ANDROID_SERIAL={SERIAL} is not connected (have: {devices})")
    if not SERIAL and len(devices) > 1:
        raise SystemExit(f"Several devices connected {devices}: set ANDROID_SERIAL to pick one")


def install(apk=None):
    apk = apk or DEFAULT_APK
    if not os.path.exists(apk):
        raise SystemExit(f"APK not found: {apk} (build first: tools/lockrun.py -- ./gradlew assembleDebug)")
    print(adb("install", "-r", "-t", apk).strip().splitlines()[-1])


def launch(persona, route, reset=False, speed=None):
    adb("shell", "am", "force-stop", PKG)
    cmd = ["shell", "am", "start", "-n", f"{PKG}/.MainActivity", "--es", "qa_persona", persona, "--es", "qa_route", route]
    if reset:
        cmd += ["--ez", "qa_reset", "true"]
    if speed:
        cmd += ["--es", "qa_speed", speed]
    adb(*cmd)


def nodes():
    """Visible nodes: list of (text, bounds(x1,y1,x2,y2))."""
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        if xml.strip().startswith("<?xml"):
            break
        time.sleep(0.5)
    out = []
    try:
        root = ET.fromstring(xml[xml.index("<?xml"):].split("\n", 1)[1] if xml.startswith("<?xml") else xml)
    except Exception:
        try:
            root = ET.fromstring(xml)
        except Exception:
            return out
    for n in root.iter("node"):
        label = (n.get("text") or "") or (n.get("content-desc") or "")
        b = re.findall(r"\d+", n.get("bounds") or "")
        if label and len(b) == 4:
            out.append((label, tuple(map(int, b))))
    return out


def find(text, index=0):
    t = text.lower()
    hits = [(l, b) for l, b in nodes() if t in l.lower()]
    return hits[index] if len(hits) > index else None


def screen_texts():
    return [l for l, _ in nodes()]


def fail(msg):
    print("FAIL:", msg)
    print("On screen:", " | ".join(screen_texts())[:3000])
    raise SystemExit(1)


def step(line):
    parts = re.findall(r'"[^"]*"|\S+', line.strip())
    parts = [p.strip('"') for p in parts]
    if not parts or parts[0].startswith("#"):
        return
    c, a = parts[0], parts[1:]
    if c == "install":
        install(a[0] if a else None)
    elif c == "launch":
        reset = "reset" in a
        speed = next((x.split("=", 1)[1] for x in a if x.startswith("speed=")), None)
        launch(a[0], a[1] if len(a) > 1 else "discover", reset, speed)
        time.sleep(2.5)
    elif c == "deeplink":
        adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", a[0], PKG)
        time.sleep(1.5)
    elif c == "wait":
        time.sleep(float(a[0]))
    elif c == "tap":
        adb("shell", "input", "tap", a[0], a[1]); time.sleep(0.6)
    elif c == "swipe":
        adb("shell", "input", "swipe", *a[:4], a[4] if len(a) > 4 else "300"); time.sleep(0.8)
    elif c == "key":
        adb("shell", "input", "keyevent", "KEYCODE_" + a[0].upper()); time.sleep(0.6)
    elif c == "home":
        adb("shell", "input", "keyevent", "KEYCODE_HOME"); time.sleep(0.8)
    elif c == "text":
        adb("shell", "input", "text", a[0].replace(" ", "%s").replace("'", "")); time.sleep(0.4)
    elif c == "tapText":
        hit = find(a[0], int(a[1]) if len(a) > 1 else 0)
        if not hit:
            fail(f"tapText: '{a[0]}' not found")
        x1, y1, x2, y2 = hit[1]
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)); time.sleep(0.8)
    elif c == "waitText":
        timeout = float(a[1]) if len(a) > 1 else 15
        t0 = time.time()
        while time.time() - t0 < timeout:
            if find(a[0]):
                return
            time.sleep(0.8)
        fail(f"waitText: '{a[0]}' not seen in {timeout:.0f}s")
    elif c == "assertText":
        if not find(a[0]):
            fail(f"assertText: '{a[0]}' not on screen")
    elif c == "assertNoText":
        if find(a[0]):
            fail(f"assertNoText: '{a[0]}' is on screen")
    elif c in ("scrollDown", "scrollUp"):
        size = adb("shell", "wm", "size").split()[-1].split("x")
        w, h = int(size[0]), int(size[1])
        y1, y2 = (int(h * 0.75), int(h * 0.3)) if c == "scrollDown" else (int(h * 0.3), int(h * 0.75))
        adb("shell", "input", "swipe", str(w // 2), str(y1), str(w // 2), str(y2), "350"); time.sleep(0.8)
    elif c == "dump":
        txt = "\n".join(screen_texts())
        if a:
            open(a[0], "w").write(txt)
        print(txt)
    elif c == "shot":
        path = a[0]
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, "wb") as f:
            f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)
        print("shot", path)
    else:
        raise SystemExit(f"unknown step: {line}")


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print(__doc__); return
    cmd, rest = sys.argv[1], sys.argv[2:]
    with open(LOCK, "w") as lf:
        t0 = time.time()
        fcntl.flock(lf, fcntl.LOCK_EX)
        if time.time() - t0 > 2:
            print(f"[emu] waited {time.time() - t0:.0f}s for the emulator lock", file=sys.stderr)
        ensure_device()
        if cmd == "shot":
            import argparse
            ap = argparse.ArgumentParser()
            ap.add_argument("--persona", default="MEERA"); ap.add_argument("--route", default="discover")
            ap.add_argument("--out", required=True); ap.add_argument("--reset", action="store_true")
            ap.add_argument("--speed"); ap.add_argument("--wait", type=float, default=3); ap.add_argument("--apk")
            ap.add_argument("--no-install", action="store_true")
            o = ap.parse_args(rest)
            if not o.no_install:
                install(o.apk)
            launch(o.persona, o.route, o.reset, o.speed)
            time.sleep(o.wait)
            step(f"shot {o.out}")
            print("texts:", " | ".join(screen_texts())[:1500])
        elif cmd == "run":
            apk = None
            if "--apk" in rest:
                apk = rest[rest.index("--apk") + 1]
            for line in open(rest[0]):
                if line.strip() == "install" and apk:
                    install(apk)
                else:
                    step(line)
        elif cmd == "steps":
            for s in rest:
                step(s)
        else:
            print(__doc__)


if __name__ == "__main__":
    main()
