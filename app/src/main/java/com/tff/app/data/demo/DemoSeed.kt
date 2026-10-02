package com.tff.app.data.demo

import com.tff.app.data.demo.SeedKit.DAY
import com.tff.app.data.demo.SeedKit.HOUR
import com.tff.app.model.*
import com.tff.app.model.Ambition.*
import com.tff.app.model.AuditionMode.*
import com.tff.app.model.CompType.*
import com.tff.app.model.Format.*
import com.tff.app.model.LocationType.*
import com.tff.app.model.Meals.*
import com.tff.app.model.PaymentTerms.*
import com.tff.app.model.RoleCode.*
import com.tff.app.model.Transport.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Deterministic demo world (PRD §11): `DemoSeed.build(anchor, Random(42))`. Every date is relative to
 * [anchor]; ids are stable (u_001, p_001, r_001_1, plus readable persona ids, see DEMO_IDS.md).
 */
object DemoSeed {
    /** Phase timings (ms) of the last build, for the startup log. */
    @Volatile var lastTimings: String = ""

    /** Bump whenever the seed's content changes, so saved deltas against an older seed are discarded. */
    const val VERSION = 3

    fun defaultNow(anchor: LocalDate, zone: ZoneId): Long = SeedKit.millisAt(anchor, LocalTime.of(9, 30), zone)

    /** Cutting Chai (Meera's booked shoot): a studio floor, crew call 12:00, Meera 12:30, wrap 20:00. */
    const val CC_ADDRESS = "Studio 2, Kelkar Film Floors, N. C. Kelkar Road, Dadar West, Mumbai"
    val CC_CALL: LocalTime = LocalTime.of(12, 0)
    val CC_MEERA_CALL: LocalTime = LocalTime.of(12, 30)
    val CC_WRAP: LocalTime = LocalTime.of(20, 0)

    fun build(
        anchor: LocalDate,
        rnd: Random = Random(42),
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = defaultNow(anchor, zone),
    ): DemoState = Builder(anchor, rnd, zone, nowMillis).build()

    // ─────────────────────────────────────────────────────────────────────────────────────────────

    private data class Crew(
        val pid: String,
        val code: RoleCode,
        val comp: CompType,
        val amount: Int? = null,
        val kit: Int? = null,
        val footage: Int? = null,
        val finalFilm: Int? = null,
        val postDays: Int? = null,               // post role: delivery = end + postDays
        val outcome: Outcome = Outcome.NORMAL,
        val notice: Int = 0,                     // released: hours of notice
        val perks: Set<Perk> = emptySet(),
        val terms: PaymentTerms? = null,
    )

    private enum class Outcome { NORMAL, RELEASED, FOOTAGE_OPEN, FOOTAGE_NOT_RECEIVED, PAID_LATE, BOOKED_POST }

    private data class Wrapped(
        val id: String, val owner: String, val title: String, val logline: String, val format: Format, val runtime: Int,
        val areaId: String, val endAgo: Int, val days: Int, val langs: List<String>, val genres: List<String>,
        val crew: List<Crew>, val postOnly: Boolean = false, val lodging: Boolean = false,
    )

    private class Builder(val anchor: LocalDate, val rnd: Random, val zone: ZoneId, val now: Long) {
        val profiles = linkedMapOf<String, Profile>()
        val rates = linkedMapOf<String, Int>()
        val emails = linkedMapOf<String, String>()
        val projects = linkedMapOf<String, Project>()
        val roles = linkedMapOf<String, ProjectRole>()
        val apps = linkedMapOf<String, Application>()
        val matches = linkedMapOf<String, Match>()
        val messages = mutableListOf<Message>()
        val cards = linkedMapOf<String, BookingCard>()
        val days = linkedMapOf<String, ShootDay>()
        val calls = mutableListOf<CallTime>()
        val items = linkedMapOf<String, WrapItem>()
        val feedback = mutableListOf<Feedback>()
        val reports = mutableListOf<ReportRow>()
        val notes = mutableListOf<NotificationRow>()
        val readMarks = linkedMapOf<String, Long>()
        var msgSeq = 1
        var appSeq = 1

        fun d(k: Int): LocalDate = anchor.plusDays(k.toLong())
        fun sat(k: Int): LocalDate = SeedKit.nextDow(d(k), DayOfWeek.SATURDAY)
        fun sun(k: Int): LocalDate = SeedKit.nextDow(d(k), DayOfWeek.SUNDAY)
        fun fri(k: Int): LocalDate = SeedKit.nextDow(d(k), DayOfWeek.FRIDAY)
        fun at(date: LocalDate, h: Int, m: Int = 0): Long = SeedKit.millisAt(date, LocalTime.of(h, m), zone)
        fun ago(hours: Double): Long = now - (hours * HOUR).toLong()
        fun cityOf(areaId: String): City = DemoCities.cityOfArea(areaId) ?: error("unknown area $areaId")

        fun build(): DemoState {
            val laps = StringBuilder()
            var t = System.nanoTime()
            fun lap(name: String) { val n = System.nanoTime(); laps.append(name).append(' ').append((n - t) / 1_000_000).append(" · "); t = n }
            buildProfiles(); lap("profiles")
            buildOpenProjects(); lap("open")
            buildHistory(); lap("history")
            buildPersonaStates(); lap("personas")
            buildMoreOpenProjects(); lap("more")
            buildFillerApplications(); lap("filler")
            buildReportsAndNotifications(); lap("notes")
            alignCallWindows()
            recount(); lap("recount")
            lastTimings = laps.toString()
            return DemoState(
                anchor = anchor,
                seedNowMillis = now,
                seedZone = zone.id,
                realUsers = DemoIds.PERSONA_USERS,
                profiles = profiles,
                privateRates = rates,
                emails = emails,
                projects = projects,
                roles = roles,
                applications = apps,
                matches = matches,
                messages = messages.sortedBy { it.createdAt },
                bookingCards = cards,
                shootDays = days,
                callTimes = calls,
                wrapItems = items,
                feedback = feedback,
                reports = reports,
                notifications = notes,
                readMarks = readMarks,
                seq = 1000,
            )
        }

        // ───────────── People ─────────────

        private val tastePool = listOf(
            "Court", "The Lunchbox", "Masaan", "Killa", "Thithi", "Lucia", "Kumbalangi Nights", "Ship of Theseus", "Tumbbad",
            "Sairat", "Peepli Live", "Andhadhun", "Pather Panchali", "Kammatipaadam", "In the Mood for Love", "Moonlight",
            "Paris, Texas", "Drive My Car", "The Florida Project", "Aftersun", "Roma", "Shoplifters", "Parasite", "Lady Bird",
            "Minari", "Past Lives", "Nomadland", "Arrival", "Children of Men", "The Conversation",
        )
        private val genrePool = listOf("Drama", "Thriller", "Comedy", "Romance", "Horror", "Documentary", "Crime", "Coming-of-age", "Music")

        private fun buildProfiles() {
            for (s in DemoPeople.all) {
                val created = now - (30 + rnd.nextInt(370)) * DAY
                val active = now - (rnd.nextDouble() * 60 * HOUR).toLong()
                val links = links(s)
                val busy = busyDates(s)
                val taste = s.taste ?: List(rnd.nextInt(0, 6)) { tastePool[rnd.nextInt(tastePool.size)] }.distinct()
                val genres = s.genres ?: List(1 + rnd.nextInt(2)) { genrePool[rnd.nextInt(genrePool.size)] }.distinct()
                val remote = s.remote || Taxonomy.dept(s.role) in Taxonomy.POST_DEPTS
                profiles[s.id] = Profile(
                    id = s.id, displayName = s.name, pronouns = s.pronouns, primaryRole = s.role, secondaryRoles = s.secondary,
                    stepUpRoles = s.stepUp, level = s.level, cityId = cityOf(s.areaId).id, areaId = s.areaId,
                    travelRadiusKm = s.radius, travelIfCovered = s.travelIfCovered, remoteOk = remote, languages = s.langs,
                    availabilityPattern = s.avail, busyDates = busy, compMode = s.comp, kitFeeDay = s.kit,
                    unpaidMustHaves = if (s.comp == CompMode.PAID_ONLY) emptyList() else s.mustHaves, goals = s.goals,
                    contentWontDo = s.wontDo, workLinks = links, gear = s.gear, tools = s.tools, tasteFilms = taste.take(5),
                    genresPref = genres, formatsPref = s.formats, bio = s.bio, affiliations = s.affiliations,
                    emailVerified = true, phoneVerified = s.phone, idVerified = s.idVerified, adultConfirmed = true,
                    createdAt = created, lastActiveAt = active,
                )
                s.minRate?.let { rates[s.id] = it }
                emails[s.id] = s.id.removePrefix("u_").replace('_', '.') + "@demo.calltime.app"
            }
            DemoPersona.entries.forEach { emails[DemoIds.personaUser(it)] = DemoIds.personaEmail(it) }
        }

        private fun links(s: PersonSpec): List<WorkLink> {
            if (s.noLinks) return emptyList()
            val out = mutableListOf<WorkLink>()
            var n = 1
            fun add(kind: LinkKind, title: String, role: RoleCode? = s.role, year: Int? = null) {
                out += WorkLink("wl_${s.id.removePrefix("u_")}_$n", "https://demo.calltime.app/${kind.name.lowercase()}/${s.id}-$n", kind, role, title, year)
                n++
            }
            val year = anchor.year
            if (s.level == Level.FIRST_PROJECT) {
                if (rnd.nextBoolean()) add(LinkKind.PORTFOLIO, if (Taxonomy.isCast(s.role)) "College play (recording)" else "Phone-shot short", year = year)
            } else {
                val reels = 1 + rnd.nextInt(3)
                val reelTitles = when (Taxonomy.dept(s.role)) {
                    Dept.CAST -> listOf("Showreel $year", "Scene: kitchen argument", "Theatre excerpt")
                    Dept.SOUND -> listOf("Sound reel $year", "Before/after dialogue clean-up", "Location sound breakdown")
                    Dept.MUSIC -> listOf("Score reel $year", "Cue: night drive", "Strings and synth suite")
                    Dept.POST -> listOf("Edit reel $year", "Short film cut", "Before/after grade")
                    else -> listOf("Reel $year", "Short film excerpt", "Music video")
                }
                repeat(reels) { add(LinkKind.REEL, reelTitles[it % reelTitles.size], year = year - it) }
            }
            if (s.imdb) add(LinkKind.IMDB, "IMDb page", role = null)
            if (s.role == ACTOR && s.level != Level.FIRST_PROJECT) add(LinkKind.HEADSHOT, "Headshot", role = ACTOR)
            return out
        }

        private val personaBusy: Map<String, List<Int>> = mapOf(
            DemoIds.MEERA to listOf(2, 9), DemoIds.KABIR to listOf(5, 12), DemoIds.DANA to listOf(16), DemoIds.ROHAN to emptyList(),
        )

        private fun busyDates(s: PersonSpec): Set<LocalDate> {
            personaBusy[s.id]?.let { ks ->
                // Personas: fixed weekday busy dates that never clash with their scripted projects.
                return ks.map { k -> var x = d(k); while (x.dayOfWeek == DayOfWeek.SATURDAY || x.dayOfWeek == DayOfWeek.SUNDAY) x = x.plusDays(1); x }.toSet()
            }
            val n = rnd.nextInt(0, 7)
            return List(n) { d(1 + rnd.nextInt(45)) }.toSet()
        }

        // ───────────── Projects & roles ─────────────

        fun project(
            id: String, owner: String, title: String, logline: String, format: Format, runtime: Int, genres: List<String>,
            langs: List<String>, areaId: String, dates: List<LocalDate>, stage: Stage = Stage.PRE, script: ScriptStatus = ScriptStatus.LOCKED,
            ambition: Ambition = PORTFOLIO, zeroBudget: Boolean = false, location: LocationType? = MIXED,
            certainty: DateCertainty? = DateCertainty.LOCKED, hours: Int? = 10, meals: Meals? = TWO_PLUS_MEALS,
            transport: Transport? = Transport.NONE, ta: Int? = null, lodging: Boolean? = null, flags: Set<ContentFlag> = emptySet(),
            ic: Boolean? = null, stunt: Boolean? = null, safety: String? = null, audition: AuditionMode? = null,
            lock: LocalDate? = null, story: String? = null, published: Double = 72.0, status: ProjectStatus = ProjectStatus.OPEN,
            address: String? = null, lookbook: Boolean = false, readable: Boolean = false, pages: Int? = null,
        ): Project {
            val postOnly = dates.isEmpty()
            val city = cityOf(areaId)
            val p = Project(
                id = id, ownerId = owner, payerId = owner, title = title, logline = logline, format = format, runtimeMin = runtime,
                pageCount = pages ?: (runtime + 2), genres = genres, languages = langs, stage = if (postOnly) Stage.POST else stage,
                scriptStatus = script, scriptReadable = readable,
                lookbookUrl = if (lookbook) "https://demo.calltime.app/lookbook/${id.removePrefix("p_")}" else null,
                ambition = ambition, honestZeroBudget = zeroBudget, cityId = city.id, areaId = areaId,
                shootLocationType = if (postOnly) null else location, exactAddress = address, storySetting = story,
                shootDates = dates.sorted(), pictureLockDate = lock, dateCertainty = if (postOnly) null else certainty,
                hoursCap = if (postOnly) null else hours, meals = if (postOnly) null else meals,
                transport = if (postOnly) null else transport, travelAllowancePerDay = ta,
                lodging = if (postOnly) null else (lodging ?: if (dates.size > 1) false else null),
                contentFlags = flags, contentNoneConfirmed = flags.isEmpty(), intimacyCoordinator = ic, stuntOrArmourer = stunt,
                safetyMeasures = safety, auditionMode = audition,
                bringFriendWelcome = location == PRIVATE_HOME || audition == STUDIO_OR_PUBLIC_WITH_OTHERS,
                insurance = YesNoNa.NOT_YET, permits = if (location == OUTDOOR_PUBLIC) YesNoNa.YES else YesNoNa.NA,
                status = status, createdAt = ago(published + 30), publishedAt = if (status == ProjectStatus.DRAFT) null else ago(published),
                updatedAt = ago(published),
            )
            projects[id] = p
            return p
        }

        fun role(
            id: String, p: Project, code: RoleCode, comp: CompType, amount: Int? = null, kit: Int? = null, terms: PaymentTerms? = null,
            perks: Set<Perk> = emptySet(), footage: Int? = null, finalFilm: Int? = null, remote: Boolean = false,
            deadline: LocalDate? = null, count: Int = 1, stepUp: Boolean = false, level: Level? = null,
            details: Map<String, String> = emptyMap(), question: String? = null, gear: GearPolicy? = null,
            scope: List<String> = emptyList(), status: RoleStatus = RoleStatus.OPEN,
        ): ProjectRole {
            val city = cityOf(p.areaId)
            val allPerks = perks + (if (footage != null) setOf(Perk.FOOTAGE_BY_DATE) else emptySet()) +
                (if (finalFilm != null) setOf(Perk.FINAL_FILM_BY_DATE) else emptySet())
            val gearPolicy = gear ?: if (Taxonomy.dept(code) in Taxonomy.GEAR_DEPTS) {
                if (kit != null) GearPolicy.BYO_WITH_KIT_FEE else GearPolicy.PRODUCTION_PROVIDES
            } else null
            val r = ProjectRole(
                id = id, projectId = p.id, roleCode = code, count = count, creditTitle = Taxonomy.info(code).defaultCredit,
                scopeIncludes = scope.ifEmpty { Taxonomy.info(code).scopeSuggestions.take(2) },
                stepUpFriendly = stepUp, levelWanted = level, compType = comp,
                amountMin = if (comp == UNPAID) null else amount, amountMax = if (comp == UNPAID) null else amount,
                currency = city.currency, kitFeeDay = kit,
                paymentTerms = if (comp == UNPAID) null else (terms ?: if (deadline != null && remote) WITHIN_7_DAYS else ON_WRAP),
                perks = allPerks, footageDueWeeks = footage, finalFilmDueWeeks = finalFilm, gearPolicy = gearPolicy,
                remoteOk = remote, postDeadline = deadline, roleDetails = details, screeningQuestion = question,
                applicationCap = Taxonomy.defaultCap(code), status = status,
            )
            roles[id] = r
            return r
        }

        private fun lead(character: String, age: String? = null) = buildMap {
            put("size", "LEAD"); put("character", character); age?.let { put("playing_age", it) }; put("sides", "Y")
        }
        private fun support(character: String, age: String? = null) = buildMap {
            put("size", "SUPPORTING"); put("character", character); age?.let { put("playing_age", it) }
        }

        private fun buildOpenProjects() {
            // ── Mumbai (9) ──
            val ll = project(DemoIds.P_LAST_LOCAL, DemoIds.LAST_LOCAL_OWNER, "Last Local",
                "On the last train to Virar, a pickpocket realises the woman she's robbing is her future.",
                SHORT, 15, listOf("Drama"), listOf("hi"), "mumbai_andheri_west", listOf(sat(10), sat(10).plusDays(7)),
                ambition = FESTIVAL, location = OUTDOOR_PUBLIC, transport = MILEAGE_OR_TA_PAID, ta = 300,
                flags = setOf(ContentFlag.NIGHT_SHOOT), audition = SELF_TAPE,
                safety = "Night shoot on platforms and a moving local: two crew always with cast, and a car back for anyone after midnight.",
                story = "Set on the last Virar fast · shot at Andheri and on board", published = 40.0,
                address = "Platform 1 parking, Andheri station (west), Andheri West, Mumbai")
            role(DemoIds.R_LL_LEAD, ll, ACTOR, UNPAID, footage = 6, details = lead("Asha, the pickpocket", "22-28"))
            role("r_ll_support", ll, ACTOR, UNPAID, footage = 6, details = support("The woman on the 11:52", "35-45"))
            role("r_ll_sound", ll, SOUND_MIXER, STIPEND, 1500, kit = 500, details = mapOf("speaking_max" to "2", "int_ext" to "10/90", "boom_op" to "N"))

            val rm = project(DemoIds.P_ROOMMATES, DemoIds.CASTING_HUB, "Roommates Wanted",
                "Three strangers answer the same flat-share ad in Lower Parel and find out the landlord promised the room to all of them.",
                SERIES_PILOT, 22, listOf("Comedy"), listOf("hi", "en"), "mumbai_lower_parel", listOf(d(12), d(13), d(14)),
                ambition = DISTRIBUTION, location = STUDIO, meals = ONE_MEAL, audition = VIDEO_CALL, published = 96.0)
            role("r_rm_leads", rm, ACTOR, STIPEND, 2000, footage = 8, count = 2, details = lead("Pooja and Tara", "21-27"),
                question = "Tell us about a comedy you've performed in.")
            role("r_rm_hmu", rm, HMU, STIPEND, 1200)

            val ms = project(DemoIds.P_MONSOON_STATIC, DemoIds.FARIDA, "Monsoon Static",
                "A rooftop band plays through the first storm of the season.",
                MUSIC_VIDEO, 4, listOf("Music"), listOf("hi", "en"), "mumbai_bandra", listOf(sun(14)),
                location = OUTDOOR_PUBLIC, transport = CARPOOL_COORDINATED, lookbook = true,
                story = "A Bandra rooftop · rain machine on the last chorus", published = 20.0)
            role(DemoIds.R_MS_DP, ms, DP, STIPEND, 2000, perks = setOf(Perk.STEP_UP_CREDIT), stepUp = true,
                details = mapOf("package" to "Production hires an FX3 kit and two lenses", "prep_days" to "1"))
            role("r_001_2", ms, GAFFER, PAID_DAY, 2500, kit = 1000)
            role("r_001_3", ms, COLORIST, STIPEND, 5000, remote = true, deadline = sun(14).plusDays(21),
                details = mapOf("specs" to "ProRes 4444 master, Rec.709", "revisions" to "2"))
            role("r_001_4", ms, PRODUCTION_DESIGNER, STIPEND, 1500, finalFilm = 8)

            val sp = project(DemoIds.P_SALT_PANS, "u_019", "Salt Pans",
                "The last salt farmers on the edge of the city work one more season before the land is sold.",
                DOCUMENTARY, 28, listOf("Documentary"), listOf("mr", "hi", "en"), "mumbai_dadar", emptyList(),
                ambition = FESTIVAL, lock = d(21), story = "Shot over a year in the salt pans at the city's eastern edge", published = 140.0)
            role("r_002_1", sp, EDITOR, STIPEND, 10000, remote = true, deadline = d(21), finalFilm = 12,
                details = mapOf("footage_hours" to "40", "revisions" to "2", "specs" to "Paper edit is done"))
            role("r_002_2", sp, COMPOSER, PAID_FLAT, 20000, remote = true, deadline = d(49), details = mapOf("minutes" to "12", "temp_track" to "Y"))
            role("r_002_3", sp, SOUND_DESIGNER, PAID_FLAT, 15000, remote = true, deadline = d(56))

            val tw = project(DemoIds.P_TIFFIN_WARS, DemoIds.FARIDA, "Tiffin Wars",
                "Two dabbawalas race to deliver the same mystery lunch across Lower Parel before noon.",
                VERTICAL, 3, listOf("Comedy"), listOf("hi", "en"), "mumbai_lower_parel", listOf(d(3)),
                ambition = CLIENT_PAID, location = OUTDOOR_PUBLIC, hours = 8, transport = PRODUCTION_VAN, audition = VIDEO_CALL,
                published = 110.0, address = "Gate 3, mill compound, Senapati Bapat Marg, Lower Parel, Mumbai")
            role(DemoIds.R_TW_LEAD, tw, ACTOR, PAID_DAY, 2500, footage = 3, details = lead("Sakhu, the fastest dabbawala", "22-30"))
            role("r_tw_support", tw, ACTOR, PAID_DAY, 2000, details = support("The office peon"))
            role("r_tw_pa", tw, PA, UNPAID, level = Level.FIRST_PROJECT)
            role("r_tw_dp", tw, DP, PAID_DAY, 4000, kit = 1500)
            role("r_tw_sound", tw, SOUND_MIXER, PAID_DAY, 2500, kit = 1000)

            val tr = project(DemoIds.P_TWO_ROOMS, DemoIds.WEAK_KEPT_OWNER, "Two Rooms",
                "A couple on the verge of splitting up spend one last night in the flat they can't afford, in separate rooms.",
                SHORT, 16, listOf("Drama", "Romance"), listOf("hi", "en"), "mumbai_bandra", listOf(d(25), d(26)),
                ambition = FESTIVAL, location = STUDIO, certainty = DateCertainty.TENTATIVE, transport = MILEAGE_OR_TA_PAID, ta = 400,
                flags = setOf(ContentFlag.INTIMACY_SCENE), ic = true,
                safety = "Intimacy coordinator Asha Menon is on set both days. The scene is non-nude; closed set; consent check-in before every take.",
                audition = STUDIO_OR_PUBLIC_WITH_OTHERS, published = 30.0)
            role("r_tr_leads", tr, ACTOR, PAID_DAY, 3000, terms = WITHIN_7_DAYS, footage = 8, count = 2, details = lead("Neel and Ira", "26-34"),
                question = "There is one non-nude intimate scene with an intimacy coordinator. Is that comfortable for you?")
            role(DemoIds.R_TR_DP, tr, DP, PAID_DAY, 3500, terms = WITHIN_7_DAYS, stepUp = true,
                details = mapOf("package" to "Production hires the camera package"))
            role("r_tr_sound", tr, SOUND_MIXER, PAID_DAY, 2500, kit = 1000, terms = WITHIN_7_DAYS)
            role("r_tr_hmu", tr, HMU, STIPEND, 1500)

            val pb = project(DemoIds.P_PAPER_BOATS, "u_024", "Paper Boats",
                "A retired schoolteacher and the young woman renting her spare room trade lessons: paper boats for phone calls.",
                STUDENT_THESIS, 15, listOf("Drama"), listOf("hi", "mr"), "mumbai_juhu", listOf(sat(17), sat(17).plusDays(1)),
                ambition = FESTIVAL, zeroBudget = true, hours = 9, transport = CARPOOL_COORDINATED, audition = SELF_TAPE, published = 60.0)
            role("r_pb_lead", pb, ACTOR, UNPAID, footage = 8, level = Level.BUILDING_REEL, details = lead("Tanu, the tenant", "22-28"))
            role("r_pb_sound", pb, SOUND_MIXER, STIPEND, 1000, details = mapOf("boom_op" to "N"))
            role("r_pb_editor", pb, EDITOR, UNPAID, remote = true, deadline = sat(17).plusDays(45), finalFilm = 16, level = Level.BUILDING_REEL,
                details = mapOf("footage_hours" to "6", "revisions" to "2"))
            role("r_pb_pa", pb, PA, UNPAID, finalFilm = 12)

            val dd = project(DemoIds.P_DEAD_DROP, DemoIds.WEAK_FOOTAGE_OWNER, "Dead Drop",
                "A delivery rider is handed the wrong parcel and has one night to give it back to the people who want it.",
                SHORT, 12, listOf("Action", "Thriller"), listOf("hi"), "mumbai_goregaon_film_city", listOf(sat(30), sat(30).plusDays(1)),
                location = OUTDOOR_PUBLIC, hours = 11, transport = PRODUCTION_VAN, flags = setOf(ContentFlag.STUNTS_FIGHTS), stunt = true,
                safety = "Fights are designed and run by the director, a stunt coordinator with 12 years on features. Crash mats, a rehearsal day before, no real weapons.",
                audition = STUDIO_OR_PUBLIC_WITH_OTHERS, published = 50.0)
            role(DemoIds.R_DD_DP, dd, DP, UNPAID, perks = setOf(Perk.STEP_UP_CREDIT), finalFilm = 10, stepUp = true)
            role("r_dd_fighters", dd, ACTOR, UNPAID, footage = 8, count = 2, details = support("The two riders"),
                question = "Any fight or dance training? It's fine if not; we rehearse.")
            role("r_dd_gaffer", dd, GAFFER, STIPEND, 1500, kit = 800)
            role("r_dd_grip", dd, GRIP, PAID_DAY, 2000)

            // A studio floor in daylight hours with a full team (two women on crew besides the producer), not a private flat at night.
            val cc = project(DemoIds.P_CUTTING_CHAI, DemoIds.FARIDA, "Cutting Chai",
                "Two night-shift nurses share one cutting chai at the end of every shift, until one of them is transferred.",
                SHORT, 8, listOf("Drama"), listOf("hi", "mr"), "mumbai_dadar", listOf(d(6)),
                location = STUDIO, hours = 8, transport = MILEAGE_OR_TA_PAID, ta = 200, audition = STUDIO_OR_PUBLIC_WITH_OTHERS, published = 170.0,
                story = "Hospital-canteen set on a studio floor in Dadar", address = CC_ADDRESS)
            role(DemoIds.R_CC_LEAD, cc, ACTOR, STIPEND, 1500, footage = 4, details = lead("Sister Rekha", "22-30"))
            role("r_cc_support", cc, ACTOR, UNPAID, footage = 4, details = support("Sister Jaya", "28-40"))
            role("r_cc_pa", cc, PA, UNPAID, level = Level.FIRST_PROJECT)
            role("r_cc_dp", cc, DP, STIPEND, 1500)
            role("r_cc_ad", cc, FIRST_AD, PAID_DAY, 3000)
            role("r_cc_sound", cc, SOUND_MIXER, PAID_DAY, 2500, kit = 800)
            role("r_cc_hmu", cc, HMU, STIPEND, 1500)

            // ── Bengaluru (7) ──
            val fc = project(DemoIds.P_FILTER_COFFEE, DemoIds.ROHAN, "Filter Coffee, 4 AM",
                "A night-shift coder and a darshini owner keep the same strange hour, until one morning only one of them shows up.",
                SHORT, 12, listOf("Drama"), listOf("kn", "en"), "bengaluru_jayanagar", listOf(sat(8), sat(8).plusDays(1)),
                ambition = FESTIVAL, transport = MILEAGE_OR_TA_PAID, ta = 300, audition = SELF_TAPE,
                story = "Set in a darshini at 4 am · shot in Jayanagar", published = 70.0, readable = true)
            role(DemoIds.R_FC_DP, fc, DP, STIPEND, 800, stepUp = true,
                details = mapOf("package" to "Production hires a Pocket 6K and two LED panels", "prep_days" to "1"))
            role(DemoIds.R_FC_SOUND, fc, SOUND_MIXER, PAID_DAY, 2500, kit = 1000,
                details = mapOf("speaking_max" to "3", "int_ext" to "80/20", "boom_op" to "N"))
            role(DemoIds.R_FC_ACTOR, fc, ACTOR, UNPAID, footage = 6, details = lead("Kaveri, the darshini owner", "25-35"),
                question = "Comfortable switching between Kannada and English mid-scene?")
            role(DemoIds.R_FC_EDITOR, fc, EDITOR, STIPEND, 6000, remote = true, deadline = sat(8).plusDays(40),
                details = mapOf("footage_hours" to "5", "revisions" to "2"))
            role(DemoIds.R_FC_PD, fc, PRODUCTION_DESIGNER, STIPEND, 1000)

            val si = project("p_003", "u_030", "Second Innings",
                "A retired fast bowler coaches a girls' street cricket team in Indiranagar and learns how to lose.",
                SHORT, 14, listOf("Drama", "Sports"), listOf("kn", "en", "hi"), "bengaluru_indiranagar", listOf(sat(21), sat(21).plusDays(1)),
                ambition = FESTIVAL, location = OUTDOOR_PUBLIC, meals = CATERED, transport = CARPOOL_COORDINATED, audition = VIDEO_CALL, published = 26.0)
            role("r_003_1", si, SOUND_MIXER, PAID_DAY, 3000, kit = 1200)
            role("r_003_2", si, FIRST_AC, PAID_DAY, 2000)
            role("r_003_3", si, ACTOR, UNPAID, footage = 6, details = support("The coach's old teammate", "45-60"))
            role("r_003_4", si, DP, PAID_DAY, 5000, kit = 2000)
            role("r_003_5", si, FIRST_AD, PAID_DAY, 2500)

            val ps = project("p_004", "u_031", "Photo Studio, 1994",
                "In a Malleshwaram photo studio in 1994, an old photographer retouches the one portrait he never delivered.",
                STUDENT_THESIS, 18, listOf("Drama", "Period"), listOf("kn", "en"), "bengaluru_malleshwaram", listOf(sat(35), sat(35).plusDays(1)),
                ambition = FESTIVAL, location = OFFICE_COMMERCIAL, certainty = DateCertainty.TENTATIVE, audition = STUDIO_OR_PUBLIC_WITH_OTHERS,
                lock = sat(35).plusDays(30), published = 12.0)
            role("r_004_1", ps, ACTOR, UNPAID, footage = 10, details = lead("Ramaiah, the photographer", "60-75"))
            role("r_004_2", ps, COMPOSER, PAID_FLAT, 12000, remote = true, deadline = sat(35).plusDays(58), details = mapOf("minutes" to "9", "temp_track" to "N"))
            role("r_004_3", ps, SOUND_DESIGNER, PAID_FLAT, 15000, remote = true, deadline = sat(35).plusDays(58))
            role("r_004_4", ps, DP, UNPAID, finalFilm = 12)

            val tt = project("p_005", "u_030", "Ten to Ten",
                "A 24-hour pharmacy in Koramangala, one night shift, and the regulars who keep the owner awake.",
                SERIES_PILOT, 24, listOf("Comedy", "Drama"), listOf("kn", "en", "hi"), "bengaluru_koramangala", listOf(d(40), d(41), d(42)),
                ambition = DISTRIBUTION, location = OFFICE_COMMERCIAL, certainty = DateCertainty.TENTATIVE, hours = 14, meals = CATERED,
                transport = PRODUCTION_VAN, audition = VIDEO_CALL, published = 8.0)
            role("r_005_1", tt, GAFFER, PAID_DAY, 2500, kit = 1200)
            role("r_005_2", tt, EDITOR, UNPAID, perks = setOf(Perk.STEP_UP_CREDIT), finalFilm = 16, remote = true, deadline = d(42).plusDays(45),
                level = Level.BUILDING_REEL, details = mapOf("footage_hours" to "14", "revisions" to "2"))
            role("r_005_3", tt, WRITER, STIPEND, 8000, terms = WITHIN_15_DAYS, remote = true, deadline = d(35), level = Level.BUILDING_REEL)
            role("r_005_4", tt, ACTOR, STIPEND, 2000, footage = 8, details = lead("Usha, the pharmacist", "30-40"))

            val mm = project("p_006", "u_033", "Malleshwaram Morning",
                "A flower seller, a temple drummer and a jogger keep time with each other at 5 am.",
                MUSIC_VIDEO, 4, listOf("Music"), listOf("kn"), "bengaluru_malleshwaram", listOf(d(9)),
                location = OUTDOOR_PUBLIC, hours = 8, transport = CARPOOL_COORDINATED, published = 54.0, lookbook = true)
            role("r_006_1", mm, HMU, STIPEND, 1200)
            role("r_006_2", mm, ART_ASSISTANT, UNPAID, finalFilm = 6, level = Level.BUILDING_REEL)
            role("r_006_3", mm, GAFFER, PAID_DAY, 2500, kit = 1200)

            val pc = project("p_007", "u_033", "Power Cut",
                "When the power goes out in a Whitefield apartment block, the neighbours meet each other for the first time in six years.",
                SHORT, 11, listOf("Drama"), listOf("kn", "en"), "bengaluru_whitefield", listOf(fri(27), fri(27).plusDays(1)),
                ambition = FESTIVAL, transport = MILEAGE_OR_TA_PAID, ta = 300, flags = setOf(ContentFlag.NIGHT_SHOOT), audition = SELF_TAPE,
                safety = "Night shoot inside the building compound; cabs home for everyone after 23:00.", published = 34.0)
            role("r_007_1", pc, SOUND_MIXER, STIPEND, 1500, kit = 500)
            role("r_007_2", pc, BOOM_OP, STIPEND, 1000)
            role("r_007_3", pc, ACTOR, UNPAID, footage = 6, details = support("The night watchman", "50-65"))
            role("r_007_4", pc, DP, STIPEND, 1500, kit = 1200)

            val ar = project("p_008", "u_048", "Auto Raja",
                "An auto driver who refuses every short trip finally picks up a passenger going two streets away.",
                SHORT, 9, listOf("Comedy"), listOf("kn"), "bengaluru_jp_nagar", listOf(sun(15)),
                ambition = FOR_FUN, zeroBudget = true, location = OUTDOOR_PUBLIC, hours = 9, transport = CARPOOL_COORDINATED,
                audition = STUDIO_OR_PUBLIC_WITH_OTHERS, published = 88.0)
            role("r_008_1", ar, DP, UNPAID, finalFilm = 8)
            role("r_008_2", ar, SOUND_MIXER, STIPEND, 1000, kit = 500)
            role("r_008_3", ar, ACTOR, UNPAID, footage = 4, details = support("The passenger"))

            // ── Pune (2) ──
            val sd = project(DemoIds.P_SECOND_DRAFT, "u_051", "Second Draft",
                "A ghostwriter hired to finish a dead man's memoir finds he wrote her into it.",
                STUDENT_THESIS, 18, listOf("Drama", "Mystery"), listOf("mr", "en"), "pune_koregaon_park", listOf(d(18), d(19), d(20)),
                ambition = FESTIVAL, certainty = DateCertainty.TENTATIVE, transport = CARPOOL_COORDINATED, audition = VIDEO_CALL, published = 45.0)
            role("r_009_1", sd, ACTOR, UNPAID, footage = 8, details = lead("Meenal, the ghostwriter", "25-35"))
            role("r_009_2", sd, SOUND_MIXER, STIPEND, 1000, kit = 500)
            role("r_009_3", sd, EDITOR, UNPAID, finalFilm = 12, remote = true, deadline = d(60), details = mapOf("revisions" to "2"))
            role("r_009_4", sd, DP, STIPEND, 1200, kit = 1000)

            val ff = project(DemoIds.P_48_FRAMES, "u_052", "48 Frames",
                "Genre drawn at kickoff. We shoot Saturday, cut Sunday. Need sound and an editor who likes deadlines.",
                CHALLENGE_48H, 7, listOf("Any genre"), listOf("mr", "hi", "en"), "pune_baner", listOf(sat(20), sat(20).plusDays(1)),
                ambition = FOR_FUN, script = ScriptStatus.IDEA, hours = 14, meals = CATERED, transport = CARPOOL_COORDINATED,
                audition = VIDEO_CALL, published = 75.0)
            role("r_010_1", ff, SOUND_MIXER, STIPEND, 1200, kit = 500)
            role("r_010_2", ff, EDITOR, STIPEND, 2000, remote = true, deadline = sat(20).plusDays(7), details = mapOf("revisions" to "1"))
            role("r_010_3", ff, ACTOR, UNPAID, footage = 2, details = lead("Decided at kickoff"))
            role("r_010_4", ff, HMU, STIPEND, 1000)

            // ── Indore (1, early city) ──
            val rr = project(DemoIds.P_RAJWADA_RAIN, DemoIds.INDORE_OWNER, "Rajwada Rain",
                "Two strangers share one umbrella across the old city and decide not to exchange names.",
                SHORT, 8, listOf("Romance", "Drama"), listOf("hi"), "indore_city_centre", listOf(sun(12)),
                ambition = FOR_FUN, zeroBudget = true, location = OUTDOOR_PUBLIC, hours = 8, audition = SELF_TAPE, published = 18.0)
            role(DemoIds.R_RR_LEADS, rr, ACTOR, UNPAID, footage = 4, count = 2, level = Level.FIRST_PROJECT, details = lead("The two strangers", "20-35"))
            role("r_rr_sound", rr, SOUND_MIXER, STIPEND, 800, kit = 300, level = Level.BUILDING_REEL)
            role(DemoIds.R_RR_WRITER, rr, WRITER, UNPAID, perks = setOf(Perk.STEP_UP_CREDIT), finalFilm = 8, remote = true,
                deadline = sun(12).minusDays(2), level = Level.FIRST_PROJECT, scope = listOf("Dialogue polish"))
            role("r_rr_editor", rr, EDITOR, UNPAID, finalFilm = 8, remote = true, deadline = sun(12).plusDays(30), level = Level.FIRST_PROJECT)
            role("r_rr_dp", rr, DP, STIPEND, 800, kit = 800)

            // ── Kochi (1) ──
            val ks = project("p_011", "u_060", "Kayal Songs",
                "A boat band plays the backwaters at dusk while the village decides whether to sell the jetty.",
                MUSIC_VIDEO, 5, listOf("Music"), listOf("ml"), "kochi_fort_kochi", listOf(d(8)),
                location = OUTDOOR_PUBLIC, certainty = DateCertainty.TENTATIVE, hours = 9, transport = PRODUCTION_VAN, published = 64.0)
            role("r_011_1", ks, DP, PAID_DAY, 3000, kit = 1000)
            role("r_011_2", ks, COLORIST, UNPAID, finalFilm = 6, remote = true, deadline = d(33))
            role("r_011_3", ks, EDITOR, PAID_FLAT, 8000, remote = true, deadline = d(28))

            // ── Los Angeles (2) ──
            val sl = project(DemoIds.P_SOFT_LIGHT, DemoIds.ELENA, "Soft Light",
                "A grief counselor rehearses the conversation she's been avoiding with her estranged brother.",
                SHORT, 14, listOf("Drama"), listOf("en"), "los_angeles_silver_lake", emptyList(),
                ambition = FESTIVAL, lock = d(5), story = "Picture locked; needs sound design and a score", published = 100.0)
            role(DemoIds.R_SL_SD, sl, SOUND_DESIGNER, PAID_FLAT, 2400, remote = true, deadline = d(33), details = mapOf("revisions" to "2"))
            role(DemoIds.R_SL_COMPOSER, sl, COMPOSER, PAID_FLAT, 1800, remote = true, deadline = d(40), details = mapOf("minutes" to "8", "temp_track" to "Y"))

            val vh = project(DemoIds.P_VALLEY_HEAT, "u_064", "Valley Heat 48",
                "Genre drawn at kickoff. We shoot Saturday in the Valley and lock picture Sunday night. Bring water and good shoes.",
                CHALLENGE_48H, 6, listOf("Any genre"), listOf("en"), "los_angeles_burbank", listOf(sat(28), sat(28).plusDays(1)),
                ambition = FOR_FUN, script = ScriptStatus.IDEA, certainty = DateCertainty.TENTATIVE, hours = 12, meals = CATERED,
                transport = CARPOOL_COORDINATED, lock = sat(28).plusDays(1), published = 22.0)
            role("r_012_1", vh, SOUND_MIXER, PAID_DAY, 300, kit = 50)
            role("r_012_2", vh, SOUND_DESIGNER, PAID_FLAT, 900, remote = true, deadline = sat(28).plusDays(7))
            role("r_012_3", vh, FIRST_AC, PAID_DAY, 300)
            role("r_012_4", vh, FIRST_AD, PAID_DAY, 450)

            // ── Atlanta (2) ──
            val yl = project(DemoIds.P_YELLOW_LINE, DemoIds.WEAK_PAY_OWNER, "Yellow Line",
                "A night-bus driver and her only passenger have one stop to settle a ten-year-old argument.",
                SHORT, 10, listOf("Drama"), listOf("en"), "atlanta_midtown", listOf(d(4), d(5)),
                ambition = FESTIVAL, location = OUTDOOR_PUBLIC, transport = MILEAGE_OR_TA_PAID, ta = 25,
                flags = setOf(ContentFlag.NIGHT_SHOOT), audition = STUDIO_OR_PUBLIC_WITH_OTHERS, published = 140.0,
                address = "Depot lot B, 980 Ponce de Leon Ave NE, Atlanta, GA")
            role("r_yl_sound", yl, SOUND_MIXER, PAID_DAY, 400, kit = 100, details = mapOf("boom_op" to "Y", "int_ext" to "100/0"))
            role("r_yl_actors", yl, ACTOR, PAID_DAY, 200, footage = 6, count = 2, details = lead("The driver and her passenger", "35-50"))
            role("r_yl_gaffer", yl, GAFFER, PAID_DAY, 350, kit = 75)
            role("r_yl_camop", yl, CAMERA_OP, PAID_DAY, 450)
            role("r_yl_grip", yl, GRIP, PAID_DAY, 300)

            val pw = project(DemoIds.P_PAWN, "u_072", "Pawn",
                "A pawnshop owner gets a starting pistol and a story she doesn't believe, five minutes before closing.",
                SHORT, 9, listOf("Crime", "Thriller"), listOf("en"), "atlanta_west_end", listOf(d(33), d(34)),
                ambition = FESTIVAL, location = OFFICE_COMMERCIAL, certainty = DateCertainty.TENTATIVE,
                flags = setOf(ContentFlag.FIREARMS_PROPS), stunt = true,
                safety = "Replica starting pistol only, handled by a licensed armourer. Safety briefing at call; no live rounds anywhere on set.",
                audition = STUDIO_OR_PUBLIC_WITH_OTHERS, published = 15.0)
            role("r_013_1", pw, SOUND_MIXER, PAID_DAY, 350, kit = 50)
            role("r_013_2", pw, DP, STIPEND, 120)
            role("r_013_3", pw, ACTOR, STIPEND, 100, footage = 6, details = lead("Dolores, the pawnbroker", "45-60"))

            // ── Rohan's draft: one R02 blocker, "feed people" (snacks only on an unpaid shoot), fixed by "2+ meals". Travel is
            // paid, so both unpaid roles already get two things (footage / step-up credit + travel) and that's the only issue. ──
            val dr = project(DemoIds.P_ROHAN_DRAFT, DemoIds.ROHAN, "Mallige",
                "A grandmother sells jasmine on a Jayanagar corner every morning and refuses to retire.",
                SHORT, 10, listOf("Drama"), listOf("kn", "en"), "bengaluru_jayanagar", listOf(sat(30)),
                location = OUTDOOR_PUBLIC, hours = 8, meals = CRAFTY_ONLY, transport = MILEAGE_OR_TA_PAID, ta = 200,
                audition = SELF_TAPE, status = ProjectStatus.DRAFT, published = 5.0)
            role("r_rd_actor", dr, ACTOR, UNPAID, footage = 6, details = lead("Ajji, the jasmine seller", "60-75"))
            role("r_rd_gaffer", dr, GAFFER, UNPAID, perks = setOf(Perk.STEP_UP_CREDIT))

            // ── Attached people (BOOKED seed matches on open projects) ──
            listOf(
                "r_001_4" to "u_016", "r_002_3" to "u_044", "r_tw_dp" to "u_004", "r_tw_sound" to "u_007", "r_tr_hmu" to "u_018",
                "r_pb_pa" to "u_012", "r_dd_grip" to "u_015", "r_cc_dp" to "u_005", DemoIds.R_FC_PD to "u_039",
                "r_003_4" to "u_033", "r_003_5" to "u_031", "r_004_4" to "u_035", "r_005_4" to "u_047", "r_006_3" to "u_036",
                "r_007_4" to "u_034", "r_008_3" to "u_045", "r_009_4" to "u_054", "r_010_4" to "u_055", "r_rr_dp" to "u_059",
                "r_011_3" to "u_061", "r_012_3" to "u_070", "r_012_4" to "u_065", "r_yl_camop" to "u_074", "r_yl_grip" to "u_075",
                "r_013_3" to "u_073",
            ).forEach { (rid, pid) ->
                val mid = if (rid == DemoIds.R_FC_PD) DemoIds.M_FC_PD else "m_" + rid.removePrefix("r_") + "_" + pid.removePrefix("u_")
                bookOpen(rid, pid, mid, appliedHoursAgo = 120.0 + rnd.nextInt(200))
            }

            // Cutting Chai's team (seed v3): 1st AD Gauri S., sound Pranav G., hair & makeup Priyanka F. (own Random, so the
            // rest of the seed draws the same numbers as before).
            val ccRng = Random(606)
            listOf("r_cc_ad" to "u_010", "r_cc_sound" to "u_007", "r_cc_hmu" to "u_018").forEach { (rid, pid) ->
                bookOpen(rid, pid, "m_" + rid.removePrefix("r_") + "_" + pid.removePrefix("u_"), appliedHoursAgo = 150.0 + ccRng.nextInt(60), rng = ccRng)
            }

            // ── Published day sheets ──
            publishDay(cc, 0, CC_CALL, CC_WRAP, CC_ADDRESS,
                parking = "Two-wheeler parking inside the studio gate. Dadar station is a 10-minute walk.",
                hospital = "24-hour casualty about 1.5 km away on the main road.",
                meals = "Lunch at 13:00, tea and snacks at 16:30 (veg and non-veg).",
                notes = "Bring two plain cotton kurtas. Wardrobe has the uniforms. Separate changing room for cast. Wrap by 20:00.",
                publishedHoursAgo = 20.0)
            publishDay(tw, 0, LocalTime.of(7, 0), LocalTime.of(15, 0), "Gate 3, mill compound, Senapati Bapat Marg, Lower Parel, Mumbai",
                parking = "Van pickup at Lower Parel station, 06:40.", hospital = "Casualty 2 km away on Senapati Bapat Marg.",
                meals = "Breakfast and lunch on set.", publishedHoursAgo = 30.0)
            publishDay(yl, 0, LocalTime.of(19, 0), LocalTime.of(5, 0), "Depot lot B, 980 Ponce de Leon Ave NE, Atlanta, GA",
                parking = "Crew parking in lot B, enter from the side street.", hospital = "ER about 2 miles south.",
                meals = "Dinner at 22:00, snacks all night.", publishedHoursAgo = 48.0)
            publishDay(yl, 1, LocalTime.of(19, 0), LocalTime.of(5, 0), "Depot lot B, 980 Ponce de Leon Ave NE, Atlanta, GA",
                parking = "Crew parking in lot B, enter from the side street.", hospital = "ER about 2 miles south.",
                meals = "Dinner at 22:00, snacks all night.", publishedHoursAgo = 48.0)
            publishDay(ll, 0, LocalTime.of(19, 30), LocalTime.of(5, 30), "Platform 1 parking, Andheri station (west), Andheri West, Mumbai",
                parking = "No crew parking at the station; take the local.", hospital = "24-hour casualty 1 km away on S.V. Road.",
                meals = "Dinner at 21:00, chai and vada pav at 01:00.", publishedHoursAgo = 12.0)
            publishDay(ll, 1, LocalTime.of(19, 30), LocalTime.of(5, 30), "Platform 1 parking, Andheri station (west), Andheri West, Mumbai",
                parking = "No crew parking at the station; take the local.", hospital = "24-hour casualty 1 km away on S.V. Road.",
                meals = "Dinner at 21:00, chai and vada pav at 01:00.", publishedHoursAgo = 12.0)
        }

        // ───────────── Seed v2: 23 more OPEN projects so every deck starts full (DEMO_IDS.md "Starting Gigs decks") ─────────────

        /**
         * Added after the persona states and before the filler applications, with their own fixed Random and explicit
         * application ids, so every v1 id, date and random draw above stays exactly as it was. Weekend dates from
         * d+7 on (never Meera's Cutting Chai day, d+6), Bengaluru director shoots from d+16 on (after Rohan's own
         * Filter Coffee weekend), US roles paid in USD at or above Dana's $350/day.
         */
        private fun buildMoreOpenProjects() {
            val r2 = Random(2_026_100_2)

            // ── Mumbai (8) ──
            val chawl = project("p_014", "u_010", "Chawl No. 7",
                "When the chawl's only landline rings at 3 am, all forty families come out to answer it.",
                SHORT, 14, listOf("Drama", "Comedy"), listOf("hi", "mr"), "mumbai_malad", listOf(sat(7), sat(7).plusDays(1)),
                ambition = FESTIVAL, transport = CARPOOL_COORDINATED, audition = SELF_TAPE,
                story = "A Malad chawl in 1998 · shot in a real chawl corridor", published = 30.0)
            role("r_014_1", chawl, ACTOR, UNPAID, footage = 6, details = lead("Sunanda, who keeps the chawl's only phone", "28-38"))
            role("r_014_2", chawl, ACTOR, UNPAID, footage = 6, details = support("The watchman's daughter", "20-26"))
            role("r_014_3", chawl, SOUND_MIXER, PAID_DAY, 2500, kit = 1000, details = mapOf("speaking_max" to "6", "int_ext" to "60/40", "boom_op" to "N"))
            role("r_014_4", chawl, GAFFER, PAID_DAY, 3000, kit = 1500, details = mapOf("package" to "Night corridor: practicals plus two LED panels"))
            role("r_014_5", chawl, DP, PAID_DAY, 3500, kit = 1500)

            val market = project("p_015", "u_016", "Sunday Market",
                "A widow selling her late husband's records at the Sunday flea market keeps the one record nobody asks for.",
                SHORT, 10, listOf("Drama"), listOf("hi", "en"), "mumbai_bandra", listOf(sun(9)),
                ambition = FESTIVAL, location = OUTDOOR_PUBLIC, transport = MILEAGE_OR_TA_PAID, ta = 250, audition = VIDEO_CALL,
                published = 52.0, lookbook = true)
            role("r_015_1", market, ACTOR, UNPAID, footage = 4, details = lead("Rukhsana, who sells the records", "45-60"))
            role("r_015_2", market, ACTOR, UNPAID, footage = 4, details = support("The collector who keeps coming back", "25-35"))
            role("r_015_3", market, DP, STIPEND, 2500, perks = setOf(Perk.STEP_UP_CREDIT), stepUp = true,
                details = mapOf("package" to "Production hires an FX3 and a 35mm prime", "prep_days" to "1"))
            role("r_015_4", market, SOUND_MIXER, STIPEND, 1500, kit = 500, details = mapOf("int_ext" to "0/100", "boom_op" to "N"))
            role("r_015_5", market, HMU, STIPEND, 1200)

            val understudy = project("p_016", "u_025", "The Understudy",
                "An understudy finally gets her one night on stage and spends it making the lead actor look good.",
                SHORT, 16, listOf("Drama"), listOf("hi", "en"), "mumbai_andheri_west", listOf(sat(14), sat(14).plusDays(1)),
                ambition = FESTIVAL, location = STUDIO, transport = CARPOOL_COORDINATED, audition = STUDIO_OR_PUBLIC_WITH_OTHERS,
                story = "Backstage at a small Andheri theatre", published = 20.0, readable = true)
            role("r_016_1", understudy, ACTOR, UNPAID, footage = 8, details = lead("Pallavi, the understudy", "24-32"))
            role("r_016_2", understudy, ACTOR, UNPAID, footage = 8, details = support("The lead she covers for", "30-40"))
            role("r_016_3", understudy, DP, UNPAID, finalFilm = 10, details = mapOf("package" to "Production hires the camera package"))
            role("r_016_4", understudy, SOUND_MIXER, PAID_DAY, 2500, kit = 1000, details = mapOf("speaking_max" to "4", "int_ext" to "100/0", "boom_op" to "N"))
            role("r_016_5", understudy, FIRST_AD, PAID_DAY, 3000)

            val lift = project("p_017", "u_013", "Lift Out of Order",
                "Six neighbours stuck on the stairs of a Malad tower argue about who broke the lift, and who pays for it.",
                SHORT, 9, listOf("Comedy"), listOf("hi", "en"), "mumbai_malad", listOf(sun(16)),
                ambition = FOR_FUN, zeroBudget = true, hours = 9, audition = SELF_TAPE, published = 64.0)
            role("r_017_1", lift, ACTOR, UNPAID, footage = 6, count = 2, level = Level.BUILDING_REEL, details = support("Two of the six neighbours", "25-60"))
            role("r_017_2", lift, ACTOR, UNPAID, footage = 6, details = lead("Mrs. Kamat from the 14th floor", "55-70"))
            role("r_017_3", lift, DP, UNPAID, perks = setOf(Perk.STEP_UP_CREDIT), finalFilm = 8, stepUp = true)
            role("r_017_4", lift, SOUND_MIXER, STIPEND, 1200, kit = 500)

            val mehendi = project("p_018", "u_017", "Mehendi Night",
                "At her cousin's mehendi, the bride's best friend has one hour to find the ring she lost in the henna.",
                SHORT, 12, listOf("Comedy", "Romance"), listOf("hi", "en"), "mumbai_juhu", listOf(sat(21)),
                ambition = PORTFOLIO, transport = PRODUCTION_VAN, audition = VIDEO_CALL, published = 40.0, lookbook = true)
            role("r_018_1", mehendi, ACTOR, UNPAID, footage = 6, details = lead("Noor, the best friend", "22-30"))
            role("r_018_2", mehendi, ACTOR, UNPAID, footage = 6, details = support("The aunt who notices everything", "45-60"))
            role("r_018_3", mehendi, DP, PAID_DAY, 5000, kit = 2000)
            role("r_018_4", mehendi, SOUND_MIXER, PAID_DAY, 3000, kit = 1200, details = mapOf("speaking_max" to "8", "boom_op" to "Y"))

            val lullaby = project("p_019", "u_020", "Local Train Lullaby",
                "A lullaby recorded on the 5:12 local, sung by the commuters who hear it every morning.",
                MUSIC_VIDEO, 4, listOf("Music"), listOf("hi", "en"), "mumbai_dadar", listOf(sun(23)),
                location = OUTDOOR_PUBLIC, hours = 8, transport = CARPOOL_COORDINATED, published = 26.0, lookbook = true)
            role("r_019_1", lullaby, DP, PAID_DAY, 3500, stepUp = true, details = mapOf("package" to "Production hires a gimbal rig", "prep_days" to "1"))
            role("r_019_2", lullaby, GAFFER, PAID_DAY, 2500, kit = 1000)
            role("r_019_3", lullaby, EDITOR, STIPEND, 4000, remote = true, deadline = sun(23).plusDays(21),
                details = mapOf("footage_hours" to "4", "revisions" to "2"))
            role("r_019_4", lullaby, WRITER, STIPEND, 3000, remote = true, deadline = sun(23).minusDays(7), scope = listOf("Treatment"))

            val rain = project("p_020", "u_008", "Rain Check",
                "A spec ad for an umbrella brand where one stubborn man refuses to buy an umbrella all monsoon.",
                SPEC_AD, 2, listOf("Comedy"), listOf("hi", "en"), "mumbai_powai", listOf(sat(28)),
                transport = MILEAGE_OR_TA_PAID, ta = 300, audition = VIDEO_CALL, published = 14.0)
            role("r_020_1", rain, ACTOR, PAID_DAY, 4000, footage = 3, details = lead("The man without an umbrella", "30-45"))
            role("r_020_2", rain, DP, STIPEND, 3000, perks = setOf(Perk.STEP_UP_CREDIT), stepUp = true, details = mapOf("package" to "Rain rig and an FX3 from the production"))
            role("r_020_3", rain, SOUND_MIXER, PAID_DAY, 2500, kit = 1000)
            role("r_020_4", rain, COLORIST, STIPEND, 4000, remote = true, deadline = sat(28).plusDays(14), details = mapOf("specs" to "Rec.709 master, two aspect ratios", "revisions" to "2"))

            val desk = project("p_021", "u_014", "Night Desk",
                "The night editor of a dying Marathi newspaper has four hours to fill the front page and one story she won't run.",
                SERIES_PILOT, 22, listOf("Drama"), listOf("mr", "hi", "en"), "mumbai_goregaon_film_city",
                listOf(sat(24), sat(24).plusDays(1), sat(24).plusDays(7)),
                ambition = DISTRIBUTION, location = STUDIO, hours = 12, meals = CATERED, transport = PRODUCTION_VAN, audition = VIDEO_CALL,
                published = 90.0, lodging = false)
            role("r_021_1", desk, GAFFER, PAID_DAY, 3500, kit = 1500)
            role("r_021_2", desk, SOUND_MIXER, PAID_DAY, 3000, kit = 1200, details = mapOf("speaking_max" to "5", "int_ext" to "100/0", "boom_op" to "Y"))
            role("r_021_3", desk, ACTOR, PAID_DAY, 4000, footage = 8, details = lead("Vaishali, the night editor", "35-45"))
            role("r_021_4", desk, WRITER, STIPEND, 12000, terms = WITHIN_15_DAYS, remote = true, deadline = d(20), scope = listOf("Rewrite", "Dialogue polish"))
            role("r_021_5", desk, DP, PAID_DAY, 6500, kit = 3000)

            // ── Bengaluru (7) ──
            val majestic = project("p_022", "u_030", "Majestic, 6:40",
                "Every morning at 6:40 a bus conductor saves the same window seat for a passenger who stopped coming.",
                SHORT, 12, listOf("Drama"), listOf("kn", "en"), "bengaluru_jayanagar", listOf(sat(16), sat(16).plusDays(1)),
                ambition = FESTIVAL, location = OUTDOOR_PUBLIC, meals = CATERED, transport = CARPOOL_COORDINATED, audition = VIDEO_CALL,
                published = 36.0)
            role("r_022_1", majestic, DIRECTOR, STIPEND, 5000, details = mapOf("note" to "Producer-led; script locked; recce done"))
            role("r_022_2", majestic, ACTOR, UNPAID, footage = 6, details = lead("Shankar, the conductor", "45-60"))
            role("r_022_3", majestic, DP, UNPAID, perks = setOf(Perk.STEP_UP_CREDIT), finalFilm = 8, stepUp = true)
            role("r_022_4", majestic, SOUND_MIXER, PAID_DAY, 2500, kit = 1000, details = mapOf("int_ext" to "30/70", "boom_op" to "N"))
            role("r_022_5", majestic, PRODUCTION_DESIGNER, STIPEND, 1500)

            val thaalam = project("p_023", "u_037", "Thaalam",
                "A drummer plays one rooftop set a night until the neighbours start playing back.",
                MUSIC_VIDEO, 4, listOf("Music"), listOf("kn", "en"), "bengaluru_indiranagar", listOf(sun(22)),
                location = OUTDOOR_PUBLIC, hours = 9, transport = CARPOOL_COORDINATED, audition = VIDEO_CALL, published = 58.0, lookbook = true)
            role("r_023_1", thaalam, DIRECTOR, PAID_FLAT, 15000)
            role("r_023_2", thaalam, DP, PAID_DAY, 5000, kit = 2000)
            role("r_023_3", thaalam, ACTOR, UNPAID, footage = 4, details = lead("The dancer on the next roof", "20-30"))
            role("r_023_4", thaalam, EDITOR, STIPEND, 5000, remote = true, deadline = sun(22).plusDays(20), details = mapOf("footage_hours" to "3", "revisions" to "2"))

            val dosa = project("p_024", "u_043", "Benne Dosa",
                "A spec ad for a corner darshini where the butter dosa is always worth the wait, even for the man in a hurry.",
                SPEC_AD, 2, listOf("Comedy"), listOf("kn", "en"), "bengaluru_jp_nagar", listOf(sat(29)),
                location = OFFICE_COMMERCIAL, hours = 8, transport = MILEAGE_OR_TA_PAID, ta = 300, audition = SELF_TAPE, published = 10.0)
            role("r_024_1", dosa, DIRECTOR, STIPEND, 3000)
            role("r_024_2", dosa, ACTOR, UNPAID, footage = 3, details = lead("The man in a hurry", "25-40"))
            role("r_024_3", dosa, DP, STIPEND, 2500, kit = 1000)
            role("r_024_4", dosa, SOUND_MIXER, STIPEND, 1500, kit = 500)

            val hostel = project("p_025", "u_047", "Hostel Day",
                "Pilot about a girls' hostel where the warden's one rule is broken every single night.",
                SERIES_PILOT, 24, listOf("Comedy"), listOf("kn", "en", "hi"), "bengaluru_koramangala", listOf(sat(31), sat(31).plusDays(1)),
                ambition = PORTFOLIO, certainty = DateCertainty.TENTATIVE, transport = MILEAGE_OR_TA_PAID, ta = 250, audition = VIDEO_CALL,
                published = 80.0)
            role("r_025_1", hostel, WRITER, STIPEND, 10000, terms = WITHIN_15_DAYS, remote = true, deadline = d(24), scope = listOf("Rewrite"))
            role("r_025_2", hostel, ACTOR, UNPAID, footage = 8, count = 2, level = Level.BUILDING_REEL, details = support("Two first-year roommates", "19-24"))
            role("r_025_3", hostel, DP, UNPAID, perks = setOf(Perk.STEP_UP_CREDIT), finalFilm = 10, stepUp = true)
            role("r_025_4", hostel, SOUND_MIXER, STIPEND, 1500, kit = 800)
            role("r_025_5", hostel, ART_ASSISTANT, STIPEND, 1000)

            val notice = project("p_026", "u_042", "Notice Period",
                "On the last day of her notice period, a team lead finally says everything she's been saving up.",
                SHORT, 10, listOf("Comedy", "Drama"), listOf("kn", "en"), "bengaluru_hsr_layout", listOf(sun(18)),
                location = OFFICE_COMMERCIAL, hours = 9, transport = CARPOOL_COORDINATED, audition = SELF_TAPE, published = 46.0)
            role("r_026_1", notice, ACTOR, STIPEND, 2000, footage = 6, details = lead("Anjana, the team lead", "28-36"))
            role("r_026_2", notice, ACTOR, UNPAID, footage = 6, details = support("The new joiner", "22-26"))
            role("r_026_3", notice, DP, UNPAID, perks = setOf(Perk.STEP_UP_CREDIT), finalFilm = 8, stepUp = true)
            role("r_026_4", notice, WRITER, STIPEND, 4000, remote = true, deadline = sun(18).minusDays(5), scope = listOf("Dialogue polish"))

            val walkers = project("p_027", "u_039", "Morning Walkers",
                "Three retired men who walk the same park loop every morning plan one last road trip that none of them can drive.",
                SHORT, 13, listOf("Comedy", "Drama"), listOf("kn", "en"), "bengaluru_malleshwaram", listOf(sat(23), sat(23).plusDays(1)),
                ambition = FESTIVAL, location = OUTDOOR_PUBLIC, transport = CARPOOL_COORDINATED, audition = SELF_TAPE, published = 22.0)
            role("r_027_1", walkers, ACTOR, UNPAID, footage = 6, count = 3, details = support("The three walkers", "60-75"))
            role("r_027_2", walkers, DP, STIPEND, 3000, perks = setOf(Perk.STEP_UP_CREDIT), stepUp = true)
            role("r_027_3", walkers, SOUND_MIXER, PAID_DAY, 2500, kit = 1000)
            role("r_027_4", walkers, DIRECTOR, UNPAID, perks = setOf(Perk.STEP_UP_CREDIT), finalFilm = 8)

            val brothers = project("p_028", "u_050", "Two Brothers, One Gift",
                "Two brothers who haven't spoken in a year are sent to buy the same birthday gift for their mother.",
                STUDENT_THESIS, 15, listOf("Drama"), listOf("kn", "en", "ta"), "bengaluru_jp_nagar", listOf(sat(37), sat(37).plusDays(1)),
                ambition = FESTIVAL, certainty = DateCertainty.TENTATIVE, transport = CARPOOL_COORDINATED, audition = SELF_TAPE, published = 6.0)
            role("r_028_1", brothers, DP, STIPEND, 2000)
            role("r_028_2", brothers, SOUND_MIXER, STIPEND, 1200, kit = 500)
            role("r_028_3", brothers, ACTOR, UNPAID, footage = 10, count = 2, details = lead("The two brothers", "25-35"))
            role("r_028_4", brothers, EDITOR, UNPAID, remote = true, finalFilm = 12, deadline = sat(37).plusDays(40), level = Level.BUILDING_REEL,
                details = mapOf("footage_hours" to "8", "revisions" to "2"))

            // ── Pune (2) ──
            val wada = project("p_029", "u_054", "Wada",
                "Three cousins have one weekend to empty their grandmother's wada before the builder's men arrive.",
                SHORT, 15, listOf("Drama"), listOf("mr", "hi", "en"), "pune_kothrud", listOf(sat(19), sat(19).plusDays(1)),
                ambition = FESTIVAL, location = PRIVATE_HOME, transport = CARPOOL_COORDINATED, audition = VIDEO_CALL, published = 44.0,
                story = "An old Kothrud wada · the family's own house")
            role("r_029_1", wada, WRITER, STIPEND, 6000, remote = true, deadline = d(14), scope = listOf("Dialogue polish"))
            role("r_029_2", wada, ACTOR, UNPAID, footage = 8, count = 3, details = lead("The three cousins", "25-35"))
            role("r_029_3", wada, SOUND_MIXER, STIPEND, 1200, kit = 500)
            role("r_029_4", wada, EDITOR, STIPEND, 5000, remote = true, deadline = sat(19).plusDays(30), details = mapOf("footage_hours" to "6", "revisions" to "2"))

            val tekdi = project("p_030", "u_056", "Tekdi",
                "Every evening on the hill, an old man teaches a stranger one Marathi word, until the stranger teaches him one back.",
                SHORT, 9, listOf("Drama"), listOf("mr", "en"), "pune_baner", listOf(sun(26)),
                ambition = FOR_FUN, location = OUTDOOR_PUBLIC, hours = 8, transport = CARPOOL_COORDINATED, audition = SELF_TAPE,
                lock = sun(26).plusDays(20), published = 70.0)
            role("r_030_1", tekdi, ACTOR, STIPEND, 1500, footage = 6, details = lead("Aaba, the old man", "65-80"))
            role("r_030_2", tekdi, DP, STIPEND, 2000, kit = 1000, perks = setOf(Perk.STEP_UP_CREDIT), stepUp = true)
            role("r_030_3", tekdi, COMPOSER, PAID_FLAT, 10000, remote = true, deadline = sun(26).plusDays(45), details = mapOf("minutes" to "5", "temp_track" to "N"))

            // ── Los Angeles (2) ──
            val swap = project("p_031", "u_066", "Swap Meet",
                "A father and daughter who run a stall at the Sunday swap meet disagree about the one thing they'll never sell.",
                SHORT, 12, listOf("Drama"), listOf("en", "es"), "los_angeles_culver_city", listOf(sat(13), sat(13).plusDays(1)),
                ambition = FESTIVAL, location = OUTDOOR_PUBLIC, transport = CARPOOL_COORDINATED, audition = SELF_TAPE,
                lock = sat(13).plusDays(21), published = 28.0)
            role("r_031_1", swap, SOUND_DESIGNER, PAID_FLAT, 1500, remote = true, deadline = sat(13).plusDays(50), details = mapOf("revisions" to "2"))
            role("r_031_2", swap, SOUND_MIXER, PAID_DAY, 350, kit = 75)
            role("r_031_3", swap, ACTOR, PAID_DAY, 250, footage = 6, details = lead("Rosa, the daughter", "20-30"))
            role("r_031_4", swap, DP, STIPEND, 120)
            role("r_031_5", swap, FIRST_AD, PAID_DAY, 450)

            val coverage = project("p_032", "u_067", "Coverage",
                "An insurance adjuster spends one night listing what's left of a burned house with the owner who won't leave it.",
                SHORT, 14, listOf("Drama"), listOf("en"), "los_angeles_burbank", listOf(d(20), d(21)),
                ambition = FESTIVAL, location = PRIVATE_HOME, certainty = DateCertainty.TENTATIVE, transport = MILEAGE_OR_TA_PAID, ta = 30,
                audition = VIDEO_CALL, lock = d(40), published = 60.0)
            role("r_032_1", coverage, SOUND_DESIGNER, PAID_FLAT, 2000, remote = true, deadline = d(70), details = mapOf("revisions" to "2"))
            role("r_032_2", coverage, COMPOSER, PAID_FLAT, 1500, remote = true, deadline = d(70), details = mapOf("minutes" to "7", "temp_track" to "Y"))
            role("r_032_3", coverage, GAFFER, PAID_DAY, 350, kit = 100)
            role("r_032_4", coverage, ACTOR, PAID_DAY, 300, footage = 8, details = lead("The adjuster", "40-55"))
            role("r_032_5", coverage, FIRST_AC, PAID_DAY, 350)

            // ── Atlanta (4) ──
            val porch = project("p_033", "u_074", "Porch Light",
                "A Decatur widow leaves her porch light on every night for a son who stopped calling, until a stranger knocks.",
                SHORT, 11, listOf("Drama"), listOf("en"), "atlanta_decatur", listOf(d(9), d(10)),
                ambition = FESTIVAL, location = PRIVATE_HOME, transport = MILEAGE_OR_TA_PAID, ta = 25, audition = SELF_TAPE,
                lock = d(30), published = 18.0)
            role("r_033_1", porch, SOUND_MIXER, PAID_DAY, 400, kit = 100, details = mapOf("speaking_max" to "2", "int_ext" to "80/20", "boom_op" to "N"))
            role("r_033_2", porch, ACTOR, PAID_DAY, 250, footage = 6, details = lead("Miss Loretta", "60-75"))
            role("r_033_3", porch, GAFFER, PAID_DAY, 350, kit = 75)
            role("r_033_4", porch, SOUND_DESIGNER, PAID_FLAT, 1800, remote = true, deadline = d(60), details = mapOf("revisions" to "2"))
            role("r_033_5", porch, GRIP, PAID_DAY, 300)

            val diner = project("p_034", "u_075", "Grits at Midnight",
                "A diner's night cook and the regular who never orders try to save the place on its last night open.",
                SHORT, 12, listOf("Comedy", "Drama"), listOf("en"), "atlanta_west_end", listOf(d(23), d(24)),
                ambition = FESTIVAL, location = OFFICE_COMMERCIAL, meals = CATERED, transport = CARPOOL_COORDINATED,
                audition = STUDIO_OR_PUBLIC_WITH_OTHERS, lock = d(40), published = 34.0)
            role("r_034_1", diner, SOUND_MIXER, PAID_DAY, 450, kit = 125, details = mapOf("speaking_max" to "3", "int_ext" to "100/0", "boom_op" to "Y"))
            role("r_034_2", diner, DP, PAID_DAY, 500, kit = 200)
            role("r_034_3", diner, ACTOR, PAID_DAY, 225, footage = 6, count = 2, details = lead("The cook and the regular", "30-55"))
            role("r_034_4", diner, SOUND_DESIGNER, PAID_FLAT, 1600, remote = true, deadline = d(65), details = mapOf("revisions" to "2"))
            role("r_034_5", diner, CAMERA_OP, PAID_DAY, 450)

            val choir = project("p_035", "u_073", "Garage Choir",
                "A church choir records its first album in a Midtown parking garage because the echo is better than the church's.",
                MUSIC_VIDEO, 5, listOf("Music"), listOf("en"), "atlanta_midtown", listOf(d(30)),
                location = OUTDOOR_PUBLIC, certainty = DateCertainty.TENTATIVE, hours = 9, transport = CARPOOL_COORDINATED, published = 50.0)
            role("r_035_1", choir, SOUND_MIXER, PAID_FLAT, 600, details = mapOf("note" to "Live choir to multitrack, 12 voices", "boom_op" to "N"))
            role("r_035_2", choir, DP, PAID_DAY, 450, kit = 150)
            role("r_035_3", choir, EDITOR, PAID_FLAT, 800, remote = true, deadline = d(44), details = mapOf("footage_hours" to "3", "revisions" to "2"))

            val slate = project("p_036", "u_072", "Clean Slate",
                "A spec ad for a laundromat where every lost sock in the neighbourhood finally comes back.",
                SPEC_AD, 2, listOf("Comedy"), listOf("en"), "atlanta_midtown", listOf(d(12)),
                location = OFFICE_COMMERCIAL, hours = 8, audition = VIDEO_CALL, published = 12.0)
            role("r_036_1", slate, SOUND_MIXER, PAID_DAY, 375, kit = 100)
            role("r_036_2", slate, ACTOR, PAID_DAY, 300, footage = 3, details = lead("The laundromat owner", "35-55"))
            role("r_036_3", slate, GAFFER, PAID_DAY, 350)

            // Attached people (BOOKED) on the new projects. Explicit ids keep the v1 application numbering intact.
            listOf(
                // Chosen so nobody is booked on two shoots that could share a date on any weekday anchor.
                "r_014_5" to "u_004", "r_015_5" to "u_018", "r_016_5" to "u_010", "r_021_5" to "u_003", "r_022_5" to "u_039",
                "r_025_5" to "u_040", "r_031_5" to "u_065", "r_032_5" to "u_070", "r_033_5" to "u_075", "r_034_5" to "u_074",
            ).forEach { (rid, pid) ->
                val mid = "m_" + rid.removePrefix("r_") + "_" + pid.removePrefix("u_")
                bookOpen(rid, pid, mid, appliedHoursAgo = 100.0 + r2.nextInt(150), appId = "a_" + mid.removePrefix("m_"), rng = r2)
            }
        }

        // ───────────── Booking helpers ─────────────

        fun newApp(roleId: String, pid: String, created: Long, status: ApplicationStatus, origin: ApplicationOrigin = ApplicationOrigin.APPLIED,
                   note: String? = null, answer: String? = null, decided: Long? = null, id: String? = null, backupRank: Int? = null,
                   decline: String? = null): Application {
            val a = Application(
                id = id ?: "a_%04d".format(appSeq++), roleId = roleId, applicantId = pid, origin = origin, note = note,
                workLinkId = profiles[pid]?.workLinks?.firstOrNull { it.kind == LinkKind.REEL || it.kind == LinkKind.PORTFOLIO }?.id,
                screeningAnswer = answer, status = status, backupRank = backupRank, declineReason = decline,
                createdAt = created, decidedAt = decided,
            )
            apps[a.id] = a
            return a
        }

        fun msg(matchId: String, sender: String?, body: String, at: Long, kind: MessageKind = MessageKind.TEXT, cardId: String? = null,
                flags: Set<MessageFlag> = emptySet(), safety: Boolean = false, audition: AuditionPayload? = null) {
            messages += Message("msg_%05d".format(msgSeq++), matchId, sender, kind, body, audition, cardId, flags, safety, at)
        }

        fun area(id: String): Area = DemoCities.area(id)!!

        fun systemTerms(matchId: String, r: ProjectRole, p: Project, at: Long) {
            val a = area(p.areaId)
            msg(matchId, null, DemoText.termsSummary(r, p, a.name, cityOf(p.areaId).name), at, MessageKind.SYSTEM)
        }

        /** Create match + accepted card + wrap items for a BOOKED person on an open project. */
        fun bookOpen(roleId: String, pid: String, mid: String, appliedHoursAgo: Double, appId: String? = null, rng: Random = rnd): Match {
            val r = roles.getValue(roleId)
            val p = projects.getValue(r.projectId)
            val owner = profiles.getValue(p.ownerId)
            val created = ago(appliedHoursAgo)
            val decided = created + (4 + rng.nextInt(30)) * HOUR
            val app = newApp(roleId, pid, created, ApplicationStatus.MATCHED, note = DemoText.applicantNote(profiles.getValue(pid), r, rng),
                decided = decided, id = appId)
            val terms = SeedKit.terms(r, p, owner.id, owner.shortName)
            val sent = decided + 3 * HOUR
            val booked = sent + (2 + rng.nextInt(20)) * HOUR
            val card = BookingCard("bc_${mid}_1", mid, 1, terms, BookingStatus.ACCEPTED, owner.id, sent, booked)
            cards[card.id] = card
            val m = Match(mid, app.id, p.id, r.id, owner.id, pid, MatchState.BOOKED, isPostRole = r.isPostRole,
                createdAt = decided, bookedAt = booked)
            matches[mid] = m
            systemTerms(mid, r, p, decided)
            msg(mid, null, "Booking card v1", sent, MessageKind.BOOKING, cardId = card.id)
            msg(mid, null, "Booked. ${profiles.getValue(pid).shortName} accepted the booking card.", booked, MessageKind.SYSTEM)
            SeedKit.wrapItems(mid, terms).forEach { items[it.id] = it }
            readMarks["$pid|$mid"] = booked
            readMarks["${owner.id}|$mid"] = booked
            return m
        }

        /**
         * Booking cards pre-fill a "~07:00–HH:00" call window from the hours cap; on projects whose first day sheet
         * is already out (Cutting Chai 16:00, Yellow Line 19:00) the card would contradict it, so cards match the sheet.
         */
        private fun alignCallWindows() {
            val firstDay = days.values.groupBy { it.projectId }.mapValues { (_, d) -> d.minBy { it.day } }
            cards.replaceAll { _, c ->
                val day = matches[c.matchId]?.let { firstDay[it.projectId] }
                if (day == null || c.terms.callWindow == null) c
                else c.copy(terms = c.terms.copy(callWindow = "~%s–%s".format(day.generalCall, day.wrapBy)))
            }
        }

        fun publishDay(p: Project, index: Int, call: LocalTime, wrap: LocalTime, address: String, parking: String? = null,
                       hospital: String? = null, meals: String? = null, notes: String? = null, publishedHoursAgo: Double) {
            val date = p.shootDates[index]
            val id = "sd_${p.id.removePrefix("p_")}_${index + 1}"
            days[id] = ShootDay(id, p.id, date, call, wrap, address, SeedKit.mapsUrl(address), parking, hospital, meals, notes,
                1, ago(publishedHoursAgo))
            // every booked on-set person gets a call time; most have acked
            matches.values.filter { it.projectId == p.id && it.state == MatchState.BOOKED && !it.isPostRole }.forEach { m ->
                calls += CallTime(id, m.collaboratorId, call, ackAt = if (rnd.nextDouble() < 0.8) ago(publishedHoursAgo - 2) else null)
            }
        }

        // ───────────── Persona states (PRD §11.5) ─────────────

        private fun buildPersonaStates() {
            val meera = profiles.getValue(DemoIds.MEERA)

            // Meera: BOOKED on Cutting Chai in 6 days, day sheet published, not acked.
            bookOpen(DemoIds.R_CC_LEAD, DemoIds.MEERA, DemoIds.M_MEERA_CUTTING_CHAI, appliedHoursAgo = 200.0, appId = "a_meera_cutting_chai")
            val ccDay = days.getValue(DemoIds.SD_CUTTING_CHAI_1)
            calls.removeAll { it.shootDayId == ccDay.id && it.profileId == DemoIds.MEERA }
            calls += CallTime(ccDay.id, DemoIds.MEERA, CC_MEERA_CALL, ackAt = null)
            calls.replaceAll { if (it.shootDayId == ccDay.id && it.profileId == "u_005") it.copy(ackAt = ago(18.0)) else it }
            msg(DemoIds.M_MEERA_CUTTING_CHAI, DemoIds.FARIDA, "Hi Meera, day sheet is up. Your call is ${CC_MEERA_CALL} at the studio in Dadar, wrap by ${CC_WRAP}. " +
                "Tap Got it when you've seen it.", ago(19.0))

            // Meera: two PENDING applications.
            newApp("r_pb_lead", DemoIds.MEERA, ago(50.0), ApplicationStatus.PENDING, id = DemoIds.A_MEERA_PAPER_BOATS,
                note = "Theatre for four years. The tenant reads like someone I know. Both days work.")
            newApp("r_dd_fighters", DemoIds.MEERA, ago(98.0), ApplicationStatus.PENDING, id = DemoIds.A_MEERA_DEAD_DROP,
                note = "Kathak trained, comfortable with choreography.", answer = "Eight years of Kathak; no fight training yet, happy to rehearse.")

            // Meera: a Rebook ask from Farida (worked together on Sea Link Sunday).
            newApp(DemoIds.R_TW_LEAD, DemoIds.MEERA, ago(5.0), ApplicationStatus.PENDING, ApplicationOrigin.INVITED, id = DemoIds.A_MEERA_REBOOK,
                note = "Meera, loved working with you on Sea Link Sunday. Same terms as the listing, one day in Lower Parel.")

            // Meera: IN TALKS with "Casting Hub Official"; the first chat message is the scam pattern (safetyExample).
            val scamApp = newApp("r_rm_leads", DemoIds.MEERA, ago(52.0), ApplicationStatus.MATCHED, id = "a_meera_scam",
                note = "Comedy is new for me but I've done a lot of theatre. Free on all three days.",
                answer = "A two-hander comedy at a college festival, and a lot of improv.", decided = ago(26.0))
            val rm = roles.getValue("r_rm_leads")
            val rmP = projects.getValue(DemoIds.P_ROOMMATES)
            matches[DemoIds.M_MEERA_SCAM] = Match(DemoIds.M_MEERA_SCAM, scamApp.id, rmP.id, rm.id, DemoIds.CASTING_HUB, DemoIds.MEERA,
                MatchState.TALKING, createdAt = ago(26.0))
            systemTerms(DemoIds.M_MEERA_SCAM, rm, rmP, ago(26.0))
            msg(DemoIds.M_MEERA_SCAM, DemoIds.CASTING_HUB, DemoText.scamOpener(DemoText.firstName(meera)), ago(20.0),
                flags = setOf(MessageFlag.MONEY_FROM_TALENT, MessageFlag.OFF_PLATFORM, MessageFlag.CONTACT), safety = true)

            // Rohan: Filter Coffee applicants 7 / 3 / 10 / 4, one DP IN TALKS, PD booked + backup. His Keep list DP (Anusha,
            // u_035) and sound recordist (Lakshmi, u_038) from Bus Stop haven't applied, so a Rebook ask can go out; the
            // seed v3 Bengaluru crew (DemoPeople.SCOUT_POOL) haven't either, so Scout has people for every crew role.
            val fcApplicants = mapOf(
                DemoIds.R_FC_DP to listOf("u_034", "u_079", "u_036", "u_005", "u_014", "u_059", "u_054"),
                DemoIds.R_FC_SOUND to listOf("u_037", "u_083", "u_044"),
                DemoIds.R_FC_ACTOR to listOf("u_045", "u_046", "u_047", "u_048", "u_049", "u_050", "u_026", "u_029", "u_053", "u_058"),
                DemoIds.R_FC_EDITOR to listOf("u_042", "u_009", "u_061", "u_008"),
            )
            fcApplicants.forEach { (rid, pids) ->
                val r = roles.getValue(rid)
                pids.forEachIndexed { i, pid ->
                    val talk = rid == DemoIds.R_FC_DP && pid == "u_034"
                    val created = if (talk) ago(30.0) else ago(4.0 + i * 9 + rnd.nextInt(8))
                    newApp(rid, pid, created, if (talk) ApplicationStatus.MATCHED else ApplicationStatus.PENDING,
                        note = DemoText.applicantNote(profiles.getValue(pid), r, rnd),
                        answer = r.screeningQuestion?.let { DemoText.screeningAnswer(rnd) },
                        decided = if (talk) ago(26.0) else null, id = if (talk) "a_rohan_dp" else null)
                }
            }
            val fcDp = roles.getValue(DemoIds.R_FC_DP)
            val fc = projects.getValue(DemoIds.P_FILTER_COFFEE)
            val dpTalkAt = apps.getValue("a_rohan_dp").decidedAt!!
            matches[DemoIds.M_ROHAN_DP] = Match(DemoIds.M_ROHAN_DP, "a_rohan_dp", fc.id, fcDp.id, DemoIds.ROHAN, "u_034", MatchState.TALKING, createdAt = dpTalkAt)
            systemTerms(DemoIds.M_ROHAN_DP, fcDp, fc, dpTalkAt)
            msg(DemoIds.M_ROHAN_DP, "u_034", "Thanks Rohan. I've shot night interiors on the Pocket 6K. Is there a recce before the shoot?", dpTalkAt + HOUR)
            msg(DemoIds.M_ROHAN_DP, DemoIds.ROHAN, "Yes, Thursday evening at the darshini. I'll send the booking card once sound is locked.", dpTalkAt + 2 * HOUR)
            msg(DemoIds.M_ROHAN_DP, "u_034", "Sounds good. Thursday works.", dpTalkAt + 3 * HOUR)
            readMarks["${DemoIds.ROHAN}|${DemoIds.M_ROHAN_DP}"] = dpTalkAt + 2 * HOUR
            newApp(DemoIds.R_FC_PD, "u_040", ago(90.0), ApplicationStatus.BACKUP, id = DemoIds.A_FC_PD_BACKUP, backupRank = 1,
                note = "Assisted on a period short. I can source props around Jayanagar.", decided = ago(80.0))

            // Dana: OFFERED booking card for sound design on Soft Light (post-only).
            val slSd = roles.getValue(DemoIds.R_SL_SD)
            val sl = projects.getValue(DemoIds.P_SOFT_LIGHT)
            val elena = profiles.getValue(DemoIds.ELENA)
            newApp(slSd.id, DemoIds.DANA, ago(70.0), ApplicationStatus.MATCHED, id = "a_dana_softlight",
                note = "I mix on Pro Tools with a calibrated 5.1 room. Two revision rounds is fine.", decided = ago(60.0))
            matches[DemoIds.M_DANA_SOFTLIGHT] = Match(DemoIds.M_DANA_SOFTLIGHT, "a_dana_softlight", sl.id, slSd.id, elena.id, DemoIds.DANA,
                MatchState.OFFERED, isPostRole = true, createdAt = ago(60.0))
            systemTerms(DemoIds.M_DANA_SOFTLIGHT, slSd, sl, ago(60.0))
            val danaTerms = SeedKit.terms(slSd, sl, elena.id, elena.shortName)
            cards[DemoIds.BC_DANA_SOFTLIGHT_1] = BookingCard(DemoIds.BC_DANA_SOFTLIGHT_1, DemoIds.M_DANA_SOFTLIGHT, 1, danaTerms,
                BookingStatus.SENT, elena.id, ago(3.0))
            msg(DemoIds.M_DANA_SOFTLIGHT, elena.id, "Hi Dana, picture locks on ${com.tff.app.core.Dates.short(sl.pictureLockDate!!)}. " +
                "The card has the delivery date and the flat fee. Tell me if the date doesn't work.", ago(3.2))
            msg(DemoIds.M_DANA_SOFTLIGHT, null, "Booking card v1", ago(3.0), MessageKind.BOOKING, cardId = DemoIds.BC_DANA_SOFTLIGHT_1)
            readMarks["${elena.id}|${DemoIds.M_DANA_SOFTLIGHT}"] = ago(3.0)
        }

        // ───────────── History: 20 WRAPPED projects, ~110 past bookings ─────────────

        private fun buildHistory() {
            val S = STIPEND
            val PDY = PAID_DAY
            val PF = PAID_FLAT
            val wrapped = listOf(
                Wrapped("p_platform_four", "u_001", "Platform Four", "A tea vendor on platform four keeps a list of everyone who never comes back for their change.",
                    SHORT, 10, "mumbai_andheri_west", 300, 1, listOf("hi"), listOf("Drama"),
                    listOf(Crew("u_025", ACTOR, UNPAID, footage = 6))),
                Wrapped("p_dabba", "u_001", "Dabba", "A dabbawala delivers the same lunch to an empty desk for a week and decides to find out why.",
                    SHORT, 9, "mumbai_versova", 200, 1, listOf("hi", "mr"), listOf("Drama"),
                    listOf(Crew("u_026", ACTOR, UNPAID, footage = 6))),
                Wrapped("p_low_tide", "u_001", "Low Tide", "Two sisters wait out the low tide at Juhu to scatter their father's ashes, and argue about everything else.",
                    SHORT, 12, "mumbai_juhu", 110, 2, listOf("hi", "en"), listOf("Drama"),
                    listOf(Crew(DemoIds.MEERA, ACTOR, UNPAID, footage = 6))),
                Wrapped("p_sea_link", "u_002", "Sea Link Sunday", "A cab driver and a runaway bride spend one long Sunday crossing the Sea Link back and forth.",
                    SHORT, 15, "mumbai_bandra", 260, 2, listOf("hi", "en"), listOf("Comedy", "Drama"), listOf(
                        Crew("u_003", DP, PDY, 6000, kit = 3000), Crew("u_004", CAMERA_OP, PDY, 3000),
                        Crew("u_006", SOUND_MIXER, PDY, 3000, kit = 1500), Crew(DemoIds.KABIR, GAFFER, PDY, 3000, kit = 1500),
                        Crew("u_014", FIRST_AC, PDY, 2000), Crew("u_010", FIRST_AD, PDY, 3000), Crew("u_016", PRODUCTION_DESIGNER, S, 1500, finalFilm = 10),
                        Crew("u_018", HMU, S, 1500), Crew(DemoIds.MEERA, ACTOR, S, 1500, footage = 6), Crew("u_025", ACTOR, S, 1500, footage = 6),
                        Crew("u_028", ACTOR, PDY, 6000, footage = 6), Crew("u_008", EDITOR, PF, 20000, postDays = 40),
                        Crew("u_020", COMPOSER, PF, 15000, postDays = 50))),
                Wrapped("p_third_floor", "u_002", "Third Floor", "A spec ad for a lift company where the lift never comes and nobody minds.",
                    SPEC_AD, 2, "mumbai_lower_parel", 170, 1, listOf("hi", "en"), listOf("Comedy"), listOf(
                        Crew("u_004", DP, PDY, 5000, kit = 1500), Crew("u_007", SOUND_MIXER, PDY, 2500, kit = 1000),
                        Crew(DemoIds.KABIR, GAFFER, PDY, 3000, kit = 1500), Crew("u_014", FIRST_AC, PDY, 2000), Crew("u_010", FIRST_AD, PDY, 3000),
                        Crew("u_016", PRODUCTION_DESIGNER, S, 1500), Crew("u_018", HMU, S, 1500), Crew("u_026", ACTOR, PDY, 4000, footage = 4),
                        Crew("u_028", ACTOR, PDY, 6000, footage = 4), Crew("u_008", EDITOR, PF, 12000, postDays = 20),
                        Crew("u_019", COLORIST, PF, 12000, postDays = 25), Crew("u_069", SOUND_DESIGNER, PF, 15000, postDays = 30))),
                Wrapped(DemoIds.P_RENT_DAY, "u_002", "Rent Day", "On rent day, four flatmates each try to borrow money from the other three.",
                    SHORT, 14, "mumbai_juhu", 4, 2, listOf("hi", "en"), listOf("Comedy"), listOf(
                        Crew("u_003", DP, PDY, 6000, kit = 3000), Crew("u_007", SOUND_MIXER, PDY, 2500, outcome = Outcome.RELEASED, notice = 240),
                        Crew("u_006", SOUND_MIXER, PDY, 3000, kit = 1500), Crew(DemoIds.KABIR, GAFFER, PDY, 3000, kit = 1500),
                        Crew("u_014", FIRST_AC, PDY, 2000), Crew("u_010", FIRST_AD, PDY, 3000), Crew("u_016", PRODUCTION_DESIGNER, S, 1500, finalFilm = 10),
                        Crew("u_018", HMU, S, 1500), Crew("u_025", ACTOR, S, 1500, footage = 6), Crew("u_026", ACTOR, S, 1500, footage = 6),
                        Crew("u_008", EDITOR, PF, 20000, postDays = 24, outcome = Outcome.BOOKED_POST),
                        Crew("u_020", COMPOSER, PF, 15000, postDays = 30, outcome = Outcome.BOOKED_POST))),
                Wrapped(DemoIds.P_HOLD_MUSIC, DemoIds.WEAK_KEPT_OWNER, "Hold Music", "A call-centre worker falls for a customer who keeps calling just to stay on hold.",
                    SHORT, 11, "mumbai_versova", 120, 2, listOf("hi", "en"), listOf("Romance", "Comedy"), listOf(
                        Crew(DemoIds.KABIR, GAFFER, S, 1500), Crew("u_004", DP, PDY, 4000, outcome = Outcome.RELEASED, notice = 30),
                        Crew("u_006", SOUND_MIXER, PDY, 2500, outcome = Outcome.RELEASED, notice = 20), Crew("u_018", HMU, S, 1200))),
                Wrapped(DemoIds.P_IRON_GATE, DemoIds.WEAK_FOOTAGE_OWNER, "Iron Gate", "A watchman locks the gate of a closing mill for the last time and three men try to stop him.",
                    SHORT, 12, "mumbai_goregaon_film_city", 75, 2, listOf("hi"), listOf("Action", "Drama"), listOf(
                        Crew(DemoIds.MEERA, ACTOR, S, 1500, footage = 6, outcome = Outcome.FOOTAGE_OPEN),
                        Crew("u_025", ACTOR, S, 1500, footage = 6, outcome = Outcome.FOOTAGE_NOT_RECEIVED),
                        Crew("u_026", ACTOR, S, 1500, footage = 6), Crew("u_028", ACTOR, PDY, 6000, footage = 6, outcome = Outcome.FOOTAGE_NOT_RECEIVED),
                        Crew("u_003", DP, S, 2000), Crew("u_007", SOUND_MIXER, PDY, 2500, kit = 1000))),
                Wrapped("p_grey_cast", "u_019", "Grey Cast", "The last plaster-idol makers in a Mumbai lane, and the idols they no longer make.",
                    DOCUMENTARY, 22, "mumbai_powai", 80, 0, listOf("mr", "hi"), listOf("Documentary"), listOf(
                        Crew("u_009", EDITOR, S, 8000, postDays = 20), Crew("u_020", COMPOSER, PF, 15000, postDays = 35)), postOnly = true),
                Wrapped("p_bus_stop", DemoIds.ROHAN, "Bus Stop, Basavanagudi", "Two retired teachers meet at the same bus stop every morning and never take the same bus.",
                    SHORT, 10, "bengaluru_jayanagar", 95, 2, listOf("kn", "en"), listOf("Drama"), listOf(
                        Crew("u_035", DP, UNPAID, finalFilm = 10), Crew("u_038", SOUND_MIXER, S, 1200), Crew("u_045", ACTOR, UNPAID, footage = 6))),
                Wrapped("p_kaapi_nights", "u_030", "Kaapi Nights", "A late-night coffee cart, three regulars and the night the cart doesn't show up.",
                    SHORT, 13, "bengaluru_malleshwaram", 280, 2, listOf("kn", "en"), listOf("Drama"), listOf(
                        Crew("u_033", DP, PDY, 5000, kit = 2000), Crew("u_037", SOUND_MIXER, PDY, 2500, kit = 1000), Crew("u_036", GAFFER, PDY, 2500, kit = 1200),
                        Crew("u_031", FIRST_AD, PDY, 2500), Crew("u_039", PRODUCTION_DESIGNER, S, 1500, finalFilm = 10),
                        Crew("u_047", ACTOR, S, 1500, footage = 6), Crew("u_048", ACTOR, S, 1500, footage = 6), Crew("u_045", ACTOR, S, 1500, footage = 6),
                        Crew("u_041", EDITOR, PF, 25000, postDays = 40), Crew("u_044", SOUND_DESIGNER, PF, 15000, postDays = 50))),
                Wrapped("p_second_shift", "u_030", "Second Shift", "Pilot about a hospital canteen that runs all night on gossip and idli.",
                    SERIES_PILOT, 25, "bengaluru_koramangala", 150, 3, listOf("kn", "en", "hi"), listOf("Comedy"), listOf(
                        Crew("u_034", DP, PDY, 4000, kit = 1200), Crew("u_037", SOUND_MIXER, PDY, 2500, outcome = Outcome.RELEASED, notice = 168),
                        Crew("u_038", SOUND_MIXER, S, 1500, kit = 800), Crew("u_036", GAFFER, PDY, 2500, kit = 1200), Crew("u_031", FIRST_AD, PDY, 2500),
                        Crew("u_039", PRODUCTION_DESIGNER, S, 1500), Crew("u_047", ACTOR, S, 2000, footage = 6), Crew("u_048", ACTOR, S, 2000, footage = 6),
                        Crew("u_045", ACTOR, S, 1500, footage = 6), Crew("u_041", EDITOR, PF, 30000, postDays = 45),
                        Crew("u_044", SOUND_DESIGNER, PF, 15000, postDays = 55))),
                Wrapped("p_lake_cleaners", "u_030", "The Lake Cleaners", "Volunteers clearing a dying lake find something that changes how the neighbourhood sees them.",
                    SHORT, 16, "bengaluru_hsr_layout", 45, 2, listOf("kn", "en"), listOf("Drama"), listOf(
                        Crew("u_033", DP, PDY, 5000, kit = 2000), Crew("u_037", SOUND_MIXER, PDY, 2500, kit = 1000), Crew("u_036", GAFFER, PDY, 2500, kit = 1200),
                        Crew("u_031", FIRST_AD, PDY, 2500), Crew("u_039", PRODUCTION_DESIGNER, S, 1500), Crew("u_047", ACTOR, S, 1500, footage = 6),
                        Crew("u_048", ACTOR, S, 1500, footage = 6), Crew("u_041", EDITOR, PF, 25000, postDays = 30),
                        Crew("u_044", SOUND_DESIGNER, PF, 15000, postDays = 35), Crew("u_068", COMPOSER, PF, 20000, postDays = 40))),
                Wrapped("p_half_day", "u_031", "Half Day", "A school principal takes a half day off for the first time in thirty years.",
                    SHORT, 8, "bengaluru_koramangala", 130, 1, listOf("kn", "en"), listOf("Comedy"), listOf(
                        Crew("u_034", DP, S, 1500), Crew("u_038", SOUND_MIXER, S, 1200))),
                Wrapped("p_silk_board", "u_033", "Silk Board", "A music video shot entirely in a traffic jam at the city's most famous junction.",
                    MUSIC_VIDEO, 4, "bengaluru_electronic_city", 200, 1, listOf("kn"), listOf("Music"), listOf(
                        Crew("u_036", GAFFER, PDY, 2500, kit = 1200), Crew("u_042", EDITOR, S, 4000, postDays = 14))),
                Wrapped("p_kothrud_calling", "u_052", "Kothrud Calling", "A 48-hour film about a wrong number that keeps calling with the right advice.",
                    CHALLENGE_48H, 7, "pune_kothrud", 220, 2, listOf("mr", "en"), listOf("Comedy"), listOf(
                        Crew("u_054", CAMERA_OP, S, 1200, kit = 1000), Crew("u_055", HMU, S, 1000))),
                Wrapped("p_ferry_times", "u_060", "Ferry Times", "The last ferry of the day, a boatman's retirement and a passenger who won't get off.",
                    SHORT, 12, "kochi_fort_kochi", 180, 2, listOf("ml", "en"), listOf("Drama"), listOf(
                        Crew("u_061", EDITOR, PF, 15000, postDays = 40), Crew("u_062", COMPOSER, PF, 12000, postDays = 50))),
                Wrapped("p_low_season", "u_063", "Low Season", "A motel in the off-season, an owner who won't sell, and one guest who stays too long.",
                    SHORT, 15, "los_angeles_silver_lake", 240, 3, listOf("en"), listOf("Drama"), listOf(
                        Crew("u_065", FIRST_AD, PDY, 450), Crew("u_070", FIRST_AC, PDY, 350), Crew("u_067", BOOM_OP, PDY, 300),
                        Crew("u_066", ACTOR, PDY, 250, footage = 6), Crew(DemoIds.DANA, SOUND_MIXER, PDY, 400, kit = 150, perks = setOf(Perk.TRAVEL_COVERED, Perk.LODGING)),
                        Crew("u_068", COMPOSER, PF, 2500, postDays = 45), Crew("u_069", SOUND_DESIGNER, PF, 2000, postDays = 50)), lodging = true),
                Wrapped("p_westbound", "u_063", "Westbound", "Two strangers share a ride west out of the city and pretend to be people they're not.",
                    SHORT, 12, "los_angeles_downtown", 90, 2, listOf("en", "es"), listOf("Drama"), listOf(
                        Crew("u_065", FIRST_AD, PDY, 450), Crew("u_070", FIRST_AC, PDY, 350), Crew("u_067", BOOM_OP, PDY, 300),
                        Crew("u_066", ACTOR, PDY, 250, footage = 6), Crew(DemoIds.DANA, SOUND_MIXER, PDY, 400, kit = 150, perks = setOf(Perk.TRAVEL_COVERED, Perk.LODGING)),
                        Crew("u_068", COMPOSER, PF, 2500, postDays = 40), Crew("u_069", SOUND_DESIGNER, PF, 2000, postDays = 45)), lodging = true),
                Wrapped("p_peachtree", DemoIds.WEAK_PAY_OWNER, "Peachtree Local", "A night on the Peachtree local with a driver who knows every passenger's story.",
                    SHORT, 10, "atlanta_midtown", 140, 2, listOf("en"), listOf("Drama"), listOf(
                        Crew(DemoIds.DANA, SOUND_MIXER, PDY, 400, kit = 150, outcome = Outcome.PAID_LATE), Crew("u_074", CAMERA_OP, PDY, 450),
                        Crew("u_075", GRIP, PDY, 300, outcome = Outcome.PAID_LATE), Crew("u_072", DIRECTOR, PF, 1500, terms = WITHIN_7_DAYS))),
            )
            wrapped.forEach { buildWrapped(it) }
        }

        private val personaMatchIds = mapOf(
            (DemoIds.P_IRON_GATE to DemoIds.MEERA) to DemoIds.M_MEERA_IRON_GATE,
            (DemoIds.P_RENT_DAY to DemoIds.KABIR) to DemoIds.M_KABIR_RENT_DAY,
            (DemoIds.P_HOLD_MUSIC to DemoIds.KABIR) to DemoIds.M_KABIR_HOLD_MUSIC,
        )

        private val weakOwners = setOf(DemoIds.WEAK_KEPT_OWNER, DemoIds.WEAK_FOOTAGE_OWNER, DemoIds.WEAK_PAY_OWNER)

        private fun buildWrapped(w: Wrapped) {
            val end = d(-w.endAgo)
            val dates = if (w.postOnly) emptyList() else (0 until w.days).map { end.minusDays((w.days - 1 - it).toLong()) }
            val start = dates.minOrNull() ?: end.minusDays(60)
            val p = project(w.id, w.owner, w.title, w.logline, w.format, w.runtime, w.genres, w.langs, w.areaId, dates,
                stage = Stage.POST, ambition = FESTIVAL, lodging = if (w.lodging) true else null, audition = if (w.crew.any { Taxonomy.isCast(it.code) }) SELF_TAPE else null,
                lock = if (w.postOnly || w.crew.any { it.code == COMPOSER || it.code == SOUND_DESIGNER }) end.plusDays(if (w.postOnly) 0 else 14) else null,
                status = ProjectStatus.WRAPPED, published = (w.endAgo + 30) * 24.0,
                address = if (w.postOnly) null else "Location on file with the production")
            projects[p.id] = p.copy(updatedAt = at(end, 20))
            val owner = profiles.getValue(w.owner)
            val ownerSlow = w.owner in weakOwners

            w.crew.forEachIndexed { i, c ->
                val post = c.postDays != null
                val rid = "r_${w.id.removePrefix("p_")}_${i + 1}"
                val r = role(rid, p, c.code, c.comp, c.amount, kit = c.kit, terms = c.terms, perks = c.perks, footage = c.footage,
                    finalFilm = c.finalFilm, remote = post, deadline = c.postDays?.let { end.plusDays(it.toLong()) },
                    status = if (c.outcome == Outcome.RELEASED) RoleStatus.CLOSED else RoleStatus.FILLED)
                roles[rid] = r.copy(applicationsCount = 1, filledCount = if (c.outcome == Outcome.RELEASED) 0 else 1)
                val mid = personaMatchIds[w.id to c.pid] ?: ("m_" + w.id.removePrefix("p_") + "_" + c.pid.removePrefix("u_"))
                val created = at(start.minusDays(25L + rnd.nextInt(10)), 11)
                val decided = created + (if (ownerSlow) 50 + rnd.nextInt(40) else 3 + rnd.nextInt(20)) * HOUR
                val app = newApp(rid, c.pid, created, ApplicationStatus.MATCHED, note = DemoText.applicantNote(profiles.getValue(c.pid), r, rnd), decided = decided)
                val terms = SeedKit.terms(roles.getValue(rid), p, owner.id, owner.shortName)
                val sent = decided + 6 * HOUR
                val booked = sent + (3 + rnd.nextInt(20)) * HOUR
                cards["bc_${mid}_1"] = BookingCard("bc_${mid}_1", mid, 1, terms, BookingStatus.ACCEPTED, owner.id, sent, booked)
                systemTerms(mid, roles.getValue(rid), p, decided)
                msg(mid, null, "Booking card v1", sent, MessageKind.BOOKING, cardId = "bc_${mid}_1")
                msg(mid, null, "Booked. ${profiles.getValue(c.pid).shortName} accepted the booking card.", booked, MessageKind.SYSTEM)

                if (c.outcome == Outcome.RELEASED) {
                    val firstDay = at(dates.first(), 7)
                    val releasedAt = firstDay - c.notice * HOUR
                    matches[mid] = Match(mid, app.id, p.id, rid, owner.id, c.pid, MatchState.RELEASED, isPostRole = false,
                        withdrawReason = if (c.notice < 72) "Schedule changed" else "Went with someone else", noticeHours = c.notice,
                        releasedByOwner = true, createdAt = decided, bookedAt = booked, endedAt = releasedAt)
                    msg(mid, null, "${owner.shortName} released this booking with ${DemoText.noticeText(c.notice)}.", releasedAt, MessageKind.SYSTEM)
                    apps[app.id] = app.copy(status = ApplicationStatus.CLOSED)
                    return@forEachIndexed
                }

                val deliveredAt: LocalDate = if (post) terms.deliveryDate!! else end
                val stillBooked = c.outcome == Outcome.BOOKED_POST
                val endedAt = if (stillBooked) null else at(deliveredAt.plusDays(1), 10)
                matches[mid] = Match(mid, app.id, p.id, rid, owner.id, c.pid, if (stillBooked) MatchState.BOOKED else MatchState.WRAPPED,
                    isPostRole = post, createdAt = decided, bookedAt = booked, endedAt = endedAt)
                if (!stillBooked) msg(mid, null, "Wrapped. The wrap checklist is open.", endedAt!!, MessageKind.SYSTEM)

                // Wrap items
                SeedKit.wrapItems(mid, terms).forEach { item ->
                    items[item.id] = historyItem(item, c, w, owner.id, stillBooked)
                }

                // Shoot days + attendance (on-set crew)
                if (!post) {
                    dates.forEachIndexed { di, date ->
                        val sid = "sd_${w.id.removePrefix("p_")}_${di + 1}"
                        if (sid !in days) days[sid] = ShootDay(sid, p.id, date, LocalTime.of(7, 0), LocalTime.of(17, 0),
                            "Location on file with the production", SeedKit.mapsUrl(w.title), version = 1, publishedAt = at(date.minusDays(5), 18))
                        calls += CallTime(sid, c.pid, LocalTime.of(7, 0), ackAt = at(date.minusDays(4), 20), attendance = Attendance.ATTENDED)
                    }
                }

                // Feedback (double-blind; revealed when both submit, or 14 days after the first)
                if (!stillBooked) seedFeedback(mid, w, c, owner.id, endedAt!!, terms)
            }
        }

        private fun historyItem(item: WrapItem, c: Crew, w: Wrapped, ownerId: String, stillBooked: Boolean): WrapItem {
            val due = item.dueDate ?: anchor
            val dueMillis = at(due, 18)
            val past = due.isBefore(anchor)
            fun done(markedAt: Long, confirmed: Boolean = true) =
                item.copy(status = if (confirmed) WrapStatus.CONFIRMED else WrapStatus.DONE, markedAt = markedAt,
                    confirmedAt = if (confirmed) markedAt + (6 + rnd.nextInt(30)) * HOUR else null,
                    link = if (item.kind == WrapKind.FOOTAGE || item.kind == WrapKind.FINAL_FILM || item.kind == WrapKind.DELIVERY)
                        "https://demo.calltime.app/${item.kind.name.lowercase()}/${item.matchId}" else null,
                    note = if (item.kind == WrapKind.PAID) listOf("Paid via UPI", "Bank transfer", "Paid in cash on wrap").random(rnd) else null)
            if (stillBooked) return item
            return when (item.kind) {
                WrapKind.PAID -> when {
                    c.outcome == Outcome.PAID_LATE -> done(dueMillis + 5 * DAY)
                    w.id == DemoIds.P_RENT_DAY -> done(at(due, 21), confirmed = c.pid != DemoIds.KABIR && rnd.nextBoolean())
                    else -> done(dueMillis - (rnd.nextInt(10)) * HOUR)
                }
                WrapKind.FOOTAGE -> when (c.outcome) {
                    Outcome.FOOTAGE_OPEN -> item
                    Outcome.FOOTAGE_NOT_RECEIVED -> item.copy(status = WrapStatus.NOT_RECEIVED, confirmedAt = dueMillis + 2 * DAY)
                    else -> if (past) done(dueMillis - (2 + rnd.nextInt(5)) * DAY) else item
                }
                WrapKind.DELIVERY -> done(dueMillis - (1 + rnd.nextInt(3)) * DAY)
                WrapKind.FINAL_FILM, WrapKind.CREDIT -> if (past) done(dueMillis - (1 + rnd.nextInt(6)) * DAY) else item
            }
        }

        private fun seedFeedback(mid: String, w: Wrapped, c: Crew, ownerId: String, endedAt: Long, terms: BookingTerms) {
            val m = matches.getValue(mid)
            val weak = w.owner in weakOwners
            val paidAns = when {
                c.comp == UNPAID -> PaidAnswer.NOT_PAID_ROLE
                c.outcome == Outcome.PAID_LATE -> PaidAnswer.LATE
                else -> PaidAnswer.ON_TIME
            }
            val delivered = when (c.outcome) {
                Outcome.FOOTAGE_OPEN -> DeliveredAnswer.NOT_YET
                Outcome.FOOTAGE_NOT_RECEIVED -> DeliveredAnswer.NO
                else -> DeliveredAnswer.YES
            }
            val collabAnswers = FeedbackAnswers(paid = paidAns, delivered = delivered, feltSafe = true, kept = KeptAnswer.YES,
                workAgain = if (weak) (c.pid == DemoIds.KABIR || rnd.nextDouble() < 0.3) else rnd.nextDouble() < 0.92)
            val ownerAnswers = if (m.isPostRole) FeedbackAnswers(deliveredOnTime = if (rnd.nextDouble() < 0.9) OnTimeAnswer.YES else OnTimeAnswer.LATE,
                revisions = TriAnswer.YES, workAgain = rnd.nextDouble() < 0.9)
            else FeedbackAnswers(showedUp = if (rnd.nextDouble() < 0.95) ShowedUpAnswer.YES else ShowedUpAnswer.LATE,
                prepared = if (rnd.nextDouble() < 0.8) TriAnswer.YES else TriAnswer.SOMEWHAT,
                workAgain = c.pid == DemoIds.MEERA || w.owner in DemoIds.PERSONA_USERS || rnd.nextDouble() < 0.9)

            val recent = w.id == DemoIds.P_RENT_DAY
            val ownerSubmits = recent || c.pid in DemoIds.PERSONA_USERS || w.owner in DemoIds.PERSONA_USERS || rnd.nextDouble() < 0.9
            val collabSubmits = !recent && rnd.nextDouble() < 0.82 || (c.pid in DemoIds.PERSONA_USERS && !recent)
            val tOwner = endedAt + (10 + rnd.nextInt(60)) * HOUR
            val tCollab = endedAt + (8 + rnd.nextInt(80)) * HOUR
            val both = ownerSubmits && collabSubmits
            val firstAt = listOfNotNull(if (ownerSubmits) tOwner else null, if (collabSubmits) tCollab else null).minOrNull() ?: return
            val revealAt = when {
                both -> maxOf(tOwner, tCollab)
                firstAt + 14 * DAY <= now -> firstAt + 14 * DAY
                else -> null
            }
            if (ownerSubmits) feedback += Feedback("fb_${mid}_o", mid, ownerId, c.pid, ownerAnswers, tOwner, revealAt)
            if (collabSubmits) feedback += Feedback("fb_${mid}_c", mid, c.pid, ownerId, collabAnswers, tCollab, revealAt)
        }

        // ───────────── Realistic pending applications on open roles ─────────────

        private fun buildFillerApplications() {
            val skip = setOf(DemoIds.R_FC_DP, DemoIds.R_FC_SOUND, DemoIds.R_FC_ACTOR, DemoIds.R_FC_EDITOR)
            val openRoles = roles.values.filter { r ->
                r.status == RoleStatus.OPEN && projects[r.projectId]?.status == ProjectStatus.OPEN && r.id !in skip &&
                    matches.values.none { it.roleId == r.id && it.state == MatchState.BOOKED }
            }
            for (r in openRoles) {
                val p = projects.getValue(r.projectId)
                val city = cityOf(p.areaId)
                val taken = apps.values.filter { it.roleId == r.id }.map { it.applicantId }.toSet()
                val booked = matches.values.filter { it.projectId == p.id }.map { it.collaboratorId }.toSet()
                val pool = profiles.values.filter { u ->
                    u.id != p.ownerId && u.id !in DemoIds.PERSONA_USERS && u.id != DemoIds.CASTING_HUB && u.id !in taken && u.id !in booked &&
                        (u.primaryRole == r.roleCode || u.secondaryRoles.any { it.code == r.roleCode } || (r.stepUpFriendly && r.roleCode in u.stepUpRoles)) &&
                        (u.cityId == city.id || (r.remoteOk && u.remoteOk && DemoCities.city(u.cityId)?.country == city.country)) &&
                        (r.compType != UNPAID || u.compMode != CompMode.PAID_ONLY)
                }.shuffled(rnd)
                val n = minOf(pool.size, rnd.nextInt(0, 5))
                pool.take(n).forEach { u ->
                    newApp(r.id, u.id, ago(2.0 + rnd.nextInt(9 * 24)), ApplicationStatus.PENDING,
                        note = DemoText.applicantNote(u, r, rnd), answer = r.screeningQuestion?.let { DemoText.screeningAnswer(rnd) })
                }
            }
        }

        // ───────────── Reports + persona notifications ─────────────

        private fun buildReportsAndNotifications() {
            // Two earlier reports on the scam account; a third (Meera's) auto-hides it from decks (PRD §5.5).
            reports += ReportRow("u_025", Report("rep_0001", ReportTarget.PROFILE, DemoIds.CASTING_HUB, ReportReason.MONEY_REQUEST,
                "Asked for a registration fee after shortlisting.", ReportStatus.REVIEWING, ago(6 * 24.0)), DemoIds.CASTING_HUB)
            reports += ReportRow("u_027", Report("rep_0002", ReportTarget.PROFILE, DemoIds.CASTING_HUB, ReportReason.MONEY_REQUEST,
                "Wanted payment for a portfolio shoot.", ReportStatus.RECEIVED, ago(40.0)), DemoIds.CASTING_HUB)

            var n = 1
            fun note(user: String, kind: NotificationKind, title: String, body: String, link: String?, hoursAgo: Double) {
                notes += NotificationRow(user, AppNotification("n_%04d".format(n++), kind, title, body, link, ago(hoursAgo)))
            }
            val cc = projects.getValue(DemoIds.P_CUTTING_CHAI)
            val ccDate = com.tff.app.core.Dates.short(cc.shootDates.first())
            note(DemoIds.MEERA, NotificationKind.ASK_RECEIVED, "Farida A. wants you back", "Rebook: Lead actor on Tiffin Wars · ₹2,500/day.", "calltime://inbox", 5.0)
            note(DemoIds.MEERA, NotificationKind.DAY_SHEET, "Day sheet · Cutting Chai", "Your call is $CC_MEERA_CALL on $ccDate. Tap Got it so the production knows.",
                "calltime://d/${cc.id}/${DemoIds.SD_CUTTING_CHAI_1}", 19.5)
            val ig = items.getValue(DemoIds.WI_MEERA_IRON_GATE_FOOTAGE)
            note(DemoIds.MEERA, NotificationKind.WRAP_OVERDUE, "Footage overdue · Iron Gate",
                "Your scenes were due ${com.tff.app.core.Dates.short(ig.dueDate!!)}. We've nudged the production.", "calltime://wrap/m/${DemoIds.M_MEERA_IRON_GATE}", 9.0)
            note(DemoIds.MEERA, NotificationKind.MATCH_CREATED, "In talks · Roommates Wanted", "Casting Hub Official wants to talk about Lead actor.",
                "calltime://m/${DemoIds.M_MEERA_SCAM}", 26.0)
            note(DemoIds.ROHAN, NotificationKind.APPLICATION_RECEIVED, "New applicants · Filter Coffee, 4 AM",
                "24 people applied across 4 roles. Every applicant gets an answer.", "calltime://p/${DemoIds.P_FILTER_COFFEE}", 4.0)
            note(DemoIds.KABIR, NotificationKind.FEEDBACK_UNLOCKED, "Rent Day wrapped", "Give private feedback. Answers unlock when you both submit, or in 14 days.",
                "calltime://wrap/m/${DemoIds.M_KABIR_RENT_DAY}", 70.0)
            val (paidTitle, paidBody) = DemoText.wrapMarked(profiles.getValue(DemoIds.FARIDA).shortName, WrapKind.PAID, "Rent Day",
                cards["bc_${DemoIds.M_KABIR_RENT_DAY}_1"]?.terms)
            note(DemoIds.KABIR, NotificationKind.WRAP_ITEM, paidTitle, paidBody, "calltime://wrap/m/${DemoIds.M_KABIR_RENT_DAY}", 72.0)
            note(DemoIds.DANA, NotificationKind.BOOKING_CARD, "Booking card · Soft Light", "Sound design · \$2,400 flat · paid within 7 days of delivery.",
                "calltime://m/${DemoIds.M_DANA_SOFTLIGHT}", 3.0)
        }

        /** applicationsCount / filledCount / status from the seeded rows. */
        private fun recount() {
            val byRole = apps.values.groupBy { it.roleId }
            for ((rid, r) in roles.toMap()) {
                val p = projects.getValue(r.projectId)
                if (p.status == ProjectStatus.WRAPPED) continue
                val list = byRole[rid].orEmpty()
                val counted = list.count { it.origin == ApplicationOrigin.APPLIED && it.status !in setOf(ApplicationStatus.DECLINED, ApplicationStatus.EXPIRED) }
                val filled = matches.values.count { it.roleId == rid && it.state == MatchState.BOOKED }
                roles[rid] = r.copy(applicationsCount = counted, filledCount = filled,
                    status = if (r.status == RoleStatus.OPEN && filled >= r.count) RoleStatus.FILLED else r.status)
            }
        }
    }
}
