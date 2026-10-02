# Calltime end-to-end QA (demo mode)

`qa/run_qa.py` runs every step of the PRD §15.3 QA script (steps 1–11) against the debug build on a
running emulator, checks real on-screen copy, takes a screenshot at every checkpoint and prints a
PASS/FAIL table. A full run takes about 25 minutes on the shared dev machine.

## Run it

```bash
export JAVA_HOME="/Users/abhi/Applicationss/Android Studio.app/Contents/jbr/Contents/Home"
tools/lockrun.py -- ./gradlew assembleDebug --console=plain -q     # build first (always through the lock)
export ANDROID_SERIAL=emulator-5554                                # pick the emulator
qa/run_qa.py                                                       # all steps
```

Never start an emulator or a second Gradle daemon for it; it uses whatever device `--serial` /
`ANDROID_SERIAL` names (default `emulator-5554`).

| Flag | What it does |
|---|---|
| `--only 2,3` | Run only these steps. A number also selects its sub-steps (`--only 5` runs 5a, 5b, 5c). |
| `--skip 5c` | Skip steps (same syntax). |
| `--no-install` | Use the APK already on the device (default: install `app/build/outputs/apk/debug/app-debug.apk`, and reinstall it if another build replaced it between steps). |
| `--serial emulator-5556` | Target another device. |
| `--list` | List the step ids and titles. |

Exit code 0 means every check passed.

## Output

- `qa/out/<step>_<NN>_<checkpoint>.png`: one screenshot per checkpoint (`<step>_NN_failure.png` when a step stops).
- `qa/out/report.md`: the PASS/FAIL table plus every check, note and error per step.
- `qa/out/` is gitignored. `qa/QA_REPORT.md` is the committed summary of the last full run.

## The steps

| Id | PRD §15.3 | What is driven and asserted |
|---|---|---|
| 1 | 1 | `pm clear` (a cold install with no data, no backend keys) → Welcome: CALLTIME, the four persona tiles with their hints, the demo note; a tile opens Discover with the DEMO badge. |
| 2 | 2 | Meera's whole deck (actor roles, Mumbai, none of her live projects); swipe Interested on Last Local and apply at 1× speed; IN TALKS within the allowance; chat terms summary; self-tape request with sides link and the five fixed safety lines; Send my tape; booking card; pledge required; BOOKED overlay; day sheet Got it; Add to calendar (ACTION_INSERT in logcat + calendar in front); Share with trusted contact (share sheet + its text preview); Projects → Joined. |
| 3 | 3 | Scam chat: red money banner, masked number, Report → "You reported this conversation" → Safety Center RECEIVED. As Dana: `person/u_meera` says "Profile not available." and Meera's name is on none of Dana's tabs. |
| 4 | 4 | Settings → Jump past next shoot; Cutting Chai wrap items; owner marks Pay; Meera taps Got it; private feedback submits and reveals ("Feedback is in"); Iron Gate footage overdue with a nudge. |
| 5a | 5 | Rohan's draft fails R02 only, with its fix; quick fix → publish; share text checked against the F17 template (on the sheet and in the Android share sheet); first applicants within 90 s at 1×. |
| 5b | 5 | Crew up Not this time / Backup / Let's talk (toasts, IN TALKS overlay) and the demo state (DECLINED with a closure message, BACKUP, MATCHED); no Scout tab on the lead-actor role, Scout on sound; booking card → accepted → role FILLED; the other pending applicants auto-closed. |
| 5c | 5 | Stopwatch: a 3-role on-set project with Pay for all roles and Duplicate ×2, published in ≤ 4 min; a post-only project (editor + composer, remote): no shoot-date fields, R20 asks for the picture-lock date, the fix opens the field, publish succeeds, post share lines. |
| 6 | 6 | Settings → Simulate a crew withdrawal: toast names backup #1, slot reopens; Offer the slot to backup #1 sends an Ask; book the backup, +7 days, Release: the "Less than 72 hours' notice" warning; Kept bookings 3/3 → 3/4. |
| 7 | 7 | Dana's deck: paid only, all in $, remote post-sound roles from Los Angeles; Soft Light card reads deliver by + paid within 7 days of delivery, no call times; accept; +30 days; Mark delivered with a link; owner confirms, match wraps; feedback asks "Delivered on time?". |
| 8 | 8 | Build my own: first-project writer, Indore (early city), no work link → Discover header "Indore · early city"; Rajwada Rain with BEGINNERS WELCOME; India-wide remote roles; no blank card lines. |
| 9 | 9 | Accept Meera's Rebook ask at 1×, press HOME; the booking card arrives as a system notification (`dumpsys notification`), its route comes from the `CalltimeNotify` log; replaying the notification's intent (what a tap sends) opens the chat with the card. |
| 10 | 10 | Save a card and move the clock; force-stop and relaunch from the launcher: both survive (state in `files/demo_state_v2.json`). Settings → Reset demo: saved role gone, date back to today, Inbox equal to the day-one Inbox. |
| 11 | 11 | Deck buttons with content descriptions and ≥ 48 dp targets; the custom TalkBack actions are checked in `SwipeDeck.kt` (adb can't fire them). `font_scale 1.3` → Discover card, Role detail and Chat screenshots with layout checks → `font_scale 1.0` (always restored, also on failure). |

## How it works

- Device control: `tools/emu.py` (adb path, lock naming) plus a richer layer in `run_qa.py` (exact/contains
  matching, waits, scrolling, text fields). Every wait reads the UI tree with `uiautomator dump`; screens are
  slow on this machine, so waits are generous.
- QA hook: `am start ... --es qa_persona P --es qa_route R [--ez qa_reset true] [--es qa_speed INSTANT]`.
  Each step starts with a demo reset, so steps run alone and in any order. INSTANT speed everywhere except
  where the PRD measures time (step 2's IN TALKS, step 5a's 90 s, step 9's background notification).
- OS UI is never tapped: the calendar and share intents are asserted from `logcat` and the resumed activity,
  notifications from `dumpsys notification`, demo state from `run-as com.tff.app cat files/demo_state_v2.json`.
- The emulator is shared: the runner holds `/tmp/tff-emulator-<serial>.lock` (the lock `tools/emu.py` takes)
  for one step at a time. If a step fails and logcat shows Calltime being started or force-stopped by
  someone else during it, the step is retried once and the note says so.
- Stuck run? `kill -USR1 <pid>` prints every thread's stack (faulthandler).
