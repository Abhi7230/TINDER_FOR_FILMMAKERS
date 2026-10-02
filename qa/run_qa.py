#!/usr/bin/env python3
"""Calltime end-to-end QA runner (demo mode): every step of PRD §15.3 (1-11) on a real emulator.

  qa/run_qa.py                      # all steps, installs app/build/outputs/apk/debug/app-debug.apk first
  qa/run_qa.py --only 2,3           # just steps 2 and 3 (also --only 5a style sub-steps, see --list)
  qa/run_qa.py --skip 5 --no-install
  qa/run_qa.py --list

Drives the app through tools/emu.py (imported: nodes, adb, launch) and the debug QA intent
(qa_persona / qa_route / qa_reset / qa_speed). Every check reads real on-screen copy (uiautomator dump);
OS UI (share sheet, calendar, notifications) is asserted with adb dumpsys instead of tapping system UI.
Screenshots: qa/out/<NN_name>.png. Report: qa/out/report.md (+ the PASS/FAIL table on stdout).

The device lock (/tmp/tff-emulator-<serial>.lock, the same one tools/emu.py uses) is held for one
step at a time, so other agents sharing the emulator wait between steps, never inside a flow.
Each step starts from a demo reset, so steps run in any order and alone.
"""
import argparse
import contextlib
import fcntl
import faulthandler
import signal
import os
import re
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET

QA_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(QA_DIR)
OUT = os.path.join(QA_DIR, "out")
APK = os.path.join(ROOT, "app/build/outputs/apk/debug/app-debug.apk")
PKG = "com.tff.app"
SRC = os.path.join(ROOT, "app/src/main/java/com/tff/app")
sys.path.insert(0, os.path.join(ROOT, "tools"))
import emu  # noqa: E402  (tools/emu.py: adb path, lock naming, the step vocabulary other agents use)


# ───────────────────────────── device layer ─────────────────────────────

class StepFail(Exception):
    """A hard failure: the step can't continue."""


class AdbTimeout(StepFail):
    """An adb command didn't return in time (a starved or busy emulator)."""


# Shell commands that change the UI or app state: never re-sent after a timeout (a retry could double-tap).
_NOT_RETRIED = ("input ", "am start", "uiautomator", "rm -f /sdcard/qa_ui")


class Node:
    __slots__ = ("text", "desc", "bounds", "clickable", "checked", "checkable", "cls", "scrollable", "enabled")

    def __init__(self, el):
        self.text = el.get("text") or ""
        self.desc = el.get("content-desc") or ""
        b = list(map(int, re.findall(r"\d+", el.get("bounds") or "0,0,0,0")))
        self.bounds = tuple(b) if len(b) == 4 else (0, 0, 0, 0)
        self.clickable = el.get("clickable") == "true"
        self.checked = el.get("checked") == "true"
        self.checkable = el.get("checkable") == "true"
        self.cls = el.get("class") or ""
        self.scrollable = el.get("scrollable") == "true"
        self.enabled = el.get("enabled") != "false"

    @property
    def label(self):
        return self.text or self.desc

    @property
    def center(self):
        x1, y1, x2, y2 = self.bounds
        return (x1 + x2) // 2, (y1 + y2) // 2

    def matches(self, t, exact):
        t = t.lower()
        for s in (self.text, self.desc):
            s = s.lower()
            if s and (s == t if exact else t in s):
                return True
        return False

    def __repr__(self):
        return f"<{self.label!r} {self.bounds}>"


class FastDumper:
    """Fast UI dumps: qa/dumper/QaDumper.java kept running on the device for one step (0.1-0.3 s per screen instead
    of the 3-10 s a fresh `uiautomator dump` takes on the loaded dev machine). Built here with the SDK's javac/d8
    on first use (qa/out/dumper/qa_dumper.jar). Any problem (no JDK, build or start failure, a bad dump) turns it off
    and Device falls back to `uiautomator dump`, so it can only make a run faster, never break it."""
    T = "/data/local/tmp"
    O = "/data/local/tmp/local/tmp"   # UiDevice.dumpWindowHierarchy writes under $ANDROID_DATA/local/tmp
    JAR = os.path.join(OUT, "dumper", "qa_dumper.jar")

    def __init__(self, dev):
        self.dev = dev
        self.proc = None
        self.n = 0
        self.disabled = None   # reason, once it's off for the run

    @staticmethod
    def _tools():
        sdk = os.path.dirname(os.path.dirname(emu.ADB))
        plats = sorted((d for d in os.listdir(os.path.join(sdk, "platforms"))
                        if os.path.exists(os.path.join(sdk, "platforms", d, "uiautomator.jar"))
                        and os.path.exists(os.path.join(sdk, "platforms", d, "optional", "android.test.base.jar"))),
                       key=lambda d: [int(x) for x in re.findall(r"\d+", d)])
        bts = sorted(os.listdir(os.path.join(sdk, "build-tools")), key=lambda d: [int(x) for x in re.findall(r"\d+", d)])
        jhome = os.environ.get("JAVA_HOME") or "/Users/abhi/Applicationss/Android Studio.app/Contents/jbr/Contents/Home"
        if not plats or not bts or not os.path.exists(os.path.join(jhome, "bin", "javac")):
            return None
        p = os.path.join(sdk, "platforms", plats[-1])
        return dict(javac=os.path.join(jhome, "bin", "javac"), jhome=jhome, d8=os.path.join(sdk, "build-tools", bts[-1], "d8"),
                    android=os.path.join(p, "android.jar"), uia=os.path.join(p, "uiautomator.jar"),
                    junit=os.path.join(p, "optional", "android.test.base.jar"))

    def build(self):
        src = os.path.join(QA_DIR, "dumper", "QaDumper.java")
        if os.path.exists(self.JAR) and os.path.getmtime(self.JAR) >= os.path.getmtime(src):
            return True
        t = self._tools()
        if t is None:
            self.disabled = "no JDK / SDK uiautomator.jar to build qa/dumper (using uiautomator dump)"
            return False
        b = os.path.join(OUT, "dumper")
        for sub in ("cls", "dex"):
            os.makedirs(os.path.join(b, sub), exist_ok=True)
        env = dict(os.environ, JAVA_HOME=t["jhome"])
        steps = [[t["javac"], "-source", "8", "-target", "8", "-nowarn", "-Xlint:-options",
                  "-cp", ":".join([t["uia"], t["android"], t["junit"]]), "-d", os.path.join(b, "cls"), src],
                 [t["d8"], "--min-api", "26", "--lib", t["android"], "--classpath", t["uia"], "--classpath", t["junit"],
                  "--output", os.path.join(b, "dex"), os.path.join(b, "cls", "QaDumper.class")]]
        for cmd in steps:
            r = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=300)
            if r.returncode != 0:
                self.disabled = f"qa/dumper build failed ({os.path.basename(cmd[0])}: {(r.stderr or r.stdout).strip()[:200]})"
                return False
        import zipfile
        with zipfile.ZipFile(self.JAR, "w") as z:
            z.write(os.path.join(b, "dex", "classes.dex"), "classes.dex")
        return True

    def start(self):
        """Start serving for one step (call with the device lock held). Returns True when it is ready."""
        if self.disabled:
            return False
        try:
            if not self.build():
                print(f"  [dump] {self.disabled}", flush=True)
                return False
            d = self.dev
            d.adb("push", self.JAR, f"{self.T}/qa_dumper.jar", timeout=60)
            d.sh(f"rm -f {self.T}/qa_req {self.T}/qa_stop; mkdir -p {self.O}; rm -f {self.O}/qa_*", timeout=30)
            self.proc = subprocess.Popen(
                [d.adb_bin, "-s", d.serial, "shell",
                 f"uiautomator runtest {self.T}/qa_dumper.jar /system/framework/android.test.base.jar -c QaDumper"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL)
            t0 = time.time()
            while time.time() - t0 < 30:
                ls = d.sh(f"ls {self.O} 2>/dev/null", timeout=20)
                if "qa_ready" in ls:
                    if "qa_cache_ok" in ls:
                        return True
                    self.off("the on-device dumper can't clear the accessibility cache (stale reads)")
                    return False
                if self.proc.poll() is not None:
                    break
                time.sleep(0.5)
            self.off("the on-device dumper didn't start")
        except Exception as e:
            self.off(f"the on-device dumper failed to start ({type(e).__name__}: {str(e)[:120]})")
        return False

    def dump(self):
        """One screen as XML text, or None (the caller falls back to uiautomator dump)."""
        if self.proc is None or self.proc.poll() is not None:
            return None
        self.n += 1
        n, T, O = self.n, self.T, self.O
        try:
            out = self.dev.sh(f"echo {n} > {T}/qa_req; i=0; while [ ! -e {O}/qa_ui_{n}.xml ] && [ ! -e {O}/qa_ui_{n}.err ] "
                              f"&& [ $i -lt 300 ]; do sleep 0.05; i=$((i+1)); done; cat {O}/qa_ui_{n}.xml 2>/dev/null; "
                              f"rm -f {O}/qa_ui_{n}.*", timeout=20, retry=False)
        except AdbTimeout:
            print("    (on-device dump timed out after 20s: the emulator stalled; reading again)", flush=True)
            return None
        return out if "<hierarchy" in out else None

    def off(self, why):
        print(f"  [dump] {why}: falling back to uiautomator dump for this step", flush=True)
        self.stop()

    def stop(self):
        """Stop serving (always before the device lock is released: it holds UiAutomation, which would break the
        `uiautomator dump` calls of other agents sharing the emulator)."""
        if self.proc is None:
            return
        with contextlib.suppress(Exception):
            self.dev.sh(f"touch {self.T}/qa_stop", timeout=20)
        try:
            self.proc.wait(timeout=15)
        except Exception:
            self.proc.kill()
            with contextlib.suppress(Exception):
                self.dev.sh("pkill -f QaDumper", timeout=15)
        self.proc = None
        with contextlib.suppress(Exception):
            self.dev.sh(f"rm -f {self.T}/qa_stop {self.T}/qa_req; rm -f {self.O}/qa_*", timeout=20)


class Device:
    def __init__(self, serial):
        self.serial = serial
        self.adb_bin = emu.ADB
        self._nodes = None
        self._nodes_t = 0.0
        self.w, self.h = 1080, 2400
        self.own_starts = 0   # am start / force-stop this runner issued (to tell other agents' traffic apart)
        self.own_stops = 0
        self.t_dev = 0.0
        self.dump_timeouts = 0
        self.stalls = 0          # adb commands that timed out (the emulator stalled); a failed step with stalls is retried
        self.t_dump = 0.0
        self.fast = FastDumper(self)   # started per step by main() unless --no-fast-dump
        self.fast_misses = 0
        self.fast_miss_t0 = 0.0
        self.dump_avg = 2.0      # moving average of one UI dump (s); waits stretch when the emulator is slow
        self.wait_scale = 1.0    # --wait-scale

    def patience(self, timeout):
        """The effective timeout for a positive wait: the step's number, stretched by --wait-scale, never under 20 s
        and never under 5 UI dumps (a starved emulator can take 10 s+ per dump, so a fixed 8 s wait would see 0-1 screens)."""
        return max(timeout * self.wait_scale, 20.0, 5 * self.dump_avg)

    # raw adb
    def adb(self, *args, check=False, timeout=120, retry=None):
        """Run adb against this device. Queries and idempotent commands are retried twice when adb hangs (the
        shared emulator sometimes stalls for a minute under host load); a final timeout raises AdbTimeout."""
        cmd = " ".join(args[1:]) if args[:1] == ("shell",) else ""
        tries = (3 if retry else 1) if retry is not None else (1 if cmd.startswith(_NOT_RETRIED) else 3)
        for attempt in range(tries):
            try:
                r = subprocess.run([self.adb_bin, "-s", self.serial, *args], capture_output=True, text=True,
                                   timeout=timeout, stdin=subprocess.DEVNULL)
                break
            except subprocess.TimeoutExpired:
                self.stalls += 1
                if attempt + 1 >= tries:
                    raise AdbTimeout(f"adb {' '.join(args)[:160]!r} timed out after {timeout:.0f}s"
                                     f"{f' ({tries} tries)' if tries > 1 else ''}: the emulator is stalled or busy")
                print(f"    (adb {' '.join(args)[:80]!r} timed out after {timeout:.0f}s; retrying)", flush=True)
        if check and r.returncode != 0:
            raise StepFail(f"adb {' '.join(args)} failed: {r.stderr.strip()[:300]}")
        return r.stdout

    def sh(self, cmd, timeout=120, retry=None):
        if cmd.startswith("am start"):
            self.own_starts += 1
        elif cmd.startswith(("am force-stop", "pm clear")):
            self.own_stops += 1
        return self.adb("shell", cmd, timeout=timeout, retry=retry)

    def mark(self):
        """Remember the device clock: interference() looks at app starts/stops after this."""
        try:
            self.t_dev = float(self.sh("date +%s").strip())
        except ValueError:
            self.t_dev = time.time()
        self.own_starts = self.own_stops = 0

    def interference(self):
        """Starts / force-stops of Calltime from adb shell since mark() that this runner didn't issue
        (another agent sharing the emulator). Returns a short description or None."""
        out = self.adb("logcat", "-d", "-v", "epoch", "-s", "ActivityTaskManager:I", "ActivityManager:I", timeout=60)
        starts = stops = 0
        for line in out.splitlines():
            m = re.match(r"\s*(\d+\.\d+)", line)
            if not m or float(m.group(1)) < self.t_dev - 1:
                continue
            if "START u0" in line and PKG in line and "from uid 2000" in line:
                starts += 1
            elif "Force stopping " + PKG in line:
                stops += 1
        extra_s, extra_k = starts - self.own_starts, stops - self.own_stops
        if extra_s > 0 or extra_k > 0:
            return f"{extra_s} foreign start(s), {extra_k} foreign force-stop(s) of {PKG} during the step"
        return None

    def size(self):
        m = re.search(r"(\d+)x(\d+)", self.sh("wm size"))
        if m:
            self.w, self.h = int(m.group(1)), int(m.group(2))
        return self.w, self.h

    # UI tree
    def nodes(self):
        t0 = time.time()
        try:
            return self._nodes_uncounted()
        finally:
            dt = time.time() - t0
            self.t_dump += dt   # harness overhead (reading the screen), excluded from stopwatches
            self.dump_avg = 0.7 * self.dump_avg + 0.3 * dt

    def _nodes_uncounted(self):
        deadline = time.time() + 120   # one screen read never takes longer than this, even on a stalled emulator
        for _ in range(3):
            res = self._dump(deadline)
            if res or time.time() > deadline:
                break
            time.sleep(0.7)
        self._nodes = res
        self._nodes_t = time.time()
        return res

    def _dump(self, deadline):
        xml = ""
        if self.fast.proc is not None:
            out = self.fast.dump()
            if out:
                self.fast_misses = 0
                xml = out[out.find("<?xml"):] if "<?xml" in out else out
            else:
                # No window root yet (an app (re)start leaves none for ~5 s) or a dead dumper. Never run uiautomator
                # dump while it holds UiAutomation: let the caller retry; after 30 s of misses use uiautomator dump.
                if not self.fast_misses:
                    self.fast_miss_t0 = time.time()
                self.fast_misses += 1
                if time.time() - self.fast_miss_t0 < 30:
                    time.sleep(0.3)
                    return []
                self.fast.off(f"{self.fast_misses} empty on-device dumps over 30 s")
        for _ in range(0 if xml else 4):
            if time.time() > deadline:
                break
            try:
                # rm first: when a dump fails ("could not get idle state") cat would otherwise return the previous screen.
                out = self.sh("rm -f /sdcard/qa_ui.xml; uiautomator dump /sdcard/qa_ui.xml >/dev/null 2>&1; "
                              "cat /sdcard/qa_ui.xml 2>/dev/null", timeout=45)
            except AdbTimeout:
                # A starved emulator (host under load) can hang a dump; drop the stuck uiautomator and try again.
                self.dump_timeouts += 1
                print("    (uiautomator dump timed out after 45s; retrying)", flush=True)
                with contextlib.suppress(Exception):
                    self.sh("pkill -f uiautomator", timeout=15)
                continue
            i = out.find("<?xml")
            if i >= 0:
                xml = out[i:]
                break
            time.sleep(0.6)
        res = []
        if xml:
            try:
                root = ET.fromstring(xml.split("?>", 1)[1] if xml.startswith("<?xml") else xml)
            except ET.ParseError:
                try:
                    root = ET.fromstring(xml)
                except ET.ParseError:
                    root = None
            if root is not None:
                res = [Node(e) for e in root.iter("node")]
        return res

    def texts(self, fresh=True):
        ns = self.nodes() if fresh or self._nodes is None else self._nodes
        return [n.label for n in ns if n.label]

    def screen(self, limit=2500):
        return " | ".join(self.texts(fresh=False) if self._nodes is not None else self.texts())[:limit]

    def find(self, text, exact=False, index=0, fresh=True):
        hits = self.find_all(text, exact, fresh)
        return hits[index] if len(hits) > index else None

    def find_all(self, text, exact=False, fresh=True):
        ns = self.nodes() if fresh or self._nodes is None else self._nodes
        hits = [n for n in ns if n.matches(text, exact)]
        if not hits and not fresh and self.fast.proc is not None and time.time() - self._nodes_t < 5:
            # A fast read can catch a screen mid-composition (the wait matched its first line, the rest wasn't laid
            # out yet): settle and read once more before saying "not on screen".
            time.sleep(0.6)
            hits = [n for n in self.nodes() if n.matches(text, exact)]
        return hits

    def has(self, text, exact=False, fresh=True):
        return self.find(text, exact, fresh=fresh) is not None

    def wait(self, texts, timeout=30, exact=False, poll=0.7):
        """Wait until any of [texts] is on screen; returns the one seen. Raises StepFail on timeout."""
        if isinstance(texts, str):
            texts = [texts]
        timeout = self.patience(timeout)
        t0 = time.time()
        while True:
            ns = self.nodes()
            for t in texts:
                if any(n.matches(t, exact) for n in ns):
                    return t
            if time.time() - t0 > timeout:
                raise StepFail(f"waited {timeout:.0f}s for {texts!r}; on screen: {self.screen()}")
            time.sleep(poll)

    def wait_node(self, pred, timeout=30, what="node", poll=0.7):
        """Wait for a node matching pred(Node); returns it."""
        timeout = self.patience(timeout)
        t0 = time.time()
        while True:
            for n in self.nodes():
                if pred(n):
                    return n
            if time.time() - t0 > timeout:
                raise StepFail(f"waited {timeout:.0f}s for {what}; on screen: {self.screen()}")
            time.sleep(poll)

    def edits(self):
        return [n for n in self.nodes() if n.cls.endswith("EditText")]

    def fill(self, index, text, clear=False):
        """Tap the [index]th text field and type [text] (optionally clearing it first)."""
        e = self.edits()
        if len(e) <= index:
            raise StepFail(f"no text field #{index}; on screen: {self.screen()}")
        self.tap_xy(*e[index].center, pause=0.5)
        if clear:
            self.sh("input keyevent KEYCODE_MOVE_END")
            self.sh("input keyevent " + " ".join(["KEYCODE_DEL"] * max(len(e[index].text), 1)))
        self.type_text(text)

    def wait_gone(self, text, timeout=20, exact=False):
        t0 = time.time()
        while time.time() - t0 < timeout:
            if not self.has(text, exact):
                return True
            time.sleep(0.7)
        return False

    def tap_xy(self, x, y, pause=0.5):
        self.sh(f"input tap {int(x)} {int(y)}")
        time.sleep(pause)

    def tap(self, text, exact=False, index=0, timeout=20, pause=0.6, last=False):
        """Wait for [text] and tap the centre of the matching node (the last match when [last]).
        Text fields whose typed value matches are skipped (tap a field with fill())."""
        timeout = self.patience(timeout)
        t0 = time.time()
        moving = 0
        while True:
            hits = [n for n in self.find_all(text, exact) if not n.cls.endswith("EditText")]
            if hits:
                n = hits[-1] if last else (hits[index] if len(hits) > index else None)
                if n is not None and (moving >= 3 or self._settled(n, text, exact)):
                    self.tap_xy(*n.center, pause=pause)   # (after 3 moving reads: an animated target, tap it anyway)
                    return n
                moving += 1
            if time.time() - t0 > timeout:
                raise StepFail(f"tap: {text!r} not found in {timeout:.0f}s; on screen: {self.screen()}")
            time.sleep(0.7)

    def _settled(self, n, text, exact):
        """With fast reads a target can still be moving (a fling, a sheet sliding up). Tap only when a second read
        finds it at the same place. (uiautomator dump waits for a 1 s idle window itself, so it needs no check.)"""
        if self.fast.proc is None:
            return True
        time.sleep(0.25)
        return any(m.bounds == n.bounds and m.matches(text, exact) for m in self.nodes())

    def swipe(self, x1, y1, x2, y2, ms=350, pause=0.9):
        self.sh(f"input swipe {int(x1)} {int(y1)} {int(x2)} {int(y2)} {ms}")
        # let the fling finish before the next read (fast reads don't wait for the UI to go idle like uiautomator dump)
        time.sleep(pause + (0.6 if self.fast.proc is not None else 0))

    def scroll_down(self, frac=0.45):
        w, h = self.w, self.h
        self.swipe(w // 2, int(h * 0.72), w // 2, int(h * (0.72 - frac)), 400)

    def scroll_up(self, frac=0.45):
        w, h = self.w, self.h
        self.swipe(w // 2, int(h * 0.3), w // 2, int(h * (0.3 + frac)), 400)

    def scroll_to(self, text, exact=False, max_swipes=10, up=False, frac=0.45):
        """Scroll until [text] is visible (and not hidden under the bottom bars). Returns the node."""
        for _ in range(max_swipes + 1):
            n = self.find(text, exact)
            if n and n.bounds[3] < self.h * 0.86 and n.bounds[1] > self.h * 0.06:
                return n
            (self.scroll_up if up else self.scroll_down)(frac)
        n = self.find(text, exact)
        if n:
            return n
        raise StepFail(f"scroll_to: {text!r} not found; on screen: {self.screen()}")

    def type_text(self, s, pause=0.5):
        # adb input text: %s is a space; escape shell metacharacters.
        esc = ""
        for ch in s:
            if ch == " ":
                esc += "%s"
            elif ch in "\\'\"&|;<>()$`!?*#~[]{}":
                esc += "\\" + ch
            else:
                esc += ch
        self.sh(f"input text {esc}")
        time.sleep(pause)

    def key(self, k, pause=0.7):
        self.sh(f"input keyevent KEYCODE_{k}")
        time.sleep(pause)

    def back(self):
        self.key("BACK")

    def hide_keyboard(self):
        if "mInputShown=true" in self.sh("dumpsys input_method | grep mInputShown"):
            self.back()

    def shot(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        data = subprocess.run([self.adb_bin, "-s", self.serial, "exec-out", "screencap", "-p"], capture_output=True, timeout=60,
                              stdin=subprocess.DEVNULL).stdout
        with open(path, "wb") as f:
            f.write(data)
        return path

    # app
    def force_stop(self):
        self.sh(f"am force-stop {PKG}")

    def launch(self, persona, route="discover", reset=False, speed=None, wait_for=None, timeout=60):
        self.force_stop()
        cmd = f"am start -n {PKG}/.MainActivity --es qa_persona {persona} --es qa_route {route}"
        if reset:
            cmd += " --ez qa_reset true"
        if speed:
            cmd += f" --es qa_speed {speed}"
        self.sh(cmd)
        time.sleep(2.0)
        if wait_for:
            return self.wait(wait_for, timeout)

    def route(self, route, persona, wait_for=None, timeout=45):
        """Open a route in the running app without a reset (the QA intent re-enters the persona)."""
        self.sh(f"am start -n {PKG}/.MainActivity --es qa_persona {persona} --es qa_route {route}")
        time.sleep(1.5)
        if wait_for:
            return self.wait(wait_for, timeout)

    def top_activity(self):
        out = self.sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity'")
        return out.strip()

    # OS-side assertions (no tapping system UI)
    def logcat_clear(self):
        self.adb("logcat", "-c")

    def wait_log(self, pattern, timeout=15, tags=("ActivityTaskManager:I", "*:S")):
        """Wait until a logcat line matches [pattern] (regex); returns the line."""
        timeout = self.patience(timeout)
        t0 = time.time()
        rx = re.compile(pattern)
        while time.time() - t0 < timeout:
            out = self.adb("logcat", "-d", "-v", "brief", *tags, timeout=60)
            for line in out.splitlines():
                if rx.search(line):
                    return line
            time.sleep(0.8)
        return None

    def foreground(self):
        """Package/activity of the resumed activity, e.g. 'com.tff.app/.MainActivity'."""
        out = self.sh("dumpsys activity activities | grep -E 'topResumedActivity|ResumedActivity:'")
        m = re.search(r"\{[^ ]+ [^ ]+ ([^ }]+)", out)
        return m.group(1) if m else out.strip()

    def wait_foreground(self, pred, timeout=15):
        timeout = self.patience(timeout)
        t0 = time.time()
        fg = ""
        while time.time() - t0 < timeout:
            fg = self.foreground()
            if pred(fg):
                return fg
            time.sleep(0.6)
        return None

    def back_to_app(self, max_backs=4):
        """Leave a system screen (share sheet, calendar) with BACK until Calltime is in front again."""
        for _ in range(max_backs):
            if self.foreground().startswith(PKG):
                return True
            self.back()
            time.sleep(0.8)
        if not self.foreground().startswith(PKG):
            self.sh(f"am start -n {PKG}/.MainActivity")
            time.sleep(1.5)
        return self.foreground().startswith(PKG)

    def chooser_texts(self, timeout=12):
        """Wait for the Android share sheet and return its visible texts (the preview shows the shared text)."""
        fg = self.wait_foreground(lambda f: "intentresolver" in f or "Chooser" in f or "ResolverActivity" in f, timeout)
        if not fg:
            return None, []
        time.sleep(1.2)
        return fg, self.texts()

    def last_update_time(self):
        m = re.search(r"lastUpdateTime=(.+)", self.sh(f"dumpsys package {PKG} | grep lastUpdateTime", timeout=45))
        return m.group(1).strip() if m else None


# ───────────────────────────── step framework ─────────────────────────────

STEPS = []  # (id, title, fn)


def step(sid, title):
    def deco(fn):
        STEPS.append((sid, title, fn))
        return fn
    return deco


class Ctx:
    """What a step gets: the device, a check recorder and screenshot helper."""

    def __init__(self, dev, sid, args):
        self.d = dev
        self.sid = sid
        self.args = args
        self.checks = []   # (ok, text)
        self.notes = []
        self.shots = []
        self.t0 = time.time()

    def check(self, ok, text):
        self.checks.append((bool(ok), text))
        print(f"    [{'ok' if ok else 'FAIL'}] {text}  (+{time.time() - self.t0:.0f}s)", flush=True)
        return bool(ok)

    def require(self, ok, text):
        if not self.check(ok, text):
            raise StepFail(text)

    def note(self, text):
        self.notes.append(text)
        print(f"    note: {text}  (+{time.time() - self.t0:.0f}s)", flush=True)

    def shot(self, name):
        n = f"{len(self.shots) + 1:02d}"
        path = os.path.join(OUT, f"{self.sid.zfill(2) if self.sid.isdigit() else self.sid}_{n}_{name}.png")
        self.d.shot(path)
        self.shots.append(path)
        return path

    def see(self, text, msg=None, timeout=25, exact=False):
        """Check (not require) that [text] shows within [timeout]."""
        try:
            self.d.wait(text, timeout, exact)
            return self.check(True, msg or f"shows {text!r}")
        except StepFail as e:
            return self.check(False, (msg or f"shows {text!r}") + f" — {str(e)[:400]}")

    def see_now(self, text, msg=None, exact=False):
        return self.check(self.d.has(text, exact, fresh=False), msg or f"shows {text!r}")

    def dont_see(self, text, msg=None, exact=False, fresh=False):
        return self.check(not self.d.has(text, exact, fresh=fresh), msg or f"does not show {text!r}")


LOCK_WAIT = [0.0]  # seconds spent waiting for other agents to free the emulator (reported, not counted as QA time)


@contextlib.contextmanager
def device_lock(serial):
    path = f"/tmp/tff-emulator-{serial}.lock"
    with open(path, "a") as lf:
        t0 = time.time()
        fcntl.flock(lf, fcntl.LOCK_EX)
        waited = time.time() - t0
        LOCK_WAIT[0] += waited
        if waited > 2:
            print(f"  [lock] waited {waited:.0f}s for the emulator lock (another agent was using {serial})", flush=True)
        try:
            yield
        finally:
            fcntl.flock(lf, fcntl.LOCK_UN)


def ensure_installed(dev, state, args):
    """(Re)install our APK if the device has none, or another build was installed since our install."""
    if args.no_install:
        return
    cur = dev.last_update_time()
    if state.get("installed") and cur == state["installed"]:
        return
    if not os.path.exists(APK):
        raise StepFail(f"APK not found: {APK} (build: tools/lockrun.py -- ./gradlew assembleDebug)")
    out = dev.adb("install", "-r", "-t", APK, timeout=300)
    if "Success" not in out:
        raise StepFail(f"install failed: {out[-300:]}")
    state["installed"] = dev.last_update_time()
    print(f"  installed {os.path.relpath(APK, ROOT)} ({state['installed']})", flush=True)


def prepare(dev):
    """Per-step device hygiene: notifications allowed (no permission dialog over the app), portrait, font 1.0."""
    dev.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    dev.sh("settings put system font_scale 1.0")
    dev.sh("cmd statusbar collapse")
    dev.size()


# ───────────────────────────── shared flows ─────────────────────────────

def seconds_since(t0):
    return f"{time.time() - t0:.1f}s"


def _top_card(ns):
    """(project, role line, pay line, place line, all labels) of the top Gigs card, or None."""
    labels = [n.label for n in ns if n.label]
    poster = next((l for l in labels if l.startswith("Poster for ")), None)
    if poster is None:
        return None
    i = next((k for k, l in enumerate(labels) if re.search(r"×\d+$", l)), None)
    if i is None:
        return None
    tail = labels[i + 1:i + 9]
    pay = tail[1] if len(tail) > 1 else "?"
    # Skip the when line: scattered shoot days read "3 DAYS: 31 OCT, 1, 7 NOV · ≤10H" (FIXA), which also has ", ".
    place = next((l for l in tail if re.match(r"^[^·]+, [^·]+ · ", l) and not re.match(r"^\d+ DAYS[:,]", l)), "?")
    when = tail[2] if len(tail) > 2 else "?"
    if when.startswith("REMOTE"):
        place = "REMOTE · " + place
    return poster[len("Poster for "):], labels[i], pay, place, labels


DECK_END = ["You've seen everything", "Nothing in range", "No roles fit", "You've sent 25 today"]


def deck_walk(c, stop_at=None, max_cards=30):
    """Walk the Gigs deck with Pass (one UI dump per card). Stops when [stop_at] is the top card.
    Returns (cards seen, True if stopped at [stop_at])."""
    d = c.d
    d.wait(["INTERESTED"] + DECK_END, 60)
    seen, pass_xy, last, t_tap = [], None, None, 0.0
    for _ in range(max_cards * 15):
        ns = d.nodes()
        if any(any(n.matches(t, False) for t in DECK_END) for n in ns):
            return seen, False
        card = _top_card(ns)
        btn = next((n for n in ns if n.desc == "Pass"), None)
        if btn is not None:
            pass_xy = btn.center
        if card is None or pass_xy is None:
            time.sleep(0.5)
            continue
        if last is not None and card[:3] == last[:3]:
            # Still the card we passed: the swipe-out animation is running, or the tap didn't land (slow emulator).
            if time.time() - t_tap < 8:
                time.sleep(0.5)
                continue
            d.tap_xy(*pass_xy, pause=1.0)   # re-tap without counting the card twice
            t_tap = time.time()
            continue
        seen.append(card[:4])
        badges = getattr(c, "badges", None)
        if badges is not None:
            badges[(card[0], card[1])] = {l.upper() for l in card[4] if l.upper() in ("BEGINNERS WELCOME", "STEP-UP OK", "REMOTE", "OUTSIDE YOUR RADIUS")}
        last = card
        if stop_at and card[0].lower() == stop_at.lower():
            return seen, True
        if len(seen) >= max_cards:
            break
        d.tap_xy(*pass_xy, pause=1.0)
        t_tap = time.time()
    return seen, False


def deck_find(c, title, max_cards=20):
    seen, ok = deck_walk(c, stop_at=title, max_cards=max_cards)
    if not ok:
        raise StepFail(f"{title!r} never reached the top of the deck; saw {[s[:2] for s in seen]}")
    return seen


def deck_cards(c, max_cards=30):
    """Pass through the whole Gigs deck; returns [(project, role line, pay line, place line)]."""
    return deck_walk(c, max_cards=max_cards)[0]


# ───────────────────────────── the steps (PRD §15.3) ─────────────────────────────

@step("1", "Cold install, no backend: opens in DEMO, Welcome shows persona tiles")
def s1_cold_install(c):
    d = c.d
    d.force_stop()
    d.sh(f"pm clear {PKG}")
    d.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    t0 = time.time()
    d.sh(f"am start -n {PKG}/.MainActivity")
    d.wait("TRY A DEMO AS", 60)
    c.note(f"Welcome after {seconds_since(t0)} from a cleared install")
    c.see_now("CALLTIME", "hero kicker CALLTIME")
    c.see_now("Made-up people and projects. Nothing leaves this phone.", "demo note")
    for name, role, hint in [("Meera", "Actor", "Apply, audition, get booked"),
                             ("Rohan", "Director", "Crew up a weekend short"),
                             ("Kabir", "Gaffer → DP", "Step up to a first DP credit"),
                             ("Dana", "Sound mixer", "Paid work only, remote post sound")]:
        c.check(d.find(f"Try the demo as {name}", fresh=False) is not None or d.has(name, fresh=False),
                f"persona tile {name} ({role})")
        c.see_now(hint, f"tile hint for {name}: {hint!r}")
    c.see_now("Build my own profile", "Build my own profile")
    c.shot("welcome")
    # One tap on a tile starts the offline demo: Discover with the DEMO badge, no sign-in.
    d.tap("Try the demo as Meera")
    d.wait("INTERESTED", 60)
    c.see_now("DEMO", "Discover shows the DEMO badge")
    c.see_now("Demo mode: sample data, nothing is sent to real people", "demo-mode announcement")
    c.shot("meera_discover")



MEERA_DECK_FORBIDDEN = ["Paper Boats", "Dead Drop", "Tiffin Wars", "Roommates Wanted", "Cutting Chai"]


# The Inbox opens on "Needs you" when something waits on the user, so "Conversations" can be below the fold.
INBOX_ANCHORS = ["CONVERSATIONS", "NEEDS YOU"]


def open_inbox_chat(c, name, line=None, timeout=40):
    """On the Inbox tab, open the conversation with [name] (optionally the one whose role line has [line])."""
    d = c.d
    d.wait(INBOX_ANCHORS, timeout)
    for _ in range(6):
        hits = [n for n in d.nodes() if n.desc.startswith(name + ",") and (line is None or line in n.desc)]
        if hits:
            d.tap_xy(*hits[0].center, pause=1.5)
            return hits[0].desc
        d.scroll_down(0.35)
    raise StepFail(f"no conversation with {name!r} {line or ''} in Inbox; on screen: {d.screen()}")


def tick(c, text, tries=3):
    """Tick the checkbox row labelled [text] and confirm it in the UI tree (checked="true"); re-tap if a tap was lost."""
    d = c.d
    for _ in range(tries):
        ns = d.nodes()
        n = next((x for x in ns if x.matches(text, False)), None)
        if n is None:
            raise StepFail(f"tick: {text!r} not on screen; on screen: {d.screen()}")
        boxes = [x for x in ns if x.checkable and x.bounds[1] < n.bounds[3] and x.bounds[3] > n.bounds[1]]
        if any(x.checked for x in boxes):
            return True
        d.tap_xy(*n.center, pause=1.0)
    ns = d.nodes()
    n = next((x for x in ns if x.matches(text, False)), None)
    return bool(n) and any(x.checkable and x.checked and x.bounds[1] < n.bounds[3] and x.bounds[3] > n.bounds[1] for x in ns)


def dismiss_overlay(d):
    """Close a full-screen moment (BOOKED / IN TALKS) that waits for a tap ('Tap anywhere to continue')."""
    if d.has("Tap anywhere to continue"):
        d.tap_xy(d.w // 2, d.h // 4, pause=1.0)
        d.wait_gone("Tap anywhere to continue", 10)


def accept_booking_with_pledge(c):
    """On a booking card screen: tick the notice pledge, Accept & book, wait for the BOOKED overlay."""
    d = c.d
    d.wait("Accept & book", 30)
    pledge = d.find("tell the production at least 72 hours before", fresh=False)
    c.require(pledge is not None, "booking card shows the 72-hour notice pledge")
    d.tap("Accept & book", exact=True)
    c.check(d.has("Tick the notice pledge to accept.") and not d.has("BOOKED.", fresh=False),
            "Accept is refused until the pledge is ticked ('Tick the notice pledge to accept.')")
    c.require(tick(c, "tell the production at least 72 hours before"), "the 72-hour pledge ticks")
    d.tap("Accept & book", exact=True)
    n = d.wait_node(lambda n: n.desc.startswith("BOOKED"), 20, "the BOOKED overlay")
    c.check(True, f"BOOKED overlay plays: {n.desc!r}")
    return n.desc


@step("2", "Meera: gated deck, apply to Last Local, IN TALKS, self-tape, booking card, BOOKED, Joined, day sheet")
def s2_meera(c):
    d = c.d
    # 2a. Walk Meera's whole deck (1× speed, so the owner's answer below takes a real 4–12 s). Swipe right on
    #     Last Local when it comes up, apply, then keep walking: the gates are checked on every card.
    d.launch("MEERA", "discover", reset=True, speed="NORMAL", wait_for="INTERESTED", timeout=90)
    c.shot("deck_top")
    before, found = deck_walk(c, stop_at="Last Local")
    c.require(found, "Last Local comes up in the deck")
    poster = d.find("Poster for Last Local", fresh=False)
    y = (poster.bounds[1] + poster.bounds[3]) // 2 + 300 if poster else int(d.h * 0.45)
    d.swipe(int(d.w * 0.25), y, int(d.w * 0.98), y, 250, pause=1.2)
    try:
        d.wait("Apply: Lead Actor", 8)
        c.check(True, "swipe right on Last Local opens the Apply sheet")
    except StepFail:
        c.note("the adb swipe didn't commit; used the Interested button (same deck action)")
        d.tap("Interested", exact=True)
        d.wait("Apply: Lead Actor", 15)
    c.see_now("You're applying to an unpaid role. You get:", "apply sheet spells out what an unpaid role gives")
    c.see_now("Free to apply. Every applicant gets an answer within 14 days.", "apply promise copy")
    c.shot("apply_sheet")
    d.tap("Send", exact=True)
    c.see("Sent. Every applicant gets an answer within 14 days.", "toast after sending", timeout=10)
    after, _ = deck_walk(c)
    cards = before + after
    c.note(f"Meera's deck: {len(cards)} cards: " + "; ".join(f"{p} / {r}" for p, r, *_ in cards))
    c.check(13 <= len(cards) <= 16, f"deck size {len(cards)} (DEMO_IDS: 14, 15 on a Tuesday anchor)")
    c.check(all("ACTOR" in r for _, r, *_ in cards), "every card is an actor role")
    c.check(all("Mumbai" in pl or pl.startswith("REMOTE") for *_, pl in cards),
            "every card is in Mumbai: " + ", ".join(sorted({pl.split(" · ")[0] for *_, pl in cards})))
    c.check(not any(p in MEERA_DECK_FORBIDDEN for p, *_ in cards),
            "no project she already has a live match or application on (Paper Boats, Dead Drop, Tiffin Wars, Roommates, Cutting Chai)")
    c.check(all(("Unpaid" not in pay) or ("·" in pay) for _, _, pay, *_ in cards), "unpaid cards say what you get")

    # 2b. IN TALKS within ~12 s (the owner's decision time, from the demo state) and visible in the Inbox.
    st = demo_state(d)
    app = next((a for a in delta(st, "applications").values() if a.get("roleId") == "r_ll_lead" and a.get("applicantId") == "u_meera"), {})
    took = (app.get("decidedAt", 0) - app.get("createdAt", 0)) / 1000 if app.get("decidedAt") else None
    c.check(app.get("status") in ("MATCHED", "DECLINED") and took is not None and took <= 12.5,
            f"the owner answers {took if took is None else round(took, 1)}s after the application (status {app.get('status')}; PRD: IN TALKS or a closure within ~12 s at 1×)")
    d.tap("Inbox", exact=True)
    d.wait_node(lambda n: n.desc.startswith("Aniket D., Actor · Last Local"), 40, "the Last Local conversation")
    c.see("In talks", "Inbox shows the Last Local conversation In talks", timeout=5)
    c.shot("inbox_in_talks")

    # 2c. Chat: terms summary, self-tape audition request with sides + the fixed safety lines, Send my tape.
    open_inbox_chat(c, "Aniket D.", "Last Local")
    d.wait("AUDITION REQUEST", 40)
    c.see_now("Self-tape", "audition request is a self-tape")
    c.see_now("demo.calltime.app/sides/p_last_local", "sides link in the request")
    c.see_now("Open sides", "Open sides action")
    for line in ["Every audition on Calltime", "No nudity at auditions", "No fees", "Bring a friend if you like", "You never have to meet alone"]:
        c.see_now(line, f"fixed safety line {line!r}")
    c.shot("audition_request")
    d.scroll_to("Terms on the listing:", up=True, max_swipes=4)
    c.see_now("In talks for Lead actor on Last Local. Terms on the listing: Unpaid · 2 meals · TA ₹300",
              "chat opens with the terms summary")
    d.scroll_to("Send my tape", exact=True)
    d.tap("Send my tape", exact=True)
    d.wait("Link to your tape", 10)
    d.fill(0, "https://youtu.be/meera-tape-01")
    d.tap("Send my tape", exact=True, last=True)
    c.see("You sent your tape.", "tape sent (card says 'You sent your tape.')", timeout=15)

    # 2d. The owner sends the booking card; accept with the pledge; BOOKED overlay.
    t1 = time.time()
    d.tap("Review offer", exact=True, timeout=45)
    c.note(f"booking card arrived {time.time() - t1:.1f}s after the tape")
    d.wait("BOOKING CARD v1", 20)
    c.see_now("If you accept, you're booked as Lead Actor", "card summary line (offer: not booked until accepted)")
    c.see_now("Check every line. Accepting books you; asking to change takes you back to the chat.", "offer note")
    c.shot("booking_card")
    overlay = accept_booking_with_pledge(c)
    c.check("Last Local" in overlay and "CALL" in overlay, "overlay names the project and the call")
    c.shot("booked_overlay")

    # 2e. Day sheet: Got it, Add to calendar (intent), Share with trusted contact (share sheet).
    d.tap("Open day sheet", exact=True)
    d.wait("Day 1 of 2", 20)
    c.see_now("Your call", "day sheet shows 'Your call'")
    d.tap("Got it", exact=True)
    c.see("you confirmed at", "Got it confirmed ('Got it · you confirmed at …')", timeout=10)
    c.shot("day_sheet")
    d.scroll_to("Add to calendar", exact=True)
    d.logcat_clear()
    d.tap("Add to calendar", exact=True, pause=1.5)
    line = d.wait_log(r"START.*act=android\.intent\.action\.INSERT.*(calendar|events)", 12)
    fg = d.wait_foreground(lambda f: not f.startswith(PKG), 8)
    c.check(line is not None, f"Add to calendar fires ACTION_INSERT on calendar events ({(line or 'no START line')[:160]})")
    c.note(f"calendar foreground: {fg}")
    if fg:
        c.shot("calendar_intent")
    c.require(d.back_to_app(), "back in Calltime after the calendar")
    d.scroll_to("Share with trusted contact", exact=True)
    d.logcat_clear()
    d.tap("Share with trusted contact", exact=True, pause=1.5)
    chooser = d.wait_log(r"START.*act=android\.intent\.action\.CHOOSER", 10)
    fg, txt = d.chooser_texts()
    c.check(chooser is not None and fg is not None, f"share sheet opens ({fg})")
    preview = " ".join(txt)
    c.check('I\'m on a shoot: "Last Local"' in preview or "I'm on a shoot" in preview,
            "share sheet text is the trusted-contact template (I'm on a shoot: \"Last Local\" …)")
    c.shot("share_sheet")
    c.require(d.back_to_app(), "back in Calltime after the share sheet")

    # 2f. Projects → Joined shows it.
    d.route("projects", "MEERA", wait_for="Joined")
    d.tap("Joined", exact=True)
    c.see("Last Local", "Projects → Joined lists Last Local", timeout=15)
    c.shot("projects_joined")


@step("3", "Meera safety: scam banner, masked number, Report → Safety Center RECEIVED; Dana can't see Meera")
def s3_safety(c):
    d = c.d
    d.launch("MEERA", "chat/m_meera_scam", reset=True, speed="INSTANT", wait_for="This is a common scam", timeout=90)
    c.check(d.has("Casting Hub Official", exact=True, fresh=False), "chat with Casting Hub Official opens (organisation-style names are shown in full, not 'Casting O.')")
    c.see_now("Calltime never allows charging cast or crew. This is a common scam.", "red money banner")
    c.see_now("Keeping talks on Calltime until you're booked protects you.", "off-platform banner")
    msg = d.find("registration fee", fresh=False)
    c.check(msg is not None and "•••• shared contact hidden" in msg.label and not re.search(r"\d{5}", msg.label),
            "the phone number in the scam message is masked ('•••• shared contact hidden')")
    c.shot("scam_chat")
    d.tap("Report", exact=True)
    d.wait("Tell us what happened. Every report is reviewed by a person.", 10)
    d.tap("Asked for money", exact=True)
    d.tap("Send report", exact=True)
    c.see("Thanks. We aim to review within 24 hours.", "report confirmation", timeout=15)
    c.see_now("You can follow it in Safety Center under My reports.", "points to Safety Center")
    c.shot("report_sent")
    if d.has("Done", exact=True, fresh=False):
        d.tap("Done", exact=True)
    c.see("You reported this conversation", "chat shows 'You reported this conversation'", timeout=10)
    d.route("safety", "MEERA", wait_for="Safety Center")
    d.scroll_to("MY REPORTS", exact=True)
    d.scroll_down(0.25)
    c.see("RECEIVED", "Safety Center → My reports shows the report as RECEIVED", timeout=10)
    c.see_now("Asked for money", "the report reads 'Asked for money'")
    c.shot("safety_center")

    # Dana: no shared project or match with Meera → Meera appears nowhere, profile(u_meera) is null.
    d.launch("DANA", "person/u_meera", speed="INSTANT", wait_for=["Profile not available.", "Meera"], timeout=60)
    c.see_now("Profile not available.", "as Dana, person/u_meera → 'Profile not available.' (DemoRepo.profile(u_meera) == null)")
    c.dont_see("Meera", "Meera's name is not shown on that screen")
    c.shot("dana_person_meera")
    seen_meera = []
    for route, anchor in [("inbox", "Inbox"), ("discover", "INTERESTED"), ("projects", "Projects"), ("me", "Me")]:
        d.route(route, "DANA", wait_for=anchor)
        for _ in range(3):
            if d.has("Meera", fresh=False):
                seen_meera.append(route)
                break
            d.scroll_down(0.5)
            d.nodes()
    c.check(not seen_meera, f"Meera appears on none of Dana's tabs (Inbox, Discover, Projects, Me){' — seen on ' + str(seen_meera) if seen_meera else ''}")


def demo_control(c, persona, label, toast=None):
    """Settings → Demo controls → tap [label] (e.g. 'Jump past next shoot'); returns the toast seen."""
    d = c.d
    d.route("settings", persona, wait_for="Settings")
    d.scroll_to(label, exact=True, max_swipes=14)
    d.tap(label, exact=True, pause=1.2)
    if toast:
        return d.wait(toast, 20)
    return None


def answer_feedback(c, answers):
    """In the private feedback sheet, pick [option] for each (question, option) and Submit."""
    d = c.d
    for q, opt in answers:
        qn = d.scroll_to(q, exact=True, max_swipes=6, frac=0.3)
        ns = d.nodes()
        qn = next((n for n in ns if n.matches(q, True)), qn)
        below = [n for n in ns if n.matches(opt, True) and n.bounds[1] >= qn.bounds[3] - 4]
        below.sort(key=lambda n: (n.bounds[1], n.bounds[0]))
        if not below:
            d.scroll_down(0.2)
            ns = d.nodes()
            qn = next((n for n in ns if n.matches(q, True)), qn)
            below = sorted([n for n in ns if n.matches(opt, True) and n.bounds[1] >= qn.bounds[3] - 4], key=lambda n: (n.bounds[1], n.bounds[0]))
        if not below:
            raise StepFail(f"feedback: no {opt!r} under {q!r}; on screen: {d.screen()}")
        d.tap_xy(*below[0].center, pause=0.5)
    d.scroll_to("Submit", exact=True, max_swipes=6)
    d.tap("Submit", exact=True, pause=1.2)


COLLAB_FEEDBACK = [("Paid as agreed?", "On time"), ("Delivered what was promised?", "Yes"),
                   ("Kept the booking as agreed?", "Yes"), ("Felt safe and respected?", "Yes"),
                   ("Work with them again?", "Yes")]


@step("4", "Meera wrap: jump past shoot, wrap items, owner marks PAID, Meera confirms, feedback reveal, overdue FOOTAGE")
def s4_wrap(c):
    d = c.d
    d.launch("MEERA", "settings", reset=True, speed="INSTANT", wait_for="Settings", timeout=90)
    t = demo_control(c, "MEERA", "Jump past next shoot", "Demo date is now")
    c.check(True, "Settings → Jump past next shoot moves the demo date")
    c.note(next((x for x in d.texts(fresh=False) if x.startswith("Demo date is now")), t))
    d.route("wrap/m/m_meera_cutting_chai", "MEERA", wait_for="Wrap checklist")
    d.wait("WHAT'S OWED", 20)
    c.see_now("Wrapped", "Cutting Chai booking is Wrapped")
    for item in ["Pay", "Footage for your reel", "Credit"]:
        c.see_now(item, f"wrap item {item!r} appears", exact=True)
    c.shot("wrap_items")
    # The seeded owner marks Pay done (on wrap, or within the terms: nudge the clock if needed).
    marked = False
    for extra in range(3):
        if d.has("Paid via", fresh=True) or d.has("Not received", exact=True, fresh=False):
            marked = True
            break
        demo_control(c, "MEERA", "+1 day", "Demo date is now")
        d.route("wrap/m/m_meera_cutting_chai", "MEERA", wait_for="WHAT'S OWED")
    c.check(marked, "the owner marked Pay as sent (Pay shows Done with the payment note)")
    c.shot("pay_marked")
    pay = d.find("Pay", exact=True, fresh=True)
    gots = [n for n in d.find_all("Got it", exact=True, fresh=False) if pay and n.bounds[1] > pay.bounds[1]]
    c.require(bool(gots), "Meera can confirm the payment (Got it under Pay)")
    d.tap_xy(*gots[0].center, pause=1.2)
    c.see("Confirmed. Thanks for closing the loop.", "Meera confirms: toast", timeout=10)
    c.see("1 of 3 items confirmed", "progress reads 1 of 3 items confirmed", timeout=10)
    c.see_now("CONFIRMED", "Pay shows CONFIRMED <date>")
    c.shot("pay_confirmed")
    d.tap("Give private feedback", exact=True)
    d.wait("Private feedback on Farida", 10)
    c.see_now("Farida answers about you: Showed up on time? · Prepared?", "sheet says what Farida answers about Meera")
    c.shot("feedback_sheet")
    answer_feedback(c, COLLAB_FEEDBACK)
    c.see(["Thanks. Answers unlock when you both submit, or in 14 days.", "Feedback is in", "Feedback sent"],
          "feedback submits", timeout=10)
    if d.has("Done", exact=True):
        d.tap("Done", exact=True)
    c.see("Feedback is in", "feedback reveals after the counterpart answers ('Feedback is in')", timeout=30)
    c.see_now("You both answered. Track records now include this booking.", "reveal body copy")
    c.shot("feedback_revealed")
    # Overdue FOOTAGE on the older project (Iron Gate, Rakesh never delivers).
    d.route("wrap/m/m_meera_iron_gate", "MEERA", wait_for="WHAT'S OWED")
    d.scroll_to("Footage for your reel", exact=True)
    c.see("Overdue. We've nudged", "Iron Gate: FOOTAGE item is overdue with a nudge ('Overdue. We've nudged …')", timeout=10)
    c.shot("iron_gate_overdue")


# ── demo state (debug build: run-as can read the persisted JSON) ──

def demo_state(d, wait_s=1.5):
    """The persisted demo snapshot (files/demo_state_v2.json): small fields in full, big collections as deltas."""
    import json
    time.sleep(wait_s)  # the store writes shortly after each change
    raw = d.adb("shell", "run-as", PKG, "cat", "files/demo_state_v2.json", timeout=60)
    try:
        return json.loads(raw)
    except ValueError:
        return {}


def delta(state, coll):
    return (state.get(coll) or {}).get("put") or {}


# ── wizard helpers ──

def wiz_next(c, expect=None):
    d = c.d
    d.hide_keyboard()
    d.tap("Next", exact=True, pause=1.4)
    if expect:
        d.wait(expect, 15)


def pick_date(c, days_after_first_enabled=14):
    """In an open Material date picker: tap an enabled day [n] days after the first selectable one, then OK."""
    d = c.d
    d.wait("Select date", 10)
    for _ in range(3):
        days = [n for n in d.nodes() if re.match(r"^(Today, )?\w+day, \w+ \d+, \d{4}$", n.label) and n.enabled]
        if len(days) > days_after_first_enabled:
            n = days[days_after_first_enabled]
            d.tap_xy(*n.center, pause=0.6)
            d.tap("OK", exact=True, pause=1.0)
            return n.label
        if days:
            n = days[-1]
            d.tap_xy(*n.center, pause=0.6)
            d.tap("OK", exact=True, pause=1.0)
            return n.label
        d.tap("Change to next month", exact=True, pause=0.8)
    raise StepFail(f"no selectable date; on screen: {d.screen()}")


def wiz_basics(c, title, logline, post_only=False):
    d = c.d
    d.wait("Basics", 30)
    d.fill(0, title)
    d.fill(1, logline)
    d.hide_keyboard()
    if post_only:
        d.scroll_to("Already shot? Post-only project", exact=True)
        d.tap("Already shot? Post-only project", exact=True)
    wiz_next(c, "When and where")


def wiz_pick_role(c, name, search=None):
    d = c.d
    d.wait(["Which role?", "Pick the new role"], 10)
    d.fill(0, search or name)
    d.hide_keyboard()
    d.tap(name, exact=True, pause=1.4)
    d.wait("How many", 10)


REVIEW_CHECKS = ["Title, logline, runtime and language", "At least one role", "Pay is clear on every paid role",
                 "Unpaid roles say what people get", "Dates, hours, food and travel answered", "Delivery dates on remote roles",
                 "Who brings the gear", "Picture-lock date for music and sound", "Content answered",
                 "Auditions are safe by design", "No fees, contact details or spec work"]


def review_status(d):
    """{check label: 'Done' | 'Needs a fix'} from the Review step (scrolls through it)."""
    out = {}
    for _ in range(4):
        for n in d.nodes():
            m = re.match(r"^(.*)\. (Done|Needs a fix)$", n.label)
            if m:
                out[m.group(1)] = m.group(2)
        if d.has("Ready to publish", fresh=False) and len(out) >= 6:
            break
        d.scroll_down(0.5)
    d.scroll_up(0.9)
    d.scroll_up(0.9)
    return out


SHARE_RX = [
    r'^(CASTING|CREW) CALL · .+ · ".+" \(.+, \d+ min\)$',
    r'^(Remote · .*deliver by \d+ \w+|.+ · \d+ days? · ≤\d+h · .+, .+|.+ · Dates to be confirmed.*)$',
    r"^Pay: .+",
    r"^Apply free on Calltime(: https?://\S+/r/\S+| \(Google Play\): https://play\.google\.com/store/apps/details\?id=com\.tff\.app)$",
]


def share_lines(d):
    """The role share text shown on screen (one text node, or one node per line)."""
    labels = [n.label for n in d.nodes()]
    i = next((k for k, l in enumerate(labels) if re.match(r"^(CASTING|CREW) CALL · ", l)), None)
    if i is None:
        return []
    lines = labels[i].split("\n")
    return lines if len(lines) >= 4 else labels[i:i + 4]


def share_text_ok(lines):
    return len(lines) == 4 and all(re.match(rx, l) for rx, l in zip(SHARE_RX, lines))


@step("5a", "Rohan: draft fails R02 with a fix → publish; share text matches the template; applicants within 90 s")
def s5a_draft(c):
    d = c.d
    d.launch("ROHAN", "post/p_rohan_draft", reset=True, speed="NORMAL", wait_for="Review", timeout=90)
    d.wait("to fix before you publish", 20)    # "1 thing" / "2 things" (FIXD: one R02 blocker)
    st = review_status(d)
    bad = [k for k, v in st.items() if v == "Needs a fix"]
    c.check(bad == ["Unpaid roles say what people get"], f"the draft fails R02 only (needs a fix: {bad})")
    c.see("If you can't pay people, feed people. Unpaid shoots need at least one meal a day.", "R02 explains the problem", timeout=8)
    c.see_now("Set 2 meals a day", "R02 offers the fix 'Set 2 meals a day'")
    c.shot("draft_r02")
    d.tap("Set 2 meals a day", exact=True, pause=1.5)
    c.see("Ready to publish", "after the fix: Ready to publish", timeout=10)
    d.tap("Publish", exact=True)
    t_pub = time.time()
    d.wait("You're crewing", 20)
    c.check(True, "publish succeeds ('You're crewing')")
    lines = share_lines(d)
    c.check(share_text_ok(lines), "share text on the sheet matches the F17 template: " + " / ".join(lines))
    c.check(bool(lines) and '"Mallige"' in lines[0], "share text names the project (\"Mallige\")")
    c.shot("published_share_text")
    d.tap("Share this role", exact=True, pause=1.5)
    fg, txt = d.chooser_texts()
    c.check(fg is not None, f"Share this role opens the Android share sheet ({fg})")
    preview = "\n".join(txt)
    c.check(bool(lines) and lines[0] in preview, "the share sheet carries the same text (first line in its preview)")
    c.shot("share_sheet")
    c.require(d.back_to_app(), "back in Calltime after the share sheet")
    # Applicants arrive within 90 s (normal speed).
    d.route("dashboard/p_rohan_draft", "ROHAN", wait_for="APPLIED", timeout=30)
    got = None
    while time.time() - t_pub < 95:
        ns = d.nodes()
        counts = [int(m.group(1)) for n in ns for m in [re.match(r"^(\d+)/\d+ APPLIED$", n.label)] if m]
        if counts and sum(counts) > 0:
            got = time.time() - t_pub
            break
        time.sleep(2)
    c.check(got is not None and got <= 90, f"first applicants arrive {got:.0f}s after publishing (≤ 90 s)" if got else "no applicants within 90 s")
    c.shot("dashboard_applicants")


@step("5b", "Rohan: Crew up Let's talk / Backup / Not this time, closure, Scout tabs, booking → FILLED, pending auto-closed")
def s5b_crewup(c):
    d = c.d
    d.launch("ROHAN", "crewup/r_fc_actor", reset=True, speed="INSTANT", wait_for="Every applicant gets an answer.", timeout=90)
    c.see_now("Actors apply to you. Calltime doesn't let anyone browse actors.", "lead-actor role: no Scout (explains why)")
    c.dont_see("Scout", "lead-actor role has no Scout tab", exact=True)
    c.shot("crewup_actor_no_scout")
    for want in ["Not this time", "Backup", "Let's talk"]:
        c.check(d.has(want, exact=True, fresh=False), f"Crew up offers {want!r}")

    def top_name():
        ns = d.nodes()
        labels = [n.label for n in ns]
        k = next((i for i, l in enumerate(labels) if re.match(r"^[A-Z ]+ · [A-Z ]+$", l) and "·" in l), None)
        return labels[k + 1] if k is not None and k + 1 < len(labels) else None

    declined = top_name()
    d.tap("Not this time", exact=True, pause=1.0)
    c.see("gets a polite closure", f"Not this time on {declined}: toast says they get a polite closure", timeout=8)
    backup = top_name()
    d.tap("Backup", exact=True, pause=1.0)
    c.see("is on your backup list", f"Backup on {backup}: toast", timeout=8)
    talk = top_name()
    d.tap("Let's talk", exact=True, pause=1.5)
    c.see("IN TALKS", f"Let's talk on {talk}: IN TALKS overlay", timeout=10)
    c.shot("crewup_in_talks")
    d.tap("Keep swiping", exact=True)
    st = demo_state(d)
    apps = [a for a in delta(st, "applications").values() if a.get("roleId") == "r_fc_actor"]
    closed = [a for a in apps if a.get("status") == "DECLINED" and a.get("declineReason")]
    c.check(bool(closed), "demo state: the declined applicant's application is DECLINED with a closure message"
            + (f" ({closed[0]['applicantId']}: {closed[0]['declineReason']!r})" if closed else ""))
    c.check(any(a.get("status") == "BACKUP" for a in apps), "demo state: the backup is on the BACKUP list")
    c.check(any(a.get("status") == "MATCHED" for a in apps), "demo state: Let's talk made a match (MATCHED)")

    # Sound mixer: has a Scout tab; book the first applicant → FILLED, other pending applicants auto-closed.
    d.route("crewup/r_fc_sound", "ROHAN", wait_for="Applicants ·")
    c.see_now("Scout", "sound-mixer role has a Scout tab", exact=True)
    n_apps = next((int(m.group(1)) for t in d.texts(fresh=False) for m in [re.match(r"^Applicants · (\d+)$", t)] if m), 0)
    c.shot("crewup_sound_scout")
    who = top_name()
    d.tap("Let's talk", exact=True, pause=1.5)
    d.tap("Open chat", exact=True, pause=1.5)
    d.tap("Send booking card", exact=True, timeout=25)
    d.wait("WHAT ", 15)
    c.shot("booking_editor")
    d.tap("Send booking card", exact=True, last=True, pause=1.5)
    c.see(["Booking card v1 sent.", "accepted the booking card"], "owner sends booking card v1", timeout=15)
    c.see(["Booked · first day", "Booked · next", "Booked"], f"{who} accepts: chat shows Booked", timeout=30)
    c.shot("sound_booked")
    st = demo_state(d)
    role = delta(st, "roles").get("r_fc_sound", {})
    c.check(role.get("status") == "FILLED", f"role r_fc_sound is FILLED in the demo state (status={role.get('status')})")
    others = [a for a in delta(st, "applications").values() if a.get("roleId") == "r_fc_sound" and a.get("status") in ("CLOSED", "DECLINED", "EXPIRED")]
    c.check(len(others) >= max(n_apps - 1, 1), f"pending sound applicants auto-closed ({len(others)} of {n_apps - 1}): "
            + "; ".join(sorted({str(a.get('declineReason')) for a in others})))
    d.route("dashboard/p_filter_coffee", "ROHAN", wait_for="Roles")
    d.scroll_to("Sound Mixer", exact=False)
    c.see("FILLED", "dashboard shows the sound role FILLED", timeout=10)
    c.shot("dashboard_filled")


@step("5c", "Rohan: post 3 on-set roles with Pay for all roles + Duplicate in ≤ 4 min; post-only project (no dates, R20 lock date)")
def s5c_post(c):
    d = c.d
    d.launch("ROHAN", "projects", reset=True, speed="INSTANT", wait_for="Projects", timeout=90)
    # Stopwatch: a second project with 3 on-set roles.
    t0, dump0 = time.time(), d.t_dump
    d.route("post", "ROHAN", wait_for="Basics")
    wiz_basics(c, "Second Shift QA", "A night-shift nurse and a cab driver share one long Bengaluru night.")
    d.tap("Jayanagar", exact=True)
    d.tap("Outdoors or public", exact=True)
    weekend = d.find_all(r"Sat ", exact=False, fresh=True)
    wk = next((n for n in weekend if re.match(r"^Sat \d+ – Sun \d+ \w+$", n.label)), None)
    c.require(wk is not None, "Quick pick offers a weekend")
    d.tap_xy(*wk.center)
    d.scroll_down(0.5)
    d.tap("Dates locked", exact=True)
    d.tap("2 meals", exact=True)
    d.tap("Production van", exact=True)
    wiz_next(c, "PAY FOR ALL ROLES")
    d.tap("Paid per day", exact=True)
    d.fill(0, "2500")
    d.hide_keyboard()
    d.tap("On wrap", exact=True)
    d.scroll_to("Add role", exact=True)
    d.tap("Add role", exact=True, pause=1.4)
    wiz_pick_role(c, "Gaffer")
    c.check(d.has("2500", exact=True), "new role inherits Pay for all roles (₹2500/day)")
    d.tap("Production provides gear", exact=True)
    d.tap("Done", exact=True, pause=1.2)
    for prev, name, search in [("Gaffer", "Production assistant", "Production assistant"), ("Production assistant", "Production sound mixer", "sound mixer")]:
        d.scroll_to(f"Duplicate {prev}", exact=True)
        d.tap(f"Duplicate {prev}", exact=True, pause=1.4)
        wiz_pick_role(c, name, search)
        d.scroll_down(0.5)
        if d.has("Who brings the gear", fresh=False) and not d.has("Production provides gear", fresh=False):
            d.scroll_down(0.3)
        if d.has("Production provides gear", exact=True, fresh=False):
            d.tap("Production provides gear", exact=True)
        d.tap("Done", exact=True, pause=1.2)
    d.scroll_to("ROLES · 3", exact=True)
    c.check(True, "3 roles added (ROLES · 3) via Add role + Duplicate ×2")
    d.scroll_down(0.4)
    labels = d.texts()
    rows = [(l, labels[i + 1] if i + 1 < len(labels) else "") for i, l in enumerate(labels) if re.search(r" ×\d+$", l)]
    c.check(len(rows) == 3 and all(p == "₹2,500/day · paid on wrap" for _, p in rows),
            "all 3 roles carry the Pay-for-all terms: " + "; ".join(f"{r} {p}" for r, p in rows))
    c.shot("three_roles")
    wiz_next(c, "Safety")
    d.tap("None of these", exact=True)
    wiz_next(c, "Review")
    d.wait(["Ready to publish", "to fix before you publish"], 15)
    if not d.has("Ready to publish", fresh=False):
        c.check(False, "review is green: " + str({k: v for k, v in review_status(d).items() if v != 'Done'}))
    d.tap("Publish", exact=True)
    d.wait("You're crewing", 20)
    wall = time.time() - t0
    took = wall - (d.t_dump - dump0)  # minus the time this runner spent reading the screen (uiautomator dumps)
    c.check(took <= 240, f"published 3 on-set roles in {took:.0f}s of app time (≤ 4 min; wall clock {wall:.0f}s "
                         f"incl. {d.t_dump - dump0:.0f}s of uiautomator dumps by this runner)")
    c.shot("second_published")
    d.tap("Go to the dashboard", exact=True, last=True)

    # Post-only project: stage POST, editor + composer remote.
    d.route("post", "ROHAN", wait_for="Basics")
    wiz_basics(c, "Night Bus Post QA", "Already shot: a night bus, two strangers and one long ride across the city.", post_only=True)
    c.see_now("Post-only projects just need the city. Every role is remote.", "post-only When and where intro")
    c.dont_see("SHOOT DATES", "post-only: no shoot-date fields", exact=True)
    c.dont_see("Quick pick: a weekend", "post-only: no weekend quick pick")
    c.shot("post_only_when")
    wiz_next(c, "PAY FOR ALL ROLES")
    d.tap("Paid flat fee", exact=True)
    d.fill(0, "15000")
    d.hide_keyboard()
    d.tap("Within 15 days", exact=True)
    for i, name in enumerate(["Editor", "Composer"]):
        d.scroll_to("Add role", exact=True)
        d.tap("Add role", exact=True, pause=1.4)
        wiz_pick_role(c, name)
        d.scroll_to("Deliver by", exact=True)
        c.check(d.has("Works from anywhere and delivers by a date. No day sheet, no call time.", fresh=False), f"{name} is remote")
        d.tap("Pick a date", exact=True)
        picked = pick_date(c, 20 + i)
        c.note(f"{name} deliver by {picked}")
        d.tap("Done", exact=True, pause=1.2)
    wiz_next(c, "Safety")
    d.tap("None of these", exact=True)
    wiz_next(c, "Review")
    d.wait(["Ready to publish", "to fix before you publish"], 15)
    st = review_status(d)
    c.check(st.get("Picture-lock date for music and sound") == "Needs a fix",
            f"R20 asks for a picture-lock date (review: {st.get('Picture-lock date for music and sound')})")
    c.shot("post_only_r20")
    d.scroll_to("Picture-lock date for music and sound", exact=True)
    fix = [n for n in d.find_all("Fix", exact=True) if n.clickable or True]
    lock_row = d.find("Picture-lock date for music and sound", exact=True, fresh=False)
    target = next((n for n in fix if lock_row and n.bounds[1] > lock_row.bounds[1]), None) or lock_row
    d.tap_xy(*target.center, pause=1.5)
    d.wait("Picture lock date", 15)
    pl = d.find("Picture lock date", exact=True, fresh=False)
    picker = next((n for n in d.nodes() if n.label in ("Pick a date",) and pl and n.bounds[1] > pl.bounds[1]), None)
    c.require(picker is not None, "the R20 fix opens the picture-lock date field")
    d.tap_xy(*picker.center, pause=1.2)
    c.note("picture lock " + pick_date(c, 7))
    d.hide_keyboard()
    d.tap(["Back to review"][0], exact=True, pause=1.4) if d.has("Back to review", exact=True) else wiz_next(c)
    d.wait(["Ready to publish", "to fix before you publish"], 15)
    c.check(d.has("Ready to publish", fresh=False), "review is green after setting the lock date")
    d.tap("Publish", exact=True)
    c.see("You're crewing", "post-only project publishes", timeout=20)
    lines = share_lines(d)
    c.check(share_text_ok(lines) and re.match(r"^Remote · deliver by \d+ \w+$", lines[1]),
            "editor share text: 'Remote · deliver by …' (no shoot dates): " + " / ".join(lines[:2]))
    d.tap("Composer", exact=True, pause=1.0)
    lines = share_lines(d)
    c.check(share_text_ok(lines) and re.match(r"^Remote · picture lock \d+ \w+ · deliver by \d+ \w+$", lines[1]),
            "composer share text: 'Remote · picture lock … · deliver by …': " + " / ".join(lines[:2]))
    c.shot("post_only_published")


def kept_bookings(c, persona):
    """'Kept bookings N of M' (as a production) from the Me tab."""
    d = c.d
    d.route("me", persona, wait_for="Edit profile")   # TRACK RECORD sits below the fold in the Me LazyColumn
    d.scroll_to("Kept bookings")
    return next((t for t in d.texts(fresh=False) if t.startswith("Kept bookings")), None)


@step("6", "Withdraw → backup: simulated withdrawal reopens the slot, Offer to backup #1 sends an Ask; release <72h warns, Kept bookings changes")
def s6_withdraw(c):
    d = c.d
    d.launch("ROHAN", "me", reset=True, speed="INSTANT", wait_for="Edit profile", timeout=90)
    before = kept_bookings(c, "ROHAN")
    c.check(before == "Kept bookings 3 of 3", f"Rohan starts with {before!r} (DEMO_IDS: 3/3)")
    demo_control(c, "ROHAN", "Simulate a crew withdrawal")
    toast = d.wait_node(lambda n: " withdrew · " in n.label, 15, "the withdrawal toast")
    c.check(True, f"Settings → Simulate a crew withdrawal: {toast.label!r}")
    c.check("Backup #1: Darshan N." in toast.label, "toast names backup #1 (Darshan N.)")
    c.check(".." not in toast.label, "toast copy has no doubled full stop")
    c.shot("withdrawal_toast")
    st = demo_state(d)
    pd = delta(st, "roles").get("r_fc_pd", {})
    c.check(pd.get("status", "OPEN") == "OPEN" and int(pd.get("filledCount", 0)) == 0,
            f"the PD slot reopens (r_fc_pd status={pd.get('status', 'OPEN')}, filled={pd.get('filledCount', 0)})")
    d.route("dashboard/p_filter_coffee", "ROHAN", wait_for="Team")
    d.tap("Team", exact=True, pause=1.2)
    d.scroll_to("Offer the slot to Darshan N.", exact=True)
    c.shot("dashboard_backups")
    d.tap("Offer the slot to Darshan N.", exact=True, pause=1.0)
    c.see("Ask sent to Darshan N.", "Offer to backup #1 sends an Ask ('Ask sent to Darshan N. …')", timeout=10)
    st = demo_state(d, 2.5)
    asks = [a for a in delta(st, "applications").values() if a.get("roleId") == "r_fc_pd" and a.get("origin") == "INVITED"]
    c.note(f"r_fc_pd asks in state: {[(a.get('applicantId'), a.get('origin'), a.get('status', 'PENDING')) for a in asks]}")

    # Book him (he answers the ask at once at INSTANT), then release inside 72 h.
    d.route("inbox", "ROHAN", wait_for=INBOX_ANCHORS)
    try:
        open_inbox_chat(c, "Darshan N.", timeout=20)
    except StepFail:
        c.note("Darshan didn't take the ask (10% decline): booking another PD applicant instead")
        d.route("crewup/r_fc_pd", "ROHAN", wait_for="Let's talk")
        d.tap("Let's talk", exact=True, pause=1.5)
        d.tap("Open chat", exact=True, pause=1.5)
    d.tap("Send booking card", exact=True, timeout=25)
    d.wait("WHAT ", 15)
    d.tap("Send booking card", exact=True, last=True, pause=1.5)
    # The owner sees the BOOKED moment (it waits for a tap) over the chat's "Booked · first day …" line.
    c.see(["Booked · first day", "Booked · next", "BOOKED. Darshan N."], "the backup accepts the booking card (Booked)", timeout=30)
    dismiss_overlay(d)
    demo_control(c, "ROHAN", "+7 days", "Demo date is now")
    d.route("inbox", "ROHAN", wait_for=INBOX_ANCHORS)
    who = open_inbox_chat(c, "Darshan N.", timeout=20) if d.find("Darshan N.,", fresh=True) else None
    d.tap("More options", exact=True, pause=1.0)
    d.tap("Release", exact=True, pause=1.2)
    d.wait("Why?", 10)
    d.tap("Schedule changed", exact=True, pause=0.6)
    c.see("Less than 72 hours' notice. This will show in your Kept bookings.",
          "Release sheet warns: 'Less than 72 hours' notice. This will show in your Kept bookings.'", timeout=8)
    c.shot("release_sheet")
    d.tap("Release", exact=True, last=True, pause=1.5)
    c.see("Released", "release goes through ('Released … They've been told.')", timeout=10)
    rel = d.find("They've been told.", fresh=False)
    c.check(rel is not None and ".." not in rel.label, f"release toast copy has no doubled full stop: {rel.label if rel else None!r}")
    if d.has("Not now", exact=True):
        d.tap("Not now", exact=True)
    after = kept_bookings(c, "ROHAN")
    c.check(after == "Kept bookings 3 of 4", f"Kept bookings changes: {before!r} → {after!r}")
    c.shot("kept_bookings_after")


@step("7", "Dana: PAID_ONLY gate, $, remote post roles outside Atlanta; Soft Light card; +30 days → Mark delivered → wraps → 'Delivered on time?'")
def s7_dana(c):
    d = c.d
    d.launch("DANA", "discover", reset=True, speed="INSTANT", wait_for="INTERESTED", timeout=90)
    c.shot("dana_deck")
    cards = deck_cards(c)
    c.note(f"Dana's deck: {len(cards)} cards: " + "; ".join(f"{p} / {r} / {pay} / {pl}" for p, r, pay, pl in cards))
    c.check(len(cards) >= 8, f"deck has {len(cards)} cards (DEMO_IDS: 11)")
    c.check(not any("Unpaid" in pay or "Stipend" in pay for _, _, pay, _ in cards), "PAID_ONLY holds: no unpaid or stipend cards")
    c.check(all("$" in pay and "₹" not in pay for _, _, pay, _ in cards), "every card's pay is in $")
    remote_far = [(p, pl) for p, _, _, pl in cards if pl.startswith("REMOTE") and "Atlanta" not in pl]
    c.check(len(remote_far) >= 2, f"remote post-sound roles from outside Atlanta appear: {remote_far}")
    # Accept the Soft Light sound-design card.
    d.route("chat/m_dana_softlight", "DANA", wait_for=["Review offer", "Booking card v1 is here"])
    d.tap("Review offer", exact=True)
    d.wait("BOOKING CARD v1", 20)
    summary = d.find("You're booked as", fresh=False)
    c.check(summary is not None and "deliver by" in summary.label and "paid within 7 days of delivery" in summary.label,
            f"card reads 'deliver by …, paid within 7 days of delivery': {summary.label if summary else None!r}")
    texts = []
    for _ in range(4):
        texts += d.texts()
        d.scroll_down(0.6)
    joined = " | ".join(texts)
    c.check("Call window" not in joined and "CALL " not in joined and "Shoot days" not in joined,
            "no call times / shoot days on a post-role card")
    c.check("Picture lock" in joined and "Deliver by" in joined, "card shows Picture lock and Deliver by")
    d.scroll_up(0.9)
    c.shot("softlight_card")
    pledge = d.find("tell the production at least 7 days before")
    c.require(pledge is not None, "post-role pledge: 'If you can't deliver by the date, tell the production at least 7 days before.'")
    c.require(tick(c, "tell the production at least 7 days before"), "the 7-day delivery pledge ticks")
    d.tap("Accept & book", exact=True)
    n = d.wait_node(lambda n: n.desc.startswith("BOOKED"), 20, "the BOOKED overlay")
    c.check("DELIVER BY" in n.desc, f"BOOKED overlay shows the delivery date: {n.desc!r}")
    c.shot("softlight_booked")
    # +30 days → Mark delivered with a link → owner confirms → wraps → feedback asks "Delivered on time?".
    demo_control(c, "DANA", "+30 days", "Demo date is now")
    d.route("wrap/m/m_dana_softlight", "DANA", wait_for="WHAT'S OWED")
    d.scroll_to("Mark delivered", exact=True)
    d.tap("Mark delivered", exact=True, pause=1.2)
    d.wait("Link (required)", 10)
    d.fill(0, "https://drive.google.com/drive/folders/softlight-mix-v1")
    d.hide_keyboard()
    d.tap("Mark delivered", exact=True, last=True, pause=1.5)
    c.see("Marked done.", "Dana marks the delivery done with a link", timeout=10)
    c.see("Wrapped", "Elena confirms the delivery and the match wraps", timeout=30)
    c.shot("softlight_wrapped")
    d.scroll_to("Give private feedback", exact=True, up=True)
    d.tap("Give private feedback", exact=True)
    c.see("Delivered on time?", "feedback (Elena's side, shown to Dana) asks 'Delivered on time?'", timeout=10)
    c.shot("softlight_feedback")


@step("8", "Build my own in Indore: FIRST_PROJECT writer, no work link → 'early city', Rajwada Rain + India-wide remote, Beginners welcome, nothing blank")
def s8_indore(c):
    d = c.d
    d.launch("NEW", "onboarding", reset=True, speed="INSTANT", wait_for="What do you do?", timeout=90)
    d.fill(0, "Asha Verma")
    d.hide_keyboard()
    d.tap("First project", exact=True)          # FIXD: level radio cards
    d.fill(1, "Writer")
    d.hide_keyboard()
    d.tap("Writer", exact=True, pause=1.0)
    c.see("YOUR MAIN ROLE", "Writer is the main role (folded main-role row)", timeout=5)
    c.see_now("Writer", "the main-role row names Writer")
    d.tap("Next", exact=True, pause=1.5)
    d.wait("Where?", 10)
    d.tap("Pick your city", exact=True, pause=1.5)
    d.scroll_to("Indore", exact=True)
    c.see_now("EARLY CITIES", "city sheet lists Indore under EARLY CITIES")
    d.tap("Indore", exact=True, pause=1.5)
    c.see("Indore is an early city", "onboarding explains Indore is an early city", timeout=8)
    d.tap("City centre", exact=True)
    sw = d.find("Remote OK", exact=True)
    c.shot("onboarding_where")
    d.scroll_to("Hindi", exact=True)
    d.tap("Hindi", exact=True)
    d.tap("Next", exact=True, pause=1.5)
    d.wait("How you work", 10)
    d.scroll_to("One work link (optional)", exact=True)
    d.scroll_down(0.25)
    c.see("No reel yet? That's fine. Beginners-welcome roles don't need one.", "no-link copy for a first-project person", timeout=5)
    c.shot("onboarding_how")
    d.scroll_to("Finish", exact=True)
    d.tap("Finish", exact=True, pause=2.0)
    d.wait(["INTERESTED", "Nothing in range", "You've seen everything"], 60)
    head = d.find("early city", fresh=False)
    c.check(head is not None, f"Discover header says 'early city': {head.label if head else d.screen(300)!r}")
    c.shot("indore_discover")
    c.badges = {}
    cards, _ = deck_walk(c, max_cards=25)
    c.note(f"Indore deck: {len(cards)} cards: " + "; ".join(f"{p} / {r} / {pl}" for p, r, _, pl in cards))
    c.check(any(p == "Rajwada Rain" for p, *_ in cards), "Rajwada Rain is in the deck")
    rr = {k: v for k, v in c.badges.items() if k[0] == "Rajwada Rain"}
    c.check(rr and all("BEGINNERS WELCOME" in v for v in rr.values()), f"Rajwada Rain shows BEGINNERS WELCOME: {rr}")
    c.note("badges: " + "; ".join(f"{p} {r}: {sorted(v)}" for (p, r), v in c.badges.items() if v))
    remote = [p for p, _, _, pl in cards if pl.startswith("REMOTE") and "Indore" not in pl]
    c.check(len(remote) >= 1, f"India-wide remote roles appear: {remote}")
    c.check(all(r != "?" and pay not in ("?", "") and pl != "?" for _, r, pay, pl in cards), "no card has a blank role, pay or place line")


def notification_dump(d):
    return d.sh("dumpsys notification --noredact", timeout=60)


@step("9", "Notifications: app in background, a simulated booking card posts a system notification; tapping it opens the chat")
def s9_notify(c):
    d = c.d
    d.launch("MEERA", "inbox", reset=True, speed="NORMAL", wait_for="Yes, let's talk", timeout=90)
    d.tap("Yes, let's talk", exact=True, pause=1.2)
    if d.has("Later", exact=True):
        d.tap("Later", exact=True, pause=0.5)
    t0 = time.time()
    d.key("HOME", pause=1.0)
    fg = d.foreground()
    c.check(not fg.startswith(PKG), f"Calltime is in the background ({fg})")
    title = "Booking card · Tiffin Wars"
    seen = None
    while time.time() - t0 < 75:
        out = notification_dump(d)
        if title in out and PKG in out:
            seen = time.time() - t0
            break
        time.sleep(2.5)
    c.check(seen is not None, f"system notification '{title}' posted while in the background"
            + (f" ({seen:.0f}s after accepting the Rebook ask)" if seen else ""))
    line = d.wait_log(r"CalltimeNotify.*posted BOOKING_CARD .*→ (calltime://\S+)", 10, tags=("CalltimeNotify:I", "*:S"))
    route = re.search(r"→ (calltime://\S+)", line).group(1) if line else None
    c.check(route is not None and route.startswith("calltime://m/"), f"its tap target is the chat ({route})")
    d.sh("cmd statusbar expand-notifications")
    time.sleep(1.5)
    c.shot("notification_shade")
    d.sh("cmd statusbar collapse")
    if route:
        # The notification's PendingIntent = MainActivity + extra "route" (NEW_TASK | SINGLE_TOP): replay it.
        d.sh(f"am start -n {PKG}/.MainActivity -f 0x30000000 --es route {route}")
        c.see(["Booking card v1 is here", "Review offer"], "tapping it opens the chat with the booking card", timeout=30)
        c.see_now("Tiffin Wars", "chat is the Tiffin Wars conversation")
        c.shot("chat_from_notification")
        # Regression (fixed in this QA pass): after an activity recreation (rotation, font size…) notification taps
        # must still navigate, and the launch deep link must not be pushed a second time.
        d.force_stop()
        d.sh(f"am start -a android.intent.action.VIEW -d {route} {PKG}")
        d.wait(["Booking card v1 is here", "Review offer"], 60)
        try:
            d.sh("settings put system font_scale 1.15")
            time.sleep(4)
        finally:
            d.sh("settings put system font_scale 1.0")
        time.sleep(4)
        d.wait(["Booking card v1 is here", "Review offer"], 30)
        d.back()
        time.sleep(1.5)
        c.check(not d.has("Review offer", exact=True) and not d.has("Booking card v1 is here"),
                "after a recreation, Back leaves the deep-linked chat (the link isn't pushed twice)")
        d.sh(f"am start -n {PKG}/.MainActivity -f 0x30000000 --es route calltime://m/m_meera_scam")
        c.see("This is a common scam", "after a recreation, a notification tap still opens its chat", timeout=30)


@step("10", "Reset demo restores the initial state; kill + relaunch keeps state (JSON persisted)")
def s10_persist(c):
    d = c.d
    d.launch("MEERA", "inbox", reset=True, speed="INSTANT", wait_for=INBOX_ANCHORS, timeout=90)
    initial_inbox = [t for t in d.texts(fresh=False) if not re.match(r"^\d\d:\d\d$", t)]
    d.route("discover", "MEERA", wait_for="INTERESTED")
    first = _top_card(d.nodes())
    d.tap("Save", exact=True, pause=1.5)
    c.see("Saved · Undo", f"saved the top card ({first[0] if first else '?'})", timeout=8)
    demo_control(c, "MEERA", "+1 day", "Demo date is now")
    moved = next((t for t in d.texts(fresh=False) if t.startswith("Demo date is now")), "")
    # Kill and relaunch from the launcher (no QA extras): the session and the changes must come back.
    d.force_stop()
    d.sh(f"am start -n {PKG}/.MainActivity")
    d.wait(["INTERESTED", "Discover"], 60)
    c.check(d.foreground().startswith(PKG), "relaunched after a kill")
    d.route("saved", "MEERA", wait_for="Saved roles")
    c.check(first is not None and d.has(first[0], fresh=True), f"after kill + relaunch the saved role ({first[0] if first else '?'}) is still saved")
    c.shot("saved_after_relaunch")
    d.route("settings", "MEERA", wait_for="Settings")
    d.scroll_to("Demo date", exact=True, max_swipes=14)
    day = moved.replace("Demo date is now ", "").rstrip(".")
    c.check(day and d.has(day, fresh=True), f"after kill + relaunch the demo date is still {day!r}")
    ls = d.adb("shell", "run-as", PKG, "ls", "-l", "files/")
    c.check("demo_state_v2.json" in ls, "state is persisted as JSON (files/demo_state_v2.json)")
    # Reset demo from Settings.
    d.scroll_to("Reset demo", exact=True, max_swipes=14)
    d.tap("Reset demo", exact=True, pause=1.2)
    d.wait("Reset the demo?", 10)
    btns = d.find_all("Reset demo", exact=True)
    d.tap_xy(*btns[-1].center, pause=2.0)
    seen = d.wait(["Demo reset. Everything is back to day one.", "INTERESTED"], 30)
    c.check(True, "Reset demo restarts into Discover" + (" with the toast 'Demo reset. Everything is back to day one.'" if seen.startswith("Demo") else " (the 4 s toast was gone before the screen settled)"))
    d.route("saved", "MEERA", wait_for="Saved roles")
    c.see("Nothing saved yet", "after Reset demo the saved role is gone", timeout=10)
    d.route("settings", "MEERA", wait_for="Settings")
    d.scroll_to("Demo date", exact=True, max_swipes=14)
    c.check(not (day and d.has(day, fresh=True)), "after Reset demo the demo date is back to today")
    d.route("inbox", "MEERA", wait_for=INBOX_ANCHORS)
    now_inbox = [t for t in d.texts() if not re.match(r"^\d\d:\d\d$", t)]
    c.check(now_inbox == initial_inbox, "after Reset demo the Inbox matches the seeded day-one Inbox"
            + ("" if now_inbox == initial_inbox else f" (diff: {sorted(set(initial_inbox) ^ set(now_inbox))[:6]})"))
    c.shot("inbox_after_reset")


@step("11", "Accessibility: Interested/Pass as TalkBack custom actions + buttons; font scale 1.3 doesn't clip Discover card, Role detail, Chat")
def s11_a11y(c):
    d = c.d
    deck_src = open(os.path.join(SRC, "ui/components/SwipeDeck.kt")).read()
    c.check("CustomAccessibilityAction(labels.right)" in deck_src and "CustomAccessibilityAction(labels.left)" in deck_src
            and "customActions = actions" in deck_src,
            "source: the top deck card exposes TalkBack custom actions (right/left/up) — adb can't fire them, so this is a code check")
    c.check("R.string.ws1_action_interested" in deck_src and "R.string.ws1_action_pass" in deck_src,
            "source: the Gigs custom actions are labelled Interested / Pass (SwipeLabels.gigs)")
    d.launch("MEERA", "discover", reset=True, speed="INSTANT", wait_for="INTERESTED", timeout=90)
    for lbl in ["Interested", "Pass", "Save"]:
        n = d.find(lbl, exact=True, fresh=False)
        c.check(n is not None and (n.bounds[2] - n.bounds[0]) >= 126 and (n.bounds[3] - n.bounds[1]) >= 126,
                f"deck button {lbl!r} has a content description and a ≥48dp target ({n.bounds if n else None})")
    try:
        d.sh("settings put system font_scale 1.3")
        time.sleep(3)
        fs = d.sh("settings get system font_scale").strip()
        c.check(fs == "1.3", f"font_scale is {fs}")
        d.launch("MEERA", "discover", speed="INSTANT", wait_for="INTERESTED", timeout=90)
        for _ in range(4):
            time.sleep(1.5)
            ns = d.nodes()
            if _top_card(ns):
                break
        c.shot("font13_discover_card")
        card = _top_card(ns)
        bar = min((n.bounds[1] for n in ns if n.desc in ("Pass", "Interested", "Save")), default=d.h)
        poster = next((n for n in ns if n.label.startswith("Poster for ")), None)
        status = next((n for n in ns if re.match(r"^(Open|Closed|Filled) · ", n.label)), None)
        c.check(card is not None and all(x not in ("?", "") for x in card[1:4]), f"1.3×: card role/pay/place lines present {card[1:4] if card else None}")
        c.check(status is not None and status.bounds[3] <= bar, f"1.3×: card footer ({status.label if status else 'missing'}) sits above the action bar")
        texts = [n for n in ns if n.text and poster and n.bounds[1] >= poster.bounds[1] and n.bounds[3] <= bar and n.bounds[0] >= 0 and n.bounds[2] <= d.w]
        c.check(len(texts) >= 6, f"1.3×: {len(texts)} card text lines inside the card and the screen")
        d.route("role/r_ll_lead", "MEERA", wait_for="WHAT YOU GET")
        c.see_now("Last Local", "1.3×: role detail renders")
        c.shot("font13_role_detail")
        d.scroll_down(0.5)
        c.shot("font13_role_detail_2")
        d.route("chat/m_meera_scam", "MEERA", wait_for="Casting Hub Official")
        ns = d.nodes()
        comp = next((n for n in ns if n.label == "Write a message"), None)
        c.check(comp is not None and comp.bounds[3] <= d.h, "1.3×: chat composer is on screen")
        c.shot("font13_chat")
        if not d.has("This is a common scam", fresh=False):
            c.note("1.3×: the chat opens at the latest message, so the red banner above it starts under the pinned bar; scrolling up shows it")
            d.scroll_up(0.25)
        banner = d.find("Calltime never allows charging cast or crew. This is a common scam.")
        c.check(banner is not None, "1.3×: the red money banner text is complete (one node, not truncated)")
        c.shot("font13_chat_banner")
    finally:
        d.sh("settings put system font_scale 1.0")
        c.check(d.sh("settings get system font_scale").strip() == "1.0", "font_scale restored to 1.0")

# Per-step time budgets (seconds), about 2x a normal run on the shared dev machine. A step that runs over
# its budget is stopped and reported as FAIL ("over its N s budget"), so a stuck screen never hangs the run.
BUDGETS = {"1": 180, "2": 600, "3": 360, "4": 420, "5a": 360, "5b": 420, "5c": 900, "6": 480, "7": 480, "8": 420,
           "9": 360, "10": 360, "11": 420}


class StepTimeout(Exception):
    """Raised by the watchdog when a step runs over its budget (not a StepFail, so ctx.see() can't swallow it)."""


def _on_alarm(signum, frame):
    raise StepTimeout("step ran over its time budget")


def _on_term(signum, frame):
    raise KeyboardInterrupt


def fmt_secs(s):
    s = int(round(s))
    return f"{s // 60}m{s % 60:02d}s" if s >= 60 else f"{s}s"


def restore_font_scale(dev):
    try:
        dev.sh("settings put system font_scale 1.0", timeout=20)
    except Exception:
        pass


def preflight(serial, args):
    """Fail fast, with a clear message, before any step runs."""
    out = subprocess.run([emu.ADB, "devices"], capture_output=True, text=True, timeout=30).stdout
    devices = [l.split("\t")[0] for l in out.splitlines()[1:] if l.endswith("\tdevice")]
    if serial not in devices:
        sys.exit(f"device {serial!r} is not connected (adb devices: {devices or 'none'}). "
                 f"Pick one with --device <serial> or ANDROID_SERIAL; never start a second emulator on this machine.")
    boot = subprocess.run([emu.ADB, "-s", serial, "shell", "getprop", "sys.boot_completed"], capture_output=True,
                          text=True, timeout=30).stdout.strip()
    if boot != "1":
        sys.exit(f"device {serial} hasn't finished booting (sys.boot_completed={boot!r})")
    if not args.no_install and not os.path.exists(APK):
        sys.exit(f"APK not found: {APK}\nbuild it first: tools/lockrun.py -- ./gradlew assembleDebug --console=plain -q"
                 f"\n(or pass --apk <path>, or --no-install to test the build already on the device)")


def main():
    global APK
    faulthandler.register(signal.SIGUSR1, all_threads=True)  # kill -USR1 <pid> prints where a run is stuck
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--device", "--serial", dest="serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"),
                    help="adb serial of the emulator (default: $ANDROID_SERIAL, else emulator-5554)")
    ap.add_argument("--only", help="comma-separated step ids (e.g. 2,3 or 5)")
    ap.add_argument("--skip", help="comma-separated step ids to skip")
    ap.add_argument("--no-install", action="store_true", help="don't (re)install the APK")
    ap.add_argument("--apk", help=f"APK to install (default {os.path.relpath(APK, ROOT)})")
    ap.add_argument("--no-fast-dump", action="store_true",
                    help="read screens with plain `uiautomator dump` (slower) instead of the on-device qa/dumper")
    ap.add_argument("--wait-scale", type=float, default=1.0,
                    help="stretch every on-screen wait (waits already adapt to slow UI dumps; never below 20 s)")
    ap.add_argument("--budget-scale", type=float, default=1.0,
                    help="multiply every step's time budget (e.g. 1.5 on a slow machine; 0 disables the watchdog)")
    ap.add_argument("--list", action="store_true", help="list the step ids, titles and time budgets")
    args = ap.parse_args()
    if args.apk:
        APK = os.path.abspath(args.apk)
    if args.list:
        for sid, title, _ in STEPS:
            print(f"{sid:>3}  [{fmt_secs(BUDGETS.get(sid, 600)):>6}]  {title}")
        return 0

    def pick(spec):
        return {s.strip() for s in spec.split(",") if s.strip()} if spec else set()
    only, skip = pick(args.only), pick(args.skip)
    known = {sid for sid, _, _ in STEPS} | {re.match(r"\d+", sid).group(0) for sid, _, _ in STEPS}
    unknown = (only | skip) - known
    if unknown:
        sys.exit(f"unknown step id(s) {sorted(unknown)}; see --list")

    def selected(sid):
        base = re.match(r"\d+", sid).group(0)
        if only and sid not in only and base not in only:
            return False
        return sid not in skip and base not in skip

    preflight(args.serial, args)
    os.makedirs(OUT, exist_ok=True)
    dev = Device(args.serial)
    dev.wait_scale = args.wait_scale
    # SIGTERM (e.g. a CI timeout) unwinds like Ctrl-C, so every finally block runs and font_scale goes back to 1.0.
    signal.signal(signal.SIGTERM, _on_term)
    signal.signal(signal.SIGALRM, _on_alarm)
    state = {}
    results = []
    todo = [(sid, title, fn) for sid, title, fn in STEPS if selected(sid)]
    t_all = time.time()
    print(f"Calltime QA · device {args.serial} · {len(todo)} step(s) · APK "
          f"{'(already on device)' if args.no_install else os.path.relpath(APK, ROOT)} · {time.strftime('%H:%M:%S')}", flush=True)
    interrupted = False
    try:
        for k, (sid, title, fn) in enumerate(todo, 1):
            budget = BUDGETS.get(sid, 600) * args.budget_scale
            print(f"\n== Step {sid} [{k}/{len(todo)}, run time so far {fmt_secs(time.time() - t_all)}, "
                  f"budget {fmt_secs(budget) if budget else 'none'}]: {title}", flush=True)
            prefix = (sid.zfill(2) if sid.isdigit() else sid) + "_"
            for f in os.listdir(OUT):
                if f.startswith(prefix) and f.endswith(".png"):
                    os.remove(os.path.join(OUT, f))
            t0, lock0 = time.time(), LOCK_WAIT[0]
            c = Ctx(dev, sid, args)
            err = None
            for attempt in (1, 2):
                err = None
                with device_lock(args.serial):
                    c.t0 = time.time()   # check timestamps (+Ns) count from the moment the step has the device
                    stalls0 = dev.stalls
                    try:
                        ensure_installed(dev, state, args)
                        prepare(dev)
                        if not args.no_fast_dump and dev.fast.start() and not state.get("fast_said"):
                            state["fast_said"] = True
                            print("  [dump] fast on-device UI dumper running (qa/dumper/QaDumper.java)", flush=True)
                        dev.fast_misses = 0
                        dev.mark()
                        if budget:
                            signal.setitimer(signal.ITIMER_REAL, budget)
                        try:
                            fn(c)
                        finally:
                            signal.setitimer(signal.ITIMER_REAL, 0)
                    except StepTimeout:
                        err = f"stopped: the step ran over its {fmt_secs(budget)} budget (last screen: {dev.screen(600)})"
                    except StepFail as e:
                        err = str(e)
                    except Exception as e:  # a script bug: report it as a FAIL with the trace
                        err = f"{type(e).__name__}: {e}\n{traceback.format_exc()[-1200:]}"
                    finally:
                        if err:
                            print(f"    [FAIL] {err[:1500]}", flush=True)
                            try:
                                c.shot("failure")
                            except Exception:
                                pass
                        restore_font_scale(dev)
                        dev.fast.stop()   # before the lock is released: it holds UiAutomation
                failed = err is not None or not all(k_ for k_, _ in c.checks)
                foreign = dev.interference() if failed else None
                if failed and not foreign and dev.stalls > stalls0:
                    foreign = f"the emulator stalled ({dev.stalls - stalls0} adb command(s) timed out)"
                if not (failed and foreign and attempt == 1):
                    break
                print(f"    retrying step {sid}: {foreign}", flush=True)
                c = Ctx(dev, sid, args)
                c.note(f"attempt 1 failed while {foreign}; retried" if foreign.startswith("the emulator") else
                       f"attempt 1 failed while another agent used the emulator ({foreign}); retried")
            ok = err is None and all(k_ for k_, _ in c.checks)
            results.append(dict(sid=sid, title=title, ok=ok, secs=time.time() - t0, lock=LOCK_WAIT[0] - lock0,
                                checks=c.checks, notes=c.notes, error=err, shots=c.shots))
            print(f"== Step {sid}: {'PASS' if ok else 'FAIL'} ({check_count(results[-1])} checks, "
                  f"{fmt_secs(time.time() - t0 - results[-1]['lock'])} on the device)", flush=True)
            write_report(results, time.time() - t_all, args, quiet=True)  # a partial report survives an interrupted run
    except KeyboardInterrupt:
        interrupted = True
        print("\ninterrupted: restoring font_scale 1.0 and writing the partial report", flush=True)
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        dev.fast.stop()
        restore_font_scale(dev)

    total = time.time() - t_all
    write_report(results, total, args, interrupted=interrupted)
    print(f"\nTotal runtime: {fmt_secs(total)} ({fmt_secs(total - LOCK_WAIT[0])} on the device, "
          f"{fmt_secs(LOCK_WAIT[0])} waiting for other agents' emulator lock) for {len(results)} step(s); "
          f"{sum(r['ok'] for r in results)} PASS, {sum(not r['ok'] for r in results)} FAIL", flush=True)
    if interrupted:
        return 130
    return 0 if results and all(r["ok"] for r in results) else 1


def check_count(r):
    """'ok/total' checks; a step that stopped early counts the stop as one more failed check ('4/5, stopped')
    so a FAIL row never reads as 'all checks passed'."""
    n_ok, n = sum(1 for k, _ in r["checks"] if k), len(r["checks"])
    return f"{n_ok}/{n + 1}, stopped" if r.get("error") else f"{n_ok}/{n}"


def write_report(results, total, args, quiet=False, interrupted=False):
    lines = ["# Calltime QA report (demo mode, PRD §15.3)", "",
             f"Device `{args.serial}` · {time.strftime('%Y-%m-%d %H:%M')} · total {fmt_secs(total)} "
             f"({fmt_secs(total - LOCK_WAIT[0])} on the device + {fmt_secs(LOCK_WAIT[0])} waiting for the shared emulator lock)"
             + (" · **interrupted (partial)**" if interrupted else ""), "",
             "| Step | What | Result | Time | Checks |", "|---|---|---|---|---|"]
    # Time = time on the device (lock waits excluded).
    for r in results:
        lines.append(f"| {r['sid']} | {r['title']} | {'PASS' if r['ok'] else '**FAIL**'} | {fmt_secs(r['secs'] - r.get('lock', 0))} | {check_count(r)} |")
    lines += ["", "## Details", ""]
    for r in results:
        lines.append(f"### Step {r['sid']}: {r['title']} — {'PASS' if r['ok'] else 'FAIL'}")
        for k, t in r["checks"]:
            lines.append(f"- [{'x' if k else ' '}] {t}")
        for n in r["notes"]:
            lines.append(f"- note: {n}")
        if r["error"]:
            err = r["error"][:1200].replace("|", "/").replace("`", "'").replace("\n", " ")
            lines.append(f"- [ ] **stopped:** `{err}`")
        if r["shots"]:
            lines.append("- screenshots: " + ", ".join(os.path.relpath(s, QA_DIR) for s in r["shots"]))
        lines.append("")
    with open(os.path.join(OUT, "report.md"), "w") as f:
        f.write("\n".join(lines))
    if quiet:
        return
    print("\n" + "\n".join(lines[:6 + len(results)]))
    print(f"\nreport: {os.path.relpath(os.path.join(OUT, 'report.md'), ROOT)} · screenshots: {os.path.relpath(OUT, ROOT)}/")


if __name__ == "__main__":
    sys.exit(main())
