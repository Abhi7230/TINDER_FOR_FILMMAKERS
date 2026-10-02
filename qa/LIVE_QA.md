# LIVE-mode QA: the real app, two real people

Run on **2 Oct 2026**: the debug app built in LIVE mode (`SUPABASE_URL=http://10.0.2.2:54321`) on the
**Pixel 7 Pro emulator (Android 14, 1440x3120, `emulator-5556`)**, against the local Supabase stack in Docker. Everything
on the device went through the UI. The debug QA hooks don't apply in LIVE mode. The other person was played by
**`qa/live_peer.py`** through the same REST / RPC API the app uses, each peer signed in with its own JWT.

## Result

| | |
|---|---|
| Flow A (app user = collaborator) | **PASS**: 15/15 steps, L1 wrap + feedback included |
| Flow B (app user = owner) | **PASS**: 11/11 steps, owner-side day sheet included |
| Flow C (session, report, block) | **PASS**: 7/7 steps |
| Bugs found and fixed | **5** (4 SQL in one new migration, 1 LIVE Discover path), each re-verified on the device |
| `supabase/tests/live_e2e.py` | **166/166** (was 163/163; +L34, L35, C114; L03 corrected) |
| Unit tests | **286/286**, incl. `LiveWireTest` 2/2 against the live stack (one expectation corrected, one owner assertion added) |

## How to rerun

```bash
# local stack running (npx supabase start …), migrations applied; app built with the LIVE local.properties
export ANDROID_SERIAL=emulator-5556
python3 qa/live_peer.py otp you@calltime.test          # sign-in code from mailpit while the app waits for it
python3 qa/live_peer.py app-user you@calltime.test     # after onboarding: remember the app user
python3 qa/live_peer.py owner-setup                    # owner peer + published project + a role that passes your gates
python3 qa/live_peer.py decide you@calltime.test TALK  # … say, send-booking, publish-shoot-day, mark-wrap, feedback
python3 qa/live_peer.py --as crew applicant-apply <roleId>   # flow B: a crew peer applies to your role
python3 qa/live_peer.py --as crew accept-booking
python3 qa/live_peer.py -h                             # every command
python3 qa/live_peer.py reset                          # delete the peer users (peer-*@calltime.test)
```

Peers are `peer-<tag>@calltime.test` (password sign-in, created with GoTrue admin). `--as app` opens a *second* session for
the app user (admin `generate_link` → `/verify`, no email sent). Use it only for setup shortcuts (it was used to set
up the re-test of fixes 2 and 3), never for a step the QA checks on the device. `mark-wrapped --force` moves the shoot
dates into the past with the service key so wrap can be tested today. State lives in `qa/.live_peer_state.json` (git-ignored).

## Flow A: the app user is a collaborator (Priya M., sound mixer, Andheri West, paid only, private min ₹3,000)

| # | Step | Result |
|---|---|---|
| A1 | Welcome → Get started → email → 18+ → Send code; 6-digit code read from mailpit → typed → verified | PASS (`a02`) |
| A2 | Onboarding 3 steps (name, level, main role; city/area/languages from `get_cities`; pay mode, private min rate) → Discover | PASS (`a06`) |
| A3 | Peer `owner-setup`: Anjali R. publishes "Last Local" with a ₹6,000/day sound role in the same area | PASS |
| A4 | Discover shows the card (pay, dates, area, language, "New on Calltime", script locked) | PASS after a cold start (`a07`); see fix 4 |
| A5 | Interested → apply sheet → note → Send; server has the PENDING application, rate_fit 1.0 | PASS (`a09`) |
| A6 | Peer `decide … TALK` while the app sits on Inbox: **IN TALKS** row + update appear in ~5 s with no restart (Realtime) | PASS (`a12`) |
| A7 | App → peer chat message arrives on the server | PASS |
| A8 | Peer → app: "call me on 98765 43210 or anjali.rao@gmail.com" arrives live and **masked** (stored masked, CONTACT + OFF_PLATFORM flags, safety banner) | PASS (`a14`) |
| A9 | Peer `send-booking`: "Booking card v1 is here" banner + card in chat, live | PASS (`a15`) |
| A10 | Review offer → tick the notice pledge → Accept & book → **BOOKED** stamp; server match BOOKED, card ACCEPTED | PASS (`a17`, `a18`) |
| A11 | Peer `publish-shoot-day --call 06:30`: chat banner turns into "next call FRI 16 OCT · 06:30", live | PASS (`a20`) |
| A12 | Open the day sheet (call, address, maps, parking, hospital, team) → **Got it**; server `ack_at` set | PASS (`a21`, `a22`); found fix 1 |
| A13 | L1 wrap: peer wraps; chat shows Wrapped + "Wrap checklist"; peer marks Pay + Footage (link) done; app taps Got it on Pay → CONFIRMED; peer marks Credit done → list updates live | PASS (`a24`, `a25`) |
| A14 | Private feedback (5 forced-choice questions) → Submit; peer submits too; server `revealed: true` | PASS (`a28`); found fix 2 |
| A15 | Projects → Joined shows the project; Me → Keep list shows Anjali (said "work again") | PASS (`a30`) |

## Flow B: the app user is an owner (Rahul I., director, Bandra)

| # | Step | Result |
|---|---|---|
| B1 | Signed in as a second account (OTP via mailpit), onboarding as Director | PASS |
| B2 | Post a project in the wizard: basics, when/where (Bandra, studio, Sat 17 – Sun 18 Oct, 2 meals, carpool), pay for all roles ₹3,500/day on wrap, one Gaffer role (production gear), safety "None of these", review all green | PASS (`b09`) |
| B3 | Publish → "You're crewing" share text; server project OPEN with the role | PASS (`b10`) |
| B4 | Peer `--as crew applicant-apply`: the applicant appears in **Crew up** live ("Gaffer · 1 new", 2/2 days, 1 km, rate in budget, the note) | PASS (`b12`) |
| B5 | Let's talk → IN TALKS overlay; server match TALKING | PASS (`b13`) |
| B6 | Chat both ways (app → peer read over REST, peer → app live) | PASS (`b14`) |
| B7 | Send booking card (pre-filled from the role: dates, pay, meals, carpool, payer) → "Offer out · v1" | PASS (`b15`) |
| B8 | Peer `accept-booking` → **BOOKED** overlay appears live on the owner's chat | PASS (`b16`) |
| B9 | Projects → Mine "Crewed · 1/1 filled"; dashboard role FILLED | PASS (`b17`) |
| B10 | Owner day sheet from the app: Shoot days → Add a day sheet → address → Publish → "Notify 1 person?" → Send; the crew peer sees it with their call | PASS (`b21`) |
| B11 | Peer `ack-day` → dashboard "1 of 1 got it" | PASS (`b22`); "Day 1 of 1" here confirmed fix 1 |

## Flow C: session, report, block

| # | Step | Result |
|---|---|---|
| C1 | Cold restart (force-stop + launch) right after onboarding: still signed in, deck loads | PASS |
| C2 | Me → Settings → Sign out → confirm → Welcome | PASS (`c02`) |
| C3 | Cold restart after sign-out: Welcome again (nothing restored) | PASS |
| C4 | Cold restart as the owner (and again after reinstalling the fixed APK): session, inbox, conversations, updates restored | PASS (`c03`) |
| C5 | Chat ⋮ → Report → Spam + details → Send report → "Thanks. We aim to review within 24 hours."; server row PROFILE / SPAM / RECEIVED | PASS (`c06`) |
| C6 | Chat ⋮ → Block → confirm: the conversation leaves the inbox; server match CLOSED; the peer's next message is refused by RLS (403) | PASS (`c08`) |
| C7 | Safety Center lists the report (Received) and the blocked person with Unblock | PASS (`c09`) |

## Bugs fixed

All four SQL fixes are in one new migration, **`supabase/migrations/20261002020000_qalive_fixes.sql`**, applied to the
local stack and recorded in `supabase_migrations.schema_migrations`. Each one was re-verified on the device.

| # | Bug (seen on device) | Fix |
|---|---|---|
| 1 | Day sheet header, owner dashboard and the DAY_SHEET notification said **"Day 1 of 1"** on a 2-day shoot with only the first sheet out: the server counted published sheets, not shoot dates. | New `ct.day_of()` (L21) used by `get_shoot_days` (L30) and `publish_shoot_day` (L54). Re-test: "FRI 16 OCT · DAY 1 OF 2", notification "Day 1 of 2" (`r02`). |
| 2 | After both sides sent private feedback, the first submitter's wrap screen kept saying "Answers unlock when … submits too": `submit_feedback` created no notification, so nothing reached the app over Realtime (demo notifies both). | `submit_feedback` (L98) notifies like `DemoEngine.submitFeedback`: "<name> left private feedback", then "Feedback is in · <title>" to both. Re-test: the owner's screen flipped to "Feedback is in" live (`r03`). |
| 3 | The owner's chat banner said "Publish a day sheet" after one was published, and never offered the day sheet: `get_match_view.my_call_time` only reads the caller's own call row, and owners have none (demo: the general call). | `get_match_view` (L127): for the owner, `my_call_time` = the next sheet's general call. Re-test: "Booked · next shoot FRI 16 OCT · 07:30 / Open the day sheet", updated live on publish (`r01`). |
| 4 | **Discover never fetched again after the first build in LIVE.** A role posted while the app was open (even after tab switches) only showed after the process restarted, and the empty state's "We'll ping you when new roles land" could not happen (closed-app notifications are off). | `DiscoverViewModel.refreshIfStale()` (`app/src/main/java/com/tff/app/ui/screens/discover/DiscoverViewModel.kt` L201, `builtAtMs` L158) + a RESUMED-lifecycle loop in `DiscoverScreen.kt` (L103–L110): LIVE only. An empty deck re-fetches on return and every 60 s while on screen; a deck with cards re-fetches on return after 10 min and is never rebuilt under the person's thumb. Demo keeps its frozen deck. Re-test: a new role landed ~45 s after the peer published it, no restart (`b23`). |
| 5 | **Chat messages never reached the phone in LIVE**: a TEXT message created no notification (demo: kind MESSAGE to the other side), so SystemNotifier had nothing to post while the app was in the background. | Trigger `messages_notify` → `ct.trg_message_notify()` (migration L161, L175). Re-test: app in the background, the peer messages → "Farah K. · New message about Tin Roof." in the shade; tapping it opens the chat (`r04`, `r05`). |

Tests updated with the fixes:
- `supabase/tests/live_e2e.py`: **L03** expected "Day 1 of 1" (the bug), now "Day 1 of 2". New **L34** (owner's match view: next
  sheet + general call, DAY_SHEET says "Day 1 of 2"), **L35** (feedback notifications to both sides), **C114** (a text
  message notifies the other side only). Plus a `quote_link()` helper.
- `app/src/test/java/com/tff/app/data/remote/LiveWireTest.kt` L360: `dayCount` 1 → 2, plus an owner `get_match_view` assertion (L364–L366).
- `supabase/README_SUPABASE.md`: migration table, check counts.

## Open issues (not fixed: not LIVE-specific, outside this scope, or by design)

1. **Wrap header counts only confirmed items** ("0/3 done" next to two DONE chips). WS6 copy, same in demo. Suggest "0/3 confirmed".
2. **Owner dashboard always says "Crewing · 1/1 filled"**, even when the project is Crewed (`ProjectDashboardScreen.kt:162` uses
   `ws5_pipe_crewing` for every status; the Projects list uses the right string per status). WS5, same in demo.
3. **The owner gets collaborator quick replies in a booked chat** ("Got the day sheet, thanks." / "Running 10 minutes late.",
   `ChatScreen.kt:645`), and the owner banner body reads "…for the address and **your** call time". WS6, same in demo.
4. **Inbox Updates still show a blocked person's name** in older notifications ("Vikram S. accepted booking card v1…").
   Same in demo (`DemoQueries` doesn't filter updates by blocks). The block dialog says "You won't see each other anywhere".
5. **Settings → Phone notifications** promises background notifications. In LIVE they arrive only while the process is
   alive (Realtime → SystemNotifier; fix 5 made chat messages part of that). F21 closed-app delivery is still off
   (`features.closedAppNotifications = false`, needs WorkManager), and no screen reads that flag to adjust the copy.
6. **Discover's density subtitle** ("N people · N open projects") refreshes only on its 5-minute poll, so it can lag a refreshed deck.
7. **Masked contact text stays masked after BOOKED.** This is by design since FIX1 (the server stores the masked copy, and the
   "Share contact details" card swaps details after booking). Listed because it differs from the README's old "masking on the device until BOOKED".
8. QA artefact, not a bug: `mark-wrapped --force` moves `shoot_dates` but not existing sheets, so a wrapped test project can
   still show a future "YOUR CALL" in Joined.

## Screenshots (`qa/live_screens/`, 900 px JPEG)

Flow A: `a02_otp` (code screen, auto-verified) · `a06_after_onb` (empty deck after onboarding) · `a07_discover_card` ·
`a09_apply_sheet` · `a12_inbox_in_talks` (Realtime) · `a14_chat_masked` · `a15_booking_card_in_chat` · `a17_pledge_ticked` ·
`a18_booked` · `a20_chat_daysheet` · `a21_daysheet` · `a22_daysheet_ack` · `a24_wrap_checklist` · `a25_wrap_pay_confirmed` ·
`a28_feedback_submitted` · `a30_projects_joined`

Flow B: `b09_wizard5_review` · `b10_published` · `b12_crewup_applicant` (Realtime) · `b13_after_talk` · `b14_owner_chat` ·
`b15_booking_editor` · `b16_owner_booked` (Realtime) · `b19_dash_shootdays` · `b21_daysheet_published` · `b22_dash_acked` ·
`b23_discover_live_refresh` (fix 4)

Flow C: `c02_after_signout` · `c03_coldstart_inbox` · `c06_report_sent` · `c08_after_block` · `c09_safety_center`

Re-tests: `r01_owner_banner_fixed` (fix 3) · `r02_daysheet_day1of2` (fix 1) · `r03_feedback_live` (fix 2) ·
`r04_bg_message_notification` · `r05_notification_opens_chat` (fix 5)

## Left running

The local Supabase stack is still up. The QA accounts are still in it (`qa-app-a@`, `qa-app-b@`, `peer-owner@`, `peer-owner2@`,
`peer-owner3@`, `peer-crew@`, `peer-crew2@calltime.test`). Remove the peers with `python3 qa/live_peer.py reset` (add `--app` to also delete the last app user).
