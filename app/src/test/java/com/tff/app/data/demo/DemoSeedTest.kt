package com.tff.app.data.demo

import com.tff.app.domain.ListingRules
import com.tff.app.domain.MatchEngine
import com.tff.app.domain.MessageSafety
import com.tff.app.domain.Severity
import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** PRD §15.2 DemoSeedTest + the §11.5 persona starting states. */
class DemoSeedTest {
    private val s = T.seed()
    private val engine = T.engine(s).first
    private val ix = engine.index()
    private val profiles = s.profiles.values.toList()
    private val open = s.projects.values.filter { it.status == ProjectStatus.OPEN }
    private val openRoles = s.roles.values.filter { it.status == RoleStatus.OPEN && s.projects[it.projectId]?.status == ProjectStatus.OPEN }
    private fun cityOf(p: Profile) = p.cityId

    @Test fun `seed is deterministic`() {
        assertEquals(DemoJson.encode(s), DemoJson.encode(T.seed()))
    }

    // ───────────── §11.2 cities ─────────────

    @Test fun `20 cities and 67 areas with the shared id convention`() {
        val cities = DemoCities.all
        assertEquals(20, cities.size)
        assertEquals(67, cities.sumOf { it.areas.size })
        cities.forEach { c ->
            assertEquals(DemoCities.snake(c.name), c.id)
            c.areas.forEach { a -> assertEquals(c.id + "_" + DemoCities.snake(a.name), a.id); assertEquals(c.id, a.cityId) }
        }
        assertEquals(setOf("mumbai", "bengaluru"), cities.filter { it.isLive }.map { it.id }.toSet())
        val early = cities.filter { it.isEarly }
        assertEquals(setOf("indore", "dehradun", "nagpur", "chandigarh", "ahmedabad", "jaipur", "lucknow", "mysuru"), early.map { it.id }.toSet())
        early.forEach { assertEquals(listOf(it.id + "_city_centre"), it.areas.map { a -> a.id }) }
        assertNotNull(DemoCities.area("mumbai_goregaon_film_city"))
        assertNotNull(DemoCities.area("bengaluru_jayanagar"))
        assertNotNull(DemoCities.city("delhi_ncr"))
        assertNotNull(DemoCities.city("los_angeles"))
        assertNotNull(DemoCities.area("chennai_t_nagar"))
    }

    @Test fun `domain Markets knows every listed city's country and currency`() {
        DemoCities.all.forEach { c ->
            assertEquals(c.id, c.country, com.tff.app.domain.Markets.countryOfCity(c.id))
            assertEquals(c.id, c.currency, com.tff.app.domain.Markets.currencyOfCity(c.id))
        }
        // every seeded role is priced in its city's currency, so the currency also tells the country
        s.roles.values.forEach { r -> assertEquals(r.id, DemoCities.city(s.projects.getValue(r.projectId).cityId)!!.currency, r.currency) }
    }

    @Test fun `seed v3 totals match DEMO_IDS md`() {
        assertEquals(3, DemoSeed.VERSION)
        assertEquals(68, s.projects.size)          // 47 open + 20 wrapped + Rohan's draft
        assertEquals(307, s.roles.size)            // v3: Cutting Chai's 1st AD, sound and hair & makeup (all booked)
        assertEquals(154, openRoles.size)
        assertEquals(154, s.matches.size)
        assertEquals(91, s.profiles.size)          // v3: + 11 Bengaluru crew (DemoPeople.SCOUT_POOL)
        // every v1 project id is still there
        (listOf(DemoIds.P_LAST_LOCAL, DemoIds.P_ROOMMATES, DemoIds.P_TIFFIN_WARS, DemoIds.P_TWO_ROOMS, DemoIds.P_PAPER_BOATS, DemoIds.P_DEAD_DROP,
            DemoIds.P_CUTTING_CHAI, DemoIds.P_FILTER_COFFEE, DemoIds.P_ROHAN_DRAFT, DemoIds.P_RAJWADA_RAIN, DemoIds.P_SOFT_LIGHT, DemoIds.P_YELLOW_LINE) +
            (1..13).map { "p_%03d".format(it) }).forEach { assertTrue(it, it in s.projects) }
        (14..36).forEach { assertEquals("p_%03d".format(it), ProjectStatus.OPEN, s.projects.getValue("p_%03d".format(it)).status) }
    }

    // ───────────── §11.3 profiles ─────────────

    @Test fun `91 profiles with the city and role distribution`() {
        // PRD §11.3's 80, plus 11 Bengaluru crew in seed v3 so Rohan's Scout has people (4 DPs, 4 sound, 3 editors)
        assertEquals(91, profiles.size)
        assertEquals(mapOf("mumbai" to 32, "bengaluru" to 33, "pune" to 6, "indore" to 3, "kochi" to 3, "los_angeles" to 8, "atlanta" to 6),
            profiles.groupingBy { cityOf(it) }.eachCount())
        val byRole = profiles.groupingBy { it.primaryRole }.eachCount()
        val expected = mapOf(
            RoleCode.ACTOR to 16, RoleCode.VOICE_ACTOR to 1, RoleCode.DIRECTOR to 7, RoleCode.WRITER to 4, RoleCode.FIRST_AD to 3,
            RoleCode.SECOND_AD to 1, RoleCode.SCRIPT_SUPERVISOR to 1, RoleCode.PRODUCER to 3, RoleCode.PA to 3, RoleCode.DP to 11,
            RoleCode.CAMERA_OP to 2, RoleCode.FIRST_AC to 2, RoleCode.GAFFER to 2, RoleCode.GRIP to 2, RoleCode.SOUND_MIXER to 9,
            RoleCode.BOOM_OP to 1, RoleCode.PRODUCTION_DESIGNER to 2, RoleCode.ART_ASSISTANT to 1, RoleCode.COSTUME to 1, RoleCode.HMU to 2,
            RoleCode.EDITOR to 8, RoleCode.COLORIST to 1, RoleCode.VFX_ARTIST to 2, RoleCode.SOUND_DESIGNER to 2, RoleCode.COMPOSER to 3,
            RoleCode.STUNT_COORDINATOR to 1,
        )
        assertEquals(expected, byRole)
    }

    @Test fun `levels comp modes and verification follow the percentages`() {
        assertEquals(mapOf(Level.FIRST_PROJECT to 17, Level.BUILDING_REEL to 34, Level.CREDITED_INDIE to 27, Level.WORKING_PRO to 13),
            profiles.groupingBy { it.level }.eachCount())
        assertEquals(mapOf(CompMode.PAID_ONLY to 17, CompMode.PAID_AND_SELECTIVE to 45, CompMode.OPEN_TO_UNPAID to 29),
            profiles.groupingBy { it.compMode }.eachCount())
        assertTrue(profiles.all { it.emailVerified && it.adultConfirmed })
        assertEquals(66, profiles.count { it.phoneVerified })
        assertEquals(30, profiles.count { it.idVerified })
        // PAID_ONLY is mostly working pros and sound
        val paidOnly = profiles.filter { it.compMode == CompMode.PAID_ONLY }
        assertTrue(paidOnly.count { it.level == Level.WORKING_PRO || Taxonomy.dept(it.primaryRole) == Dept.SOUND } >= paidOnly.size / 2)
    }

    @Test fun `languages availability and busy dates`() {
        assertTrue(profiles.all { "en" in it.languages })
        profiles.filter { it.cityId in setOf("mumbai", "pune") }.forEach { assertTrue(it.displayName, it.languages.any { l -> l == "hi" || l == "mr" }) }
        profiles.filter { it.cityId == "bengaluru" }.forEach { assertTrue(it.displayName, it.languages.any { l -> l in setOf("kn", "ta", "hi") }) }
        profiles.filter { it.cityId == "indore" }.forEach { assertTrue("hi" in it.languages) }
        profiles.filter { it.cityId == "kochi" }.forEach { assertTrue("ml" in it.languages) }
        assertTrue(profiles.count { it.cityId == "los_angeles" && "es" in it.languages } in 1..3)
        val india = profiles.filter { DemoCities.city(it.cityId)!!.country == "IN" }
        val weekendish = india.count { it.availabilityPattern == AvailabilityPattern.WEEKENDS_ONLY || it.availabilityPattern == AvailabilityPattern.EVENINGS_AND_WEEKENDS }
        assertTrue("India weekend share $weekendish/${india.size}", weekendish * 100 / india.size in 55..65)
        val us = profiles.filter { DemoCities.city(it.cityId)!!.country == "US" }
        assertTrue(us.count { it.availabilityPattern == AvailabilityPattern.ANY_DAY } * 100 / us.size in 65..75)
        profiles.forEach { p ->
            assertTrue(p.busyDates.size <= 6)
            p.busyDates.forEach { d -> assertTrue(d.isAfter(T.anchor) && !d.isAfter(T.anchor.plusDays(45))) }
        }
    }

    @Test fun `work links gear and remote coverage`() {
        profiles.filter { it.level != Level.FIRST_PROJECT }.forEach { p ->
            val reels = p.workLinks.filter { it.kind == LinkKind.REEL }
            assertTrue(p.displayName, reels.size in 1..3 && reels.all { it.roleCode == p.primaryRole })
        }
        profiles.filter { it.level == Level.FIRST_PROJECT }.forEach { p ->
            assertTrue(p.workLinks.none { it.kind == LinkKind.REEL })
            assertTrue(p.workLinks.count { it.kind == LinkKind.PORTFOLIO } <= 1)
        }
        assertEquals(24, profiles.count { p -> p.workLinks.any { it.kind == LinkKind.IMDB } })
        assertTrue(profiles.flatMap { it.workLinks }.all { it.url.startsWith("https://demo.calltime.app/") })
        // no faces: headshots are links to the demo host only, never images in the seed
        assertTrue(profiles.flatMap { it.workLinks }.filter { it.kind == LinkKind.HEADSHOT }.all { it.url.startsWith("https://demo.calltime.app/") })
        profiles.filter { it.primaryRole == RoleCode.SOUND_MIXER }.forEach { assertNotNull(it.displayName, it.kitFeeDay) }
        val dpCapable = profiles.filter { it.primaryRole == RoleCode.DP || RoleCode.DP in it.stepUpRoles || it.secondaryRoles.any { s -> s.code == RoleCode.DP } }
        assertTrue(dpCapable.size >= 10)
        assertTrue(dpCapable.count { it.gear.isNotEmpty() && it.primaryRole != RoleCode.GAFFER } >= 6)
        assertEquals(2, profiles.count { it.primaryRole == RoleCode.GAFFER && it.gear.isNotEmpty() })
        assertTrue(profiles.count { it.primaryRole == RoleCode.EDITOR && it.gear.any { g -> g.contains("edit station", true) } } >= 3)
        assertTrue(profiles.count { it.primaryRole == RoleCode.COMPOSER && it.remoteOk } >= 2)
        assertTrue(profiles.count { it.primaryRole == RoleCode.SOUND_DESIGNER && it.remoteOk } >= 2)
        val indore = profiles.filter { it.cityId == "indore" }
        val dir = indore.single { it.primaryRole == RoleCode.DIRECTOR }
        assertEquals(Level.FIRST_PROJECT, dir.level)
        assertTrue(dir.workLinks.isEmpty())
        assertTrue(indore.any { it.primaryRole == RoleCode.ACTOR } && indore.any { it.primaryRole == RoleCode.DP })
        val kochi = profiles.filter { it.cityId == "kochi" }
        assertTrue(kochi.any { it.primaryRole == RoleCode.EDITOR && it.remoteOk } && kochi.any { it.primaryRole == RoleCode.COMPOSER && it.remoteOk })
    }

    @Test fun `launch city scarce role guarantees`() {
        for (city in listOf("mumbai", "bengaluru")) {
            val ps = profiles.filter { it.cityId == city }
            fun n(code: RoleCode) = ps.count { it.primaryRole == code }
            assertTrue("$city sound", n(RoleCode.SOUND_MIXER) >= 2)
            assertTrue("$city dp", n(RoleCode.DP) >= 3)
            assertTrue("$city dp kits", ps.count { it.primaryRole == RoleCode.DP && it.gear.isNotEmpty() } >= 2)
            assertTrue("$city editors", n(RoleCode.EDITOR) >= 2)
            assertTrue("$city gaffer", n(RoleCode.GAFFER) >= 1)
            assertTrue("$city 1st AD", n(RoleCode.FIRST_AD) >= 1)
            assertTrue("$city producer", n(RoleCode.PRODUCER) >= 1)
            assertTrue("$city actors", n(RoleCode.ACTOR) >= 6)
            assertTrue("$city women actors", ps.count { it.primaryRole == RoleCode.ACTOR && it.pronouns == "she/her" } >= 3)
        }
    }

    @Test fun `history gives 45 people a past booking and 28 at least three`() {
        val booked = s.matches.values.filter { it.bookedAt != null && s.projects[it.projectId]?.status == ProjectStatus.WRAPPED }
        val per = booked.groupingBy { it.collaboratorId }.eachCount()
        assertEquals(45, per.size)
        assertEquals(28, per.count { it.value >= 3 })
        assertEquals(20, s.projects.values.count { it.status == ProjectStatus.WRAPPED })
        assertEquals(13, s.projects.values.filter { it.status == ProjectStatus.WRAPPED }.map { it.ownerId }.distinct().size)
        assertTrue(booked.size in 100..120)
        assertTrue(booked.count { it.isPostRole } >= 15)
        val items = s.wrapItems.values.filter { it.matchId in booked.map { m -> m.id }.toSet() }
        assertTrue("wrap items ${items.size}", items.size in 230..320)
        val past = items.filter { it.dueDate?.isBefore(T.anchor) == true }
        assertTrue(past.count { it.status == WrapStatus.CONFIRMED } * 100 / past.size >= 80)
        assertTrue("feedback ${s.feedback.size}", s.feedback.size in 160..220)
        val releases = s.matches.values.filter { it.state == MatchState.RELEASED && it.releasedByOwner }
        assertEquals(4, releases.size)
        assertEquals(2, releases.count { (it.noticeHours ?: 0) < 72 })
    }

    // ───────────── §11.4 projects ─────────────

    /** Seed v2 (FIX1): the v1 world (24 projects, ~60 roles) plus 23 projects so every persona's deck starts with ≥10 cards. */
    @Test fun `47 open projects with the city and format mix`() {
        assertEquals(47, open.size)
        assertEquals(mapOf("mumbai" to 17, "bengaluru" to 14, "pune" to 4, "indore" to 1, "kochi" to 1, "los_angeles" to 4, "atlanta" to 6),
            open.groupingBy { it.cityId }.eachCount())
        assertEquals(mapOf(Format.SHORT to 26, Format.MUSIC_VIDEO to 6, Format.STUDENT_THESIS to 4, Format.CHALLENGE_48H to 2,
            Format.SERIES_PILOT to 4, Format.VERTICAL to 1, Format.DOCUMENTARY to 1, Format.SPEC_AD to 3), open.groupingBy { it.format }.eachCount())
        assertEquals(41, open.map { it.ownerId }.distinct().size)
        assertEquals("open roles", 154, openRoles.size)
        open.forEach { p ->
            val n = openRoles.count { it.projectId == p.id }
            assertTrue("${p.title} has $n open roles", n in 2..4)
        }
        assertTrue(open.count { p -> s.matches.values.any { it.projectId == p.id && it.state == MatchState.BOOKED } } >= 30)
        // content mix: no nudity; one each of stunts, intimacy, firearms; a couple of night shoots
        assertTrue(open.none { ContentFlag.NUDITY in it.contentFlags })
        assertEquals(1, open.count { ContentFlag.STUNTS_FIGHTS in it.contentFlags })
        assertEquals(1, open.count { ContentFlag.INTIMACY_SCENE in it.contentFlags })
        assertEquals(1, open.count { ContentFlag.FIREARMS_PROPS in it.contentFlags })
        assertTrue(open.count { ContentFlag.NIGHT_SHOOT in it.contentFlags } in 2..3)
        assertTrue(open.count { it.contentFlags.isEmpty() } >= 40)
    }

    @Test fun `comp mix and sound roles are mostly paid`() {
        val byComp = openRoles.groupingBy { it.compType }.eachCount()
        val n = openRoles.size.toDouble()
        assertTrue(byComp.toString(), (byComp[CompType.PAID_DAY] ?: 0) / n in 0.20..0.30)
        assertTrue(byComp.toString(), (byComp[CompType.PAID_FLAT] ?: 0) / n in 0.07..0.13)
        assertTrue(byComp.toString(), (byComp[CompType.STIPEND] ?: 0) / n in 0.25..0.35)
        assertTrue(byComp.toString(), (byComp[CompType.UNPAID] ?: 0) / n in 0.30..0.40)
        val sound = openRoles.filter { Taxonomy.dept(it.roleCode) == Dept.SOUND || it.roleCode == RoleCode.SOUND_DESIGNER }
        assertTrue(sound.count { it.compType != CompType.UNPAID } * 100 / sound.size >= 80)
        val withPay = open.count { p -> s.roles.values.any { it.projectId == p.id && it.compType != CompType.UNPAID } }
        assertTrue(withPay * 100 / open.size >= 60)
    }

    @Test fun `dates are relative and in range`() {
        val onSet = open.filter { it.shootDates.isNotEmpty() }
        onSet.forEach { p ->
            val start = p.shootStart!!
            assertTrue("${p.title} $start", !start.isBefore(T.anchor.plusDays(3)) && !start.isAfter(T.anchor.plusDays(45)))
            assertTrue(p.hoursCap!! in 8..14)
        }
        assertTrue(onSet.count { !it.shootStart!!.isAfter(T.anchor.plusDays(10)) } in 6..12)
        assertEquals(2, onSet.count { it.hoursCap == 14 })
        assertTrue(onSet.count { it.dateCertainty == DateCertainty.LOCKED } * 100 / onSet.size in 60..80)
        val india = onSet.filter { DemoCities.city(it.cityId)!!.country == "IN" }
        val weekend = india.count { p -> p.shootDates.all { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY } }
        assertTrue("weekend $weekend/${india.size}", weekend * 100 / india.size >= 55)
        // every date anywhere in the world sits inside a window around the anchor
        val all = mutableListOf<LocalDate>()
        s.projects.values.forEach { all += it.shootDates; it.pictureLockDate?.let(all::add) }
        s.roles.values.forEach { it.postDeadline?.let(all::add) }
        s.shootDays.values.forEach { all += it.day }
        s.wrapItems.values.forEach { it.dueDate?.let(all::add) }
        s.bookingCards.values.forEach { all += it.terms.dates; it.terms.deliveryDate?.let(all::add); it.terms.payByDate?.let(all::add) }
        profiles.forEach { all += it.busyDates }
        all.forEach { assertTrue("$it", !it.isBefore(T.anchor.minusDays(400)) && !it.isAfter(T.anchor.plusDays(160))) }
        // relative: moving the anchor a week moves every shoot a week
        val later = T.seed(T.anchor.plusDays(7))
        s.projects.values.forEach { p -> assertEquals(p.shootDates.map { it.plusDays(7) }, later.projects.getValue(p.id).shootDates) }
    }

    @Test fun `every open listing passes ListingRules with no BLOCK`() {
        open.forEach { p ->
            val roles = s.roles.values.filter { it.projectId == p.id }
            val owner = s.profiles.getValue(p.ownerId)
            val city = DemoCities.city(p.cityId)!!
            val blocks = ListingRules.check(ProjectDraft(p, roles), owner, city, T.anchor).filter { it.severity == Severity.BLOCK }
            assertTrue("${p.title}: $blocks", blocks.isEmpty())
            val auto = ListingRules.check(ProjectDraft(p, roles), owner, city, T.anchor).filter { it.severity == Severity.AUTO }
            assertTrue("${p.title} would be relabelled: $auto", auto.isEmpty())
        }
    }

    @Test fun `must include items from 11_4`() {
        val postOnly = open.filter { ListingRules.isPostOnly(s.roles.values.filter { r -> r.projectId == it.id }) }
        assertEquals(2, postOnly.size)
        assertTrue(postOnly.any { it.id == DemoIds.P_SOFT_LIGHT && it.cityId == "los_angeles" })
        postOnly.forEach { p -> assertEquals(Stage.POST, p.stage); assertTrue(p.shootDates.isEmpty()); assertNotNull(p.pictureLockDate) }
        val remoteIndia = openRoles.filter { it.isPostRole && DemoCities.city(s.projects.getValue(it.projectId).cityId)!!.country == "IN" }
        assertTrue("remote post roles ${remoteIndia.size}", remoteIndia.size >= 8)
        assertTrue(openRoles.count { it.levelWanted == Level.FIRST_PROJECT || it.levelWanted == Level.BUILDING_REEL } >= 3)
        val indore = open.single { it.cityId == "indore" }
        assertEquals(listOf("hi"), indore.languages)
        assertEquals(Level.FIRST_PROJECT, s.profiles.getValue(indore.ownerId).level)
        val castRoles = openRoles.filter { it.isCast }
        assertTrue(castRoles.size >= 4)
        assertEquals(AuditionMode.entries.toSet(), castRoles.mapNotNull { s.projects[it.projectId]?.auditionMode }.toSet())
    }

    @Test fun `no contact info in seed text except tagged safety examples`() {
        val texts = mutableListOf<Pair<String, String>>()
        profiles.forEach { p ->
            texts += "name ${p.id}" to p.displayName
            p.bio?.let { texts += "bio ${p.id}" to it }
            p.affiliations.forEach { texts += "aff ${p.id}" to it }
            p.gear.forEach { texts += "gear ${p.id}" to it }
            p.workLinks.forEach { l -> l.title?.let { texts += "link ${p.id}" to it } }
        }
        s.projects.values.forEach { p ->
            texts += "title ${p.id}" to p.title; texts += "logline ${p.id}" to p.logline
            listOfNotNull(p.storySetting, p.safetyMeasures, p.exactAddress).forEach { texts += "project ${p.id}" to it }
        }
        s.roles.values.forEach { r ->
            texts += "credit ${r.id}" to r.creditTitle
            r.screeningQuestion?.let { texts += "q ${r.id}" to it }
            (r.roleDetails.values + r.scopeIncludes).forEach { texts += "role ${r.id}" to it }
        }
        s.applications.values.forEach { a -> listOfNotNull(a.note, a.screeningAnswer, a.declineReason).forEach { texts += "app ${a.id}" to it } }
        s.shootDays.values.forEach { d -> listOfNotNull(d.address, d.parkingNote, d.hospitalNote, d.mealsPlan, d.notes).forEach { texts += "day ${d.id}" to it } }
        s.wrapItems.values.forEach { w -> w.note?.let { texts += "wrap ${w.id}" to it } }
        s.reports.forEach { r -> r.report.details?.let { texts += "report" to it } }
        s.notifications.forEach { n -> texts += "note ${n.n.id}" to n.n.title; texts += "note ${n.n.id}" to n.n.body }
        s.bookingCards.values.forEach { c -> listOfNotNull(c.terms.notes, c.terms.callWindow).forEach { texts += "card ${c.id}" to it } }
        s.messages.filter { !it.safetyExample }.forEach { texts += "msg ${it.id}" to it.body }
        val leaks = texts.filter { (_, t) -> MessageSafety.hasContact(t) }
        assertTrue("contact info in seed text: $leaks", leaks.isEmpty())
        // the one scam example is tagged and is caught by MessageSafety exactly as the persona needs
        val examples = s.messages.filter { it.safetyExample }
        assertEquals(1, examples.size)
        val flags = MessageSafety.analyze(examples.single().body)
        assertTrue(flags.containsAll(setOf(MessageFlag.MONEY_FROM_TALENT, MessageFlag.OFF_PLATFORM, MessageFlag.CONTACT)))
        assertEquals(flags, examples.single().flags)
    }

    @Test fun `seeded owners have the strong weak and new records the PRD asks for`() {
        val owners = open.map { it.ownerId }.distinct()
        val reps = owners.associateWith { ix.reputation(it) }
        val weak = reps.filterValues { r -> listOfNotNull(r.paidOnTime, r.footageDelivered, r.keptBookings).any { it.value < 0.8 } }.keys
        assertEquals(setOf(DemoIds.WEAK_KEPT_OWNER, DemoIds.WEAK_FOOTAGE_OWNER, DemoIds.WEAK_PAY_OWNER), weak)
        assertEquals(Ratio(2, 4), reps.getValue(DemoIds.WEAK_KEPT_OWNER).keptBookings)
        assertEquals(Ratio(1, 4), reps.getValue(DemoIds.WEAK_FOOTAGE_OWNER).footageDelivered)
        assertEquals(Ratio(2, 4), reps.getValue(DemoIds.WEAK_PAY_OWNER).paidOnTime)
        val strong = reps.filterValues { r -> listOfNotNull(r.paidOnTime, r.footageDelivered, r.keptBookings).let { it.isNotEmpty() && it.all { x -> x.value >= 0.8 } } }.keys
        assertEquals(setOf(DemoIds.LAST_LOCAL_OWNER, DemoIds.FARIDA, DemoIds.ROHAN, "u_030", DemoIds.ELENA), strong)
        // §7.5 worked example: Last Local's owner has 3 shoots, footage 3/3, kept 3/3, no paid history, not ID-verified
        val ll = reps.getValue(DemoIds.LAST_LOCAL_OWNER)
        assertEquals(3, ll.shootsCompleted)
        assertEquals(Ratio(3, 3), ll.footageDelivered)
        assertEquals(Ratio(3, 3), ll.keptBookings)
        assertNull(ll.paidOnTime)
        assertFalse(s.profiles.getValue(DemoIds.LAST_LOCAL_OWNER).idVerified)
        assertEquals(Ratio(3, 3), reps.getValue(DemoIds.ROHAN).keptBookings)
    }

    // ───────────── §11.5 personas ─────────────

    @Test fun `meera starting state`() {
        val mine = s.applications.values.filter { it.applicantId == DemoIds.MEERA }
        assertEquals(2, mine.count { it.status == ApplicationStatus.PENDING && it.origin == ApplicationOrigin.APPLIED })
        val ask = mine.single { it.origin == ApplicationOrigin.INVITED && it.status == ApplicationStatus.PENDING }
        val askOwner = s.projects.getValue(s.roles.getValue(ask.roleId).projectId).ownerId
        assertTrue("rebook comes from a past owner", s.matches.values.any { it.ownerId == askOwner && it.collaboratorId == DemoIds.MEERA && it.state == MatchState.WRAPPED })
        assertTrue(s.roles.getValue(ask.roleId).isCast)
        val inbox = DemoQueries(engine).inbox(s, DemoIds.MEERA)
        assertTrue(inbox.asks.single().isRebook)

        val booked = s.matches.getValue(DemoIds.M_MEERA_CUTTING_CHAI)
        assertEquals(MatchState.BOOKED, booked.state)
        assertEquals(T.anchor.plusDays(6), s.projects.getValue(booked.projectId).shootStart)
        val call = s.callTimes.single { it.shootDayId == DemoIds.SD_CUTTING_CHAI_1 && it.profileId == DemoIds.MEERA }
        assertNull(call.ackAt)
        assertNotNull(s.shootDays.getValue(DemoIds.SD_CUTTING_CHAI_1).publishedAt)

        val ig = s.matches.getValue(DemoIds.M_MEERA_IRON_GATE)
        assertEquals(MatchState.WRAPPED, ig.state)
        val footage = s.wrapItems.getValue(DemoIds.WI_MEERA_IRON_GATE_FOOTAGE)
        assertEquals(WrapStatus.OPEN, footage.status)
        assertTrue(footage.dueDate!!.isBefore(T.anchor))

        val scam = s.matches.getValue(DemoIds.M_MEERA_SCAM)
        assertEquals(MatchState.TALKING, scam.state)
        assertEquals(DemoIds.CASTING_HUB, scam.ownerId)
        val first = s.messages.filter { it.matchId == scam.id && it.kind == MessageKind.TEXT }.minBy { it.createdAt }
        assertTrue(first.safetyExample)
        assertTrue(first.body.contains("registration fee") && first.body.contains("WhatsApp me"))
        // the listing itself is clean
        val p = s.projects.getValue(scam.projectId)
        val rules = ListingRules.check(ProjectDraft(p, s.roles.values.filter { it.projectId == p.id }), s.profiles.getValue(p.ownerId), DemoCities.city(p.cityId)!!, T.anchor)
        assertTrue(rules.none { it.severity == Severity.BLOCK })
        // the recipient sees the number masked
        val shown = DemoQueries(engine).messages(s, DemoIds.MEERA, scam.id).single { it.id == first.id }
        assertFalse(shown.body.contains("98201"))
        assertTrue(shown.body.contains("shared contact hidden"))
        // Meera's deck shows Last Local's lead (the §7.5 role) and only gate-passing roles
        val deck = T.passing(engine, DemoIds.MEERA)
        assertTrue(deck.any { it.role.id == DemoIds.R_LL_LEAD })
    }

    @Test fun `rohan starting state`() {
        val fc = s.projects.getValue(DemoIds.P_FILTER_COFFEE)
        assertEquals(DemoIds.ROHAN, fc.ownerId)
        assertEquals(ProjectStatus.OPEN, fc.status)
        assertEquals(AuditionMode.SELF_TAPE, fc.auditionMode)
        val roles = s.roles.values.filter { it.projectId == fc.id && it.status == RoleStatus.OPEN }.associateBy { it.id }
        assertEquals(setOf(DemoIds.R_FC_DP, DemoIds.R_FC_SOUND, DemoIds.R_FC_ACTOR, DemoIds.R_FC_EDITOR), roles.keys)
        assertEquals(CompType.STIPEND, roles.getValue(DemoIds.R_FC_DP).compType)
        assertEquals(CompType.PAID_DAY, roles.getValue(DemoIds.R_FC_SOUND).compType)
        assertEquals(CompType.UNPAID, roles.getValue(DemoIds.R_FC_ACTOR).compType)
        assertEquals(6, roles.getValue(DemoIds.R_FC_ACTOR).footageDueWeeks)
        val ed = roles.getValue(DemoIds.R_FC_EDITOR)
        assertTrue(ed.isPostRole && ed.compType == CompType.STIPEND)
        fun apps(rid: String) = s.applications.values.count { it.roleId == rid && it.origin == ApplicationOrigin.APPLIED }
        assertEquals(listOf(7, 3, 10, 4), listOf(DemoIds.R_FC_DP, DemoIds.R_FC_SOUND, DemoIds.R_FC_ACTOR, DemoIds.R_FC_EDITOR).map(::apps))
        val talk = s.matches.getValue(DemoIds.M_ROHAN_DP)
        assertEquals(MatchState.TALKING, talk.state)
        assertEquals(DemoIds.R_FC_DP, talk.roleId)
        val draft = s.projects.getValue(DemoIds.P_ROHAN_DRAFT)
        assertEquals(ProjectStatus.DRAFT, draft.status)
        val res = ListingRules.check(ProjectDraft(draft, s.roles.values.filter { it.projectId == draft.id }), s.profiles.getValue(DemoIds.ROHAN),
            DemoCities.city(draft.cityId)!!, T.anchor).filter { it.severity == Severity.BLOCK }
        assertTrue(res.isNotEmpty())
        assertEquals(setOf("R02"), res.map { it.rule }.toSet())
    }

    @Test fun `kabir starting state`() {
        val k = s.profiles.getValue(DemoIds.KABIR)
        assertEquals(CompMode.PAID_AND_SELECTIVE, k.compMode)
        assertEquals(listOf(RoleCode.DP), k.stepUpRoles)
        val deck = T.passing(engine, DemoIds.KABIR)
        assertTrue("step-up DP roles: ${deck.map { it.role.id }}", deck.count { it.role.roleCode == RoleCode.DP && it.role.stepUpFriendly } >= 3)
        val rent = s.matches.getValue(DemoIds.M_KABIR_RENT_DAY)
        assertEquals(MatchState.WRAPPED, rent.state)
        assertEquals(RoleCode.GAFFER, s.roles.getValue(rent.roleId).roleCode)
        val fs = DemoQueries(engine).feedbackState(s, DemoIds.KABIR, rent.id)
        assertTrue(fs.unlocked && !fs.iSubmitted && !fs.revealed)
        val keep = DemoQueries(engine).keepList(s, DemoIds.KABIR)
        assertTrue(keep.any { it.reputation.keptBookings == Ratio(2, 4) })
    }

    @Test fun `dana starting state`() {
        val d = s.profiles.getValue(DemoIds.DANA)
        assertEquals(CompMode.PAID_ONLY, d.compMode)
        assertEquals(350, s.privateRates[DemoIds.DANA])
        assertTrue(d.secondaryRoles.any { it.code == RoleCode.SOUND_DESIGNER })
        val deck = T.passing(engine, DemoIds.DANA)
        val us = deck.filter { DemoCities.city(it.project.cityId)!!.country == "US" }
        assertTrue("US sound roles: ${us.map { it.role.id }}", us.count { it.role.roleCode == RoleCode.SOUND_MIXER || it.role.roleCode == RoleCode.SOUND_DESIGNER } >= 3)
        assertTrue(deck.all { it.role.isPaid })
        assertTrue(deck.any { it.role.isPostRole && it.project.cityId != "atlanta" })
        val m = s.matches.getValue(DemoIds.M_DANA_SOFTLIGHT)
        assertEquals(MatchState.OFFERED, m.state)
        assertTrue(m.isPostRole)
        val card = s.bookingCards.getValue(DemoIds.BC_DANA_SOFTLIGHT_1)
        assertEquals(BookingStatus.SENT, card.status)
        val t = card.terms
        assertEquals(RoleCode.SOUND_DESIGNER, t.roleCode)
        assertTrue(t.isPostRole && t.dates.isEmpty())
        assertEquals(T.anchor.plusDays(5), t.pictureLockDate)
        assertEquals(T.anchor.plusDays(33), t.deliveryDate)
        assertEquals(CompType.PAID_FLAT, t.compType)
        assertEquals(2400, t.amount)
        assertEquals("USD", t.currency)
        assertEquals(PaymentTerms.WITHIN_7_DAYS, t.paymentTerms)
        assertEquals(T.anchor.plusDays(40), t.payByDate)
        assertTrue(t.deliverables.any { it.kind == WrapKind.DELIVERY && it.owedBy == OwedBy.COLLABORATOR })
        assertTrue(t.deliverables.none { it.kind == WrapKind.FOOTAGE })
    }

    @Test fun `build my own in indore sees the early city honestly`() {
        val (e, _) = T.engine()
        val sess = e.startDemo(null)
        assertFalse(sess.hasProfile)
        val me = Profile(sess.userId, "Test Writer", primaryRole = RoleCode.WRITER, level = Level.FIRST_PROJECT, cityId = "indore",
            areaId = "indore_city_centre", remoteOk = true, languages = listOf("hi", "en"), compMode = CompMode.OPEN_TO_UNPAID,
            availabilityPattern = AvailabilityPattern.WEEKENDS_ONLY)
        e.saveProfile(sess.userId, me, null).getOrThrow()
        assertTrue(e.state.value.session!!.hasProfile)
        val q = DemoQueries(e)
        val density = q.density(e.state.value, "indore")
        assertTrue(density.isEarly)
        assertEquals(4, density.people)   // 3 seeded people + the new user
        assertEquals(3, q.density(T.seed(), "indore").people)
        assertEquals(1, density.openProjects)
        val ix = e.index()
        val deck = MatchEngine.buildDeck(T.viewer(ix, ix.profile(sess.userId)!!), q.discoverCandidates(ix.s, sess.userId, null), ix.today, ix.now)
        assertTrue(deck.isNotEmpty())
        assertTrue(deck.any { it.item.project.id == DemoIds.P_RAJWADA_RAIN })
        assertTrue(deck.any { it.item.role.isPostRole && it.item.project.cityId != "indore" })
        assertTrue(deck.any { it.item.role.levelWanted == Level.FIRST_PROJECT || it.item.role.levelWanted == Level.BUILDING_REEL })
    }
}
