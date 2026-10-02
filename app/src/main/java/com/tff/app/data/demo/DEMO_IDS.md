# Demo ids (WS3 seed)

The offline world is built by `DemoSeed.build(anchor, Random(42))`. Every date is relative to the
anchor (the device date at first launch, or at the last **Reset demo**); "d+6" means anchor + 6 days.
Ids are stable across launches and resets. Constants live in `DemoIds.kt`.

**Seed v2** (`DemoSeed.VERSION = 2`, FIX1): every v1 id below is unchanged; 23 OPEN projects (`p_014`…`p_036`) were
added so each persona's Gigs deck starts with at least 10 cards.

**Seed v3** (`DemoSeed.VERSION = 3`, FIXD): every v2 id is unchanged. 11 Bengaluru crew `u_076`…`u_086`
(`DemoPeople.SCOUT_POOL`: DPs `u_076`–`u_079`, sound mixers `u_080`–`u_083`, editors `u_084`–`u_086`) so Rohan's Scout
has ≥3 fitting non-applicants for every open crew role; `u_079` and `u_083` take the Filter Coffee applications that
Keep-list people `u_035` / `u_038` used to hold, so both can be Rebooked. Cutting Chai moved to a studio floor, 12:00–20:00,
with a fuller team (3 booked roles added: `r_cc_ad` Gauri S. `u_010`, `r_cc_sound` Pranav G. `u_007`, `r_cc_hmu`
Priyanka F. `u_018`). Filter Coffee is no longer "Honest zero-budget" (it pays sound). Booking cards carry rights wording
by department (`BookingTermsFactory.rightsNote`). Totals: 91 people · 68 projects (47 OPEN, 20 WRAPPED, 1 DRAFT) ·
307 roles (154 OPEN on OPEN projects) · 154 matches.

Persistence: `DeltaStateStore` keeps a small delta (`demo_state_v2.json`) plus the materialised seed (`demo_seed_v3.json`,
written a few seconds after startup), so a relaunch decodes the seed instead of rebuilding it. Splash holds its mark
("Loading the demo…") until the world is loaded, ticked and warmed up.
Open roles by pay: PAID_DAY 45 · PAID_FLAT 15 · STIPEND 46 · UNPAID 48. Open projects by city: Mumbai 17 · Bengaluru 14 ·
Pune 4 · Indore 1 · Kochi 1 · Los Angeles 4 · Atlanta 6.

QA hook: `adb shell am start -n com.tff.app/.MainActivity --es qa_persona MEERA --es qa_route chat/m_meera_scam [--ez qa_reset true] [--es qa_speed INSTANT]`

## Personas (user ids)

| Persona | User id | Who | Starting state (PRD §11.5) |
|---|---|---|---|
| MEERA | `u_meera` | Meera Kulkarni, actor, Versova, Mumbai | 2 PENDING applications (`a_meera_paper_boats`, `a_meera_dead_drop`); Rebook ask `a_meera_rebook` from Farida A. (`u_002`) for Lead actor on Tiffin Wars; BOOKED on Cutting Chai at d+6 (`m_meera_cutting_chai`, day sheet `sd_cutting_chai_1` published: studio floor in Dadar, crew call 12:00, Meera 12:30, wrap 20:00, not acked); WRAPPED Iron Gate with overdue footage (`m_meera_iron_gate`, item `wi_m_meera_iron_gate_FOOTAGE`); IN TALKS with "Casting Hub Official" (`m_meera_scam`), first message is the tagged scam example (fee + "WhatsApp me" + number, masked) |
| ROHAN | `u_rohan` | Rohan Kamath, writer-director, Jayanagar, Bengaluru | Owns OPEN `p_filter_coffee` (4 open roles, 7/3/10/4 applicants; Scout has ≥3 people for DP, sound and editor); IN TALKS with DP Nithin R. (`m_rohan_dp`); PD booked (`m_fc_pd`, Bhavana M.) with backup #1 Darshan N. (`a_fc_pd_backup`); Keep list Divya S. (`u_045`, applied), Lakshmi P. (`u_038`, can be Rebooked for sound) and Anusha K. (`u_035`, can be Rebooked for DP); DRAFT `p_rohan_draft` ("Mallige") has one R02 blocker, "feed people" (snacks only; travel is paid so the 2-perk rule passes), fix = meals "2+ meals" |
| KABIR | `u_kabir` | Kabir Shaikh, gaffer → DP, Andheri West, Mumbai | PAID_AND_SELECTIVE, step-up DP; deck has 3 step-up DP roles (`r_001_1` Monsoon Static, `r_tr_dp` Two Rooms, `r_dd_dp` Dead Drop); WRAPPED gaffer booking on Rent Day with his feedback pending (`m_kabir_rent_day`, owner already answered); Keep list includes Nikhil D. (`u_028`, Kept bookings 2/4) via `m_kabir_hold_music` |
| DANA | `u_dana` | Dana Robinson, sound mixer, Midtown, Atlanta | PAID_ONLY, private min rate $350, secondary SOUND_DESIGNER; OFFERED card `bc_dana_softlight_1` on `m_dana_softlight`: sound design on Soft Light, picture lock d+5, deliver d+33, $2,400 flat, paid within 7 days of delivery |
| NEW | (`u_new_<n>`) | "Build my own" | `startDemo(null)`: no profile, goes to onboarding. Indore (`indore` / `indore_city_centre`) shows the early-city path |

Persona emails (sign-in with any 6-digit code in demo; `000000` = expired code): `meera@demo.calltime.app`, `rohan@…`, `kabir@…`, `dana@…`.

## Projects

| Id | Title | City | Owner | Notes |
|---|---|---|---|---|
| `p_last_local` | Last Local | Mumbai | `u_001` Aniket D. | §7.5 worked example. Roles `r_ll_lead` (lead actor, unpaid, footage 6 wks, self-tape), `r_ll_support`, `r_ll_sound`. Two Saturdays from d+10; 2 day sheets published (Meera gets call times when booked) |
| `p_roommates` | Roommates Wanted | Mumbai | `u_casting_hub` | The clean-looking listing behind the scam chat. 2 earlier reports; a 3rd report auto-hides the account from decks |
| `p_001` | Monsoon Static | Mumbai | `u_002` | Music video; step-up DP `r_001_1` |
| `p_002` | Salt Pans | Mumbai | `u_019` | Post-only documentary (picture lock d+21) |
| `p_tiffin_wars` | Tiffin Wars | Mumbai | `u_002` | Vertical, d+3. `r_tw_lead` (Meera's Rebook), day sheet `sd_tiffin_wars_1` |
| `p_two_rooms` | Two Rooms | Mumbai | `u_028` | Intimacy scene (non-nude, IC on set; ID-verified owner). Step-up DP `r_tr_dp` |
| `p_paper_boats` | Paper Boats | Mumbai | `u_024` | Student thesis, beginners welcome |
| `p_dead_drop` | Dead Drop | Mumbai | `u_022` | Stunts with coordinator. Step-up DP `r_dd_dp` |
| `p_cutting_chai` | Cutting Chai | Mumbai | `u_002` | d+6 daytime studio shoot (12:00–20:00); Meera booked (`r_cc_lead`), plus DP Omkar J. (`u_005`), 1st AD Gauri S. (`u_010`), sound Pranav G. (`u_007`), hair & makeup Priyanka F. (`u_018`) |
| `p_filter_coffee` | Filter Coffee, 4 AM | Bengaluru | `u_rohan` | `r_fc_dp` (DP stipend ₹800), `r_fc_sound` (₹2,500/day + ₹1,000 kit), `r_fc_actor` (lead, unpaid, footage 6 wks, self-tape, no Scout tab), `r_fc_editor` (remote stipend, post deadline), `r_fc_pd` (booked). Sat–Sun from d+8 |
| `p_rohan_draft` | Mallige | Bengaluru | `u_rohan` | DRAFT failing R02 (meals), fixed by "2+ meals" |
| `p_003`…`p_008` | Second Innings, Photo Studio 1994, Ten to Ten, Malleshwaram Morning, Power Cut, Auto Raja | Bengaluru | various | |
| `p_009` | Second Draft | Pune | `u_051` | Student thesis |
| `p_010` | 48 Frames | Pune | `u_052` | 48-hour challenge |
| `p_rajwada_rain` | Rajwada Rain | Indore | `u_057` (FIRST_PROJECT, no links) | Hindi short, beginners welcome; `r_rr_leads`, `r_rr_writer` (remote, first project) |
| `p_011` | Kayal Songs | Kochi | `u_060` | Music video |
| `p_soft_light` | Soft Light | Los Angeles | `u_063` Elena M. | Post-only. `r_sl_sd` (Dana's offer), `r_sl_composer` |
| `p_012` | Valley Heat 48 | Los Angeles | `u_064` | Remote sound design `r_012_2` (passes Dana's gate) |
| `p_yellow_line` | Yellow Line | Atlanta | `u_071` | Night shoot d+4/d+5; `r_yl_sound` $400/day passes Dana's gate |
| `p_013` | Pawn | Atlanta | `u_072` | Replica firearm with armourer; `r_013_1` $350/day |
| `p_014` | Chawl No. 7 | Mumbai (Malad) | `u_010` | v2. Weekend from d+7. `r_014_1`/`r_014_2` actors, `r_014_3` sound, `r_014_4` gaffer (paid); DP `r_014_5` booked (`u_004`) |
| `p_015` | Sunday Market | Mumbai (Bandra) | `u_016` | v2. One Sunday from d+9. Actors `r_015_1`, `r_015_2`; step-up DP `r_015_3`; sound `r_015_4` |
| `p_016` | The Understudy | Mumbai (Andheri W) | `u_025` | v2. Actors `r_016_1`, `r_016_2`; DP `r_016_3`; sound `r_016_4`; 1st AD booked (`u_010`) |
| `p_017` | Lift Out of Order | Mumbai (Malad) | `u_013` (first project) | v2. Actors `r_017_1` (×2, beginners welcome), `r_017_2`; step-up DP `r_017_3` (unpaid, step-up credit); sound `r_017_4` |
| `p_018` | Mehendi Night | Mumbai (Juhu) | `u_017` | v2. Actors `r_018_1`, `r_018_2`; DP `r_018_3`; sound `r_018_4` |
| `p_019` | Local Train Lullaby | Mumbai (Dadar) | `u_020` | v2 music video. Step-up DP `r_019_1` (paid), gaffer `r_019_2`, remote editor `r_019_3`, remote writer `r_019_4` |
| `p_020` | Rain Check | Mumbai (Powai) | `u_008` | v2 spec ad. Actor `r_020_1`, step-up DP `r_020_2`, sound `r_020_3`, remote colorist `r_020_4` |
| `p_021` | Night Desk | Mumbai (Goregaon) | `u_014` | v2 pilot, 3 weekend days. Gaffer `r_021_1`, sound `r_021_2`, actor `r_021_3`, remote writer `r_021_4`; DP booked (`u_003`) |
| `p_022` | Majestic, 6:40 | Bengaluru (Jayanagar) | `u_030` | v2. Director `r_022_1` (producer-led), actor `r_022_2`, DP `r_022_3`, sound `r_022_4`; PD booked (`u_039`) |
| `p_023` | Thaalam | Bengaluru (Indiranagar) | `u_037` | v2 music video. Director `r_023_1` (flat), DP `r_023_2`, actor `r_023_3`, remote editor `r_023_4` |
| `p_024` | Benne Dosa | Bengaluru (JP Nagar) | `u_043` | v2 spec ad. Director `r_024_1`, actor `r_024_2`, DP `r_024_3`, sound `r_024_4` |
| `p_025` | Hostel Day | Bengaluru (Koramangala) | `u_047` | v2 pilot. Remote writer `r_025_1`, actors `r_025_2`, DP `r_025_3`, sound `r_025_4`; art assistant booked (`u_040`) |
| `p_026` | Notice Period | Bengaluru (HSR Layout) | `u_042` | v2. Actors `r_026_1`, `r_026_2`; DP `r_026_3`; remote writer `r_026_4` |
| `p_027` | Morning Walkers | Bengaluru (Malleshwaram) | `u_039` | v2. Actors `r_027_1` (×3), step-up DP `r_027_2`, sound `r_027_3`, director `r_027_4` |
| `p_028` | Two Brothers, One Gift | Bengaluru (JP Nagar) | `u_050` | v2 student thesis. DP `r_028_1`, sound `r_028_2`, actors `r_028_3`, remote editor `r_028_4` |
| `p_029` | Wada | Pune (Kothrud) | `u_054` | v2. Remote writer `r_029_1`, actors `r_029_2`, sound `r_029_3`, remote editor `r_029_4` |
| `p_030` | Tekdi | Pune (Baner) | `u_056` | v2. Actor `r_030_1`, step-up DP `r_030_2`, remote composer `r_030_3` |
| `p_031` | Swap Meet | Los Angeles | `u_066` | v2. Remote sound design `r_031_1` ($1,500 flat), sound `r_031_2`, actor `r_031_3`, DP `r_031_4`; 1st AD booked (`u_065`) |
| `p_032` | Coverage | Los Angeles | `u_067` | v2. Remote sound design `r_032_1`, remote composer `r_032_2`, gaffer `r_032_3`, actor `r_032_4`; 1st AC booked (`u_070`) |
| `p_033` | Porch Light | Atlanta | `u_074` | v2. Sound `r_033_1` ($400/day), actor `r_033_2`, gaffer `r_033_3`, remote sound design `r_033_4`; grip booked (`u_075`) |
| `p_034` | Grits at Midnight | Atlanta | `u_075` | v2. Sound `r_034_1` ($450/day), DP `r_034_2`, actors `r_034_3`, remote sound design `r_034_4`; camera op booked (`u_074`) |
| `p_035` | Garage Choir | Atlanta | `u_073` | v2 music video. Sound `r_035_1` ($600 flat, one day), DP `r_035_2`, remote editor `r_035_3` |
| `p_036` | Clean Slate | Atlanta | `u_072` | v2 spec ad. Sound `r_036_1` ($375/day), actor `r_036_2`, gaffer `r_036_3` |

Wrapped history (20 projects, ~112 bookings): `p_platform_four`, `p_dabba`, `p_low_tide` (Aniket's 3 shoots), `p_sea_link`, `p_third_floor`, `p_rent_day` (Farida), `p_hold_music` (Nikhil, 2 late releases), `p_iron_gate` (Rakesh, footage 1/4), `p_grey_cast`, `p_bus_stop` (Rohan, kept 3/3), `p_kaapi_nights`, `p_second_shift`, `p_lake_cleaners` (Deepa), `p_half_day`, `p_silk_board`, `p_kothrud_calling`, `p_ferry_times`, `p_low_season`, `p_westbound` (Elena), `p_peachtree` (Malik, paid on time 2/4).

## Track records that QA relies on

| Owner | Record |
|---|---|
| `u_001` Aniket D. | 3 shoots, Footage 3/3, Kept bookings 3/3, no paid history, not ID-verified |
| `u_rohan` | Kept bookings 3/3; becomes 3/4 after a release inside 72h |
| `u_028` Nikhil D. | Kept bookings 2/4 (weak) |
| `u_022` Rakesh Y. | Footage 1/4 (weak) |
| `u_071` Malik J. | Paid on time 2/4 (weak) |
| `u_002`, `u_030`, `u_063` | Strong records |

## Matches, cards, days

`m_meera_scam`, `m_meera_cutting_chai`, `m_meera_iron_gate`, `m_rohan_dp`, `m_fc_pd`, `m_kabir_rent_day`,
`m_kabir_hold_music`, `m_dana_softlight` · cards `bc_<matchId>_<version>` (e.g. `bc_dana_softlight_1`) ·
day sheets `sd_<project>_<n>` (e.g. `sd_cutting_chai_1`, `sd_last_local_1`) · wrap items `wi_<matchId>_<KIND>`.
Other seeded ids: people `u_001`…`u_075`, applications `a_0001`…, history matches `m_<project>_<person>`,
messages `msg_00001`…, notifications `n_0001`…, reports `rep_0001`/`rep_0002`. Runtime ids: `<prefix>_<seq>` from 1000.

## Starting Gigs decks (after reset, before any swipe)

Measured by `DeckDensityTest` (app/src/test/java/com/tff/app/integration): the real deck, `MatchEngine.buildDeck` over
`discoverCandidates` with the viewer built from `me()` + `myPrivateMinRate()`, on all 7 weekday anchors. Only gate-passing
cards are counted (small-market fallback cards are not). Order varies with the anchor.

| Persona | Cards | Deck |
|---|---|---|
| MEERA | 14 (15 on a Tuesday anchor) | Actor roles only: `r_ll_lead`, `r_ll_support`, `r_014_1`, `r_014_2`, `r_015_1`, `r_015_2`, `r_016_1`, `r_016_2`, `r_017_1`, `r_017_2`, `r_018_1`, `r_018_2`, `r_020_1`, `r_021_3` (+ `r_tr_leads` when both Two Rooms days fall on a weekend) |
| ROHAN | 11 | Director `r_022_1`, `r_023_1`, `r_024_1`, `r_027_4`; remote writing `r_005_3`, `r_rr_writer`, `r_019_4`, `r_021_4`, `r_025_1`, `r_026_4`, `r_029_1` |
| KABIR | 11 | Gaffer `r_001_2`, `r_014_4`, `r_019_2`, `r_021_1`; step-up DP `r_001_1`, `r_tr_dp`, `r_dd_dp`, `r_015_3`, `r_017_3`, `r_019_1`, `r_020_2` |
| DANA | 11 | US only, all paid in USD: Atlanta sound `r_yl_sound`, `r_013_1`, `r_033_1`, `r_034_1`, `r_035_1`, `r_036_1`; remote sound design `r_012_2`, `r_031_1`, `r_032_1`, `r_033_4`, `r_034_4` |
| NEW | ≥ 8 | "Build my own" in Mumbai (Andheri West, Bandra) or Bengaluru (Koramangala, Jayanagar) as actor / DP / editor / sound mixer, any-day or weekends-only, paid-and-selective or open to unpaid. Minimums: actor 16 (Mumbai) / 11 (Bengaluru), DP 8 / 9, editor 11, sound mixer 9 / 8 |

Rules behind these decks (FIX1):
- **Live projects aren't offered again.** A project where the viewer has an active match (TALKING, OFFERED, BOOKED) or an
  application or Ask that is PENDING, on BACKUP or MATCHED is dropped from `discoverCandidates`. Meera never sees
  `r_cc_support` (booked on Cutting Chai), nor Paper Boats, Dead Drop, Tiffin Wars (Rebook ask) or Roommates (in talks).
- **Booked dates are busy.** `DemoIndex.bookedDates`: shoot days of BOOKED on-set matches plus the person's own OPEN/CREWED
  projects, from today on. `me()` returns them inside `busyDates` (so MatchEngine avail / G3 sees them);
  `saveProfile` strips them again so a booking never becomes a manual busy date. `discoverCandidates` also drops on-set
  roles that clash (cast: any shoot day booked; crew: every shoot day booked). Scout, applicant ranking and the
  simulator's reverse gates use the same dates.
- **Local first (FIXD).** A local card (on-set nearby, or remote work for a project in the viewer's city) never ranks
  below a remote role from another city, and remote work in your own city scores on-set proximity plus a same-city bonus
  (`MatchEngine.isLocal`). An Indore first-project writer gets `r_rr_writer` (Rajwada Rain) first.
- **Remote stays in-country.** Remote roles are offered only in the viewer's own country (guests: the browsed city's);
  MatchEngine G2 enforces the same (`Markets`, `Viewer.country`). G4 never compares a min rate across currencies.

`Wave1IntegrationTest` (app/src/test/java/com/tff/app/integration) checks that every id in this file and in
`DemoIds.kt` exists in the seed, the track records above, and a non-empty deck for every persona on every weekday anchor;
`DeckDensityTest` checks the sizes above.

## Simulator rules worth knowing (§11.6)

- A persona's **first** application after a reset is always answered with "Let's talk" (so the QA loop runs); later ones use p = clamp(0.15 + 0.7 × score / 100, 0.2, 0.85).
- The **first** booking card a persona sends as an owner is always accepted; later ones 90%, else "Ask to change".
- Typing "booking", "confirm" or "offer" in a chat with a seeded owner makes them send the card at once.
- Weak owners never deliver footage (`u_022`) or pay late (`u_071`, `u_028`), so the signals stay visible.
- Speed: Settings → demo speed NORMAL (1×), FAST (⅓ of the delays), INSTANT (no wait).
- A newly published project gets 3–8 applicants per role from people who pass the reverse gates, always leaving 3 fitting
  people un-applied for Scout (`DemoSimulator.pickApplicants`); only a small market is topped up to 3 from the same country.
- Blocking someone hides them everywhere, older notifications and Inbox updates included (`DemoQueries.blockedMentions`).
- "Inject flagged example" creates the runtime account `u_scam_bright_star` / `p_scam_bright_star`, already hidden from decks by 3 reports.
