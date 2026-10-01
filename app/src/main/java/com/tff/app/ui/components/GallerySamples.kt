package com.tff.app.ui.components

import com.tff.app.core.AppClock
import com.tff.app.domain.BannerLevel
import com.tff.app.domain.DeckCard
import com.tff.app.domain.SafetyBannerSpec
import com.tff.app.model.Application
import com.tff.app.model.ApplicantView
import com.tff.app.model.ApplicationOrigin
import com.tff.app.model.AuditionMode
import com.tff.app.model.CompType
import com.tff.app.model.ContentFlag
import com.tff.app.model.DateCertainty
import com.tff.app.model.Format
import com.tff.app.model.GearPolicy
import com.tff.app.model.LinkKind
import com.tff.app.model.Level
import com.tff.app.model.LocationType
import com.tff.app.model.Meals
import com.tff.app.model.MessageFlag
import com.tff.app.model.OwnerSummary
import com.tff.app.model.PaymentTerms
import com.tff.app.model.Perk
import com.tff.app.model.Profile
import com.tff.app.model.ProfileCard
import com.tff.app.model.Project
import com.tff.app.model.ProjectRole
import com.tff.app.model.ProjectStatus
import com.tff.app.model.Ratio
import com.tff.app.model.Reputation
import com.tff.app.model.RoleCode
import com.tff.app.model.RoleWithProject
import com.tff.app.model.ScriptStatus
import com.tff.app.model.Stage
import com.tff.app.model.TeamMember
import com.tff.app.model.Transport
import com.tff.app.model.WorkLink
import java.time.LocalDate

/**
 * Local sample data for [ComponentGallery] only (no Repo, no demo seed). Realistic Calltime cards:
 * a paid on-set role, an unpaid cast role, a remote post role and a stipend role with warnings.
 */
object GallerySamples {
    private val now: Long get() = AppClock.nowMillis()
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    // ── Owners ──
    val rohan = OwnerSummary(
        id = "p_rohan", displayName = "Rohan Kulkarni", level = Level.CREDITED_INDIE, idVerified = true,
        reputation = Reputation(
            profileId = "p_rohan", shootsCompleted = 3, paidOnTime = Ratio(6, 6), footageDelivered = Ratio(5, 5),
            keptBookings = Ratio(6, 6), closesLoop = Ratio(14, 15), replyHoursMedian = 20, confirmedCredits = 2,
        ),
        lastActiveAt = 0, closureRate14d = 0.93,
    )
    val newOwner = OwnerSummary(
        id = "p_aditi", displayName = "Aditi Rao", level = Level.BUILDING_REEL, idVerified = false,
        reputation = Reputation(profileId = "p_aditi"), lastActiveAt = 0,
    )
    val lateOwner = OwnerSummary(
        id = "p_vik", displayName = "Vikram Shah", level = Level.WORKING_PRO, idVerified = true,
        reputation = Reputation(profileId = "p_vik", shootsCompleted = 7, paidOnTime = Ratio(3, 7), footageDelivered = Ratio(6, 7), keptBookings = Ratio(7, 7)),
        lastActiveAt = 0,
    )

    private fun project(
        id: String, owner: OwnerSummary, title: String, logline: String, city: String, area: String,
        format: Format = Format.SHORT, runtime: Int = 12, genres: List<String> = listOf("Drama"), langs: List<String> = listOf("en"),
        dates: List<LocalDate> = emptyList(), lookbook: String? = null, script: ScriptStatus = ScriptStatus.LOCKED,
        flags: Set<ContentFlag> = emptySet(), location: LocationType? = LocationType.OUTDOOR_PUBLIC, meals: Meals? = Meals.TWO_PLUS_MEALS,
        transport: Transport? = Transport.NONE, ta: Int? = null, audition: AuditionMode? = null, zeroBudget: Boolean = false,
        reschedules: Int = 0, lock: LocalDate? = null, stage: Stage = Stage.PRE,
    ) = Project(
        id = id, ownerId = owner.id, payerId = owner.id, title = title, logline = logline, format = format, runtimeMin = runtime,
        genres = genres, languages = langs, stage = stage, scriptStatus = script, lookbookUrl = lookbook, cityId = city, areaId = area,
        shootLocationType = location, shootDates = dates, pictureLockDate = lock,
        dateCertainty = if (dates.isNotEmpty()) DateCertainty.LOCKED else null, hoursCap = if (dates.isNotEmpty()) 10 else null,
        meals = if (dates.isNotEmpty()) meals else null, transport = if (dates.isNotEmpty()) transport else null, travelAllowancePerDay = ta,
        contentFlags = flags, contentNoneConfirmed = flags.isEmpty(), auditionMode = audition,
        bringFriendWelcome = location == LocationType.PRIVATE_HOME, honestZeroBudget = zeroBudget,
        status = ProjectStatus.OPEN, rescheduleCount = reschedules, createdAt = now - 2 * DAY, publishedAt = now - 2 * DAY,
    )

    private val nov14: LocalDate = LocalDate.of(2026, 11, 14)

    val filterCoffee = project(
        "pj_fc", rohan, "Filter Coffee, 4 AM",
        "A night-shift barista and a lost autorickshaw driver keep each other awake until the first tram of the morning.",
        "blr", "blr_jayanagar", genres = listOf("Drama"), langs = listOf("kn", "en"), dates = listOf(nov14, nov14.plusDays(1)),
    )
    val monsoon = project(
        "pj_mr", newOwner, "Monsoon Rerun",
        "Two flatmates relive the same rain-soaked Sunday until one of them finally says what she came to say.",
        "mum", "mum_versova", genres = listOf("Comedy"), langs = listOf("hi", "en"), dates = listOf(LocalDate.of(2026, 11, 21)),
        lookbook = "https://www.youtube.com/watch?v=aqz-KE-bpKQ", script = ScriptStatus.DRAFT,
        flags = setOf(ContentFlag.NIGHT_SHOOT), location = LocationType.PRIVATE_HOME, meals = Meals.ONE_MEAL, audition = AuditionMode.SELF_TAPE,
    )
    val lastFerry = project(
        "pj_lf", lateOwner, "The Last Ferry",
        "A retired boatman ferries one final passenger across the backwaters and learns who she really is.",
        "koc", "koc_fortkochi", runtime = 18, genres = listOf("Thriller"), langs = listOf("ml", "en"),
        lock = LocalDate.of(2027, 1, 10), stage = Stage.POST, location = null,
    )
    val chaiBreak = project(
        "pj_cb", newOwner, "Chai Break",
        "A 48-hour challenge short about a tea stall that only opens for people who are about to make a big mistake.",
        "mum", "mum_andheri", format = Format.CHALLENGE_48H, runtime = 6, genres = listOf("Fantasy"), langs = listOf("hi"),
        dates = listOf(LocalDate.of(2026, 10, 24)), zeroBudget = true, reschedules = 2, transport = Transport.MILEAGE_OR_TA_PAID, ta = 300,
        flags = setOf(ContentFlag.STUNTS_FIGHTS, ContentFlag.WATER),
    )

    private val team = listOf(
        TeamMember("p_rohan", "Rohan K.", "RK", RoleCode.DIRECTOR, isOwner = true),
        TeamMember("p_kabir", "Kabir S.", "KS", RoleCode.DP),
        TeamMember("p_meera", "Meera K.", "MK", RoleCode.ACTOR),
    )

    val soundMixer = RoleWithProject(
        role = ProjectRole(
            id = "r_fc_sound", projectId = "pj_fc", roleCode = RoleCode.SOUND_MIXER, creditTitle = "Sound Recordist",
            compType = CompType.PAID_DAY, amountMin = 2500, amountMax = 2500, currency = "INR", kitFeeDay = 1000,
            paymentTerms = PaymentTerms.ON_WRAP, gearPolicy = GearPolicy.BYO_WITH_KIT_FEE, applicationCap = 15, applicationsCount = 6,
        ),
        project = filterCoffee, owner = rohan, cityName = "Bengaluru", areaName = "Jayanagar", areaLat = 12.93, areaLng = 77.58,
        team = team, paidRoles = 4, totalRoles = 5,
    )
    val leadActor = RoleWithProject(
        role = ProjectRole(
            id = "r_mr_actor", projectId = "pj_mr", roleCode = RoleCode.ACTOR, creditTitle = "Cast", count = 1,
            compType = CompType.UNPAID, currency = "INR", perks = setOf(Perk.FOOTAGE_BY_DATE, Perk.STEP_UP_CREDIT), footageDueWeeks = 6,
            stepUpFriendly = true, levelWanted = Level.BUILDING_REEL, applicationCap = 20, applicationsCount = 11,
        ),
        project = monsoon, owner = newOwner, cityName = "Mumbai", areaName = "Versova", areaLat = 19.13, areaLng = 72.82,
        team = listOf(TeamMember("p_aditi", "Aditi R.", "AR", RoleCode.DIRECTOR, isOwner = true)), paidRoles = 0, totalRoles = 3,
    )
    val composer = RoleWithProject(
        role = ProjectRole(
            id = "r_lf_comp", projectId = "pj_lf", roleCode = RoleCode.COMPOSER, creditTitle = "Music by",
            compType = CompType.PAID_FLAT, amountMin = 12000, amountMax = 12000, currency = "INR", paymentTerms = PaymentTerms.WITHIN_7_DAYS,
            remoteOk = true, postDeadline = LocalDate.of(2027, 2, 7), applicationCap = 12, applicationsCount = 4,
        ),
        project = lastFerry, owner = lateOwner, cityName = "Kochi", areaName = "Fort Kochi", areaLat = 9.96, areaLng = 76.24,
        team = listOf(TeamMember("p_vik", "Vikram S.", "VS", RoleCode.PRODUCER, isOwner = true), TeamMember("p_e", "Esha N.", "EN", RoleCode.EDITOR)),
        paidRoles = 2, totalRoles = 2,
    )
    val gaffer = RoleWithProject(
        role = ProjectRole(
            id = "r_cb_gaffer", projectId = "pj_cb", roleCode = RoleCode.GAFFER, creditTitle = "Gaffer",
            compType = CompType.STIPEND, amountMin = 800, amountMax = 800, currency = "INR", paymentTerms = PaymentTerms.ON_WRAP,
            gearPolicy = GearPolicy.BYO_NO_FEE, applicationCap = 25, applicationsCount = 3,
        ),
        project = chaiBreak, owner = newOwner, cityName = "Mumbai", areaName = "Andheri West", areaLat = 19.13, areaLng = 72.83,
        paidRoles = 1, totalRoles = 4,
    )

    val deck: List<DeckCard> = listOf(
        DeckCard(soundMixer, score = 86.0, fit = null, distanceKm = 4.2),
        DeckCard(leadActor, score = 74.0, fit = null, distanceKm = 2.6),
        DeckCard(composer, score = 70.0, fit = null, distanceKm = null),
        DeckCard(gaffer, score = 52.0, fit = null, distanceKm = 18.4, outsideRadius = true),
    )

    /** A longer deck for swipe testing: the four cards twice, with distinct keys. */
    val longDeck: List<DeckCard> = deck + deck.map { d ->
        d.copy(item = d.item.copy(role = d.item.role.copy(id = d.item.role.id + "_2")))
    }

    // ── People ──
    private fun profile(
        id: String, name: String, role: RoleCode, level: Level, city: String, area: String,
        stepUp: List<RoleCode> = emptyList(), kit: Int? = null, gear: List<String> = emptyList(), reel: String? = null, reelTitle: String? = null,
        email: Boolean = true, phone: Boolean = false, idv: Boolean = false, remote: Boolean = false, activeHoursAgo: Long = 2,
    ) = Profile(
        id = id, displayName = name, primaryRole = role, level = level, cityId = city, areaId = area, stepUpRoles = stepUp,
        kitFeeDay = kit, gear = gear, remoteOk = remote, emailVerified = email, phoneVerified = phone, idVerified = idv,
        workLinks = reel?.let { listOf(WorkLink("wl_$id", it, LinkKind.REEL, role, reelTitle)) } ?: emptyList(),
        lastActiveAt = now - activeHoursAgo * HOUR, createdAt = now - 90 * DAY,
    )

    val dana = ApplicantView(
        application = Application(
            id = "a_dana", roleId = "r_fc_sound", applicantId = "p_dana",
            note = "Nine years on indie sets. I bring my own mixer, two lavs and a boom op if you need one.",
            screeningAnswer = "Two speaking actors max per scene, mostly INT. That's fine on one boom.",
            kitOffered = true, createdAt = now - 3 * HOUR,
        ),
        card = ProfileCard(
            profile = profile("p_dana", "Dana Reyes", RoleCode.SOUND_MIXER, Level.WORKING_PRO, "blr", "blr_jayanagar",
                kit = 1500, gear = listOf("Sound Devices MixPre-6", "2 × Sanken lavs"), phone = true, idv = true),
            reputation = Reputation("p_dana", shootsCompleted = 9, showedUp = Ratio(9, 9)),
            distanceKm = 6.1, areaName = "Jayanagar", cityName = "Bengaluru",
        ),
        score = 91.0, reasons = listOf("Showed up 9/9", "Has boom op", "Speaks Kannada"), rateFit = 1.0, availDays = 2, totalDays = 2,
    )
    val meera = ApplicantView(
        application = Application(
            id = "a_meera", roleId = "r_mr_actor", applicantId = "p_meera",
            note = "Four years of Prithvi theatre. Comedy is home for me.", createdAt = now - 5 * HOUR,
        ),
        card = ProfileCard(
            profile = profile("p_meera", "Meera Kapoor", RoleCode.ACTOR, Level.BUILDING_REEL, "mum", "mum_versova", activeHoursAgo = 30),
            reputation = Reputation("p_meera"), distanceKm = 1.2, areaName = "Versova", cityName = "Mumbai",
        ),
        score = 78.0, reasons = listOf("Speaks Hindi", "Theatre 4 yrs", "New on Calltime"), rateFit = 1.0, availDays = 1, totalDays = 1,
    )
    val kabirScout = ApplicantView(
        application = null,
        card = ProfileCard(
            profile = profile("p_kabir", "Kabir Sethi", RoleCode.GAFFER, Level.CREDITED_INDIE, "mum", "mum_andheri",
                stepUp = listOf(RoleCode.DP), gear = listOf("Aputure 600d", "2 × Nova P300c"), reel = "https://youtu.be/aqz-KE-bpKQ",
                reelTitle = "Gaffer reel 2026", phone = true),
            reputation = Reputation("p_kabir", shootsCompleted = 30, showedUp = Ratio(28, 30)),
            distanceKm = 3.4, areaName = "Andheri West", cityName = "Mumbai",
        ),
        score = 84.0, reasons = listOf("Showed up 28/30", "Has kit", "Step-up to DP"), rateFit = 0.7, availDays = 1, totalDays = 1,
    )
    val invited = meera.copy(application = meera.application!!.copy(id = "a_meera_inv", origin = ApplicationOrigin.INVITED, note = null, createdAt = now - 2 * DAY))

    val people: List<ApplicantView> = listOf(dana, kabirScout, meera)

    // ── Safety banners (as MessageSafety.bannersFor would return them) ──
    val bannerMoney = SafetyBannerSpec(MessageFlag.MONEY_FROM_TALENT, BannerLevel.DANGER, "Calltime never allows charging cast or crew. This is a common scam.", offerReport = true, offerBlock = true)
    val bannerMeeting = SafetyBannerSpec(MessageFlag.PRIVATE_MEETING, BannerLevel.WARN, "Normal auditions are self-tape, video call, or a public or studio space with others present.", offerReport = false, offerBlock = false)
    val bannerOffPlatform = SafetyBannerSpec(MessageFlag.OFF_PLATFORM, BannerLevel.INFO, "Keeping talks on Calltime until you're booked protects you. Here's why.", offerReport = false, offerBlock = false)

    const val TIP_CAST = "What normal looks like: auditions are self-tape, video, or a public or studio space with others present, during the day. No fees, ever. No nudity at auditions."
}
