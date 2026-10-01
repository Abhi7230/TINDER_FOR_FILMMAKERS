# Calltime build guide (for every engineer and agent)

The product spec is `research/PRD.md` (read the sections your workstream owns, plus §3 core loop, §6 schemas, §12 visual direction and Appendix D copy rules). Evidence is in `research/REDDIT_RESEARCH.md`.

## Stack (fixed, no new dependencies)
Kotlin 2.4.20 · AGP 9.4.1 (built-in Kotlin) · Gradle 9.8 · compileSdk 37 / targetSdk 36 / minSdk 26 · Jetpack Compose (BOM 2026.09.00) + Material 3 + material-icons-extended · navigation-compose 2.10 (type-safe `@Serializable` routes) · lifecycle 2.11 · kotlinx-serialization 1.11 · coroutines 1.11 · Coil 3 · supabase-kt 3.8 (auth, postgrest, realtime) + ktor-okhttp. JUnit 4 + coroutines-test for unit tests. Do not add dependencies; if you think you need one, report it instead.

## Commands (always through the lock: parallel agents share one machine)
```bash
export JAVA_HOME="/Users/abhi/Applicationss/Android Studio.app/Contents/jbr/Contents/Home"
tools/lockrun.py -- ./gradlew assembleDebug --console=plain -q        # build
tools/lockrun.py -- ./gradlew testDebugUnitTest --console=plain -q    # unit tests
tools/emu.py shot --persona MEERA --route discover --out /tmp/x.png   # install + launch + screenshot (holds the emulator lock)
tools/emu.py steps "launch ROHAN crewup" "wait 2" "tapText Let's talk" "shot /tmp/y.png"
```
Never run `./gradlew` without `tools/lockrun.py --`. Never run two emulators. Use the `Read` tool on screenshot PNGs to look at them.

## Package map (`app/src/main/java/com/tff/app`)
| Path | What | Owner |
|---|---|---|
| `core/` | AppClock (demo offset), Geo, Money, Dates, Ids, serializers | contracts (frozen) |
| `model/` | Enums, Taxonomy, Models — the data contract | contracts (frozen) |
| `data/Repo.kt` | THE Repo interface + DemoControls | contracts (frozen) |
| `domain/` | Pure Kotlin: MatchEngine, ApplicantRanker, ListingRules, MessageSafety, Booking (terms/diff/wrap/audition/notice), ReputationCalc, CardText, ShareText | WS2 (signatures frozen, bodies theirs) |
| `data/demo/` | DemoRepo + seed + store + simulator | WS3 |
| `data/remote/` + `supabase/` | SupabaseRepo + SQL migrations | WS8 |
| `ui/theme/` | Color/Type/Shape/Theme tokens (frozen) + Motion/Grain (WS1) | contracts / WS1 |
| `ui/components/` | Design system components + ComponentGallery | WS1 |
| `nav/`, `di/`, `MainActivity`, `App` | Navigation, DI, QA hooks | contracts (frozen) |
| `ui/screens/onboarding, me, safety, settings` + `notify/` | S01–S04, S19–S24, F21 demo notifications | WS7 |
| `ui/screens/discover, role, crewup, person` | S05–S10 | WS4 |
| `ui/screens/post, projects` | S11–S13, S16E | WS5 |
| `ui/screens/inbox, chat, booking, shootday, wrap` | S14–S18, S16V, S16W, F20 audition | WS6 |

## Rules
1. **Edit only the files your workstream owns.** Frozen contracts may not be changed. If a contract truly blocks you, make the smallest *additive* change (new optional field with a default, new function) and list it under "Contract changes" in your final report. Never rename or remove anything.
2. **Strings**: put your screen copy in `res/values/strings_<ws>.xml` (e.g. `strings_ws4.xml`), never in the shared `strings.xml`. Inline literals are acceptable for copy that is formatted from data.
3. **Screens** keep their exact signatures (see `nav/NavGraph.kt`). Navigate only via `AppNavigator` (`nav.toChat(id)`, `nav.back()`, `nav.share(text)`, `nav.toast(msg)`). Get a ViewModel with `rememberVm { repo -> MyViewModel(repo) }` (pass `key =` for per-id VMs). ViewModels expose `StateFlow<UiState>`; every screen handles Loading, Error ("Couldn't load. Retry") and Empty states.
4. **Insets**: tab screens (Discover, Projects, Inbox, Me) sit inside the bottom-nav scaffold: they draw their own top bar with status-bar insets and must NOT add bottom/navigation-bar padding. Full-screen destinations use their own `Scaffold` (status + navigation bar insets) and `imePadding()` for inputs.
5. **Guests** (`repo.session.value == null`): Discover and Role detail work read-only; any action opens the sign-in prompt (`GuestGate` / `SignInPromptSheet` from ui/components). Projects / Inbox / Me show a friendly "Sign in to…" empty state with a button to `nav.toWelcome()`.
6. **Design**: only `Ct.colors` / `Ct.type` / `CtShapes` tokens, never raw colours. Dark "darkroom" look. Display font (Barlow Condensed) for slate labels and role lines, Inter for body, JetBrains Mono for dates, call times, rates and counters. No hearts, flames or pink. 48dp minimum touch targets; every swipe also has a button and a TalkBack custom action; content descriptions on icon buttons and chips.
7. **Copy** (PRD Appendix D): say Interested · Pass · In talks · Booking card · Booked · Call time · Crew up · Gig · Role · Unpaid (with what you get) · Stipend · "Every applicant gets an answer." Never say: It's a match · Tinder · hot · collab · exposure · opportunity · verified project · vetted · background checked · DM me. Plain, crew-to-crew, no exclamation-mark hype; errors say how to fix.
8. **Domain code is pure Kotlin** (no `android.*`), unit-tested in `app/src/test/java/com/tff/app/...`.
9. **Demo mode is the deliverable**: everything must work offline with seeded data. LIVE-only gaps are hidden using `repo.features`.
10. **Stuck?** Max 3 attempts on any one problem, then leave a clear TODO, move on, and report it.

## QA hooks (debug builds)
`adb shell am start -n com.tff.app/.MainActivity --es qa_persona MEERA --es qa_route chat/m_xxx [--ez qa_reset true] [--es qa_speed INSTANT]`
Personas: MEERA (actor), ROHAN (writer-director/owner), KABIR (gaffer→DP), DANA (sound mixer, US), NEW (fresh user → onboarding). Routes: see `NavigatorImpl.go()` in `nav/NavGraph.kt` (discover, projects, inbox, me, role/{id}, crewup/{roleId}, person/{id}, post, dashboard/{projectId}, shootday-edit/{projectId}, chat/{matchId}, booking/{matchId}, shootday/{projectId}/{dayId}, wrap/m/{matchId}, wrap/p/{projectId}, editprofile, safety, settings, saved, trust/{PAGE}, gallery, welcome, onboarding).

Stable demo ids (from WS3's seed) are documented in `data/demo/DEMO_IDS.md`.
