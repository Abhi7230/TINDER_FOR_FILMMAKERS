package com.tff.app.domain

import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.model.*
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** One run of the booking summary with its emphasis, so the UI can bold the role and italicise the title. */
data class TextSpan(val text: String, val style: SpanStyleKind = SpanStyleKind.NORMAL)
enum class SpanStyleKind { NORMAL, BOLD, ITALIC }

/**
 * Card copy (PRD §6.5, Appendix D). Pure formatting so RoleCard, RoleDetail, share text and the booking
 * card all say exactly the same thing.
 */
object CardText {
    private const val SEP = " · "

    // ───────────────────────── Role names ─────────────────────────

    /** Short crew-facing role name used on cards, share text and booking cards ("Sound Mixer", "DP", "1st AD"). */
    fun roleName(code: RoleCode): String = when (code) {
        RoleCode.DIRECTOR -> "Director"
        RoleCode.WRITER -> "Writer"
        RoleCode.PRODUCER -> "Producer"
        RoleCode.FIRST_AD -> "1st AD"
        RoleCode.SECOND_AD -> "2nd AD"
        RoleCode.SCRIPT_SUPERVISOR -> "Script Supervisor"
        RoleCode.PA -> "Production Assistant"
        RoleCode.DP -> "DP"
        RoleCode.CAMERA_OP -> "Camera Operator"
        RoleCode.FIRST_AC -> "1st AC"
        RoleCode.GAFFER -> "Gaffer"
        RoleCode.GRIP -> "Grip"
        RoleCode.SOUND_MIXER -> "Sound Mixer"
        RoleCode.BOOM_OP -> "Boom Operator"
        RoleCode.PRODUCTION_DESIGNER -> "Production Designer"
        RoleCode.ART_ASSISTANT -> "Art Assistant"
        RoleCode.COSTUME -> "Costume"
        RoleCode.HMU -> "Hair & Makeup"
        RoleCode.ACTOR -> "Actor"
        RoleCode.VOICE_ACTOR -> "Voice Actor"
        RoleCode.EDITOR -> "Editor"
        RoleCode.COLORIST -> "Colorist"
        RoleCode.VFX_ARTIST -> "VFX Artist"
        RoleCode.SOUND_DESIGNER -> "Sound Designer"
        RoleCode.COMPOSER -> "Composer"
        RoleCode.BTS_STILLS -> "BTS / Stills"
        RoleCode.INTIMACY_COORDINATOR -> "Intimacy Coordinator"
        RoleCode.STUNT_COORDINATOR -> "Stunt Coordinator"
        RoleCode.LOCATION_MANAGER -> "Location Manager"
    }

    /** Role name for one listing: actor roles use the size from role details ("Lead Actor", "Day Player"). */
    fun roleName(role: ProjectRole): String {
        if (role.roleCode == RoleCode.ACTOR) {
            when (role.roleDetails["size"]?.trim()?.uppercase()?.replace(' ', '_')) {
                "LEAD" -> return "Lead Actor"
                "SUPPORTING" -> return "Supporting Actor"
                "DAY_PLAYER" -> return "Day Player"
            }
        }
        return roleName(role.roleCode)
    }

    /** "SOUND MIXER ×1". */
    fun roleLabel(role: ProjectRole): String = "${roleName(role).uppercase(Locale.ENGLISH)} ×${role.count}"

    // ───────────────────────── Pay line ─────────────────────────

    /** "₹2,500/day + ₹1,000 kit · paid on wrap" / "Stipend ₹800/day · 2 meals · TA ₹300" / "Unpaid · 2 meals · your scenes in 6 wks · step-up credit". */
    fun payLine(role: ProjectRole, project: Project): String {
        val post = role.isPostRole
        return when (role.compType) {
            CompType.PAID_DAY, CompType.PAID_FLAT -> {
                val parts = mutableListOf(moneyWithKit(role.compType, role.amountMin, role.amountMax, role.currency, role.kitFeeDay))
                role.paymentTerms?.let { parts += termsLabel(it, post) }
                parts.filter { it.isNotBlank() }.joinToString(SEP)
            }
            CompType.STIPEND -> {
                val amount = Money.range(role.amountMin, role.amountMax, role.currency)
                val head = when {
                    amount.isBlank() -> "Stipend"
                    post -> "Stipend $amount"
                    else -> "Stipend $amount/day"
                }
                (listOf(head) + perkBits(role, project)).joinToString(SEP)
            }
            CompType.UNPAID -> (listOf("Unpaid") + perkBits(role, project)).joinToString(SEP)
        }
    }

    /** "₹2,500/day + ₹1,000 kit", "₹12,000 flat", "₹2,000–3,000/day". */
    private fun moneyWithKit(type: CompType, min: Int?, max: Int?, currency: String, kit: Int?): String {
        val amount = Money.range(min, max, currency)
        val base = when {
            amount.isBlank() -> ""
            type == CompType.PAID_FLAT -> "$amount flat"
            else -> "$amount/day"
        }
        val kitText = kit?.takeIf { it > 0 }?.let {
            if (type == CompType.PAID_FLAT) "${Money.format(it, currency)}/day kit" else "${Money.format(it, currency)} kit"
        }
        return listOfNotNull(base.ifBlank { null }, kitText).joinToString(" + ")
    }

    /** What an unpaid or stipend role gives, in card order: meals, transport, lodging, footage, final film, step-up credit. */
    private fun perkBits(role: ProjectRole, project: Project): List<String> {
        val out = mutableListOf<String>()
        val onSet = !role.isPostRole
        var travelShown = false
        if (onSet) {
            project.meals?.let { m -> mealsShort(m)?.let { out += it } }
            when (project.transport) {
                Transport.MILEAGE_OR_TA_PAID -> {
                    out += project.travelAllowancePerDay?.takeIf { it > 0 }?.let { "TA ${Money.format(it, role.currency)}" } ?: "travel allowance"
                    travelShown = true
                }
                Transport.PRODUCTION_VAN -> { out += "pickup van"; travelShown = true }
                Transport.CARPOOL_COORDINATED -> out += "carpool"
                else -> {}
            }
            if (Perk.TRAVEL_COVERED in role.perks && !travelShown) out += "travel covered"
            if (Perk.LODGING in role.perks || project.lodging == true) out += "stay covered"
        }
        if (Perk.FOOTAGE_BY_DATE in role.perks) {
            out += role.footageDueWeeks?.let { "your scenes in ${weeks(it)}" } ?: "footage of your scenes"
        }
        if (Perk.FINAL_FILM_BY_DATE in role.perks) {
            out += role.finalFilmDueWeeks?.let { "final film in ${weeks(it)}" } ?: "copy of the final film"
        }
        if (Perk.STEP_UP_CREDIT in role.perks) out += "step-up credit"
        return out
    }

    private fun weeks(n: Int) = if (n == 1) "1 wk" else "$n wks"

    /** Pay-line form of meals; null when it isn't worth listing as something you get. */
    internal fun mealsShort(m: Meals): String? = when (m) {
        Meals.ONE_MEAL -> "1 meal"
        Meals.TWO_PLUS_MEALS -> "2 meals"
        Meals.CATERED -> "catered meals"
        Meals.NONE, Meals.CRAFTY_ONLY -> null
    }

    // ───────────────────────── When / where ─────────────────────────

    /** "SAT 14 – SUN 15 NOV · 2 DAYS · ≤10H · LOCKED" or "REMOTE · LOCK 10 JAN → DUE 7 FEB · 4 WKS" or "REMOTE · DUE 30 JAN". */
    fun whenLine(role: ProjectRole, project: Project): String {
        if (role.isPostRole) return postWhen(role, project)
        val start = project.shootStart
        val end = project.shootEnd
        if (start == null || end == null) {
            return role.postDeadline?.let { "REMOTE · DUE ${Dates.dayMonth(it).uppercase(Locale.ENGLISH)}" } ?: "DATES TBC"
        }
        val parts = mutableListOf<String>()
        if (role.remoteOk) parts += "REMOTE"
        parts += Dates.span(start, end).uppercase(Locale.ENGLISH)
        parts += daysText(project.shootDays).uppercase(Locale.ENGLISH)
        project.hoursCap?.let { parts += "≤${it}H" }
        project.dateCertainty?.let { parts += it.name }
        return parts.joinToString(SEP)
    }

    private fun postWhen(role: ProjectRole, project: Project): String {
        val due = role.postDeadline!!
        val dueText = Dates.dayMonth(due).uppercase(Locale.ENGLISH)
        val lock = project.pictureLockDate
        if (needsPictureLock(role.roleCode) && lock != null) {
            val lockText = Dates.dayMonth(lock).uppercase(Locale.ENGLISH)
            return "REMOTE · LOCK $lockText → DUE $dueText · ${lockToDue(lock, due).uppercase(Locale.ENGLISH)}"
        }
        return "REMOTE · DUE $dueText"
    }

    /** Music and sound design are judged against picture lock (R20/R21). */
    fun needsPictureLock(code: RoleCode): Boolean = code == RoleCode.COMPOSER || code == RoleCode.SOUND_DESIGNER

    /** "4 wks" between picture lock and delivery (whole weeks, rounded down; under a week shows days). */
    fun lockToDue(lock: LocalDate, due: LocalDate): String {
        val days = Dates.daysBetween(lock, due)
        return when {
            days < 7 -> if (days == 1L) "1 day" else "${maxOf(days, 0)} days"
            else -> weeks((days / 7).toInt())
        }
    }

    private fun daysText(n: Int) = if (n == 1) "1 day" else "$n days"

    /** "Jayanagar, Bengaluru · Kannada/English". */
    fun whereLine(item: RoleWithProject): String {
        val place = placeName(item.areaName, item.cityName)
        val langs = languageNames(item.project.languages)
        return listOf(place, langs).filter { it.isNotBlank() }.joinToString(SEP)
    }

    /** "Jayanagar, Bengaluru" (area omitted when blank or the same as the city). */
    fun placeName(area: String, city: String): String = when {
        area.isBlank() -> city
        city.isBlank() || area.equals(city, ignoreCase = true) -> area
        else -> "$area, $city"
    }

    /** "SHORT · 12 MIN". */
    fun formatChip(project: Project): String = "${formatLabel(project.format).uppercase(Locale.ENGLISH)} · ${project.runtimeMin} MIN"

    /** "Open · 6/15 applied · replies in ~1 day". */
    fun footer(item: RoleWithProject): String {
        val r = item.role
        val status = when (r.status) { RoleStatus.OPEN -> "Open"; RoleStatus.FILLED -> "Filled"; RoleStatus.CLOSED -> "Closed" }
        val parts = mutableListOf(status, "${r.applicationsCount}/${r.applicationCap} applied")
        item.owner.reputation.replyHoursMedian?.let { parts += "replies in ${replyTime(it)}" }
        return parts.joinToString(SEP)
    }

    /** "~3h", "~1 day", "~2 days". */
    fun replyTime(hours: Int): String = when {
        hours <= 1 -> "~1h"
        hours < 18 -> "~${hours}h"
        else -> {
            val d = maxOf(1, (hours / 24.0).roundToInt())
            if (d == 1) "~1 day" else "~$d days"
        }
    }

    // ───────────────────────── Detail lists ─────────────────────────

    /** Perks with dates, for "What you get". */
    fun perksDetailed(role: ProjectRole, project: Project): List<String> {
        val out = mutableListOf<String>()
        val onSet = !role.isPostRole
        val anchor: LocalDate? = if (onSet) project.shootEnd else role.postDeadline
        if (onSet) {
            when (project.meals) {
                Meals.ONE_MEAL -> out += "1 meal a day on set"
                Meals.TWO_PLUS_MEALS -> out += "2 meals a day on set"
                Meals.CATERED -> out += "Catered meals on set"
                Meals.CRAFTY_ONLY -> out += "Snacks on set"
                else -> {}
            }
            when (project.transport) {
                Transport.MILEAGE_OR_TA_PAID -> out += project.travelAllowancePerDay?.takeIf { it > 0 }
                    ?.let { "Travel allowance ${Money.format(it, role.currency)}/day" } ?: "Travel allowance paid"
                Transport.PRODUCTION_VAN -> out += "Pickup in the production van"
                Transport.CARPOOL_COORDINATED -> out += "Carpool arranged by the production"
                else -> {}
            }
            if (Perk.TRAVEL_COVERED in role.perks && project.transport != Transport.MILEAGE_OR_TA_PAID && project.transport != Transport.PRODUCTION_VAN) {
                out += "Travel covered"
            }
            if (Perk.LODGING in role.perks || project.lodging == true) out += "Stay covered on shoot nights"
        }
        if (Perk.FOOTAGE_BY_DATE in role.perks) {
            val w = role.footageDueWeeks
            out += when {
                w != null && anchor != null -> "Approved clips of your scenes, graded, by ${Dates.dayMonth(anchor.plusWeeks(w.toLong()))} (${weeks(w)} after the shoot)"
                w != null -> "Approved clips of your scenes, graded, within ${weeks(w)} of the shoot"
                else -> "Approved clips of your scenes, graded, for your reel"
            }
        }
        if (Perk.FINAL_FILM_BY_DATE in role.perks) {
            val w = role.finalFilmDueWeeks
            val after = if (onSet) "after the shoot" else "after delivery"
            out += when {
                w != null && anchor != null -> "A copy of the final film by ${Dates.dayMonth(anchor.plusWeeks(w.toLong()))} (${weeks(w)} $after)"
                w != null -> "A copy of the final film within ${weeks(w)}"
                else -> "A copy of the final film"
            }
        }
        if (Perk.STEP_UP_CREDIT in role.perks) out += "Step-up credit: credited in the role you're stepping into"
        return out
    }

    // ───────────────────────── Booking summary ─────────────────────────

    /** Plain-language booking summary ("You're booked as Sound Mixer (credit: 'Sound Recordist') on …"). */
    fun bookingSummary(t: BookingTerms): String = bookingSummarySpans(t).joinToString("") { it.text }

    /** Same text as [bookingSummary], split so the UI can bold the role and italicise the project title. */
    fun bookingSummarySpans(t: BookingTerms): List<TextSpan> {
        val spans = mutableListOf(
            TextSpan("You're booked as "),
            TextSpan(roleName(t.roleCode), SpanStyleKind.BOLD),
            TextSpan(" (credit: '${t.creditTitle}') on "),
            TextSpan(t.projectTitle, SpanStyleKind.ITALIC),
        )
        val bits = bookingBits(t)
        if (bits.isNotEmpty()) spans += TextSpan(SEP + bits.joinToString(SEP))
        return spans
    }

    private fun bookingBits(t: BookingTerms): List<String> {
        val out = mutableListOf<String>()
        if (t.isPostRole) {
            out += "remote"
            t.pictureLockDate?.let { out += "picture lock ${Dates.dayMonth(it)}" }
            t.deliveryDate?.let { out += "deliver by ${Dates.dayMonth(it)}" }
            t.revisionRounds?.let { out += if (it == 1) "1 revision round" else "$it revision rounds" }
        } else {
            if (t.dates.isNotEmpty()) out += dateList(t.dates)
            t.callWindow?.takeIf { it.isNotBlank() }?.let { out += callWindowText(it) }
        }
        // Pay
        when (t.compType) {
            CompType.PAID_DAY, CompType.PAID_FLAT -> {
                moneyWithKit(t.compType, t.amount, t.amount, t.currency, t.kitFeeDay).takeIf { it.isNotBlank() }?.let { out += it }
                t.paymentTerms?.let { out += termsLabel(it, t.isPostRole) }
            }
            CompType.STIPEND -> {
                val a = t.amount?.let { Money.format(it, t.currency) }
                out += when {
                    a == null -> "Stipend"
                    t.isPostRole -> "Stipend $a"
                    else -> "Stipend $a/day"
                }
                t.paymentTerms?.let { out += termsLabel(it, t.isPostRole) }
            }
            CompType.UNPAID -> out += "Unpaid"
        }
        // Perks & logistics
        if (!t.isPostRole) {
            t.meals?.let { m -> mealsShort(m)?.let { out += it } }
            var travelShown = false
            when (t.transport) {
                Transport.MILEAGE_OR_TA_PAID -> {
                    out += t.travelAllowancePerDay?.takeIf { it > 0 }?.let { "TA ${Money.format(it, t.currency)}/day" } ?: "travel allowance"
                    travelShown = true
                }
                Transport.PRODUCTION_VAN -> { out += "pickup van"; travelShown = true }
                Transport.CARPOOL_COORDINATED -> out += "carpool"
                else -> {}
            }
            if (Perk.TRAVEL_COVERED in t.perks && !travelShown) out += "travel covered"
            if (Perk.LODGING in t.perks) out += "stay covered"
        }
        val footage = t.deliverables.firstOrNull { it.kind == WrapKind.FOOTAGE }
        if (footage != null) {
            out += footage.dueDate?.let { "approved clips of your scenes by ${Dates.dayMonth(it)}" } ?: "approved clips of your scenes"
        } else if (Perk.FOOTAGE_BY_DATE in t.perks && !t.isPostRole) {
            out += t.footageDueWeeks?.let { "approved clips of your scenes in ${weeks(it)}" } ?: "approved clips of your scenes"
        }
        val finalFilm = t.deliverables.firstOrNull { it.kind == WrapKind.FINAL_FILM }
        if (finalFilm?.dueDate != null) out += "final film by ${Dates.dayMonth(finalFilm.dueDate)}"
        else if (Perk.FINAL_FILM_BY_DATE in t.perks) out += t.finalFilmDueWeeks?.let { "final film in ${weeks(it)}" } ?: "copy of the final film"
        if (Perk.STEP_UP_CREDIT in t.perks) out += "step-up credit"
        out += "Payer: ${t.payerName}"
        return out
    }

    /** "Sat 14 & Sun 15 Nov", "Sat 28, Sun 29 Nov & Sat 5 Dec"; more than 4 days: "6 days, Sat 14 – Sun 22 Nov". */
    fun dateList(dates: List<LocalDate>): String {
        val ds = dates.distinct().sorted()
        if (ds.isEmpty()) return ""
        if (ds.size > 4) return "${ds.size} days, ${Dates.span(ds.first(), ds.last())}"
        val dayFmt = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)
        val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
        val tokens = ds.mapIndexed { i, d ->
            val next = ds.getOrNull(i + 1)
            val closesMonth = next == null || next.month != d.month || next.year != d.year
            if (closesMonth) "${dayFmt.format(d)} ${monthFmt.format(d)}" else dayFmt.format(d)
        }
        return if (tokens.size == 1) tokens[0] else tokens.dropLast(1).joinToString(", ") + " & " + tokens.last()
    }

    private val windowRx = Regex("""^~?\s*(\d{1,2})(?::(\d{2}))?\s*[-–—]\s*(\d{1,2})(?::(\d{2}))?$""")

    /** "~7:00–17:00" → "call ~07:00, wrap by 17:00". Anything else is shown as "call <text>". */
    fun callWindowText(window: String): String {
        val m = windowRx.find(window.trim()) ?: return "call ${window.trim()}"
        val (h1, m1, h2, m2) = m.destructured
        val start = runCatching { LocalTime.of(h1.toInt(), m1.ifBlank { "0" }.toInt()) }.getOrNull()
        val end = runCatching { LocalTime.of(h2.toInt(), m2.ifBlank { "0" }.toInt()) }.getOrNull()
        if (start == null || end == null) return "call ${window.trim()}"
        return "call ~${Dates.time(start)}, wrap by ${Dates.time(end)}"
    }

    // ───────────────────────── Labels ─────────────────────────

    private val LANGUAGE_NAMES = mapOf(
        "en" to "English", "hi" to "Hindi", "kn" to "Kannada", "mr" to "Marathi", "ta" to "Tamil", "te" to "Telugu",
        "ml" to "Malayalam", "bn" to "Bengali", "gu" to "Gujarati", "pa" to "Punjabi", "ur" to "Urdu", "or" to "Odia",
        "as" to "Assamese", "kok" to "Konkani", "sd" to "Sindhi", "ne" to "Nepali", "es" to "Spanish", "fr" to "French",
        "de" to "German", "pt" to "Portuguese", "it" to "Italian", "ar" to "Arabic", "zh" to "Chinese", "ja" to "Japanese",
        "ko" to "Korean", "ru" to "Russian", "tl" to "Tagalog", "vi" to "Vietnamese", "pl" to "Polish", "yo" to "Yoruba",
    )

    /** One language name ("kn" → "Kannada"); unknown codes are shown in caps. */
    fun languageName(code: String): String = LANGUAGE_NAMES[code.trim().lowercase(Locale.ENGLISH)] ?: code.trim().uppercase(Locale.ENGLISH)

    fun languageNames(codes: List<String>): String = codes.filter { it.isNotBlank() }.distinct().joinToString("/") { languageName(it) }     // "Kannada/English"

    fun levelLabel(l: Level): String = when (l) {                   // "Building a reel"
        Level.FIRST_PROJECT -> "First project"
        Level.BUILDING_REEL -> "Building a reel"
        Level.CREDITED_INDIE -> "Credited indie"
        Level.WORKING_PRO -> "Working pro"
    }

    fun formatLabel(f: Format): String = when (f) {
        Format.SHORT -> "Short"
        Format.FEATURE -> "Feature"
        Format.SERIES_PILOT -> "Series pilot"
        Format.MUSIC_VIDEO -> "Music video"
        Format.SPEC_AD -> "Spec ad"
        Format.STUDENT_THESIS -> "Student thesis"
        Format.CHALLENGE_48H -> "48-hour challenge"
        Format.VERTICAL -> "Vertical"
        Format.DOCUMENTARY -> "Documentary"
    }

    fun termsLabel(t: PaymentTerms): String = termsLabel(t, postRole = false)            // "paid on wrap"

    /** Post roles count pay from delivery ("paid within 7 days of delivery"). */
    fun termsLabel(t: PaymentTerms, postRole: Boolean): String = when (t) {
        PaymentTerms.ON_WRAP -> if (postRole) "paid on delivery" else "paid on wrap"
        PaymentTerms.HALF_UPFRONT -> if (postRole) "half upfront, rest on delivery" else "half upfront, rest on wrap"
        PaymentTerms.WITHIN_7_DAYS -> if (postRole) "paid within 7 days of delivery" else "paid within 7 days"
        PaymentTerms.WITHIN_15_DAYS -> if (postRole) "paid within 15 days of delivery" else "paid within 15 days"
        PaymentTerms.WITHIN_30_DAYS -> if (postRole) "paid within 30 days of delivery" else "paid within 30 days"
    }

    fun perkLabel(p: Perk): String = when (p) {
        Perk.TRAVEL_COVERED -> "Travel covered"
        Perk.LODGING -> "Stay covered"
        Perk.FOOTAGE_BY_DATE -> "Footage by a date"
        Perk.FINAL_FILM_BY_DATE -> "Final film by a date"
        Perk.STEP_UP_CREDIT -> "Step-up credit"
        Perk.MEALS -> "2+ meals a day"
        Perk.SHORT_COMMITMENT -> "3 days or fewer"
        Perk.SCRIPT_READY -> "Script locked"
    }

    fun contentFlagLabel(f: ContentFlag): String = when (f) {
        ContentFlag.NUDITY -> "Nudity"
        ContentFlag.SIMULATED_SEX -> "Simulated sex"
        ContentFlag.INTIMACY_SCENE -> "Intimacy scene"
        ContentFlag.STUNTS_FIGHTS -> "Stunts or fights"
        ContentFlag.FIREARMS_PROPS -> "Prop firearms"
        ContentFlag.MINORS_ON_SET -> "Minors on set"
        ContentFlag.NIGHT_SHOOT -> "Night shoot"
        ContentFlag.WATER -> "Water work"
        ContentFlag.ANIMALS -> "Animals"
        ContentFlag.GORE -> "Gore"
    }

    fun mealsLabel(m: Meals): String = when (m) {
        Meals.NONE -> "No meals"
        Meals.CRAFTY_ONLY -> "Snacks only"
        Meals.ONE_MEAL -> "1 meal"
        Meals.TWO_PLUS_MEALS -> "2 meals"
        Meals.CATERED -> "Catered"
    }

    fun transportLabel(t: Transport): String = when (t) {
        Transport.NONE -> "Make your own way"
        Transport.CARPOOL_COORDINATED -> "Carpool arranged"
        Transport.MILEAGE_OR_TA_PAID -> "Travel allowance paid"
        Transport.PRODUCTION_VAN -> "Production van"
    }

    fun auditionModeLabel(m: AuditionMode): String = when (m) {
        AuditionMode.SELF_TAPE -> "Self-tape"
        AuditionMode.VIDEO_CALL -> "Video call"
        AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS -> "Studio or public space, others present"
    }

    fun gearPolicyLabel(g: GearPolicy): String = when (g) {
        GearPolicy.PRODUCTION_PROVIDES -> "Production provides gear"
        GearPolicy.BYO_WITH_KIT_FEE -> "Own gear, kit fee paid"
        GearPolicy.BYO_NO_FEE -> "Own gear, no kit fee"
        GearPolicy.NA -> "No gear needed"
    }

    fun scriptStatusLabel(s: ScriptStatus): String = when (s) {
        ScriptStatus.IDEA -> "Idea stage"
        ScriptStatus.DRAFT -> "Script in draft"
        ScriptStatus.LOCKED -> "Script locked"
    }

    fun compTypeLabel(c: CompType): String = when (c) {
        CompType.PAID_DAY -> "Paid per day"
        CompType.PAID_FLAT -> "Paid flat fee"
        CompType.STIPEND -> "Stipend"
        CompType.UNPAID -> "Unpaid"
    }

    // ───────────────────────── Card face helpers (§6.5) ─────────────────────────

    /** Poster top-right chip: "4 KM", "<1 KM" or "REMOTE"; null when the distance isn't known (guests). */
    fun distanceChip(distanceKm: Double?, remote: Boolean): String? = when {
        remote -> "REMOTE"
        distanceKm == null -> null
        distanceKm < 1.0 -> "<1 KM"
        else -> "${distanceKm.roundToInt()} KM"
    }

    /** Badges next to the role label: "STEP-UP OK", "BEGINNERS WELCOME". */
    fun badges(role: ProjectRole): List<String> = buildList {
        if (role.stepUpFriendly) add("STEP-UP OK")
        if (role.levelWanted == Level.FIRST_PROJECT || role.levelWanted == Level.BUILDING_REEL) add("BEGINNERS WELCOME")
    }

    /** "DP · Sound attached" from booked people (the owner isn't listed); null when nobody is attached yet. */
    fun teamLine(team: List<TeamMember>): String? {
        val names = team.filterNot { it.isOwner }.map { teamRoleName(it.roleCode) }.distinct()
        return if (names.isEmpty()) null else names.joinToString(SEP) + " attached"
    }

    private fun teamRoleName(code: RoleCode): String = when (code) {
        RoleCode.SOUND_MIXER, RoleCode.BOOM_OP -> "Sound"
        RoleCode.SOUND_DESIGNER -> "Sound design"
        RoleCode.ACTOR, RoleCode.VOICE_ACTOR -> "Actor"
        RoleCode.COMPOSER -> "Music"
        else -> roleName(code)
    }

    /**
     * Credibility row: ID verified, "3 shoots done", the owner's weakest track-record number (or
     * "New on Calltime"), "Script locked", team attached.
     */
    fun credibilityChips(item: RoleWithProject): List<String> = buildList {
        val o = item.owner
        if (o.idVerified) add("ID verified")
        val shoots = o.reputation.shootsCompleted
        if (shoots > 0) add(if (shoots == 1) "1 shoot done" else "$shoots shoots done")
        ReputationCalc.headlineChip(o.reputation, asOwner = true).takeIf { it.isNotBlank() }?.let { add(it) }
        if (item.project.scriptStatus == ScriptStatus.LOCKED) add("Script locked")
        teamLine(item.team)?.let { add(it) }
    }

    /** Flags row: content flags, then "Private home · friend welcome" for shoots at a home. */
    fun flagsRow(project: Project): List<String> = buildList {
        ContentFlag.entries.filter { it in project.contentFlags }.forEach { add(contentFlagLabel(it)) }
        if (project.shootLocationType == LocationType.PRIVATE_HOME) add("Private home · friend welcome")
        if (project.rescheduleCount >= 2) add("Rescheduled ${project.rescheduleCount}×")
    }

    /** "2 of 4 roles paid". */
    fun rolesPaidChip(item: RoleWithProject): String = "${item.paidRoles} of ${item.totalRoles} roles paid"

    /** "Posted 2d ago". */
    fun postedAgo(project: Project, nowMillis: Long): String? =
        project.publishedAt?.takeIf { it > 0 }?.let { "Posted ${Dates.ago(it, nowMillis)}" }

    /** Person Card headline: "SOUND MIXER · CREDITED INDIE". */
    fun personRoleLine(p: Profile): String =
        "${roleName(p.primaryRole).uppercase(Locale.ENGLISH)} · ${levelLabel(p.level).uppercase(Locale.ENGLISH)}"

    /** Step-up chip: "→ DP" (first step-up role), or null. */
    fun stepUpChip(p: Profile): String? = p.stepUpRoles.firstOrNull()?.let { "→ ${roleName(it)}" }

    /**
     * Person Card fit line: "3/3 days · 6 km · Kit ₹1,500/day · rate in budget". Unpaid roles end with
     * "Open to unpaid · conditions met". Uses the server-computed rate fit, never the private rate.
     */
    fun fitLine(item: RoleWithProject, view: ApplicantView): String {
        val r = item.role
        val p = view.card.profile
        val parts = mutableListOf<String>()
        if (!r.isPostRole && view.totalDays > 0) parts += "${view.availDays}/${view.totalDays} days"
        val d = view.card.distanceKm
        when {
            r.remoteOk -> parts += "Remote"
            d == null -> {}
            d < 1.0 -> parts += "<1 km"
            else -> parts += "${d.roundToInt()} km"
        }
        if (p.gear.isNotEmpty()) p.kitFeeDay?.takeIf { it > 0 }?.let { parts += "Kit ${Money.format(it, r.currency)}/day" }
        when {
            r.compType == CompType.UNPAID -> if (p.compMode != CompMode.PAID_ONLY) { parts += "Open to unpaid"; parts += "conditions met" }
            view.rateFit >= 1.0 -> parts += "rate in budget"
        }
        return parts.joinToString(SEP)
    }

    /** Ask card: "Would you be Sound Mixer on Filter Coffee, 4 AM? Sat 14 – Sun 15 Nov · ₹2,500/day + kit · 2 meals". */
    fun askLine(item: RoleWithProject): String {
        val r = item.role
        val p = item.project
        val parts = mutableListOf<String>()
        if (r.isPostRole) {
            parts += "Remote"
            if (needsPictureLock(r.roleCode)) p.pictureLockDate?.let { parts += "picture lock ${Dates.dayMonth(it)}" }
            parts += "deliver by ${Dates.dayMonth(r.postDeadline!!)}"
        } else {
            val s = p.shootStart
            val e = p.shootEnd
            if (s != null && e != null) parts += Dates.span(s, e)
        }
        parts += when (r.compType) {
            CompType.PAID_DAY, CompType.PAID_FLAT -> {
                val money = Money.range(r.amountMin, r.amountMax, r.currency)
                val unit = if (r.compType == CompType.PAID_FLAT) " flat" else "/day"
                (if (money.isBlank()) "Paid" else money + unit) + if ((r.kitFeeDay ?: 0) > 0) " + kit" else ""
            }
            else -> payLine(r, p)
        }
        if (r.isPaid && !r.isPostRole) p.meals?.let { m -> mealsShort(m)?.let { parts += it } }
        return "Would you be ${roleName(r)} on ${p.title}? " + parts.joinToString(SEP)
    }

    /** First system message in a new chat: the role's terms, so nobody has to ask "what's the rate?". */
    fun termsMessage(item: RoleWithProject): String {
        val r = item.role
        val p = item.project
        val lines = mutableListOf("${roleName(r)} on ${p.title}")
        lines += "Pay: ${payLine(r, p)}"
        if (r.isPostRole) {
            val bits = mutableListOf("Remote")
            if (needsPictureLock(r.roleCode)) p.pictureLockDate?.let { bits += "picture lock ${Dates.dayMonth(it)}" }
            bits += "deliver by ${Dates.dayMonth(r.postDeadline!!)}"
            lines += bits.joinToString(SEP)
        } else {
            val s = p.shootStart
            val e = p.shootEnd
            val bits = mutableListOf<String>()
            if (s != null && e != null) { bits += Dates.span(s, e); bits += daysText(p.shootDays) }
            p.hoursCap?.let { bits += "≤${it}h" }
            p.dateCertainty?.let { bits += if (it == DateCertainty.LOCKED) "dates locked" else "dates tentative" }
            if (bits.isNotEmpty()) lines += bits.joinToString(SEP)
            lines += "Where: ${placeName(item.areaName, item.cityName)} (exact address once you're booked)"
        }
        val perks = perksDetailed(r, p)
        if (perks.isNotEmpty()) lines += "You get: " + perks.joinToString("; ")
        return lines.joinToString("\n")
    }

    /** "Mumbai · 214 people · 31 open projects · 9 sound mixers"; early cities: "Indore · early city · 3 people · 1 open project · + remote roles". */
    fun densityHeader(d: Density, role: RoleCode? = null): String {
        val parts = mutableListOf(d.cityName)
        if (d.isEarly && !d.isLive) parts += "early city"
        parts += if (d.people == 1) "1 person" else "${d.people} people"
        parts += if (d.openProjects == 1) "1 open project" else "${d.openProjects} open projects"
        if (d.isEarly && !d.isLive) parts += "+ remote roles"
        else if (role != null) d.perRole[role]?.let { n -> parts += "$n ${if (n == 1) roleNoun(role) else rolePlural(role)}" }
        return parts.joinToString(SEP)
    }

    private fun roleNoun(code: RoleCode): String = rolePlural(code).let { pl ->
        when (code) {
            RoleCode.HMU -> "hair & makeup artist"
            RoleCode.COSTUME -> "costume designer"
            RoleCode.BTS_STILLS -> "BTS and stills shooter"
            else -> if (pl.endsWith("s")) pl.dropLast(1) else pl
        }
    }

    /** "sound mixers", "DPs", "1st ADs". */
    fun rolePlural(code: RoleCode): String = when (code) {
        RoleCode.DP -> "DPs"
        RoleCode.FIRST_AD -> "1st ADs"
        RoleCode.SECOND_AD -> "2nd ADs"
        RoleCode.FIRST_AC -> "1st ACs"
        RoleCode.VFX_ARTIST -> "VFX artists"
        RoleCode.BTS_STILLS -> "BTS and stills shooters"
        RoleCode.HMU -> "hair & makeup artists"
        RoleCode.COSTUME -> "costume designers"
        else -> roleName(code).lowercase(Locale.ENGLISH) + "s"
    }

    internal fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** Share-out text (F17, S11, S16V). No dead links: blank base URL → Play Store line. */
object ShareText {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.tff.app"

    /**
     * ```
     * CREW CALL · Sound Mixer · "Filter Coffee, 4 AM" (Kannada/English short, 12 min)
     * Sat 14 – Sun 15 Nov · 2 days · ≤10h · Jayanagar, Bengaluru
     * Pay: ₹2,500/day + ₹1,000 kit · paid on wrap · 2 meals
     * Apply free on Calltime: <SHARE_BASE_URL>/r/<roleId>
     * ```
     * Cast roles say CASTING CALL. Post roles replace the dates line with "Remote · picture lock 10 Jan · deliver by 7 Feb".
     */
    fun roleCard(item: RoleWithProject, baseUrl: String): String {
        val r = item.role
        val p = item.project
        val head = if (r.isCast) "CASTING CALL" else "CREW CALL"
        val langs = CardText.languageNames(p.languages)
        val fmt = CardText.formatLabel(p.format).lowercase(java.util.Locale.ENGLISH)
        val about = if (langs.isBlank()) "${CardText.formatLabel(p.format)}, ${p.runtimeMin} min" else "$langs $fmt, ${p.runtimeMin} min"
        val line1 = "$head · ${CardText.roleName(r)}${if (r.count > 1) " ×${r.count}" else ""} · \"${p.title}\" ($about)"
        val place = CardText.placeName(item.areaName, item.cityName)

        val line2 = if (r.isPostRole) {
            val parts = mutableListOf("Remote")
            if (CardText.needsPictureLock(r.roleCode)) p.pictureLockDate?.let { parts += "picture lock ${Dates.dayMonth(it)}" }
            parts += "deliver by ${Dates.dayMonth(r.postDeadline!!)}"
            parts.joinToString(" · ")
        } else {
            val start = p.shootStart
            val end = p.shootEnd
            val parts = mutableListOf<String>()
            if (r.remoteOk) parts += "Remote"
            if (start != null && end != null) {
                parts += Dates.span(start, end)
                parts += if (p.shootDays == 1) "1 day" else "${p.shootDays} days"
                p.hoursCap?.let { parts += "≤${it}h" }
            } else {
                parts += "Dates to be confirmed"
            }
            if (!r.remoteOk) parts += place
            parts.joinToString(" · ")
        }

        var pay = CardText.payLine(r, p)
        if (r.isPaid && !r.isPostRole) p.meals?.let { m -> CardText.mealsShort(m)?.let { pay += " · $it" } }
        val line3 = "Pay: $pay"
        val line4 = if (baseUrl.isBlank()) "Apply free on Calltime (Google Play): $PLAY_URL"
        else "Apply free on Calltime: ${baseUrl.trim().trimEnd('/')}/r/${r.id}"
        return listOf(line1, line2, line3, line4).joinToString("\n")
    }

    /**
     * ```
     * CALL SHEET · "Filter Coffee, 4 AM" · Day 1 of 2 · Sat 14 Nov
     * General call 07:00 · Wrap by 17:00
     * Location: <address> · Map: https://maps.google.com/?q=<urlencoded address>
     * Parking: <...> · Nearest hospital: <...>
     * Meals: breakfast + lunch
     * Your individual call time is in Calltime: https://calltime.app/d/<shootDayId>
     * ```
     */
    fun daySheet(project: Project, day: ShootDay, dayIndex: Int, dayCount: Int, baseUrl: String): String {
        val lines = mutableListOf<String>()
        val cancelled = if (day.cancelledAt != null) "CANCELLED · " else ""
        lines += "${cancelled}CALL SHEET · \"${project.title}\" · Day $dayIndex of $dayCount · ${Dates.short(day.day)}"
        lines += "General call ${Dates.time(day.generalCall)} · Wrap by ${Dates.time(day.wrapBy)}"
        val address = oneLine(day.address)
        lines += "Location: $address · Map: ${mapFor(day)}"
        val logistics = listOfNotNull(
            day.parkingNote?.let(::oneLine)?.takeIf { it.isNotBlank() }?.let { "Parking: $it" },
            day.hospitalNote?.let(::oneLine)?.takeIf { it.isNotBlank() }?.let { "Nearest hospital: $it" },
        )
        if (logistics.isNotEmpty()) lines += logistics.joinToString(" · ")
        day.mealsPlan?.let(::oneLine)?.takeIf { it.isNotBlank() }?.let { lines += "Meals: $it" }
        day.notes?.let(::oneLine)?.takeIf { it.isNotBlank() }?.let { lines += "Notes: $it" }
        lines += if (baseUrl.isBlank()) "Your individual call time is in Calltime (Google Play): $PLAY_URL"
        else "Your individual call time is in Calltime: ${baseUrl.trim().trimEnd('/')}/d/${day.id}"
        return lines.joinToString("\n")
    }

    /** For a trusted contact: project, address, call time, wrap-by. Nobody else's details. */
    fun trustedContact(project: Project, day: ShootDay, myCall: java.time.LocalTime?): String {
        val call = myCall ?: day.generalCall
        return listOf(
            "I'm on a shoot: \"${project.title}\" · ${Dates.short(day.day)}",
            "My call time ${Dates.time(call)} · Wrap by ${Dates.time(day.wrapBy)}",
            "Address: ${oneLine(day.address)}",
            "Map: ${mapFor(day)}",
            "Sent from Calltime so someone I trust knows where I'll be.",
        ).joinToString("\n")
    }

    fun mapsUrl(address: String): String = "https://maps.google.com/?q=${CardText.urlEncode(oneLine(address))}"

    /** Calendar event description for Add to calendar. */
    fun calendarDescription(project: Project, day: ShootDay): String {
        val lines = mutableListOf(
            "\"${project.title}\" · shoot day",
            "General call ${Dates.time(day.generalCall)} · Wrap by ${Dates.time(day.wrapBy)}",
            "Location: ${oneLine(day.address)}",
            "Map: ${mapFor(day)}",
        )
        day.parkingNote?.let(::oneLine)?.takeIf { it.isNotBlank() }?.let { lines += "Parking: $it" }
        day.hospitalNote?.let(::oneLine)?.takeIf { it.isNotBlank() }?.let { lines += "Nearest hospital: $it" }
        day.mealsPlan?.let(::oneLine)?.takeIf { it.isNotBlank() }?.let { lines += "Meals: $it" }
        day.notes?.let(::oneLine)?.takeIf { it.isNotBlank() }?.let { lines += "Notes: $it" }
        lines += "Your individual call time is in Calltime."
        return lines.joinToString("\n")
    }

    private fun mapFor(day: ShootDay): String = day.mapsUrl.takeIf { it.isNotBlank() } ?: mapsUrl(day.address)

    /** Multiline addresses read as one line in a WhatsApp message. */
    private fun oneLine(s: String): String = s.lines().map { it.trim().trimEnd(',') }.filter { it.isNotEmpty() }.joinToString(", ")
}
