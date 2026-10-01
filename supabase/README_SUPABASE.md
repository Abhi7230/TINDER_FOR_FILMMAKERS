# Calltime on Supabase (LIVE mode)

The app runs offline in **demo mode** when `SUPABASE_URL` is blank. Fill it in and the app runs in **LIVE mode**
against this backend (PRD §9). Both tiers of PRD §4.2 are wired: **L0** (the loop) and **L1** (day sheets, wrap,
feedback, reschedule/cancel, withdraw/release). Only F21 closed-app notifications are still off (see §5).

| Path | What |
|---|---|
| `migrations/20261002000000_init.sql` | Every table of PRD §9.2, text enums (values = Kotlin enum names), RLS on every table, column grants, triggers (verified flags, message safety flags, shoot-date sync, OPEN-listing re-check), `listing_rules_ok()`, the L0 RPCs, Realtime publication |
| `seed.sql` | Reference data only: 20 cities, 67 areas (ids in `CITY_IDS.md`). **Never seed people here.** |
| `CITY_IDS.md` | City / area ids, shared with the demo seed |
| `tests/live_e2e.py` | End-to-end proof: real users through GoTrue (password + email OTP via mailpit) and PostgREST, the whole L0 loop (C01–C113) and L1 (L01–L33): 146 PASS/FAIL checks |
| `templates/otp_code.html` | Sign-in email showing the 6-digit `{{ .Token }}` (wired in `config.toml`; paste into the cloud templates too) |
| `../app/src/test/java/com/tff/app/data/remote/LiveWireTest.kt` | Kotlin wire-format test (L0 + L1): app models → DTOs → live server → DTOs → models (skips when no local stack) |
| `../app/src/main/java/com/tff/app/data/remote/` | `SupabaseRepo` (the `Repo` for LIVE mode), `SupabaseProvider`, `Dtos`, `Mappers` |

## 1. Run it locally

Needs Docker and Node (for `npx`). From the repo root (the directory that contains `supabase/config.toml`):

```bash
# start (lean: no Studio etc.). Keep mailpit if you want to read sign-in codes in a browser.
npx supabase start -x studio,imgproxy,edge-runtime,logflare,vector,supavisor,postgres-meta
#   (add ,mailpit to -x if you don't need the local inbox)

npx supabase status            # URLs + the local anon / service_role keys
npx supabase db reset --local  # drop + re-apply migrations/ and seed.sql (wipes local data and users)
npx supabase stop              # stop containers (data is kept)
```

Local endpoints: API `http://127.0.0.1:54321`, DB `postgresql://postgres:postgres@127.0.0.1:54322/postgres`,
mailpit inbox `http://127.0.0.1:54324` (every sign-in code email lands there).

The local stack uses Supabase's public **demo** keys, which are fine to keep in docs and tests:

```
anon:          eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0
service_role:  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU
```

### Prove it

```bash
python3 supabase/tests/live_e2e.py          # PASS/FAIL per check, exit 1 on any failure; re-runnable
export JAVA_HOME="/Users/abhi/Applicationss/Android Studio.app/Contents/jbr/Contents/Home"
tools/lockrun.py -- ./gradlew testDebugUnitTest --tests com.tff.app.data.remote.LiveWireTest --console=plain -q
```

Both create throwaway users (`e2e-calltime-*@calltime.test`) and delete them afterwards. Never point them at production.

## 2. Point the app at it

`local.properties` (never committed):

```properties
# Android emulator → your machine's localhost
SUPABASE_URL=http://10.0.2.2:54321
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0
```

Rebuild (`tools/lockrun.py -- ./gradlew assembleDebug`). Cleartext HTTP is allowed only for `10.0.2.2`, `localhost`
and `127.0.0.1` (`res/xml/network_security_config.xml`); a physical phone needs a cloud project (HTTPS).
Leave `SUPABASE_URL` empty for demo mode; Settings → "Use demo mode" also forces demo on the next launch.

Sign-in is email OTP: the app calls `auth.signInWith(OTP)`, the 6-digit code arrives by email (locally: mailpit),
then `auth.verifyEmailOtp(EMAIL, email, code)`. The session is persisted and restored on the next app start.
Local rate limit: `[auth.rate_limit] email_sent` in `config.toml` (codes per hour).

## 3. Deploy to a free Supabase cloud project

1. Create a project at <https://supabase.com/dashboard> (free tier; region **Mumbai `ap-south-1`** for the launch city).
   Keep the database password somewhere safe.
2. Link and push the schema from the repo root:
   ```bash
   npx supabase login
   npx supabase link --project-ref <project-ref>        # the id in https://<project-ref>.supabase.co
   npx supabase db push                                 # applies supabase/migrations/*
   npx supabase db push --include-seed                  # or: psql "<connection string>" -f supabase/seed.sql
   ```
   For a non-Mumbai launch, flip `is_live` in `cities` (SQL editor) instead of editing the seed.
3. **Auth → Providers → Email**: enabled, "Confirm email" on. **Auth → Email Templates**: the *Magic Link* and
   *Confirm signup* templates must contain `{{ .Token }}` (the 6-digit code), e.g.
   `Your Calltime code is {{ .Token }}. It expires in 1 hour.` Without it users get a link the app can't use.
   Set OTP length 6 and expiry (Auth → Settings). The built-in mailer sends only a few emails per hour:
   configure **custom SMTP** (Auth → SMTP) before inviting anyone.
4. `local.properties`:
   ```properties
   SUPABASE_URL=https://<project-ref>.supabase.co
   SUPABASE_ANON_KEY=<Project Settings → API → anon / publishable key>
   ```
   The **service_role / secret key never goes into the app or git.** It is only for admin scripts and the tests.
5. Realtime needs nothing extra: the migration adds `messages`, `notifications`, `booking_cards`, `applications`
   and `call_times` to the `supabase_realtime` publication, and RLS decides who receives which row.

### Admin (service_role / SQL editor)

- Concierge ID check (F15): `update profiles set id_verified = true where id = '<uuid>';`
  The `profiles_protect` trigger resets `*_verified` for everyone except `service_role` / an admin SQL session.
- Moderation: `reports` (`status` RECEIVED → REVIEWING → ACTION_TAKEN / NO_ACTION). A profile with ≥3 open reports
  from different people is auto-hidden from decks and Scout until reviewed.

## 4. Security model (PRD §9.3)

- **RLS on every table.** State changes go through `SECURITY DEFINER` RPCs in `public` that check `auth.uid()`.
  Internal helpers live in schema `ct`, which PostgREST does not expose.
- **Profiles are not a directory.** A user reads only their own `profiles` row. Other people's cards come from
  `get_profile` (relationship required: match counterpart, applicant ↔ role owner, teammates, or the owner of an
  OPEN project), `get_applicants`, `scout_candidates` (crew only) and the owner mini-card in `discover_roles`.
  `profile_cards` (view) is not granted to `anon` / `authenticated`.
- **Private data never leaves the server.** `profile_private.min_day_rate` is readable only by its owner
  (`my_private_min_rate`); owners get a server-computed `rate_fit` (0..1). `content_wont_do` is blanked in every
  card shown to someone else. HEADSHOT links appear only to the owner of a cast role the actor applied to, or a
  match counterpart. Other people's screening answers stay server-side (`answer_duplicate` flag instead).
- **Column grants.** `projects.exact_address` is not selectable by `anon` / `authenticated` (read via RPC by the owner
  and BOOKED people); system columns (`status`, counters, `filled_count`, `applications_count`) are not writable.
  Clients must name columns (`select=id,title`), not `select=*`, on `projects`.
- **Messages.** Direct insert only for `kind = TEXT`, `sender_id = auth.uid()`, a participant of a live, unblocked
  match. The `messages_flags` trigger stores MessageSafety flags (Appendix C, ported to Postgres regex); MONEY is
  flagged only when the sender is the owner side. Contact masking is done on the device until BOOKED.
- **Listings.** `publish_project` runs `listing_rules_ok()` (BLOCK rules R01–R03, R05, R07–R14, R18–R20; R04
  relabels low paid day rates to Stipend). Editing an OPEN listing re-runs the rules at commit (deferred trigger).

## 5. RPC surface

Guests (`anon`): `get_cities`, `city_density`, `nearby_counts`, `discover_roles(p_city)`, `get_role`.

Signed in, **L0 (wired in `SupabaseRepo`)**

| Area | RPCs |
|---|---|
| Account | `get_my_profile`, `save_profile(p_profile, p_min_rate)`, `my_private_min_rate`, `touch_active`, `get_profile`, `get_reputation` / `reputation_stats`, `export_my_data`, `delete_my_account` |
| Gigs | `discover_roles`, `get_role`, `swipe_role`, `undo_swipe`, `saved_roles`, `apply_to_role` (open, cap, 25/day, not owner, not blocked, answer required), `likes_left_today` |
| Projects | `save_project_draft(p_project, p_roles)`, `get_project_draft`, `listing_rules_check`, `listing_rules_ok`, `publish_project`, `close_role`, `my_projects`, `joined_projects`, `get_project_full` |
| Crew up | `get_applicants` (rate_fit, never the rate), `decide_application` (TALK / BACKUP / DECLINE), `scout_candidates` (refuses cast; reverse G4/G5/G7), `ask_check`, `send_ask` (10/project/day; cast only from the Keep list), `asks_left_today`, `respond_ask` |
| Chat & booking | `get_inbox`, `get_match_view`, `mark_read`, messages via table insert, `send_audition_request` / `respond_audition` (mode locked to the listing, in-person 08:00–21:00, ≥1 other person present), `send_booking_card` (payer only, versions + diff, supersede) / `respond_booking_card` (BOOKED, filled_count, role FILLED closes PENDING applications, wrap items from deliverables), `share_contacts`, `shared_contacts` |
| Safety | reports via table insert, `block_user` (ends matches, closes applications), `unblock_user`, `my_blocked`, `keep_list` |

Signed in, **L1 (wired in `SupabaseRepo`, `features` on)**

| Area | RPCs |
|---|---|
| Day sheets | `get_shoot_days` (owner: all calls; collaborators: role + first name), `publish_shoot_day` (per-person call times, maps link, notifies; an update clears acks), `ack_shoot_day`, `nudge_call`, `set_attendance` (CONFIRMED / CANT_MAKE_IT by the person, ATTENDED / NO_SHOW by the owner after the day, DISPUTED on a no-show files a report) |
| Wrap | `mark_wrapped` (after the last day), `mark_wrap_item` (by whoever owes it; FOOTAGE / FINAL_FILM / DELIVERY need a link), `confirm_wrap_item` (by the other side; a confirmed DELIVERY wraps a post-role match), `project_wrap` |
| Feedback | `feedback_state`, `submit_feedback` (WRAPPED only, once, double-blind; "work again" feeds `keep_list`, which unlocks cast Rebook asks) |
| Changes & exits | `reschedule_project` (reschedule_count++, new booking-card versions for booked people, cancelled day sheets), `cancel_project`, `withdraw_match` (notice, slot reopens, returns backup #1), `release_match`, `offer_slot_to_backup` (match + booking card on the same terms) |
| Lazy jobs | `get_inbox()` runs `ct.housekeeping`: on-set auto-wrap 3 days after the last shoot day, DELIVERY auto-confirm after 7 days, application expiry after 14 days ("No answer from …"), one 72h "Send the booking card?" nudge, 7-day silent matches close, listings past their start close |

**Not yet:** F21 closed-app notifications (a WorkManager poll of `notifications` + scheduled local reminders) need
`androidx.work:work-runtime-ktx`, which isn't in the dependency set, so `features.closedAppNotifications = false`.
Realtime covers the app while it's open. FCM is v1.0. Wrap-overdue daily nudges and "cut late" booking versions in
Kept bookings are TODOs in `ct.housekeeping` / `ct.reputation_json`.
