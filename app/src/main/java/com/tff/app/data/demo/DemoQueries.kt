package com.tff.app.data.demo

import java.util.concurrent.ConcurrentHashMap

import com.tff.app.core.Dates
import com.tff.app.core.Geo
import com.tff.app.domain.ApplicantInput
import com.tff.app.domain.ApplicantRanker
import com.tff.app.domain.Gate
import com.tff.app.domain.MatchEngine
import com.tff.app.domain.MessageSafety
import com.tff.app.domain.Viewer
import com.tff.app.domain.WrapItemsFactory
import com.tff.app.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Read models for every Repo query, computed from one DemoState snapshot so Flows can simply map the
 * state. Every function takes the viewer ([actor]) and enforces what they may see (PRD §9.3).
 */
class DemoQueries(private val engine: DemoEngine) {

    private fun ix(s: DemoState) = engine.index(s)

    // ── Results memo: the heavier read models (deck candidates, inbox, notifications) are computed once per world and
    // shared by the startup warm-up, the screens and every collector of the same flow. A world that differs only in
    // its analytics events (a "log app_open") counts as the same; results older than 30 s are recomputed. ──

    private class Box(val v: Any?)
    private class Memo(val s: DemoState, val bucket: Long, val map: ConcurrentHashMap<String, Box> = ConcurrentHashMap())
    @Volatile private var memo: Memo? = null

    @Suppress("UNCHECKED_CAST")
    private fun <T> memo(s: DemoState, key: String, compute: () -> T): T {
        val bucket = engine.nowOf(s) / 30_000
        var m = memo
        if (m == null || m.bucket != bucket || !sameExceptEvents(m.s, s)) {
            if (m != null && m.s !== s && m.s.seq > s.seq) return compute()     // an older world: don't evict the newer memo
            m = Memo(s, bucket).also { memo = it }
        }
        m.map[key]?.let { return it.v as T }
        return compute().also { m.map[key] = Box(it) }
    }

    /** Every field but [DemoState.events] is the same object (DemoState is immutable, so identity means unchanged). */
    private fun sameExceptEvents(a: DemoState, b: DemoState): Boolean = a === b || (
        DemoIndex.sameTables(a, b) && a.swipes === b.swipes && a.notifications === b.notifications && a.readMarks === b.readMarks &&
            a.flags === b.flags && a.session == b.session && a.privateRates === b.privateRates && a.realUsers === b.realUsers &&
            a.offsetMillis == b.offsetMillis && a.anchor == b.anchor && a.seq == b.seq && a.speed == b.speed && a.emails === b.emails)

    /**
     * The viewer's own profile, with the dates they're booked on other shoots added to busyDates (DemoIndex.bookedDates),
     * so the Gigs deck built from it (MatchEngine availability, G3) treats booked days as busy. DemoEngine.saveProfile
     * strips those derived dates again, so editing the profile never turns a booking into a manual busy date.
     */
    fun me(s: DemoState, actor: String?): Profile? = actor?.let { s.profiles[it] }?.let { ix(s).withBookedDates(it) }

    fun myPrivateMinRate(s: DemoState, actor: String?): Int? = actor?.let { s.privateRates[it] }

    /** Null when no relationship exists (PRD §9.3) or either side blocked the other. */
    fun profileCard(s: DemoState, actor: String?, id: String): ProfileCard? {
        val ix = ix(s)
        val target = ix.profile(id) ?: return null
        if (!ix.hasRelationship(actor, id)) return null
        return ix.card(target, actor, headshot = ix.headshotAllowed(actor, id), from = ix.viewerPoint(actor))
    }

    fun cities(): List<City> = DemoCities.all.sortedWith(compareBy<City>({ !it.isLive }, { it.isEarly }))

    fun density(s: DemoState, cityId: String): Density {
        val ix = ix(s)
        val c = DemoCities.city(cityId) ?: DemoCities.city("mumbai")!!
        val people = s.profiles.values.filter { it.cityId == c.id && it.id !in ix.hidden }
        return Density(
            cityId = c.id, cityName = c.name, isLive = c.isLive, isEarly = c.isEarly,
            people = people.size,
            openProjects = s.projects.values.count { it.cityId == c.id && it.status == ProjectStatus.OPEN && it.ownerId !in ix.hidden },
            perRole = people.groupingBy { it.primaryRole }.eachCount(),
        )
    }

    // ───────────── Gigs ─────────────

    /**
     * Pre-gate candidates: OPEN roles in the browsed city (or within travel reach), plus remote roles in the viewer's
     * OWN country (guests: the browsed city's country), so a Mumbai actor never gets an LA remote role and Dana never
     * gets a ₹ one. Never offered again: roles the viewer swiped or applied to, every role on a project where they
     * still have a live match or application (DemoIndex.liveProjectIds: Meera, booked on Cutting Chai, never sees
     * Cutting Chai's other roles), and on-set roles that clash with dates they're booked elsewhere (cast: any shoot
     * day booked; crew: every shoot day booked). MatchEngine also sees the booked dates as busy via [me].
     */
    fun discoverCandidates(s: DemoState, actor: String?, cityId: String?): List<RoleWithProject> =
        memo(s, "dc|$actor|$cityId") { discoverCandidates0(s, actor, cityId) }

    private fun discoverCandidates0(s: DemoState, actor: String?, cityId: String?): List<RoleWithProject> {
        val ix = ix(s)
        val me = actor?.let { ix.profile(it) }
        val cid = cityId ?: me?.cityId ?: "mumbai"
        val city = DemoCities.city(cid) ?: DemoCities.city("mumbai")!!
        val homeCountry = me?.let { DemoCities.city(it.cityId)?.country } ?: city.country
        val point = me?.let { ix.area(it.areaId) }?.let { it.lat to it.lng } ?: (city.lat to city.lng)
        val reach = (me?.travelRadiusKm ?: 15) * 4.5
        val swiped = actor?.let { a -> s.swipes.filter { it.actorId == a && it.targetKind == "ROLE" }.map { it.targetId }.toSet() }.orEmpty()
        val applied = actor?.let { ix.appsByApplicant[it].orEmpty().map { a -> a.roleId }.toSet() }.orEmpty()
        val live = ix.liveProjectIds(actor)
        val booked = ix.bookedDates(actor)
        return s.roles.values.asSequence()
            .filter { it.status == RoleStatus.OPEN && it.id !in swiped && it.id !in applied }
            .mapNotNull { r -> s.projects[r.projectId]?.let { r to it } }
            .filter { (r, p) ->
                val open = p.status == ProjectStatus.OPEN || (r.isPostRole && p.status in setOf(ProjectStatus.CREWED, ProjectStatus.WRAPPED))
                open && p.ownerId != actor && p.id !in live && !ix.blocked(actor, p.ownerId) && p.ownerId !in ix.hidden &&
                    (r.isPostRole || p.shootStart?.let { !it.isBefore(ix.today) } ?: true) &&
                    !clashesWithBooked(r, p, booked) && run {
                    val pCity = DemoCities.city(p.cityId)
                    if (r.remoteOk) pCity?.country == homeCountry
                    else p.cityId == city.id ||
                        ix.area(p.areaId)?.let { a -> Geo.haversineKm(point.first, point.second, a.lat, a.lng) <= reach } == true
                }
            }
            .mapNotNull { (r, _) -> ix.roleWithProject(r, actor) }
            .sortedByDescending { it.project.publishedAt ?: 0 }
            .toList()
    }

    /** An on-set role the viewer can't do because they're booked elsewhere: cast needs every day, crew at least one. */
    private fun clashesWithBooked(r: ProjectRole, p: Project, booked: Set<java.time.LocalDate>): Boolean {
        if (booked.isEmpty() || r.isPostRole || (r.remoteOk && r.postDeadline != null)) return false
        val days = p.shootDates.distinct()
        if (days.isEmpty()) return false
        return if (r.isCast) days.any { it in booked } else days.all { it in booked }
    }

    fun roleWithProject(s: DemoState, actor: String?, roleId: String): RoleWithProject? {
        val ix = ix(s)
        val r = s.roles[roleId] ?: return null
        val p = s.projects[r.projectId] ?: return null
        if (p.status == ProjectStatus.DRAFT && p.ownerId != actor) return null
        if (ix.blocked(actor, p.ownerId)) return null
        return ix.roleWithProject(r, actor)
    }

    fun likesLeftToday(s: DemoState, actor: String?): Int {
        actor ?: return DemoEngine.DAILY_LIKES
        val today = ix(s).today
        val used = s.applications.values.count { it.applicantId == actor && it.origin == ApplicationOrigin.APPLIED && engine.dateOf(it.createdAt) == today }
        return (DemoEngine.DAILY_LIKES - used).coerceAtLeast(0)
    }

    fun saved(s: DemoState, actor: String?): List<RoleWithProject> {
        actor ?: return emptyList()
        val ix = ix(s)
        val applied = ix.appsByApplicant[actor].orEmpty().map { it.roleId }.toSet()
        return s.swipes.filter { it.actorId == actor && it.targetKind == "ROLE" && it.dir == SwipeDir.SAVE && it.targetId !in applied }
            .sortedByDescending { it.at }.distinctBy { it.targetId }
            .mapNotNull { sw -> s.roles[sw.targetId]?.let { r -> s.projects[r.projectId]?.takeIf { !ix.blocked(actor, it.ownerId) }?.let { ix.roleWithProject(r, actor) } } }
    }

    fun myApplications(s: DemoState, actor: String?): List<Application> =
        actor?.let { ix(s).appsByApplicant[it].orEmpty().sortedByDescending { a -> a.createdAt } }.orEmpty()

    // ───────────── Projects ─────────────

    private fun overdue(item: WrapItem, today: java.time.LocalDate): Boolean = runCatching { WrapItemsFactory.isOverdue(item, today) }
        .getOrElse { item.status in setOf(WrapStatus.OPEN, WrapStatus.NOT_RECEIVED) && item.dueDate?.isBefore(today) == true }

    private fun summary(ix: DemoIndex, p: Project, actor: String?): ProjectSummary {
        val roles = ix.rolesByProject[p.id].orEmpty().sortedBy { it.id }
        val live = roles.filter { it.status != RoleStatus.CLOSED }
        val matches = ix.matchesByProject[p.id].orEmpty()
        val items = matches.flatMap { ix.itemsByMatch[it.id].orEmpty() }
        val roleIds = roles.map { it.id }.toSet()
        val mine = matches.filter { it.collaboratorId == actor && it.state in DemoIndex.BOOKED_LIKE }.maxByOrNull { it.createdAt }
        val myDay = mine?.takeIf { !it.isPostRole }?.let { m ->
            ix.daysByProject[p.id].orEmpty().firstOrNull { it.cancelledAt == null && !it.day.isBefore(ix.today) }?.let { d ->
                val call = ix.callsByDay[d.id].orEmpty().firstOrNull { it.profileId == m.collaboratorId }?.callTime
                Dates.shortUpper(d.day) + (call?.let { " · " + Dates.time(it) } ?: "")
            } ?: p.shootDates.firstOrNull { !it.isBefore(ix.today) }?.let { Dates.shortUpper(it) }
        }
        val canSeeAddress = actor == p.ownerId || mine != null
        return ProjectSummary(
            project = if (canSeeAddress) p else p.copy(exactAddress = null),
            roles = roles,
            filledSlots = live.sumOf { minOf(it.filledCount, it.count) },
            totalSlots = live.sumOf { it.count },
            nextShootDate = p.shootDates.firstOrNull { !it.isBefore(ix.today) },
            overdueWrapItems = items.count { overdue(it, ix.today) },
            newApplicants = ix.s.applications.values.count { it.roleId in roleIds && it.status == ApplicationStatus.PENDING && it.origin == ApplicationOrigin.APPLIED },
            myRoleCode = mine?.let { ix.s.roles[it.roleId]?.roleCode },
            myMatchId = mine?.id,
            myNextCall = myDay,
            myDeliveryDate = mine?.takeIf { it.isPostRole }?.let { ix.acceptedCard(it.id)?.terms?.deliveryDate },
        )
    }

    fun myProjects(s: DemoState, actor: String?): List<ProjectSummary> {
        actor ?: return emptyList()
        val ix = ix(s)
        val order = mapOf(ProjectStatus.DRAFT to 0, ProjectStatus.OPEN to 1, ProjectStatus.CREWED to 1, ProjectStatus.WRAPPED to 2, ProjectStatus.CANCELLED to 3)
        return ix.projectsByOwner[actor].orEmpty()
            .sortedWith(compareBy<Project>({ order[it.status] ?: 9 }, { it.shootStart ?: java.time.LocalDate.MAX }).thenByDescending { it.updatedAt })
            .map { summary(ix, it, actor) }
    }

    fun joinedProjects(s: DemoState, actor: String?): List<ProjectSummary> {
        actor ?: return emptyList()
        val ix = ix(s)
        return ix.matchesByUser[actor].orEmpty().filter { it.collaboratorId == actor && it.state in DemoIndex.BOOKED_LIKE }
            .mapNotNull { s.projects[it.projectId] }.distinctBy { it.id }
            .sortedWith(compareBy<Project>({ it.status == ProjectStatus.WRAPPED }, { it.shootStart ?: java.time.LocalDate.MAX }))
            .map { summary(ix, it, actor) }
    }

    fun projectFull(s: DemoState, actor: String?, id: String): ProjectFull? {
        val ix = ix(s)
        val p = s.projects[id] ?: return null
        val isOwner = actor == p.ownerId
        if (p.status == ProjectStatus.DRAFT && !isOwner) return null
        if (ix.blocked(actor, p.ownerId)) return null
        val myMatch = ix.matchesByProject[id].orEmpty().filter { it.collaboratorId == actor }.maxByOrNull { it.createdAt }
        val member = isOwner || ix.isBookedOn(id, actor)
        val roles = ix.rolesByProject[id].orEmpty().sortedBy { it.id }
        val nextDay = ix.daysByProject[id].orEmpty().firstOrNull { it.cancelledAt == null && !it.day.isBefore(ix.today) }
        val team = if (!member) emptyList() else ix.matchesByProject[id].orEmpty().filter { it.state in DemoIndex.BOOKED_LIKE && !ix.blocked(actor, it.collaboratorId) }
            .mapNotNull { m ->
                val u = ix.profile(m.collaboratorId) ?: return@mapNotNull null
                val r = s.roles[m.roleId] ?: return@mapNotNull null
                val items = ix.itemsByMatch[m.id].orEmpty()
                TeamEntry(
                    match = m,
                    person = ix.card(u, actor, headshot = isOwner && r.isCast, from = ix.area(p.areaId)?.let { it.lat to it.lng }),
                    roleCode = r.roleCode,
                    nextAck = if (m.isPostRole || nextDay == null) null else ix.callsByDay[nextDay.id].orEmpty().firstOrNull { it.profileId == m.collaboratorId }?.let { it.ackAt != null },
                    wrapDone = items.count { it.status == WrapStatus.CONFIRMED },
                    wrapTotal = items.size,
                )
            }
        val backups = if (!isOwner) emptyMap() else roles.associate { r ->
            val item = ix.roleWithProject(r, actor)
            val apps = ix.appsByRole[r.id].orEmpty().filter { it.status == ApplicationStatus.BACKUP && !ix.blocked(actor, it.applicantId) }
                .sortedBy { it.backupRank ?: Int.MAX_VALUE }
            r.id to if (item == null) emptyList() else apps.mapNotNull { input(ix, item, it, it.applicantId) }.map { view(item, it) }
        }.filterValues { it.isNotEmpty() }
        val a = ix.area(p.areaId)
        return ProjectFull(
            project = if (member) p else p.copy(exactAddress = null),
            roles = roles,
            owner = ix.ownerSummary(p.ownerId),
            isOwner = isOwner,
            team = team,
            backups = backups,
            myMatch = myMatch,
            paidRoles = roles.count { it.isPaid },
            totalRoles = roles.size,
            cityName = DemoCities.city(p.cityId)?.name ?: "",
            areaName = a?.name ?: "",
        )
    }

    fun draft(s: DemoState, actor: String?, projectId: String): ProjectDraft? {
        val p = s.projects[projectId] ?: return null
        if (p.ownerId != actor) return null
        return ProjectDraft(p, s.roles.values.filter { it.projectId == projectId }.sortedBy { it.id })
    }

    // ───────────── Crew up ─────────────

    private fun input(ix: DemoIndex, item: RoleWithProject, a: Application?, pid: String): ApplicantInput? {
        val prof = ix.gateProfile(pid) ?: return null     // booked dates count as busy for avail
        val headshotOk = item.role.isCast && a != null && a.origin == ApplicationOrigin.APPLIED
        val card = ix.card(prof, item.project.ownerId, headshot = headshotOk, from = item.areaLat to item.areaLng)
        val rate = ix.s.privateRates[pid]
        val fit = runCatching { ApplicantRanker.rateFit(item.role, item.project, rate) }.getOrElse {
            if (rate == null || item.role.compType == CompType.UNPAID) 1.0 else minOf(1.0, (item.role.amountMax ?: 0).toDouble() / rate)
        }
        val others = ix.appsByApplicant[pid].orEmpty().filter { it.id != a?.id }.mapNotNull { it.screeningAnswer }
        val headshot = if (headshotOk) prof.workLinks.firstOrNull { it.kind == LinkKind.HEADSHOT }?.url else null
        return ApplicantInput(a, card, fit, others, headshot)
    }

    private fun view(item: RoleWithProject, i: ApplicantInput) =
        ApplicantView(i.application, i.card, 0.0, emptyList(), i.rateFit, 0, item.project.shootDays, i.headshotUrl)

    private fun rank(ix: DemoIndex, item: RoleWithProject, inputs: List<ApplicantInput>): List<ApplicantView> {
        val ownerLevel = ix.profile(item.project.ownerId)?.level ?: Level.CREDITED_INDIE
        return runCatching { ApplicantRanker.rank(item, inputs, ownerLevel, ix.today) }
            .getOrElse { inputs.sortedBy { it.application?.createdAt ?: 0 }.map { view(item, it) } }
    }

    fun applicants(s: DemoState, actor: String?, roleId: String): List<ApplicantView> {
        val ix = ix(s)
        val r = s.roles[roleId] ?: return emptyList()
        val p = s.projects[r.projectId] ?: return emptyList()
        if (p.ownerId != actor) return emptyList()
        val item = ix.roleWithProject(r, actor) ?: return emptyList()
        val apps = ix.appsByRole[roleId].orEmpty().filter {
            it.status == ApplicationStatus.PENDING && it.origin == ApplicationOrigin.APPLIED && !ix.blocked(actor, it.applicantId) && it.applicantId !in ix.hidden
        }
        return rank(ix, item, apps.mapNotNull { input(ix, item, it, it.applicantId) })
    }

    /** Crew roles only; reverse gates (G1–G7 without G6, plus askBlockReason) for people who haven't applied. */
    fun scout(s: DemoState, actor: String?, roleId: String): List<ApplicantView> {
        val ix = ix(s)
        val r = s.roles[roleId] ?: return emptyList()
        val p = s.projects[r.projectId] ?: return emptyList()
        if (p.ownerId != actor || r.isCast || r.status != RoleStatus.OPEN) return emptyList()
        val item = ix.roleWithProject(r, actor) ?: return emptyList()
        val touched = ix.appsByRole[roleId].orEmpty().map { it.applicantId }.toSet()
        val passed = s.swipes.filter { it.actorId == actor && it.targetKind == "PROFILE" && it.contextRoleId == roleId }.map { it.targetId }.toSet()
        val askedRecently = s.applications.values.filter { a ->
            a.origin == ApplicationOrigin.INVITED && ix.now - a.createdAt < 30 * SeedKit.DAY && s.roles[a.roleId]?.projectId?.let { s.projects[it]?.ownerId } == actor
        }.map { it.applicantId }.toSet()
        val booked = ix.matchesByProject[p.id].orEmpty().filter { it.state in DemoIndex.ACTIVE }.map { it.collaboratorId }.toSet()
        val pCountry = DemoCities.city(p.cityId)?.country
        val candidates = s.profiles.values.filter { u ->
            u.id != actor && u.id !in touched && u.id !in passed && u.id !in askedRecently && u.id !in booked && u.id !in ix.hidden &&
                !ix.blocked(actor, u.id) && ix.roleMatches(u, r) &&
                (u.cityId == p.cityId || (r.remoteOk && u.remoteOk && DemoCities.city(u.cityId)?.country == pCountry) ||
                    ix.area(u.areaId)?.let { a -> Geo.haversineKm(a.lat, a.lng, item.areaLat, item.areaLng) <= u.travelRadiusKm * 3.0 } == true)
        }.filter { u ->
            val rate = s.privateRates[u.id]
            val a = ix.area(u.areaId) ?: return@filter false
            val gatesOk = runCatching {
                (MatchEngine.failedGates(Viewer(ix.withBookedDates(u), rate, a.lat, a.lng), item, ix.today) - Gate.G6_STATUS).isEmpty()
            }.getOrElse { true }
            val askOk = runCatching { ApplicantRanker.askBlockReason(item, u, rate, ix.today, isKeepList = false) == null }.getOrElse {
                !(u.compMode == CompMode.PAID_ONLY && !r.isPaid) && u.contentWontDo.none { it in p.contentFlags }
            }
            gatesOk && askOk
        }
        return rank(ix, item, candidates.mapNotNull { input(ix, item, null, it.id) })
    }

    fun asksLeftToday(s: DemoState, projectId: String): Int {
        val ix = ix(s)
        val roleIds = ix.rolesByProject[projectId].orEmpty().map { it.id }.toSet()
        val used = s.applications.values.count { it.roleId in roleIds && it.origin == ApplicationOrigin.INVITED && engine.dateOf(it.createdAt) == ix.today }
        return (DemoEngine.DAILY_ASKS - used).coerceAtLeast(0)
    }

    fun keepList(s: DemoState, actor: String?): List<ProfileCard> {
        actor ?: return emptyList()
        val ix = ix(s)
        return s.feedback.filter { it.fromId == actor && it.answers.workAgain == true }.sortedByDescending { it.submittedAt }
            .map { it.toId }.distinct().filter { !ix.blocked(actor, it) }
            .mapNotNull { ix.profile(it) }.map { ix.card(it, actor, headshot = false, from = ix.viewerPoint(actor)) }
    }

    // ───────────── Inbox & chat ─────────────

    private fun masked(m: Match, msg: Message, viewer: String?): Message {
        if (msg.senderId == null || msg.senderId == viewer || m.state in DemoIndex.BOOKED_LIKE) return msg
        val hasContact = MessageFlag.CONTACT in msg.flags || runCatching { MessageFlag.CONTACT in MessageSafety.analyze(msg.body) }.getOrDefault(false)
        if (!hasContact) return msg
        return msg.copy(body = runCatching { MessageSafety.mask(msg.body) }.getOrElse { fallbackMask(msg.body) })
    }

    private fun fallbackMask(t: String): String = t
        .replace(Regex("(?:\\+?\\d[\\s-]?){10,13}"), "•••• shared contact hidden")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "•••• shared contact hidden")
        .replace(Regex("(?<![\\w@])@[A-Za-z0-9_.]{3,30}\\b"), "•••• shared contact hidden")

    fun inbox(s: DemoState, actor: String?): Inbox = memo(s, "inbox|$actor") { inbox0(s, actor) }

    private fun inbox0(s: DemoState, actor: String?): Inbox {
        actor ?: return Inbox()
        val ix = ix(s)
        val asks = ix.appsByApplicant[actor].orEmpty()
            .filter { it.origin == ApplicationOrigin.INVITED && it.status == ApplicationStatus.PENDING }
            .mapNotNull { a ->
                val r = s.roles[a.roleId] ?: return@mapNotNull null
                val p = s.projects[r.projectId] ?: return@mapNotNull null
                if (r.status != RoleStatus.OPEN || ix.blocked(actor, p.ownerId)) return@mapNotNull null
                val item = ix.roleWithProject(r, actor) ?: return@mapNotNull null
                val rebook = ix.matchesByUser[actor].orEmpty().any { it.ownerId == p.ownerId && it.collaboratorId == actor && it.state == MatchState.WRAPPED }
                AskView(a, item, rebook)
            }.sortedByDescending { it.application.createdAt }
        val convs = ix.matchesByUser[actor].orEmpty().mapNotNull { m ->
            val isOwner = m.ownerId == actor
            val cp = if (isOwner) m.collaboratorId else m.ownerId
            if (ix.blocked(actor, cp)) return@mapNotNull null
            val cpProfile = ix.profile(cp)
            val r = s.roles[m.roleId] ?: return@mapNotNull null
            val p = s.projects[m.projectId] ?: return@mapNotNull null
            val msgs = ix.messagesByMatch[m.id].orEmpty()
            val last = msgs.maxByOrNull { it.createdAt }?.let { masked(m, it, actor) }
            val read = s.readMarks["$actor|${m.id}"] ?: 0L
            ConversationRow(
                match = m,
                counterpartName = cpProfile?.shortName ?: "Deleted account",
                counterpartInitials = cpProfile?.initials ?: "–",
                roleCode = r.roleCode,
                projectTitle = p.title,
                isOwner = isOwner,
                lastMessage = last?.body,
                lastAt = last?.createdAt ?: m.createdAt,
                unread = msgs.count { it.createdAt > read && it.senderId != null && it.senderId != actor },
            )
        }.sortedByDescending { it.lastAt }
        val hide = blockedMentions(ix, actor)
        val updates = s.notifications.filter { it.userId == actor && it.n.kind != NotificationKind.MESSAGE && it.n.kind != NotificationKind.ASK_RECEIVED }
            .map { it.n }.filterNot { hide(it) }.sortedByDescending { it.createdAt }.take(60)
        return Inbox(asks, convs, updates)
    }

    private fun placeholder(id: String, r: ProjectRole, p: Project) =
        Profile(id, "Deleted account", primaryRole = r.roleCode, level = Level.FIRST_PROJECT, cityId = p.cityId, areaId = p.areaId)

    fun matchView(s: DemoState, actor: String?, matchId: String): MatchView? {
        val ix = ix(s)
        val m = s.matches[matchId] ?: return null
        if (actor != m.ownerId && actor != m.collaboratorId) return null
        val r = s.roles[m.roleId] ?: return null
        val p = s.projects[m.projectId] ?: return null
        val item = ix.roleWithProject(r, actor) ?: return null
        val isOwner = actor == m.ownerId
        val cpId = if (isOwner) m.collaboratorId else m.ownerId
        val cp = ix.profile(cpId)
        val card = if (cp == null) ProfileCard(placeholder(cpId, r, p), Reputation(cpId))
        else ix.card(cp, actor, headshot = isOwner && r.isCast, from = item.areaLat to item.areaLng)
        val cards = ix.cardsByMatch[matchId].orEmpty()
        val next = if (m.isPostRole) null else ix.daysByProject[p.id].orEmpty().firstOrNull { it.cancelledAt == null && !it.day.isBefore(ix.today) }
        val myCall = next?.let { d -> if (isOwner) d.generalCall else ix.callsByDay[d.id].orEmpty().firstOrNull { it.profileId == actor }?.callTime }
        return MatchView(m, item, card, isOwner, cards.lastOrNull(), cards.lastOrNull { it.status == BookingStatus.ACCEPTED },
            next?.takeIf { isOwner || ix.callsByDay[it.id].orEmpty().any { c -> c.profileId == actor } }, myCall)
    }

    fun messages(s: DemoState, actor: String?, matchId: String): List<Message> {
        val m = s.matches[matchId] ?: return emptyList()
        if (actor != m.ownerId && actor != m.collaboratorId) return emptyList()
        return ix(s).messagesByMatch[matchId].orEmpty().sortedBy { it.createdAt }.map { masked(m, it, actor) }
    }

    fun bookingCards(s: DemoState, actor: String?, matchId: String): List<BookingCard> {
        val m = s.matches[matchId] ?: return emptyList()
        if (actor != m.ownerId && actor != m.collaboratorId) return emptyList()
        return ix(s).cardsByMatch[matchId].orEmpty()
    }

    // ───────────── Shoot & wrap ─────────────

    fun shootDays(s: DemoState, actor: String?, projectId: String): List<ShootDayFull> {
        val ix = ix(s)
        val p = s.projects[projectId] ?: return emptyList()
        val isOwner = actor == p.ownerId
        if (!isOwner && !ix.isBookedOn(projectId, actor)) return emptyList()
        val days = ix.daysByProject[projectId].orEmpty().filter { it.cancelledAt == null }.sortedBy { it.day }
        val roleOf = ix.matchesByProject[projectId].orEmpty().filter { it.state in DemoIndex.BOOKED_LIKE }
            .associate { it.collaboratorId to (s.roles[it.roleId]?.roleCode ?: RoleCode.PA) }
        return days.mapIndexed { i, d ->
            val calls = ix.callsByDay[d.id].orEmpty().map { c ->
                val prof = ix.profile(c.profileId)
                CallTimeView(c, if (isOwner) prof?.shortName ?: "Someone" else DemoText.firstName(prof), roleOf[c.profileId] ?: prof?.primaryRole ?: RoleCode.PA)
            }
            ShootDayFull(d, i + 1, days.size, calls, calls.firstOrNull { it.call.profileId == actor }?.call)
        }
    }

    private val kindOrder = listOf(WrapKind.DELIVERY, WrapKind.PAID, WrapKind.FOOTAGE, WrapKind.FINAL_FILM, WrapKind.CREDIT)

    fun wrapItems(s: DemoState, actor: String?, matchId: String): List<WrapItem> {
        val m = s.matches[matchId] ?: return emptyList()
        if (actor != m.ownerId && actor != m.collaboratorId) return emptyList()
        return ix(s).itemsByMatch[matchId].orEmpty().sortedBy { kindOrder.indexOf(it.kind) }
    }

    fun projectWrap(s: DemoState, actor: String?, projectId: String): List<WrapGroup> {
        val ix = ix(s)
        val p = s.projects[projectId] ?: return emptyList()
        if (p.ownerId != actor) return emptyList()
        return ix.matchesByProject[projectId].orEmpty().filter { it.state in DemoIndex.BOOKED_LIKE }
            .mapNotNull { m ->
                val items = ix.itemsByMatch[m.id].orEmpty().sortedBy { kindOrder.indexOf(it.kind) }
                if (items.isEmpty()) return@mapNotNull null
                val u = ix.profile(m.collaboratorId) ?: return@mapNotNull null
                val r = s.roles[m.roleId] ?: return@mapNotNull null
                WrapGroup(m, ix.card(u, actor, headshot = r.isCast), r.roleCode, items)
            }.sortedWith(compareBy<WrapGroup>({ !it.match.isPostRole }, { it.person.profile.displayName }))
    }

    fun feedbackState(s: DemoState, actor: String?, matchId: String): FeedbackState {
        val m = s.matches[matchId] ?: return FeedbackState(false, false, false, false)
        if (actor != m.ownerId && actor != m.collaboratorId) return FeedbackState(false, false, false, false)
        val rows = ix(s).feedbackByMatch[matchId].orEmpty()
        return FeedbackState(
            unlocked = m.state == MatchState.WRAPPED || m.state == MatchState.NO_SHOW,
            iSubmitted = rows.any { it.fromId == actor },
            otherSubmitted = rows.any { it.fromId != actor },
            revealed = rows.any { it.revealedAt != null },
        )
    }

    fun reputation(s: DemoState, profileId: String): Reputation = ix(s).reputation(profileId)

    // ───────────── Safety & account ─────────────

    fun myReports(s: DemoState, actor: String?): List<Report> =
        s.reports.filter { it.reporterId == actor }.map { it.report }.sortedByDescending { it.createdAt }

    /** Who / what a report of mine is about, for S21: ("Casting H.", "CH") for a person, ("Rent Day", null) for a project. */
    fun reportTarget(s: DemoState, actor: String?, report: Report): Pair<String, String?>? {
        val row = s.reports.firstOrNull { it.reporterId == actor && it.report.id == report.id } ?: return null
        val ix = ix(s)
        fun person(id: String?) = id?.let { ix.profile(it) }?.let { it.shortName to it.initials }
        return when (report.targetKind) {
            ReportTarget.PROFILE -> person(report.targetId)
            ReportTarget.PROJECT -> s.projects[report.targetId]?.let { it.title to null }
            ReportTarget.ROLE -> s.roles[report.targetId]?.let { r -> "${DemoText.roleName(r)} · ${s.projects[r.projectId]?.title ?: ""}".trimEnd(' ', '·') to null }
            ReportTarget.MESSAGE -> s.messages.firstOrNull { it.id == report.targetId }?.senderId?.let { person(it) } ?: person(row.subjectProfileId)
            ReportTarget.BOOKING -> s.bookingCards[report.targetId]?.terms?.projectTitle?.let { it to null } ?: person(row.subjectProfileId)
        }
    }

    fun blocked(s: DemoState, actor: String?): List<ProfileCard> {
        actor ?: return emptyList()
        val ix = ix(s)
        return s.blocks.filter { it.blockerId == actor }.mapNotNull { b -> ix.profile(b.blockedId) }.map { ix.card(it, actor) }
    }

    fun notifications(s: DemoState, actor: String?): List<AppNotification> = memo(s, "notes|$actor") { notifications0(s, actor) }

    private fun notifications0(s: DemoState, actor: String?): List<AppNotification> {
        val hide = blockedMentions(ix(s), actor)
        return s.notifications.filter { it.userId == actor }.map { it.n }.filterNot { hide(it) }.sortedByDescending { it.createdAt }
    }

    /**
     * Blocking someone hides them everywhere, older notifications included: anything that links to a conversation, booking,
     * day sheet, wrap, role or project shared with a blocked person, or names them in its title or body.
     */
    internal fun blockedMentions(ix: DemoIndex, actor: String?): (AppNotification) -> Boolean {
        actor ?: return { false }
        val s = ix.s
        val blockedIds = s.blocks.mapNotNull { b ->
            when (actor) { b.blockerId -> b.blockedId; b.blockedId -> b.blockerId; else -> null }
        }.toSet()
        if (blockedIds.isEmpty()) return { false }
        val names = blockedIds.mapNotNull { ix.profile(it) }.flatMap { listOf(it.shortName, it.displayName) }.filter { it.length >= 3 }.distinct()
        val matchIds = ix.matchesByUser[actor].orEmpty().filter { it.ownerId in blockedIds || it.collaboratorId in blockedIds }.map { it.id }.toSet()
        val theirProjects = blockedIds.flatMap { ix.projectsByOwner[it].orEmpty() }.map { it.id }.toSet()
        val theirRoles = theirProjects.flatMap { ix.rolesByProject[it].orEmpty() }.map { it.id }.toSet()
        return { n ->
            val link = n.deepLink.orEmpty().removePrefix("calltime://")
            val parts = link.split('/')
            val linked = when (parts.firstOrNull()) {
                "m", "booking" -> parts.getOrNull(1) in matchIds
                "wrap" -> (parts.getOrNull(1) == "m" && parts.getOrNull(2) in matchIds) || (parts.getOrNull(1) == "p" && parts.getOrNull(2) in theirProjects)
                "d", "p", "dashboard" -> parts.getOrNull(1) in theirProjects
                "r", "role" -> parts.getOrNull(1) in theirRoles
                else -> false
            }
            linked || names.any { nm -> n.title.contains(nm) || n.body.contains(nm) }
        }
    }

    @Serializable
    data class ExportBundle(
        val exportedAt: Long,
        val email: String? = null,
        val profile: Profile? = null,
        val privateMinRate: Int? = null,
        val projects: List<Project> = emptyList(),
        val roles: List<ProjectRole> = emptyList(),
        val applications: List<Application> = emptyList(),
        val matches: List<Match> = emptyList(),
        val messagesSent: List<Message> = emptyList(),
        val bookingCards: List<BookingCard> = emptyList(),
        val wrapItems: List<WrapItem> = emptyList(),
        val feedbackGiven: List<Feedback> = emptyList(),
        val reports: List<Report> = emptyList(),
        val blockedProfileIds: List<String> = emptyList(),
        val savedRoleIds: List<String> = emptyList(),
        val notifications: List<AppNotification> = emptyList(),
    )

    private val exportJson = Json { prettyPrint = true; encodeDefaults = true }

    /** Everything the viewer owns or authored. Never other people's private answers or rates. */
    fun exportMyData(s: DemoState, actor: String?): String {
        actor ?: return "{}"
        val myProjects = s.projects.values.filter { it.ownerId == actor }
        val myProjectIds = myProjects.map { it.id }.toSet()
        val myMatches = s.matches.values.filter { it.ownerId == actor || it.collaboratorId == actor }
        val matchIds = myMatches.map { it.id }.toSet()
        val bundle = ExportBundle(
            exportedAt = engine.nowOf(s),
            email = s.emails[actor],
            profile = s.profiles[actor],
            privateMinRate = s.privateRates[actor],
            projects = myProjects,
            roles = s.roles.values.filter { it.projectId in myProjectIds },
            applications = s.applications.values.filter { it.applicantId == actor },
            matches = myMatches,
            messagesSent = s.messages.filter { it.senderId == actor },
            bookingCards = s.bookingCards.values.filter { it.matchId in matchIds },
            wrapItems = s.wrapItems.values.filter { it.matchId in matchIds },
            feedbackGiven = s.feedback.filter { it.fromId == actor },
            reports = s.reports.filter { it.reporterId == actor }.map { it.report },
            blockedProfileIds = s.blocks.filter { it.blockerId == actor }.map { it.blockedId },
            savedRoleIds = s.swipes.filter { it.actorId == actor && it.dir == SwipeDir.SAVE }.map { it.targetId },
            notifications = s.notifications.filter { it.userId == actor }.map { it.n },
        )
        return exportJson.encodeToString(ExportBundle.serializer(), bundle)
    }
}
