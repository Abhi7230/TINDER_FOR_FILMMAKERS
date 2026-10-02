# Calltime QA report: PRD §15.3 demo script (draft, QA2)

Build tested: `main` at `6b6dedc` (before the five fixer branches were merged), debug APK, emulator-5554
(Android 16, 1080x2400). Runner: `qa/run_qa.py` (this branch). Date: 2026-10-02.

**Status: draft.** Every step 1–11 passed at least once on this build, but no single run had every step
passing. The coordinator stopped the last full run (run 3) at step 5c to re-run on the merged build. Steps
5c–11 haven't been run with the final runner code yet: the fast-read fixes listed below came in after
their last pass. The next full run on merged `main` is the one that counts.

**App bugs found: none.** Every FAIL below came from the runner or the environment: copy below the fold,
waits too short for a starved emulator, adb hangs, or reads that ran ahead of the screen. The fixes are in
`qa/run_qa.py`. A few observations for product (not bugs) are listed at the end.

## Results

### Run 1: full run, slow reads (`uiautomator dump`), 63.9 min total (≈37 min on the device + ≈27 min waiting for other agents' emulator lock)

Check counts use the new format, where a stop counts as one failed check.

| Step | What | Result | Device time | Checks | Why it failed (all runner or environment) |
|---|---|---|---|---|---|
| 1 | Cold install → Welcome, persona tiles, DEMO | PASS | 2m51s | 13/13 | (attempt 1 hit a 60 s `uiautomator dump` hang; retried automatically) |
| 2 | Meera: gated deck, apply, IN TALKS, self-tape, booking card, BOOKED, day sheet, Joined | PASS | 4m43s | 36/36 | |
| 3 | Scam banner, masked number, Report → RECEIVED; Dana can't see Meera | FAIL | 1m00s | 4/5, stopped | Report sheet took >10 s to open on the loaded emulator (fixed wait) |
| 4 | Wrap: Pay marked, confirmed, feedback reveal, Iron Gate footage overdue | PASS | 4m06s | 15/15 | |
| 5a | Draft fails R02 only → fix → publish; F17 share text; applicants ≤ 90 s | PASS | 1m54s | 11/11 | |
| 5b | Crew up actions, closure, Scout tabs, booking → FILLED, others auto-closed | FAIL | 4m08s | 12/13, stopped | `uiautomator dump` hung 60 s → uncaught TimeoutExpired |
| 5c | 3 on-set roles ≤ 4 min; post-only project, R20 | FAIL | 2m33s | 0/1, stopped | `dumpsys package` hung 120 s during the install check |
| 6 | Withdrawal → backup, Ask, release < 72 h, Kept bookings 3/3 → 3/4 | FAIL | 3m37s | 0/1, stopped | waited for `TRACK RECORD`, now below the fold on Me (fixed anchor) |
| 7 | Dana: paid only, $, remote LA roles; Soft Light; delivered → wrapped | PASS | 3m48s | 12/12 | |
| 8 | Build my own, Indore early city, Rajwada Rain, remote India roles | PASS | 2m36s | 9/9 | |
| 9 | Background booking-card notification → tap opens the chat | PASS | 1m33s | 7/7 | |
| 10 | Kill + relaunch keeps state; Reset demo restores day one | PASS | 2m55s | 9/9 | |
| 11 | TalkBack actions, 48 dp targets, font 1.3× layouts | PASS | 1m33s | 13/13 | |

### Re-run of the run-1 failures (`--only 3,5b,5c,6`), slow reads: all PASS

| Step | Result | Device time | Checks |
|---|---|---|---|
| 3 | PASS | 2m37s | 12/12 |
| 5b | PASS | 1m52s | 17/17 |
| 5c | PASS | 8m19s | 16/16 (stopwatch: 73 s of app time; 231 s wall clock including 158 s of the runner's own dumps) |
| 6 | PASS | 3m12s | 11/11 |

### Run 2: full run, first version of the fast on-device reader, 31.3 min total (18m46s on the device)

6 PASS, 7 FAIL. All 7 failures came from reads running ahead of the screen: taps landing mid-fling, the
first frame of a screen, and the node cache. These are fixed:

| Step | Result | Checks | Diagnosis → fix |
|---|---|---|---|
| 1, 3, 4, 5b, 8, 9 | PASS | all | |
| 2 | FAIL | 35/36 | "offer note" read before the line was laid out → re-read before reporting text as missing |
| 5a | FAIL | 3/5, stopped | "Set 2 meals a day" tapped during a scroll fling → tap only when a second read finds the target in the same place; settle after swipes |
| 5c | FAIL | 1/2, stopped | "Add role" tapped mid-fling → same fix |
| 6 | FAIL | 10/11 | owner's BOOKED moment waits for a tap and covers "Booked · first day" → accept the overlay as the booked state, then dismiss it |
| 7 | FAIL | 8/9, stopped | pledge tap lost → `tick()` taps and confirms `checked="true"` in the UI tree |
| 10 | FAIL | 1/2, stopped | "Demo date is now …" snackbar never seen. Most likely the long-lived UiAutomation answering from a stale node cache → the reader now clears the cache before every read (unverified on step 10) |
| 11 | FAIL | 12/13 | money-banner text not found after a scroll → settle and cache fixes |

### Run 3: full run, final fast reader (stopped at step 5c by the coordinator)

| Step | Result | Device time | Checks |
|---|---|---|---|
| 1 | PASS | 38s | 13/13 |
| 2 | PASS | 1m50s | 37/37 |
| 3 | PASS | 2m08s | 12/12 |
| 4 | PASS | 1m38s | 15/15 |
| 5a | PASS | 1m24s | 11/11 |
| 5b | FAIL | 3m19s | 16/17. The emulator stalled for 45 s while the "is on your backup list" toast (4 s) was showing. The demo state confirms the backup was made. A failed step with adb stalls is now retried once automatically. |
| 5c–11 | not run | | stopped by the coordinator |

Total: 41m49s wall clock, but only 13m07s of it on the device. The other 28m42s went to waiting for other
agents' emulator lock, including one 706 s wait.

### Best evidence per step on this build

| Step | Passed in | Latest result |
|---|---|---|
| 1 | runs 1, 2, 3 | PASS |
| 2 | runs 1, 3 | PASS (run 3, final runner) |
| 3 | re-run, runs 2, 3 | PASS (run 3) |
| 4 | runs 1, 2, 3 | PASS (run 3) |
| 5a | runs 1, 3 | PASS (run 3) |
| 5b | re-run, run 2 | FAIL in run 3 (emulator stall, now auto-retried) |
| 5c | re-run | not yet run with the final runner |
| 6 | re-run | not yet run with the final runner |
| 7 | run 1 | not yet run with the final runner |
| 8 | runs 1, 2 | not yet run with the final runner |
| 9 | runs 1, 2 | not yet run with the final runner |
| 10 | run 1 | not yet run with the final runner |
| 11 | run 1 (13/13) | not yet run with the final runner |

### About the earlier "step 11 FAIL with 11/11 checks"

That came from the previous runner's report format. When a step stopped on an error (here a wait that timed
out), the error wasn't counted as a check, so a FAIL row could read "11/11". Step 11 then passed 13/13 in
run 1. The report now counts a stop as one more failed check (`11/12, stopped`).

## Runner changes in this pass (`qa/run_qa.py`, `qa/dumper/QaDumper.java`, `qa/README.md`)

- **Fast screen reads.** `qa/dumper/QaDumper.java` is a 90-line UI Automator 1.0 test that stays running
  on the device for one step and answers a read in 0.1–0.3 s. A fresh `uiautomator dump` takes 3–10 s on
  this machine. It brings device time for a full run from about 40 min to about 15–19 min.
  - The runner builds it on first use with `$JAVA_HOME`'s javac and the SDK's `d8` and `uiautomator.jar`.
  - It starts under the device lock and always stops before the lock is released. It holds UiAutomation,
    which would break other agents' `uiautomator dump`, and it exits by itself after 90 s idle.
  - It clears the accessibility cache before every read.
  - If it can't build or start, or stops answering for 30 s, the step falls back to `uiautomator dump`.
    `--no-fast-dump` forces that path.
  - Android 14+ note: the legacy runner needs `/system/framework/android.test.base.jar` passed as a second
    jar. That missing jar is why the earlier attempt at this reader was abandoned.
- **Reads that can't run ahead of the screen.** Taps fire only when two reads agree on where the target is.
  Swipes wait for the fling to finish. A missing text is re-read once before it counts as missing. Pledges
  are confirmed through `checked="true"`. Overlays that wait for a tap are dismissed.
- **Timeouts.** Every step has a budget of about 2× a normal run (see `--list`); a step that runs over is
  stopped as FAIL with the last screen. Waits stretch with the measured read time and are never under 20 s.
  adb queries that hang are retried twice, but taps and launches are never re-sent. A hung read is bounded
  at 120 s.
- **Stale-read fix on the slow path.** `uiautomator dump` failures used to return the previous screen,
  because the old file was never removed before the next dump. The file is now deleted first.
- **Retries.** A failed step is retried once if another agent started or stopped Calltime during it
  (existing behaviour), or now also if adb stalled during it.
- **CLI.** New flags: `--device` (alias of `--serial`; defaults to `$ANDROID_SERIAL`, then emulator-5554),
  `--apk`, `--no-fast-dump`, `--wait-scale`, `--budget-scale`. Unknown step ids are rejected.
  - Preflight checks that the device is connected and booted and that the APK exists.
  - Progress lines read `[k/13, run time so far …, budget …]`.
  - The total runtime is printed split into device time and lock waits.
  - `report.md` is rewritten after every step. Ctrl-C or SIGTERM restores font_scale 1.0, stops the reader
    and writes a partial report (exit 130).
- **Stopwatch (5c).** App time is wall clock minus the runner's own reads, and both numbers are printed.
  On this machine the 4-minute check failed on dump overhead alone (the dumps took 158 s of a 231 s run).
- **Step 6** waits for `Edit profile` (TRACK RECORD is below the fold on Me) and then scrolls.
- The old abandoned `qa/dumper` jar approach was replaced by the working one above.

## Product observations (not failures, for the PRD owners)

1. At font scale 1.3, the scam chat opens at the latest message, so the red money banner starts under the
   pinned "Waiting for the booking card" bar. The user sees only its Report / Block buttons until they scroll
   up. The banner is complete, but on open it isn't what a user reads first. (`ui/screens/chat`)
2. The owner-side BOOKED moment in chat (after the other person accepts) waits for a tap ("Tap anywhere to
   continue") and has no auto-dismiss. This looks deliberate, but it covers the chat's "Booked · first day …"
   line. (`ui/components/Overlays.kt` `BookedOverlay`, `autoDismissMillis`)
3. Not yet explained: in run 2, Settings → "+1 day" moved the date but the "Demo date is now …" snackbar was
   never read in 20 s. This is probably the reader's cache (fixed), but re-check step 10 on the next run.
   (`ui/screens/settings/SettingsScreen.kt` → `nav.toast`)

## How to run

```bash
cd <worktree>
export JAVA_HOME="/Users/abhi/Applicationss/Android Studio.app/Contents/jbr/Contents/Home"
tools/lockrun.py -- ./gradlew assembleDebug --console=plain -q   # always through the lock
qa/run_qa.py --device emulator-5554                              # or ANDROID_SERIAL=…; --only 5c,6 to re-run
```

- Output: the PASS/FAIL table on stdout, plus `qa/out/report.md` (every check, note and screenshot path)
  and `qa/out/<step>_<NN>_<name>.png`.
- Exit codes: 0 = all pass, 1 = any FAIL, 130 = interrupted.
- Timing on this shared machine: about 15–19 min of device time with the fast reader (about 40 min
  without), plus whatever other agents hold the emulator lock. Runs 1–3 waited 9–29 min for the lock.
  On a quiet machine a full run fits in about 20 min.
- Never start a second emulator. The runner restores `font_scale 1.0` itself. If it is ever killed with
  `-9`, run `adb -s <serial> shell settings put system font_scale 1.0` and `touch /data/local/tmp/qa_stop`.
- The demo copy changed in the five fixer merges after `6b6dedc`. Their own `qa/run_qa.py` expectation
  updates are on `main`, so merge this branch into `main` before the final run.
