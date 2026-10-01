package com.tff.app.data.demo

import com.tff.app.core.Geo
import com.tff.app.domain.BookingDiff
import com.tff.app.domain.BookingFact
import com.tff.app.domain.ReputationCalc
import com.tff.app.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Lookup tables over one immutable [DemoState] (built lazily, cached per state instance) plus the
 * derived facts every screen needs: relationship checks (PRD §9.3), sanitised profile cards,
 * reputation from booking facts (ReputationCalc), owner summaries and Role cards.
 */
class DemoIndex(val s: DemoState, val now: Long, val zone: ZoneId) {
    val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    val rolesByProject: Map<String, List<ProjectRole>> = s.roles.values.groupBy { it.projectId }
    val appsByRole: Map<String, List<Application>> = s.applications.values.groupBy { it.roleId }
    val appsByApplicant: Map<String, List<Application>> = s.applications.values.groupBy { it.applicantId }
    val matchesByProject: Map<String, List<Match>> = s.matches.values.groupBy { it.projectId }
    val matchesByUser: Map<String, List<Match>> = buildMap<String, MutableList<Match>> {
        s.matches.values.forEach { m ->
            getOrPut(m.ownerId) { mutableListOf() } += m
            if (m.collaboratorId != m.ownerId) getOrPut(m.collaboratorId) { mutableListOf() } += m
        }
    }
    val messagesByMatch: Map<String, List<Message>> = s.messages.groupBy { it.matchId }
    val cardsByMatch: Map<String, List<BookingCard>> = s.bookingCards.values.groupBy { it.matchId }.mapValues { (_, v) -> v.sortedBy { it.version } }
    val itemsByMatch: Map<String, List<WrapItem>> = s.wrapItems.values.groupBy { it.matchId }
    val daysByProject: Map<String, List<ShootDay>> = s.shootDays.values.groupBy { it.projectId }.mapValues { (_, v) -> v.sortedBy { it.day } }
    val callsByDay: Map<String, List<CallTime>> = s.callTimes.groupBy { it.shootDayId }
    val feedbackByMatch: Map<String, List<Feedback>> = s.feedback.groupBy { it.matchId }
    val projectsByOwner: Map<String, List<Project>> = s.projects.values.groupBy { it.ownerId }
    private val blockPairs: Set<Pair<String, String>> = s.blocks.map { it.blockerId to it.blockedId }.toSet()

    /** ≥3 independent reports → auto-hidden from decks pending review (PRD §5.5). */
    val hidden: Set<String> = s.reports.filter { it.subjectProfileId != null }
        .groupBy { it.subjectProfileId!! }
        .filterValues { rows -> rows.map { it.reporterId }.distinct().size >= 3 }
        .keys

    fun blocked(a: String?, b: String?): Boolean = a != null && b != null && ((a to b) in blockPairs || (b to a) in blockPairs)

    fun profile(id: String): Profile? = s.profiles[id]
    fun area(id: String): Area? = DemoCities.area(id)
    fun city(id: String): City? = DemoCities.city(id)

    fun latestCard(matchId: String): BookingCard? = cardsByMatch[matchId]?.lastOrNull()
    fun acceptedCard(matchId: String): BookingCard? = cardsByMatch[matchId]?.lastOrNull { it.status == BookingStatus.ACCEPTED }
        ?: cardsByMatch[matchId]?.lastOrNull { it.status == BookingStatus.SUPERSEDED && it.respondedAt != null }

    fun isBookedOn(projectId: String, userId: String?): Boolean = userId != null &&
        matchesByProject[projectId].orEmpty().any { it.collaboratorId == userId && it.state in BOOKED_LIKE }

    // ───────────── Relationship (PRD §9.3) ─────────────

    fun hasRelationship(viewer: String?, target: String): Boolean {
        if (viewer == null) return false
        if (viewer == target) return true
        if (blocked(viewer, target)) return false
        val mine = matchesByUser[viewer].orEmpty()
        if (mine.any { it.ownerId == target || it.collaboratorId == target }) return true
        // teammates on a project
        val myProjects = mine.filter { it.state in BOOKED_LIKE }.map { it.projectId }.toSet() + projectsByOwner[viewer].orEmpty().map { it.id }
        val theirProjects = matchesByUser[target].orEmpty().filter { it.state in BOOKED_LIKE && it.collaboratorId == target }.map { it.projectId }.toSet() +
            projectsByOwner[target].orEmpty().filter { p -> matchesByProject[p.id].orEmpty().any { it.collaboratorId == viewer && it.state in BOOKED_LIKE } }.map { it.id }
        if (myProjects.intersect(theirProjects).isNotEmpty()) return true
        // the person applied to / was asked for one of my roles, or I applied to theirs
        val myRoleIds = projectsByOwner[viewer].orEmpty().flatMap { rolesByProject[it.id].orEmpty() }.map { it.id }.toSet()
        if (appsByApplicant[target].orEmpty().any { it.roleId in myRoleIds }) return true
        val theirRoleIds = projectsByOwner[target].orEmpty().flatMap { rolesByProject[it.id].orEmpty() }.map { it.id }.toSet()
        if (appsByApplicant[viewer].orEmpty().any { it.roleId in theirRoleIds }) return true
        // posters are public: the person owns an OPEN project
        if (projectsByOwner[target].orEmpty().any { it.status == ProjectStatus.OPEN }) return true
        // Scout: I own an OPEN crew role this person could be asked onto
        val t = profile(target) ?: return false
        if (Taxonomy.isCast(t.primaryRole) && t.secondaryRoles.none { !Taxonomy.isCast(it.code) }) return false
        val tCity = city(t.cityId)
        return projectsByOwner[viewer].orEmpty().filter { it.status == ProjectStatus.OPEN }.any { p ->
            rolesByProject[p.id].orEmpty().any { r ->
                r.status == RoleStatus.OPEN && !r.isCast && roleMatches(t, r) &&
                    (t.cityId == p.cityId || (r.remoteOk && t.remoteOk && tCity?.country == city(p.cityId)?.country))
            }
        }
    }

    fun roleMatches(p: Profile, r: ProjectRole): Boolean =
        p.primaryRole == r.roleCode || p.secondaryRoles.any { it.code == r.roleCode } || (r.stepUpFriendly && r.roleCode in p.stepUpRoles)

    // ───────────── Reputation (facts → ReputationCalc) ─────────────

    private val repCache = ConcurrentHashMap<String, Reputation>()
    private val creditsCache = ConcurrentHashMap<String, List<ConfirmedCredit>>()

    fun reputation(id: String): Reputation = repCache.getOrPut(id) { computeReputation(id) }

    fun bookingFacts(id: String): List<BookingFact> = matchesByUser[id].orEmpty().filter { it.bookedAt != null }.map { m ->
        val cards = cardsByMatch[m.id].orEmpty()
        val accepted = acceptedCard(m.id)?.terms ?: cards.firstOrNull()?.terms
        val dayIds = daysByProject[m.projectId].orEmpty().map { it.id }.toSet()
        val attendance = s.callTimes.filter { it.shootDayId in dayIds && it.profileId == m.collaboratorId }.mapNotNull { it.attendance }
        BookingFact(
            match = m,
            terms = accepted,
            attendance = attendance,
            wrapItems = itemsByMatch[m.id].orEmpty(),
            cutLate = cutLate(m, cards),
            disputed = Attendance.DISPUTED in attendance,
        )
    }

    /** A version that cut days or pay, sent within 72h of day 1 / the delivery date (PRD F14). */
    private fun cutLate(m: Match, cards: List<BookingCard>): Boolean {
        if (cards.size < 2) return false
        var prevAccepted: BookingCard? = null
        for (c in cards) {
            val prev = prevAccepted
            if (prev != null && c.version > prev.version) {
                val first = if (prev.terms.isPostRole) prev.terms.deliveryDate else prev.terms.dates.minOrNull()
                val firstMillis = first?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                val cut = runCatching { BookingDiff.isCut(prev.terms, c.terms) }.getOrElse { (c.terms.amount ?: 0) < (prev.terms.amount ?: 0) || c.terms.dates.size < prev.terms.dates.size }
                if (cut && firstMillis != null && firstMillis - c.sentAt < 72 * SeedKit.HOUR) return true
            }
            if (c.status == BookingStatus.ACCEPTED || (c.status == BookingStatus.SUPERSEDED && c.respondedAt != null)) prevAccepted = c
        }
        return false
    }

    fun ownerApplications(ownerId: String): List<Application> {
        val roleIds = projectsByOwner[ownerId].orEmpty().flatMap { rolesByProject[it.id].orEmpty() }.map { it.id }.toSet()
        return s.applications.values.filter { it.roleId in roleIds && it.origin == ApplicationOrigin.APPLIED }
    }

    /** Owner "closes the loop": share of applications given a status within 14 days (only ones old enough to count). */
    fun closesLoop(ownerId: String): Ratio? {
        val apps = ownerApplications(ownerId)
        val window = 14 * SeedKit.DAY
        val eligible = apps.filter { it.decidedAt != null || now - it.createdAt >= window }
        if (eligible.isEmpty()) return null
        val closed = eligible.count { a -> a.decidedAt != null && a.decidedAt - a.createdAt <= window && a.status != ApplicationStatus.EXPIRED }
        return Ratio(closed, eligible.size)
    }

    fun replyHoursMedian(ownerId: String): Int? {
        val hours = ownerApplications(ownerId).mapNotNull { a -> a.decidedAt?.let { ((it - a.createdAt) / SeedKit.HOUR).toInt() } }
            .filter { it >= 0 }.sorted()
        if (hours.isEmpty()) return null
        return hours[hours.size / 2]
    }

    fun confirmedCredits(id: String): List<ConfirmedCredit> = creditsCache.getOrPut(id) {
        matchesByUser[id].orEmpty().filter { it.collaboratorId == id }.mapNotNull { m ->
            val credit = itemsByMatch[m.id].orEmpty().firstOrNull { it.kind == WrapKind.CREDIT && it.status in DONE_LIKE } ?: return@mapNotNull null
            val p = s.projects[m.projectId] ?: return@mapNotNull null
            val r = s.roles[m.roleId] ?: return@mapNotNull null
            val year = (p.shootEnd ?: r.postDeadline ?: today).year
            if (credit.status in DONE_LIKE) ConfirmedCredit(p.title, r.roleCode, r.creditTitle, year) else null
        }
    }

    private fun computeReputation(id: String): Reputation {
        val facts = bookingFacts(id)
        val credits = confirmedCredits(id).size
        val owns = projectsByOwner[id].orEmpty().isNotEmpty()
        val closes = if (owns) closesLoop(id) else null
        val reply = if (owns) replyHoursMedian(id) else null
        return runCatching { ReputationCalc.forProfile(id, facts, closes, reply, credits, today) }
            .getOrElse { Reputation(profileId = id, confirmedCredits = credits, closesLoop = closes, replyHoursMedian = reply) }
    }

    fun ownerSummary(ownerId: String): OwnerSummary {
        val p = profile(ownerId)
        val closes = closesLoop(ownerId)
        return OwnerSummary(
            id = ownerId,
            displayName = p?.displayName ?: "Deleted account",
            level = p?.level ?: Level.FIRST_PROJECT,
            idVerified = p?.idVerified ?: false,
            reputation = reputation(ownerId),
            lastActiveAt = p?.lastActiveAt ?: 0,
            closureRate14d = closes?.takeIf { it.den >= 5 }?.value,
        )
    }

    // ───────────── Cards ─────────────

    /**
     * What [viewer] may see of [target]: contentWontDo emptied, HEADSHOT links only when allowed
     * (owner of a cast role the person applied to, or a match counterpart). Never the private rate.
     */
    fun card(target: Profile, viewer: String?, headshot: Boolean = false, from: Pair<Double, Double>? = null): ProfileCard {
        val own = viewer == target.id
        val shown = if (own) target else target.copy(
            contentWontDo = emptyList(),
            workLinks = target.workLinks.filter { it.kind != LinkKind.HEADSHOT || headshot },
        )
        val a = area(target.areaId)
        val c = city(target.cityId)
        val dist = if (from != null && a != null) Geo.haversineKm(from.first, from.second, a.lat, a.lng) else null
        return ProfileCard(shown, reputation(target.id), confirmedCredits(target.id), dist, a?.name, c?.name)
    }

    fun viewerPoint(viewer: String?): Pair<Double, Double>? = viewer?.let { profile(it) }?.let { area(it.areaId) }?.let { it.lat to it.lng }

    fun headshotAllowed(viewer: String?, target: String): Boolean {
        if (viewer == null) return false
        if (viewer == target) return true
        // counterpart of a match (owner sees their cast's headshot; the reverse never matters for crew)
        val castRoleIdsOfViewer = projectsByOwner[viewer].orEmpty().flatMap { rolesByProject[it.id].orEmpty() }.filter { it.isCast }.map { it.id }.toSet()
        return appsByApplicant[target].orEmpty().any { it.roleId in castRoleIdsOfViewer && it.origin == ApplicationOrigin.APPLIED } ||
            matchesByUser[viewer].orEmpty().any { it.ownerId == viewer && it.collaboratorId == target && s.roles[it.roleId]?.isCast == true }
    }

    fun teamOf(p: Project): List<TeamMember> {
        val owner = profile(p.ownerId)
        val out = mutableListOf<TeamMember>()
        if (owner != null) out += TeamMember(owner.id, owner.shortName, owner.initials, owner.primaryRole, isOwner = true)
        matchesByProject[p.id].orEmpty().filter { it.state in BOOKED_LIKE }.forEach { m ->
            val u = profile(m.collaboratorId) ?: return@forEach
            val r = s.roles[m.roleId] ?: return@forEach
            out += TeamMember(u.id, u.shortName, u.initials, r.roleCode)
        }
        return out
    }

    /** Role card data. The exact address is only included for the owner and booked people. */
    fun roleWithProject(r: ProjectRole, viewer: String?): RoleWithProject? {
        val p = s.projects[r.projectId] ?: return null
        val a = area(p.areaId) ?: return null
        val c = city(p.cityId) ?: return null
        val canSeeAddress = viewer != null && (viewer == p.ownerId || isBookedOn(p.id, viewer))
        val roles = rolesByProject[p.id].orEmpty()
        return RoleWithProject(
            role = r,
            project = if (canSeeAddress) p else p.copy(exactAddress = null),
            owner = ownerSummary(p.ownerId),
            cityName = c.name,
            areaName = a.name,
            areaLat = a.lat,
            areaLng = a.lng,
            team = teamOf(p),
            paidRoles = roles.count { it.isPaid },
            totalRoles = roles.size,
        )
    }

    fun firstName(id: String?): String = DemoText.firstName(id?.let { profile(it) })
    fun shortName(id: String?): String = id?.let { profile(it)?.shortName } ?: "Someone"

    companion object {
        val BOOKED_LIKE = setOf(MatchState.BOOKED, MatchState.WRAPPED)
        val DONE_LIKE = setOf(WrapStatus.DONE, WrapStatus.CONFIRMED)
        val ACTIVE = setOf(MatchState.TALKING, MatchState.OFFERED, MatchState.BOOKED)
    }
}
