package com.tff.app.data.remote

import com.tff.app.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

/** DTO ↔ model mapping for the Supabase wire format (see Dtos.kt). Pure functions. */
internal object Mappers {

    fun isUuid(s: String?): Boolean = s != null && runCatching { UUID.fromString(s) }.isSuccess && s.length == 36

    // ── reference ──
    fun AreaDto.toModel() = Area(id, cityId, name, lat, lng)
    fun CityDto.toModel() = City(id, name, country, currency, lat, lng, paidFloorDay, emergencyNumber, isLive, isEarly, areas.map { it.toModel() })
    fun DensityDto.toModel() = Density(
        cityId, cityName, isLive, isEarly, people, openProjects,
        perRole.mapNotNull { (k, v) -> runCatching { RoleCode.valueOf(k) }.getOrNull()?.let { it to v } }.toMap(),
    )

    // ── people ──
    fun WorkLinkDto.toModel() = WorkLink(id, url, kind, roleCode, title, year)
    fun WorkLink.toDto() = WorkLinkDto(if (isUuid(id)) id else "", url, kind, roleCode, title, year)

    fun ProfileDto.toModel() = Profile(
        id = id, displayName = displayName, pronouns = pronouns, primaryRole = primaryRole,
        secondaryRoles = secondaryRoles.map { SecondaryRole(it.code, it.proficiency) }, stepUpRoles = stepUpRoles,
        level = level, cityId = cityId, areaId = areaId, travelRadiusKm = travelRadiusKm, travelIfCovered = travelIfCovered,
        remoteOk = remoteOk, languages = languages, availabilityPattern = availabilityPattern, busyDates = busyDates.toSet(),
        compMode = compMode, kitFeeDay = kitFeeDay, unpaidMustHaves = unpaidMustHaves, goals = goals, contentWontDo = contentWontDo,
        workLinks = workLinks.map { it.toModel() }, gear = gear, tools = tools, tasteFilms = tasteFilms, genresPref = genresPref,
        formatsPref = formatsPref, bio = bio, affiliations = affiliations, emailVerified = emailVerified, phoneVerified = phoneVerified,
        idVerified = idVerified, adultConfirmed = adultConfirmed, createdAt = createdAt, lastActiveAt = lastActiveAt,
    )

    /** For save_profile. Verified flags are sent but the server ignores them (trigger). */
    fun Profile.toDto() = ProfileDto(
        id = id, displayName = displayName, pronouns = pronouns, primaryRole = primaryRole,
        secondaryRoles = secondaryRoles.map { SecondaryRoleDto(it.code, it.proficiency) }, stepUpRoles = stepUpRoles,
        level = level, cityId = cityId, areaId = areaId, travelRadiusKm = travelRadiusKm, travelIfCovered = travelIfCovered,
        remoteOk = remoteOk, languages = languages, availabilityPattern = availabilityPattern, busyDates = busyDates.sorted(),
        compMode = compMode, kitFeeDay = kitFeeDay, unpaidMustHaves = unpaidMustHaves, goals = goals, contentWontDo = contentWontDo,
        workLinks = workLinks.map { it.toDto() }, gear = gear, tools = tools, tasteFilms = tasteFilms, genresPref = genresPref,
        formatsPref = formatsPref, bio = bio, affiliations = affiliations, emailVerified = emailVerified, phoneVerified = phoneVerified,
        idVerified = idVerified, adultConfirmed = adultConfirmed, createdAt = createdAt, lastActiveAt = lastActiveAt,
    )

    fun RatioDto.toModel() = Ratio(num, den)

    fun ReputationDto.toModel() = Reputation(
        profileId = profileId, shootsCompleted = shootsCompleted, showedUp = showedUp?.toModel(),
        deliveredOnTime = deliveredOnTime?.toModel(), paidOnTime = paidOnTime?.toModel(), footageDelivered = footageDelivered?.toModel(),
        keptBookings = keptBookings?.toModel(), closesLoop = closesLoop?.toModel(), replyHoursMedian = replyHoursMedian,
        confirmedCredits = confirmedCredits,
    )

    /** ProfileCard of someone else: contentWontDo always emptied (the server already blanks it). */
    fun CardDto.toModel() = ProfileCard(
        profile = profile.toModel().copy(contentWontDo = emptyList()),
        reputation = reputation.toModel(),
        confirmedCredits = confirmedCredits.map { ConfirmedCredit(it.projectTitle, it.roleCode, it.creditTitle, it.year) },
        distanceKm = distanceKm, areaName = areaName, cityName = cityName,
    )

    fun OwnerDto.toModel() = OwnerSummary(id, displayName, level, idVerified, reputation.toModel(), lastActiveAt, closureRate14d)

    // ── projects ──
    fun ProjectDto.toModel() = Project(
        id = id, ownerId = ownerId, payerId = payerId, title = title, logline = logline, format = format, runtimeMin = runtimeMin,
        pageCount = pageCount, genres = genres, languages = languages, stage = stage, scriptStatus = scriptStatus,
        scriptReadable = scriptReadable, lookbookUrl = lookbookUrl, ambition = ambitionTier, honestZeroBudget = honestZeroBudget,
        cityId = cityId, areaId = areaId, shootLocationType = shootLocationType, exactAddress = exactAddress,
        storySetting = storySetting, shootDates = shootDates.sorted(), pictureLockDate = pictureLockDate, dateCertainty = dateCertainty,
        hoursCap = hoursCap, meals = meals, transport = transport, travelAllowancePerDay = travelAllowancePerDay, lodging = lodging,
        contentFlags = contentFlags.toSet(), contentNoneConfirmed = contentNoneConfirmed, intimacyCoordinator = intimacyCoordinator,
        stuntOrArmourer = stuntOrArmourer, guardianPresent = guardianPresent, safetyMeasures = safetyMeasures,
        auditionMode = auditionMode, bringFriendWelcome = bringFriendWelcome, insurance = insurance, permits = permits,
        status = status, rescheduleCount = rescheduleCount, createdAt = createdAt, publishedAt = publishedAt, updatedAt = updatedAt,
    )

    fun Project.toDto() = ProjectDto(
        id = if (isUuid(id)) id else "", ownerId = ownerId, payerId = payerId, title = title, logline = logline, format = format,
        runtimeMin = runtimeMin, pageCount = pageCount, genres = genres, languages = languages, stage = stage,
        scriptStatus = scriptStatus, scriptReadable = scriptReadable, lookbookUrl = lookbookUrl, ambitionTier = ambition,
        honestZeroBudget = honestZeroBudget, cityId = cityId, areaId = areaId, shootLocationType = shootLocationType,
        exactAddress = exactAddress, storySetting = storySetting, shootDates = shootDates.sorted(), pictureLockDate = pictureLockDate,
        dateCertainty = dateCertainty, hoursCap = hoursCap, meals = meals, transport = transport,
        travelAllowancePerDay = travelAllowancePerDay, lodging = lodging, contentFlags = contentFlags.sortedBy { it.ordinal },
        contentNoneConfirmed = contentNoneConfirmed, intimacyCoordinator = intimacyCoordinator, stuntOrArmourer = stuntOrArmourer,
        guardianPresent = guardianPresent, safetyMeasures = safetyMeasures, auditionMode = auditionMode,
        bringFriendWelcome = bringFriendWelcome, insurance = insurance, permits = permits, status = status,
        rescheduleCount = rescheduleCount, createdAt = createdAt, publishedAt = publishedAt, updatedAt = updatedAt,
    )

    private fun JsonElement.asText(): String? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> contentOrNull
        else -> toString()
    }

    fun RoleDto.toModel() = ProjectRole(
        id = id, projectId = projectId, roleCode = roleCode, count = count, creditTitle = creditTitle,
        scopeIncludes = scopeIncludes, scopeExcludes = scopeExcludes, stepUpFriendly = stepUpFriendly, levelWanted = levelWanted,
        compType = compType, amountMin = amountMin, amountMax = amountMax, currency = currency, kitFeeDay = kitFeeDay,
        paymentTerms = paymentTerms, perks = perks.toSet(), footageDueWeeks = footageDueWeeks, finalFilmDueWeeks = finalFilmDueWeeks,
        gearPolicy = gearPolicy, remoteOk = remoteOk, postDeadline = postDeadline,
        roleDetails = roleDetails.mapNotNull { (k, v) -> v.asText()?.let { k to it } }.toMap(),
        screeningQuestion = screeningQuestion, applicationCap = applicationCap, applicationsCount = applicationsCount,
        filledCount = filledCount, status = status,
    )

    fun ProjectRole.toDto() = RoleDto(
        id = if (isUuid(id)) id else "", projectId = projectId, roleCode = roleCode, count = count, creditTitle = creditTitle,
        scopeIncludes = scopeIncludes, scopeExcludes = scopeExcludes, stepUpFriendly = stepUpFriendly, levelWanted = levelWanted,
        compType = compType, amountMin = amountMin, amountMax = amountMax, currency = currency, kitFeeDay = kitFeeDay,
        paymentTerms = paymentTerms, perks = perks.sortedBy { it.ordinal }, footageDueWeeks = footageDueWeeks,
        finalFilmDueWeeks = finalFilmDueWeeks, gearPolicy = gearPolicy, remoteOk = remoteOk, postDeadline = postDeadline,
        roleDetails = JsonObject(roleDetails.mapValues { JsonPrimitive(it.value) }), screeningQuestion = screeningQuestion,
        applicationCap = applicationCap, applicationsCount = applicationsCount, filledCount = filledCount, status = status,
    )

    fun TeamDto.toModel() = TeamMember(profileId, shortName, initials, roleCode, isOwner)

    fun RwpDto.toModel() = RoleWithProject(
        role = role.toModel(), project = project.toModel(), owner = owner.toModel(), cityName = cityName, areaName = areaName,
        areaLat = areaLat, areaLng = areaLng, team = team.map { it.toModel() }, paidRoles = paidRoles, totalRoles = totalRoles,
    )

    fun DraftDto.toModel() = ProjectDraft(project.toModel(), roles.map { it.toModel() })

    // ── matching ──
    fun ApplicationDto.toModel() = Application(
        id = id, roleId = roleId, applicantId = applicantId, origin = origin, note = note, workLinkId = workLinkId,
        screeningAnswer = screeningAnswer, kitOffered = kitOffered, status = status, backupRank = backupRank,
        declineReason = declineReason, createdAt = createdAt, decidedAt = decidedAt,
    )

    fun MatchDto.toModel() = Match(
        id = id, applicationId = applicationId, projectId = projectId, roleId = roleId, ownerId = ownerId,
        collaboratorId = collaboratorId, state = state, isPostRole = isPostRole, changePending = changePending,
        contactsSharedByOwner = contactsSharedByOwner, contactsSharedByCollab = contactsSharedByCollab,
        withdrawReason = withdrawReason, noticeHours = noticeHours, releasedByOwner = releasedByOwner,
        createdAt = createdAt, bookedAt = bookedAt, endedAt = endedAt,
    )

    // ── messaging & booking ──
    fun AuditionRequestDto.toModel() = AuditionRequest(mode, sidesUrl, dueDate, date, time, meetingLink, venue, address, alsoPresent, note)
    fun AuditionRequest.toDto() = AuditionRequestDto(mode, sidesUrl, dueDate, date, time, meetingLink, venue, address, alsoPresent, note)

    fun MessageDto.toModel(json: Json): Message = Message(
        id = id, matchId = matchId, senderId = senderId, kind = kind, body = body,
        audition = payload?.takeIf { kind == MessageKind.AUDITION && it !is JsonNull }?.let { pl ->
            runCatching { json.decodeFromJsonElement(AuditionPayloadDto.serializer(), pl) }.getOrNull()
                ?.let { AuditionPayload(it.request.toModel(), it.response, it.tapeUrl) }
        },
        bookingCardId = bookingCardId, flags = flags.toSet(), safetyExample = safetyExample, createdAt = createdAt,
    )

    fun BookingTermsDto.toModel() = BookingTerms(
        roleCode = roleCode, creditTitle = creditTitle, projectTitle = projectTitle, isPostRole = isPostRole, dates = dates.sorted(),
        deliveryDate = deliveryDate, pictureLockDate = pictureLockDate, revisionRounds = revisionRounds, callWindow = callWindow,
        compType = compType, amount = amount, currency = currency, kitFeeDay = kitFeeDay, paymentTerms = paymentTerms,
        payByDate = payByDate, perks = perks.toSet(), meals = meals, transport = transport, travelAllowancePerDay = travelAllowancePerDay,
        footageDueWeeks = footageDueWeeks, finalFilmDueWeeks = finalFilmDueWeeks,
        deliverables = deliverables.map { Deliverable(it.kind, it.dueDate, it.owedBy) }, footageDefinition = footageDefinition,
        rightsNote = rightsNote, notes = notes, payerId = payerId, payerName = payerName, noticePledge = noticePledge,
    )

    fun BookingTerms.toDto() = BookingTermsDto(
        roleCode = roleCode, creditTitle = creditTitle, projectTitle = projectTitle, isPostRole = isPostRole, dates = dates.sorted(),
        deliveryDate = deliveryDate, pictureLockDate = pictureLockDate, revisionRounds = revisionRounds, callWindow = callWindow,
        compType = compType, amount = amount, currency = currency, kitFeeDay = kitFeeDay, paymentTerms = paymentTerms,
        payByDate = payByDate, perks = perks.sortedBy { it.ordinal }, meals = meals, transport = transport,
        travelAllowancePerDay = travelAllowancePerDay, footageDueWeeks = footageDueWeeks, finalFilmDueWeeks = finalFilmDueWeeks,
        deliverables = deliverables.map { DeliverableDto(it.kind, it.dueDate, it.owedBy) }, footageDefinition = footageDefinition,
        rightsNote = rightsNote, notes = notes, payerId = payerId, payerName = payerName, noticePledge = noticePledge,
    )

    fun BookingCardDto.toModel() = BookingCard(
        id = id, matchId = matchId, version = version, terms = terms.toModel(), status = status, sentBy = sentBy ?: "",
        sentAt = sentAt, respondedAt = respondedAt, changedFields = changedFields.toSet(),
    )

    fun WrapItemDto.toModel() = WrapItem(id, matchId, kind, dueDate, owedBy, status, link, note, markedAt, confirmedAt, autoConfirmed)

    // ── inbox, safety, system ──
    fun NotificationDto.toModel() = AppNotification(id, kind, title, body, deepLink, createdAt, readAt)

    fun ConversationDto.toModel() = ConversationRow(
        match = match.toModel(), counterpartName = counterpartName, counterpartInitials = counterpartInitials, roleCode = roleCode,
        projectTitle = projectTitle, isOwner = isOwner, lastMessage = lastMessage, lastAt = lastAt, unread = unread,
    )

    fun InboxDto.toModel() = Inbox(
        asks = asks.map { AskView(it.application.toModel(), it.item.toModel(), it.isRebook) },
        conversations = conversations.map { it.toModel() },
        updates = updates.map { it.toModel() },
    )

    fun ReportDto.toModel() = Report(id, targetKind, targetId, reason, details, status, createdAt)

    // ── L1 ──
    fun ShootDayDto.toModel() = ShootDay(id, projectId, day, generalCall, wrapBy, address, mapsUrl, parkingNote, hospitalNote,
                                         mealsPlan, notes, version, publishedAt, cancelledAt)
    fun CallTimeDto.toModel() = CallTime(shootDayId, profileId, callTime, ackAt, attendance)
    fun ShootDayFullDto.toModel() = ShootDayFull(day.toModel(), dayIndex, dayCount,
                                                 calls.map { CallTimeView(it.call.toModel(), it.shortName, it.roleCode) }, myCall?.toModel())
    fun ShootDayDraft.toDto() = ShootDayDraftDto(projectId, shootDayId?.takeIf { isUuid(it) }, day, generalCall, wrapBy, address,
                                                 parkingNote, hospitalNote, mealsPlan, notes, perPersonCall.filterKeys { isUuid(it) })
    fun WrapGroupDto.toModel() = WrapGroup(match.toModel(), person.toModel(), roleCode, items.map { it.toModel() })
    fun FeedbackAnswers.toDto() = FeedbackAnswersDto(paid, delivered, feltSafe, kept, showedUp, prepared, deliveredOnTime, revisions, workAgain)
    fun FeedbackStateDto.toModel() = FeedbackState(unlocked, iSubmitted, otherSubmitted, revealed)

    fun ProjectSummaryDto.toModel() = ProjectSummary(
        project = project.toModel(), roles = roles.map { it.toModel() }, filledSlots = filledSlots, totalSlots = totalSlots,
        nextShootDate = nextShootDate, overdueWrapItems = overdueWrapItems, newApplicants = newApplicants,
        myRoleCode = myRoleCode, myMatchId = myMatchId, myNextCall = myNextCall, myDeliveryDate = myDeliveryDate,
    )
}
