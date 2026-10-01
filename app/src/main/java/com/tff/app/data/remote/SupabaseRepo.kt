package com.tff.app.data.remote

import android.content.Context
import com.tff.app.core.AppClock
import com.tff.app.data.DemoControls
import com.tff.app.data.NotLiveYet
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.data.remote.Mappers.isUuid
import com.tff.app.data.remote.Mappers.toDto
import com.tff.app.data.remote.Mappers.toModel
import com.tff.app.domain.ApplicantInput
import com.tff.app.domain.ApplicantRanker
import com.tff.app.domain.BookingTermsFactory
import com.tff.app.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate

/**
 * LIVE mode (PRD §4.2 tier L0) on Supabase: email OTP auth, profiles (relationship-scoped reads), cities and
 * density, drafts + publish (listing_rules_ok), Gigs, apply, Crew up (applicants with server rate_fit, scout,
 * asks), inbox + chat with Realtime and server safety flags, auditions, booking cards, contacts, report, block,
 * export and delete. Gating and ranking stay on the device (MatchEngine / ApplicantRanker).
 *
 * L1 (shoot days, wrap actions, feedback, reschedule/cancel, withdraw/release) is not wired: [features] hides
 * those entry points and their actions return Result.failure(NotLiveYet). Read-only L1 flows emit empty data.
 *
 * Server contract: supabase/migrations/20261002000000_init.sql. Wire DTOs: Dtos.kt. Mapping: Mappers.kt.
 */
class SupabaseRepo(context: Context, url: String, anonKey: String) : Repo {

    private val sb: SupabaseClient = SupabaseProvider.client(url, anonKey)
    private val json: Json = SupabaseProvider.json
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.applicationContext.getSharedPreferences("calltime_live", Context.MODE_PRIVATE)
    private val signInLock = Mutex()

    override val mode: AppMode = AppMode.LIVE
    override val features: Features = Features(
        shootDays = false, wrap = false, feedback = false, reschedule = false, withdrawRelease = false, closedAppNotifications = false,
    )
    override val demo: DemoControls? = null

    private val _session = MutableStateFlow(cachedSession())
    override val session: StateFlow<Session?> = _session.asStateFlow()
    private val myProfile = MutableStateFlow<Profile?>(null)
    @Volatile private var citiesCache: List<City>? = null

    /** A change notice: from Realtime (table + match id) or from a local mutation / sign-in (table == null → everything). */
    private data class Change(val table: String?, val matchId: String? = null)
    private val changes = MutableSharedFlow<Change>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var realtimeJob: Job? = null
    private var channel: RealtimeChannel? = null
    @Volatile private var realtimeUser: String? = null

    init {
        // Session restore + every later auth change (sign-in, refresh, sign-out).
        scope.launch {
            sb.auth.sessionStatus.collect { st ->
                when (st) {
                    is SessionStatus.Authenticated -> runCatching { onSignedIn(st.session.user?.id, st.session.user?.email) }
                    is SessionStatus.NotAuthenticated -> onSignedOut()
                    else -> Unit      // Initializing / RefreshFailure (offline): keep the cached session
                }
            }
        }
    }

    // ───────────────────────── plumbing ─────────────────────────

    private fun uid(): String = _session.value?.userId ?: throw RepoException("Sign in first.", "AUTH")

    private fun changed(table: String? = null, matchId: String? = null) { changes.tryEmit(Change(table, matchId)) }

    private suspend fun rpcRaw(fn: String, params: JsonObject = JsonObject(emptyMap())): String =
        sb.postgrest.rpc(fn, params).data

    private suspend fun <T> rpc(fn: String, ser: KSerializer<T>, params: JsonObjectBuilder.() -> Unit = {}): T {
        val body = rpcRaw(fn, buildJsonObject(params)).ifBlank { "null" }
        return json.decodeFromString(ser, body)
    }

    private suspend fun rpcUnit(fn: String, params: JsonObjectBuilder.() -> Unit = {}) { rpcRaw(fn, buildJsonObject(params)) }

    private fun <T> enc(ser: KSerializer<T>, v: T): JsonElement = json.encodeToJsonElement(ser, v)

    private fun <T> list(ser: KSerializer<T>, data: String): List<T> = json.decodeFromString(ListSerializer(ser), data.ifBlank { "[]" })

    /** Runs a read; errors → RepoException. */
    private suspend fun <T> read(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.toRepoException())
    }

    /** Runs a mutation; on success every live flow refreshes. */
    private suspend fun <T> mutate(block: suspend () -> T): Result<T> = read(block).onSuccess { changed() }

    /**
     * A refreshing Flow: fetches now, then again on matching Realtime events, local mutations and (optionally) a
     * poll. It never throws (some collectors, e.g. the bottom-nav inbox badge, have no catch): until the first
     * success it retries with backoff (the screen stays in Loading), after that a failed refresh keeps the last
     * good value and the next trigger tries again.
     */
    private fun <T> live(tables: Set<String>, matchId: String? = null, pollMs: Long = 0, fetch: suspend () -> T): Flow<T> = flow {
        var emitted = false
        val poll: Flow<Change> = if (pollMs > 0) flow { while (true) { delay(pollMs); emit(Change(null)) } } else emptyFlow()
        val triggers = merge(
            flowOf(Change(null)),
            changes.filter { c -> c.table == null || (c.table in tables && (matchId == null || c.matchId == null || c.matchId == matchId)) },
            poll,
        ).conflate()
        triggers.collect {
            var attempt = 0
            while (true) {
                val r = try { Result.success(fetch()) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }
                if (r.isSuccess) { emit(r.getOrThrow()); emitted = true; break }
                if (emitted) break
                delay(minOf(30_000L, 2_000L shl minOf(attempt++, 4)))       // 2s, 4s, 8s, 16s, 30s …
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun Throwable.toRepoException(): Exception = when (this) {
        is RepoException, is NotLiveYet -> this as Exception
        is PostgrestRestException -> when (code) {
            "P0001" -> RepoException(error, (details as? JsonPrimitive)?.contentOrNull)
            "42501" -> RepoException("You can't do that here.", "FORBIDDEN")
            "23505" -> RepoException("That's already done.", "DUPLICATE")
            "23514", "22P02", "22023" -> RepoException("Some details aren't valid. Check the form and try again.", code)
            else -> RepoException("Something went wrong on our side. Try again.", code)
        }
        is AuthRestException -> RepoException(
            when (errorCode) {
                AuthErrorCode.OtpExpired -> "That code is wrong or has expired. Check it, or send a new one."
                AuthErrorCode.OverEmailSendRateLimit -> "Too many codes sent. Wait a minute, then try again."
                AuthErrorCode.EmailAddressInvalid, AuthErrorCode.ValidationFailed -> "Check the email address and try again."
                else -> if (statusCode == 429) "Too many tries. Wait a minute, then try again."
                        else "Couldn't sign you in. Check the code, or send a new one."
            }, errorCode?.value,
        )
        is HttpRequestException, is java.io.IOException -> RepoException("No connection. Check your internet and try again.", "NETWORK")
        else -> RepoException("Something went wrong. Try again.", this::class.simpleName)
    }

    // ───────────────────────── session ─────────────────────────

    private fun cachedSession(): Session? {
        val id = prefs.getString("user_id", null) ?: return null
        return Session(userId = id, email = prefs.getString("email", null), persona = null, hasProfile = prefs.getBoolean("has_profile", false))
    }

    private fun publish(s: Session?) {
        _session.value = s
        prefs.edit().apply {
            if (s == null) clear() else putString("user_id", s.userId).putString("email", s.email).putBoolean("has_profile", s.hasProfile)
        }.apply()
    }

    private suspend fun onSignedIn(userId: String?, email: String?): Session? = signInLock.withLock {
        if (userId == null) return@withLock _session.value
        val prev = _session.value
        val fetched = read { rpc("get_my_profile", ProfileDto.serializer().nullable) }
        val profile = fetched.getOrNull()?.toModel()
        val hasProfile = when {
            fetched.isSuccess -> profile != null
            else -> prev?.userId == userId && prev.hasProfile          // offline: trust the cache
        }
        if (fetched.isSuccess || prev?.userId != userId) myProfile.value = profile
        val s = Session(userId = userId, email = email ?: prev?.email, persona = null, hasProfile = hasProfile)
        val userChanged = prev?.userId != userId || prev.hasProfile != hasProfile
        publish(s)
        if (realtimeUser != userId) startRealtime(userId)
        if (userChanged) changed()
        if (hasProfile) runCatching { rpcUnit("touch_active") }
        s
    }

    private suspend fun onSignedOut() {
        stopRealtime()
        myProfile.value = null
        if (_session.value != null) { publish(null); changed() }
    }

    /** One channel per signed-in user. RLS decides which rows each listener actually receives. */
    private suspend fun startRealtime(userId: String) {
        stopRealtime()
        val ch = sb.channel("calltime-$userId")
        val tables = listOf("messages", "booking_cards", "applications", "notifications")
        val flows = tables.map { t ->
            t to ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = t
                if (t == "notifications") filter("user_id", FilterOperator.EQ, userId)
            }
        }
        realtimeJob = scope.launch {
            flows.forEach { (t, f) ->
                launch {
                    f.collect { a ->
                        val rec = when (a) {
                            is PostgresAction.Insert -> a.record
                            is PostgresAction.Update -> a.record
                            is PostgresAction.Delete -> a.oldRecord
                            else -> null
                        }
                        changed(t, rec?.get("match_id")?.jsonPrimitive?.contentOrNull)
                    }
                }
            }
        }
        runCatching { ch.subscribe() }
        channel = ch
        realtimeUser = userId
    }

    private suspend fun stopRealtime() {
        realtimeJob?.cancel(); realtimeJob = null
        channel?.let { runCatching { sb.realtime.removeChannel(it) } }
        channel = null; realtimeUser = null
    }

    // ───────────────────────── Auth & profile ─────────────────────────

    override suspend fun sendOtp(email: String): Result<Unit> = read {
        val e = email.trim()
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(e)) throw RepoException("Check the email address and try again.", "EMAIL")
        sb.auth.signInWith(OTP) { this.email = e; createUser = true }
    }

    override suspend fun verifyOtp(email: String, code: String): Result<Session> = read {
        sb.auth.verifyEmailOtp(OtpType.Email.EMAIL, email.trim(), code.trim())
        val user = sb.auth.currentUserOrNull() ?: throw RepoException("Couldn't sign you in. Send a new code.", "NO_SESSION")
        onSignedIn(user.id, user.email ?: email.trim()) ?: throw RepoException("Couldn't sign you in. Send a new code.", "NO_SESSION")
    }

    override suspend fun startDemo(persona: DemoPersona?): Session =
        throw RepoException("Demo personas are only available in demo mode. Turn on \"Use demo mode\" in Settings.", "LIVE_MODE")

    override suspend fun signOut() {
        runCatching { sb.auth.signOut() }
        runCatching { sb.auth.clearSession() }
        onSignedOut()
    }

    override fun me(): Flow<Profile?> = myProfile.asStateFlow()

    override suspend fun myPrivateMinRate(): Int? =
        read { rpc("my_private_min_rate", Int.serializer().nullable) }.getOrNull()

    override suspend fun saveProfile(p: Profile, privateMinRate: Int?): Result<Profile> = mutate {
        val me = uid()
        val saved = rpc("save_profile", ProfileDto.serializer()) {
            put("p_profile", enc(ProfileDto.serializer(), p.copy(id = me).toDto()))
            put("p_min_rate", privateMinRate)
        }.toModel()
        myProfile.value = saved
        _session.value?.let { if (!it.hasProfile) publish(it.copy(hasProfile = true)) }
        saved
    }

    override suspend fun profile(id: String): ProfileCard? {
        if (!isUuid(id)) return null
        return read { rpc("get_profile", CardDto.serializer().nullable) { put("p_profile", id) } }.getOrNull()?.toModel()
    }

    override suspend fun cities(): List<City> {
        citiesCache?.let { return it }
        return read { rpc("get_cities", ListSerializer(CityDto.serializer())).map { it.toModel() } }
            .getOrElse { throw it }
            .also { citiesCache = it }
    }

    override fun density(cityId: String): Flow<Density> = live(emptySet(), pollMs = 5 * 60_000L) {
        rpc("city_density", DensityDto.serializer().nullable) { put("p_city", cityId) }?.toModel()
            ?: Density(cityId, cityId, isLive = false, isEarly = false, people = 0, openProjects = 0)
    }

    // ───────────────────────── Gigs ─────────────────────────

    override suspend fun discoverCandidates(cityId: String?): List<RoleWithProject> =
        read {
            rpc("discover_roles", ListSerializer(RwpDto.serializer())) {
                put("p_city", cityId)
                put("p_include_remote", true)
            }.map { it.toModel() }
        }.getOrElse { throw it }

    override suspend fun roleWithProject(roleId: String): RoleWithProject? {
        if (!isUuid(roleId)) return null
        return read { rpc("get_role", RwpDto.serializer().nullable) { put("p_role", roleId) } }.getOrNull()?.toModel()
    }

    override suspend fun swipeRole(roleId: String, dir: SwipeDir): Result<Unit> = mutate {
        rpcUnit("swipe_role") { put("p_role", roleId); put("p_dir", dir.name) }
    }

    override suspend fun undoSwipe(roleId: String): Result<Unit> = mutate { rpcUnit("undo_swipe") { put("p_role", roleId) } }

    override suspend fun apply(roleId: String, note: String?, workLinkId: String?, answer: String?, kit: Boolean): Result<Application> = mutate {
        rpc("apply_to_role", ApplicationDto.serializer()) {
            put("p_role", roleId)
            put("p_note", note)
            put("p_link", workLinkId?.takeIf { isUuid(it) })
            put("p_answer", answer)
            put("p_kit", kit)
        }.toModel()
    }

    override fun likesLeftToday(): Flow<Int> = live(setOf("applications")) {
        if (_session.value == null) 25 else rpc("likes_left_today", Int.serializer())
    }

    override fun saved(): Flow<List<RoleWithProject>> = live(emptySet()) {
        if (_session.value == null) emptyList()
        else rpc("saved_roles", ListSerializer(RwpDto.serializer())).map { it.toModel() }
    }

    override fun myApplications(): Flow<List<Application>> = live(setOf("applications")) {
        val me = _session.value?.userId ?: return@live emptyList()
        list(ApplicationDto.serializer(), sb.from("applications").select {
            filter { eq("applicant_id", me) }
            order("created_at", Order.DESCENDING)
        }.data).map { it.toModel() }
    }

    // ───────────────────────── Projects (owner) ─────────────────────────

    override fun myProjects(): Flow<List<ProjectSummary>> = live(setOf("applications", "booking_cards")) {
        if (_session.value == null) emptyList()
        else rpc("my_projects", ListSerializer(ProjectSummaryDto.serializer())).map { it.toModel() }
    }

    override fun joinedProjects(): Flow<List<ProjectSummary>> = live(setOf("booking_cards")) {
        if (_session.value == null) emptyList()
        else rpc("joined_projects", ListSerializer(ProjectSummaryDto.serializer())).map { it.toModel() }
    }

    override fun project(id: String): Flow<ProjectFull?> = live(setOf("applications", "booking_cards")) {
        if (!isUuid(id)) return@live null
        val dto = rpc("get_project_full", ProjectFullDto.serializer().nullable) { put("p_project", id) } ?: return@live null
        val project = dto.project.toModel()
        val roles = dto.roles.map { it.toModel() }
        val owner = dto.owner.toModel()
        val backups = dto.backups.mapValues { (roleId, rows) ->
            val role = roles.firstOrNull { it.id == roleId } ?: return@mapValues emptyList()
            val item = RoleWithProject(role, project, owner, dto.cityName, dto.areaName, dto.areaLat, dto.areaLng,
                                       paidRoles = dto.paidRoles, totalRoles = dto.totalRoles)
            scoreInOrder(item, rows)          // keep backup rank order (#1 first)
        }
        ProjectFull(
            project = project, roles = roles, owner = owner, isOwner = dto.isOwner,
            team = dto.team.map { TeamEntry(it.match.toModel(), it.person.toModel(), it.roleCode, null, it.wrapDone, it.wrapTotal) },
            backups = backups, myMatch = dto.myMatch?.toModel(), paidRoles = dto.paidRoles, totalRoles = dto.totalRoles,
            cityName = dto.cityName, areaName = dto.areaName,
        )
    }

    override suspend fun draft(projectId: String): ProjectDraft? {
        if (!isUuid(projectId)) return null
        return read { rpc("get_project_draft", DraftDto.serializer().nullable) { put("p_project", projectId) } }.getOrNull()?.toModel()
    }

    override suspend fun saveDraft(d: ProjectDraft): Result<String> = mutate {
        val me = uid()
        rpc("save_project_draft", String.serializer()) {
            put("p_project", enc(ProjectDto.serializer(), d.project.copy(ownerId = me, payerId = me).toDto()))
            put("p_roles", enc(ListSerializer(RoleDto.serializer()), d.roles.map { it.toDto() }))
        }
    }

    override suspend fun publish(projectId: String): Result<Unit> = mutate {
        rpcUnit("publish_project") { put("p_project", projectId) }
    }

    override suspend fun reschedule(projectId: String, newDates: List<LocalDate>): Result<Unit> = Result.failure(NotLiveYet("Reschedule"))
    override suspend fun cancelProject(projectId: String, reason: String): Result<Unit> = Result.failure(NotLiveYet("Cancel project"))

    override suspend fun closeRole(roleId: String): Result<Unit> = mutate { rpcUnit("close_role") { put("p_role", roleId) } }

    // ───────────────────────── Crew up ─────────────────────────

    private fun ApplicantRowDto.toInput() = ApplicantInput(
        application = application?.toModel(),
        card = card.toModel(),
        rateFit = rateFit,
        // The server compares this answer with the person's other answers (they never leave the server);
        // a near-duplicate is passed as itself so ApplicantRanker's similarity check fires.
        otherAnswers = if (answerDuplicate) listOfNotNull(application?.screeningAnswer) else emptyList(),
        headshotUrl = headshotUrl,
    )

    private fun ownerLevel(): Level = myProfile.value?.level ?: Level.CREDITED_INDIE

    /** Unranked fallback (keeps the screen usable if the ranker throws). */
    private fun plainView(item: RoleWithProject, i: ApplicantInput): ApplicantView {
        val days = item.project.shootDates
        return ApplicantView(i.application, i.card, 0.0, emptyList(), i.rateFit,
                             availDays = days.count { it !in i.card.profile.busyDates }, totalDays = days.size, headshotUrl = i.headshotUrl)
    }

    private fun rank(item: RoleWithProject, rows: List<ApplicantRowDto>): List<ApplicantView> {
        val inputs = rows.map { it.toInput() }
        return runCatching { ApplicantRanker.rank(item, inputs, ownerLevel(), AppClock.today()) }
            .getOrElse { inputs.map { plainView(item, it) } }
    }

    private fun scoreInOrder(item: RoleWithProject, rows: List<ApplicantRowDto>): List<ApplicantView> {
        val ranked = rank(item, rows).associateBy { it.card.profile.id }
        return rows.mapNotNull { ranked[it.card.profile.id] }
    }

    override fun applicants(roleId: String): Flow<List<ApplicantView>> = live(setOf("applications")) {
        val item = roleWithProject(roleId) ?: return@live emptyList()
        val rows = rpc("get_applicants", ListSerializer(ApplicantRowDto.serializer())) { put("p_role", roleId) }
            .filter { it.application?.status == ApplicationStatus.PENDING && it.application.origin == ApplicationOrigin.APPLIED }
        rank(item, rows)
    }

    override suspend fun decide(applicationId: String, d: Decision, reason: String?): Result<Match?> = mutate {
        rpc("decide_application", MatchDto.serializer().nullable) {
            put("p_app", applicationId); put("p_decision", d.name); put("p_reason", reason)
        }?.toModel()
    }

    override suspend fun scout(roleId: String): List<ApplicantView> {
        val item = roleWithProject(roleId) ?: return emptyList()
        if (item.role.isCast) return emptyList()           // actors are never scouted
        val rows = read { rpc("scout_candidates", ListSerializer(ApplicantRowDto.serializer())) { put("p_role", roleId) } }
            .getOrElse { if ((it as? RepoException)?.code == "CAST_SCOUT") emptyList() else throw it }
        return rank(item, rows)
    }

    override suspend fun askCheck(roleId: String, profileId: String): String? =
        read { rpc("ask_check", String.serializer().nullable) { put("p_role", roleId); put("p_profile", profileId) } }
            .getOrElse { (it as? RepoException)?.userMessage ?: "Couldn't check right now. Try again." }

    override suspend fun sendAsk(roleId: String, profileId: String, note: String?): Result<Unit> = mutate {
        rpcUnit("send_ask") { put("p_role", roleId); put("p_profile", profileId); put("p_note", note) }
    }

    override fun asksLeftToday(projectId: String): Flow<Int> = live(setOf("applications")) {
        if (!isUuid(projectId) || _session.value == null) 10
        else rpc("asks_left_today", Int.serializer()) { put("p_project", projectId) }
    }

    override suspend fun respondAsk(applicationId: String, accept: Boolean, reason: AskDeclineReason?): Result<Match?> = mutate {
        rpc("respond_ask", MatchDto.serializer().nullable) {
            put("p_app", applicationId); put("p_accept", accept); put("p_reason", reason?.name)
        }?.toModel()
    }

    override suspend fun offerSlotToBackup(roleId: String): Result<Unit> = Result.failure(NotLiveYet("Offer slot to backup"))

    // ───────────────────────── Inbox / chat / booking ─────────────────────────

    override fun inbox(): Flow<Inbox> = live(setOf("messages", "notifications", "applications", "booking_cards"), pollMs = 60_000L) {
        if (_session.value == null) Inbox() else rpc("get_inbox", InboxDto.serializer()).toModel()
    }

    override fun matchView(matchId: String): Flow<MatchView?> = live(setOf("messages", "booking_cards"), matchId) {
        if (!isUuid(matchId)) return@live null
        val dto = rpc("get_match_view", MatchViewDto.serializer().nullable) { put("p_match", matchId) } ?: return@live null
        MatchView(
            match = dto.match.toModel(), item = dto.item.toModel(), counterpart = dto.counterpart.toModel(), isOwner = dto.isOwner,
            currentCard = dto.currentCard?.toModel(), acceptedCard = dto.acceptedCard?.toModel(),
        )
    }

    override fun messages(matchId: String): Flow<List<Message>> = live(setOf("messages"), matchId, pollMs = 60_000L) {
        if (!isUuid(matchId)) return@live emptyList()
        list(MessageDto.serializer(), sb.from("messages").select {
            filter { eq("match_id", matchId) }
            order("created_at", Order.ASCENDING)
        }.data).map { it.toModel(json) }
    }

    override suspend fun sendMessage(matchId: String, body: String): Result<Unit> = mutate {
        val text = body.trim()
        if (text.isEmpty()) throw RepoException("Type a message first.", "EMPTY")
        if (text.length > 2000) throw RepoException("Keep messages under 2,000 characters.", "TOO_LONG")
        sb.from("messages").insert(MessageInsertDto(matchId = matchId, senderId = uid(), body = text))
        Unit
    }

    override suspend fun markRead(matchId: String) {
        if (isUuid(matchId)) read { rpcUnit("mark_read") { put("p_match", matchId) } }.onSuccess { changed("messages", matchId) }
    }

    override suspend fun sendAuditionRequest(matchId: String, req: AuditionRequest): Result<Unit> = mutate {
        rpcUnit("send_audition_request") { put("p_match", matchId); put("p_payload", enc(AuditionRequestDto.serializer(), req.toDto())) }
    }

    override suspend fun respondAudition(messageId: String, response: AuditionResponse, tapeUrl: String?): Result<Unit> = mutate {
        rpcUnit("respond_audition") { put("p_message", messageId); put("p_response", response.name); put("p_tape_url", tapeUrl) }
    }

    override fun bookingCards(matchId: String): Flow<List<BookingCard>> = live(setOf("booking_cards"), matchId) {
        if (!isUuid(matchId)) return@live emptyList()
        list(BookingCardDto.serializer(), sb.from("booking_cards").select {
            filter { eq("match_id", matchId) }
            order("version", Order.ASCENDING)
        }.data).map { it.toModel() }
    }

    override suspend fun draftBookingTerms(matchId: String): BookingTerms {
        val mv = read { rpc("get_match_view", MatchViewDto.serializer().nullable) { put("p_match", matchId) } }
            .getOrElse { throw it } ?: throw RepoException("Conversation not found.", "NOT_FOUND")
        mv.currentCard?.let { return it.terms.toModel() }
        val item = mv.item.toModel()
        val payerName = myProfile.value?.shortName ?: item.owner.shortName
        return runCatching { BookingTermsFactory.fromRole(item, payerName) }.getOrElse { basicTerms(item, payerName) }
    }

    override suspend fun sendBookingCard(matchId: String, terms: BookingTerms): Result<Unit> = mutate {
        val t = runCatching { BookingTermsFactory.normalize(terms) }.getOrDefault(terms)
        rpcUnit("send_booking_card") { put("p_match", matchId); put("p_terms", enc(BookingTermsDto.serializer(), t.toDto())) }
    }

    override suspend fun respondBookingCard(cardId: String, accept: Boolean): Result<Unit> = mutate {
        rpcUnit("respond_booking_card") { put("p_card", cardId); put("p_accept", accept) }
    }

    override suspend fun withdraw(matchId: String, reason: WithdrawReason, note: String?): Result<String?> = Result.failure(NotLiveYet("Withdraw"))
    override suspend fun release(matchId: String, reason: ReleaseReason): Result<Unit> = Result.failure(NotLiveYet("Release"))

    override suspend fun shareContacts(matchId: String): Result<Unit> = mutate { rpcUnit("share_contacts") { put("p_match", matchId) } }

    // ───────────────────────── Shoot & wrap (L1: read-only / not live yet) ─────────────────────────

    override fun shootDays(projectId: String): Flow<List<ShootDayFull>> = flowOf(emptyList())
    override suspend fun publishShootDay(d: ShootDayDraft): Result<Unit> = Result.failure(NotLiveYet("Day sheets"))
    override suspend fun ackShootDay(shootDayId: String): Result<Unit> = Result.failure(NotLiveYet("Day sheets"))
    override suspend fun nudge(shootDayId: String, profileId: String): Result<Unit> = Result.failure(NotLiveYet("Day sheets"))
    override suspend fun setAttendance(shootDayId: String, status: Attendance, profileId: String?): Result<Unit> = Result.failure(NotLiveYet("Attendance"))
    override suspend fun markWrapped(projectId: String): Result<Unit> = Result.failure(NotLiveYet("Wrap"))

    /** Read-only in L0: items are created from the accepted booking card's deliverables. */
    override fun wrapItems(matchId: String): Flow<List<WrapItem>> = live(setOf("booking_cards"), matchId) {
        if (!isUuid(matchId)) return@live emptyList()
        list(WrapItemDto.serializer(), sb.from("wrap_items").select {
            filter { eq("match_id", matchId) }
            order("due_date", Order.ASCENDING)
        }.data).map { it.toModel() }
    }

    override fun projectWrap(projectId: String): Flow<List<WrapGroup>> = flowOf(emptyList())
    override suspend fun markWrapItem(itemId: String, link: String?, note: String?): Result<Unit> = Result.failure(NotLiveYet("Wrap checklist"))
    override suspend fun confirmWrapItem(itemId: String, received: Boolean): Result<Unit> = Result.failure(NotLiveYet("Wrap checklist"))
    override fun feedbackState(matchId: String): Flow<FeedbackState> =
        flowOf(FeedbackState(unlocked = false, iSubmitted = false, otherSubmitted = false, revealed = false))
    override suspend fun submitFeedback(matchId: String, answers: FeedbackAnswers): Result<Unit> = Result.failure(NotLiveYet("Feedback"))

    override suspend fun reputation(profileId: String): Reputation {
        if (!isUuid(profileId)) return Reputation(profileId)
        return read { rpc("get_reputation", ReputationDto.serializer().nullable) { put("p_profile", profileId) } }
            .getOrNull()?.toModel() ?: Reputation(profileId)
    }

    override fun keepList(): Flow<List<ProfileCard>> = live(emptySet()) {
        if (_session.value == null) emptyList() else rpc("keep_list", ListSerializer(CardDto.serializer())).map { it.toModel() }
    }

    // ───────────────────────── Safety & account ─────────────────────────

    override suspend fun report(kind: ReportTarget, targetId: String, reason: ReportReason, details: String?): Result<Unit> = mutate {
        if (!isUuid(targetId)) throw RepoException("Couldn't report this item. Try again from the message or profile.", "BAD_TARGET")
        if ((details?.length ?: 0) > 500) throw RepoException("Keep details under 500 characters.", "TOO_LONG")
        sb.from("reports").insert(ReportInsertDto(uid(), kind, targetId, reason, details?.trim()?.ifEmpty { null }))
        Unit
    }

    override fun myReports(): Flow<List<Report>> = live(emptySet()) {
        val me = _session.value?.userId ?: return@live emptyList()
        list(ReportDto.serializer(), sb.from("reports").select {
            filter { eq("reporter_id", me) }
            order("created_at", Order.DESCENDING)
        }.data).map { it.toModel() }
    }

    override suspend fun block(profileId: String): Result<Unit> = mutate { rpcUnit("block_user") { put("p_profile", profileId) } }
    override suspend fun unblock(profileId: String): Result<Unit> = mutate { rpcUnit("unblock_user") { put("p_profile", profileId) } }

    override fun blocked(): Flow<List<ProfileCard>> = live(emptySet()) {
        if (_session.value == null) emptyList() else rpc("my_blocked", ListSerializer(CardDto.serializer())).map { it.toModel() }
    }

    override fun notifications(): Flow<List<AppNotification>> = live(setOf("notifications"), pollMs = 60_000L) {
        val me = _session.value?.userId ?: return@live emptyList()
        list(NotificationDto.serializer(), sb.from("notifications").select {
            filter { eq("user_id", me) }
            order("created_at", Order.DESCENDING)
            limit(100)
        }.data).map { it.toModel() }
    }

    override suspend fun markNotificationRead(id: String) {
        if (!isUuid(id)) return
        read {
            sb.from("notifications").update({ set("read_at", Instant.ofEpochMilli(System.currentTimeMillis()).toString()) }) {
                filter { eq("id", id) }
            }
        }.onSuccess { changed("notifications") }
    }

    override suspend fun exportMyData(): String {
        val raw = read { rpcRaw("export_my_data") }.getOrElse { throw it }
        return runCatching {
            Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), json.parseToJsonElement(raw))
        }.getOrDefault(raw)
    }

    override suspend fun deleteAccount(): Result<Unit> = mutate {
        rpcUnit("delete_my_account")
        runCatching { sb.auth.clearSession() }
        onSignedOut()
    }

    override suspend fun log(event: String, props: Map<String, String>) {
        val me = _session.value?.userId ?: return
        runCatching { sb.from("events").insert(EventInsertDto(me, event.take(80), props)) }
    }

    // ───────────────────────── fallbacks ─────────────────────────

    /** Minimal BookingTermsFactory stand-in, used only if the domain factory throws. */
    private fun basicTerms(item: RoleWithProject, payerName: String): BookingTerms {
        val r = item.role
        val p = item.project
        val last = p.shootDates.maxOrNull()
        val termsDays = when (r.paymentTerms) {
            PaymentTerms.WITHIN_7_DAYS -> 7L; PaymentTerms.WITHIN_15_DAYS -> 15L; PaymentTerms.WITHIN_30_DAYS -> 30L; else -> 0L
        }
        val base = if (r.isPostRole) r.postDeadline else last
        val payBy = if (r.compType == CompType.UNPAID) null else base?.plusDays(termsDays)
        val deliverables = buildList {
            add(Deliverable(WrapKind.CREDIT, base, OwedBy.PRODUCTION))
            if (r.compType != CompType.UNPAID) add(Deliverable(WrapKind.PAID, payBy, OwedBy.PRODUCTION))
            if (r.isPostRole) add(Deliverable(WrapKind.DELIVERY, r.postDeadline, OwedBy.COLLABORATOR))
            else if (Perk.FOOTAGE_BY_DATE in r.perks) add(Deliverable(WrapKind.FOOTAGE, last?.plusWeeks((r.footageDueWeeks ?: 8).toLong()), OwedBy.PRODUCTION))
            if (Perk.FINAL_FILM_BY_DATE in r.perks) add(Deliverable(WrapKind.FINAL_FILM, base?.plusWeeks((r.finalFilmDueWeeks ?: 26).toLong()), OwedBy.PRODUCTION))
        }
        return BookingTerms(
            roleCode = r.roleCode, creditTitle = r.creditTitle, projectTitle = p.title, isPostRole = r.isPostRole,
            dates = if (r.isPostRole) emptyList() else p.shootDates, deliveryDate = if (r.isPostRole) r.postDeadline else null,
            pictureLockDate = if (r.roleCode == RoleCode.COMPOSER || r.roleCode == RoleCode.SOUND_DESIGNER) p.pictureLockDate else null,
            compType = r.compType, amount = r.amountMax ?: r.amountMin, currency = r.currency, kitFeeDay = r.kitFeeDay,
            paymentTerms = r.paymentTerms, payByDate = payBy, perks = r.perks, meals = p.meals, transport = p.transport,
            travelAllowancePerDay = p.travelAllowancePerDay, footageDueWeeks = r.footageDueWeeks, finalFilmDueWeeks = r.finalFilmDueWeeks,
            deliverables = deliverables, payerId = p.payerId, payerName = payerName,
            noticePledge = if (r.isPostRole) "If you can't deliver by the date, tell the production at least 7 days before."
                           else "If you can't make it, tell the production at least 72 hours before.",
        )
    }
}
