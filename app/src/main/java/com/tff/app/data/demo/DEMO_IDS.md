# Demo ids (WS3 seed)

The offline world is built by `DemoSeed.build(anchor, Random(42))`. Every date is relative to the
anchor (the device date at first launch, or at the last **Reset demo**); "d+6" means anchor + 6 days.
Ids are stable across launches and resets. Constants live in `DemoIds.kt`.

QA hook: `adb shell am start -n com.tff.app/.MainActivity --es qa_persona MEERA --es qa_route chat/m_meera_scam [--ez qa_reset true] [--es qa_speed INSTANT]`

## Personas (user ids)

| Persona | User id | Who | Starting state (PRD §11.5) |
|---|---|---|---|
| MEERA | `u_meera` | Meera Kulkarni, actor, Versova, Mumbai | 2 PENDING applications (`a_meera_paper_boats`, `a_meera_dead_drop`); Rebook ask `a_meera_rebook` from Farida A. (`u_002`) for Lead actor on Tiffin Wars; BOOKED on Cutting Chai at d+6 (`m_meera_cutting_chai`, day sheet `sd_cutting_chai_1` published, call 16:30 not acked); WRAPPED Iron Gate with overdue footage (`m_meera_iron_gate`, item `wi_m_meera_iron_gate_FOOTAGE`); IN TALKS with "Casting Hub Official" (`m_meera_scam`), first message is the tagged scam example (fee + "WhatsApp me" + number, masked) |
| ROHAN | `u_rohan` | Rohan Kamath, writer-director, Jayanagar, Bengaluru | Owns OPEN `p_filter_coffee` (4 open roles, 7/3/10/4 applicants); IN TALKS with DP Nithin R. (`m_rohan_dp`); PD booked (`m_fc_pd`, Bhavana M.) with backup #1 Darshan N. (`a_fc_pd_backup`); DRAFT `p_rohan_draft` ("Mallige") fails R02 only, fix = meals "2+ meals" |
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
| `p_cutting_chai` | Cutting Chai | Mumbai | `u_002` | d+6 evening shoot; Meera booked (`r_cc_lead`) |
| `p_filter_coffee` | Filter Coffee, 4 AM | Bengaluru | `u_rohan` | `r_fc_dp` (DP stipend ₹800), `r_fc_sound` (₹2,500/day + ₹1,000 kit), `r_fc_actor` (lead, unpaid, footage 6 wks, self-tape, no Scout tab), `r_fc_editor` (remote stipend, post deadline), `r_fc_pd` (booked). Sat–Sun from d+8 |
| `p_rohan_draft` | Mallige | Bengaluru | `u_rohan` | DRAFT failing R02 |
| `p_003`…`p_008` | Second Innings, Photo Studio 1994, Ten to Ten, Malleshwaram Morning, Power Cut, Auto Raja | Bengaluru | various | |
| `p_009` | Second Draft | Pune | `u_051` | Student thesis |
| `p_010` | 48 Frames | Pune | `u_052` | 48-hour challenge |
| `p_rajwada_rain` | Rajwada Rain | Indore | `u_057` (FIRST_PROJECT, no links) | Hindi short, beginners welcome; `r_rr_leads`, `r_rr_writer` (remote, first project) |
| `p_011` | Kayal Songs | Kochi | `u_060` | Music video |
| `p_soft_light` | Soft Light | Los Angeles | `u_063` Elena M. | Post-only. `r_sl_sd` (Dana's offer), `r_sl_composer` |
| `p_012` | Valley Heat 48 | Los Angeles | `u_064` | Remote sound design `r_012_2` (passes Dana's gate) |
| `p_yellow_line` | Yellow Line | Atlanta | `u_071` | Night shoot d+4/d+5; `r_yl_sound` $400/day passes Dana's gate |
| `p_013` | Pawn | Atlanta | `u_072` | Replica firearm with armourer; `r_013_1` $350/day |

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

Decks are small by design (about 60 open roles across 7 cities, and G1 gates on the person's own roles), so
Discover must handle a 2-4 card deck and its end-of-deck state. Order can vary; `*` = weekday-dependent.

| Persona | Cards |
|---|---|
| MEERA | `r_ll_lead`, `r_ll_support`, plus one `*` (Sun/Mon anchor: `r_cc_support`; Tue: `r_tr_leads`; Wed/Thu: `r_tw_support`; Fri/Sat: none) |
| ROHAN | `r_005_3`, `r_rr_writer` |
| KABIR | `r_001_2`, `r_tr_dp`, `r_001_1`, `r_dd_dp` |
| DANA | `r_yl_sound`, `r_013_1`, `r_012_2` |

`Wave1IntegrationTest` (app/src/test/java/com/tff/app/integration) checks that every id in this file and in
`DemoIds.kt` exists in the seed, the track records above, and a non-empty deck for every persona on every weekday anchor.

## Simulator rules worth knowing (§11.6)

- A persona's **first** application after a reset is always answered with "Let's talk" (so the QA loop runs); later ones use p = clamp(0.15 + 0.7 × score / 100, 0.2, 0.85).
- The **first** booking card a persona sends as an owner is always accepted; later ones 90%, else "Ask to change".
- Typing "booking", "confirm" or "offer" in a chat with a seeded owner makes them send the card at once.
- Weak owners never deliver footage (`u_022`) or pay late (`u_071`, `u_028`), so the signals stay visible.
- Speed: Settings → demo speed NORMAL (1×), FAST (⅓ of the delays), INSTANT (no wait).
- "Inject flagged example" creates the runtime account `u_scam_bright_star` / `p_scam_bright_star`, already hidden from decks by 3 reports.
