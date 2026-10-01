# Calltime: Product Requirements (MVP v0.1)

| | |
|---|---|
| **Status** | Build-ready spec for MVP. Date: 2 Oct 2026 |
| **Platform** | Native Android: Kotlin, Jetpack Compose, Material 3. minSdk 26, target 36 (matches `app/build.gradle.kts`) |
| **Backend** | Supabase (Postgres, Auth, Realtime). **No Storage in the MVP** (no profile photos or media uploads; links only). **Offline demo mode is mandatory**: when `SUPABASE_URL` is blank in `local.properties`, the APK runs fully on seeded data with simulated counterparts |
| **Session deliverable** | DEMO mode end to end, passing the §15.3 QA script. LIVE (Supabase) mode is built in two tiers (§4.2): **L0** (the core loop) in this session, **L1** (shoot, wrap, reputation, closed-app notifications) before any public launch |
| **Package** | `com.tff.app` (keep). Display name: **Calltime** |
| **Evidence** | `research/REDDIT_RESEARCH.md` (consolidated) and `research/raw/*.md`. Pain tags below (`P1`...`P12`) refer to the ranked pains in REDDIT_RESEARCH §2 |
| **Scope rule** | Anything not listed in §4 "MVP features" is **not** in the MVP. When in doubt, cut it and add it to §4.3 "Not in MVP" |

---

## 1. Name, one-liner, positioning

### Name
**Calltime.** A call time is the time you're due on set, and you only get one once you're booked. That is the product promise: we don't stop at a match, we get you to set.
- **Public tagline:** "Swipe real projects. Get a call time."
- **Internal pitch only:** "Tinder for filmmakers". **Never** use it in the store listing, onboarding or marketing. Research: "Tinder" framing reads as dating in an industry where predators pose as directors ([15sa7ji](https://reddit.com/r/Filmmakers/comments/15sa7ji/), [165ao2t](https://reddit.com/r/Filmmakers/comments/165ao2t/)).
- **Fallback names if trademark or Play Store checks fail:** *Pencil* (to "pencil" someone in is film slang for a hold), *Onset*, *Take One*. Run the trademark check before the store listing goes live.

### One-liner
Calltime is a free Android app where indie filmmakers swipe real projects that show pay, dates and perks up front. Every yes ends in a two-tap booking card, every shoot gets a WhatsApp-friendly day sheet, and every wrap tracks the footage, credit and pay people were promised.

### Positioning
**For** no- and low-budget filmmakers, crew and actors who aren't plugged into a hiring network (newcomers, weekend filmmakers, people in non-hub cities), starting in **Mumbai**, with remote post roles (editor, composer, colorist, sound design) open India-wide from day one: Calltime turns a crew call into a booked shoot that keeps its promises. **Unlike** Facebook and WhatsApp groups, it is structured, local and verified, and every applicant gets an answer. **Unlike** StaffMeUp, Mandy or Backstage, it is free for talent forever. **Unlike** earlier swipe apps (BeScene, Cinematcher, Daisie), it is built around projects rather than people, and it follows through after the match.

| Alternative | What they get wrong (evidence) | Calltime's answer |
|---|---|---|
| Facebook / WhatsApp groups | Buried posts, approvals over a week, scams, dead small-city groups, no structure, newcomers can't see history (P9, P11) | Structured cards in a local deck, honest density counts, one-tap share **back into** WhatsApp and FB |
| StaffMeUp / Mandy / Backstage / Stage 32 | Pay to apply, 200+ applicants and ~15 qualified, opaque ranking, scams, dark patterns (P10, P12) | Free for talent; hard gates, application caps and a daily like limit; ranked shortlists with reasons; every applicant gets a status |
| BeScene / Liaise / Credits / Filmclusive | Empty rooms, paid boosts, "vibe coded", Swiss-army-knife scope (graveyard, REDDIT_RESEARCH §4.3) | One job done well: crew up and get to set. No boosts. Launch one city at a time |
| StudioBinder / Google Sheets call sheets | Delivery fails: wrong channel, address you can't tap, no change alerts (P11) | Day sheet that works by link, text and calendar, with "Got it" acknowledgements and change broadcasts |
| DIY deal memos / chat agreements | "Agreed through chat", bait-and-switch, footage never delivered (P1, P4) | Booking card accepted by whoever controls the money, diff on every change, wrap checklist |

---

## 2. Personas and jobs-to-be-done

| Persona | Who (grounded in research) | Top jobs-to-be-done | Pains |
|---|---|---|---|
| **Rohan, weekend writer-director** | 24, software engineer in Jayanagar, Bengaluru. Two phone-shot shorts. Kannada, English, Hindi. Free Sat/Sun only. ₹15k budget. No crew. ([1uhya0h](https://reddit.com/r/indianFilmmakers/comments/1uhya0h/), [1pw8crx](https://reddit.com/r/indianFilmmakers/comments/1pw8crx/), [1qfck65](https://reddit.com/r/indianFilmmakers/comments/1qfck65/)) | 1. Turn a locked script into a 2-day weekend shoot with 5 reliable people. 2. Look legitimate, not like a scammer, without a track record. 3. Find the scarce roles (sound, an editor who will sit with him). 4. Not lose half the crew in the last week | P3, P6, P9, P10 |
| **Meera, reel-building actor** | 23, Versova, Mumbai. Four years of theatre, wants screen footage. Hindi, English, Marathi. Free evenings and weekends. Has been burned by footage that never came and by a "registration fee" casting. ([1infcka](https://www.reddit.com/r/acting/comments/1infcka/), [1tumcb1](https://reddit.com/r/acting/comments/1tumcb1/), [1w8nk3i](https://reddit.com/r/indianFilmmakers/comments/1w8nk3i/)) | 1. Find real projects that will deliver her scenes by a date. 2. Avoid fee scams and creeps, and audition only in safe settings. 3. Know quickly whether she got the role. 4. Build a credible, verified track record | P1, P5, P8, P3 |
| **Kabir, gaffer stepping up to DP** | 29, Andheri, Mumbai. 30+ ad shoots as a gaffer. Owns a small LED kit. Wants a first DP credit and would bring his best boy. ([1vabt10](https://www.reddit.com/r/Filmmakers/comments/1vabt10/), [1b5xk6q](https://reddit.com/r/cinematography/comments/1b5xk6q/)) | 1. Find shorts that are open to a step-up DP. 2. Get gear terms in writing (kit fee or at least insurance). 3. Have the credit worded correctly ("Director of Photography", not "camera") | P2, P7, P1 |
| **Dana, location sound mixer (scarce pro)** | 36, Atlanta. Nine years, union-eligible, under-employed in the 2025-26 slowdown. Paid work only: day rate plus kit fee plus boom op. ([dvww8y](https://www.reddit.com/r/LocationSound/comments/dvww8y/), [1q4rzfo](https://www.reddit.com/r/LocationSound/comments/1q4rzfo/), [1ptjvi6](https://reddit.com/r/AtlFilmmakers/comments/1ptjvi6/)) | 1. See rate, kit fee, boom op and hours *before* any DM. 2. Never see "exposure" posts. 3. Get paid on wrap, and see who pays late | P2, P4, P7 |
| **Priya, thesis producer and 1st AD** | 26, final-year MA student, London. Crewing a thesis short with a £1.2k budget. Drowning in actor submissions, short of a gaffer and sound. ([1rdzhzw](https://reddit.com/r/Filmmakers/comments/1rdzhzw/), [1kwxpw2](https://reddit.com/r/acting/comments/1kwxpw2/), [1bdo28y](https://reddit.com/r/Filmmakers/comments/1bdo28y/)) | 1. Get a ranked, capped shortlist instead of 300 DMs. 2. Lock people in with written terms. 3. Keep backups for every key role. 4. Send one day sheet that reaches everyone | P10, P3, P11 |

Secondary:
- A **remote editor or composer in a small town** (Kochi or Dehradun; Arkansas in the US) who can't find local work ([15sa7ji](https://reddit.com/r/Filmmakers/comments/15sa7ji/), [1vhr8kq](https://reddit.com/r/indianFilmmakers/comments/1vhr8kq/)). Served by remote post roles with their own delivery-based loop (§3.4), open India-wide from day one.
- A **director or self-taught student in an "early city"** (Indore, Dehradun, Nagpur) with no portfolio and a dead local Facebook group ([1uhya0h](https://reddit.com/r/indianFilmmakers/comments/1uhya0h/), [74567o](https://reddit.com/r/Filmmakers/comments/74567o/)). Served by posting from any listed city, honest early-city counts, share-out into local WhatsApp groups (F17), remote roles, and ranking that doesn't punish a missing reel on beginner projects (§7.4).

---

## 3. Core loop

### 3.1 Two decks, one rule: projects first
Calltime has **two swipe decks** on the Discover tab. They are asymmetric because the market is asymmetric (P10).

| Deck | Who sees it | Card = | Purpose |
|---|---|---|---|
| **Gigs** (default for everyone) | Anyone, including guests (read-only) | **One role on one project** (Role Card) | "Projects for me" |
| **Crew up** | Users who own at least one OPEN role | **One person for one of my roles** (Person Card), in two sub-decks: **Applicants** (people who said yes to this role) and **Scout** (available people nearby who fit but haven't applied; **crew roles only, never cast**) | "People for my project" |

There is no symmetric "people swiping on people" mode. Crew never swipe on faces. Actors' headshots are visible **only** in the Applicants sub-deck of a role they applied to.

**Actors can't be scouted.** Cast roles are Applicants-only: owners can't browse actors or send them cold Ask cards. Actors are the most oversupplied role, so hirers filter rather than scout (REDDIT_RESEARCH §8 principle 7), and an unsolicited approach from a self-styled "director" is the opening move of the predator pattern ([165ao2t](https://www.reddit.com/r/Filmmakers/comments/165ao2t/), [1vbtcdq](https://reddit.com/r/acting/comments/1vbtcdq/)). The only way an owner can invite an actor who hasn't applied is **Rebook** from the Keep list, i.e. someone they have already worked with on Calltime.

### 3.2 What each swipe means

| Deck | Right (button: "Interested" / "Let's talk") | Left (button: "Pass" / "Not this time") | Up (button: "Save" / "Backup") |
|---|---|---|---|
| Gigs | Opens the **Apply sheet** (optional one line, choose a reel, answer the screening question if any). Submitting creates an `application` (PENDING). Uses 1 of **25 daily likes** | Hides the role. The owner is never told | Saves to "Saved" (no application) |
| Crew up / Applicants | **Match**: creates a `match` in TALKING state and opens chat with a draft booking card | **Decline**: the applicant gets a templated, no-blame closure ("This role went another way. Thanks for applying.") | **Backup**: application becomes BACKUP with a rank, and the applicant is told "You're on the backup list" |
| Crew up / Scout (crew roles only) | Sends an **Ask card** ("Would you be Sound Mixer on *Filter Coffee, 4 AM*? Sat 14-Sun 15 Nov · ₹2,500/day + kit · 2 meals") as an application with `origin=INVITED`. Max **10 asks per project per day** | Hides the person for this role | n/a |

**Every Ask respects the recipient's hard limits.** Scout and Rebook asks run the reverse of gates G4 (money) and G5 (content) server-side: a PAID_ONLY gaffer can never be sent an unpaid ask, and nobody is asked onto content they ruled out. If a Keep-list person fails a gate, the Rebook composer says why ("Kabir only takes paid work right now") instead of sending.

**Swipe-free equivalents.** Every swipe has a button and a TalkBack custom action. Swiping is never the only input.

### 3.3 When a match forms
A match forms **only when both sides have said yes to the same role**:
1. The collaborator applies (right swipe on Gigs), then the owner taps "Let's talk" (right swipe on Applicants); **or**
2. The owner sends an Ask card (right swipe on Scout for crew roles, or Rebook from the Keep list), then the person taps "Yes, let's talk".

A match is shown as **"IN TALKS"**, never as "It's a match!". The celebration happens later, at **"BOOKED"** (REDDIT_RESEARCH §8, "Never do": treating a swipe-match as a booking is an anti-pattern; [1gqihq7](https://reddit.com/r/acting/comments/1gqihq7/)).

### 3.4 From match to shoot (the part every other app skips)

```
Gigs right-swipe ─► APPLICATION (PENDING) ─► owner: Let's talk ─► MATCH (TALKING)
                         │ owner: Not this time ─► DECLINED (auto closure message)
                         │ owner: Backup ───────► BACKUP (ranked)
                         │ role filled/expired ─► CLOSED (auto closure message)

MATCH (TALKING) ─► (cast roles, optional) owner sends Audition request (F20) ─► actor: I'll do it / Can't make it
      │                                        └─ self-tape: actor taps [Send my tape] (link) ─► back in chat
      ─► owner sends Booking Card v1 ─► OFFERED ─► collaborator Accept ─► BOOKED
      ▲                                   │ collaborator "Ask to change" ─► back to TALKING (chat prefilled)
      └──────────── collaborator Decline ◄┘

ON-SET ROLE (cast, camera, sound, G&E, art, production...)
BOOKED ─► owner publishes Day Sheet(s) ─► each person taps "Got it" (ack)
       ─► T-48h: "Still coming?" Confirm / Can't make it (= withdraw flow)
       ─► any change to dates, pay or role = Booking Card v(n+1) with diff; re-accept required
       ─► last shoot date passes ─► WRAPPED ─► Wrap checklist (paid / footage / credit)
                                            ─► private feedback (double-blind) ─► reputation aggregates
                                            ─► "Keep" list ─► Rebook (Ask card for next project)

POST ROLE (remote_ok and dept ∈ POST, MUSIC, WRITING: editor, colorist, VFX, sound designer, composer, writer)
BOOKED ─► no day sheet, no T-48h check; the booking card carries a delivery date instead of shoot days
       ─► collaborator marks DELIVERY done (link) ─► owner [Got it] / [Not received]
              (no owner answer within 7 days of the collaborator marking it = auto-confirmed)
       ─► match WRAPPED (independently of the project's shoot) ─► Wrap checklist (paid / final film / credit)
       ─► feedback ("Delivered on time?" replaces "Showed up?") ─► reputation ─► Keep list ─► Rebook

Exits: WITHDRAWN (collaborator, with reason; on-set: notice ≥72h is neutral; post roles: ≥7 days before delivery)
       RELEASED (owner, with reason; release or cancellation with <72h notice counts against the owner's
                 "Kept bookings", §4 F14)
       NO_SHOW (owner marks after a shoot day; collaborator can dispute → moderation)
```

Why post roles get their own path: composers, editors and sound designers work **after** the shoot, so wrapping them when the last shoot day passes would ask for feedback before they've started and date their pay to the wrong day. It also lets a post role be booked in pre-production, which is what composers and VFX artists ask for ("brought in early", [1v1nrip](https://www.reddit.com/r/filmscoring/comments/1v1nrip/), [roles-perspectives](raw/roles-perspectives.md)).

What happens right after a match (all in MVP):
1. Chat opens with a **system message** summarising the role's terms (pay line, dates, area, perks) so nobody has to ask "what's the rate?" ([1v4eyq0](https://www.reddit.com/r/Filmmakers/comments/1v4eyq0/)).
2. A pinned bar shows **"Send booking card"** to the owner and **"Waiting for the booking card"** to the collaborator. The card is pre-filled from the role, so sending it takes two taps. On cast roles the bar also offers **[Request audition]** (F20), which can only use the audition mode declared on the listing.
3. Quick-reply chips help introverts: "Yes, I'm in", "What are the call times?", "Can't make those dates, thanks!" (P6; [1rdzhzw](https://reddit.com/r/Filmmakers/comments/1rdzhzw/)).
4. If there is no booking card within **72h**, the owner gets a nudge; after **7 days** of silence the match auto-closes with a polite message to both.
5. On acceptance: the role's `filled_count` goes up. When the role is full, all PENDING applicants get an automatic closure, and BACKUP applicants stay on the bench.
6. The booked person appears on the project's **Team** list with their call status and wrap progress.

### 3.5 Asymmetry by role scarcity
Each role in the taxonomy (Appendix A) has a `scarcity`: SCARCE, BALANCED or OVERSUPPLIED.

| Effect | SCARCE (sound, boom, gaffer, 1st AD, producer, locations, intimacy or stunt coordinator) | BALANCED | OVERSUPPLIED (actor, director, writer, composer, PA) |
|---|---|---|---|
| Default application cap | 25 | 15 | 12 (actor: 20, with a screening question suggested) |
| Crew-up default sub-deck | **Scout** first | Applicants | Applicants (cast roles: Applicants only, no Scout tab) |
| Posting nudge | If the role is unpaid: "Sound is the role most likely to be paid even on free sets. Add a stipend or kit fee?" ([1f23l1v](https://www.reddit.com/r/LocationSound/comments/1f23l1v/)) | none | Suggest a screening question |

### 3.6 Caps and closure (fewer, better applications)
- **Applicant daily likes:** 25 right-swipes per user per local day. The counter is visible ("18 left today").
- **Application cap per role:** default by scarcity, editable from 5 to 40. A role at its cap leaves the Gigs deck. A role that falls back below its cap (after declines) reappears.
- **Closure guarantee:** every application ends in a visible status within **14 days**. If the owner doesn't act, it auto-expires with "No answer from this project. That counts against their response rate." Owner response rate is a public chip.
- **Role auto-close:** when the shoot start date passes (on-set roles) or the post deadline passes (post roles).

---

## 4. MVP features

Each feature lists the pains it addresses (P#, from REDDIT_RESEARCH §2) and the definition of done. Every feature sits behind one `Repo` interface (§10). Every feature must work in **demo mode**. Supabase (LIVE) mode is built in the tiers in §4.2: L0 in this session, L1 before public launch. A LIVE build hides the entry points of L1 features until they ship, so no button leads to a dead end.

### 4.1 Feature list

| ID | Feature | Pains | Evidence | Done when |
|---|---|---|---|---|
| **F1** | **Browse without an account** (guest Gigs deck and role detail; actions prompt sign-in) | P9, launch friction | "Still nothing. I guess you have to sign up first... ain't happening" ([1m4gvfv](https://reddit.com/r/filmmaking/comments/1m4gvfv/)) | A guest can swipe the Gigs deck read-only, open details, and sees a sign-in sheet on any action |
| **F2** | **Light sign-in and onboarding.** Email OTP (phone OTP when an SMS provider is configured). 18+ confirmation. 3 steps: roles and level; city, area, radius, languages; how you work (comp mode, unpaid must-haves, availability, one optional work link) | P6, friction | "Requiring profile setup at signup is a great way to lose... downloaders" ([18kqlnz](https://reddit.com/r/Filmmakers/comments/18kqlnz/)) | First swipe within 90 s of install. Step 3 is skippable (defaults applied) |
| **F3** | **Role taxonomy with plain-language duties and step-up roles** (29 roles, Appendix A) | P7, P10 | "Producer/PA position is really confusing" ([1wi3qhz](https://reddit.com/r/AtlFilmmakers/comments/1wi3qhz/)); step-up is a top yes-reason ([1vabt10](https://www.reddit.com/r/Filmmakers/comments/1vabt10/)) | Every role picker shows the duties line; profiles accept up to 2 step-up roles; roles can be marked "step-up friendly" |
| **F4** | **Post a Project wizard with a listing rules engine** (structured pay, logistics, perks with dates, safety flags; "no terms, no post"). **Low friction is part of the spec:** only the fields marked Req in §6.2-6.3 are asked up front; pay, terms and perks set once at project level copy into every role; [Duplicate role]; everything else sits under a collapsed "More details". A post-only project (every role remote, e.g. a film already shot that needs an editor and composer) skips the shoot-logistics fields | P2, P7, P5, P8 | "'Collaborate' is always code for unpaid work" ([1ovhqqk](https://www.reddit.com/r/Filmmakers/comments/1ovhqqk/)); r/filmscoring forced pay flair ([1h06fmg](https://www.reddit.com/r/filmscoring/comments/1h06fmg/)); "Will owners accept mandatory fields?" (REDDIT_RESEARCH §9 Q2) | `ListingRules` (§5.3) blocks publishing on any BLOCK rule; warnings are shown inline; a live card preview is shown; publishing works in demo mode; a 3-role on-set project can be published in **≤4 minutes** and a post-only project in ≤2 minutes in the QA script |
| **F5** | **Gigs deck** with hard gates, a 0-100 fit score, exploration and small-market fallback (§7), Role Card face (§6.5), filters, an honest density header ("Mumbai · 214 people · 31 open projects · 9 sound mixers"). **Early cities** (listed but not yet launched, e.g. Indore) work too: people there can sign up and post, the header says so honestly ("Indore · early city · 3 people · 1 open project · remote roles from across India included"), and remote post roles from the whole country fill the deck | P9, P10, P7 | "only 2 ppl near me" ([4q7fgz](https://reddit.com/r/WeAreTheMusicMakers/comments/4q7fgz/)); "Even the filmmaker Facebook group is dead quiet" ([15sa7ji](https://reddit.com/r/Filmmakers/comments/15sa7ji/)) | The deck never shows a role that fails a gate; "Outside your radius" label on fallback cards; empty state offers fixes; an early-city user sees remote roles and their own city's projects, never a blank screen |
| **F6** | **Apply sheet**: one line (≤140 chars), pick a reel, answer the screening question. No prescreens or demos for unpaid roles. 25/day cap | P10, spec work | "Do NOT ask for a prescreen video" ([1rdzhzw](https://reddit.com/r/Filmmakers/comments/1rdzhzw/)) | An application is created, the role count increments, and the cap is enforced in both modes |
| **F7** | **Crew-up deck** (Applicants and Scout) with `ApplicantRanker` (§7.4), reason chips, Backup action, auto-decline message, Ask cards (10/day/project). **Scout is for crew roles only**; cast roles have no Scout tab (§3.1) | P10, P3, P6, P8 | "200 responses of which maybe 15 are qualified" ([1axfd2z](https://reddit.com/r/FilmIndustryLA/comments/1axfd2z/)); "have a list of second choices" ([1bdo28y](https://reddit.com/r/Filmmakers/comments/1bdo28y/)) | Owner can talk, decline or backup; declined applicants see closure; Ask cards arrive in the recipient's Inbox; Scout never returns a cast profile and never returns someone who fails reverse G4/G5 |
| **F8** | **Inbox and 1:1 chat** with contact masking, off-platform, money and body-request warnings (`MessageSafety`, §5.2), quick replies, report and block from any message | P5, P8, messaging friction | "He definitely wanted to move the conversation away from CN" ([165ao2t](https://www.reddit.com/r/Filmmakers/comments/165ao2t/)) | Flags render on the recipient's side; contact details are masked until BOOKED; Realtime updates in Supabase mode |
| **F9** | **Booking card**: a two-tap, plain-language "booking confirmation" pre-filled from the role. Sent by the **payer**. Accept requires a notice pledge (72h before the shoot; for post roles, 7 days before delivery). Every later change creates a new version with a highlighted **diff** and needs re-acceptance. **Post roles** carry a delivery date (and picture-lock date for music and sound design) instead of shoot days, and their pay-by date is counted from delivery | P4, P1, P3 | "make sure its the person who pays that signs" ([1um8uk5](https://www.reddit.com/r/Filmmakers/comments/1um8uk5/)); "a week became two days"; editors need "a post end date" in writing ([1udttys](https://www.reddit.com/r/editors/comments/1udttys/)) | Versions persisted; diff shows changed fields in amber; BOOKED only after acceptance; role filled logic runs; a post-role card shows "Deliver by 7 Feb · paid within 7 days of delivery" |
| **F10** | **Backup bench and withdrawal flow**: ranked backups per role. Collaborator withdraws with a reason; notice of ≥72h is neutral. Owner gets a one-tap "Offer to backup #1" | P3 | "90% of the crew... bail out 5 days before" ([1s7vlqc](https://reddit.com/r/Filmmakers/comments/1s7vlqc/)) | Withdrawal reopens the slot; backup #1 receives an Ask card pre-filled with the same terms |
| **F11** | **Day sheet**: per shoot day, general call and staggered per-person call times, full address (booked people only) with tappable maps link, parking or transit, nearest hospital, meals, wrap-by time, notes. Publish or update notifies the team. "Got it" ack, ack list with "Nudge", T-48h "Still coming?" (an in-app banner plus an Inbox item, and a local notification when the app is closed, via F21). Share as **WhatsApp text**, **Add to calendar**, **Share with a trusted contact**. On-set roles only; post roles have no day sheet | P11, P3, P8 | "PLEASE PUT THE LOCATION ADDRESS IN THE BODY OF THE EMAIL" ([1udy93i](https://reddit.com/r/Filmmakers/comments/1udy93i/)); "the one-liner... can go out weeks ahead" ([1vpfkp0](https://www.reddit.com/r/Filmmakers/comments/1vpfkp0/)) | Booked people see it; acks recorded; share intents work with no app installed on the receiving end |
| **F12** | **Change and cancel broadcast**: rescheduling a project or cancelling a shoot day notifies every booked and pending person, with an acknowledgement. Reschedules increment `reschedule_count`, shown on the card at 2 or more | P11, P3 | "things that push twice on the start date always seem to vanish" ([1kzesa0](https://www.reddit.com/r/Filmmakers/comments/1kzesa0/)); actor ghosted on shoot day ([1vnwpl6](https://reddit.com/r/acting/comments/1vnwpl6/)) | Booked people get a new booking-card version with the changed dates (accepting or declining is penalty-free) |
| **F13** | **Wrap checklist**: items generated from the booking card (PAID, FOOTAGE, FINAL_FILM, CREDIT) with due dates. Owner marks done (with link); collaborator confirms "Got it" or "Not received". Post roles add a **DELIVERY** item that runs the other way: the collaborator marks it done with a link and the owner confirms (auto-confirmed after 7 days of owner silence); post roles get no FOOTAGE item. Overdue items nudge whoever owes them, daily, in the Inbox | P1, P4 | "The footage is our pay" ([1infcka](https://www.reddit.com/r/acting/comments/1infcka/)); "a wrap checklist" proposed ([1uc2pmc](https://www.reddit.com/r/acting/comments/1uc2pmc/)) | Both sides see the same states; overdue logic works with the demo clock |
| **F14** | **Private feedback and fact-based reputation**: forced-choice questions after wrap, double-blind (revealed when both submit, or after 14 days). Public chips show **facts only**, and only when n ≥ 3. **Both sides are accountable.** For owners: Paid on time X/Y, Footage delivered X/Y, **Kept bookings X/Y** (a booking counts as not kept if it was released or cancelled with <72h notice, or if a new booking-card version that cut days or pay was sent within 72h of the first shoot day or delivery date; `ReputationCalc` derives this from `matches.notice_hours` and `booking_cards`), Shoots completed N, Replies in ~Xh, Closes the loop X%. For collaborators: Showed up X/Y (on-set), **Delivered on time X/Y** (post roles), Shoots completed N. "Would work again" stays private and feeds the **Keep list** and **Rebook** | P1, P3, P4, reputation; "Accountability for one side only" is a never-do (REDDIT_RESEARCH §8) | "Dont say shit, one day that nothing comment will bite you" ([1ov4ary](https://reddit.com/r/Filmmakers/comments/1ov4ary/)); "We all have a list" ([1kzesa0](https://www.reddit.com/r/Filmmakers/comments/1kzesa0/)) | Aggregates match `ReputationCalc` unit tests; no individual answer is ever shown to anyone else |
| **F15** | **Trust and safety basics**: verification badges with exact wording, report (any profile, project or message) with status tracking, block (both directions), Safety Center, contextual safety tips, community rules, 18+ only | P5, P8 | "background checked... so I thought it was legitimate" ([1awhlch](https://reddit.com/r/acting/comments/1awhlch/)) | See §5. Reports visible in the Safety Center with status |
| **F16** | **Profiles, work-first**: roles and level, reels (YouTube thumbnails via Coil, generated posters otherwise), confirmed credits (from wrapped bookings) shown separately from self-listed ones, gear, languages, availability, taste (5 films), goals. **No profile photos in the MVP**: an initials monogram stands in, and actors' faces appear only through HEADSHOT links on roles they applied to. **Profiles are not a public directory**: they are readable only where a relationship exists (§9.3), so they can't be scraped. Data export (JSON via the share sheet). Account deletion | P6, P12, P5, P8 | "Finding crew... booked up for years" ([1hw08t3](https://reddit.com/r/Filmmakers/comments/1hw08t3/)); Coverfly and Daisie portfolio loss; fake-check scams built from scraped Mandy/Backstage profiles ([existing-platforms](raw/existing-platforms.md)) | Export produces a valid JSON file; deletion removes the profile (cascade); a signed-in user with no relationship to a profile can't fetch it |
| **F17** | **Share a role card out** (WhatsApp, Instagram, FB groups) as plain text plus a link; deep link `https://calltime.app/r/{roleId}` and `calltime://r/{roleId}` opens the role (guest OK). **The share text must stand on its own** (role, pay, dates, place), because most people in a WhatsApp group won't have the app. The https base comes from `SHARE_BASE_URL` in `local.properties`; when it is blank, the link line reads "Get Calltime on Google Play" with the Play listing URL. Public launch needs a one-page static fallback at that base (shows the card text and a Play button) | P9, cold start | Airbnb-Craigslist playbook ([1uq24in](https://reddit.com/r/startups/comments/1uq24in/)); FB is the incumbent | Share text matches the template in §8.S11; the deep link resolves in both modes; no share text ever contains a dead link |
| **F18** | **Demo mode**: seeded realistic data, 4 personas plus "build my own", a simulator for match-backs, replies, booking acceptance, acks and wrap; demo controls (reset, fast-forward clock, speed) | Build constraint | n/a | The APK runs with no backend; the full loop can be demonstrated in under 5 minutes (§15 QA script) |
| **F19** | **Trust pages**: "How Calltime makes money" (free for talent forever; future paid tools only for project owners), "No gen-AI on your work" pledge, community rules, privacy | P12, AI distrust | "What's the CATCH" ([18kqlnz](https://reddit.com/r/Filmmakers/comments/18kqlnz/)); "dude uses AI, that's gonna be a no from me" ([1r6tsu7](https://reddit.com/r/Filmmakers/comments/1r6tsu7/)) | Static screens reachable from Settings and Welcome |
| **F20** | **Audition request card** (cast roles, in TALKING): a structured chat card whose mode is locked to the listing's `audition_mode`. Self-tape: sides link + due date, and the actor replies with [Send my tape] (a link). Video call: date and time. Studio or public space: venue name, address, time, and who will be present (≥1 other person by role). In-person times are limited to **08:00-21:00**. Every card carries the fixed lines "No nudity at auditions · No fees · Bring a friend if you like · You never have to meet alone". Actor buttons: [I'll do it] [Can't make that time] [Report] | P8, P5, P3 | "any deviation to a private place or 1-1 is NOT ok" ([165ao2t](https://www.reddit.com/r/Filmmakers/comments/165ao2t/)); late-night script swap and FaceTime audition ([1wb9byp](https://reddit.com/r/acting/comments/1wb9byp/)); "I direct short films" texts after 11pm ([1jgaubm](https://reddit.com/r/hyderabad/comments/1jgaubm/)) | The composer can't pick a mode the listing didn't declare, can't pick a private home, and can't pick an in-person time outside 08:00-21:00; the card renders with the fixed lines; [Report] pre-fills "Unsafe set or audition" |
| **F21** | **Closed-app notifications**. DEMO: the simulator posts local notifications. LIVE (L1): a WorkManager job polls `notifications` every 15 minutes and posts local notifications, and booking a shoot schedules local reminders (T-48h "Still coming?", morning-of call time). FCM replaces the poll in v1.0 | P3, P10, P11 | Every closure, Ask, booking card and T-48h check fails if people only see it when they happen to open the app; WhatsApp-native users don't browse apps for updates ([beginners-students-india](raw/beginners-students-india.md)) | With the app swiped away, a new booking card or Ask produces a notification within 15 minutes (LIVE) or immediately (DEMO); tapping it opens the right screen via `calltime://m/{matchId}` |

### 4.2 Priority inside the MVP (if the session runs short, cut from the bottom)
**DEMO mode (everything must exist here; this is what QA runs against):**
- **P0 (the loop):** F1, F2, F3, F4, F5, F6, F7, F8, F9, F13, F15, F16, F18, F20 (small: one message kind and one sheet)
- **P1:** F11, F14, F10, F17, F21 (demo half)
- **P2:** F12, F19 (static pages are cheap; ship them if possible), and visual polish: film grain, light leak, sprocket strips, the slate-clap and call-time typing animations (§12). Until P2 lands, posters are a flat gradient plus title and the overlays are a simple fade and scale.

**LIVE (Supabase) mode, in tiers.** The migration in §9 creates **every** table now, so the schema is frozen once; the tiers only decide which RPCs and `SupabaseRepo` methods are wired up.
- **L0, this session:** email OTP auth, profiles (with the relationship-scoped reads in §9.3), cities and density, `publish_project` with `listing_rules_ok()`, `discover_roles`, `apply_to_role`, `get_applicants`, `decide_application`, `scout_candidates`, `send_ask` / `respond_ask`, messages with Realtime and the flags trigger, audition cards, `send_booking_card` / `respond_booking_card`, report, block, export and delete.
- **L1, before any public launch:** shoot days, acks and attendance, wrap items (including DELIVERY), feedback and `reputation_stats`, reschedule and cancel broadcast, withdraw and release with backups, `get_inbox` auto-wrap, and the F21 WorkManager poller. Until L1 ships, a LIVE build hides those entry points (§4 intro). **Calltime does not launch publicly on L0 alone**: the wrap checklist and reputation are the product's reason to exist.

### 4.3 Not in MVP (explicit "Later" list)
| Later item | Why deferred | Research priority |
|---|---|---|
| Payments, escrow, deposits, UPI/Stripe payout (MVP **records** pay status only) | Regulation, KYC, scope | should |
| Vendor ID plus selfie liveness (MVP: **concierge ID check** by the team, set via service role) | Vendor choice; Aadhaar restrictions in India | must, but the concierge check covers launch |
| FCM push (MVP: Realtime while running, plus the F21 WorkManager poll and scheduled local reminders when closed) | Firebase setup | must for v1.0 |
| Profile photos and avatar upload (and so Supabase Storage). MVP: initials monogram; actors' HEADSHOT links only on roles they applied to | Faces-first risk (P8) and Storage scope | could |
| Scouting actors (MVP: cast roles are Applicants-only; Rebook from the Keep list is the only invite) | Predator pattern; actors are oversupplied | won't, unless the safety data says otherwise |
| Late-cancellation (kill) fee on the booking card; overtime terms | Can't be enforced without payments; MVP shows "Kept bookings" and the hours cap instead | should |
| Message quiet hours (hold non-booked messages overnight) | Small setting; the 08:00-21:00 audition window covers the sharpest risk | could |
| Pencil holds with expiry (soft holds) | Adds a state machine | must (finding-crew) |
| Project team group thread (MVP: 1:1 chats, plus the day sheet as broadcast, plus WhatsApp share) | RLS and members complexity | must (India) |
| Crew packages (a DP applies with their gaffer and AC), department heads inviting teams | Graph complexity | should |
| Mutual connections and vouch graph | Needs history | should |
| Challenge mode (IFP 50-Hour, 48HFP city lobbies), monthly city challenges in-app | Run manually for launch (§13) | should |
| Footage upload and hosting (MVP: links only) | Storage cost, rights | should |
| PDF call sheets, weather, hospital auto-fill, one-liner schedule view | Text share covers MVP | should |
| Carpool board, room roster, dietary notes, gear check-in/out and insurance | Logistics suite creep | should |
| Favour bank / skill swap ledger | Nice-to-have | should/could |
| Script sharing with watermark, script registration hash | IP features | could |
| Release forms and e-sign legal documents | Legal review per country | could |
| Hindi, Tamil and other UI languages (MVP: all strings in `strings.xml`, ready for translation) | Translation | could |
| Exposure cap per role per day; batched release windows (so a capped role isn't a race to apply in the first hour) | Server work; at launch density caps fill slowly. Revisit when the "cap-fill time" metric (§14) shows any role reaching its cap in under 2h | should |
| Rate guides, budget sanity hints | Data needed | could |
| Minors and guardian accounts (MVP: 18+ only; no under-18 cast roles) | Safety and legal | n/a |
| Web view, iOS | Android-first | n/a |
| Template-spam detection across users; late-payment pattern review queue (MVP: per-message flags and per-person aggregates) | Server jobs | must (v1.0) |
| Private notes on people; private blocklist notes | Small | could |
| Festival submission tracker | Retention extra | could |
| Any monetization | Trust first | n/a |

---

## 5. Trust and safety (MVP)

### 5.1 Verification: what is checked and how it is worded
| Badge | How it is earned (MVP) | Exact UI wording (tooltip) | Who can set it |
|---|---|---|---|
| Email verified | Supabase email OTP | "Email confirmed by code." | System |
| Phone verified | Supabase phone OTP (if an SMS provider is configured) | "Phone confirmed by code." | System |
| **ID verified** | **Concierge check** in the launch city: a 5-minute video call where a team member matches the face to a government ID. **No ID image is stored.** | "We matched this person's face to a government ID on a video call. This does not vouch for any project." | `service_role` only (admin script). RLS blocks self-set |
| Confirmed credit | A credit from a Calltime booking whose CREDIT wrap item the production confirmed | "Confirmed by the production on Calltime." Self-listed credits are labelled "Self-listed" | System |
| New on Calltime | Fewer than 3 data points for any aggregate | "Not enough shoots yet to show a track record." | System |

**Never** use "verified project", "background checked", "vetted" or "safe" on any badge (REDDIT_RESEARCH P8; [1awhlch](https://reddit.com/r/acting/comments/1awhlch/)). Badges can't be bought, now or later.

### 5.2 Message safety (chat). Module `domain/MessageSafety.kt`, pure Kotlin
Runs on chat messages, **Ask-card notes and the free-text fields of audition cards**: on send (sender-side preview) and on render (recipient-side banners). The server trigger stores the same flags (§9) for moderation. Patterns are in Appendix C.

| Category | Example triggers (English and Hinglish) | Action |
|---|---|---|
| CONTACT | Phone numbers, emails, @handles | **Masked** ("•••• shared contact hidden") until the match is BOOKED. After booking, both people see "Share contacts" buttons (opt-in each way) |
| OFF_PLATFORM | "whatsapp me", "telegram", "email me", "number do", "call karo" | Blue soft banner to **both**: "Keeping talks on Calltime until you're booked protects you. Here's why." |
| MONEY_FROM_TALENT | "registration fee", "security deposit", "portfolio shoot fee", "send ₹", "Zelle", "pay to audition", "fees bharni" | **Red banner to the recipient:** "Calltime never allows charging cast or crew. This is a common scam." Buttons: [Report] [Block]. Message auto-flagged for moderation |
| BODY_REQUEST | "bikini", "bold photos", "measurements", "bra size", "photos bhejo" | Red banner: "You never need to send body photos or measurements to apply. Report?" Auto-flagged |
| PRIVATE_MEETING | "my place", "my flat", "come alone", "hotel room" | Amber tip: "Normal auditions are self-tape, video call, or a public or studio space with others present." |

### 5.3 Listing rules engine. Module `domain/ListingRules.kt`, pure Kotlin. Run in the wizard (live) and on publish; mirrored by a DB check function in Supabase mode
| Rule | Check | Severity |
|---|---|---|
| R01 | Every role has a `comp_type`. PAID_DAY, PAID_FLAT and STIPEND require amount > 0 and `payment_terms` | BLOCK |
| R02 | An UNPAID **on-set** role needs ≥2 perks (role perks plus derived perks, §7.1) from {TRAVEL_COVERED, LODGING, FOOTAGE_BY_DATE, FINAL_FILM_BY_DATE, STEP_UP_CREDIT, MEALS}, **and** project `meals` ≥ ONE_MEAL ("If you can't pay people, feed people") | BLOCK |
| R03 | An UNPAID ACTOR role requires FOOTAGE_BY_DATE with weeks ≤ 26 | BLOCK |
| R04 | Paid amount per day below the city's `paid_floor_day` is auto-relabelled **Stipend** (the card never says "Paid") | AUTO + warning |
| R05 | **If the project has any on-set role:** dates are valid (start ≥ today; end ≥ start; 1 ≤ shoot_days ≤ span; 2 ≤ hours_cap ≤ 16) and meals, transport and location type are answered. **Post-only projects** (every role remote in POST/MUSIC/WRITING) skip these checks; each role instead needs `post_deadline` ≥ today + 7 | BLOCK |
| R06 | `hours_cap` > 12: "Long days lose crew. Can you cap at 10-12?" | WARN |
| R07 | Content flags explicitly answered ("None of these" counts) | BLOCK |
| R08 | NUDITY, SIMULATED_SEX or INTIMACY_SCENE → intimacy coordinator Y/N answered **and** `safety_measures` ≥ 20 chars. "No nudity at auditions" is fixed and can't be changed | BLOCK |
| R09 | STUNTS_FIGHTS or FIREARMS_PROPS → coordinator or armourer answered, plus `safety_measures` | BLOCK |
| R10 | Cast roles are 18+ only in the MVP. MINORS_ON_SET requires "guardian present" = yes | BLOCK |
| R11 | Cast roles need `audition_mode` ∈ {SELF_TAPE, VIDEO_CALL, STUDIO_OR_PUBLIC_WITH_OTHERS}. A private home is **not** an audition option | BLOCK |
| R12 | Any text field matching MONEY_FROM_TALENT | BLOCK ("Calltime never allows charging cast or crew.") |
| R13 | Unpaid role whose screening question asks for spec work ("demo", "test edit", "prescreen", "custom", "sample cut") | BLOCK |
| R14 | Contact info (phone, email, @handle) in any public text | BLOCK ("Keep contact in Calltime until you're booked.") |
| R15 | UNPAID role whose text uses "collab", "exposure", "passion project", "great opportunity", "deferred", "profit share" | WARN ("Say what people get instead." For deferred: "Deferred pay shows as Unpaid. Say what people get now.") |
| R16 | Role `scope_includes` spans ≥3 departments | WARN ("This reads like 3 jobs. Split it into roles?") |
| R17 | Camera, sound or G&E role with gear policy BYO_NO_FEE | WARN, and the card shows the chip "Own gear, no kit fee" |
| R18 | Title 2-60 chars; logline 20-200 chars; ≥1 language; ≥1 role | BLOCK |
| R19 | A project with any cast role **and** NUDITY, SIMULATED_SEX or INTIMACY_SCENE can be published only by an **ID-verified** owner | BLOCK ("Projects with intimate scenes need an ID-verified owner. Book a 5-minute check.") |
| R20 | COMPOSER or SOUND_DESIGNER role on a project with no `picture_lock_date` (a target date is fine) | BLOCK ("Composers and sound designers need a picture-lock date to judge the job.") |
| R21 | COMPOSER or SOUND_DESIGNER role whose `post_deadline` − `picture_lock_date` < 21 days | WARN ("That's under 3 weeks after picture lock. Composers and sound designers need time.") |
| R22 | Project in DEVELOPMENT or PRE with on-set roles but no POST or MUSIC role | WARN, shown once on the Review step ("Editors and composers do better work when they're in early. Add them now as remote roles?") |

### 5.4 What is shown (pay transparency and legitimacy)
- **Pay line first** on every Role Card (§6.5). PAID shows amount, unit, kit fee and terms. STIPEND is labelled "Stipend". UNPAID reads "Unpaid · " followed by the perks. "Credit" is never shown as a perk because every role gets a credit. There is **no "Deferred" pay type**: deferred pay "never" pays (P4), so a deferred role is posted as Unpaid and may mention the deferral in its notes (R15 warns).
- **"X of Y roles paid"** chip on project detail (pay parity: [u9seb6](https://www.reddit.com/r/LocationSound/comments/u9seb6/), [1v1nrip](https://www.reddit.com/r/filmscoring/comments/1v1nrip/)).
- **Owner track record chips** (n ≥ 3, including "Kept bookings X/Y"), owner **response rate**, "Honest zero-budget" label, script status, team attached, "Rescheduled 2×" (if ≥2), and **"Beginners welcome"** when a role's `level_wanted` is FIRST_PROJECT or BUILDING_REEL.
- **Exact address** is hidden until BOOKED; only the area is shown publicly.
- **Shoot location is a private home**: the project card shows "Shoot at a private home · Bring a friend welcome" (the toggle is forced on).

### 5.5 Report and block
- **Report** is available from: profile overflow, role or project overflow, long-press on a message, the booking card overflow, and the feedback answer "Felt safe? No".
- Reasons: Asked for money · Sexual or inappropriate request · Fake or impersonation · Unsafe set or audition · Harassment · Didn't pay as agreed · Spam · Other (+ details ≤500 chars).
- After submitting: "Thanks. We aim to review within 24 hours." The report appears in **Safety Center → My reports** with status RECEIVED → REVIEWING → ACTION_TAKEN / NO_ACTION. Moderation runs on the Supabase dashboard via `service_role` in the MVP.
- **Block** hides both people from each other everywhere (decks, inbox, applicants) and ends active matches with a neutral system message.
- Repeat pattern: a target with ≥3 independent reports is auto-hidden from decks pending review (server function; a demo flag).

### 5.6 Safety tips (contextual, not a wall of text)
| Moment | Tip card |
|---|---|
| First time opening any cast role | "What normal looks like: auditions are self-tape, video, or a public or studio space with others present, during the day. No fees, ever. No nudity at auditions. Sides come with the audition request in the app, so you never need to move to WhatsApp or meet anyone to get them." |
| Before the first in-person meeting (booking accepted) | "Share your call time and location with someone you trust" → [Share with trusted contact]. "Bring a friend if the listing says it's welcome." "Cast and crew can always share contacts with each other." |
| Day sheet | [Share with trusted contact] button (plain text: project, address, call time, wrap-by; no other people's details) |
| Project at a private home | "This shoot is at a private home. A friend is welcome. If plans change to 1:1, leave and report." |
| Safety Center | Emergency number by country (India 112, US 911, UK 999), community rules, how reporting works |

---

## 6. Schemas

Conventions: `type` is the Kotlin model type (DB column type in §9). **Req** means required to save or publish. **Face** means shown on the card face (S05/S09); "detail" means only in the detail view.

### 6.1 Profile
| Field | Type | Req | Shown on card face? |
|---|---|---|---|
| id | UUID (= auth user id) | yes | no |
| display_name | String 2-40 | yes | **Face** as "Meera K." (first name + last initial) |
| pronouns | String? | no | detail |
| primary_role | RoleCode | yes | **Face** |
| secondary_roles | `List<{RoleCode, proficiency 1-3}>` ≤4 | no | detail |
| step_up_roles | `List<RoleCode>` ≤2 | no | **Face** as "→ DP" chip |
| level | Level (FIRST_PROJECT, BUILDING_REEL, CREDITED_INDIE, WORKING_PRO) | yes | **Face** |
| city_id, area_id | String | yes | **Face** as area name + distance ("Versova · 3 km") |
| travel_radius_km | Int 2-150 (default 15) | yes | no |
| travel_if_covered | Boolean | no | no |
| remote_ok | Boolean (default true for POST/MUSIC/WRITING roles) | yes | **Face** "Remote OK" |
| languages | `List<String>` ISO 639-1, ≥1 | yes | detail (Face only when a project language matches: "Speaks Kannada") |
| availability_pattern | ANY_DAY, WEEKENDS_ONLY, EVENINGS_AND_WEEKENDS, WEEKDAYS_ONLY | yes | **Face** (computed "3/3 days" in Crew up) |
| busy_dates | `Set<LocalDate>` | no | computed only |
| comp_mode | PAID_ONLY, PAID_AND_SELECTIVE, OPEN_TO_UNPAID | yes | **Face** ("Paid only" / "Open to unpaid if...") |
| min_day_rate, currency | Int? (**private**, never displayed) | no | no (gating only) |
| kit_fee_day | Int? | no | **Face** if gear is listed ("Kit ₹1,500/day") |
| unpaid_must_haves | `List<Perk>` | required if comp_mode ≠ PAID_ONLY (may be empty) | detail ("Will do unpaid if: footage by date, meals") |
| goals | `List<Goal>` (PAID_WORK, REEL_FOOTAGE, STEP_UP, FESTIVAL_CREDIT, BUILD_TEAM, FUN_COMMUNITY) | no | no (ranking) |
| content_wont_do | `List<ContentFlag>` | no | **never** shown (gate only) |
| work_links | `List<{url, kind REEL/CREDIT/HEADSHOT/PORTFOLIO/IMDB/OTHER, role_code?, title?, year?}>` | ≥0 (1 recommended) | **Face**: first REEL thumbnail. HEADSHOT appears only in Applicants for a cast role the actor applied to |
| gear | `List<String>` | no | **Face** chip "Has kit" + detail list |
| tools | `List<String>` (Resolve, Premiere, Pro Tools...) | no | detail |
| taste_films | `List<String>` ≤5 | no | detail ("Favourite films") |
| genres_pref, formats_pref | `List<String>` | no | no (ranking) |
| bio | String ≤280 | no | detail |
| affiliations | `List<String>` (school, theatre group, collective) | no | detail |
| (no avatar) | Not in the MVP. UI shows an initials monogram ("MK") | n/a | n/a |
| email_verified, phone_verified, id_verified | Boolean (system) | system | **Face** badges |
| reputation | Reputation (system aggregate, §4 F14) | system | **Face**: one best chip, e.g. "Showed up 9/9" or "New on Calltime" |
| last_active_at | Instant (system) | system | **Face** "Active today" if <24h |

### 6.2 Project
| Field | Type | Req | Shown on card face? |
|---|---|---|---|
| id, owner_id | UUID | system | no |
| payer_id | UUID (MVP = owner_id; shown as "Payer: Rohan K.") | system | detail + booking card |
| title | String 2-60 | yes | **Face** |
| logline | String 20-200 | yes | **Face** (2 lines max) |
| format | Format (SHORT, FEATURE, SERIES_PILOT, MUSIC_VIDEO, SPEC_AD, STUDENT_THESIS, CHALLENGE_48H, VERTICAL, DOCUMENTARY) | yes | **Face** chip |
| runtime_min | Int 1-240 | yes | **Face** ("12 min") |
| page_count | Int? | no | detail |
| genres | `List<String>` 1-3 | yes | **Face** (first genre) |
| languages | `List<String>` ≥1 | yes | **Face** chip (e.g. "Kannada · English") |
| stage | DEVELOPMENT, PRE, PRODUCTION, POST | yes (default PRE; POST pre-selects "post-only" in the wizard) | detail |
| script_status | IDEA, DRAFT, LOCKED | yes | **Face** chip "Script locked" |
| script_readable_on_request | Boolean | no | detail |
| lookbook_url | String? (YouTube, Vimeo, Drive...) | no | **Face** thumbnail (YouTube) or generated poster |
| ambition_tier | FOR_FUN, PORTFOLIO, FESTIVAL, DISTRIBUTION, CLIENT_PAID | yes (default PORTFOLIO) | detail |
| honest_zero_budget | Boolean | no | **Face** chip "Honest zero-budget" |
| city_id, area_id | String | yes | **Face** ("Jayanagar · 4 km") |
| shoot_location_type | STUDIO, OUTDOOR_PUBLIC, PRIVATE_HOME, OFFICE_COMMERCIAL, MIXED | if any on-set role | detail (PRIVATE_HOME also shows a Face chip) |
| exact_address | String? | required before the first day sheet | **booked people only** |
| story_setting | String? ≤80 | no | detail ("Set in a hill station · shot in Jayanagar") |
| shoot_start, shoot_end | LocalDate? | if any on-set role (post-only projects: null, or past dates kept for reference) | **Face** |
| shoot_days | Int? | if any on-set role | **Face** ("2 days") |
| date_certainty | LOCKED, TENTATIVE | if any on-set role | **Face** ("Dates locked") |
| hours_cap | Int 2-16 | if any on-set role | **Face** ("≤10h") |
| meals | NONE, CRAFTY_ONLY, ONE_MEAL, TWO_PLUS_MEALS, CATERED | if any on-set role | **Face** (in the pay line perks) |
| transport | NONE, CARPOOL_COORDINATED, MILEAGE_OR_TA_PAID, PRODUCTION_VAN | if any on-set role | **Face** (in the perks when ≠ NONE) |
| picture_lock_date | LocalDate? (a target is fine) | if any COMPOSER or SOUND_DESIGNER role (R20) | **Face** on music and sound-design roles ("Lock 10 Jan → due 7 Feb · 4 wks") |
| travel_allowance_per_day | Int? | if MILEAGE_OR_TA_PAID | **Face** ("TA ₹300/day") |
| lodging | Boolean | required if shoot_days > 1 | detail |
| content_flags | `Set<ContentFlag>` (NUDITY, SIMULATED_SEX, INTIMACY_SCENE, STUNTS_FIGHTS, FIREARMS_PROPS, MINORS_ON_SET, NIGHT_SHOOT, WATER, ANIMALS, GORE), or explicit none | yes | **Face** icons |
| intimacy_coordinator | Boolean? | if R08 | detail |
| stunt_or_armourer | Boolean? | if R09 | detail |
| guardian_present | Boolean? | if MINORS_ON_SET | detail |
| safety_measures | String? ≤400 | if any risk flag | detail |
| audition_mode | SELF_TAPE, VIDEO_CALL, STUDIO_OR_PUBLIC_WITH_OTHERS | required if any cast role | detail (Face icon on cast roles) |
| bring_friend_welcome | Boolean (forced true for PRIVATE_HOME) | no | **Face** chip |
| insurance, permits | YES, NO, NOT_YET, NA | no | detail |
| status | DRAFT, OPEN, CREWED, WRAPPED, CANCELLED (system) | system | Face footer |
| reschedule_count | Int (system) | system | **Face** if ≥2 |
| team_attached | derived from the owner + BOOKED matches | system | **Face** initials monograms + roles ("DP · Sound attached") |
| created_at, published_at, updated_at | Instant | system | Face footer ("Posted 2d ago") |

### 6.3 Project role (one card in the Gigs deck = one role)
| Field | Type | Req | Shown on card face? |
|---|---|---|---|
| id, project_id | UUID | system | no |
| role_code | RoleCode | yes | **Face** (headline) |
| count | Int 1-10 | yes | **Face** if >1 ("×2") |
| credit_title | String (default from taxonomy) | yes | detail + booking card |
| scope_includes, scope_excludes | `List<String>` chips (from taxonomy suggestions + custom ≤30 chars) | no | detail |
| step_up_friendly | Boolean | no | **Face** badge "STEP-UP OK" |
| level_wanted | Level? (null = any) | no | detail; FIRST_PROJECT or BUILDING_REEL also shows the **Face** badge "BEGINNERS WELCOME" |
| comp_type | PAID_DAY, PAID_FLAT, STIPEND, UNPAID | yes | **Face** (pay line, first) |
| amount_min, amount_max | Int? (minor units not needed; whole currency) | if not UNPAID | **Face** |
| currency | from city | system | **Face** |
| kit_fee_day | Int? | no | **Face** ("+ ₹1,000 kit") |
| payment_terms | ON_WRAP, HALF_UPFRONT, WITHIN_7_DAYS, WITHIN_15_DAYS, WITHIN_30_DAYS | if not UNPAID | **Face** ("paid on wrap") |
| perks | `Set<Perk>` (TRAVEL_COVERED, LODGING, FOOTAGE_BY_DATE, FINAL_FILM_BY_DATE, STEP_UP_CREDIT) | ≥2 if UNPAID (R02) | **Face** |
| footage_due_weeks | Int 1-26 | if FOOTAGE_BY_DATE | **Face** ("your scenes in 8 wks") |
| final_film_due_weeks | Int 1-52 | if FINAL_FILM_BY_DATE | detail |
| gear_policy | PRODUCTION_PROVIDES, BYO_WITH_KIT_FEE, BYO_NO_FEE, NA | required for CAMERA, SOUND, GE departments | **Face** chip |
| remote_ok | Boolean (default by taxonomy) | yes | **Face** ("Remote") |
| post_deadline | LocalDate? | required if remote_ok and dept ∈ {POST, MUSIC, WRITING} (this makes it a **post role**, §3.4) | **Face** ("Due 30 Jan"; music and sound design: "Lock 10 Jan → due 7 Feb · 4 wks") |
| role_details | `Map<String,String>` from the per-family form (Appendix A.2) | no | detail |
| screening_question | String? ≤140 | no | Apply sheet |
| application_cap | Int 5-40 (default by scarcity) | system default, editable | **Face** footer ("6/15 applied") |
| applications_count, filled_count | Int (system) | system | Face footer |
| status | OPEN, FILLED, CLOSED (system) | system | Face footer |

### 6.4 Booking terms (JSON in `booking_cards.terms`, rendered in plain language)
| Field | Type | Req | Notes |
|---|---|---|---|
| role_code, credit_title | | yes | "Credit as: Sound Recordist" |
| dates | `List<LocalDate>` | on-set roles | Exact shoot days (empty for post roles) |
| delivery_date | LocalDate | post roles | Copied from `post_deadline`, editable. "Deliver by 7 Feb" |
| picture_lock_date | LocalDate? | music and sound design | Copied from the project. "Picture lock 10 Jan" |
| revision_rounds | Int? | no (post roles) | From the editor/composer role details. "2 revision rounds included" |
| call_window | String ≤40 | no (on-set roles) | "~7:00-17:00" |
| comp_type, amount, currency, kit_fee_day | | yes | Copied from the role and editable |
| payment_terms, pay_by_date | | if paid | On-set: pay_by_date = last shoot day + terms (on wrap = same day). Post roles: delivery_date + terms ("paid within 7 days of delivery") |
| perks, meals, transport, travel_allowance_per_day | | yes (meals and transport on-set only) | Copied from the project and role |
| deliverables | `List<{kind DELIVERY / FOOTAGE / FINAL_FILM / CREDIT, due_date, owed_by}>` | auto | On-set: FOOTAGE due = last day + footage_due_weeks. Post roles: DELIVERY (owed by the collaborator, due delivery_date), no FOOTAGE |
| footage_definition | String (fixed) | auto | "Approved clips of your scenes, graded, for your reel. Not raw footage." ([1khzk7e](https://www.reddit.com/r/Filmmakers/comments/1khzk7e/)) |
| rights_note | String (fixed default, editable) | auto | "Production owns the film. You may use approved clips in your reel." |
| notes | String ≤280 | no | |
| payer_id, payer_name | | system | "Payer: Rohan K." |
| notice_pledge | fixed | system | On-set: "If you can't make it, tell the production at least 72 hours before." Post roles: "If you can't deliver by the date, tell the production at least 7 days before." The production's side is shown too: "If the production releases you or cancels with less than 72 hours' notice, it shows on their record." |

### 6.5 Card faces (exact layout)
**Role Card (Gigs deck), portrait, 20dp corners, top to bottom:**
1. **Poster band** (2.39:1 letterbox): lookbook YouTube thumbnail, or a generated gradient poster with the title in display type, light-leak overlay, 4% grain. Top-left chip: `SHORT · 12 MIN`. Top-right chip: `4 KM` or `REMOTE`.
2. **Role label** (Barlow caps, Tungsten): `SOUND MIXER ×1` + badges `STEP-UP OK` / `BEGINNERS WELCOME`.
3. **Project title** (titleL).
4. **Pay line** (Inter 600; Greenlit for PAID): `₹2,500/day + ₹1,000 kit · paid on wrap` / `Stipend ₹800/day · 2 meals · TA ₹300` / `Unpaid · 2 meals · your scenes in 6 wks · step-up credit`.
5. **When and where** (mono): `SAT 14 – SUN 15 NOV · 2 DAYS · ≤10H · LOCKED` and `Jayanagar, Bengaluru · Kannada/English`. Post roles: `REMOTE · LOCK 10 JAN → DUE 7 FEB · 4 WKS`.
6. **Credibility row**: ID verified, "3 shoots done", "Footage 5/5" or "Kept bookings 6/6" (whichever is weaker, so the card never hides a bad number) or "New on Calltime", "Script locked", team monograms "DP · Actor attached".
7. **Logline** (2 lines).
8. **Flags row** (only if any): content icons, "Night shoot", "Private home · friend welcome".
9. **Footer** (muted): `Open · 6/15 applied · replies in ~1 day`.

**Person Card (Crew-up deck):**
1. Reel thumbnail band (2.39:1). For actors in Applicants only: the headshot link may be shown as a portrait inset (left, 30% width).
2. `SOUND MIXER · CREDITED INDIE` + step-up chip `→ DP` if applicable.
3. Name ("Dana R.") + badges.
4. Fit line: `3/3 days · 6 km · Kit ₹1,500/day · rate in budget` ("rate in budget" comes from the server-computed `rate_fit`, never the person's private rate; for unpaid roles: "Open to unpaid · conditions met").
5. Reason chips from the ranker (top 3): "Showed up 9/9", "Speaks Kannada", "Has boom op".
6. Their one-line note (if applied) and screening answer (truncated).
7. Footer: `Applied 3h ago` / `Scout · not applied`.

---

## 7. Matching and ranking

All logic lives in pure Kotlin in `domain/MatchEngine.kt` and `domain/ApplicantRanker.kt`, unit-tested, and runs on the device in both modes. In Supabase mode the candidate set comes from the RPC `discover_roles` (§9.4). In demo mode it comes from the in-memory store.

**Notation:** U = viewing user, R = role, P = R's project, d = haversine distance in km between U.area and P.area centroids.
- `remote(R)` = R.remote_ok && U.remote_ok.
- `days(P)` = the list of shoot dates.
- `avail(U, days)` = the count of days that are not in U.busy_dates and that match U.availability_pattern (WEEKENDS_ONLY = Sat/Sun; EVENINGS_AND_WEEKENDS = Sat/Sun, plus weekdays when P.hours_cap ≤ 5; WEEKDAYS_ONLY = Mon-Fri).

### 7.1 Stage 0: hard gates (all must pass, or the card is never shown)
| Gate | Rule |
|---|---|
| G1 Role | R.role_code ∈ U.primary ∪ U.secondary, or (R.role_code ∈ U.step_up and R.step_up_friendly) |
| G2 Place | remote(R), or d ≤ U.radius, or (P.transport ≠ NONE and U.travel_if_covered and (P.shoot_days = 1 or P.lodging) and d ≤ 3 × U.radius) |
| G3 Time | On-set: avail ≥ 1 (cast roles: avail = all days). Remote: R.post_deadline ≥ today + 7 |
| G4 Money | PAID_ONLY: comp ∈ {PAID_DAY, PAID_FLAT} and dayRate(R) ≥ U.min_day_rate (if set). PAID_AND_SELECTIVE: paid, or U.unpaid_must_haves ⊆ perks(R, P). OPEN_TO_UNPAID: pass |
| G5 Content | P.content_flags ∩ U.content_wont_do = ∅ |
| G6 Status | R.status = OPEN, R.applications_count < R.application_cap, owner active ≤14 days, not blocked in either direction, U ≠ owner, U hasn't swiped R; on-set roles: start date not passed (post roles are covered by G3) |
| G7 Language | If dept(R) ∈ {CAST, DIRECTION, WRITING} and P.languages ≠ ∅: P.languages ∩ U.languages ≠ ∅ |

`perks(R, P)` = R.perks plus derived perks: MEALS (P.meals ≥ TWO_PLUS_MEALS), TRAVEL_COVERED (P.transport ∈ {MILEAGE_OR_TA_PAID, PRODUCTION_VAN}), LODGING (P.lodging), SHORT_COMMITMENT (P.shoot_days ≤ 3), SCRIPT_READY (P.script_status = LOCKED). `Meals` is ordered NONE < CRAFTY_ONLY < ONE_MEAL < TWO_PLUS_MEALS < CATERED. `dayRate(R)` = amount_max for PAID_DAY and STIPEND, or amount_max / shoot_days for on-set PAID_FLAT. **Post roles paid flat have no day rate**: G4 checks only that they are paid, the comp score uses its no-min-rate base (16), and the card shows the flat fee.

### 7.2 Stage 1: fit score (0-100)
```
role   (0-25): primary 25 | step-up 22 if STEP_UP ∈ goals else 15 | secondary: proficiency 3/2/1 → 18/12/6
comp   (0-20):
   PAID_*  : base = U.min_day_rate? 20 × min(1, dayRate/U.min_day_rate) : 16 ; +2 if terms ∈ {ON_WRAP, HALF_UPFRONT}; cap 20
   STIPEND : 8 + 2 × |perks| ; cap 16
   UNPAID  : 3 × |perks ∩ {MEALS, TRAVEL_COVERED, LODGING, FOOTAGE_BY_DATE(≤8 wks), STEP_UP_CREDIT, SHORT_COMMITMENT, SCRIPT_READY}| ; cap 15
   −5 if UNPAID and project has ≥1 paid role (pay-parity penalty)
logist (0-15): remote → 12 ; else 15 × (1 − min(d/U.radius, 1))^0.7 ; +2 if transport ≠ NONE ; −3 if hours_cap > 12 ; clamp 0..15
sched  (0-10): on-set → 10 × avail/shoot_days ; +2 if LOCKED (cap 10) ; −3 if TENTATIVE and start < today+7
               remote → 10 if post_deadline ≥ today+21 else 6
cred   (0-15): owner.id_verified 3 + min(5, owner.shoots_completed) + 4 × owner.deliverable_rate (2 if n<3)
               + 2 if script LOCKED + 1 if team_attached ≥ 2
               deliverable_rate = mean of the owner's non-null ratios: Paid on time, Footage delivered, Kept bookings
creat  (0-10): 5 × jaccard(P.genres, U.genres_pref) (2.5 if U.genres_pref empty)
               + 3 if ambition fits goals (FESTIVAL↔FESTIVAL_CREDIT, PORTFOLIO↔REEL_FOOTAGE, CLIENT_PAID↔PAID_WORK, FOR_FUN↔FUN_COMMUNITY)
               + 2 if P.format ∈ U.formats_pref (1 if prefs empty)
level  (0-5) : level_wanted null or == U.level → 5 ; wanted one above U: 4 if STEP_UP ∈ goals else 2 ;
               wanted one below U → 3 ; ≥2 apart → 0
fit = role + comp + logist + sched + cred + creat + level            // 0..100
```

### 7.3 Stage 2: multipliers and deck assembly
```
m_resp   = owner.closure_rate_14d (n≥5): <0.5 → 0.70 ; ≥0.9 → 1.05 ; else 1.0 (new owners 1.0)
m_fresh  = published ≤72h → 1.05 ; owner inactive >7d → 0.80 ; else 1.0
m_urgent = start ≤ today+10 and avail == shoot_days → 1.10 ; else 1.0
score    = fit × m_resp × m_fresh × m_urgent
```
Deck assembly (`MatchEngine.buildDeck(user, candidates, now): List<DeckCard>`):
1. Apply gates, score, and sort descending. Keep the top 60.
2. **Diversity:** at most 2 roles from the same project within the first 10 cards.
3. **Exploration (≈15%):** every 7th slot is filled by the best-scoring role whose owner has `shoots_completed = 0`, so new owners aren't locked out.
4. **Small-market fallback:** if fewer than 10 cards pass the gates, rerun G2 with radius × 1.5 and include all remote roles. Those cards carry the `outsideRadius = true` label "Outside your radius".
5. Saved cards never reappear in the deck (they live in "Saved").

### 7.4 Owner view: ApplicantRanker (0-100)
```
roleTier (25): primary + level==wanted 25 | primary, level off by 1 20 | step-up 18 | secondary 15
avail    (15): 15 × avail/shoot_days   (remote roles: 15)
distance (10): remote 10 ; else 10 × (1 − min(d / 25km, 1))
rate     (10): 10 × rate_fit ; rate_fit is computed server-side (LIVE) or by DemoRepo (DEMO) as
               min(1, amount_max / min_day_rate), 1 if no private rate is set, 1 for unpaid roles (they applied).
               The owner's device never receives min_day_rate itself
reel     (10): role-tagged REEL link 10 | any REEL 5 | any other work link (PORTFOLIO, phone-shot short, theatre) 4 | none 0
               beginner floor: if R.level_wanted ∈ {FIRST_PROJECT, BUILDING_REEL} or the owner's level is FIRST_PROJECT,
               the reel score is at least 5, so a missing reel doesn't sink a newcomer on a beginner project
reliab   (15): n≥3 → 10 × show_up_rate + (resp_median<24h 5 | <72h 3 | else 0) ; n<3 → 8
answer   (5) : non-empty screening answer, not a near-duplicate (normalized Levenshtein ≥0.9) of this user's other answers → 5
trust    (10): id_verified 4 + phone_verified 2 + min(4, confirmed_credits)
```
Sort descending; ties go to the earlier application. Show the top 3 contributing factors as reason chips. **Scout** uses the same scorer on non-applicants who pass the gates in reverse (the person would pass G1-G7 for this role), and excludes anyone who passed on the role or was asked in the last 30 days. Scout is **never run for cast roles**, and the reverse G4 (money) and G5 (content) checks run server-side in `scout_candidates`, because they need `profile_private.min_day_rate` and `content_wont_do`, which never leave the server.

### 7.5 Worked example (put this in the unit tests)
Meera (actor, BUILDING_REEL, Versova, radius 15, OPEN_TO_UNPAID, goals REEL_FOOTAGE, prefers DRAMA) sees "Last Local": lead actor, UNPAID, footage in 6 weeks, 2 meals, TA, Andheri (3 km), 2 Saturday days, LOCKED, owner with 3 shoots, footage 3/3 and kept bookings 3/3 (not ID-verified; no paid roles, so Paid on time is null), script LOCKED, no paid roles on the project, no team attached yet, SHORT, FESTIVAL ambition, drama. Meera's `formats_pref` is empty.
- role 25
- comp: 3 × 5 perks (MEALS, TRAVEL_COVERED via TA, FOOTAGE≤8, SHORT, SCRIPT) = 15
- logistics: 15 × (1 − 3/15)^0.7 = 12.8, +2 for transport → 14.8
- schedule: 10 + 2, capped at 10 → 10
- credibility: 0 + 3 + 4 + 2 + 0 = 9
- creative: 5 × 1.0 + 0 (FESTIVAL vs REEL_FOOTAGE) + 1 → 6
- level: 5

Fit ≈ **84.8** before multipliers.

---

## 8. Screen-by-screen spec

### 8.0 Navigation
- **Root graph** (navigation-compose, `@Serializable` routes): `Splash → Welcome → (SignIn → Onboarding) → Main`. Guests go `Welcome → Main(guest)`.
- **Bottom nav (4 tabs):** **Discover** (cards icon) · **Projects** (clapperboard) · **Inbox** (chat bubble, with unread badge) · **Me** (person).
- **Full-screen destinations:** RoleDetail, Apply (bottom sheet), PersonDetail, PostProject wizard, ProjectDashboard, CrewUp, Chat, BookingCard, ShootDayEditor, ShootDayView, Wrap, Feedback (sheet), EditProfile, SafetyCenter, Settings, Report (sheet), TrustPages.
- **Deep links:** `calltime://r/{roleId}`, `https://calltime.app/r/{roleId}` → RoleDetail; `calltime://m/{matchId}` → Chat (from notifications).
- **Demo ribbon:** a thin Tungsten "DEMO" tag at the top-right of every top app bar while in demo mode.

### 8.1 Screens
Every screen has three states: Loading (skeleton shimmer in surface colours), Error ("Couldn't load. Retry" + Retry button), and Empty (as listed).

**S01 Splash.** Purpose: choose the mode (demo or Supabase) and restore the session. Components: logo (slate mark), progress. Navigation: session plus profile present → Main; session but no profile → Onboarding; otherwise → Welcome.

**S02 Welcome.** Purpose: value and trust in five seconds.
- Components: three value rows ("Pay, dates and perks on every card" · "Booked, not just matched" · "Free forever for cast and crew. No fees to apply"); buttons [Look around] (guest) and [Get started]; links "How we make money", "Community rules".
- Demo mode replaces [Get started] with **"Try a demo as…"** and 4 persona tiles (Meera, actor · Rohan, director · Kabir, gaffer→DP · Dana, sound mixer) plus "Build my own profile".

**S03 Sign in.** Email field → [Send code] → 6-digit OTP field (auto-advance), with a phone tab if enabled. Required checkbox "I'm 18 or older", plus links to rules and privacy. Errors: wrong code, expired code (resend after 30 s).

**S04 Onboarding (3 steps; top progress bar of 3 segments).**
1. *What do you do?* Role chips grouped by department (search field). Tap once = primary (max 1), tap again = secondary, long-press = "step up to" (max 2). Below: level segmented control (4 options) with one-line descriptions.
2. *Where?* City dropdown (live cities first, then **early cities** labelled "early: few people yet, remote roles included"; a "My city isn't listed" link asks for the nearest listed city and logs the request) → area chips → radius slider 2-50 km with live "≈ N people and M projects within X km" → Remote OK toggle (default on for POST/MUSIC/WRITING) → languages chips.
3. *How you work* (skippable): comp mode (3 cards); if not PAID_ONLY, "I'll do unpaid if…" perk checklist; availability pattern; private min day rate (optional, "never shown"); one work link (URL field with kind auto-detected). [Finish] → Discover.

**S05 Discover: Gigs deck.**
- **Top bar:** density header ("Bengaluru · 128 people · 19 open projects"; early cities: "Indore · early city · 3 people · 1 open project · + remote roles"), [Filters] icon, segmented control **Gigs | Crew up** (Crew up only if the user owns OPEN roles), demo ribbon.
- **Body:** card stack (top 3 rendered; §12 motion).
- **Bottom row:** [Pass ✕] [Save ⌑] [Interested ✓] plus a likes counter "18 left today" and an Undo chip (last swipe, 5 s).
- **Card tap:** S06.
- **Empty states:**
  - (a) Nothing passes the gates: "Nothing in range yet." Buttons: [Widen radius], [Include remote roles], [Edit what I'll do unpaid], [Post your own project], plus honest numbers.
  - (b) Daily cap reached: "You've sent 25 today. Fewer, better applications get answered. Back tomorrow."
  - (c) Deck exhausted: "You've seen everything near you. We'll ping you when new roles land." [See saved]
- **Guest:** any action opens the sign-in sheet ("Free forever. No fees to apply.").

**S06 Role detail.** Scroll sections in this order:
1. Header: poster, role, title, pay line.
2. What you get: perks with dates.
3. Dates and place: shoot vs story location, days, hours cap, certainty, transport, lodging.
4. The role: duties line from the taxonomy, includes/excludes, gear policy, credit title, role details.
5. The project: logline, format, runtime, script status, stage, ambition, lookbook link, "X of Y roles paid".
6. Team attached.
7. Who's posting: owner mini-profile, badges, track record chips, response time.
8. Safety and content: flags, safety measures, audition mode, bring-a-friend.
9. Status.

Sticky bottom: [Pass] [Interested]. Overflow: Share, Report. The first time a cast role is opened, the "What normal looks like" tip card appears above section 8.

**S07 Apply sheet (modal bottom sheet).**
- Fields: note (≤140, counter); reel picker (role-tagged first; "Add a link" inline); screening question answer (required if present); "I'll bring my kit" toggle (BYO roles); for UNPAID, the summary "You're applying to an unpaid role: you get …".
- [Send] → toast "Sent. Every applicant gets an answer within 14 days." Then the notification-permission prompt (first time only).
- Errors: role filled meanwhile ("Just filled. Here's a similar one" → next card), cap reached.

**S08 Filters sheet** (7 filters in the MVP; formats, script locked, meals and a second rate field are Later because the gates and profile already cover them). Paid only · distance slider · remote only · dates (this week / 30 days / any) · ID-verified owners only · footage by a date · languages. [Reset] [Apply]. Note at the bottom: "Your hard limits (content, pay conditions) live in your profile." [Edit]

**S09 Crew up deck.**
- **Top:** role selector chips for the user's open roles ("Sound mixer · 3 new", "Lead actor · 10"), sub-tabs **Applicants | Scout** (default per scarcity, §3.5). For a cast role the Scout tab is absent and a one-line note explains: "Actors apply to you. Calltime doesn't let anyone browse actors."
- **Card:** Person Card (§6.5).
- **Actions:** [Not this time ✕] (left) · [Backup ⌑] (up) · [Let's talk ✓] (right). Scout: [Skip] · [Send ask].
- **Ask composer:** pre-filled Ask card preview plus an optional note ≤140, with the "10 asks left today" counter.
- **Match moment:** "IN TALKS" slate overlay (§12.4) → [Open chat] [Keep swiping].
- **Empty:**
  - Applicants: "No applicants yet." [Share this role] [Open Scout]
  - Scout: "No one nearby fits yet." [Widen radius] [Make it remote] [Share to WhatsApp]

**S10 Person detail.** Header (name, role, level, area, distance, badges, "Active today"); reels (YouTube thumbnails, tap opens externally); roles including step-up; track record chips with an "i" explainer; confirmed credits, then self-listed credits; gear and tools; languages; availability summary; taste (5 films); bio; affiliations. Owner-in-context: their application note and answer, plus the same actions as S09. Overflow: Report, Block.

**S11 Post project wizard** (5 steps + review; [Save draft] always available; live **card preview** button opens the Role Card exactly as it will appear). Target: a 3-role on-set project in ≤4 minutes. Every step shows its required fields first and folds the rest under **"More details"** (collapsed by default). Defaults are pre-filled wherever the research gives a sane one.
1. *Basics:* title, logline, format, runtime, languages, script status. More details: genres, stage (default PRE), ambition (default PORTFOLIO), readable on request, lookbook link, honest zero-budget toggle. Choosing stage POST asks "Already shot? Post-only project" and, if yes, step 2 shrinks to city/area only.
2. *When and where:* city/area, location type, date range + shoot days, certainty, hours cap (default 10), meals, transport (+ TA amount), lodging (if >1 day). More details: story setting. Picture-lock date appears here when a music or sound-design role exists (R20).
3. *Roles:* first, a **"Pay for all roles"** row (comp type, payment terms, unpaid perks and footage weeks) that every new role inherits; then role rows with [Add role] and **[Duplicate]**. Role editor, required: taxonomy picker, count (default 1), comp type and amount (inherited, editable), gear policy (camera, sound, G&E only), remote + post deadline (post roles). More details: credit title (default from taxonomy), scope chips, step-up friendly, level wanted, kit fee, role details form (Appendix A.2), screening question, cap (default by scarcity). Scarcity nudges appear inline.
4. *Safety:* content flags grid with "None of these"; conditional follow-ups; audition mode (if cast roles); bring a friend. More details: insurance and permits.
5. *Review:* rules checklist (green ticks; red items link back to the field), one-time nudges (R21, R22), preview carousel of each role card. Log `publish_blocked{rule}` and the time from wizard open to publish.

[Publish] → status OPEN → **Share sheet** with the pre-built text:
```
CREW CALL · Sound Mixer · "Filter Coffee, 4 AM" (Kannada/English short, 12 min)
Sat 14 – Sun 15 Nov · 2 days · ≤10h · Jayanagar, Bengaluru
Pay: ₹2,500/day + ₹1,000 kit · paid on wrap · 2 meals
Apply free on Calltime: <SHARE_BASE_URL>/r/<roleId>
```
When `SHARE_BASE_URL` is blank, the last line is "Apply free on Calltime (Google Play): https://play.google.com/store/apps/details?id=com.tff.app". Post roles replace the dates line with "Remote · picture lock 10 Jan · deliver by 7 Feb".

**S12 Projects tab.**
- Segmented **Mine | Joined**.
- **Mine:** project cards with the status pipeline ("Crewing · 3/5 filled", next shoot date, "2 wrap items overdue" in Safelight). FAB [Post a project].
- **Joined:** projects where I'm booked, with the next call time in mono type.
- **Empty (Mine):** "Got a script? Post it. People say yes to a plan, not a vibe." [Post a project]
- **Empty (Joined):** "Your booked shoots show up here." [Find gigs]

**S13 Project dashboard.**
- Header: title, status chip, [Share], [Edit]. Tabs:
  1. **Roles:** per role, status, applied/cap, filled/count; actions [Crew up], [Edit], [Close].
  2. **Team:** booked people with their role, ack status of the next day sheet, wrap progress. "Backups" collapsible per role with [Offer slot] when a slot is open.
  3. **Shoot days:** list + [Add shoot day] → S16E. Hidden on post-only projects.
  4. **Wrap:** visible after the first shoot day, or as soon as a post role is booked; per person checklist (S17). Post roles show their DELIVERY item at the top.
  5. **Settings:** reschedule (date picker → change broadcast), cancel project (reason → notifies all), delete draft.
- **Collaborator view (Joined):** my booking card (current version), day sheets, my wrap items, team list (names and roles only), [Withdraw] (S16W).

**S14 Inbox.**
- Sections:
  1. **Asks for you** (Ask cards: [Yes, let's talk] [Not this time ▾ reasons: Dates don't work · Too far · Not my kind of project · Paid work only right now]).
  2. **Conversations:** match rows with state chip (IN TALKS / OFFER OUT / BOOKED / WRAPPED), last message, unread dot.
  3. **Updates:** application closures, role filled, reminders, overdue wrap items.
- Empty: "When someone wants to talk, it shows up here. Every application gets an answer."

**S15 Chat.**
- **Top bar:** person name + role on project + state chip; overflow: View project, View profile, Report, Block, Withdraw/Release.
- **Pinned bar:** booking card status with CTA. Owner: [Send booking card] / [Edit terms], plus [Request audition] on cast roles. Collaborator: [Review offer]. After BOOKED: "Booked · next call Sat 07:30" → S16V (post roles: "Booked · deliver by 7 Feb" → S17).
- **Audition request sheet (owner, cast roles, F20):** the mode is fixed to the listing's `audition_mode` and shown read-only. SELF_TAPE: sides link (URL) + due date. VIDEO_CALL: date + time (+ meeting link, allowed here only). STUDIO_OR_PUBLIC_WITH_OTHERS: venue name, address, date + time (time picker limited to 08:00-21:00), "Also present" (≥1 role, e.g. "Casting assistant, DP"). [Send] posts an AUDITION card. The actor sees the card with the fixed safety lines and [I'll do it] · [Can't make that time] · [Report]; self-tape cards also show [Send my tape] (link field).
- **Messages:** bubbles; system messages centred in muted text; safety banners above flagged bubbles (§5.2); masked contact tokens.
- **Composer:** text field, quick-reply chips row, send. Inline sender-side warning when a draft matches MONEY or CONTACT patterns ("Contact details stay hidden until you're booked").
- Empty: system terms summary as the first message.

**S16 Booking card.**
- **Owner editor:** pre-filled fields (§6.4), grouped as Role & credit · Dates & call window · Pay · Perks & logistics · Deliverables · Notes. A **plain-language preview** at the top re-renders live, for example: "You're booked as **Sound Mixer** (credit: 'Sound Recordist') on *Filter Coffee, 4 AM* · Sat 14 & Sun 15 Nov · call ~07:00, wrap by 17:00 · ₹2,500/day + ₹1,000 kit · paid on wrap · 2 meals · TA ₹300/day · approved clips of your scenes by 10 Jan · Payer: Rohan K." [Send].
- **Collaborator view:** the preview plus [Accept & book] (requires ticking the 72h notice pledge) · [Ask to change] (returns to chat with "Could we change…" prefilled) · [Decline].
- **Versions:** "v2 · changed: dates, amount" with the changed fields highlighted in Tungsten and the old value struck through.
- **Post-role variant:** the Dates & call window group becomes "Delivery": delivery date, picture-lock date (music and sound design), revision rounds. Preview example: "You're booked as **Composer** (credit: 'Music by') on *Filter Coffee, 4 AM* · remote · picture lock 10 Jan · deliver by 7 Feb · 2 revision rounds · ₹12,000 flat · paid within 7 days of delivery · Payer: Rohan K."
- **BOOKED overlay** (§12.4).
- **S16W Withdraw sheet:** reason chips (Dates no longer work · Paid work came up · Health/family · Terms changed · Safety concern · Other), computed notice ("4 days' notice: this won't count against you"; post roles measure notice against the delivery date with a 7-day threshold), [Withdraw]. "Safety concern" offers [Report] too.
- **Owner Release sheet:** reason chips, computed notice, and an honest line when it is under 72h: "Less than 72 hours' notice. This will show in your Kept bookings."

**S16E Shoot day editor (owner).** Date (from booking dates), general call, wrap-by, per-person call-time list (defaults to general call; staggered with the time picker), address (multiline) + "Test maps link", parking/transit, nearest hospital, meals plan, notes ≤500. [Publish] / [Update] (shows "Notify 5 people?"). After publishing: ack list ("4/5 got it") with [Nudge] on the missing person.

**S16V Shoot day view (team).** Big mono call time ("YOUR CALL 07:30"), date, address with [Open in Maps], parking, hospital, meals, wrap-by, notes, team list (role + first name; no phone numbers). Buttons: **[Got it]** · [Add to calendar] · [Share with trusted contact] · (owner only) [Share to WhatsApp]. A T-48h banner asks "Still coming?" [Yes] [Can't make it → withdraw flow].

WhatsApp text template:
```
CALL SHEET · "Filter Coffee, 4 AM" · Day 1 of 2 · Sat 14 Nov
General call 07:00 · Wrap by 17:00
Location: <address> · Map: https://maps.google.com/?q=<urlencoded address>
Parking: <...> · Nearest hospital: <...>
Meals: breakfast + lunch
Your individual call time is in Calltime: https://calltime.app/d/<shootDayId>
```

**S17 Wrap.**
- Per collaborator (owner) or my own items (collaborator). Each item row: kind icon, due date (mono), state chip (OPEN, DONE, CONFIRMED, NOT_RECEIVED, OVERDUE).
- Owner actions: [Mark done] (FOOTAGE/FINAL_FILM need a link; PAID takes an optional "paid via UPI/cash/bank" note). On a DELIVERY item: [Got it] / [Not received].
- Collaborator actions: [Got it] / [Not received]. On a DELIVERY item (post roles): [Mark delivered] (link required). If the owner doesn't answer within 7 days, it is auto-confirmed and the PAID item's clock keeps running.
- Banner when the feedback is unlocked: [Give private feedback].
- Empty: "Nothing owed. Clean wrap."

**S18 Feedback sheet.**
- Header: "Private. Others only see totals of facts, once someone has 3+ shoots."
- **Collaborator → production:** Paid as agreed? (On time / Late / No / Not a paid role) · Delivered what was promised? (Yes / Partly / Not yet / No) · Felt safe and respected? (Yes / No → offers a private report) · Work with them again? (Yes / No).
- **Collaborator → production** also asks: Kept the booking as agreed? (Yes / Cut or changed late / Cancelled late). This cross-checks the system's Kept-bookings count.
- **Owner → collaborator (on-set):** Showed up on time? (Yes / Late / No-show) · Prepared? (Yes / Somewhat / No) · Work with them again? (Yes / No).
- **Owner → collaborator (post roles):** Delivered on time? (Yes / Late / Didn't deliver) · Revisions handled as agreed? (Yes / Somewhat / No) · Work with them again? (Yes / No).
- [Submit] → "Thanks. Answers unlock when you both submit, or in 14 days."

**S19 Me (my profile).** Same layout as S10 with edit pencils per section, plus:
- a "Track record" explainer;
- **Keep list** (people I'd work with again) with [Invite to a project] → choose project and role → Ask card (the reverse G4/G5 check runs first; if it fails, say why instead of sending; this is the only way to invite an actor who hasn't applied);
- **Saved** roles;
- entry points to Safety Center and Settings.

**S20 Edit profile.** Sectioned forms mirroring onboarding plus: busy dates calendar, gear, tools, taste films, bio, affiliations, work links manager (reorder, role tag, kind; "phone-shot short" and "theatre" are valid kinds of PORTFOLIO link), private min rate. No photo upload in the MVP.

**S21 Safety Center.** Community rules (6 short rules) · What normal looks like · My reports (status list) · Blocked people (unblock) · Emergency numbers for my country · "How we handle reports".

**S22 Settings.**
- Account (email/phone, sign out); notifications toggles.
- **Data:** [Export my data] (JSON via the share sheet), [Delete account] (double confirm).
- Trust pages (How we make money · No gen-AI pledge · Privacy · Licences); version.
- **Demo controls** (demo only): persona switcher · [Reset demo] · simulation speed (1×, 3×, instant) · clock: [+1 day] [+7 days] [+30 days] [Jump past next shoot] [Jump past wrap +30d] · [Inject flagged message example] · [Simulate a crew withdrawal] · current demo date shown in mono.

**S23 Report sheet.** Reasons (§5.5) → details → [Send] → confirmation with status link.

**S24 Trust pages.** Static markdown-like composables (no WebView).

---

## 9. Data model (Supabase)

### 9.1 Conventions
- Migration: `supabase/migrations/20261002000000_init.sql`. Seed reference data (cities, areas) goes in `supabase/seed.sql`. **Never seed demo people into Supabase.**
- Enums are `text` with `check` constraints (easier to evolve than Postgres enums). IDs are `uuid default gen_random_uuid()`, except reference tables (text ids).
- All tables have RLS **enabled**. State transitions happen **only** through `security definer` RPCs that check `auth.uid()`; direct table writes are limited to the owner's own simple rows.

### 9.2 Tables (key columns)
```sql
-- Reference
cities(id text pk, name, country text check in ('IN','US','GB'), currency text, lat float8, lng float8,
       paid_floor_day int, emergency_number text, is_live bool default false)
areas(id text pk, city_id text fk cities, name, lat float8, lng float8)

-- People
profiles(id uuid pk fk auth.users on delete cascade, display_name text check len 2..40, pronouns text,
  primary_role text not null, level text not null, city_id fk, area_id fk,
  travel_radius_km int default 15 check 2..150, travel_if_covered bool default false, remote_ok bool default false,
  languages text[] not null, availability_pattern text not null, comp_mode text not null,
  unpaid_must_haves text[] default '{}', goals text[] default '{}', content_wont_do text[] default '{}',
  gear text[] default '{}', kit_fee_day int, tools text[] default '{}', taste_films text[] check cardinality<=5,
  genres_pref text[], formats_pref text[], bio text check len<=280, affiliations text[],
  email_verified bool default false, phone_verified bool default false, id_verified bool default false,
  adult_confirmed bool not null default false, created_at, last_active_at)       -- no avatar column in the MVP
profile_private(id uuid pk fk profiles on delete cascade, min_day_rate int, currency text)       -- owner-only
profile_roles(profile_id fk, role_code text, kind text check in ('PRIMARY','SECONDARY','STEP_UP'),
  proficiency int check 1..3, pk(profile_id, role_code, kind))
work_links(id pk, profile_id fk, role_code text, kind text check in ('REEL','CREDIT','HEADSHOT','PORTFOLIO','IMDB','OTHER'),
  url text not null, title text, year int, sort int default 0)
busy_dates(profile_id fk, day date, pk(profile_id, day))

-- Projects
projects(id pk, owner_id fk profiles, payer_id fk profiles, title, logline, format, runtime_min int, page_count int,
  genres text[], languages text[], stage, script_status, script_readable bool, lookbook_url, ambition_tier,
  honest_zero_budget bool, city_id fk, area_id fk, shoot_location_type, exact_address text, story_setting,
  shoot_start date null, shoot_end date null, shoot_days int null, picture_lock_date date null,   -- null on post-only projects
  date_certainty, hours_cap int, meals, transport,
  travel_allowance_per_day int, lodging bool, content_flags text[] not null, content_none_confirmed bool,
  intimacy_coordinator bool, stunt_or_armourer bool, guardian_present bool, safety_measures text,
  audition_mode text, bring_friend_welcome bool, insurance text, permits text,
  status text check in ('DRAFT','OPEN','CREWED','WRAPPED','CANCELLED') default 'DRAFT',
  reschedule_count int default 0, created_at, published_at, updated_at)
project_roles(id pk, project_id fk on delete cascade, role_code, count int check 1..10, credit_title,
  scope_includes text[], scope_excludes text[], step_up_friendly bool, level_wanted text,
  comp_type text check in ('PAID_DAY','PAID_FLAT','STIPEND','UNPAID'), amount_min int, amount_max int, currency,
  kit_fee_day int, payment_terms text, perks text[], footage_due_weeks int, final_film_due_weeks int,
  gear_policy text, remote_ok bool, post_deadline date, role_details jsonb default '{}',
  screening_question text check len<=140, application_cap int, applications_count int default 0,
  filled_count int default 0, status text check in ('OPEN','FILLED','CLOSED') default 'OPEN')

-- Matching
swipes(id pk, actor_id fk, target_kind text check in ('ROLE','PROFILE'), target_id uuid, context_role_id uuid,
  direction text check in ('RIGHT','LEFT','SAVE','BACKUP'), created_at)
  -- unique INDEX on (actor_id, target_kind, target_id, coalesce(context_role_id, '00000000-0000-0000-0000-000000000000'))
applications(id pk, role_id fk, applicant_id fk, origin text check in ('APPLIED','INVITED'),
  note text check len<=140, work_link_id fk, screening_answer text check len<=300, kit_offered bool,
  status text check in ('PENDING','MATCHED','BACKUP','DECLINED','WITHDRAWN','CLOSED','EXPIRED'),
  backup_rank int, decline_reason text, created_at, decided_at, unique(role_id, applicant_id))
matches(id pk, application_id fk unique, project_id fk, role_id fk, owner_id fk, collaborator_id fk,
  state text check in ('TALKING','OFFERED','BOOKED','WRAPPED','WITHDRAWN','RELEASED','NO_SHOW','CLOSED'),
  change_pending bool default false, contacts_shared_by_owner bool default false,
  contacts_shared_by_collab bool default false, withdraw_reason text, notice_hours int,
  created_at, booked_at, ended_at)
messages(id pk, match_id fk, sender_id fk null (null = system), kind text check in ('TEXT','SYSTEM','BOOKING','AUDITION'),
  body text check len<=2000, payload jsonb null,          -- AUDITION: {mode, sides_url, due, at, venue, address, also_present[], response}
  flags text[] default '{}', created_at)
booking_cards(id pk, match_id fk, version int, terms jsonb not null,
  status text check in ('SENT','ACCEPTED','DECLINED','SUPERSEDED','CANCELLED'),
  sent_by fk, sent_at, responded_at, unique(match_id, version))

-- Shoot & wrap
shoot_days(id pk, project_id fk, day date, general_call time, wrap_by time, address text, maps_url text,
  parking_note, hospital_note, meals_plan, notes text check len<=500, version int default 1,
  published_at, cancelled_at)
call_times(shoot_day_id fk, profile_id fk, call_time time, ack_at timestamptz, attendance text
  check in ('CONFIRMED','CANT_MAKE_IT','ATTENDED','NO_SHOW','DISPUTED'), pk(shoot_day_id, profile_id))
wrap_items(id pk, match_id fk, kind text check in ('PAID','FOOTAGE','FINAL_FILM','CREDIT','DELIVERY'), due_date date,
  owed_by text check in ('PRODUCTION','COLLABORATOR'),     -- DELIVERY is owed by the collaborator; all others by the production
  status text check in ('OPEN','DONE','CONFIRMED','NOT_RECEIVED'), link text, note text, marked_at, confirmed_at,
  auto_confirmed bool default false)
feedback(id pk, match_id fk, from_id fk, to_id fk, answers jsonb not null, private_note text,
  submitted_at, revealed_at, unique(match_id, from_id))

-- Safety & system
reports(id pk, reporter_id fk, target_kind text check in ('PROFILE','PROJECT','ROLE','MESSAGE','BOOKING'),
  target_id uuid, reason text, details text check len<=500,
  status text default 'RECEIVED' check in ('RECEIVED','REVIEWING','ACTION_TAKEN','NO_ACTION'), created_at, resolved_at)
blocks(blocker_id fk, blocked_id fk, created_at, pk(blocker_id, blocked_id))
notifications(id pk, user_id fk, kind text, payload jsonb, read_at, created_at)
events(id bigserial pk, user_id uuid, name text, props jsonb, created_at)      -- analytics, insert-only
```
**Indexes:** `project_roles(status, project_id)`, `projects(status, city_id, shoot_start)`, `applications(role_id, status)`, `applications(applicant_id, created_at)`, `messages(match_id, created_at)`, `notifications(user_id, read_at)`, `swipes(actor_id, target_kind)`.

**Views:**
- `profile_cards`: safe columns of `profiles` + `profile_roles` + reputation aggregates. **Excludes** `profile_private`, `content_wont_do` and HEADSHOT links. **Not granted to `anon` or `authenticated`**: it is read only inside the security-definer RPCs below, so nobody can page through it to build a scrape list.
- `reputation_stats`: security definer; computes the aggregates of F14 (including Kept bookings and Delivered on time) and returns ratio fields as NULL when n < 3.

### 9.3 RLS intent
| Table | SELECT | INSERT / UPDATE / DELETE |
|---|---|---|
| cities, areas | anon + authenticated | service_role only |
| profiles | Own row fully. **No general SELECT for anyone else.** Other people's cards come only from RPCs that check a relationship: `get_profile` (caller is a match counterpart, a teammate on a project, the owner of a role the person applied to or was asked for, or the person owns an OPEN project, since posters are public), `get_applicants`, `scout_candidates` (crew only), `discover_roles` (owner mini-cards only) | own row (`id = auth.uid()`), except `id_verified`, `email_verified`, `phone_verified` (trigger resets them unless service_role) |
| profile_private | own row only | own row only |
| profile_roles, work_links, busy_dates | own rows; others only through the same RPCs (work_links kind HEADSHOT: only to the owner of a cast role the person applied to, or a match counterpart) | own rows |
| projects, project_roles | anon + authenticated when `status <> 'DRAFT'`; owner always. `exact_address` is excluded from the public view `project_cards`; readable only by the owner and BOOKED collaborators via RPC | owner (insert/update while DRAFT or OPEN). Publishing via RPC `publish_project` (runs the rules check) |
| swipes | own only | own insert |
| applications | applicant, or the owner of the role's project | via RPC only (`apply_to_role`, `decide_application`, `send_ask`, `respond_ask`) |
| matches | owner or collaborator | via RPC only |
| messages | participants of the match | insert where sender = auth.uid() and the user is a participant and the match state ∉ (CLOSED, RELEASED, WITHDRAWN) and not blocked, and kind = 'TEXT'. AUDITION messages only via RPC `send_audition_request`. Trigger computes `flags` |
| booking_cards | participants | via RPC (`send_booking_card`, `respond_booking_card`) |
| shoot_days, call_times | project owner and BOOKED collaborators | owner via RPC (`publish_shoot_day`); a collaborator updates their own `ack_at` and attendance via RPC |
| wrap_items | participants | via RPC (`mark_wrap_item` by whoever the item is `owed_by`, `confirm_wrap_item` by the other side) |
| feedback | own submitted rows only (never the other person's raw answers) | via RPC `submit_feedback` (only when the match is WRAPPED) |
| reports | own | own insert |
| blocks | own | own insert/delete |
| notifications | own | own update (`read_at`); inserts by RPCs |
| events | none | own insert |

### 9.4 RPC functions (security definer, all check `auth.uid()`)
| Function | Behaviour |
|---|---|
| `discover_roles(p_city text, p_include_remote bool, p_limit int default 300)` | Returns OPEN roles joined with project card fields + owner stats; excludes own, swiped, blocked, past-start. Gates and scoring are applied on the client |
| `get_applicants(p_role uuid)` | Owner only. Returns applications + `profile_cards` + reputation + HEADSHOT links (cast roles) + a computed `rate_fit` (0..1). Never returns `min_day_rate` or `content_wont_do` |
| `get_profile(p_profile uuid)` | Returns one `profile_cards` row only if a relationship exists (§9.3 profiles row); otherwise "not found" |
| `scout_candidates(p_role uuid, p_limit int default 100)` | Owner only. **Refuses cast roles.** Profiles in the city or remote that match the role code, pass reverse G4 and G5 server-side, haven't applied, and weren't asked in 30 days. Returns `rate_fit`, never the rate |
| `apply_to_role(p_role, p_note, p_link, p_answer, p_kit)` | Checks open, cap, 25/day, not owner, not blocked → insert; `applications_count++`; notify the owner |
| `decide_application(p_app, p_decision text /*TALK,BACKUP,DECLINE*/, p_reason text)` | TALK → match + system message + notify. BACKUP → rank = max+1. DECLINE → templated notification |
| `send_ask(p_role, p_profile, p_note)` / `respond_ask(p_app, p_accept, p_reason)` | 10 asks/project/day. Cast roles: allowed only when the recipient is on the caller's Keep list. Reverse G4/G5 must pass. Accept → match (TALKING) |
| `send_audition_request(p_match, p_payload jsonb)` / `respond_audition(p_message, p_response, p_tape_url)` | Owner of a cast-role match in TALKING only. Mode must equal the project's `audition_mode`; in-person times 08:00-21:00 local; ≥1 `also_present`. Inserts an AUDITION message (flags computed on free text) |
| `send_booking_card(p_match, p_terms jsonb)` | Payer only. New version; supersedes SENT; match → OFFERED (or `change_pending` if BOOKED) |
| `respond_booking_card(p_card, p_accept bool)` | Collaborator only. Accept → ACCEPTED, previous ACCEPTED → SUPERSEDED; match BOOKED; `filled_count++`; if filled → role FILLED + PENDING apps CLOSED (notify each); upsert wrap_items from deliverables (post roles get DELIVERY owed by the collaborator) |
| `withdraw_match(p_match, p_reason, p_note)` / `release_match(p_match, p_reason)` | Computes `notice_hours` (against the first shoot day, or the delivery date for post roles); reopens the slot (`filled_count--`, role OPEN); notifies; returns backup #1 id. A release with <72h notice is recorded against the owner's Kept bookings |
| `publish_project(p_project)` | Runs `listing_rules_ok()` (SQL mirror of the BLOCK rules R01-R14, R18-R20) → OPEN |
| `reschedule_project(p_project, p_start, p_end, p_days)` | `reschedule_count++`; new booking-card versions for BOOKED matches; notify all |
| `cancel_project(p_project, p_reason)` | Owner. Project CANCELLED; every BOOKED match → RELEASED with `notice_hours`; under 72h counts against Kept bookings; notify all, PENDING applications CLOSED |
| `publish_shoot_day(...)`, `ack_shoot_day(p_day)`, `set_attendance(p_day, p_status)` | Notifications + ack bookkeeping |
| `mark_wrapped(p_project)` | Owner, allowed after `shoot_end`. Wraps the **on-set** matches only. Also lazily auto-run by `get_inbox()` for projects 3 days past `shoot_end` |
| `mark_wrap_item(p_item, p_link, p_note)` / `confirm_wrap_item(p_item, p_received bool)` | State machine above. Confirming a DELIVERY item (or its 7-day auto-confirm, run lazily by `get_inbox()`) moves that post-role match to WRAPPED |
| `submit_feedback(p_match, p_answers jsonb, p_note)` | Insert; if both submitted → set `revealed_at` on both |
| `get_reputation(p_profile)` | Aggregates; NULL ratios when n < 3 |
| `share_contacts(p_match)` | BOOKED only; sets the caller's share flag |
| `export_my_data()` | Returns JSON of everything the caller owns or authored |

### 9.5 Realtime and Storage
- **Realtime** (postgres_changes): `messages` (filter `match_id=in.(...)`), `notifications` (filter `user_id=eq.<uid>`), `booking_cards`, `applications` (owner's roles), `call_times`.
- **Storage:** none in the MVP. No avatars, no uploads; reels, sides, tapes and deliveries are links.

---

## 10. App architecture (Kotlin)

### 10.1 Modules and packages (single `:app` module)
```
com.tff.app
├─ App.kt, MainActivity.kt                 // edge-to-edge, dark theme, splash
├─ di/AppContainer.kt                      // manual DI; picks DemoRepo or SupabaseRepo
├─ core/  AppClock (demo offset), Ids, Money (format by currency), Dates, Geo (haversine), Result
├─ model/ Enums.kt, Taxonomy.kt (Appendix A), Profile.kt, Project.kt, Role.kt, Application.kt, Match.kt,
│         Message.kt, BookingCard.kt, ShootDay.kt, WrapItem.kt, Feedback.kt, Reputation.kt, Notification.kt
├─ domain/ MatchEngine, ApplicantRanker, ListingRules, MessageSafety, BookingDiff, BookingTermsFactory,
│          WrapItemsFactory, ReputationCalc, ShareText        // PURE Kotlin, no android.* imports
├─ data/Repo.kt                            // the single contract (below)
├─ data/demo/ DemoRepo, DemoState (@Serializable), DemoStore (JSON in filesDir), DemoSeed, DemoText, DemoSimulator
├─ data/remote/ SupabaseProvider, SupabaseRepo, Dtos, Mappers
├─ ui/theme/ Color, Type, Shape, Motion, Grain
├─ ui/components/ SwipeDeck, RoleCard, PersonCard, PayLine, ChipRow, Badge, TrackRecordChips, SafetyBanner,
│                 SlateOverlay, BookedOverlay, EmptyState, TimecodeText, PosterBand, StepHeader
├─ ui/screens/<screen>/ <Screen>Screen.kt + <Screen>ViewModel.kt (StateFlow<UiState>)
└─ nav/ Routes.kt (@Serializable), NavGraph.kt, BottomBar.kt
```
- **Mode selection:** `AppMode.DEMO` if `BuildConfig.SUPABASE_URL.isBlank()` or the user turned on "Use demo mode" (SharedPreferences); otherwise `AppMode.LIVE`.
- **No new dependencies for this session.** Use what's in `libs.versions.toml`: Compose, Material 3, navigation-compose, lifecycle, supabase-kt (auth, postgrest, realtime; storage is present but unused), ktor-okhttp, Coil 3, kotlinx-serialization, coroutines. Demo-mode notifications use the platform `NotificationManager` (and the POST_NOTIFICATIONS runtime permission on Android 13+). **One allowed addition in L1:** `androidx.work:work-runtime-ktx` for the F21 poller and scheduled reminders.
- **Persistence:** SharedPreferences for session and flags; a JSON file for demo state.
- **Fonts:** bundled `.ttf` files in `res/font`.
- **Domain code is pure Kotlin** and fully unit-tested (JUnit and coroutines-test are already in the deps).

### 10.2 The Repo contract (freeze this first; every workstream codes against it)
```kotlin
interface Repo {
    val mode: AppMode
    val session: StateFlow<Session?>                 // null = guest
    suspend fun sendOtp(email: String): Result<Unit>
    suspend fun verifyOtp(email: String, code: String): Result<Session>
    suspend fun startDemo(persona: DemoPersona?): Session     // demo only
    fun me(): Flow<Profile?>
    suspend fun profile(id: String): ProfileCard?   // null when no relationship exists (§9.3)
    suspend fun saveProfile(p: Profile, privateMinRate: Int?): Result<Profile>
    suspend fun cities(): List<City>                 // with areas
    fun density(cityId: String): Flow<Density>       // people, open projects, per-role counts

    // Gigs
    suspend fun discoverCandidates(): List<RoleWithProject>       // pre-gate; MatchEngine does the rest
    suspend fun swipeRole(roleId: String, dir: SwipeDir): Result<Unit>
    suspend fun apply(roleId: String, note: String?, workLinkId: String?, answer: String?, kit: Boolean): Result<Application>
    fun likesLeftToday(): Flow<Int>
    fun saved(): Flow<List<RoleWithProject>>

    // Projects (owner)
    fun myProjects(): Flow<List<ProjectSummary>>
    fun joinedProjects(): Flow<List<ProjectSummary>>
    fun project(id: String): Flow<ProjectFull?>
    suspend fun saveDraft(p: ProjectDraft): Result<String>       // returns projectId
    suspend fun publish(projectId: String): Result<Unit>         // ListingRules must pass
    suspend fun reschedule(projectId: String, start: LocalDate, end: LocalDate, days: Int): Result<Unit>
    suspend fun cancelProject(projectId: String, reason: String): Result<Unit>

    // Crew up
    fun applicants(roleId: String): Flow<List<ApplicantView>>
    suspend fun decide(applicationId: String, d: Decision, reason: String? = null): Result<Match?>
    suspend fun scout(roleId: String): List<ProfileCard>
    suspend fun sendAsk(roleId: String, profileId: String, note: String?): Result<Unit>
    suspend fun respondAsk(applicationId: String, accept: Boolean, reason: String?): Result<Match?>

    // Inbox / chat / booking
    fun inbox(): Flow<Inbox>
    fun messages(matchId: String): Flow<List<Message>>
    suspend fun sendMessage(matchId: String, body: String): Result<Unit>
    suspend fun sendAuditionRequest(matchId: String, req: AuditionRequest): Result<Unit>   // cast roles only
    suspend fun respondAudition(messageId: String, response: AuditionResponse, tapeUrl: String?): Result<Unit>
    fun bookingCards(matchId: String): Flow<List<BookingCard>>
    suspend fun sendBookingCard(matchId: String, terms: BookingTerms): Result<Unit>
    suspend fun respondBookingCard(cardId: String, accept: Boolean): Result<Unit>
    suspend fun withdraw(matchId: String, reason: WithdrawReason, note: String?): Result<String?> // backup appId
    suspend fun release(matchId: String, reason: String): Result<Unit>
    suspend fun shareContacts(matchId: String): Result<Unit>

    // Shoot & wrap
    fun shootDays(projectId: String): Flow<List<ShootDayFull>>
    suspend fun publishShootDay(d: ShootDayDraft): Result<Unit>
    suspend fun ackShootDay(shootDayId: String): Result<Unit>
    suspend fun setAttendance(shootDayId: String, status: Attendance): Result<Unit>
    suspend fun markWrapped(projectId: String): Result<Unit>
    fun wrapItems(matchId: String): Flow<List<WrapItem>>
    suspend fun markWrapItem(itemId: String, link: String?, note: String?): Result<Unit>  // by whoever owes it (DELIVERY: collaborator)
    suspend fun confirmWrapItem(itemId: String, received: Boolean): Result<Unit>
    suspend fun submitFeedback(matchId: String, answers: FeedbackAnswers): Result<Unit>
    suspend fun reputation(profileId: String): Reputation
    fun keepList(): Flow<List<ProfileCard>>

    // Safety & account
    suspend fun report(kind: ReportTarget, targetId: String, reason: ReportReason, details: String?): Result<Unit>
    fun myReports(): Flow<List<Report>>
    suspend fun block(profileId: String): Result<Unit>
    suspend fun unblock(profileId: String): Result<Unit>
    fun notifications(): Flow<List<AppNotification>>
    suspend fun exportMyData(): String               // JSON
    suspend fun deleteAccount(): Result<Unit>
    suspend fun log(event: String, props: Map<String, String> = emptyMap())
}
```

---

## 11. Demo mode: seed data and simulation

### 11.1 Rules
- **Deterministic:** `DemoSeed.build(anchor: LocalDate, rnd = Random(42))`. The anchor is the device date at first launch, stored in `DemoState`, so dates stay consistent across launches; [Reset demo] re-anchors. Stable IDs (`u_001`, `p_001`, `r_001_1`).
- **Fictional only:** names are drawn from curated pools of common first and last names per region. No real people, no celebrity names, no real company names as employers. Taste films *may* reference real, well-known films (that's taste, not identity). Reel and IMDb links use `https://demo.calltime.app/...`; tapping one shows the toast "Demo link". Posters and reel thumbnails are generated in-app (gradient + grain + title), so demo needs **no network**.
- **No faces:** no headshot images in the seed (silhouette placeholder in the actor inset).
- **Compliance:** every seeded OPEN listing must pass `ListingRules` (enforced by `DemoSeedTest`).
- **Never leaks into LIVE mode.**

### 11.2 Cities and areas (20 cities, 67 areas; coordinates approximate to ±1 km, which is fine for coarse distance)
All 20 go into reference data (`cities`, `areas`; `seed.sql` in LIVE). `is_live` = true for Mumbai only at launch (Bengaluru in the demo too, so Rohan's deck is full). **People and projects are seeded in only 7 of them** (Mumbai, Bengaluru, Pune, Indore, Kochi, Los Angeles, Atlanta); the rest exist so anyone can sign up, see honest counts and post.

| City (currency, paid floor/day*) | Areas |
|---|---|
| Mumbai (INR, 2,000) 19.076, 72.878 | Andheri West (19.136, 72.830), Versova (19.131, 72.815), Bandra (19.060, 72.830), Juhu (19.108, 72.826), Goregaon Film City (19.159, 72.889), Malad (19.187, 72.849), Dadar (19.018, 72.848), Powai (19.118, 72.906), Lower Parel (18.996, 72.830), Thane (19.218, 72.978) |
| Bengaluru (INR, 2,000) 12.972, 77.595 | Indiranagar (12.978, 77.641), Koramangala (12.935, 77.625), Jayanagar (12.925, 77.594), HSR Layout (12.912, 77.645), Whitefield (12.970, 77.750), Malleshwaram (13.003, 77.570), JP Nagar (12.907, 77.586), Electronic City (12.845, 77.660) |
| Delhi NCR (INR, 2,000) 28.614, 77.209 | Saket (28.525, 77.207), Hauz Khas (28.549, 77.200), Dwarka (28.592, 77.046), Lajpat Nagar (28.568, 77.243), Noida Sec 18 (28.570, 77.321), Gurugram Cyber City (28.495, 77.089) |
| Pune (INR, 1,500) 18.520, 73.857 | Koregaon Park (18.536, 73.894), Kothrud (18.507, 73.808), Baner (18.559, 73.787), Viman Nagar (18.567, 73.914) |
| Hyderabad (INR, 1,500) 17.385, 78.487 | Banjara Hills (17.416, 78.438), Jubilee Hills (17.431, 78.407), Gachibowli (17.440, 78.349), Ameerpet (17.437, 78.448) |
| Chennai (INR, 1,500) 13.083, 80.271 | T. Nagar (13.042, 80.234), Adyar (13.001, 80.257), Velachery (12.976, 80.221), Anna Nagar (13.085, 80.210) |
| Kolkata (INR, 1,200) 22.573, 88.364 | Salt Lake (22.580, 88.415), Park Street (22.553, 88.352), Jadavpur (22.499, 88.371), Tollygunge (22.498, 88.346) |
| Kochi (INR, 1,200) 9.931, 76.267 | Kakkanad (10.016, 76.341), Edappally (10.025, 76.308), Fort Kochi (9.966, 76.242) |
| Los Angeles (USD, 150) 34.052, -118.244 | Hollywood (34.093, -118.329), Burbank (34.181, -118.309), Silver Lake (34.087, -118.270), Downtown (34.040, -118.247), Culver City (34.021, -118.396) |
| Atlanta (USD, 150) 33.749, -84.388 | Midtown (33.781, -84.383), Decatur (33.775, -84.296), West End (33.736, -84.413) |
| London (GBP, 120) 51.507, -0.128 | Soho (51.514, -0.136), Shoreditch (51.526, -0.078), Hackney (51.545, -0.055), Brixton (51.461, -0.116), Ealing (51.513, -0.305) |
| Manchester (GBP, 120) 53.481, -2.243 | Northern Quarter (53.484, -2.236), Salford Quays (53.471, -2.296), Didsbury (53.416, -2.231) |
| **Early cities, India** (INR, 1,000 each), one "City centre" area at the city coordinates | Indore (22.719, 75.857), Dehradun (30.317, 78.032), Nagpur (21.146, 79.088), Chandigarh (30.733, 76.779), Ahmedabad (23.023, 72.571), Jaipur (26.912, 75.787), Lucknow (26.847, 80.947), Mysuru (12.296, 76.639) |

\*Paid floors are **placeholders** used only for the "Stipend" relabel (R04). Validate them with local crews before launch.

### 11.3 Profiles: 80 (cut from 120 by the critic panel: fewer cities to fill and verify, same coverage of every flow)
- **By city:** Mumbai 32 · Bengaluru 22 · Pune 6 · Indore 3 · Kochi 3 · LA 8 · Atlanta 6.
- **By primary role (sums to 80):**
  - Cast 17 (actor 16, voice actor 1)
  - Direction/writing 16 (director 7, writer 4, 1st AD 3, 2nd AD 1, script supervisor 1)
  - Production 6 (producer 3, PA 3)
  - Camera 11 (DP 7, camera op 2, 1st AC 2)
  - G&E 4 (gaffer 2, grip 2)
  - Sound 6 (mixer 5, boom 1)
  - Art/costume/HMU 6 (PD 2, art assistant 1, costume 1, HMU 2)
  - Post 10 (editor 5, colorist 1, VFX 2, sound designer 2)
  - Music 3 (composer)
  - Safety 1 (stunt coordinator 1; intimacy coordinators on seeded projects are named in `safety_measures`, not profiles)
- **Guarantees per launch city (Mumbai, Bengaluru):** ≥2 sound mixers, ≥3 DPs (≥2 with a camera kit), ≥2 editors, ≥1 gaffer, ≥1 1st AD, ≥1 producer, ≥6 actors (≥3 women).
- **Early-city and remote coverage:** Indore has 1 writer-director (FIRST_PROJECT, no work links), 1 actor and 1 DP; Kochi has 1 remote editor and 1 remote composer; at least 2 composers and 2 sound designers in total are `remote_ok`.
- **Levels:** FIRST_PROJECT 20% · BUILDING_REEL 35% · CREDITED_INDIE 30% · WORKING_PRO 15%.
- **Comp modes:** PAID_ONLY 20% (mostly WORKING_PRO and sound) · PAID_AND_SELECTIVE 50% · OPEN_TO_UNPAID 30%.
- **Languages:** everyone has `en`. Mumbai/Pune add hi and/or mr; Bengaluru kn/ta/hi; Indore hi; Kochi ml. LA: 20% es.
- **Availability:** India 60% WEEKENDS_ONLY or EVENINGS_AND_WEEKENDS; US/UK 70% ANY_DAY. Each profile has 0-6 random busy dates within the next 45 days.
- **Work links:** every non-FIRST_PROJECT profile has 1-3 REEL links (role-tagged); 30% have IMDB; FIRST_PROJECT profiles have 0-1 PORTFOLIO links ("phone-shot short").
- **Verification:** email 100% · phone 70% · id_verified 35%.
- **Gear:** all sound mixers have kits (`kit_fee_day` set); 6/10 DPs have camera kits; 2/3 gaffers have LED kits; 3 editors list their own edit stations.
- **History** (seeded WRAPPED projects, §11.4): 45 profiles have ≥1 past booking and 28 have ≥3 (so aggregates show). Track records skew positive; 3 owners have weak records (e.g. footage 1/4, paid on time 2/4, kept bookings 3/5) so the signals visibly work, including on the owner side.

**Bio examples (match this tone: concrete, honest, short):**
1. *Sound mixer, Andheri:* "Location sound, 6 yrs. Own kit: 8-track recorder, 2 wireless lavs, shotgun on a boom. I bring a boom op on multi-day shoots. Kit ₹1,500/day; flexible for honest zero-budget shorts with a locked script."
2. *Actor, Versova:* "Four years of theatre, now building a screen reel. Hindi, English, Marathi. Free weekends and after 7 pm. Unpaid is OK if there's a footage date and real food."
3. *Writer-director, Jayanagar:* "Software engineer on weekdays, writer-director on weekends. Two phone-shot shorts. Writing in Kannada and English. Looking for a DP who cares about night light."
4. *Gaffer→DP, Andheri:* "Gaffer on 30+ ad shoots and indie shorts. Small LED kit. Looking for my first DP credit on a short; I'll bring my best boy."
5. *Editor, Kakkanad (remote):* "Editor, Resolve. 20+ shorts, assistant on 3 features. Remote-friendly. I need a post end date and two revision rounds in writing."
6. *Sound mixer, Atlanta:* "Production sound mixer, 9 yrs. Paid work only right now: day rate + kit, boom op budgeted. Happy to look at locations before you lock them."
7. *Composer, Fort Kochi (remote):* "Composer for picture: strings, synths, small ensembles. Tell me minutes of music and your lock date. My reel is the demo; no unpaid custom demos."
8. *1st AD, Koramangala:* "1st AD on 12 shorts and a web series. I build the schedule and keep the day on time. Not a PA. Weekends."
9. *Writer-director, Indore (no portfolio yet):* "Self-taught, writing my first short in Hindi. No reel yet; I've shot two scenes on my phone. Free Sundays. Looking for people at my level who want to finish something."

### 11.4 Projects
- **24 OPEN projects with about 60 open roles** (cut from 36/90).
  - By city: Mumbai 9 · Bengaluru 7 · Pune 2 · Indore 1 · Kochi 1 · LA 2 · Atlanta 2.
  - Formats: SHORT 12 · MUSIC_VIDEO 3 · STUDENT_THESIS 3 · CHALLENGE_48H 2 · SERIES_PILOT 2 · VERTICAL 1 · DOCUMENTARY 1.
  - **Must include:** 2 post-only projects (stage POST, no shoot dates; editor, composer or sound-design roles with a picture-lock date and post deadline), one of them in LA for Dana; ≥8 remote post roles in total, India-wide; ≥3 roles with `level_wanted` FIRST_PROJECT or BUILDING_REEL ("Beginners welcome"); 1 Indore project (a Hindi short by a FIRST_PROJECT owner); ≥4 cast roles with each audition mode represented.
  - Roles per project: 2-4 open + 1-3 already attached (BOOKED seed matches).
  - Comp across roles: PAID_DAY 25% · PAID_FLAT 10% · STIPEND 30% · UNPAID 35%. 60% of projects have ≥1 paid or stipend role; sound roles are paid or stipend 80% of the time.
  - Dates: start = anchor + 3 to 45 days; 25% within 10 days; India 60% weekend dates; 70% LOCKED; hours_cap 8-12 (two at 14 to trigger the R06 warning display).
  - Content: 75% none · 10% NIGHT_SHOOT · 5% STUNTS_FIGHTS (with coordinator) · 5% INTIMACY_SCENE (non-nude, IC yes) · 5% FIREARMS_PROPS (replica, armourer). **No nudity listings in the seed.**
  - Owners: 20 distinct; 5 with strong records, 3 weak, the rest new.
- **20 WRAPPED projects** (history, owned by 13 of the 20 owners) supply **about 110 past bookings** (28 collaborators with ≥3, 17 with 1-2; about 15 are post-role bookings so "Delivered on time" shows), about 280 wrap items (85% confirmed) and about 190 feedback rows. Include 4 owner releases, 2 of them with <72h notice, so Kept bookings isn't always perfect.
- **Example loglines (write the other 16 in this voice):**
  - *Filter Coffee, 4 AM* (Bengaluru · Kannada/English short, 12 min): "A night-shift coder and a darshini owner keep the same strange hour, until one morning only one of them shows up."
  - *Last Local* (Mumbai · Hindi short, 15 min, night): "On the last train to Virar, a pickpocket realises the woman she's robbing is her future."
  - *Monsoon Static* (Mumbai · music video, 4 min): "A rooftop band plays through the first storm of the season."
  - *Second Draft* (Pune · student thesis, 18 min): "A ghostwriter hired to finish a dead man's memoir finds he wrote her into it."
  - *Yellow Line* (Atlanta · short, 10 min): "A night-bus driver and her only passenger have one stop to settle a ten-year-old argument."
  - *Soft Light* (LA · short, 14 min, **post-only**: picture locked, needs sound design and a composer): "A grief counselor rehearses the conversation she's been avoiding with her estranged brother."
  - *48 Frames* (Pune · 48-hour challenge): "Genre drawn at kickoff. We shoot Saturday, cut Sunday. Need sound and an editor who likes deadlines."
  - *Rajwada Rain* (Indore · Hindi short, 8 min, beginners welcome): "Two strangers share one umbrella across the old city and decide not to exchange names."

### 11.5 Personas (pre-built states)
| Persona | Starting state |
|---|---|
| **Meera** (actor, Versova) | 2 PENDING applications; 1 **Rebook** Ask card from an owner she worked with before (actors can't be cold-asked, §3.1); 1 BOOKED shoot in 6 days (day sheet published, not acked); 1 WRAPPED project with an **overdue FOOTAGE** item; one IN TALKS match with "Casting Hub Official" (a role she applied to that passed the listing rules) whose first chat message asks for a "registration fee" and says "WhatsApp me" with a fictional 10-digit number, showing the red banner and the number masked (this message is tagged `safetyExample`). This is the realistic scam path: the listing looks clean, the ask comes in chat |
| **Rohan** (writer-director, Jayanagar) | Owns OPEN *Filter Coffee, 4 AM* with 4 roles (DP stipend, sound mixer paid, lead actor unpaid with footage in 6 wks and self-tape auditions, editor remote stipend with a post deadline) and 7/3/10/4 applicants; 1 match IN TALKS with a DP; 1 DRAFT project that fails R02 (shows the blocker UI) |
| **Kabir** (gaffer→DP, Andheri) | PAID_AND_SELECTIVE; step-up enabled for DP; the deck has 3 step-up-friendly DP roles; 1 WRAPPED gaffer booking with feedback pending; one past owner on his Keep list has "Kept bookings 2/4" so the owner-side chip is visible |
| **Dana** (sound mixer, Atlanta) | PAID_ONLY with min rate $350; secondary role SOUND_DESIGNER; the seed guarantees ≥3 US sound or post-sound roles that pass her gate (LA/Atlanta paid roles + remote post-sound); 1 OFFERED booking card awaiting acceptance for **sound design on *Soft Light*** (post-only: picture lock in 5 days, deliver in 33 days, $2,400 flat, paid within 7 days of delivery) |
| **Build my own** | Runs real onboarding; the deck is built from the seed for the chosen city. Choosing **Indore** shows the early-city experience: honest counts, *Rajwada Rain*, India-wide remote roles, "Beginners welcome" chips |

### 11.6 DemoSimulator (coroutine scope in DemoRepo; all delays × speed factor; 1× defaults)
| Trigger | Simulated response |
|---|---|
| User applies to a seeded role | After 4-12 s the owner decides: match with p = clamp(0.15 + 0.7·score/100, 0.2, 0.85), else a templated decline. On match: system message + owner opener 2-5 s later ("Hey Meera! Saw your reel. Are both Saturdays OK?") |
| User sends a chat message to a seeded person | 60% chance of a reply after 3-8 s from intent templates (dates, pay, location, yes, thanks, can't). Hinglish variants for India personas |
| Match in TALKING with a seeded owner | Cast roles: the owner first sends an audition request in the listing's mode (3-6 s), and booking card v1 follows 6-15 s after the user answers [I'll do it] or sends a tape link. Other roles: booking card v1 6-15 s later. Either way, immediately if the user writes "booking", "confirm" or "offer" |
| User (as owner) sends a booking card to a seeded person | Accept after 4-10 s (p = 0.9), else "Ask to change" with a canned request ("Could call be 8 am? Last train timing.") |
| User publishes a project | Each role receives 3-8 applicants over 90 s (staggered), drawn from seed profiles that pass the reverse gates |
| User sends an Ask (Scout) | Accept after 5-15 s (p = 0.6), else a no-fault decline reason |
| User publishes a day sheet | Seeded booked people ack within 2-20 s (90%); one stays un-acked to show [Nudge] → acks 5 s after the nudge |
| Clock jump past the last shoot day | Project → WRAPPED (on-set matches); seeded owner marks PAID done (50% immediately); FOOTAGE due later; seeded counterpart submits feedback 5 s after the user's, so the reveal happens |
| Post-role match (user is the collaborator) after a clock jump to near the delivery date | [Mark delivered] is highlighted; after the user marks it with a link, the seeded owner confirms in 3-6 s, the match wraps, PAID is marked done within the terms, and feedback unlocks |
| Post-role match (user is the owner) | The seeded collaborator marks DELIVERY with a demo link 5 s after the clock passes delivery − 2 days |
| [Inject flagged message example] | Cast personas: opens a new IN TALKS chat whose first message is a scam pattern (fee + "WhatsApp me"). Crew personas: an Ask card whose note is a scam pattern |
| [Simulate a crew withdrawal] | A BOOKED seeded collaborator on the persona's project withdraws ("Paid work came up", 5 days' notice); the slot reopens and [Offer to backup #1] appears |

---

## 12. Visual direction: "darkroom"

The look is cinematic and work-first: a dark theme with film-inspired accents. Nothing about it suggests dating (no hearts, flames, gradients of pink, or face-first heroes).

**Build order:** colour tokens, typography, shapes, the swipe physics and accessibility (§12.1, 12.2, the Swipe part of 12.4) are P0. Texture (grain, light leak, sprocket strips) and the elaborate overlay animations (slate clap, stamp rotation, typed call time) are **P2 polish** (§4.2); until then, posters are a flat gradient with the title and overlays are a simple fade plus scale.

### 12.1 Colour tokens (dark only in the MVP; define in `ui/theme/Color.kt`)
| Token | Hex | Use |
|---|---|---|
| `bg` Darkroom | `#0B0B0D` | App background |
| `surface` | `#141418` | Cards, sheets |
| `surfaceRaised` | `#1C1C22` | Card on card, input fields |
| `surfaceHigh` | `#26262E` | Pressed, selected |
| `outline` | `#34343E` | Dividers, chip outlines |
| `text` Paper | `#F2EFE9` | Primary text (warm white) |
| `textSecondary` | `#A8A39A` | Secondary text |
| `textMuted` | `#8A857E` | Footers, hints |
| `accent` Tungsten | `#FFB547` | Primary CTA, role labels, highlights (3200K amber) |
| `accentPressed` | `#E09A2E` | Pressed CTA |
| `onAccent` | `#1A1206` | Text on Tungsten |
| `go` Greenlit | `#3DDC97` | Interested, BOOKED, paid pay-line |
| `stop` Safelight | `#FF5A4E` | Pass, danger banners, overdue |
| `info` Daylight | `#6CB4FF` | Links, soft tips, off-platform banner |
| `dangerContainer` | `#3A1716` (text `#FF8A80`) | Money and body-request banners |
| `infoContainer` | `#132235` (text `#A9D1FF`) | Tips |

Contrast (WCAG): Paper on bg ≈ 17:1, Tungsten on bg ≈ 11:1, textSecondary on surface ≈ 7:1, textMuted on bg/surface/surfaceRaised ≥ 4.6:1. Keep all text ≥ 4.5:1.

### 12.2 Typography (bundled OFL fonts in `res/font`)
- **Display: Barlow Condensed** SemiBold 600, used for slate-style labels, card role lines, overlays. Uppercase labels at +1.5sp tracking. Sizes: displayL 40/44, displayM 32/36, titleL 24/28.
- **Body: Inter** 400/500/600. Sizes: titleM 18/24 (600), body 16/24, bodyS 14/20, label 12/16.
- **Mono: JetBrains Mono** 500, timecode feel. Used for dates, call times, rates and counters ("SAT 14 NOV · 07:30", "18 left today").
- If a font file is missing, fall back to `FontFamily.Default` / `Monospace`. Support font scale up to 1.3: the card face drops the flags row, then the credibility row overflows into "+2".

### 12.3 Shape, texture, iconography
- **Shapes:** cards 20dp; sheets 28dp top corners; chips 10dp; buttons are 52dp-tall pills.
- **Texture:**
  - Poster band with a 2.39:1 letterbox (black bars).
  - Light leak: a radial gradient of Tungsten at 18% opacity from the top-left, fading to transparent.
  - Film grain: a tiled noise bitmap generated once at 3-4% alpha over posters and overlays only (never over text blocks).
  - Sprocket-hole strip divider on Day Sheet headers and the Booked overlay.
- **Icons:** Material Symbols Rounded (material-icons-extended). Interested = check in a Greenlit circle; Pass = close in Safelight; Save = bookmark; Booked = clapperboard. **No heart or flame icons anywhere.**

### 12.4 Motion
- **Swipe** (SwipeDeck):
  - The card follows the finger; rotation = (dx / width) × 12°, clamped at ±15°.
  - Stamps fade in from |dx| > 8% of width: right = **"INTERESTED"** (Greenlit slate tag), left = **"PASS"** (Safelight), up = **"SAVE"**.
  - Commit when |dx| > 35% of width or velocity > 1,200 px/s; fling out in 220 ms (FastOutLinearIn).
  - Below the threshold, spring back (dampingRatio 0.75, stiffness 400).
  - The next card rises from scale 0.94 / y +16dp to 1.0 / 0 (spring dampingRatio 0.8, stiffness 300).
  - Buttons trigger the same animation programmatically. Undo reverses the fling in 260 ms.
- **Haptics:** light tick when crossing the commit threshold; `LongPress` haptic on IN TALKS and BOOKED overlays.
- **IN TALKS overlay:** a clapperboard slate (two bars) claps closed in 180 ms. Text "IN TALKS" (Barlow, Paper) with "Rohan K. · Sound Mixer · Filter Coffee, 4 AM" underneath. Auto-dismiss after 1.6 s or on tap.
- **BOOKED overlay:** a stamp scales from 1.3 to 1.0 with a slight rotation of -4° (240 ms), Greenlit outline, then the call time types on in mono ("CALL SAT 14 NOV · 07:30"), with a sprocket strip at the bottom.
- **Reduce motion:** if the system animator scale is 0 or "Remove animations" is on, use cross-fades only and no rotation.
- **Accessibility:** every swipe has a button and TalkBack custom actions ("Interested", "Pass", "Save"); 48dp minimum touch targets; content descriptions on chips ("Paid on time 11 of 12 projects").

---

## 13. Cold start, 90-day launch plan, monetization (later)

### 13.1 Principles (from research)
- Win one city before opening the next.
- Seed the **scarce side**: real projects and scarce roles.
- Plug into WhatsApp and FB groups instead of fighting them.
- No fake activity in live mode, ever.
- No self-promotional Reddit posts.
- The founder shows up as a practitioner.

### 13.2 Launch city: Mumbai (Versova-Andheri-Goregaon belt first), then Bengaluru
- **Why Mumbai:** the densest indie and ad ecosystem, theatre groups, MAMI and Versova screening culture, Android-majority users, and the IFP 50-Hour team-formation spikes in Mumbai and Pune ([1w4fa0z](https://reddit.com/r/indianFilmmakers/comments/1w4fa0z/)).
- **Why Bengaluru next:** IT-worker weekend filmmakers and active communities that were displaced by the Telegram pause ([1u51cvf](https://reddit.com/r/indianFilmmakers/comments/1u51cvf/)).

| Phase | Days | Actions | Gate to move on |
|---|---|---|---|
| **Pre-launch** | -30 to 0 | Hand-recruit **30 real Mumbai projects** with real dates, starting with people already posting team calls on r/indianFilmmakers and city subs. Approach is value-first: "we'll structure your crew call for free". Recruit scarce roles first: **15 sound, 20 DP/camera, 15 editors, 10 gaffers/grips, 25 actors (≥50% women)** via theatre groups, acting workshops, rental houses and mass-media college clubs. Concierge ID checks (video calls). Publish community rules, "How we make money" and the no-gen-AI pledge. Store listing: "Free forever for cast and crew." | ≥30 projects with ≥1 OPEN role; ≥150 profiles; ≥8 sound mixers within 15 km of Andheri; LIVE **L1 shipped** (wrap, reputation, closed-app notifications; §4.2); the share-link fallback page is live |
| **Open Mumbai** | 1-30 | Invite-led: every owner gets [Share to WhatsApp] links for their groups (the Craigslist move). **Remote post roles are open India-wide from day 1** (editor, colorist, composer, sound design, writer), and early cities (§11.2) can sign up and post, with honest counts and no marketing spend there. The founder answers every message and report within 24h. Weekly honest "Booked this week: N shoots" post in-app. Run the **first "Calltime Weekend"** (a 48-hour short challenge; teams form in-app; role gaps highlighted; screening at a partner venue) | ≥40 shoots booked; median time-to-crew < 10 days; report SLA met |
| **Deepen** | 31-60 | Tune ranking weights from data (§14). Second Calltime Weekend. Partner with 2 screening nights and 2 acting studios (actors need reel material; [1rdzhzw](https://reddit.com/r/Filmmakers/comments/1rdzhzw/)). Open the Bengaluru waitlist (people can browse Mumbai and create profiles) | Mumbai: ≥25 OPEN projects at any time; D30 retention of booked users ≥35% |
| **Second city** | 61-90 | Open Bengaluru when the density gate is met (≥20 OPEN projects, ≥120 profiles including ≥6 sound and ≥8 editors). Check whether India-wide remote roles are rescuing small cities (Kochi, Dehradun, Indore) or diluting Mumbai (REDDIT_RESEARCH §9 Q8). Ship the first "Later" items in order: FCM push → team thread → pencil holds | Bengaluru density gate; rebook rate ≥15% in Mumbai |

**Content as marketing** (not ads):
- Guides that answer real threads, posted only where rules allow and always useful on their own: "How to write a crew call that gets answers", "What normal looks like at an audition", "Booking confirmation template".
- Never "I built an app" posts ([1muqlpe](https://reddit.com/r/Filmmakers/comments/1muqlpe/)).

**2027 moment:** IFP 50-Hour (September). Ship Challenge mode (city lobbies with role-gap team formation) before then.

### 13.3 Monetization (Later; nothing ships in the MVP)
**Never charge for:** applying, being visible, messaging, uploading media, verification, or safety features.

Fair revenue, in order:
1. **Pro tools for project owners:** unlimited active projects, PDF call sheets, releases with e-sign, document vault. A generous free tier for student and zero-budget films. India-specific pricing.
2. **Payment protection on paid gigs:** a small fee paid by the production on escrowed day rates (also fixes P4).
3. **B2B plans for film schools, festivals and challenge organisers:** a thesis-crewing portal and organiser dashboards; their members use the core app free.
4. **Boosts only for PAID projects,** clearly labelled. Never on unpaid listings and never for applicants.
5. **Sponsored challenges** (gear, software and festival brands).

Publish the "How we make money" page from day one.

---

## 14. Success metrics

**North Star: shoots that happened via Calltime per week, per city.** Definition: BOOKED matches whose last shoot day has passed with attendance ATTENDED (or ≥1 confirmed wrap item), counted once per project. "We don't count matches. We count call times that happened."

| Metric | Definition | 90-day target (Mumbai) |
|---|---|---|
| Time-to-crew | Median days from role published to FILLED | < 7 days (baseline: "months") |
| Role fill rate | % of roles FILLED within 14 days of publishing | ≥ 50% |
| Match → booked | % of matches reaching BOOKED | ≥ 40% |
| Applications per role | Median at close | 5-15 (not 200) |
| Closure rate | % of applications with a status within 14 days | ≥ 95% |
| Owner response time | Median hours to decide | < 48h |
| Show-up rate | ATTENDED / booked shoot-days (excluding ≥72h withdrawals) | ≥ 90% |
| Kept bookings (owners) | Bookings not released, cut or cancelled with <72h notice / bookings | ≥ 95% |
| Delivered on time (post roles) | DELIVERY items confirmed by delivery_date + 3 days / post-role bookings | ≥ 80% |
| Time to publish | Median minutes from opening the wizard to OPEN (3-role on-set project) | ≤ 4 min |
| Cap-fill time | Median hours for a capped role to reach its cap | Watch: under 2h triggers the batched-release work (§4.3) |
| Dropout inside 72h | Withdrawals with <72h notice / bookings | ≤ 8% |
| Paid on time | PAID items CONFIRMED by pay_by_date | ≥ 85% |
| Footage delivered | FOOTAGE items CONFIRMED by due + 14 days | ≥ 70% |
| Day sheet ack | % of booked people who tap "Got it" before call | ≥ 90% |
| Rebook rate | % of owners who invite a Keep-list person to a new project within 60 days | ≥ 15% (stretch 25%) |
| Retention | D30 retention of users with ≥1 booking | ≥ 35% |
| Density | OPEN projects and profiles per scarce role within 15 km | per §13 gates |
| **Safety guardrails** | Reports per 1,000 messages; % of reports actioned within 24h; money/body flags per 1,000 messages; repeat offenders | 100% within 24h; flags trending down |
| Quality guardrails | Publish-step abandonment (rules too strict?); % of UNPAID roles; % of listings warned for R15 | Abandonment < 30% |

**Instrumentation** (the `events` table in LIVE mode, Logcat in DEMO). Log these names exactly: `app_open`, `guest_browse`, `signup_done`, `onboarding_done`, `card_impression{role_code,score,outside_radius}`, `swipe{dir,deck}`, `apply`, `decide{decision,latency_h}`, `ask_sent`, `ask_answered`, `match_created`, `message_sent{flags}`, `booking_sent{version}`, `booking_accepted`, `booking_changed{fields}`, `withdraw{notice_h,reason}`, `shoot_day_published`, `shoot_day_ack`, `attendance{status}`, `wrap_item_marked{kind}`, `wrap_item_confirmed{kind,received,days_late}`, `feedback_submitted`, `report_created{reason}`, `block`, `rebook_invite`, `share_role{channel}`, `publish_blocked{rule}`, `publish_done{minutes,roles,post_only}`, `audition_sent{mode}`, `audition_answered{response}`, `delivery_marked`, `release{notice_h}`, `early_city_signup{city}`.

---

## 15. Build plan and QA

### 15.1 Workstreams for a small team of AI engineers (parallel after hour 0)
| WS | Owns | Depends on |
|---|---|---|
| **WS0 Contracts** (first, ~1h, one engineer) | `model/*`, `Enums.kt`, `Taxonomy.kt`, `data/Repo.kt`, `nav/Routes.kt`, theme tokens | none |
| WS1 Design system | `ui/theme`, `ui/components` (SwipeDeck, RoleCard, PersonCard, PayLine, chips, overlays, EmptyState) | WS0 |
| WS2 Domain | `domain/*` + unit tests (MatchEngine, ApplicantRanker, ListingRules, MessageSafety, BookingDiff, BookingTermsFactory, WrapItemsFactory, ReputationCalc, ShareText) | WS0 |
| WS3 Demo data | `data/demo/*` (DemoSeed, DemoText, DemoStore, DemoRepo, DemoSimulator) + DemoSeedTest | WS0, WS2 |
| WS4 Discover + Apply + Crew up | S05-S10 | WS1, WS2 |
| WS5 Projects | S11-S13, S16E | WS1, WS2 |
| WS6 Inbox, chat, booking, wrap | S14-S18, S16V, audition sheet and card (F20), post-role booking and DELIVERY | WS1, WS2 |
| WS7 Onboarding, profile, safety, settings | S01-S04, S19-S24, demo notifications (F21 demo half) | WS1 |
| WS8 Supabase | `supabase/migrations/*` (all tables), `seed.sql` (20 cities), `data/remote/*` (SupabaseRepo **L0 only** this session; L1 methods return a typed `NotLiveYet` and their UI entry points are hidden in LIVE) | WS0 |

Builds must run through `tools/lockrun.py -- ./gradlew ...` so parallel agents never run two Gradle builds at once.

### 15.2 Unit tests (required)
- `MatchEngineTest`: each gate G1-G7 positive and negative; the worked example in §7.5 ≈ 84.8; fallback triggers below 10 cards; diversity cap; exploration slot; a post-only project's roles pass G6 with past or null shoot dates; a PAID_ONLY user passes G4 on a flat-fee post role.
- `ApplicantRankerTest`: ordering and reason chips; near-duplicate answer detection; the beginner floor (no reel scores 5 on a "Beginners welcome" role, 0 elsewhere); PORTFOLIO links score 4; `rate_fit` drives the rate component.
- `ScoutTest`: cast roles return nothing; reverse G4/G5 exclude a PAID_ONLY person from an unpaid ask and a person whose `content_wont_do` matches; Rebook to a cast Keep-list person is allowed.
- `ListingRulesTest`: one test per rule R01-R22, including R05 skipping on post-only projects and R19 blocking an unverified owner.
- `AuditionRequestTest`: mode must equal the listing's `audition_mode`; in-person time outside 08:00-21:00 rejected; empty "also present" rejected for studio/public.
- `WrapItemsFactoryTest`: on-set booking → PAID/FOOTAGE/FINAL_FILM/CREDIT; post-role booking → DELIVERY (owed by collaborator) + PAID/FINAL_FILM/CREDIT, no FOOTAGE; post-role pay_by_date = delivery_date + terms.
- `MessageSafetyTest`: positives and negatives in English and Hinglish (e.g. "whatsapp karo" flags; "the WhatsApp scene in act 2" does not trigger MONEY); masking.
- `BookingDiffTest`: changed fields detected; no-op edits produce no new version.
- `ReputationCalcTest`: n<3 → null ratios; withdrawals with ≥72h notice excluded; disputes excluded; owner releases and cancellations under 72h lower Kept bookings; post-role bookings feed Delivered on time, not Showed up.
- `DemoSeedTest`: counts match §11; every OPEN listing passes ListingRules; no contact info in any seed text except messages tagged `safetyExample`; all dates are relative and in range; launch-city scarce-role guarantees hold; the §11.4 "must include" items exist.

### 15.3 End-to-end QA script (demo mode, must pass before anything is shown)
1. **Cold install, no backend.** The app opens in DEMO. Welcome shows the persona tiles.
2. **Meera.**
   - The deck shows only roles that pass the gates.
   - Swipe right on "Last Local" and apply. Within ~12 s, IN TALKS (or a closure).
   - The chat shows the terms summary; the owner sends a **self-tape audition request** with a sides link and the fixed safety lines; tap [Send my tape] with a link.
   - The owner sends a booking card; accept with the pledge; the BOOKED overlay plays.
   - Projects → Joined shows it.
   - The day sheet: tap Got it, then Add to calendar (intent opens), then Share with trusted contact (share sheet opens).
3. **Meera safety.** Open the "Casting Hub Official" chat: the red banner shows, the phone number is masked, Report works, the report appears in the Safety Center with status RECEIVED. Switch to Dana (no shared project or match with Meera): Meera's profile appears on no screen, and `DemoRepo.profile(meeraId)` returns null.
4. **Meera wrap.** Settings → Jump past next shoot. Wrap items appear. Owner marks PAID. Meera confirms. Feedback submits and reveals after the counterpart answers. The overdue FOOTAGE nudge is visible on the older project.
5. **Rohan.**
   - The draft fails R02 with a clear fix. Fix it and publish.
   - The share sheet text matches the template.
   - Applicants arrive within 90 s.
   - Crew up: Let's talk / Backup / Not this time; the declined applicant's closure exists (inspect demo state or the persona switch). The lead-actor role has **no Scout tab**; the sound-mixer role does.
   - Send a booking card; the seeded person accepts; the role becomes FILLED when count is reached; pending applicants are auto-closed.
   - Post a second project with a stopwatch: 3 on-set roles using "Pay for all roles" and [Duplicate] in **≤4 minutes**. Post a post-only project (stage POST, editor + composer remote): no shoot-date fields, R20 asks for a picture-lock date, publish succeeds.
6. **Withdraw → backup.** As Rohan, use Settings → [Simulate a crew withdrawal]: the slot reopens and [Offer to backup #1] sends an Ask. Then release a booked person inside 72h: the Release sheet warns, and Rohan's Kept bookings changes.
7. **Dana.** The PAID_ONLY gate holds (no unpaid cards); currency shows $; remote post-sound roles appear outside Atlanta. Accept the *Soft Light* sound-design card (it reads "deliver by …, paid within 7 days of delivery", no call times). Settings → +30 days: [Mark delivered] with a link; the owner confirms; the match wraps; feedback asks "Delivered on time?".
8. **Build my own in Indore** as a FIRST_PROJECT writer with no work link: the header says "early city"; *Rajwada Rain* and India-wide remote roles appear with "Beginners welcome" where set; nothing is blank.
9. **Notifications.** With the app in the background, a simulated booking card produces a system notification; tapping it opens the chat.
10. **Reset demo** restores the initial state. Killing and relaunching the app keeps state (JSON persisted).
11. **Accessibility.** TalkBack can Interested/Pass via custom actions; font scale 1.3 doesn't clip card text.

---

## Appendix A. Role taxonomy

### A.1 Roles (code · dept · label · plain duties · scarcity · remote by default · default credit)
| Code | Dept | Label | Plain duties (shown in pickers) | Scarcity | Remote | Default credit |
|---|---|---|---|---|---|---|
| DIRECTOR | DIRECTION | Director | Leads the creative vision; directs cast and camera | OVERSUPPLIED | no | Director |
| WRITER | WRITING | Writer | Writes or rewrites the script | OVERSUPPLIED | yes | Written by |
| PRODUCER | PRODUCTION | Producer | Finds money, locations and people; owns schedule and budget | SCARCE | no | Producer |
| FIRST_AD | DIRECTION | 1st Assistant Director | Builds the schedule and runs the set day. Not a PA | SCARCE | no | 1st Assistant Director |
| SECOND_AD | DIRECTION | 2nd Assistant Director | Call sheets, cast wrangling, paperwork | BALANCED | no | 2nd Assistant Director |
| SCRIPT_SUPERVISOR | DIRECTION | Script supervisor | Continuity and script notes | BALANCED | no | Script Supervisor |
| PA | PRODUCTION | Production assistant | General help: setup, runs, lock-ups | OVERSUPPLIED | no | Production Assistant |
| DP | CAMERA | Director of photography | Designs the look; leads camera and lighting | BALANCED | no | Director of Photography |
| CAMERA_OP | CAMERA | Camera operator | Operates camera under the DP | BALANCED | no | Camera Operator |
| FIRST_AC | CAMERA | 1st AC / focus puller | Focus, lenses, camera build | BALANCED | no | 1st Assistant Camera |
| GAFFER | GE | Gaffer | Head of lighting; runs lights with the DP | SCARCE | no | Gaffer |
| GRIP | GE | Grip | Rigging, stands, flags, dolly | BALANCED | no | Grip |
| SOUND_MIXER | SOUND | Production sound mixer | Records dialogue on set; owns the sound kit | SCARCE | no | Sound Recordist |
| BOOM_OP | SOUND | Boom operator | Positions the boom mic; assists the mixer | SCARCE | no | Boom Operator |
| PRODUCTION_DESIGNER | ART | Production designer | Sets, props, the visual world | BALANCED | no | Production Designer |
| ART_ASSISTANT | ART | Art assistant | Dressing, props, builds | BALANCED | no | Art Assistant |
| COSTUME | ART | Costume / wardrobe | Sources, fits and maintains costumes | BALANCED | no | Costume |
| HMU | ART | Hair & makeup | Hair and makeup looks and continuity | BALANCED | no | Hair & Makeup |
| ACTOR | CAST | Actor | Performs a role | OVERSUPPLIED | no | Cast |
| VOICE_ACTOR | CAST | Voice actor | Voice-over and dubbing | BALANCED | yes | Voice |
| EDITOR | POST | Editor | Cuts picture; delivers versions | BALANCED | yes | Editor |
| COLORIST | POST | Colorist | Grades the picture | BALANCED | yes | Colorist |
| VFX_ARTIST | POST | VFX artist | Compositing, cleanup, effects shots | BALANCED | yes | Visual Effects |
| SOUND_DESIGNER | POST | Sound designer / re-recording mixer | Edits, designs and mixes sound in post | BALANCED | yes | Sound Design |
| COMPOSER | MUSIC | Composer | Writes original score | OVERSUPPLIED | yes | Music by |
| BTS_STILLS | OTHER | BTS / stills | Behind-the-scenes video and stills | OVERSUPPLIED | no | Stills |
| INTIMACY_COORDINATOR | SAFETY | Intimacy coordinator | Choreographs and safeguards intimate scenes | SCARCE | no | Intimacy Coordinator |
| STUNT_COORDINATOR | SAFETY | Fight / stunt coordinator | Designs and safeguards fights and stunts | SCARCE | no | Stunt Coordinator |
| LOCATION_MANAGER | PRODUCTION | Locations | Finds and secures locations and permissions | SCARCE | no | Locations |

### A.2 Role details forms (`project_roles.role_details`, all optional, ≤3 fields per family)
| Family (role codes) | Fields |
|---|---|
| Cast (ACTOR, VOICE_ACTOR) | character name · playing age range · size (LEAD / SUPPORTING / DAY_PLAYER) · sides available Y/N |
| Sound (SOUND_MIXER, BOOM_OP) | speaking actors per scene (max) · INT/EXT split · boom op budgeted Y/N |
| Composer | minutes of music (or cues) · picture-lock date · temp track Y/N |
| Editor / Colorist | hours of footage · revision rounds · delivery specs (short text) |
| VFX | number of shots · boards/previz Y/N · supervisor on set Y/N |
| Camera / G&E (DP, CAMERA_OP, FIRST_AC, GAFFER, GRIP) | camera/lighting package (short text) · crew under this role (count) · prep days |

## Appendix B. Enums (Kotlin names = DB text values)
`Level`: FIRST_PROJECT, BUILDING_REEL, CREDITED_INDIE, WORKING_PRO · `CompMode`: PAID_ONLY, PAID_AND_SELECTIVE, OPEN_TO_UNPAID · `CompType`: PAID_DAY, PAID_FLAT, STIPEND, UNPAID · `PaymentTerms`: ON_WRAP, HALF_UPFRONT, WITHIN_7_DAYS, WITHIN_15_DAYS, WITHIN_30_DAYS · `Perk`: TRAVEL_COVERED, LODGING, FOOTAGE_BY_DATE, FINAL_FILM_BY_DATE, STEP_UP_CREDIT (+ derived: MEALS, SHORT_COMMITMENT, SCRIPT_READY) · `Goal`: PAID_WORK, REEL_FOOTAGE, STEP_UP, FESTIVAL_CREDIT, BUILD_TEAM, FUN_COMMUNITY · `AvailabilityPattern`: ANY_DAY, WEEKENDS_ONLY, EVENINGS_AND_WEEKENDS, WEEKDAYS_ONLY · `Format`, `Stage`, `ScriptStatus`, `Ambition`, `LocationType`, `DateCertainty`, `Meals`, `Transport`, `ContentFlag`, `AuditionMode`, `GearPolicy` (see §6.2-6.3) · `ApplicationStatus`, `MatchState`, `BookingStatus`, `WrapStatus`, `Attendance` (see §9.2) · `WrapKind`: PAID, FOOTAGE, FINAL_FILM, CREDIT, DELIVERY · `OwedBy`: PRODUCTION, COLLABORATOR · `MessageKind`: TEXT, SYSTEM, BOOKING, AUDITION · `AuditionResponse`: WILL_DO, CANT_MAKE_IT, TAPE_SENT · `SwipeDir`: RIGHT, LEFT, SAVE, BACKUP · `Decision`: TALK, BACKUP, DECLINE · `WithdrawReason`: DATES_CHANGED, PAID_WORK, HEALTH_FAMILY, TERMS_CHANGED, SAFETY, OTHER · `ReportReason`: MONEY_REQUEST, SEXUAL_INAPPROPRIATE, FAKE_IMPERSONATION, UNSAFE_SET, HARASSMENT, NOT_PAID, SPAM, OTHER.

## Appendix C. MessageSafety patterns (case-insensitive; tune with tests)
```
CONTACT        (?:\+?\d[\s-]?){10,13}
               [A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}
               (?<![\w@])@[A-Za-z0-9_.]{3,30}\b
OFF_PLATFORM   \b(whats\s?app|wa\.me|telegram|t\.me|signal app|insta(gram)?\s*(dm|id)|dm me on|text me|call me|email me|gmail|my number|number (do|bhejo|send)|call karo|whatsapp karo)\b
MONEY          \b(registration|security|portfolio|audition|processing|joining|membership|profile)\s*(fee|fees|charges?|deposit|amount)\b
               \b(pay|send|transfer|deposit|bharna|bharni|jama)\b.{0,24}(₹|\brs\.?|\binr\b|\$|usd|£|\bupi\b|zelle|paypal|venmo|gpay|paytm|phonepe)
               \bpaise\s*(bhejo|do|dena)\b
BODY_REQUEST   \b(bikini|swimsuit|lingerie|bold\s*(photos?|pics?|shoot)|measurements?|bra\s*size|body\s*(pics?|photos?)|photos?\s*bhejo)\b
PRIVATE_MEET   \b(my\s*(place|flat|apartment|room)|hotel\s*room|come\s*alone|ghar\s*aa\s*jao)\b
```
Direction matters: MONEY is flagged red only when the sender is the owner side of the match, or the message comes from an unmatched or unknown sender. A collaborator writing "paid via UPI?" isn't a scam, so it gets no banner.

## Appendix D. Copy rules
- **Say:** Interested · Pass · In talks · Booking card · Booked · Call time · Crew up · Gig · Role · Unpaid (with what you get) · Stipend · "Every applicant gets an answer."
- **Never say:** It's a match · Tinder · hot · swipe right on talent · collab (as a pay type) · exposure · opportunity (for unpaid) · verified project · vetted · background checked · "DM me".
- **Tone:** plain, direct, crew-to-crew. Numbers in mono. No exclamation-mark hype. Error messages tell people how to fix the problem ("Add pay, or mark it unpaid and say what people get.").

---

## Critic panel review

**What this is:** a stress test of this spec by five synthetic personas, each built from the Reddit evidence in `REDDIT_RESEARCH.md` and `raw/*.md`. They are not real interviews. The panel read the whole PRD and the research, then answered five questions each. The must-fix changes they agreed on have already been applied in the sections above. This section records what they said, what changed, and what they turned down.

### Persona verdicts

**1. Indie director in a small city** (Indore; struggles to staff shorts; the local Facebook group is "dead quiet", [15sa7ji](https://reddit.com/r/Filmmakers/comments/15sa7ji/))
- **Install?** Yes, for the structured crew call he can paste into his WhatsApp groups. Before the fixes his city wasn't in the list, so he couldn't have signed up.
- **Still using it after week one?** Only if one stranger turns up from a shared card. Density, not features, decides it.
- **Would delete it if:** the deck is blank, or posting a 4-role short takes 20 minutes (the old wizard had about 17 fields per role).
- **Missing (fixed):** early cities he can post from, honest counts instead of a blank screen, India-wide remote post roles from day one, a share text that works for people without the app, a faster wizard.
- **Over-built:** the 12-city seed and the texture polish. Neither helps him get crew.
- **Verdict:** "I'll install it to write a crew call my WhatsApp group takes seriously, and I'll keep it if one stranger actually shows up."

**2. Working gaffer / AC sick of unpaid "passion projects"** ([u9seb6](https://www.reddit.com/r/LocationSound/comments/u9seb6/), [1vabt10](https://www.reddit.com/r/Filmmakers/comments/1vabt10/))
- **Install?** Yes. The PAID_ONLY gate, the "Stipend" relabel, the kit-fee chip and "Own gear, no kit fee" warnings are exactly his filters.
- **Still using it after week one?** Yes, if paid gigs exist in Mumbai. The booking-card diff is the first tool that makes "a week became two days" provable.
- **Would delete it if:** he gets one unpaid ask, or a production cuts or cancels on him the day before with no consequence.
- **Missing (fixed):** accountability on the production side ("Kept bookings"; release and cancellation under 72h count against the owner), Ask cards that respect his money gate, and his private minimum rate never reaching the owner's phone.
- **Missing (not fixed):** a kill fee and overtime terms; bringing his best boy as a package. See Rejected.
- **Over-built:** taste films and genre preferences on a crew profile. They're harmless because they're optional.
- **Verdict:** "Paid-only and kit fee get the install. Producers having a record too is why I'd stay. One unpaid ask and I'm gone."

**3. Female actor wary of fake casting calls** ([1awhlch](https://reddit.com/r/acting/comments/1awhlch/), [165ao2t](https://www.reddit.com/r/Filmmakers/comments/165ao2t/), [1tumcb1](https://reddit.com/r/acting/comments/1tumcb1/))
- **Install?** Yes, cautiously. No faces on cards, honest badge wording and blocked fee requests are what she's never seen elsewhere.
- **Still using it after week one?** If a real audition happens the way the listing said, and footage arrives with a date.
- **Would delete it if:** a stranger who "posted a project" could browse actors and message her. That was possible through Scout and the readable `profile_cards` view. Also if an audition moved to someone's flat at 10pm.
- **Missing (fixed):** actors can't be scouted (Applicants-only, Rebook only from the Keep list); profiles aren't a scrapeable directory; no profile photos; an audition request card locked to the listing's declared mode, limited to 08:00-21:00 for in-person, with others present; intimacy listings need an ID-verified owner (R19). Also fixed: the safety tip promised that "sides are shared here, in the app", which the old spec had no way to do.
- **Over-built:** nothing safety-related. She'd rather have more.
- **Verdict:** "No faces, no cold messages from 'directors', fee scams flagged in chat, auditions only at the venue and hours on the listing. I'd try it, and keep it if the footage actually comes."

**4. Self-taught film student in India with no portfolio** ([1uhya0h](https://reddit.com/r/indianFilmmakers/comments/1uhya0h/), [1usmaqf](https://reddit.com/r/Filmmakers/comments/1usmaqf/))
- **Install?** Yes. It's free, on Android, with no fees, and the first swipe comes before a full profile.
- **Still using it after week one?** If he gets one "Let's talk". The old ranker gave him 0 for a reel and close to 0 for trust, so he'd sit at the bottom of every list.
- **Would delete it if:** he never hears back, though the 14-day closure guarantee helps. He'd also leave if the app felt like it was only for working pros.
- **Missing (fixed):** a "beginner floor" in the ranker on beginner projects, phone-shot and theatre links counting as work, a "Beginners welcome" chip, and the early-city path for students outside metros.
- **Missing (not fixed):** Hindi UI, under-18 access, and a festival tracker. See Rejected.
- **Over-built:** the 7-part fit score. It's invisible to him, and the panel kept it because it's cheap pure Kotlin.
- **Verdict:** "Free, Android, and it doesn't bury me for having no reel on beginner projects. I'd install today, and I'll stay if I finish one short through it."

**5. Composer / sound designer who always gets brought in too late** ([1v1nrip](https://www.reddit.com/r/filmscoring/comments/1v1nrip/), [1vtihev](https://www.reddit.com/r/filmscoring/comments/1vtihev/))
- **Install?** Yes. Pay parity ("X of Y roles paid") and the ban on spec demos for unpaid roles are the two things his subreddit has been asking for.
- **Still using it after week one?** Before the fixes, no. The loop was built only for shooting days:
  - his booking would have wrapped when the *shoot* ended, before he'd written a cue;
  - his pay-by date was counted from the last shoot day;
  - feedback asked whether he "showed up";
  - a film already in post couldn't be published at all, because R05 required future shoot dates.
- **Would delete it if:** it was another board of "1-week feature deadline, DM me" posts.
- **Missing (fixed):**
  - a separate post-role loop: delivery date, a DELIVERY item he marks, pay counted from delivery, wrap on delivery, and a "Delivered on time" record;
  - post-only projects;
  - a required picture-lock date for music and sound design (R20), plus a warning when the window is under 3 weeks (R21);
  - a nudge to attach post roles in pre-production (R22);
  - the scoring window shown on the card face.
- **Over-built:** the day sheet and T-48h checks, which don't apply to him. They're now on-set only.
- **Verdict:** "The app used to wrap me before I'd written a note. Now the lock date, delivery date and pay-from-delivery are on the card. I'll install it, and keep it if directors post me in pre-production."

### Changes applied (must-fix)
| # | Change | Why (kill-adoption / safety / scope / loop) | Where |
|---|---|---|---|
| 1 | **Post-role loop**: delivery date replaces shoot days; DELIVERY wrap item owed by the collaborator (auto-confirm after 7 days of owner silence); pay-by from delivery; post matches wrap independently of the shoot; "Delivered on time" record; post-role feedback questions; 7-day notice rule | Missing core-loop step for 6 of 29 roles | §3.4, F9, F13, F14, §6.4, S16, S17, S18, §9.2 `wrap_items`, §9.4 |
| 2 | **Post-only projects publishable** (nullable shoot fields; R05 scoped to on-set roles; G6 start-date check on-set only; wizard shrinks) | A film in post couldn't be posted at all | §5.3 R05, §6.2, §7.1, S11 |
| 3 | **Picture-lock date** + R20 (required for music and sound design) + R21 (warn under 3 weeks) + R22 (attach post early) + lock-to-due shown on the card | Composers' top pain: brought in late, with a 1-week deadline | §5.3, §6.2, §6.3, §6.5 |
| 4 | **Actors can't be scouted**: cast roles are Applicants-only; the only invite to a non-applicant actor is Rebook from the Keep list | Safety: closes the "post a fake project, browse actors, DM them" route | §3.1, §3.2, §3.5, F7, S09, §7.4, §9.4 |
| 5 | **Profiles are not a public directory**: `profile_cards` is no longer granted to `authenticated`; reads go through relationship-checked RPCs (`get_profile` and others) | Safety: scraped profile data feeds fake-check and casting scams | F16, §9.2, §9.3, §9.4, §10.2 |
| 6 | **Audition request card (F20)**: mode locked to the listing, in-person 08:00-21:00, others present, fixed safety lines, sides and tape as links; the safety tip copy fixed | Safety plus a missing core-loop step for actors | §3.4, F20, §5.6, S15, §9.2 `messages`, §9.4 |
| 7 | **R19**: intimacy or nudity listings with cast need an ID-verified owner | Safety at the highest-risk listings | §5.3 |
| 8 | **No profile photos and no Storage in the MVP** (initials monogram) | Safety (faces-first) and scope | header, §4.3, §6.1, S20, §9.2, §9.5 |
| 9 | **Two-sided accountability**: "Kept bookings" for owners (release, cut or cancel under 72h); Release-sheet warning; `cancel_project` RPC; owner credibility uses it | Gaffer and crew: one-sided accountability is a research never-do | F14, §5.4, §6.4, §6.5, §7.2, S16, §9.4, §14 |
| 10 | **Asks respect hard limits**: reverse G4/G5 checked server-side for Scout and Rebook | Unpaid asks to paid-only crew would kill trust | §3.2, §7.4, §9.4, S19 |
| 11 | **Private rate never leaves the server**: `rate_fit` replaces `min_day_rate` in ranking | Privacy bug: the ranker needed data it was promised never to show | §6.5, §7.4, §9.4 |
| 12 | **Beginner fairness**: reel floor on beginner projects, PORTFOLIO links score, "Beginners welcome" chip | Newcomers would rank last everywhere | §5.4, §6.3, §6.5, §7.4 |
| 13 | **Early cities + India-wide remote roles from day 1** (8 tier-2 cities in reference data; honest header; "my city isn't listed") | Small-city users had no city to choose, and remote roles waited until day 61 | §2, F5, §11.2, S04, S05, §13.2 |
| 14 | **Wizard friction**: required fields first, "More details" collapsed, "Pay for all roles", [Duplicate], ≤4-minute target, time-to-publish metric | Owners abandoning the post step would empty the supply side | F4, §6.2, S11, §14 |
| 15 | **No dead share links**: self-contained share text; `SHARE_BASE_URL` with a Play Store fallback; static fallback page before launch | Share-out is the main acquisition channel | F17, S11, §13.2 |
| 16 | **Closed-app notifications (F21)**: local notifications in demo; WorkManager poll and scheduled reminders in LIVE L1 | The closure guarantee, Ask cards, booking cards and T-48h checks all fail if nobody sees them | F11, F21, §4.3, §10.1 |
| 17 | **Deferred pay** is shown as Unpaid, with an R15 warning | "Deferred never pays"; this closes a loophole for euphemism | §5.3, §5.4 |
| 18 | **Scope for one session**: LIVE split into L0 (this session) and L1 (before launch), with the full schema frozen now; textures and fancy overlays moved to P2; seed cut from 120 to 80 profiles, 36 to 24 open projects and 40 to 20 wrapped, in 7 seeded cities; filters cut from 11 to 7 | The old spec required every feature in both modes, about 20 RPCs and 12 seeded cities in one session | header, §4 intro, §4.2, S08, §11.3-11.5, §12, §15.1 |
| 19 | **Tests and QA updated** for all of the above (ScoutTest, AuditionRequestTest, WrapItemsFactoryTest, R19-R22, timed wizard, post-only publish, Dana's delivery loop, Indore early city, background notification) | Every change has to be checked before anything is shown | §15.2, §15.3 |

### Changes considered and rejected
| Proposal | Raised by | Why rejected |
|---|---|---|
| Kill fee or late-cancellation fee on the booking card | Gaffer | Can't be enforced without payments, and reads as legal terms at micro budgets. "Kept bookings" gives the same accountability as a fact. Moved to Later |
| Overtime terms | Gaffer | The hours cap and the R06 warning cover the main pain. Revisit alongside rate guides |
| Crew packages (a DP or gaffer applies with their team) | Gaffer | Too much graph complexity for one session. A one-line apply note covers it for now. Stays in Later |
| Project team group thread | Director, student | WhatsApp after booking plus the day-sheet broadcast is enough for the MVP. Stays in Later (a must for India v1.0) |
| Batched release windows, so a capped role isn't a first-hour race | Actor | At launch density (about 150 profiles) caps won't fill in minutes. Added a "cap-fill time" metric that triggers the work |
| Message quiet hours | Actor | Useful, but the 08:00-21:00 audition window covers the sharpest risk. Later |
| Detecting studio or channel impersonation in names | Actor | The founder reviews every report at launch scale, and the MONEY and OFF_PLATFORM rules catch what these scams actually ask for. Revisit with moderation data |
| A "women on the crew" signal | Actor | Gender data is sensitive and could itself become a targeting signal. The safety rules apply to everyone regardless |
| ID verification for every cast listing | Actor | Would block small-city owners where concierge checks don't run yet. Scoped to intimacy listings (R19), plus the "ID-verified owners only" filter |
| Mandatory phone OTP for India | Student | Needs an SMS provider. Email OTP stays the default, with a phone tab when one is configured |
| Under-18 accounts | Student | Safety and legal. The MVP stays 18+ only |
| Hindi and regional-language UI | Student | Strings are ready in `strings.xml`, but translation isn't one-session work |
| A list view instead of swiping for owners | Director | Already an open question (REDDIT_RESEARCH §9 Q1), and every swipe already has a button |
| Cut the 7-part fit score | Student | It's pure Kotlin, unit-tested and cheap. The gates do the important work. Kept, but don't tune it before real data |
| Cut Scout entirely | Panel | Scarce crew roles (sound, gaffer, 1st AD) need hirers to pitch to them. Kept for crew, removed for cast |
| Cut demo personas or the simulator | Panel | They're how this session's deliverable is verified (§15.3) |
