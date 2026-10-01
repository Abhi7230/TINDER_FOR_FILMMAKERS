package com.tff.app.data.remote

import com.tff.app.data.remote.Mappers.toDto
import com.tff.app.data.remote.Mappers.toModel
import com.tff.app.model.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Wire-format contract test for LIVE mode: encodes app models with Mappers/DTOs, sends them to the local
 * Supabase stack exactly like SupabaseRepo does (PostgREST RPC + table reads with user JWTs), and decodes every
 * response back into DTOs → models. Catches any drift between Dtos.kt and supabase/migrations/20261002000000_init.sql.
 *
 * Skips itself when no local stack answers on http://127.0.0.1:54321 (override with SUPABASE_URL /
 * SUPABASE_ANON_KEY / SUPABASE_SERVICE_ROLE_KEY). Uses throwaway users e2e-calltime-kt-*@calltime.test, deleted after.
 */
class LiveWireTest {

    companion object {
        private val URL_BASE = (System.getenv("SUPABASE_URL") ?: "http://127.0.0.1:54321").trimEnd('/')
        private val ANON = System.getenv("SUPABASE_ANON_KEY")
            ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
        private val SERVICE = System.getenv("SUPABASE_SERVICE_ROLE_KEY")
            ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU"
        private val json = SupabaseProvider.json
        private val createdUsers = mutableListOf<String>()
        private var reachable = false

        data class Resp(val code: Int, val body: String)

        fun http(method: String, path: String, token: String? = null, key: String = ANON, body: String? = null, prefer: String? = null): Resp {
            if (method == "PATCH") return patch(path, token ?: key, key, body ?: "{}", prefer)
            val c = URL(URL_BASE + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.setRequestProperty("apikey", key)
            c.setRequestProperty("Authorization", "Bearer ${token ?: key}")
            c.setRequestProperty("Content-Type", "application/json")
            prefer?.let { c.setRequestProperty("Prefer", it) }
            c.connectTimeout = 3000; c.readTimeout = 20000
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
            return Resp(code, text)
        }

        /** HttpURLConnection can't PATCH; use the app's own Ktor + OkHttp stack. */
        private fun patch(path: String, token: String, key: String, body: String, prefer: String?): Resp = runBlocking {
            HttpClient(OkHttp).use { c ->
                val r = c.patch(URL_BASE + path) {
                    header("apikey", key); header("Authorization", "Bearer $token")
                    prefer?.let { header("Prefer", it) }
                    contentType(ContentType.Application.Json); setBody(body)
                }
                Resp(r.status.value, r.bodyAsText())
            }
        }

        @JvmStatic @BeforeClass fun ping() {
            reachable = runCatching { http("GET", "/auth/v1/health").code == 200 }.getOrDefault(false)
        }

        @JvmStatic @AfterClass fun cleanup() {
            createdUsers.forEach { runCatching { http("DELETE", "/auth/v1/admin/users/$it", SERVICE, SERVICE) } }
        }
    }

    private class User(val id: String, val token: String, val email: String)

    private fun user(tag: String): User {
        val email = "e2e-calltime-kt-$tag-${UUID.randomUUID().toString().take(6)}@calltime.test"
        val r = http("POST", "/auth/v1/admin/users", SERVICE, SERVICE, """{"email":"$email","password":"Calltime-kt-1","email_confirm":true}""")
        assertTrue("create user: ${r.body}", r.code < 300)
        val id = json.parseToJsonElement(r.body).jsonObject["id"]!!.jsonPrimitive.content
        createdUsers += id
        val t = http("POST", "/auth/v1/token?grant_type=password", body = """{"email":"$email","password":"Calltime-kt-1"}""")
        assertTrue("sign in: ${t.body}", t.code < 300)
        return User(id, json.parseToJsonElement(t.body).jsonObject["access_token"]!!.jsonPrimitive.content, email)
    }

    private fun <T> rpc(u: User?, fn: String, ser: KSerializer<T>, params: JsonObjectBuilder.() -> Unit = {}): T {
        val r = http("POST", "/rest/v1/rpc/$fn", u?.token, body = buildJsonObject(params).toString())
        assertTrue("$fn → ${r.code} ${r.body}", r.code < 300)
        return json.decodeFromString(ser, r.body.ifBlank { "null" })
    }

    private fun <T> select(u: User, table: String, query: String, ser: KSerializer<T>): List<T> {
        val r = http("GET", "/rest/v1/$table?$query", u.token)
        assertTrue("$table → ${r.code} ${r.body}", r.code < 300)
        return json.decodeFromString(ListSerializer(ser), r.body)
    }

    private fun <T> insert(u: User, table: String, ser: KSerializer<T>, v: T) {
        val r = http("POST", "/rest/v1/$table", u.token, body = json.encodeToString(ser, v), prefer = "return=minimal")
        assertTrue("insert $table → ${r.code} ${r.body}", r.code < 300)
    }

    private fun <T> enc(ser: KSerializer<T>, v: T): JsonElement = json.encodeToJsonElement(ser, v)

    @Test fun wholeL0LoopRoundTripsThroughDtos() {
        assumeTrue("local Supabase not running at $URL_BASE — skipped", reachable)
        val owner = user("owner"); val crew = user("crew")
        val today = LocalDate.now()
        val d1 = today.plusDays(14); val d2 = today.plusDays(15)

        // cities + density
        val cities = rpc(null, "get_cities", ListSerializer(CityDto.serializer())).map { it.toModel() }
        assertEquals(20, cities.size)
        assertEquals(67, cities.sumOf { it.areas.size })
        assertTrue(cities.first().isLive)
        assertTrue(cities.first { it.id == "indore" }.isEarly)
        rpc(null, "city_density", DensityDto.serializer()) { put("p_city", "mumbai") }.toModel()

        // profiles: model → DTO → server → DTO → model
        val ownerModel = Profile(id = "", displayName = "Rohan Kulkarni", primaryRole = RoleCode.DIRECTOR, level = Level.CREDITED_INDIE,
            cityId = "mumbai", areaId = "mumbai_versova", languages = listOf("hi", "en"), adultConfirmed = true)
        val savedOwner = rpc(owner, "save_profile", ProfileDto.serializer()) {
            put("p_profile", enc(ProfileDto.serializer(), ownerModel.copy(id = owner.id).toDto())); put("p_min_rate", null as Int?)
        }.toModel()
        assertEquals("Rohan K.", savedOwner.shortName)
        assertTrue(savedOwner.emailVerified)

        val crewModel = Profile(id = crew.id, displayName = "Meera Kapoor", primaryRole = RoleCode.SOUND_MIXER, level = Level.BUILDING_REEL,
            cityId = "mumbai", areaId = "mumbai_andheri_west", compMode = CompMode.OPEN_TO_UNPAID,
            secondaryRoles = listOf(SecondaryRole(RoleCode.ACTOR, 2)), stepUpRoles = listOf(RoleCode.BOOM_OP),
            busyDates = setOf(d2), unpaidMustHaves = listOf(Perk.MEALS), goals = listOf(Goal.REEL_FOOTAGE),
            contentWontDo = listOf(ContentFlag.GORE), formatsPref = listOf(Format.SHORT), tasteFilms = listOf("Court"),
            workLinks = listOf(WorkLink("wl_local1", "https://youtu.be/reel", LinkKind.REEL, RoleCode.SOUND_MIXER, "Reel", 2025),
                               WorkLink("wl_local2", "https://example.com/headshot.jpg", LinkKind.HEADSHOT)),
            idVerified = true)
        val savedCrew = rpc(crew, "save_profile", ProfileDto.serializer()) {
            put("p_profile", enc(ProfileDto.serializer(), crewModel.toDto())); put("p_min_rate", 3000)
        }.toModel()
        assertFalse("id_verified must not be self-set", savedCrew.idVerified)
        assertEquals(setOf(d2), savedCrew.busyDates)
        assertEquals(listOf(ContentFlag.GORE), savedCrew.contentWontDo)        // own view keeps it
        assertTrue(savedCrew.workLinks.all { Mappers.isUuid(it.id) })        // local ids replaced by server uuids
        assertEquals(listOf(RoleCode.BOOM_OP), savedCrew.stepUpRoles)
        assertEquals(3000, rpc(crew, "my_private_min_rate", Int.serializer().nullable))

        // project draft with local (non-uuid) ids → server ids
        val project = Project(id = "p_local", ownerId = "", payerId = "", title = "Last Local",
            logline = "A night-shift motorman and a runaway share the last local train to Churchgate.",
            languages = listOf("hi", "en"), scriptStatus = ScriptStatus.LOCKED, cityId = "mumbai", areaId = "mumbai_andheri_west",
            shootLocationType = LocationType.OUTDOOR_PUBLIC, exactAddress = "Platform 3, Andheri", shootDates = listOf(d2, d1),
            dateCertainty = DateCertainty.LOCKED, hoursCap = 10, meals = Meals.TWO_PLUS_MEALS, transport = Transport.MILEAGE_OR_TA_PAID,
            travelAllowancePerDay = 300, lodging = false, contentNoneConfirmed = true, auditionMode = AuditionMode.SELF_TAPE)
        val roles = listOf(
            ProjectRole(id = "r_local1", projectId = "p_local", roleCode = RoleCode.SOUND_MIXER, creditTitle = "Sound Recordist",
                compType = CompType.PAID_DAY, amountMin = 2500, amountMax = 2500, currency = "INR", kitFeeDay = 1000,
                paymentTerms = PaymentTerms.ON_WRAP, gearPolicy = GearPolicy.BYO_WITH_KIT_FEE, applicationCap = 25,
                roleDetails = mapOf("boom_op" to "Y")),
            ProjectRole(id = "r_local2", projectId = "p_local", roleCode = RoleCode.ACTOR, creditTitle = "Cast", compType = CompType.UNPAID,
                currency = "INR", perks = setOf(Perk.FOOTAGE_BY_DATE), footageDueWeeks = 6, applicationCap = 20),
        )
        val pid = rpc(owner, "save_project_draft", String.serializer()) {
            put("p_project", enc(ProjectDto.serializer(), project.toDto()))
            put("p_roles", enc(ListSerializer(RoleDto.serializer()), roles.map { it.toDto() }))
        }
        assertTrue(Mappers.isUuid(pid))
        val draft = rpc(owner, "get_project_draft", DraftDto.serializer().nullable) { put("p_project", pid) }!!.toModel()
        assertEquals(listOf(d1, d2), draft.project.shootDates)
        assertEquals(2, draft.project.shootDays)
        assertEquals("Platform 3, Andheri", draft.project.exactAddress)
        assertEquals(mapOf("boom_op" to "Y"), draft.roles.first { it.roleCode == RoleCode.SOUND_MIXER }.roleDetails)
        assertEquals(emptyList<JsonElement>(), rpc(owner, "listing_rules_check", ListSerializer(JsonElement.serializer())) { put("p_project", pid) })
        rpc(owner, "publish_project", JsonObject.serializer()) { put("p_project", pid) }
        val soundId = draft.roles.first { it.roleCode == RoleCode.SOUND_MIXER }.id
        val actorId = draft.roles.first { it.roleCode == RoleCode.ACTOR }.id

        // gigs
        val deck = rpc(crew, "discover_roles", ListSerializer(RwpDto.serializer())) { put("p_city", null as String?); put("p_include_remote", true) }
            .map { it.toModel() }
        val card = deck.first { it.role.id == soundId }
        assertEquals("Rohan K.", card.owner.shortName)
        assertNull(card.project.exactAddress)
        assertEquals(ProjectStatus.OPEN, card.project.status)
        assertEquals(1, card.paidRoles); assertEquals(2, card.totalRoles)
        assertTrue(card.team.single().isOwner)
        assertNotNull(rpc(null, "get_role", RwpDto.serializer().nullable) { put("p_role", soundId) })
        val reel = savedCrew.workLinks.first { it.kind == LinkKind.REEL }.id
        val app = rpc(crew, "apply_to_role", ApplicationDto.serializer()) {
            put("p_role", soundId); put("p_note", "Own kit."); put("p_link", reel); put("p_answer", null as String?); put("p_kit", true)
        }.toModel()
        assertEquals(ApplicationStatus.PENDING, app.status)
        rpc(crew, "apply_to_role", ApplicationDto.serializer()) { put("p_role", actorId); put("p_note", "Theatre.") }
        assertEquals(23, rpc(crew, "likes_left_today", Int.serializer()))
        assertEquals(2, select(crew, "applications", "applicant_id=eq.${crew.id}&order=created_at.desc", ApplicationDto.serializer()).size)
        rpc(crew, "swipe_role", Unit.serializer().nullable) { put("p_role", soundId); put("p_dir", "SAVE") }
        assertEquals(1, rpc(crew, "saved_roles", ListSerializer(RwpDto.serializer())).size)

        // crew up
        val rows = rpc(owner, "get_applicants", ListSerializer(ApplicantRowDto.serializer())) { put("p_role", soundId) }
        val row = rows.single()
        assertEquals(0.833, row.rateFit, 0.001)
        val cardModel = row.card.toModel()
        assertTrue(cardModel.profile.contentWontDo.isEmpty())
        assertTrue(cardModel.profile.workLinks.none { it.kind == LinkKind.HEADSHOT })
        assertEquals(setOf(d2), cardModel.profile.busyDates)             // avail() input
        val castRow = rpc(owner, "get_applicants", ListSerializer(ApplicantRowDto.serializer())) { put("p_role", actorId) }.single()
        assertEquals("https://example.com/headshot.jpg", castRow.headshotUrl)
        val match = rpc(owner, "decide_application", MatchDto.serializer().nullable) {
            put("p_app", app.id); put("p_decision", Decision.TALK.name); put("p_reason", null as String?)
        }!!.toModel()
        assertEquals(MatchState.TALKING, match.state)
        assertNotNull(rpc(owner, "get_profile", CardDto.serializer().nullable) { put("p_profile", crew.id) }?.toModel())

        // chat (direct table rows carry ISO timestamps)
        insert(crew, "messages", MessageInsertDto.serializer(), MessageInsertDto(match.id, crew.id, body = "call me on 98765 43210"))
        val msgs = select(crew, "messages", "match_id=eq.${match.id}&order=created_at.asc", MessageDto.serializer()).map { it.toModel(json) }
        assertEquals(MessageKind.SYSTEM, msgs.first().kind)
        assertTrue(msgs.all { it.createdAt > 1_700_000_000_000L })
        assertTrue(MessageFlag.CONTACT in msgs.last().flags)

        // booking card from a model BookingTerms
        val terms = BookingTerms(roleCode = RoleCode.SOUND_MIXER, creditTitle = "Sound Recordist", projectTitle = "Last Local",
            dates = listOf(d1, d2), callWindow = "~07:00–17:00", compType = CompType.PAID_DAY, amount = 2500, currency = "INR",
            kitFeeDay = 1000, paymentTerms = PaymentTerms.ON_WRAP, payByDate = d2, meals = Meals.TWO_PLUS_MEALS,
            transport = Transport.MILEAGE_OR_TA_PAID, travelAllowancePerDay = 300,
            deliverables = listOf(Deliverable(WrapKind.PAID, d2, OwedBy.PRODUCTION), Deliverable(WrapKind.CREDIT, d2, OwedBy.PRODUCTION)),
            payerId = "", payerName = "", noticePledge = "If you can't make it, tell the production at least 72 hours before.")
        val bc = rpc(owner, "send_booking_card", BookingCardDto.serializer()) {
            put("p_match", match.id); put("p_terms", enc(BookingTermsDto.serializer(), terms.toDto()))
        }.toModel()
        assertEquals(owner.id, bc.terms.payerId)
        assertEquals("Rohan K.", bc.terms.payerName)
        assertEquals(terms.dates, bc.terms.dates)
        val cards = select(crew, "booking_cards", "match_id=eq.${match.id}&order=version.asc", BookingCardDto.serializer()).map { it.toModel() }
        assertEquals(1, cards.single().version)
        assertTrue(cards.single().sentAt > 1_700_000_000_000L)
        val booked = rpc(crew, "respond_booking_card", MatchDto.serializer()) { put("p_card", bc.id); put("p_accept", true) }.toModel()
        assertEquals(MatchState.BOOKED, booked.state)
        val wraps = select(crew, "wrap_items", "match_id=eq.${match.id}&order=due_date.asc", WrapItemDto.serializer()).map { it.toModel() }
        assertEquals(setOf(WrapKind.PAID, WrapKind.CREDIT), wraps.map { it.kind }.toSet())

        // views
        val mv = rpc(crew, "get_match_view", MatchViewDto.serializer().nullable) { put("p_match", match.id) }!!
        assertEquals("Platform 3, Andheri", mv.item.toModel().project.exactAddress)
        assertEquals(BookingStatus.ACCEPTED, mv.acceptedCard!!.toModel().status)
        val inbox = rpc(crew, "get_inbox", InboxDto.serializer()).toModel()
        assertEquals("Rohan K.", inbox.conversations.first { it.match.id == match.id }.counterpartName)
        assertTrue(inbox.updates.isNotEmpty())
        val mine = rpc(owner, "my_projects", ListSerializer(ProjectSummaryDto.serializer())).map { it.toModel() }
        assertEquals(1, mine.single().filledSlots)
        val joined = rpc(crew, "joined_projects", ListSerializer(ProjectSummaryDto.serializer())).map { it.toModel() }
        assertEquals(match.id, joined.single().myMatchId)
        val full = rpc(owner, "get_project_full", ProjectFullDto.serializer().nullable) { put("p_project", pid) }!!
        assertTrue(full.isOwner); assertEquals(1, full.team.size); assertTrue(full.areaLat > 0)
        full.team.single().person.toModel()
        val notes = select(owner, "notifications", "user_id=eq.${owner.id}&order=created_at.desc", NotificationDto.serializer()).map { it.toModel() }
        assertTrue(notes.any { it.kind == NotificationKind.BOOKING_ACCEPTED && it.deepLink == "calltime://m/${match.id}" })
        rpc(owner, "get_reputation", ReputationDto.serializer().nullable) { put("p_profile", crew.id) }!!.toModel()

        // audition card payload round trip
        val m2 = rpc(owner, "decide_application", MatchDto.serializer().nullable) {
            put("p_app", castRow.application!!.id); put("p_decision", "TALK")
        }!!
        val req = AuditionRequest(mode = AuditionMode.SELF_TAPE, sidesUrl = "https://example.com/sides.pdf", dueDate = d1, note = "Two takes")
        rpc(owner, "send_audition_request", MessageDto.serializer()) {
            put("p_match", m2.id); put("p_payload", enc(AuditionRequestDto.serializer(), req.toDto()))
        }
        val aud = select(crew, "messages", "match_id=eq.${m2.id}&kind=eq.AUDITION", MessageDto.serializer()).single().toModel(json)
        assertEquals(req, aud.audition!!.request)
        rpc(crew, "respond_audition", MessageDto.serializer()) { put("p_message", aud.id); put("p_response", "TAPE_SENT"); put("p_tape_url", "https://youtu.be/tape") }
        val aud2 = select(crew, "messages", "id=eq.${aud.id}", MessageDto.serializer()).single().toModel(json)
        assertEquals(AuditionResponse.TAPE_SENT, aud2.audition!!.response)
        val inPerson = AuditionRequest(mode = AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS, date = d1, time = LocalTime.of(11, 0), venue = "V", address = "A", alsoPresent = listOf("DP"))
        assertEquals("\"11:00\"", json.encodeToJsonElement(AuditionRequestDto.serializer(), inPerson.toDto()).jsonObject["time"].toString())

        // safety
        insert(crew, "reports", ReportInsertDto.serializer(), ReportInsertDto(crew.id, ReportTarget.PROFILE, owner.id, ReportReason.SPAM, "test"))
        assertEquals(ReportStatus.RECEIVED, select(crew, "reports", "reporter_id=eq.${crew.id}", ReportDto.serializer()).single().toModel().status)
        insert(crew, "events", EventInsertDto.serializer(), EventInsertDto(crew.id, "wire_test", mapOf("k" to "v")))
        rpc(crew, "keep_list", ListSerializer(CardDto.serializer()))
        rpc(crew, "block_user", Unit.serializer().nullable) { put("p_profile", owner.id) }
        assertEquals(owner.id, rpc(crew, "my_blocked", ListSerializer(CardDto.serializer())).single().toModel().profile.id)
        val export = rpc(crew, "export_my_data", JsonObject.serializer())
        assertEquals(2, export["applications"]!!.jsonArray.size)
        // timestamp parser handles every Postgres flavour
        assertEquals(0L, Millis.parseMillis(kotlinx.serialization.json.JsonPrimitive("1970-01-01T00:00:00+00:00")))
        assertEquals(1000L, Millis.parseMillis(kotlinx.serialization.json.JsonPrimitive("1970-01-01 00:00:01+00")))
        assertEquals(1500L, Millis.parseMillis(kotlinx.serialization.json.JsonPrimitive(1500)))
    }

    /** Books [crew] on a fresh one-role project of [owner]; returns (projectId, roleId, matchId). */
    private fun bookOne(owner: User, crew: User, title: String, d1: LocalDate, d2: LocalDate): Triple<String, String, String> {
        val project = Project(id = "", ownerId = "", payerId = "", title = title,
            logline = "A night bus driver and a sound recordist chase one quiet take across Mumbai.",
            cityId = "mumbai", areaId = "mumbai_bandra", shootLocationType = LocationType.OUTDOOR_PUBLIC, shootDates = listOf(d1, d2),
            hoursCap = 10, meals = Meals.TWO_PLUS_MEALS, transport = Transport.NONE, contentNoneConfirmed = true)
        val role = ProjectRole(id = "", projectId = "", roleCode = RoleCode.SOUND_MIXER, creditTitle = "Sound Recordist",
            compType = CompType.PAID_DAY, amountMax = 3000, currency = "INR", paymentTerms = PaymentTerms.ON_WRAP)
        val pid = rpc(owner, "save_project_draft", String.serializer()) {
            put("p_project", enc(ProjectDto.serializer(), project.toDto()))
            put("p_roles", enc(ListSerializer(RoleDto.serializer()), listOf(role.toDto())))
        }
        rpc(owner, "publish_project", JsonObject.serializer()) { put("p_project", pid) }
        val roleId = rpc(owner, "get_project_draft", DraftDto.serializer().nullable) { put("p_project", pid) }!!.roles.single().id
        val app = rpc(crew, "apply_to_role", ApplicationDto.serializer()) { put("p_role", roleId) }
        val m = rpc(owner, "decide_application", MatchDto.serializer().nullable) { put("p_app", app.id); put("p_decision", "TALK") }!!
        val terms = BookingTerms(roleCode = RoleCode.SOUND_MIXER, creditTitle = "Sound Recordist", projectTitle = title, dates = listOf(d1, d2),
            compType = CompType.PAID_DAY, amount = 3000, currency = "INR", paymentTerms = PaymentTerms.ON_WRAP, payByDate = d2,
            payerId = "", payerName = "", noticePledge = "If you can't make it, tell the production at least 72 hours before.")
        val card = rpc(owner, "send_booking_card", BookingCardDto.serializer()) { put("p_match", m.id); put("p_terms", enc(BookingTermsDto.serializer(), terms.toDto())) }
        rpc(crew, "respond_booking_card", MatchDto.serializer()) { put("p_card", card.id); put("p_accept", true) }
        return Triple(pid, roleId, m.id)
    }

    @Test fun l1RoundTripsThroughDtos() {
        assumeTrue("local Supabase not running at $URL_BASE — skipped", reachable)
        val owner = user("l1owner"); val crew = user("l1crew")
        val today = LocalDate.now()
        for ((u, name, role) in listOf(Triple(owner, "Rohan Kulkarni", RoleCode.DIRECTOR), Triple(crew, "Meera Kapoor", RoleCode.SOUND_MIXER))) {
            val p = Profile(id = u.id, displayName = name, primaryRole = role, level = Level.CREDITED_INDIE, cityId = "mumbai", areaId = "mumbai_versova")
            rpc(u, "save_profile", ProfileDto.serializer()) { put("p_profile", enc(ProfileDto.serializer(), p.toDto())); put("p_min_rate", null as Int?) }
        }

        // A: day sheet → ack → reschedule → withdraw
        val (pidA, _, matchA) = bookOne(owner, crew, "Night Bus", today.plusDays(14), today.plusDays(15))
        val draft = ShootDayDraft(projectId = pidA, day = today.plusDays(14), generalCall = LocalTime.of(7, 0), wrapBy = LocalTime.of(17, 0),
            address = "Bandstand, Bandra West", hospitalNote = "Lilavati", perPersonCall = mapOf(crew.id to LocalTime.of(6, 30), "local_x" to LocalTime.NOON))
        rpc(owner, "publish_shoot_day", ShootDayDto.serializer()) { put("p_day", enc(ShootDayDraftDto.serializer(), draft.toDto())) }
        val days = rpc(crew, "get_shoot_days", ListSerializer(ShootDayFullDto.serializer())) { put("p_project", pidA) }.map { it.toModel() }
        val day = days.single()
        assertEquals(LocalTime.of(6, 30), day.myCall!!.callTime)
        assertEquals(LocalTime.of(7, 0), day.day.generalCall)
        assertEquals("Meera", day.calls.single().shortName)
        assertEquals(1, day.dayIndex); assertEquals(1, day.dayCount)
        val mv = rpc(crew, "get_match_view", MatchViewDto.serializer().nullable) { put("p_match", matchA) }!!
        assertEquals(LocalTime.of(6, 30), mv.myCallTime)
        assertEquals(day.day.id, mv.nextShootDay!!.toModel().id)
        val joined = rpc(crew, "joined_projects", ListSerializer(ProjectSummaryDto.serializer())).map { it.toModel() }
        assertTrue(joined.single { it.project.id == pidA }.myNextCall!!.endsWith("06:30"))
        rpc(crew, "ack_shoot_day", Unit.serializer().nullable) { put("p_day", day.day.id) }
        rpc(crew, "set_attendance", Unit.serializer().nullable) { put("p_day", day.day.id); put("p_status", Attendance.CONFIRMED.name); put("p_profile", null as String?) }
        val full = rpc(owner, "get_project_full", ProjectFullDto.serializer().nullable) { put("p_project", pidA) }!!
        assertEquals(true, full.team.single().nextAck)
        val newDates = listOf(today.plusDays(22), today.plusDays(21))
        rpc(owner, "reschedule_project", Unit.serializer().nullable) {
            put("p_project", pidA); put("p_dates", enc(ListSerializer(String.serializer()), newDates.sorted().map { it.toString() }))
        }
        val v2 = select(crew, "booking_cards", "match_id=eq.$matchA&order=version.desc&limit=1", BookingCardDto.serializer()).single().toModel()
        assertEquals(newDates.sorted(), v2.terms.dates)
        assertTrue("dates" in v2.changedFields)
        val w = rpc(crew, "withdraw_match", WithdrawResultDto.serializer()) {
            put("p_match", matchA); put("p_reason", WithdrawReason.PAID_WORK.name); put("p_note", "paid feature")
        }
        assertEquals(MatchState.WITHDRAWN, w.match.toModel().state)
        assertTrue(w.match.toModel().noticeHours!! >= 72)
        assertNull(w.backupApplicationId)

        // B: shoot happened → wrap → checklist → feedback
        val (pidB, _, matchB) = bookOne(owner, crew, "Night Bus 2", today.plusDays(14), today.plusDays(15))
        val past = """{"shoot_dates":["${today.minusDays(3)}","${today.minusDays(2)}"]}"""
        assertTrue(http("PATCH", "/rest/v1/projects?id=eq.$pidB", SERVICE, SERVICE, past, "return=minimal").code < 300)
        rpc(owner, "mark_wrapped", Unit.serializer().nullable) { put("p_project", pidB) }
        val groups = rpc(owner, "project_wrap", ListSerializer(WrapGroupDto.serializer())) { put("p_project", pidB) }.map { it.toModel() }
        val group = groups.single()
        assertEquals(MatchState.WRAPPED, group.match.state)
        val paid = group.items.single { it.kind == WrapKind.PAID }
        val marked = rpc(owner, "mark_wrap_item", WrapItemDto.serializer()) { put("p_item", paid.id); put("p_link", null as String?); put("p_note", "UPI") }.toModel()
        assertEquals(WrapStatus.DONE, marked.status); assertNotNull(marked.markedAt)
        val confirmed = rpc(crew, "confirm_wrap_item", WrapItemDto.serializer()) { put("p_item", paid.id); put("p_received", true) }.toModel()
        assertEquals(WrapStatus.CONFIRMED, confirmed.status)
        val fs0 = rpc(crew, "feedback_state", FeedbackStateDto.serializer().nullable) { put("p_match", matchB) }!!.toModel()
        assertTrue(fs0.unlocked); assertFalse(fs0.iSubmitted)
        val answers = FeedbackAnswers(paid = PaidAnswer.ON_TIME, delivered = DeliveredAnswer.YES, feltSafe = true, kept = KeptAnswer.YES, workAgain = true,
                                      privateNote = "Lovely set")
        val fs1 = rpc(crew, "submit_feedback", FeedbackStateDto.serializer()) {
            put("p_match", matchB); put("p_answers", enc(FeedbackAnswersDto.serializer(), answers.toDto())); put("p_note", answers.privateNote)
        }.toModel()
        assertTrue(fs1.iSubmitted); assertFalse(fs1.revealed)
        val mine = select(crew, "feedback", "match_id=eq.$matchB", JsonObject.serializer()).single()
        assertEquals("ON_TIME", mine["answers"]!!.jsonObject["paid"]!!.jsonPrimitive.content)
        assertEquals("true", mine["answers"]!!.jsonObject["work_again"]!!.jsonPrimitive.content)
        assertEquals(1, rpc(crew, "keep_list", ListSerializer(CardDto.serializer())).size)
    }
}
