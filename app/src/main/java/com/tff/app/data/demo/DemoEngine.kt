package com.tff.app.data.demo

import com.tff.app.core.Dates
import com.tff.app.data.RepoException
import com.tff.app.data.demo.SeedKit.DAY
import com.tff.app.data.demo.SeedKit.HOUR
import com.tff.app.domain.AuditionRules
import com.tff.app.domain.BookingDiff
import com.tff.app.domain.BookingTermsFactory
import com.tff.app.domain.ListingRules
import com.tff.app.domain.MessageSafety
import com.tff.app.domain.Notice
import com.tff.app.domain.Severity
import com.tff.app.domain.WrapItemsFactory
import com.tff.app.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.random.Random

/** The demo clock: real time (injectable for tests) + DemoState.offsetMillis. */
interface DemoClock {
    fun baseMillis(): Long
    val zone: ZoneId
}

object SystemDemoClock : DemoClock {
    override fun baseMillis(): Long = System.currentTimeMillis()
    override val zone: ZoneId get() = ZoneId.systemDefault()
}

/** What just happened, so DemoSimulator can answer on behalf of seeded people. [actor] did it. */
sealed interface SimEvent {
    val actor: String?
    data class Applied(override val actor: String, val appId: String) : SimEvent
    data class Decided(override val actor: String, val appId: String, val decision: Decision, val matchId: String?) : SimEvent
    data class AskSent(override val actor: String, val appId: String, val isBackupOffer: Boolean) : SimEvent
    data class AskAnswered(override val actor: String, val appId: String, val matchId: String?) : SimEvent
    data class MessageSent(override val actor: String, val matchId: String, val body: String) : SimEvent
    data class AuditionSent(override val actor: String, val matchId: String, val messageId: String) : SimEvent
    data class AuditionResponded(override val actor: String, val matchId: String, val response: AuditionResponse) : SimEvent
    data class BookingSent(override val actor: String, val matchId: String, val cardId: String) : SimEvent
    data class BookingResponded(override val actor: String, val matchId: String, val cardId: String, val accepted: Boolean) : SimEvent
    data class Published(override val actor: String, val projectId: String) : SimEvent
    data class DaySheetPublished(override val actor: String, val dayId: String) : SimEvent
    data class Nudged(override val actor: String, val dayId: String, val profileId: String) : SimEvent
    data class Wrapped(override val actor: String?, val matchIds: List<String>) : SimEvent
    data class WrapMarked(override val actor: String, val itemId: String) : SimEvent
    data class FeedbackSubmitted(override val actor: String, val matchId: String) : SimEvent
    data class ClockMoved(override val actor: String?, val days: Double) : SimEvent
    data class Withdrawn(override val actor: String, val matchId: String) : SimEvent
}

/**
 * All demo logic over an immutable [DemoState]. Every mutation runs in [mutate] (one lock, one new
 * state, then events), so DemoRepo can expose the state as a StateFlow and persist it write-behind.
 * Pure JVM: no android.* here; the clock is injected.
 */
class DemoEngine(
    initial: DemoState,
    val clock: DemoClock = SystemDemoClock,
    private val logger: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<DemoState> get() = _state
    @Volatile var listener: ((SimEvent) -> Unit)? = null
    private val lock = Any()
    @Volatile private var cached: DemoIndex? = null

    val zone: ZoneId get() = clock.zone
    fun nowOf(s: DemoState): Long = clock.baseMillis() + s.offsetMillis
    fun now(): Long = nowOf(_state.value)
    fun today(): LocalDate = dateOf(now())
    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun index(s: DemoState = _state.value): DemoIndex {
        val c = cached
        val n = nowOf(s)
        if (c != null && c.s === s && abs(c.now - n) < 30_000) return c
        return DemoIndex(s, n, zone).also { cached = it }
    }

    /** Replace the whole world (load, reset). */
    fun replace(s: DemoState) = synchronized(lock) { _state.value = s }

    // ─────────────────────────── transactions ───────────────────────────

    inner class Tx(var s: DemoState) {
        val now: Long = nowOf(s)
        val today: LocalDate = dateOf(now)
        val events = mutableListOf<SimEvent>()
        val ix: DemoIndex by lazy { DemoIndex(s, now, zone) }

        fun fail(msg: String, code: String? = null): Nothing = throw RepoException(msg, code)
        fun user(actor: String?): String = actor ?: fail("Sign in to do that.")
        fun id(prefix: String): String { val n = s.seq; s = s.copy(seq = n + 1); return "${prefix}_$n" }
        fun emit(e: SimEvent) { events += e }
        fun flag(f: String): Boolean { if (f in s.flags) return false; s = s.copy(flags = s.flags + f); return true }

        fun put(a: Application) { s = s.copy(applications = s.applications + (a.id to a)) }
        fun put(m: Match) { s = s.copy(matches = s.matches + (m.id to m)) }
        fun put(r: ProjectRole) { s = s.copy(roles = s.roles + (r.id to r)) }
        fun put(p: Project) { s = s.copy(projects = s.projects + (p.id to p)) }
        fun put(c: BookingCard) { s = s.copy(bookingCards = s.bookingCards + (c.id to c)) }
        fun put(w: WrapItem) { s = s.copy(wrapItems = s.wrapItems + (w.id to w)) }
        fun put(d: ShootDay) { s = s.copy(shootDays = s.shootDays + (d.id to d)) }
        fun put(p: Profile) { s = s.copy(profiles = s.profiles + (p.id to p)) }

        fun app(id: String) = s.applications[id] ?: fail("That application no longer exists.")
        fun match(id: String) = s.matches[id] ?: fail("That conversation no longer exists.")
        fun role(id: String) = s.roles[id] ?: fail("That role no longer exists.")
        fun project(id: String) = s.projects[id] ?: fail("That project no longer exists.")
        fun profile(id: String) = s.profiles[id]
        fun name(id: String?) = id?.let { s.profiles[it]?.shortName } ?: "Someone"
        fun first(id: String?) = DemoText.firstName(id?.let { s.profiles[it] })

        fun message(matchId: String, sender: String?, body: String, kind: MessageKind = MessageKind.TEXT, cardId: String? = null,
                    flags: Set<MessageFlag> = emptySet(), audition: AuditionPayload? = null, safety: Boolean = false, at: Long = now): Message {
            val m = Message(id("msg"), matchId, sender, kind, body, audition, cardId, flags, safety, at)
            s = s.copy(messages = s.messages + m)
            return m
        }

        fun system(matchId: String, body: String) = message(matchId, null, body, MessageKind.SYSTEM)

        /** Notifications are stored only for people who can log in (personas + "build my own"). */
        fun notify(userId: String, kind: NotificationKind, title: String, body: String, link: String?) {
            if (userId !in s.realUsers) return
            val n = AppNotification(id("n"), kind, title, body, link, now)
            val mine = s.notifications.count { it.userId == userId }
            val trimmed = if (mine > 200) {
                val oldest = s.notifications.filter { it.userId == userId }.minByOrNull { it.n.createdAt }
                s.notifications - oldest!!
            } else s.notifications
            s = s.copy(notifications = trimmed + NotificationRow(userId, n))
        }

        fun touch(userId: String) {
            val p = s.profiles[userId] ?: return
            if (now - p.lastActiveAt > 10 * 60_000) put(p.copy(lastActiveAt = now))
        }

        fun termsSummary(r: ProjectRole, p: Project): String {
            val a = DemoCities.area(p.areaId)
            return DemoText.termsSummary(r, p, a?.name ?: "", DemoCities.city(p.cityId)?.name ?: "")
        }

        fun roleItem(r: ProjectRole, viewer: String?): RoleWithProject =
            DemoIndex(s, now, zone).roleWithProject(r, viewer) ?: fail("That role no longer exists.")

        fun likesUsedToday(uid: String): Int = s.applications.values.count {
            it.applicantId == uid && it.origin == ApplicationOrigin.APPLIED && dateOf(it.createdAt) == today
        }

        fun asksUsedToday(projectId: String): Int {
            val roleIds = s.roles.values.filter { it.projectId == projectId }.map { it.id }.toSet()
            return s.applications.values.count { it.roleId in roleIds && it.origin == ApplicationOrigin.INVITED && dateOf(it.createdAt) == today }
        }
    }

    fun <T> mutate(block: Tx.() -> T): Result<T> {
        val events: List<SimEvent>
        val result: T
        synchronized(lock) {
            val tx = Tx(_state.value)
            try {
                result = tx.block()
            } catch (e: RepoException) {
                return Result.failure(e)
            } catch (e: Throwable) {
                logger("DemoEngine error: ${e.javaClass.simpleName}: ${e.message}")
                return Result.failure(RepoException("Something went wrong on our side. Try again.", "internal"))
            }
            _state.value = tx.s
            events = tx.events
        }
        events.forEach { e -> runCatching { listener?.invoke(e) }.onFailure { logger("listener: ${it.message}") } }
        return Result.success(result)
    }

    // ─────────────────────────── session & profile ───────────────────────────

    fun startDemo(persona: DemoPersona?): Session = mutate {
        val sess = if (persona != null) {
            val uid = DemoIds.personaUser(persona)
            touch(uid)
            Session(uid, s.emails[uid] ?: DemoIds.personaEmail(persona), persona, hasProfile = uid in s.profiles)
        } else {
            val uid = id("u_new")
            Session(uid, null, null, hasProfile = false)
        }
        s = s.copy(session = sess, realUsers = s.realUsers + sess.userId)
        sess
    }.getOrThrow()

    fun signOut() { mutate { s = s.copy(session = null) } }

    fun sendOtp(email: String): Result<Unit> = mutate {
        if (!EMAIL.matches(email.trim())) fail("Enter a valid email address, like name@example.com.")
    }

    fun verifyOtp(email: String, code: String): Result<Session> = mutate {
        val e = email.trim().lowercase()
        if (!EMAIL.matches(e)) fail("Enter a valid email address, like name@example.com.")
        if (!Regex("\\d{6}").matches(code.trim())) fail("Enter the 6-digit code from the email.")
        if (code.trim() == "000000") fail("That code has expired. Send a new one; you can resend after 30 seconds.")
        val existing = s.emails.entries.firstOrNull { it.value.equals(e, true) }?.key
        val uid = existing ?: id("u_new")
        val persona = DemoPersona.entries.firstOrNull { DemoIds.personaUser(it) == uid }
        val sess = Session(uid, e, persona, hasProfile = uid in s.profiles)
        s = s.copy(session = sess, realUsers = s.realUsers + uid, emails = s.emails + (uid to e))
        sess
    }

    fun saveProfile(actor: String?, p: Profile, minRate: Int?): Result<Profile> = mutate {
        val uid = user(actor)
        val name = p.displayName.trim()
        if (name.length !in 2..40) fail("Your name needs 2 to 40 characters.")
        if (p.languages.isEmpty()) fail("Pick at least one language.")
        if ((p.bio?.length ?: 0) > 280) fail("Keep your bio under 280 characters.")
        if (p.tasteFilms.size > 5) fail("Pick up to 5 favourite films.")
        if (p.secondaryRoles.size > 4) fail("Pick up to 4 other roles.")
        if (p.stepUpRoles.size > 2) fail("Pick up to 2 step-up roles.")
        val area = DemoCities.area(p.areaId) ?: fail("Pick your area.")
        if (area.cityId != p.cityId) fail("Pick an area in your city.")
        if (minRate != null && minRate <= 0) fail("Leave the minimum rate empty, or enter an amount above zero.")
        val old = s.profiles[uid]
        val links = p.workLinks.map { if (it.id.isBlank() || s.profiles.values.any { o -> o.id != uid && o.workLinks.any { l -> l.id == it.id } }) it.copy(id = id("wl")) else it }
        val saved = p.copy(
            id = uid, displayName = name, workLinks = links,
            travelRadiusKm = p.travelRadiusKm.coerceIn(2, 150),
            emailVerified = true, phoneVerified = old?.phoneVerified ?: false, idVerified = old?.idVerified ?: false,
            adultConfirmed = true, createdAt = old?.createdAt ?: now, lastActiveAt = now,
        )
        put(saved)
        s = s.copy(
            privateRates = if (minRate == null) s.privateRates - uid else s.privateRates + (uid to minRate),
            session = s.session?.takeIf { it.userId == uid }?.copy(hasProfile = true) ?: s.session,
            realUsers = s.realUsers + uid,
        )
        saved
    }

    // ─────────────────────────── gigs ───────────────────────────

    fun swipeRole(actor: String?, roleId: String, dir: SwipeDir): Result<Unit> = mutate {
        val uid = user(actor)
        role(roleId)
        if (dir == SwipeDir.BACKUP) fail("Backup is for people, not roles.")
        s = s.copy(swipes = s.swipes.filterNot { it.actorId == uid && it.targetKind == "ROLE" && it.targetId == roleId && it.dir != SwipeDir.RIGHT } +
            SwipeRow(uid, "ROLE", roleId, null, dir, now))
        touch(uid)
    }

    fun undoSwipe(actor: String?, roleId: String): Result<Unit> = mutate {
        val uid = user(actor)
        val last = s.swipes.lastOrNull { it.actorId == uid && it.targetKind == "ROLE" && it.targetId == roleId } ?: fail("Nothing to undo.")
        if (last.dir == SwipeDir.RIGHT) fail("An application can't be undone.")
        s = s.copy(swipes = s.swipes - last)
    }

    fun apply(actor: String?, roleId: String, note: String?, workLinkId: String?, answer: String?, kit: Boolean): Result<Application> = mutate {
        val uid = user(actor)
        val me = profile(uid) ?: fail("Finish your profile first, then apply.")
        val r = role(roleId)
        val p = project(r.projectId)
        if (p.ownerId == uid) fail("This is your own project.")
        val openProject = p.status == ProjectStatus.OPEN || (r.isPostRole && p.status in setOf(ProjectStatus.CREWED, ProjectStatus.WRAPPED))
        if (r.status != RoleStatus.OPEN || !openProject) fail("Just filled. Here's a similar one.", "filled")
        if (ix.blocked(uid, p.ownerId)) fail("You can't apply to this project.")
        if (s.applications.values.any { it.roleId == roleId && it.applicantId == uid }) fail("You've already applied to this role.")
        if (!r.isPostRole && p.shootStart?.isBefore(today) == true) fail("This shoot has already started.", "filled")
        if (r.applicationsCount >= r.applicationCap) fail("This role has all the applications it can take right now. Try a similar one.", "cap")
        if (uid in s.realUsers && likesUsedToday(uid) >= DAILY_LIKES) fail("You've sent 25 today. Fewer, better applications get answered. Back tomorrow.", "daily_cap")
        val n = note?.trim()?.takeIf { it.isNotEmpty() }
        if ((n?.length ?: 0) > 140) fail("Keep the note under 140 characters.")
        val ans = answer?.trim()?.takeIf { it.isNotEmpty() }
        if (r.screeningQuestion != null && ans == null) fail("Answer the screening question to apply.")
        if ((ans?.length ?: 0) > 300) fail("Keep the answer under 300 characters.")
        val link = workLinkId?.takeIf { lid -> me.workLinks.any { it.id == lid } }
        val a = Application(id("a"), roleId, uid, ApplicationOrigin.APPLIED, n, link, ans, kit, createdAt = now)
        put(a)
        put(r.copy(applicationsCount = r.applicationsCount + 1))
        s = s.copy(swipes = s.swipes.filterNot { it.actorId == uid && it.targetKind == "ROLE" && it.targetId == roleId } + SwipeRow(uid, "ROLE", roleId, null, SwipeDir.RIGHT, now))
        notify(p.ownerId, NotificationKind.APPLICATION_RECEIVED, "New applicant · ${DemoText.roleName(r)}",
            "${me.shortName} applied to ${p.title}.", "calltime://crewup/${r.id}")
        touch(uid)
        emit(SimEvent.Applied(uid, a.id))
        a
    }

    // ─────────────────────────── projects (owner) ───────────────────────────

    fun saveDraft(actor: String?, d: ProjectDraft): Result<String> = mutate {
        val uid = user(actor)
        profile(uid) ?: fail("Finish your profile first, then post a project.")
        val existing = s.projects[d.project.id]
        if (existing != null && existing.ownerId != uid) fail("You can only edit your own projects.")
        if (existing != null && existing.status !in setOf(ProjectStatus.DRAFT, ProjectStatus.OPEN)) fail("This project can't be edited any more.")
        val area = DemoCities.area(d.project.areaId) ?: fail("Pick the city and area where you'll shoot.")
        val city = DemoCities.city(area.cityId)!!
        val pid = existing?.id ?: d.project.id.takeIf { it.isNotBlank() && it !in s.projects } ?: id("p")
        val proj = d.project.copy(
            id = pid, ownerId = uid, payerId = uid, cityId = city.id, areaId = area.id,
            shootDates = d.project.shootDates.distinct().sorted(),
            status = existing?.status ?: ProjectStatus.DRAFT, rescheduleCount = existing?.rescheduleCount ?: 0,
            createdAt = existing?.createdAt ?: now, publishedAt = existing?.publishedAt, updatedAt = now,
            bringFriendWelcome = d.project.bringFriendWelcome || d.project.shootLocationType == LocationType.PRIVATE_HOME,
        )
        put(proj)
        val oldRoles = s.roles.values.filter { it.projectId == pid }.associateBy { it.id }
        val keep = mutableSetOf<String>()
        d.roles.forEach { r0 ->
            val old = oldRoles[r0.id]
            val rid = old?.id ?: r0.id.takeIf { it.isNotBlank() && it !in s.roles } ?: id("r_" + pid.removePrefix("p_"))
            keep += rid
            put(r0.copy(
                id = rid, projectId = pid, currency = city.currency,
                creditTitle = r0.creditTitle.ifBlank { Taxonomy.info(r0.roleCode).defaultCredit },
                applicationCap = (if (r0.applicationCap <= 0) Taxonomy.defaultCap(r0.roleCode) else r0.applicationCap).coerceIn(5, 40),
                count = r0.count.coerceIn(1, 10),
                applicationsCount = old?.applicationsCount ?: 0, filledCount = old?.filledCount ?: 0,
                status = old?.status ?: RoleStatus.OPEN,
            ))
        }
        oldRoles.values.filter { it.id !in keep }.forEach { r ->
            val hasApps = s.applications.values.any { it.roleId == r.id }
            if (hasApps) closeRoleInternal(r.id, DemoText.CLOSURE_ROLE_CLOSED)
            else s = s.copy(roles = s.roles - r.id)
        }
        pid
    }

    fun publish(actor: String?, projectId: String): Result<Unit> = mutate {
        val uid = user(actor)
        val p = project(projectId)
        if (p.ownerId != uid) fail("You can only publish your own projects.")
        if (p.status !in setOf(ProjectStatus.DRAFT, ProjectStatus.OPEN)) fail("This project can't be published again.")
        val owner = profile(uid) ?: fail("Finish your profile first.")
        val city = DemoCities.city(p.cityId) ?: fail("Pick the city where you'll shoot.")
        val roles = s.roles.values.filter { it.projectId == projectId && it.status != RoleStatus.CLOSED }
        val draft = ListingRules.applyAuto(ProjectDraft(p, roles), city)
        val results = ListingRules.check(draft, owner, city, today)
        val block = results.firstOrNull { it.severity == Severity.BLOCK }
        if (block != null) fail(block.message, block.rule)
        val first = p.status == ProjectStatus.DRAFT
        put(draft.project.copy(status = ProjectStatus.OPEN, publishedAt = p.publishedAt ?: now, updatedAt = now))
        draft.roles.forEach { r -> put(r.copy(status = if (r.status == RoleStatus.FILLED) RoleStatus.FILLED else RoleStatus.OPEN)) }
        if (first) emit(SimEvent.Published(uid, projectId))
    }

    fun reschedule(actor: String?, projectId: String, newDates: List<LocalDate>): Result<Unit> = mutate {
        val uid = user(actor)
        val p = project(projectId)
        if (p.ownerId != uid) fail("Only the production can reschedule.")
        val dates = newDates.distinct().sorted()
        if (dates.isEmpty()) fail("Pick at least one shoot date.")
        if (dates.first().isBefore(today)) fail("New dates can't be in the past.")
        put(p.copy(shootDates = dates, rescheduleCount = p.rescheduleCount + 1, updatedAt = now))
        s.shootDays.values.filter { it.projectId == projectId && it.cancelledAt == null && it.day !in dates }
            .forEach { put(it.copy(cancelledAt = now)) }
        val title = p.title
        s.matches.values.filter { it.projectId == projectId && !it.isPostRole }.forEach { m ->
            when (m.state) {
                MatchState.BOOKED, MatchState.OFFERED -> {
                    val base = (DemoIndex(s, now, zone).acceptedCard(m.id) ?: DemoIndex(s, now, zone).latestCard(m.id))?.terms ?: return@forEach
                    val cardId = sendCardInternal(m.id, uid, base.copy(dates = dates), reschedule = true)
                    emit(SimEvent.BookingSent(uid, m.id, cardId))
                }
                MatchState.TALKING -> system(m.id, "${name(uid)} moved the shoot to ${Dates.span(dates.first(), dates.last())}.")
                else -> {}
            }
        }
        val roleIds = s.roles.values.filter { it.projectId == projectId }.map { it.id }.toSet()
        s.applications.values.filter { it.roleId in roleIds && it.status in setOf(ApplicationStatus.PENDING, ApplicationStatus.BACKUP) }.forEach { a ->
            notify(a.applicantId, NotificationKind.PROJECT_CHANGED, "New dates · $title",
                "${Dates.span(dates.first(), dates.last())}. Your application still stands.", "calltime://r/${a.roleId}")
        }
    }

    fun cancelProject(actor: String?, projectId: String, reason: String): Result<Unit> = mutate {
        val uid = user(actor)
        val p = project(projectId)
        if (p.ownerId != uid) fail("Only the production can cancel this project.")
        if (p.status == ProjectStatus.CANCELLED) fail("This project is already cancelled.")
        val why = reason.trim().ifBlank { "No reason given" }
        put(p.copy(status = ProjectStatus.CANCELLED, updatedAt = now))
        s.matches.values.filter { it.projectId == projectId && it.state in DemoIndex.ACTIVE }.forEach { m ->
            endMatchByOwner(m, why, cancelled = true)
        }
        s.roles.values.filter { it.projectId == projectId }.forEach { r -> closeRoleInternal(r.id, DemoText.CLOSURE_CANCELLED) }
        s.shootDays.values.filter { it.projectId == projectId && it.cancelledAt == null }.forEach { put(it.copy(cancelledAt = now)) }
    }

    fun closeRole(actor: String?, roleId: String): Result<Unit> = mutate {
        val uid = user(actor)
        val r = role(roleId)
        if (project(r.projectId).ownerId != uid) fail("Only the production can close this role.")
        closeRoleInternal(roleId, DemoText.CLOSURE_ROLE_CLOSED)
        s.matches.values.filter { it.roleId == roleId && it.state in setOf(MatchState.TALKING, MatchState.OFFERED) }.forEach { m ->
            put(m.copy(state = MatchState.CLOSED, endedAt = now))
            system(m.id, "The production closed this role. Thanks for talking.")
            notify(m.collaboratorId, NotificationKind.APPLICATION_CLOSED, "Role closed · ${project(r.projectId).title}", DemoText.CLOSURE_ROLE_CLOSED, "calltime://m/${m.id}")
        }
    }

    private fun Tx.closeRoleInternal(roleId: String, closure: String) {
        val r = role(roleId)
        val p = project(r.projectId)
        if (r.status != RoleStatus.FILLED) put(r.copy(status = RoleStatus.CLOSED))
        s.applications.values.filter { it.roleId == roleId && it.status in setOf(ApplicationStatus.PENDING, ApplicationStatus.BACKUP) }.forEach { a ->
            put(a.copy(status = ApplicationStatus.CLOSED, declineReason = closure, decidedAt = now))
            notify(a.applicantId, NotificationKind.APPLICATION_CLOSED, "Update · ${p.title}", closure, "calltime://r/$roleId")
        }
    }

    // ─────────────────────────── crew up ───────────────────────────

    fun decide(actor: String?, applicationId: String, d: Decision, reason: String?): Result<Match?> = mutate {
        val uid = user(actor)
        val a = app(applicationId)
        val r = role(a.roleId)
        val p = project(r.projectId)
        if (p.ownerId != uid) fail("Only the production can decide on applicants.")
        if (a.status !in setOf(ApplicationStatus.PENDING, ApplicationStatus.BACKUP)) fail("You've already answered this applicant.")
        if (a.origin == ApplicationOrigin.INVITED && a.status == ApplicationStatus.PENDING) fail("Waiting for their answer to your ask.")
        val m: Match? = when (d) {
            Decision.TALK -> {
                if (r.status == RoleStatus.CLOSED) fail("This role is closed.")
                val m = openMatch(a, r, p)
                notify(a.applicantId, NotificationKind.MATCH_CREATED, "In talks · ${p.title}",
                    "${name(uid)} wants to talk about ${DemoText.roleName(r)}.", "calltime://m/${m.id}")
                m
            }
            Decision.DECLINE -> {
                put(a.copy(status = ApplicationStatus.DECLINED, declineReason = DemoText.CLOSURE_DECLINED, decidedAt = now, backupRank = null))
                put(r.copy(applicationsCount = (r.applicationsCount - 1).coerceAtLeast(0)))
                notify(a.applicantId, NotificationKind.APPLICATION_CLOSED, "Update · ${p.title}", DemoText.CLOSURE_DECLINED, "calltime://r/${r.id}")
                reason?.let { s = s.copy(events = (s.events + LogEvent(uid, "decline_reason", mapOf("app" to a.id, "reason" to it), now)).takeLast(300)) }
                null
            }
            Decision.BACKUP -> {
                val rank = (s.applications.values.filter { it.roleId == r.id && it.status == ApplicationStatus.BACKUP }.mapNotNull { it.backupRank }.maxOrNull() ?: 0) + 1
                put(a.copy(status = ApplicationStatus.BACKUP, backupRank = rank, decidedAt = now))
                notify(a.applicantId, NotificationKind.APPLICATION_BACKUP, "Backup list · ${p.title}", DemoText.BACKUP_LINE, "calltime://r/${r.id}")
                null
            }
        }
        touch(uid)
        emit(SimEvent.Decided(uid, a.id, d, m?.id))
        m
    }

    private fun Tx.openMatch(a: Application, r: ProjectRole, p: Project): Match {
        val m = Match(id("m"), a.id, p.id, r.id, p.ownerId, a.applicantId, MatchState.TALKING, isPostRole = r.isPostRole, createdAt = now)
        put(m)
        put(a.copy(status = ApplicationStatus.MATCHED, decidedAt = now, backupRank = null))
        message(m.id, null, termsSummary(r, p), MessageKind.SYSTEM)
        return m
    }

    fun askCheck(actor: String?, roleId: String, profileId: String): String? {
        val ix = index()
        val uid = actor ?: return "Sign in to send an ask."
        val r = ix.s.roles[roleId] ?: return "That role no longer exists."
        val p = ix.s.projects[r.projectId] ?: return "That project no longer exists."
        if (p.ownerId != uid) return "Only the production can send asks for this role."
        if (r.status != RoleStatus.OPEN || p.status != ProjectStatus.OPEN) return "This role isn't open."
        val t = ix.profile(profileId) ?: return "This profile isn't available."
        val first = DemoText.firstName(t)
        if (profileId == uid) return "That's you."
        if (ix.blocked(uid, profileId)) return "You can't ask this person."
        if (profileId in ix.hidden) return "This profile is under review."
        if (ix.appsByRole[roleId].orEmpty().any { it.applicantId == profileId }) return "$first already applied or was asked for this role."
        if (ix.isBookedOn(p.id, profileId)) return "$first is already booked on this project."
        val asked30 = ix.s.applications.values.any { it.applicantId == profileId && it.origin == ApplicationOrigin.INVITED &&
            ix.s.roles[it.roleId]?.projectId?.let { pid -> ix.s.projects[pid]?.ownerId } == uid && now() - it.createdAt < 30 * DAY &&
            ix.s.roles[it.roleId]?.projectId == p.id }
        if (asked30) return "You asked $first for this project in the last 30 days."
        val used = ix.s.applications.values.count { a -> a.origin == ApplicationOrigin.INVITED && ix.s.roles[a.roleId]?.projectId == p.id && dateOf(a.createdAt) == ix.today }
        if (used >= DAILY_ASKS) return "You've sent 10 asks for this project today. More tomorrow."
        val keep = isOnKeepList(ix, uid, profileId)
        if (r.isCast && !keep) return "Actors apply to you. Calltime doesn't let anyone browse actors."
        val item = ix.roleWithProject(r, uid) ?: return "That role no longer exists."
        return runCatching { com.tff.app.domain.ApplicantRanker.askBlockReason(item, t, ix.s.privateRates[profileId], ix.today, keep) }
            .getOrElse { fallbackAskBlock(item, t, ix.s.privateRates[profileId]) }
    }

    /** Minimal reverse G4/G5 used only if the domain check is unavailable. */
    private fun fallbackAskBlock(item: RoleWithProject, t: Profile, rate: Int?): String? {
        val first = DemoText.firstName(t)
        if (t.compMode == CompMode.PAID_ONLY && !item.role.isPaid) return "$first only takes paid work right now."
        if (t.contentWontDo.any { it in item.project.contentFlags }) return "This project has content $first has ruled out."
        if (rate != null && item.role.compType == CompType.PAID_DAY && (item.role.amountMax ?: 0) < rate && t.compMode == CompMode.PAID_ONLY)
            return "The day rate is below what $first takes."
        return null
    }

    fun isOnKeepList(ix: DemoIndex, ownerId: String, profileId: String): Boolean =
        ix.s.feedback.any { it.fromId == ownerId && it.toId == profileId && it.answers.workAgain == true }

    fun sendAsk(actor: String?, roleId: String, profileId: String, note: String?): Result<Unit> {
        askCheck(actor, roleId, profileId)?.let { return Result.failure(RepoException(it)) }
        return mutate {
            val uid = user(actor)
            val r = role(roleId)
            val p = project(r.projectId)
            val n = note?.trim()?.takeIf { it.isNotEmpty() }
            if ((n?.length ?: 0) > 140) fail("Keep the note under 140 characters.")
            val a = Application(id("a"), roleId, profileId, ApplicationOrigin.INVITED, n, createdAt = now)
            put(a)
            notify(profileId, NotificationKind.ASK_RECEIVED, "${name(uid)} asked you to be ${DemoText.roleName(r)}",
                "${p.title} · ${DemoText.payLine(r, p)}", "calltime://inbox")
            touch(uid)
            emit(SimEvent.AskSent(uid, a.id, isBackupOffer = false))
        }
    }

    fun respondAsk(actor: String?, applicationId: String, accept: Boolean, reason: AskDeclineReason?): Result<Match?> = mutate {
        val uid = user(actor)
        val a = app(applicationId)
        if (a.applicantId != uid) fail("This ask isn't for you.")
        if (a.origin != ApplicationOrigin.INVITED || a.status != ApplicationStatus.PENDING) fail("You've already answered this ask.")
        val r = role(a.roleId)
        val p = project(r.projectId)
        val m = if (accept) {
            if (r.status != RoleStatus.OPEN) fail("This role has closed. Thanks for answering.")
            val m = openMatch(a, r, p)
            notify(p.ownerId, NotificationKind.MATCH_CREATED, "${name(uid)} said yes to talk", "${DemoText.roleName(r)} on ${p.title}.", "calltime://m/${m.id}")
            m
        } else {
            val label = reason?.let { DemoText.askDeclineLabel(it) } ?: "Not this time"
            put(a.copy(status = ApplicationStatus.DECLINED, declineReason = label, decidedAt = now))
            notify(p.ownerId, NotificationKind.APPLICATION_CLOSED, "${name(uid)} passed", "${DemoText.roleName(r)} on ${p.title}: $label.", "calltime://crewup/${r.id}")
            null
        }
        touch(uid)
        emit(SimEvent.AskAnswered(uid, a.id, m?.id))
        m
    }

    fun offerSlotToBackup(actor: String?, roleId: String): Result<Unit> = mutate {
        val uid = user(actor)
        val r = role(roleId)
        val p = project(r.projectId)
        if (p.ownerId != uid) fail("Only the production can offer this slot.")
        if (r.status == RoleStatus.CLOSED) fail("This role is closed.")
        if (r.filledCount >= r.count) fail("This role is already full.")
        if (asksUsedToday(p.id) >= DAILY_ASKS) fail("You've sent 10 asks for this project today. More tomorrow.")
        val b = s.applications.values.filter { it.roleId == roleId && it.status == ApplicationStatus.BACKUP }.minByOrNull { it.backupRank ?: Int.MAX_VALUE }
            ?: fail("No one is on the backup list for this role.")
        put(b.copy(status = ApplicationStatus.PENDING, origin = ApplicationOrigin.INVITED, backupRank = null, createdAt = now,
            note = "A slot opened on ${p.title}. Same terms as before.", decidedAt = null))
        notify(b.applicantId, NotificationKind.ASK_RECEIVED, "A slot opened · ${p.title}",
            "${name(uid)} is offering you ${DemoText.roleName(r)}. Same terms as before.", "calltime://inbox")
        emit(SimEvent.AskSent(uid, b.id, isBackupOffer = true))
    }

    // ─────────────────────────── chat ───────────────────────────

    fun sendMessage(actor: String?, matchId: String, body: String): Result<Unit> = mutate {
        val uid = user(actor)
        val m = match(matchId)
        if (uid != m.ownerId && uid != m.collaboratorId) fail("You're not part of this conversation.")
        if (m.state in CLOSED_STATES) fail("This conversation has ended.")
        val other = if (uid == m.ownerId) m.collaboratorId else m.ownerId
        if (ix.blocked(uid, other)) fail("You can't message this person.")
        val text = body.trim()
        if (text.isEmpty()) fail("Write a message first.")
        if (text.length > 2000) fail("Keep messages under 2,000 characters.")
        val flags = runCatching { MessageSafety.analyze(text) }.getOrDefault(emptySet())
        message(matchId, uid, text, flags = flags)
        s = s.copy(readMarks = s.readMarks + ("$uid|$matchId" to now))
        val p = project(m.projectId)
        notify(other, NotificationKind.MESSAGE, name(uid), "New message about ${p.title}.", "calltime://m/$matchId")
        touch(uid)
        emit(SimEvent.MessageSent(uid, matchId, text))
    }

    fun markRead(actor: String?, matchId: String) {
        val uid = actor ?: return
        mutate { s = s.copy(readMarks = s.readMarks + ("$uid|$matchId" to now)) }
    }

    fun sendAuditionRequest(actor: String?, matchId: String, req: AuditionRequest): Result<Unit> = mutate {
        val uid = user(actor)
        val m = match(matchId)
        if (uid != m.ownerId) fail("Only the production can request an audition.")
        val r = role(m.roleId)
        if (!r.isCast) fail("Auditions are for cast roles only.")
        if (m.state != MatchState.TALKING) fail("Request auditions while you're in talks, before the booking card.")
        val p = project(m.projectId)
        AuditionRules.validate(req, p.auditionMode)?.let { fail(it) }
        val free = listOfNotNull(req.note, req.venue, req.address).joinToString(" ")
        val flags = if (free.isBlank()) emptySet() else runCatching { MessageSafety.analyze(free) }.getOrDefault(emptySet())
        val body = when (req.mode) {
            AuditionMode.SELF_TAPE -> "Audition request · self-tape" + (req.dueDate?.let { " · due ${Dates.short(it)}" } ?: "")
            AuditionMode.VIDEO_CALL -> "Audition request · video call" + (req.date?.let { " · ${Dates.short(it)}" } ?: "") + (req.time?.let { " ${Dates.time(it)}" } ?: "")
            AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS -> "Audition request · ${req.venue ?: "studio"}" + (req.date?.let { " · ${Dates.short(it)}" } ?: "") + (req.time?.let { " ${Dates.time(it)}" } ?: "")
        }
        val msg = message(matchId, uid, body, MessageKind.AUDITION, flags = flags, audition = AuditionPayload(req))
        notify(m.collaboratorId, NotificationKind.MESSAGE, "Audition request · ${p.title}", body, "calltime://m/$matchId")
        emit(SimEvent.AuditionSent(uid, matchId, msg.id))
    }

    fun respondAudition(actor: String?, messageId: String, response: AuditionResponse, tapeUrl: String?): Result<Unit> = mutate {
        val uid = user(actor)
        val msg = s.messages.firstOrNull { it.id == messageId } ?: fail("That audition request no longer exists.")
        val payload = msg.audition ?: fail("That message isn't an audition request.")
        val m = match(msg.matchId)
        if (uid != m.collaboratorId) fail("Only the actor can answer this audition request.")
        if (m.state in CLOSED_STATES) fail("This conversation has ended.")
        val url = tapeUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (response == AuditionResponse.TAPE_SENT) {
            if (payload.request.mode != AuditionMode.SELF_TAPE) fail("This audition isn't a self-tape.")
            if (url == null || !URL.matches(url)) fail("Paste a link to your tape (starting with https://).")
        }
        s = s.copy(messages = s.messages.map { if (it.id == messageId) it.copy(audition = payload.copy(response = response, tapeUrl = url ?: payload.tapeUrl)) else it })
        val who = first(uid)
        system(m.id, when (response) {
            AuditionResponse.WILL_DO -> "$who will do the audition."
            AuditionResponse.CANT_MAKE_IT -> "$who can't make that audition time."
            AuditionResponse.TAPE_SENT -> "$who sent their tape."
        })
        notify(m.ownerId, NotificationKind.MESSAGE, "Audition · ${name(uid)}", when (response) {
            AuditionResponse.WILL_DO -> "Will do the audition."
            AuditionResponse.CANT_MAKE_IT -> "Can't make that time."
            AuditionResponse.TAPE_SENT -> "Sent their tape."
        }, "calltime://m/${m.id}")
        emit(SimEvent.AuditionResponded(uid, m.id, response))
    }

    // ─────────────────────────── booking ───────────────────────────

    fun draftBookingTerms(actor: String?, matchId: String): BookingTerms {
        val ix = index()
        val m = ix.s.matches[matchId] ?: throw RepoException("That conversation no longer exists.")
        ix.latestCard(matchId)?.let { return it.terms }
        val r = ix.s.roles.getValue(m.roleId)
        val p = ix.s.projects.getValue(m.projectId)
        val payer = ix.profile(m.ownerId)
        val item = ix.roleWithProject(r, m.ownerId)!!
        return runCatching { BookingTermsFactory.fromRole(item, payer?.shortName ?: "") }
            .getOrElse { SeedKit.terms(r, p, m.ownerId, payer?.shortName ?: "") }
    }

    fun sendBookingCard(actor: String?, matchId: String, terms: BookingTerms): Result<Unit> = mutate {
        val uid = user(actor)
        val m = match(matchId)
        if (uid != m.ownerId) fail("Only the payer can send the booking card.")
        if (m.state !in DemoIndex.ACTIVE) fail("This conversation has ended.")
        if ((terms.notes?.length ?: 0) > 280) fail("Keep the notes under 280 characters.")
        if (terms.compType != CompType.UNPAID && (terms.amount ?: 0) <= 0) fail("Add pay, or mark it unpaid and say what people get.")
        val cardId = sendCardInternal(matchId, uid, terms, reschedule = false)
        touch(uid)
        emit(SimEvent.BookingSent(uid, matchId, cardId))
    }

    private fun Tx.sendCardInternal(matchId: String, uid: String, terms0: BookingTerms, reschedule: Boolean): String {
        val m = match(matchId)
        val r = role(m.roleId)
        val p = project(m.projectId)
        val fixed = terms0.copy(
            roleCode = r.roleCode, projectTitle = p.title, isPostRole = r.isPostRole, currency = r.currency,
            payerId = m.ownerId, payerName = name(m.ownerId),
            noticePledge = if (r.isPostRole) SeedKit.NOTICE_POST else SeedKit.NOTICE_ON_SET,
        )
        val terms = runCatching { BookingTermsFactory.normalize(fixed) }.getOrElse {
            val payBy = if (fixed.compType == CompType.UNPAID) null else (if (fixed.isPostRole) fixed.deliveryDate else fixed.dates.maxOrNull())?.plusDays(SeedKit.termsDays(fixed.paymentTerms))
            fixed.copy(payByDate = payBy).let { it.copy(deliverables = SeedKit.deliverables(it)) }
        }
        val cards = s.bookingCards.values.filter { it.matchId == matchId }.sortedBy { it.version }
        val prev = cards.lastOrNull()
        val changed = prev?.let { pv -> runCatching { BookingDiff.changedFields(pv.terms, terms) }.getOrElse { if (pv.terms == terms) emptySet() else setOf("terms") } } ?: emptySet()
        if (prev != null && changed.isEmpty() && prev.status in setOf(BookingStatus.SENT, BookingStatus.ACCEPTED)) fail("Nothing changed. Edit a term to send a new version.")
        if (prev?.status == BookingStatus.SENT) put(prev.copy(status = BookingStatus.SUPERSEDED))
        val v = (prev?.version ?: 0) + 1
        val card = BookingCard("bc_${matchId}_$v", matchId, v, terms, BookingStatus.SENT, uid, now, changedFields = changed)
        put(card)
        if (reschedule) flag("reschedule:${card.id}")
        when (m.state) {
            MatchState.TALKING, MatchState.OFFERED -> put(m.copy(state = MatchState.OFFERED))
            MatchState.BOOKED -> put(m.copy(changePending = true))
            else -> {}
        }
        message(matchId, uid, if (v == 1) "Booking card v1" else "Booking card v$v · changed: ${changed.joinToString(", ").ifBlank { "terms" }}",
            MessageKind.BOOKING, cardId = card.id)
        val booked = m.state == MatchState.BOOKED
        notify(m.collaboratorId, if (booked) NotificationKind.BOOKING_CHANGED else NotificationKind.BOOKING_CARD,
            if (booked) "Booking changed · ${p.title}" else "Booking card · ${p.title}",
            if (reschedule) "New dates. Accepting or declining is penalty-free." else "${DemoText.roleName(r)} · ${DemoText.payLine(r, p)}",
            "calltime://m/$matchId")
        return card.id
    }

    fun respondBookingCard(actor: String?, cardId: String, accept: Boolean): Result<Unit> = mutate {
        val uid = user(actor)
        val card = s.bookingCards[cardId] ?: fail("That booking card no longer exists.")
        val m = match(card.matchId)
        if (uid != m.collaboratorId) fail("Only the person being booked can answer this card.")
        val latest = s.bookingCards.values.filter { it.matchId == m.id }.maxByOrNull { it.version }
        if (card.status != BookingStatus.SENT || latest?.id != card.id) fail("This version was replaced. Open the latest booking card.")
        val r = role(m.roleId)
        val p = project(m.projectId)
        if (accept) {
            s.bookingCards.values.filter { it.matchId == m.id && it.status == BookingStatus.ACCEPTED }.forEach { put(it.copy(status = BookingStatus.SUPERSEDED)) }
            put(card.copy(status = BookingStatus.ACCEPTED, respondedAt = now))
            val firstBooking = m.state != MatchState.BOOKED
            put(m.copy(state = MatchState.BOOKED, bookedAt = m.bookedAt ?: now, changePending = false))
            if (firstBooking) fillSlot(r, p)
            upsertWrapItems(m.id, card.terms)
            if (!r.isPostRole) {
                // a booked person joins every published day sheet still ahead
                s.shootDays.values.filter { it.projectId == p.id && it.cancelledAt == null && !it.day.isBefore(today) }.forEach { d ->
                    if (s.callTimes.none { it.shootDayId == d.id && it.profileId == uid }) {
                        s = s.copy(callTimes = s.callTimes + CallTime(d.id, uid, d.generalCall))
                        notify(uid, NotificationKind.DAY_SHEET, "Day sheet · ${p.title}",
                            "Your call is ${Dates.time(d.generalCall)} on ${Dates.short(d.day)}. Tap Got it so the production knows.",
                            "calltime://d/${p.id}/${d.id}")
                    }
                }
            }
            system(m.id, if (firstBooking) "Booked. ${name(uid)} accepted the booking card." else "${name(uid)} accepted booking card v${card.version}.")
            notify(m.ownerId, NotificationKind.BOOKING_ACCEPTED, "${name(uid)} accepted · ${p.title}",
                if (firstBooking) "${DemoText.roleName(r)} is booked." else "v${card.version} is now the agreed terms.", "calltime://m/${m.id}")
        } else {
            put(card.copy(status = BookingStatus.DECLINED, respondedAt = now))
            when {
                m.state == MatchState.BOOKED && "reschedule:${card.id}" in s.flags -> {
                    // new dates don't work: a penalty-free exit, the slot reopens
                    put(m.copy(state = MatchState.WITHDRAWN, changePending = false, endedAt = now, withdrawReason = "New dates don't work", noticeHours = 9999))
                    put(app(m.applicationId).copy(status = ApplicationStatus.WITHDRAWN))
                    reopenSlot(m)
                    system(m.id, "${name(uid)} can't make the new dates. No one is marked down for this.")
                }
                m.state == MatchState.BOOKED -> {
                    put(m.copy(changePending = false))
                    system(m.id, "${name(uid)} declined v${card.version}. The earlier agreed terms still stand.")
                }
                else -> {
                    put(m.copy(state = MatchState.TALKING))
                    system(m.id, "${name(uid)} declined booking card v${card.version}.")
                }
            }
            notify(m.ownerId, NotificationKind.BOOKING_CHANGED, "${name(uid)} declined · ${p.title}", "Booking card v${card.version} was declined.", "calltime://m/${m.id}")
        }
        touch(uid)
        emit(SimEvent.BookingResponded(uid, m.id, card.id, accept))
    }

    private fun Tx.fillSlot(r0: ProjectRole, p: Project) {
        val r = role(r0.id).let { it.copy(filledCount = it.filledCount + 1) }
        val full = r.filledCount >= r.count
        put(if (full) r.copy(status = RoleStatus.FILLED) else r)
        if (full) {
            s.applications.values.filter { it.roleId == r.id && it.status == ApplicationStatus.PENDING }.forEach { a ->
                put(a.copy(status = ApplicationStatus.CLOSED, declineReason = DemoText.CLOSURE_FILLED, decidedAt = now))
                notify(a.applicantId, NotificationKind.APPLICATION_CLOSED, "Role filled · ${p.title}", DemoText.CLOSURE_FILLED, "calltime://r/${r.id}")
            }
        }
        val roles = s.roles.values.filter { it.projectId == p.id && it.status != RoleStatus.CLOSED }
        if (roles.isNotEmpty() && roles.all { it.status == RoleStatus.FILLED } && project(p.id).status == ProjectStatus.OPEN) {
            put(project(p.id).copy(status = ProjectStatus.CREWED))
        }
    }

    private fun Tx.reopenSlot(m: Match) {
        val r = role(m.roleId)
        put(r.copy(filledCount = (r.filledCount - 1).coerceAtLeast(0), status = if (r.status == RoleStatus.FILLED) RoleStatus.OPEN else r.status))
        val p = project(m.projectId)
        if (p.status == ProjectStatus.CREWED) put(p.copy(status = ProjectStatus.OPEN))
        // nothing is owed on a booking that ended before the shoot; drop future call times
        val futureDays = s.shootDays.values.filter { it.projectId == p.id && !it.day.isBefore(today) }.map { it.id }.toSet()
        s = s.copy(
            callTimes = s.callTimes.filterNot { it.profileId == m.collaboratorId && it.shootDayId in futureDays },
            wrapItems = s.wrapItems.filterValues { !(it.matchId == m.id && it.status == WrapStatus.OPEN) },
        )
    }

    private fun Tx.upsertWrapItems(matchId: String, terms: BookingTerms) {
        val fresh = runCatching { WrapItemsFactory.fromTerms(matchId, terms) { k -> "wi_${matchId}_${k.name}" } }
            .getOrElse { SeedKit.wrapItems(matchId, terms) }
        val existing = s.wrapItems.values.filter { it.matchId == matchId }.associateBy { it.kind }
        val keepKinds = fresh.map { it.kind }.toSet()
        existing.values.filter { it.kind !in keepKinds && it.status == WrapStatus.OPEN }.forEach { s = s.copy(wrapItems = s.wrapItems - it.id) }
        fresh.forEach { item ->
            val old = existing[item.kind]
            put(if (old == null || old.status == WrapStatus.OPEN) item.copy(id = old?.id ?: item.id) else old.copy(dueDate = item.dueDate))
        }
    }

    fun withdraw(actor: String?, matchId: String, reason: WithdrawReason, note: String?): Result<String?> = mutate {
        val uid = user(actor)
        val m = match(matchId)
        if (uid != m.collaboratorId) fail("Only the booked person can withdraw. The production can release instead.")
        if (m.state !in DemoIndex.ACTIVE) fail("This booking has already ended.")
        val label = DemoText.withdrawLabel(reason) + (note?.trim()?.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: "")
        val p = project(m.projectId)
        val r = role(m.roleId)
        val hours = noticeHours(m)
        put(m.copy(state = MatchState.WITHDRAWN, endedAt = now, withdrawReason = label, noticeHours = hours))
        put(app(m.applicationId).copy(status = ApplicationStatus.WITHDRAWN))
        if (m.state == MatchState.BOOKED) reopenSlot(m)
        val backup = s.applications.values.filter { it.roleId == r.id && it.status == ApplicationStatus.BACKUP }.minByOrNull { it.backupRank ?: Int.MAX_VALUE }
        system(m.id, "${name(uid)} withdrew${hours?.let { " with ${DemoText.noticeText(it)}" } ?: ""}. Reason: ${DemoText.withdrawLabel(reason)}.")
        notify(m.ownerId, NotificationKind.WITHDRAWAL, "${name(uid)} withdrew · ${DemoText.roleName(r)}",
            listOfNotNull(hours?.let { DemoText.noticeText(it).replaceFirstChar { c -> c.uppercase() } }, backup?.let { "Backup #1: ${name(it.applicantId)}. Offer them the slot?" })
                .joinToString(". ").ifBlank { "The slot is open again." }, "calltime://p/${p.id}")
        emit(SimEvent.Withdrawn(uid, m.id))
        backup?.id
    }

    private fun Tx.noticeHours(m: Match): Int? {
        if (m.state != MatchState.BOOKED) return null
        val terms = s.bookingCards.values.filter { it.matchId == m.id && it.status == BookingStatus.ACCEPTED }.maxByOrNull { it.version }?.terms ?: return null
        return runCatching { Notice.hoursUntil(terms, now) }.getOrElse {
            val first = if (terms.isPostRole) terms.deliveryDate else terms.dates.minOrNull()
            first?.let { ((SeedKit.millisAt(it, LocalTime.of(7, 0), zone) - now) / HOUR).toInt() }
        }
    }

    fun release(actor: String?, matchId: String, reason: ReleaseReason): Result<Unit> = mutate {
        val uid = user(actor)
        val m = match(matchId)
        if (uid != m.ownerId) fail("Only the production can release someone.")
        if (m.state !in DemoIndex.ACTIVE) fail("This booking has already ended.")
        endMatchByOwner(m, DemoText.releaseLabel(reason), cancelled = false)
    }

    private fun Tx.endMatchByOwner(m: Match, why: String, cancelled: Boolean) {
        val p = project(m.projectId)
        val r = role(m.roleId)
        if (m.state == MatchState.BOOKED || m.state == MatchState.OFFERED && m.bookedAt != null) {
            val hours = noticeHours(m)
            put(m.copy(state = MatchState.RELEASED, endedAt = now, withdrawReason = why, noticeHours = hours, releasedByOwner = true))
            if (m.state == MatchState.BOOKED) reopenSlot(m)
            val late = hours != null && hours < 72
            system(m.id, "${name(m.ownerId)} ${if (cancelled) "cancelled the project" else "released this booking"}" +
                (hours?.let { " with ${DemoText.noticeText(it)}" } ?: "") + ". Reason: $why." +
                if (late) " Less than 72 hours' notice shows on the production's Kept bookings." else "")
            notify(m.collaboratorId, NotificationKind.RELEASE, if (cancelled) "Project cancelled · ${p.title}" else "Released · ${p.title}",
                "${DemoText.roleName(r)}. Reason: $why.", "calltime://m/${m.id}")
        } else {
            put(m.copy(state = MatchState.CLOSED, endedAt = now, withdrawReason = why))
            system(m.id, if (cancelled) "The production cancelled this project. Reason: $why." else "The production ended these talks. Reason: $why.")
            notify(m.collaboratorId, NotificationKind.APPLICATION_CLOSED, "Update · ${p.title}",
                if (cancelled) DemoText.CLOSURE_CANCELLED else DemoText.CLOSURE_DECLINED, "calltime://m/${m.id}")
        }
        put(app(m.applicationId).copy(status = ApplicationStatus.CLOSED, decidedAt = now))
    }

    fun shareContacts(actor: String?, matchId: String): Result<Unit> = mutate {
        val uid = user(actor)
        val m = match(matchId)
        if (uid != m.ownerId && uid != m.collaboratorId) fail("You're not part of this conversation.")
        if (m.state !in DemoIndex.BOOKED_LIKE) fail("You can share contacts once you're booked.")
        put(if (uid == m.ownerId) m.copy(contactsSharedByOwner = true) else m.copy(contactsSharedByCollab = true))
        system(m.id, "${name(uid)} shared contact details: ${s.emails[uid] ?: "email on file"}.")
    }

    // ─────────────────────────── shoot days ───────────────────────────

    fun publishShootDay(actor: String?, d: ShootDayDraft): Result<Unit> = mutate {
        val uid = user(actor)
        val p = project(d.projectId)
        if (p.ownerId != uid) fail("Only the production can publish day sheets.")
        if (p.shootDates.isNotEmpty() && d.day !in p.shootDates) fail("Pick one of the shoot dates.")
        if (d.address.isBlank()) fail("Add the full address so people can find the set.")
        if ((d.notes?.length ?: 0) > 500) fail("Keep the notes under 500 characters.")
        val existing = d.shootDayId?.let { s.shootDays[it] } ?: s.shootDays.values.firstOrNull { it.projectId == p.id && it.day == d.day && it.cancelledAt == null }
        val dayId = existing?.id ?: id("sd_" + p.id.removePrefix("p_"))
        val day = ShootDay(dayId, p.id, d.day, d.generalCall, d.wrapBy, d.address.trim(), SeedKit.mapsUrl(d.address.trim()),
            d.parkingNote, d.hospitalNote, d.mealsPlan, d.notes, (existing?.version ?: 0) + 1, now)
        put(day)
        if (p.exactAddress.isNullOrBlank()) put(p.copy(exactAddress = d.address.trim()))
        val booked = s.matches.values.filter { it.projectId == p.id && it.state == MatchState.BOOKED && !it.isPostRole }
        // update in place (stable order), drop people no longer booked, append newcomers
        val fresh = booked.associate { m -> m.collaboratorId to CallTime(dayId, m.collaboratorId, d.perPersonCall[m.collaboratorId] ?: d.generalCall) }
        val kept = s.callTimes.mapNotNull { c -> if (c.shootDayId != dayId) c else fresh[c.profileId] }
        s = s.copy(callTimes = kept + fresh.values.filter { f -> kept.none { it.shootDayId == dayId && it.profileId == f.profileId } })
        booked.forEach { m ->
            val call = d.perPersonCall[m.collaboratorId] ?: d.generalCall
            notify(m.collaboratorId, NotificationKind.DAY_SHEET, (if (existing == null) "Day sheet · " else "Day sheet updated · ") + p.title,
                "Your call is ${Dates.time(call)} on ${Dates.short(d.day)}. Tap Got it so the production knows.", "calltime://d/${p.id}/$dayId")
        }
        emit(SimEvent.DaySheetPublished(uid, dayId))
    }

    fun ackShootDay(actor: String?, shootDayId: String): Result<Unit> = mutate {
        val uid = user(actor)
        val call = s.callTimes.firstOrNull { it.shootDayId == shootDayId && it.profileId == uid } ?: fail("You're not on this day sheet.")
        s = s.copy(callTimes = s.callTimes.map { if (it === call) it.copy(ackAt = now) else it })
    }

    fun nudge(actor: String?, shootDayId: String, profileId: String): Result<Unit> = mutate {
        val uid = user(actor)
        val day = s.shootDays[shootDayId] ?: fail("That day sheet no longer exists.")
        val p = project(day.projectId)
        if (p.ownerId != uid) fail("Only the production can nudge.")
        val call = s.callTimes.firstOrNull { it.shootDayId == shootDayId && it.profileId == profileId } ?: fail("They're not on this day sheet.")
        if (call.ackAt != null) fail("${first(profileId)} already tapped Got it.")
        notify(profileId, NotificationKind.DAY_SHEET, "Please confirm your call · ${p.title}",
            "${name(uid)} asked you to tap Got it on the ${Dates.short(day.day)} day sheet.", "calltime://d/${p.id}/$shootDayId")
        emit(SimEvent.Nudged(uid, shootDayId, profileId))
    }

    fun setAttendance(actor: String?, shootDayId: String, status: Attendance, profileId: String?): Result<Unit> = mutate {
        val uid = user(actor)
        val day = s.shootDays[shootDayId] ?: fail("That day sheet no longer exists.")
        val p = project(day.projectId)
        val target = profileId ?: uid
        val call = s.callTimes.firstOrNull { it.shootDayId == shootDayId && it.profileId == target } ?: fail("They're not on this day sheet.")
        if (target == uid) {
            if (status !in setOf(Attendance.CONFIRMED, Attendance.CANT_MAKE_IT, Attendance.DISPUTED)) fail("Only the production can mark attendance.")
            if (status == Attendance.DISPUTED && call.attendance != Attendance.NO_SHOW) fail("There's nothing to dispute.")
        } else {
            if (p.ownerId != uid) fail("Only the production can mark attendance.")
            if (status !in setOf(Attendance.ATTENDED, Attendance.NO_SHOW)) fail("Mark them as attended or no-show.")
            if (day.day.isAfter(today)) fail("You can mark attendance once the day has happened.")
        }
        s = s.copy(callTimes = s.callTimes.map { if (it === call) it.copy(attendance = status, ackAt = it.ackAt ?: if (status == Attendance.CONFIRMED) now else null) else it })
        when (status) {
            Attendance.CANT_MAKE_IT -> notify(p.ownerId, NotificationKind.STILL_COMING, "${name(uid)} can't make it · ${p.title}",
                "${Dates.short(day.day)}. Check your backups.", "calltime://p/${p.id}")
            Attendance.NO_SHOW -> notify(target, NotificationKind.SYSTEM, "Marked as no-show · ${p.title}",
                "If that's wrong, open the day sheet and dispute it.", "calltime://d/${p.id}/$shootDayId")
            else -> {}
        }
    }

    fun markWrapped(actor: String?, projectId: String): Result<Unit> = mutate {
        val uid = user(actor)
        val p = project(projectId)
        if (p.ownerId != uid) fail("Only the production can wrap the project.")
        val end = p.shootEnd ?: fail("This project has no shoot days to wrap.")
        if (!today.isAfter(end)) fail("You can wrap after the last shoot day.")
        wrapProject(p, uid)
    }

    private fun Tx.wrapProject(p: Project, actor: String?) {
        val ms = s.matches.values.filter { it.projectId == p.id && !it.isPostRole }
        val wrapped = mutableListOf<String>()
        ms.forEach { m ->
            when (m.state) {
                MatchState.BOOKED -> {
                    put(m.copy(state = MatchState.WRAPPED, endedAt = now, changePending = false))
                    system(m.id, "Wrapped. The wrap checklist is open, and private feedback unlocks now.")
                    notify(m.collaboratorId, NotificationKind.FEEDBACK_UNLOCKED, "${p.title} wrapped",
                        "Your wrap checklist is ready. Give private feedback when you can.", "calltime://wrap/m/${m.id}")
                    notify(m.ownerId, NotificationKind.FEEDBACK_UNLOCKED, "${p.title} wrapped",
                        "Mark pay, footage and credit as you deliver them.", "calltime://wrap/p/${p.id}")
                    wrapped += m.id
                }
                MatchState.TALKING, MatchState.OFFERED -> {
                    put(m.copy(state = MatchState.CLOSED, endedAt = now))
                    system(m.id, "The shoot has wrapped, so these talks have closed.")
                    put(app(m.applicationId).copy(status = ApplicationStatus.CLOSED, declineReason = DemoText.CLOSURE_ROLE_CLOSED, decidedAt = now))
                }
                else -> {}
            }
        }
        val dayIds = s.shootDays.values.filter { it.projectId == p.id && !it.day.isAfter(today) }.map { it.id }.toSet()
        s = s.copy(callTimes = s.callTimes.map { if (it.shootDayId in dayIds && it.attendance == null) it.copy(attendance = Attendance.ATTENDED) else it })
        s.roles.values.filter { it.projectId == p.id && !it.isPostRole && it.status == RoleStatus.OPEN }.forEach { closeRoleInternal(it.id, DemoText.CLOSURE_ROLE_CLOSED) }
        put(project(p.id).copy(status = ProjectStatus.WRAPPED, updatedAt = now))
        if (wrapped.isNotEmpty()) emit(SimEvent.Wrapped(actor, wrapped))
    }

    // ─────────────────────────── wrap & feedback ───────────────────────────

    fun markWrapItem(actor: String?, itemId: String, link: String?, note: String?): Result<Unit> = mutate {
        val uid = user(actor)
        val item = s.wrapItems[itemId] ?: fail("That wrap item no longer exists.")
        val m = match(item.matchId)
        val owes = if (item.owedBy == OwedBy.PRODUCTION) m.ownerId else m.collaboratorId
        if (uid != owes) fail("Only the person who owes this can mark it done.")
        if (item.status !in setOf(WrapStatus.OPEN, WrapStatus.NOT_RECEIVED)) fail("This is already marked done.")
        val url = link?.trim()?.takeIf { it.isNotEmpty() }
        if (item.kind in setOf(WrapKind.FOOTAGE, WrapKind.FINAL_FILM, WrapKind.DELIVERY) && (url == null || !URL.matches(url)))
            fail("Add a link (starting with https://) so they can open it.")
        if ((note?.length ?: 0) > 200) fail("Keep the note under 200 characters.")
        put(item.copy(status = WrapStatus.DONE, link = url ?: item.link, note = note?.trim()?.takeIf { it.isNotEmpty() } ?: item.note, markedAt = now))
        val other = if (uid == m.ownerId) m.collaboratorId else m.ownerId
        val p = project(m.projectId)
        notify(other, NotificationKind.WRAP_ITEM, "${name(uid)} marked ${kindLabel(item.kind)} done", "${p.title} · tap Got it when it reaches you.",
            "calltime://wrap/m/${m.id}")
        emit(SimEvent.WrapMarked(uid, itemId))
    }

    fun confirmWrapItem(actor: String?, itemId: String, received: Boolean): Result<Unit> = mutate {
        val uid = user(actor)
        val item = s.wrapItems[itemId] ?: fail("That wrap item no longer exists.")
        val m = match(item.matchId)
        val owes = if (item.owedBy == OwedBy.PRODUCTION) m.ownerId else m.collaboratorId
        val confirmer = if (owes == m.ownerId) m.collaboratorId else m.ownerId
        if (uid != confirmer) fail("The other side confirms this item.")
        if (item.status == WrapStatus.CONFIRMED) fail("Already confirmed.")
        if (received && item.status != WrapStatus.DONE) fail("It hasn't been marked done yet.")
        put(item.copy(status = if (received) WrapStatus.CONFIRMED else WrapStatus.NOT_RECEIVED, confirmedAt = now))
        val p = project(m.projectId)
        notify(owes, NotificationKind.WRAP_ITEM,
            if (received) "${name(uid)} confirmed ${kindLabel(item.kind)}" else "${name(uid)}: ${kindLabel(item.kind)} not received",
            "${p.title}${if (received) "" else " · check and mark it again"}", "calltime://wrap/m/${m.id}")
        if (received && item.kind == WrapKind.DELIVERY && m.state == MatchState.BOOKED) wrapPostMatch(m, actor = uid)
    }

    private fun Tx.wrapPostMatch(m: Match, actor: String?) {
        put(m.copy(state = MatchState.WRAPPED, endedAt = now))
        val p = project(m.projectId)
        system(m.id, "Delivered and confirmed. Wrapped; private feedback unlocks now.")
        notify(m.collaboratorId, NotificationKind.FEEDBACK_UNLOCKED, "Delivery confirmed · ${p.title}", "Wrapped. Give private feedback when you can.", "calltime://wrap/m/${m.id}")
        notify(m.ownerId, NotificationKind.FEEDBACK_UNLOCKED, "Delivery confirmed · ${p.title}", "Wrapped. Pay is due within the terms.", "calltime://wrap/m/${m.id}")
        emit(SimEvent.Wrapped(actor, listOf(m.id)))
    }

    fun submitFeedback(actor: String?, matchId: String, answers: FeedbackAnswers): Result<Unit> = mutate {
        val uid = user(actor)
        val m = match(matchId)
        if (uid != m.ownerId && uid != m.collaboratorId) fail("You're not part of this booking.")
        if (m.state != MatchState.WRAPPED && m.state != MatchState.NO_SHOW) fail("Feedback opens after wrap.")
        if (s.feedback.any { it.matchId == matchId && it.fromId == uid }) fail("You've already sent feedback for this booking.")
        if ((answers.privateNote?.length ?: 0) > 500) fail("Keep the note under 500 characters.")
        val other = if (uid == m.ownerId) m.collaboratorId else m.ownerId
        val theirs = s.feedback.firstOrNull { it.matchId == matchId && it.fromId == other }
        val mine = Feedback("fb_${matchId}_${if (uid == m.ownerId) "o" else "c"}", matchId, uid, other, answers, now, if (theirs != null) now else null)
        s = s.copy(feedback = s.feedback.map { if (it.id == theirs?.id) it.copy(revealedAt = now) else it } + mine)
        val p = project(m.projectId)
        if (theirs != null) {
            listOf(uid, other).forEach { notify(it, NotificationKind.FEEDBACK_UNLOCKED, "Feedback is in · ${p.title}",
                "You both answered. Track records now include this booking.", "calltime://wrap/m/$matchId") }
        } else {
            notify(other, NotificationKind.FEEDBACK_UNLOCKED, "${name(uid)} left private feedback", "${p.title} · answers unlock when you both submit, or in 14 days.",
                "calltime://wrap/m/$matchId")
        }
        emit(SimEvent.FeedbackSubmitted(uid, matchId))
    }

    // ─────────────────────────── safety & account ───────────────────────────

    fun report(actor: String?, kind: ReportTarget, targetId: String, reason: ReportReason, details: String?): Result<Unit> = mutate {
        val uid = user(actor)
        val d = details?.trim()?.takeIf { it.isNotEmpty() }
        if ((d?.length ?: 0) > 500) fail("Keep the details under 500 characters.")
        val subject: String? = when (kind) {
            ReportTarget.PROFILE -> targetId.takeIf { it in s.profiles }
            ReportTarget.PROJECT -> s.projects[targetId]?.ownerId
            ReportTarget.ROLE -> s.roles[targetId]?.let { s.projects[it.projectId]?.ownerId }
            ReportTarget.MESSAGE -> s.messages.firstOrNull { it.id == targetId }?.senderId
            ReportTarget.BOOKING -> s.bookingCards[targetId]?.sentBy
        }
        if (subject == null && kind != ReportTarget.MESSAGE) fail("We couldn't find what you're reporting. Try again from the profile or project.")
        if (subject == uid) fail("You can't report yourself.")
        val rep = Report(id("rep"), kind, targetId, reason, d, ReportStatus.RECEIVED, now)
        s = s.copy(reports = s.reports + ReportRow(uid, rep, subject))
        notify(uid, NotificationKind.REPORT_UPDATE, "Report received", "Thanks. We aim to review within 24 hours.", "calltime://safety")
        s = s.copy(events = (s.events + LogEvent(uid, "report", mapOf("kind" to kind.name, "reason" to reason.name), now)).takeLast(300))
    }

    fun block(actor: String?, profileId: String): Result<Unit> = mutate {
        val uid = user(actor)
        if (profileId == uid) fail("You can't block yourself.")
        if (profileId !in s.profiles) fail("This profile isn't available.")
        if (s.blocks.none { it.blockerId == uid && it.blockedId == profileId }) s = s.copy(blocks = s.blocks + BlockRow(uid, profileId, now))
        s.matches.values.filter { m -> m.state in DemoIndex.ACTIVE && ((m.ownerId == uid && m.collaboratorId == profileId) || (m.ownerId == profileId && m.collaboratorId == uid)) }
            .forEach { m ->
                val wasBooked = m.state == MatchState.BOOKED
                put(m.copy(state = MatchState.CLOSED, endedAt = now))
                put(app(m.applicationId).copy(status = ApplicationStatus.CLOSED, decidedAt = now))
                if (wasBooked) reopenSlot(m)
                system(m.id, DemoText.BLOCK_ENDED)
            }
        // pending applications either way are closed quietly
        val theirRoles = s.roles.values.filter { s.projects[it.projectId]?.ownerId == profileId }.map { it.id }.toSet()
        val myRoles = s.roles.values.filter { s.projects[it.projectId]?.ownerId == uid }.map { it.id }.toSet()
        s.applications.values.filter { a -> a.status in setOf(ApplicationStatus.PENDING, ApplicationStatus.BACKUP) &&
            ((a.applicantId == uid && a.roleId in theirRoles) || (a.applicantId == profileId && a.roleId in myRoles)) }
            .forEach { put(it.copy(status = ApplicationStatus.CLOSED, decidedAt = now)) }
    }

    fun unblock(actor: String?, profileId: String): Result<Unit> = mutate {
        val uid = user(actor)
        s = s.copy(blocks = s.blocks.filterNot { it.blockerId == uid && it.blockedId == profileId })
    }

    fun markNotificationRead(actor: String?, id: String) {
        val uid = actor ?: return
        mutate { s = s.copy(notifications = s.notifications.map { if (it.userId == uid && it.n.id == id && it.n.readAt == null) it.copy(n = it.n.copy(readAt = now)) else it }) }
    }

    fun deleteAccount(actor: String?): Result<Unit> = mutate {
        val uid = user(actor)
        val myProjects = s.projects.values.filter { it.ownerId == uid }.map { it.id }.toSet()
        val myRoles = s.roles.values.filter { it.projectId in myProjects }.map { it.id }.toSet()
        val goneMatches = s.matches.values.filter { it.ownerId == uid || it.collaboratorId == uid || it.projectId in myProjects }.map { it.id }.toSet()
        val goneDays = s.shootDays.values.filter { it.projectId in myProjects }.map { it.id }.toSet()
        // people on the other side of an active booking get a neutral notice
        s.matches.values.filter { it.id in goneMatches && it.state in DemoIndex.ACTIVE }.forEach { m ->
            val other = if (m.ownerId == uid) m.collaboratorId else m.ownerId
            notify(other, NotificationKind.SYSTEM, "A conversation ended", "The other person deleted their account.", "calltime://inbox")
        }
        s = s.copy(
            profiles = s.profiles - uid,
            privateRates = s.privateRates - uid,
            emails = s.emails - uid,
            projects = s.projects - myProjects,
            roles = s.roles - myRoles,
            applications = s.applications.filterValues { it.applicantId != uid && it.roleId !in myRoles },
            matches = s.matches - goneMatches,
            messages = s.messages.filter { it.matchId !in goneMatches },
            bookingCards = s.bookingCards.filterValues { it.matchId !in goneMatches },
            shootDays = s.shootDays - goneDays,
            callTimes = s.callTimes.filter { it.profileId != uid && it.shootDayId !in goneDays },
            wrapItems = s.wrapItems.filterValues { it.matchId !in goneMatches },
            feedback = s.feedback.filter { it.fromId != uid && it.toId != uid },
            reports = s.reports.filter { it.reporterId != uid },
            blocks = s.blocks.filter { it.blockerId != uid && it.blockedId != uid },
            swipes = s.swipes.filter { it.actorId != uid },
            notifications = s.notifications.filter { it.userId != uid },
            readMarks = s.readMarks.filterKeys { !it.startsWith("$uid|") },
            session = null,
            realUsers = s.realUsers - uid,
        )
        // roles that lost a booked person reopen
        s.roles.values.forEach { r ->
            val filled = s.matches.values.count { it.roleId == r.id && it.state == MatchState.BOOKED }
            if (filled != r.filledCount && s.projects[r.projectId]?.status == ProjectStatus.OPEN)
                put(r.copy(filledCount = filled, status = if (r.status == RoleStatus.FILLED && filled < r.count) RoleStatus.OPEN else r.status))
        }
    }

    fun log(actor: String?, event: String, props: Map<String, String>) {
        mutate { s = s.copy(events = (s.events + LogEvent(actor, event, props, now)).takeLast(300)) }
    }

    // ─────────────────────────── demo controls ───────────────────────────

    fun setSpeed(sp: SimSpeed) { mutate { s = s.copy(speed = sp) } }

    /** Re-anchor to the device date and reseed; a persona session survives, a "build my own" user doesn't. */
    fun reset(cachedSeed: (LocalDate) -> DemoState? = { null }) {
        val base = clock.baseMillis()
        val anchor = Instant.ofEpochMilli(base).atZone(zone).toLocalDate()
        synchronized(lock) {
            val old = _state.value
            val fresh = cachedSeed(anchor)?.takeIf { it.anchor == anchor && it.seedZone == zone.id } ?: DemoSeed.build(anchor, Random(42), zone, base)
            val sess = old.session?.takeIf { it.persona != null }
            _state.value = fresh.copy(session = sess, speed = old.speed)
        }
    }

    fun advanceDays(days: Double) {
        if (days <= 0) return
        mutate { s = s.copy(offsetMillis = s.offsetMillis + (days * DAY).toLong()); emit(SimEvent.ClockMoved(s.session?.userId, days)) }
        tick()
    }

    /** Move past the last day of the next shoot the session user is part of (booked or owner). */
    fun jumpPastNextShoot() {
        val ix = index()
        val uid = ix.s.session?.userId
        val t = ix.today
        val ends = ix.s.projects.values.filter { p ->
            p.shootDates.isNotEmpty() && p.status in setOf(ProjectStatus.OPEN, ProjectStatus.CREWED) &&
                (p.ownerId == uid || ix.matchesByProject[p.id].orEmpty().any { it.collaboratorId == uid && it.state == MatchState.BOOKED })
        }.mapNotNull { it.shootEnd }.filter { !it.isBefore(t) }
        val target = ends.minOrNull()?.plusDays(1) ?: t.plusDays(1)
        advanceDays(Dates.daysBetween(t, target).toDouble().coerceAtLeast(1.0))
    }

    /** "Jump past wrap +30d": 30 days after the most relevant wrap (or 30 days from now). */
    fun jumpPastWrap() {
        val ix = index()
        val uid = ix.s.session?.userId
        val t = ix.today
        val mine = ix.matchesByUser[uid].orEmpty().filter { it.state in DemoIndex.BOOKED_LIKE }
        val ends = mine.mapNotNull { m ->
            val p = ix.s.projects[m.projectId] ?: return@mapNotNull null
            if (m.isPostRole) ix.acceptedCard(m.id)?.terms?.deliveryDate else p.shootEnd
        }
        val upcoming = ends.filter { !it.isBefore(t) }.minOrNull()
        val target = (upcoming ?: t).plusDays(30)
        advanceDays(Dates.daysBetween(t, target).toDouble().coerceAtLeast(1.0))
    }

    /** Cast personas: a new IN TALKS chat opening with a fee scam. Crew personas: an Ask whose note is a scam. */
    fun injectFlaggedExample(): Result<Unit> = mutate {
        val uid = user(s.session?.userId)
        val me = profile(uid) ?: fail("Finish your profile first.")
        val city = DemoCities.city(me.cityId) ?: DemoCities.city("mumbai")!!
        val area = city.areas.first()
        if (DemoIds.U_SCAM_2 !in s.profiles) {
            put(Profile(DemoIds.U_SCAM_2, "Bright Star Casting", primaryRole = RoleCode.PRODUCER, level = Level.CREDITED_INDIE,
                cityId = city.id, areaId = area.id, languages = listOf("en", "hi"), bio = "New faces for web series and ads. Quick selection.",
                emailVerified = true, createdAt = now - 3 * DAY, lastActiveAt = now))
            // already reported by three people, so it never shows in anyone's deck (PRD §5.5)
            listOf("u_013", "u_029", "u_046").forEachIndexed { i, rep ->
                s = s.copy(reports = s.reports + ReportRow(rep, Report(id("rep"), ReportTarget.PROFILE, DemoIds.U_SCAM_2, ReportReason.MONEY_REQUEST,
                    "Asked for a fee to confirm the role.", ReportStatus.REVIEWING, now - (i + 1) * DAY), DemoIds.U_SCAM_2))
            }
        }
        val p = s.projects[DemoIds.P_SCAM_2] ?: Project(DemoIds.P_SCAM_2, DemoIds.U_SCAM_2, DemoIds.U_SCAM_2, "Star Search Season 2",
            "A web series about first-time actors chasing one big break in the city.", Format.SERIES_PILOT, 20, genres = listOf("Drama"),
            languages = listOf("hi", "en"), cityId = city.id, areaId = area.id, shootLocationType = LocationType.STUDIO,
            shootDates = listOf(today.plusDays(20)), dateCertainty = DateCertainty.LOCKED, hoursCap = 10, meals = Meals.ONE_MEAL,
            transport = Transport.NONE, contentNoneConfirmed = true, auditionMode = AuditionMode.VIDEO_CALL,
            status = ProjectStatus.OPEN, createdAt = now - 2 * DAY, publishedAt = now - 2 * DAY, updatedAt = now)
        put(p)
        val code = me.primaryRole
        val rid = "r_scam_${code.name.lowercase()}"
        val r = s.roles[rid] ?: ProjectRole(rid, p.id, code, creditTitle = Taxonomy.info(code).defaultCredit, compType = CompType.STIPEND,
            amountMin = 3000, amountMax = 3000, currency = city.currency, paymentTerms = PaymentTerms.ON_WRAP,
            gearPolicy = if (Taxonomy.dept(code) in Taxonomy.GEAR_DEPTS) GearPolicy.PRODUCTION_PROVIDES else null, status = RoleStatus.CLOSED)
        put(r)
        val existing = s.applications.values.firstOrNull { it.roleId == rid && it.applicantId == uid }
        if (Taxonomy.isCast(code)) {
            val a = existing ?: Application(id("a"), rid, uid, ApplicationOrigin.APPLIED, "Interested in the lead.", createdAt = now - HOUR,
                status = ApplicationStatus.MATCHED, decidedAt = now)
            put(a.copy(status = ApplicationStatus.MATCHED))
            val m = s.matches.values.firstOrNull { it.applicationId == a.id && it.state in DemoIndex.ACTIVE }
                ?: Match(id("m"), a.id, p.id, rid, DemoIds.U_SCAM_2, uid, MatchState.TALKING, createdAt = now).also { put(it); system(it.id, termsSummary(r, p)) }
            message(m.id, DemoIds.U_SCAM_2, DemoText.scamOpener(DemoText.firstName(me)),
                flags = setOf(MessageFlag.MONEY_FROM_TALENT, MessageFlag.OFF_PLATFORM, MessageFlag.CONTACT), safety = true)
            notify(uid, NotificationKind.MATCH_CREATED, "In talks · ${p.title}", "Bright Star Casting sent you a message.", "calltime://m/${m.id}")
        } else {
            if (existing != null) s = s.copy(applications = s.applications - existing.id)
            val a = Application(id("a"), rid, uid, ApplicationOrigin.INVITED, DemoText.SAFETY_EXAMPLE_NOTE, createdAt = now)
            put(a)
            put(r.copy(status = RoleStatus.OPEN))
            notify(uid, NotificationKind.ASK_RECEIVED, "Bright Star Casting asked you to be ${DemoText.roleName(code)}", "${p.title} · read the note carefully.", "calltime://inbox")
        }
    }

    /** A BOOKED seeded collaborator on the persona's own project withdraws ("Paid work came up"). */
    fun simulateWithdrawal(): Result<String?> {
        val ix = index()
        val uid = ix.s.session?.userId ?: return Result.failure(RepoException("Pick a persona first."))
        val candidates = ix.s.matches.values.filter { m ->
            m.ownerId == uid && m.state == MatchState.BOOKED && m.collaboratorId !in ix.s.realUsers
        }
        val pick = candidates.maxByOrNull { m -> ix.appsByRole[m.roleId].orEmpty().count { it.status == ApplicationStatus.BACKUP } * 10 - (ix.s.projects[m.projectId]?.shootStart?.toEpochDay() ?: 0) / 1000 }
            ?: return Result.failure(RepoException("No one booked on your projects can withdraw right now."))
        return withdraw(pick.collaboratorId, pick.id, WithdrawReason.PAID_WORK, "Got a paid shoot on the same dates. Sorry for the short notice.")
    }

    // ─────────────────────────── time rules ───────────────────────────

    /** Runs every time-based rule for the current demo clock. Idempotent; safe to call often. */
    fun tick() {
        mutate {
            // seeded owners stay "active" so their roles keep passing G6 after clock jumps
            s.projects.values.filter { it.status == ProjectStatus.OPEN && it.ownerId !in s.realUsers }.map { it.ownerId }.toSet().forEach { oid ->
                val p = s.profiles[oid] ?: return@forEach
                if (now - p.lastActiveAt > 20 * HOUR) put(p.copy(lastActiveAt = now - (oid.hashCode().mod(8)) * HOUR))
            }
            // 1. closure guarantee: 14 days without an answer → EXPIRED
            s.applications.values.filter { it.status == ApplicationStatus.PENDING && now - it.createdAt > 14 * DAY }.forEach { a ->
                val r = s.roles[a.roleId] ?: return@forEach
                val p = s.projects[r.projectId] ?: return@forEach
                put(a.copy(status = ApplicationStatus.EXPIRED, declineReason = DemoText.CLOSURE_EXPIRED, decidedAt = now))
                if (a.origin == ApplicationOrigin.APPLIED) {
                    put(role(r.id).let { it.copy(applicationsCount = (it.applicationsCount - 1).coerceAtLeast(0)) })
                    notify(a.applicantId, NotificationKind.APPLICATION_CLOSED, "No answer · ${p.title}", DemoText.CLOSURE_EXPIRED, "calltime://r/${r.id}")
                }
            }
            // 2. role auto-close: on-set when the start date passes, post roles when the deadline passes
            s.roles.values.filter { it.status == RoleStatus.OPEN }.forEach { r ->
                val p = s.projects[r.projectId] ?: return@forEach
                if (p.status !in setOf(ProjectStatus.OPEN, ProjectStatus.CREWED)) return@forEach
                val passed = if (r.isPostRole) r.postDeadline?.isBefore(today) == true else p.shootStart?.isBefore(today) == true
                if (passed) closeRoleInternal(r.id, DemoText.CLOSURE_ROLE_CLOSED)
            }
            // 3. auto-wrap on-set bookings once the last shoot day has passed
            s.projects.values.filter { it.status in setOf(ProjectStatus.OPEN, ProjectStatus.CREWED) && it.shootEnd?.isBefore(today) == true }.forEach { p ->
                wrapProject(p, null)
            }
            // 4. no booking card after 72h → nudge the owner; 7 days of silence → close politely
            s.matches.values.filter { it.state == MatchState.TALKING || it.state == MatchState.OFFERED }.forEach { m ->
                val cards = s.bookingCards.values.filter { it.matchId == m.id }
                val last = listOfNotNull(m.createdAt, s.messages.filter { it.matchId == m.id }.maxOfOrNull { it.createdAt }, cards.maxOfOrNull { it.sentAt }).max()
                if (cards.isEmpty() && now - m.createdAt > 72 * HOUR && flag("nudge72:${m.id}")) {
                    notify(m.ownerId, NotificationKind.SYSTEM, "Send the booking card?", "${name(m.collaboratorId)} is waiting on ${project(m.projectId).title}.", "calltime://m/${m.id}")
                }
                if (now - last > 7 * DAY && m.state == MatchState.TALKING) {
                    put(m.copy(state = MatchState.CLOSED, endedAt = now))
                    put(app(m.applicationId).copy(status = ApplicationStatus.CLOSED, decidedAt = now))
                    system(m.id, DemoText.MATCH_AUTO_CLOSED)
                    val title = project(m.projectId).title
                    listOf(m.ownerId, m.collaboratorId).forEach { notify(it, NotificationKind.APPLICATION_CLOSED, "Talks closed · $title", DemoText.MATCH_AUTO_CLOSED, "calltime://m/${m.id}") }
                }
            }
            // 5. T-48h "Still coming?"
            s.matches.values.filter { it.state == MatchState.BOOKED && !it.isPostRole && it.collaboratorId in s.realUsers }.forEach { m ->
                val p = s.projects[m.projectId] ?: return@forEach
                s.shootDays.values.filter { it.projectId == p.id && it.cancelledAt == null }.forEach { d ->
                    val call = s.callTimes.firstOrNull { it.shootDayId == d.id && it.profileId == m.collaboratorId } ?: return@forEach
                    val at = SeedKit.millisAt(d.day, call.callTime, zone)
                    if (at > now && at - now <= 48 * HOUR && flag("still:${d.id}:${m.collaboratorId}")) {
                        notify(m.collaboratorId, NotificationKind.STILL_COMING, "Still coming? · ${p.title}",
                            "Your call is ${Dates.time(call.callTime)} on ${Dates.short(d.day)}. Confirm, or tell the production now.", "calltime://d/${p.id}/${d.id}")
                    }
                }
            }
            // 6. overdue wrap items nudge whoever owes them, once a day
            s.wrapItems.values.filter { it.status in setOf(WrapStatus.OPEN, WrapStatus.NOT_RECEIVED) && it.dueDate?.isBefore(today) == true }.forEach { w ->
                val m = s.matches[w.matchId] ?: return@forEach
                if (m.ownerId !in s.realUsers && m.collaboratorId !in s.realUsers) return@forEach
                if (m.state !in DemoIndex.BOOKED_LIKE) return@forEach
                if (!flag("overdue:${w.id}:$today")) return@forEach
                val owes = if (w.owedBy == OwedBy.PRODUCTION) m.ownerId else m.collaboratorId
                val other = if (owes == m.ownerId) m.collaboratorId else m.ownerId
                val title = project(m.projectId).title
                notify(owes, NotificationKind.WRAP_OVERDUE, "${kindLabel(w.kind).replaceFirstChar { it.uppercase() }} overdue · $title",
                    "It was due ${Dates.short(w.dueDate!!)}. Mark it done when it's sent.", "calltime://wrap/m/${m.id}")
                notify(other, NotificationKind.WRAP_OVERDUE, "${kindLabel(w.kind).replaceFirstChar { it.uppercase() }} overdue · $title",
                    "It was due ${Dates.short(w.dueDate)}. We've nudged them.", "calltime://wrap/m/${m.id}")
            }
            // 7. DELIVERY auto-confirms after 7 days of owner silence
            s.wrapItems.values.filter { it.kind == WrapKind.DELIVERY && it.status == WrapStatus.DONE && (it.markedAt ?: now) < now - 7 * DAY }.forEach { w ->
                put(w.copy(status = WrapStatus.CONFIRMED, confirmedAt = now, autoConfirmed = true))
                val m = s.matches[w.matchId] ?: return@forEach
                system(m.id, "Delivery auto-confirmed after 7 days without an answer from the production.")
                if (m.state == MatchState.BOOKED) wrapPostMatch(m, null)
            }
            // 8. feedback reveals 14 days after wrap even if only one side answered
            val toReveal = s.feedback.filter { f -> f.revealedAt == null && (s.matches[f.matchId]?.endedAt ?: Long.MAX_VALUE) + 14 * DAY <= now }
            if (toReveal.isNotEmpty()) {
                val ids = toReveal.map { it.id }.toSet()
                s = s.copy(feedback = s.feedback.map { if (it.id in ids) it.copy(revealedAt = now) else it })
            }
            // 9. reports move through review (moderation runs on the dashboard in LIVE)
            s.reports.filter { it.reporterId in s.realUsers }.forEach { row ->
                val r = row.report
                val next = when {
                    r.status == ReportStatus.RECEIVED && now - r.createdAt > 3 * HOUR -> ReportStatus.REVIEWING
                    r.status == ReportStatus.REVIEWING && now - r.createdAt > 24 * HOUR ->
                        if (r.reason in setOf(ReportReason.MONEY_REQUEST, ReportReason.SEXUAL_INAPPROPRIATE, ReportReason.UNSAFE_SET) ||
                            row.subjectProfileId in DemoIndex(s, now, zone).hidden) ReportStatus.ACTION_TAKEN else ReportStatus.NO_ACTION
                    else -> null
                } ?: return@forEach
                s = s.copy(reports = s.reports.map { if (it === row) it.copy(report = r.copy(status = next)) else it })
                notify(row.reporterId, NotificationKind.REPORT_UPDATE, "Report ${next.name.lowercase().replace('_', ' ')}", when (next) {
                    ReportStatus.REVIEWING -> "We're looking at your report now."
                    ReportStatus.ACTION_TAKEN -> "We acted on your report. Thanks for flagging it."
                    else -> "We reviewed your report and didn't find a rule broken. You can still block them."
                }, "calltime://safety")
            }
        }
    }

    companion object {
        const val DAILY_LIKES = 25
        const val DAILY_ASKS = 10
        val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        val URL = Regex("https?://\\S+")
        val CLOSED_STATES = setOf(MatchState.CLOSED, MatchState.RELEASED, MatchState.WITHDRAWN)

        fun kindLabel(k: WrapKind): String = when (k) {
            WrapKind.PAID -> "pay"
            WrapKind.FOOTAGE -> "footage"
            WrapKind.FINAL_FILM -> "final film"
            WrapKind.CREDIT -> "credit"
            WrapKind.DELIVERY -> "delivery"
        }
    }
}
