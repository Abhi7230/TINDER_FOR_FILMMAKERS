# Calltime QA report (v0.1, 2 Oct 2026)

## Summary

| Layer | Result |
|---|---|
| Unit tests (`./gradlew testDebugUnitTest`) | **337 / 337 pass** |
| Demo end to end, PRD §15.3 steps 1–11 (`qa/run_qa.py`) | **All 13 steps pass on both devices** (see the tables and notes below) |
| Live server end to end (`supabase/tests/live_e2e.py`, local Supabase) | **166 / 166 checks pass** |
| Live app, two real people (`qa/live_peer.py` + the app on a device) | Flows A, B and C pass (see [`LIVE_QA.md`](LIVE_QA.md)) |
| Design critic and filmmaker-persona critic | 68 issues filed (1 P0, 23 P1, 44 P2). The P0 and every P1 are fixed, as are most P2s |

## Devices

- **Android 16:** `emulator-5554`, Medium Phone API 36.1, 1080×2400, 420 dpi.
- **Android 14:** `emulator-5556`, Pixel 7 Pro API 34, 1440×3120, 560 dpi.

## Demo end to end (`qa/run_qa.py`)

Both devices ran a full pass on build `647c037`. The steps that failed in that pass were re-run on the final build, after fixes to the app and to the runner. Every step has now passed on each device.

| Step | What it checks | Android 16 | Android 14 |
|---|---|---|---|
| 1 | Cold install with no backend opens in demo mode, and Welcome shows the persona tiles | PASS 13/13 | PASS 13/13 |
| 2 | Meera: gated deck → apply to *Last Local* → IN TALKS → self-tape → booking card → BOOKED → Joined → day sheet (Got it, calendar, trusted-contact share) | PASS 37/37 ¹ | PASS 37/37 ² |
| 3 | Meera's safety: scam banner, masked number, Report → Safety Center RECEIVED; Dana can't see Meera's profile | PASS 12/12 | PASS 12/12 |
| 4 | Meera's wrap: jump past the shoot, wrap items, owner marks PAID, Meera confirms, feedback reveal, overdue FOOTAGE | PASS 15/15 | PASS 15/15 |
| 5a | Rohan: draft fails R02 with a fix → publish; share text matches the template; applicants arrive within 90 s | PASS 11/11 | PASS 11/11 |
| 5b | Rohan, Crew up: Let's talk / Backup / Not this time, closure message, Scout tabs (none on cast roles), booking → FILLED, pending applicants auto-closed | PASS 17/17 | PASS 17/17 |
| 5c | Rohan posts 3 on-set roles with "Pay for all roles" and Duplicate in ≤ 4 min, plus a post-only project (no dates; R20 lock date) | PASS 16/16 ² | PASS 16/16 ² |
| 6 | Withdraw → backup: a simulated withdrawal reopens the slot; "Offer to backup #1" sends an Ask; a release inside 72 h warns; Kept bookings changes | PASS 11/11 | PASS 11/11 ² |
| 7 | Dana: PAID_ONLY gate, $, remote post roles outside Atlanta, the *Soft Light* card; +30 days → Mark delivered → wraps → "Delivered on time?" | PASS 13/13 | PASS 13/13 |
| 8 | Build my own profile in Indore as a FIRST_PROJECT writer with no work link: "early city", *Rajwada Rain* first, India-wide remote roles, Beginners welcome | PASS 10/10 ² | PASS 10/10 ² |
| 9 | With the app in the background, a simulated booking card posts a system notification, and tapping it opens the chat | PASS 7/7 | PASS 7/7 |
| 10 | Reset demo restores the initial state; kill + relaunch keeps state (JSON persisted) | PASS 9/9 | PASS 9/9 |
| 11 | Accessibility: Interested/Pass as TalkBack custom actions and buttons; font scale 1.3 doesn't clip the Discover card, Role detail or Chat | PASS 13/13 | PASS 13/13 |

¹ Passed on the runner's automatic retry. The first attempt failed when Google Calendar opened for the first time on the emulator. Its "Checking info…" screen took the next tap, which was meant for "Share with trusted contact". That was the emulator, not the app.
² Passed on the re-run against the final build.

### What the full passes found, and the fixes

- **App:** the "every applicant gets an answer within 14 days" line sat below the Send button and was off-screen on shorter devices. It now sits above Send, so it's read before sending.
- **Demo data:** a backup offered a slot used to decline 10% of the time, which made step 6 flaky. Backups have already applied and are on the bench, so they now always take the slot. Cold Scout and Rebook asks still accept 60% of the time.
- **Runner:**
  - Close the keyboard before tapping the next field.
  - Find the role search by scrolling, not by index.
  - Scroll every wizard chip into view before tapping it. Earlier, on shorter screens, a tap on a field hidden behind the keyboard typed into the previous field.

## Run it

```bash
export JAVA_HOME="/path/to/Android Studio.app/Contents/jbr/Contents/Home"   # the fast on-device reader is built with javac
./gradlew assembleDebug
python3 qa/run_qa.py --device emulator-5554            # full run, ~16–30 min depending on the machine
python3 qa/run_qa.py --device emulator-5554 --only 2,5c # selected steps
python3 qa/run_qa.py --list
```

- **Exit codes:** 0 = all pass, 1 = any failure, 130 = interrupted. On Ctrl-C the runner restores font_scale 1.0.
- **Output:** screenshots and `report.md` go to `qa/out/`, which is gitignored. See [`README.md`](README.md) for every flag.

## Known limitations

1. **Demo cold start:**
   - A cold launch takes about 9 s to show the first card on the heavily loaded test machine (release build).
   - Splash holds with the slate mark and "Loading the demo…", so no blank screen appears.
   - Rebuilding the deterministic seed is the main cost; loading a saved snapshot was tried, and on the device it was slower.
2. **Live-mode gaps (L1 items still open):**
   - Closed-app notifications need a WorkManager poller or FCM. Today notifications arrive only while the app process is alive.
   - In live mode, older notifications that name a blocked person still need a server-side filter.
   - Profile busy dates don't yet include bookings (the server still drops clashing roles).
3. **Accessibility checks:** TalkBack is checked through the accessibility tree (custom actions and labels), not with a running screen reader. Reduce-motion paths exist but weren't exercised on a device.
4. **Not in this MVP (PRD §4.3):** payments and escrow, FCM push, team group threads, pencil holds, crew packages, Hindi and regional UI.
