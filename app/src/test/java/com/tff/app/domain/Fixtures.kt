package com.tff.app.domain

import com.tff.app.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Test-only builders. Defaults describe Rohan's "Filter Coffee, 4 AM" in Jayanagar, Bengaluru (PRD §11.4). */
object Fx {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 2)                 // a Friday
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    val NOW: Long = TODAY.atTime(10, 0).atZone(ZONE).toInstant().toEpochMilli()
    const val DAY_MS = 24L * 3600 * 1000

    val SAT_14_NOV: LocalDate = LocalDate.of(2026, 11, 14)
    val SUN_15_NOV: LocalDate = LocalDate.of(2026, 11, 15)

    // Area centroids (approximate, PRD §11.2)
    const val JAYANAGAR_LAT = 12.9250; const val JAYANAGAR_LNG = 77.5938
    const val VERSOVA_LAT = 19.1310; const val VERSOVA_LNG = 72.8150
    const val ANDHERI_LAT = 19.1180; const val ANDHERI_LNG = 72.8420   // ≈3 km from Versova

    fun city(
        id: String = "blr", name: String = "Bengaluru", currency: String = "INR", paidFloorDay: Int = 1000,
    ) = City(id = id, name = name, country = "IN", currency = currency, lat = 12.97, lng = 77.59,
        paidFloorDay = paidFloorDay, emergencyNumber = "112", isLive = true)

    fun project(
        id: String = "p_fc",
        ownerId: String = "u_rohan",
        title: String = "Filter Coffee, 4 AM",
        logline: String = "A night-shift coder and a darshini owner keep the same strange hour, until one morning only one of them shows up.",
        format: Format = Format.SHORT,
        runtimeMin: Int = 12,
        genres: List<String> = listOf("Drama"),
        languages: List<String> = listOf("kn", "en"),
        stage: Stage = Stage.PRE,
        scriptStatus: ScriptStatus = ScriptStatus.LOCKED,
        ambition: Ambition = Ambition.FESTIVAL,
        cityId: String = "blr",
        areaId: String = "blr_jayanagar",
        shootLocationType: LocationType? = LocationType.MIXED,
        shootDates: List<LocalDate> = listOf(SAT_14_NOV, SUN_15_NOV),
        pictureLockDate: LocalDate? = null,
        dateCertainty: DateCertainty? = DateCertainty.LOCKED,
        hoursCap: Int? = 10,
        meals: Meals? = Meals.TWO_PLUS_MEALS,
        transport: Transport? = Transport.MILEAGE_OR_TA_PAID,
        travelAllowancePerDay: Int? = 300,
        lodging: Boolean? = false,
        contentFlags: Set<ContentFlag> = emptySet(),
        contentNoneConfirmed: Boolean = true,
        intimacyCoordinator: Boolean? = null,
        stuntOrArmourer: Boolean? = null,
        guardianPresent: Boolean? = null,
        safetyMeasures: String? = null,
        auditionMode: AuditionMode? = AuditionMode.SELF_TAPE,
        bringFriendWelcome: Boolean = true,
        status: ProjectStatus = ProjectStatus.OPEN,
        publishedAt: Long? = NOW - 5 * DAY_MS,
        rescheduleCount: Int = 0,
    ) = Project(
        id = id, ownerId = ownerId, payerId = ownerId, title = title, logline = logline, format = format,
        runtimeMin = runtimeMin, genres = genres, languages = languages, stage = stage, scriptStatus = scriptStatus,
        ambition = ambition, cityId = cityId, areaId = areaId, shootLocationType = shootLocationType,
        shootDates = shootDates, pictureLockDate = pictureLockDate, dateCertainty = dateCertainty, hoursCap = hoursCap,
        meals = meals, transport = transport, travelAllowancePerDay = travelAllowancePerDay, lodging = lodging,
        contentFlags = contentFlags, contentNoneConfirmed = contentNoneConfirmed,
        intimacyCoordinator = intimacyCoordinator, stuntOrArmourer = stuntOrArmourer, guardianPresent = guardianPresent,
        safetyMeasures = safetyMeasures, auditionMode = auditionMode, bringFriendWelcome = bringFriendWelcome,
        status = status, publishedAt = publishedAt, rescheduleCount = rescheduleCount,
    )

    /** A post-only project (stage POST, no shoot dates). */
    fun postProject(
        id: String = "p_soft",
        title: String = "Soft Light",
        pictureLockDate: LocalDate? = LocalDate.of(2027, 1, 10),
        languages: List<String> = listOf("en"),
    ) = project(
        id = id, title = title, stage = Stage.POST, shootDates = emptyList(), pictureLockDate = pictureLockDate,
        dateCertainty = null, hoursCap = null, meals = null, transport = null, travelAllowancePerDay = null,
        lodging = null, shootLocationType = null, auditionMode = null, languages = languages,
    )

    fun role(
        id: String = "r_sound",
        projectId: String = "p_fc",
        roleCode: RoleCode = RoleCode.SOUND_MIXER,
        count: Int = 1,
        creditTitle: String = Taxonomy.info(roleCode).defaultCredit,
        compType: CompType = CompType.PAID_DAY,
        amountMin: Int? = if (compType == CompType.UNPAID) null else 2500,
        amountMax: Int? = amountMin,
        currency: String = "INR",
        kitFeeDay: Int? = null,
        paymentTerms: PaymentTerms? = if (compType == CompType.UNPAID) null else PaymentTerms.ON_WRAP,
        perks: Set<Perk> = emptySet(),
        footageDueWeeks: Int? = null,
        finalFilmDueWeeks: Int? = null,
        gearPolicy: GearPolicy? = if (Taxonomy.dept(roleCode) in Taxonomy.GEAR_DEPTS) GearPolicy.BYO_WITH_KIT_FEE else null,
        remoteOk: Boolean = false,
        postDeadline: LocalDate? = null,
        stepUpFriendly: Boolean = false,
        levelWanted: Level? = null,
        scopeIncludes: List<String> = emptyList(),
        screeningQuestion: String? = null,
        roleDetails: Map<String, String> = emptyMap(),
        applicationCap: Int = 15,
        applicationsCount: Int = 6,
        status: RoleStatus = RoleStatus.OPEN,
    ) = ProjectRole(
        id = id, projectId = projectId, roleCode = roleCode, count = count, creditTitle = creditTitle,
        scopeIncludes = scopeIncludes, stepUpFriendly = stepUpFriendly, levelWanted = levelWanted, compType = compType,
        amountMin = amountMin, amountMax = amountMax, currency = currency, kitFeeDay = kitFeeDay,
        paymentTerms = paymentTerms, perks = perks, footageDueWeeks = footageDueWeeks,
        finalFilmDueWeeks = finalFilmDueWeeks, gearPolicy = gearPolicy, remoteOk = remoteOk,
        postDeadline = postDeadline, roleDetails = roleDetails, screeningQuestion = screeningQuestion,
        applicationCap = applicationCap, applicationsCount = applicationsCount, status = status,
    )

    fun reputation(
        id: String = "u_rohan",
        shoots: Int = 3,
        paidOnTime: Ratio? = null,
        footage: Ratio? = null,
        kept: Ratio? = null,
        showedUp: Ratio? = null,
        deliveredOnTime: Ratio? = null,
        replyHours: Int? = 20,
        confirmedCredits: Int = 0,
        closesLoop: Ratio? = null,
    ) = Reputation(
        profileId = id, shootsCompleted = shoots, showedUp = showedUp, deliveredOnTime = deliveredOnTime,
        paidOnTime = paidOnTime, footageDelivered = footage, keptBookings = kept, closesLoop = closesLoop,
        replyHoursMedian = replyHours, confirmedCredits = confirmedCredits,
    )

    fun owner(
        id: String = "u_rohan",
        name: String = "Rohan Kulkarni",
        level: Level = Level.CREDITED_INDIE,
        idVerified: Boolean = false,
        reputation: Reputation = reputation(id = id),
        lastActiveAt: Long = NOW - 3600_000,
        closureRate14d: Double? = null,
    ) = OwnerSummary(id = id, displayName = name, level = level, idVerified = idVerified,
        reputation = reputation, lastActiveAt = lastActiveAt, closureRate14d = closureRate14d)

    fun item(
        role: ProjectRole = role(),
        project: Project = project(id = role.projectId),
        owner: OwnerSummary = owner(id = project.ownerId),
        cityName: String = "Bengaluru",
        areaName: String = "Jayanagar",
        lat: Double = JAYANAGAR_LAT,
        lng: Double = JAYANAGAR_LNG,
        team: List<TeamMember> = emptyList(),
        paidRoles: Int = 0,
        totalRoles: Int = 1,
    ) = RoleWithProject(role = role, project = project, owner = owner, cityName = cityName, areaName = areaName,
        areaLat = lat, areaLng = lng, team = team, paidRoles = paidRoles, totalRoles = totalRoles)

    fun profile(
        id: String = "u_meera",
        name: String = "Meera Kapoor",
        primaryRole: RoleCode = RoleCode.ACTOR,
        secondaryRoles: List<SecondaryRole> = emptyList(),
        stepUpRoles: List<RoleCode> = emptyList(),
        level: Level = Level.BUILDING_REEL,
        cityId: String = "mum",
        areaId: String = "mum_versova",
        radius: Int = 15,
        travelIfCovered: Boolean = false,
        remoteOk: Boolean = false,
        languages: List<String> = listOf("hi", "en", "mr"),
        availability: AvailabilityPattern = AvailabilityPattern.ANY_DAY,
        busyDates: Set<LocalDate> = emptySet(),
        compMode: CompMode = CompMode.OPEN_TO_UNPAID,
        unpaidMustHaves: List<Perk> = emptyList(),
        goals: List<Goal> = emptyList(),
        contentWontDo: List<ContentFlag> = emptyList(),
        workLinks: List<WorkLink> = emptyList(),
        gear: List<String> = emptyList(),
        genresPref: List<String> = emptyList(),
        formatsPref: List<Format> = emptyList(),
        idVerified: Boolean = false,
        phoneVerified: Boolean = false,
        kitFeeDay: Int? = null,
    ) = Profile(
        id = id, displayName = name, primaryRole = primaryRole, secondaryRoles = secondaryRoles,
        stepUpRoles = stepUpRoles, level = level, cityId = cityId, areaId = areaId, travelRadiusKm = radius,
        travelIfCovered = travelIfCovered, remoteOk = remoteOk, languages = languages,
        availabilityPattern = availability, busyDates = busyDates, compMode = compMode,
        unpaidMustHaves = unpaidMustHaves, goals = goals, contentWontDo = contentWontDo, workLinks = workLinks,
        gear = gear, genresPref = genresPref, formatsPref = formatsPref, idVerified = idVerified,
        phoneVerified = phoneVerified, kitFeeDay = kitFeeDay,
    )

    fun viewer(profile: Profile = profile(), minDayRate: Int? = null, lat: Double = VERSOVA_LAT, lng: Double = VERSOVA_LNG) =
        Viewer(profile, minDayRate, lat, lng)

    fun shootDay(
        id: String = "sd_1",
        projectId: String = "p_fc",
        day: LocalDate = SAT_14_NOV,
        address: String = "12, 4th Cross, Jayanagar 4th Block, Bengaluru 560011",
        mapsUrl: String = "",
        parking: String? = "Street parking on 4th Cross",
        hospital: String? = "Sagar Hospital, Jayanagar (2 km)",
        meals: String? = "breakfast + lunch",
        notes: String? = null,
    ) = ShootDay(id = id, projectId = projectId, day = day, generalCall = LocalTime.of(7, 0), wrapBy = LocalTime.of(17, 0),
        address = address, mapsUrl = mapsUrl, parkingNote = parking, hospitalNote = hospital, mealsPlan = meals, notes = notes)

    fun match(
        id: String = "m_1",
        ownerId: String = "u_rohan",
        collaboratorId: String = "u_meera",
        state: MatchState = MatchState.WRAPPED,
        isPostRole: Boolean = false,
        noticeHours: Int? = null,
        releasedByOwner: Boolean = false,
    ) = Match(id = id, applicationId = "a_$id", projectId = "p_fc", roleId = "r_sound", ownerId = ownerId,
        collaboratorId = collaboratorId, state = state, isPostRole = isPostRole, noticeHours = noticeHours,
        releasedByOwner = releasedByOwner, bookedAt = NOW - 30 * DAY_MS)

    fun reel(id: String = "l_1", role: RoleCode? = null, kind: LinkKind = LinkKind.REEL) =
        WorkLink(id = id, url = "https://youtube.com/watch?v=$id", kind = kind, roleCode = role)
}
