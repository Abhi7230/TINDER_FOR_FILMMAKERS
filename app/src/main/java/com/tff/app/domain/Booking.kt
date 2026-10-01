package com.tff.app.domain

import com.tff.app.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** PRD §6.4: pre-fills booking terms from the role + project (two-tap booking card). */
object BookingTermsFactory {
    const val PLEDGE_ON_SET = "If you can't make it, tell the production at least 72 hours before."
    const val PLEDGE_POST = "If you can't deliver by the date, tell the production at least 7 days before."
    const val PLEDGE_PRODUCTION = "If the production releases you or cancels with less than 72 hours' notice, it shows on their record."

    /** Credit is confirmed this long after the shoot or delivery when no final-film date was promised. */
    const val CREDIT_WEEKS_DEFAULT = 4L

    fun noticePledge(isPostRole: Boolean): String = (if (isPostRole) PLEDGE_POST else PLEDGE_ON_SET) + " " + PLEDGE_PRODUCTION

    fun fromRole(item: RoleWithProject, payerName: String): BookingTerms {
        val r = item.role
        val p = item.project
        val post = r.isPostRole
        val perks = r.perks.filter { it in STORED_PERKS }.toMutableSet()
        if (!post && p.lodging == true) perks += Perk.LODGING
        val terms = BookingTerms(
            roleCode = r.roleCode,
            creditTitle = r.creditTitle.ifBlank { Taxonomy.info(r.roleCode).defaultCredit },
            projectTitle = p.title,
            isPostRole = post,
            dates = if (post) emptyList() else p.shootDates.distinct().sorted(),
            deliveryDate = if (post) r.postDeadline else null,
            pictureLockDate = if (CardText.needsPictureLock(r.roleCode)) p.pictureLockDate else null,
            revisionRounds = revisionRounds(r.roleDetails),
            callWindow = null,
            compType = r.compType,
            amount = if (r.compType == CompType.UNPAID) null else r.amountMax ?: r.amountMin,
            currency = r.currency,
            kitFeeDay = if (r.compType == CompType.UNPAID) null else r.kitFeeDay,
            paymentTerms = if (r.compType == CompType.UNPAID) null else r.paymentTerms,
            perks = perks,
            meals = if (post) null else p.meals,
            transport = if (post) null else p.transport,
            travelAllowancePerDay = if (post || p.transport != Transport.MILEAGE_OR_TA_PAID) null else p.travelAllowancePerDay,
            footageDueWeeks = if (post) null else r.footageDueWeeks,
            finalFilmDueWeeks = r.finalFilmDueWeeks,
            payerId = p.payerId,
            payerName = payerName,
            noticePledge = noticePledge(post),
        )
        return normalize(terms)
    }

    private val STORED_PERKS = setOf(Perk.TRAVEL_COVERED, Perk.LODGING, Perk.FOOTAGE_BY_DATE, Perk.FINAL_FILM_BY_DATE, Perk.STEP_UP_CREDIT)

    private fun revisionRounds(details: Map<String, String>): Int? {
        val raw = details["revisions"] ?: details["revision_rounds"] ?: return null
        return Regex("\\d+").find(raw)?.value?.toIntOrNull()
    }

    /** The day the work is counted from: last shoot day (on-set) or delivery date (post role). */
    fun anchorDate(terms: BookingTerms): LocalDate? = if (terms.isPostRole) terms.deliveryDate else terms.dates.maxOrNull()

    /** pay_by_date: on-set = last shoot day + terms; post role = delivery date + terms. */
    fun payByDate(terms: BookingTerms): LocalDate? {
        if (terms.compType == CompType.UNPAID) return null
        val t = terms.paymentTerms ?: return null
        val anchor = anchorDate(terms) ?: return null
        return when (t) {
            PaymentTerms.ON_WRAP, PaymentTerms.HALF_UPFRONT -> anchor
            PaymentTerms.WITHIN_7_DAYS -> anchor.plusDays(7)
            PaymentTerms.WITHIN_15_DAYS -> anchor.plusDays(15)
            PaymentTerms.WITHIN_30_DAYS -> anchor.plusDays(30)
        }
    }

    /** Recompute derived fields (payByDate, deliverables) after an edit. */
    fun normalize(terms: BookingTerms): BookingTerms {
        val t = terms.copy(
            dates = if (terms.isPostRole) emptyList() else terms.dates.distinct().sorted(),
            notes = terms.notes?.trim()?.ifBlank { null },
            callWindow = terms.callWindow?.trim()?.ifBlank { null },
            amount = if (terms.compType == CompType.UNPAID) null else terms.amount,
            paymentTerms = if (terms.compType == CompType.UNPAID) null else terms.paymentTerms,
            kitFeeDay = if (terms.compType == CompType.UNPAID) null else terms.kitFeeDay?.takeIf { it > 0 },
        )
        val payBy = payByDate(t)
        return t.copy(payByDate = payBy, deliverables = deliverables(t, payBy))
    }

    private fun deliverables(t: BookingTerms, payBy: LocalDate?): List<Deliverable> {
        val anchor = anchorDate(t)
        val out = mutableListOf<Deliverable>()
        if (t.isPostRole) out += Deliverable(WrapKind.DELIVERY, t.deliveryDate, OwedBy.COLLABORATOR)
        if (t.compType != CompType.UNPAID) out += Deliverable(WrapKind.PAID, payBy, OwedBy.PRODUCTION)
        if (!t.isPostRole && Perk.FOOTAGE_BY_DATE in t.perks) {
            out += Deliverable(WrapKind.FOOTAGE, t.footageDueWeeks?.let { w -> anchor?.plusWeeks(w.toLong()) }, OwedBy.PRODUCTION)
        }
        val finalDue = if (Perk.FINAL_FILM_BY_DATE in t.perks) t.finalFilmDueWeeks?.let { w -> anchor?.plusWeeks(w.toLong()) } else null
        if (Perk.FINAL_FILM_BY_DATE in t.perks) out += Deliverable(WrapKind.FINAL_FILM, finalDue, OwedBy.PRODUCTION)
        out += Deliverable(WrapKind.CREDIT, finalDue ?: anchor?.plusWeeks(CREDIT_WEEKS_DEFAULT), OwedBy.PRODUCTION)
        return out
    }
}

/** Changed-field keys between two versions: "dates", "deliveryDate", "amount", "kitFeeDay", "paymentTerms", "perks", "meals", "transport", "callWindow", "creditTitle", "notes", "revisionRounds", "pictureLockDate". */
object BookingDiff {
    fun changedFields(old: BookingTerms, new: BookingTerms): Set<String> {
        val out = mutableSetOf<String>()
        if (old.dates.distinct().sorted() != new.dates.distinct().sorted()) out += "dates"
        if (old.deliveryDate != new.deliveryDate) out += "deliveryDate"
        if (old.amount != new.amount || old.compType != new.compType || !old.currency.equals(new.currency, true)) out += "amount"
        if ((old.kitFeeDay ?: 0) != (new.kitFeeDay ?: 0)) out += "kitFeeDay"
        if (old.paymentTerms != new.paymentTerms) out += "paymentTerms"
        if (old.perks != new.perks || old.footageDueWeeks != new.footageDueWeeks || old.finalFilmDueWeeks != new.finalFilmDueWeeks) out += "perks"
        if (old.meals != new.meals) out += "meals"
        if (old.transport != new.transport || old.travelAllowancePerDay != new.travelAllowancePerDay) out += "transport"
        if (clean(old.callWindow) != clean(new.callWindow)) out += "callWindow"
        if (old.creditTitle.trim() != new.creditTitle.trim() || old.roleCode != new.roleCode) out += "creditTitle"
        if (clean(old.notes) != clean(new.notes)) out += "notes"
        if (old.revisionRounds != new.revisionRounds) out += "revisionRounds"
        if (old.pictureLockDate != new.pictureLockDate) out += "pictureLockDate"
        return out
    }

    /** No changed field = no new version. */
    fun isNoOp(old: BookingTerms, new: BookingTerms): Boolean = changedFields(old, new).isEmpty()

    private fun clean(s: String?): String? = s?.trim()?.ifBlank { null }

    /** True when the change cuts days or pay (counts against Kept bookings if within 72h of the first day). */
    fun isCut(old: BookingTerms, new: BookingTerms): Boolean {
        if (!old.isPostRole && new.dates.distinct().size < old.dates.distinct().size) return true
        if (payRank(new.compType) < payRank(old.compType)) return true
        if (old.compType == new.compType && (new.amount ?: 0) < (old.amount ?: 0)) return true
        if ((new.kitFeeDay ?: 0) < (old.kitFeeDay ?: 0)) return true
        return totalValue(new) < totalValue(old)
    }

    private fun payRank(c: CompType) = when (c) { CompType.UNPAID -> 0; CompType.STIPEND -> 1; CompType.PAID_DAY, CompType.PAID_FLAT -> 2 }

    private fun totalValue(t: BookingTerms): Long {
        val days = maxOf(1, t.dates.distinct().size).toLong()
        val amount = (t.amount ?: 0).toLong()
        val kit = (t.kitFeeDay ?: 0).toLong()
        val base = when (t.compType) {
            CompType.PAID_DAY, CompType.STIPEND -> if (t.isPostRole) amount else amount * days
            CompType.PAID_FLAT -> amount
            CompType.UNPAID -> 0L
        }
        return base + kit * (if (t.isPostRole) 0 else days)
    }
}

/** Wrap checklist items from accepted terms (PRD F13). */
object WrapItemsFactory {
    fun fromTerms(matchId: String, terms: BookingTerms, idFor: (WrapKind) -> String): List<WrapItem> =
        BookingTermsFactory.normalize(terms).deliverables
            .distinctBy { it.kind }
            .map { d -> WrapItem(id = idFor(d.kind), matchId = matchId, kind = d.kind, dueDate = d.dueDate, owedBy = d.owedBy) }

    /** Past its due date and still owed (open, or reported not received). */
    fun isOverdue(item: WrapItem, today: LocalDate): Boolean {
        val due = item.dueDate ?: return false
        return (item.status == WrapStatus.OPEN || item.status == WrapStatus.NOT_RECEIVED) && today.isAfter(due)
    }

    /** DELIVERY items the owner hasn't answered within 7 days of the collaborator marking them are auto-confirmed. */
    const val DELIVERY_AUTO_CONFIRM_DAYS = 7L

    fun shouldAutoConfirm(item: WrapItem, nowMillis: Long): Boolean =
        item.kind == WrapKind.DELIVERY && item.status == WrapStatus.DONE &&
            item.markedAt != null && nowMillis - item.markedAt >= DELIVERY_AUTO_CONFIRM_DAYS * 24 * 3600_000L
}

/** Audition request validation (F20). Null = valid, else the error to show. */
object AuditionRules {
    const val SAFETY_LINES = "No nudity at auditions · No fees · Bring a friend if you like · You never have to meet alone"
    val EARLIEST: LocalTime = LocalTime.of(8, 0)
    val LATEST: LocalTime = LocalTime.of(21, 0)

    const val ERR_NO_MODE = "This listing doesn't say how auditions happen. Add an audition mode to the listing first."
    const val ERR_SIDES = "Add a link to the sides."
    const val ERR_DUE = "Pick a date the tape is due."
    const val ERR_WHEN = "Pick a date and time."
    const val ERR_HOURS = "Auditions happen between 08:00 and 21:00."
    const val ERR_VENUE = "Add the venue name and address."
    const val ERR_PRIVATE = "Auditions can't be at a private home or hotel room. Pick a studio or public space."
    const val ERR_ALSO_PRESENT = "Say who else will be there, by role (at least one person)."
    const val ERR_LINK_ONLY_VIDEO = "Meeting links are only for video-call auditions."
    const val ERR_PAST = "That date has passed. Pick a date from today on."
    const val ERR_MONEY = "Calltime never allows charging cast or crew."
    const val ERR_BODY = "Don't ask for body photos or measurements."

    private val HOME = Regex("""\b(?:my|our|his|her)\s+(?:home|house|place|flat|apartment|room|bedroom)\b|\bhotel\s+room\b|\b(?:ghar|residence)\b""", RegexOption.IGNORE_CASE)

    fun modeLabel(m: AuditionMode): String = when (m) {
        AuditionMode.SELF_TAPE -> "self-tape"
        AuditionMode.VIDEO_CALL -> "video call"
        AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS -> "a studio or public space with others present"
    }

    fun validate(req: AuditionRequest, listingMode: AuditionMode?): String? = validate(req, listingMode, today = null)

    /** As [validate]; with [today] set, dates in the past are rejected too. */
    fun validate(req: AuditionRequest, listingMode: AuditionMode?, today: LocalDate?): String? {
        if (listingMode == null) return ERR_NO_MODE
        if (req.mode != listingMode) return "This listing says auditions are by ${modeLabel(listingMode)}. Use that."
        if (req.mode != AuditionMode.VIDEO_CALL && !req.meetingLink.isNullOrBlank()) return ERR_LINK_ONLY_VIDEO
        when (req.mode) {
            AuditionMode.SELF_TAPE -> {
                if (req.sidesUrl.isNullOrBlank() || !looksLikeUrl(req.sidesUrl)) return ERR_SIDES
                val due = req.dueDate ?: return ERR_DUE
                if (today != null && due.isBefore(today)) return ERR_PAST
            }
            AuditionMode.VIDEO_CALL -> {
                val date = req.date ?: return ERR_WHEN
                val time = req.time ?: return ERR_WHEN
                if (!withinHours(time)) return ERR_HOURS
                if (today != null && date.isBefore(today)) return ERR_PAST
            }
            AuditionMode.STUDIO_OR_PUBLIC_WITH_OTHERS -> {
                if (req.venue.isNullOrBlank() || req.address.isNullOrBlank()) return ERR_VENUE
                val place = "${req.venue} ${req.address}"
                if (HOME.containsMatchIn(place) || MessageFlag.PRIVATE_MEETING in MessageSafety.analyze(place)) return ERR_PRIVATE
                val date = req.date ?: return ERR_WHEN
                val time = req.time ?: return ERR_WHEN
                if (!withinHours(time)) return ERR_HOURS
                if (today != null && date.isBefore(today)) return ERR_PAST
                if (req.alsoPresent.none { it.isNotBlank() }) return ERR_ALSO_PRESENT
            }
        }
        val free = listOfNotNull(req.note, req.venue, req.address).joinToString(" \n ")
        if (free.isNotBlank()) {
            val flags = MessageSafety.analyze(free)
            if (MessageFlag.MONEY_FROM_TALENT in flags) return ERR_MONEY
            if (MessageFlag.BODY_REQUEST in flags) return ERR_BODY
            if (MessageFlag.PRIVATE_MEETING in flags) return ERR_PRIVATE
        }
        return null
    }

    /** 08:00–21:00 inclusive. */
    fun withinHours(t: LocalTime): Boolean = !t.isBefore(EARLIEST) && !t.isAfter(LATEST)

    private fun looksLikeUrl(s: String): Boolean {
        val t = s.trim()
        return Regex("""^(https?://)?[\w.-]+\.[a-z]{2,}(/\S*)?$""", RegexOption.IGNORE_CASE).matches(t)
    }
}

/** Notice computed for withdraw / release sheets. */
object Notice {
    const val ON_SET_NEUTRAL_HOURS = 72
    const val POST_NEUTRAL_HOURS = 7 * 24

    /** Hours from [nowMillis] to the first shoot day (on-set) or the delivery date (post role). */
    fun hoursUntil(terms: BookingTerms, nowMillis: Long): Int = hoursUntil(terms, nowMillis, ZoneId.systemDefault())

    /**
     * As [hoursUntil] in an explicit zone. On-set notice runs to the call-window start on day 1 when the
     * window is set ("~7:00–17:00"), else to the start of day 1. Post roles run to the start of the delivery date.
     */
    fun hoursUntil(terms: BookingTerms, nowMillis: Long, zone: ZoneId): Int {
        val target = if (terms.isPostRole) {
            terms.deliveryDate?.atStartOfDay(zone)
        } else {
            val first = terms.dates.minOrNull()
            first?.atTime(callStart(terms.callWindow) ?: LocalTime.MIDNIGHT)?.atZone(zone)
        } ?: return Int.MAX_VALUE
        val hours = ChronoUnit.HOURS.between(Instant.ofEpochMilli(nowMillis), target.toInstant())
        return hours.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    /** On-set: ≥72h is neutral. Post role: ≥7 days (168h) before delivery is neutral. */
    fun isNeutral(terms: BookingTerms, hours: Int): Boolean = isNeutral(terms.isPostRole, hours)

    fun isNeutral(isPostRole: Boolean, hours: Int): Boolean = hours >= if (isPostRole) POST_NEUTRAL_HOURS else ON_SET_NEUTRAL_HOURS

    /** "4 days' notice: this won't count against you" / "Less than 72 hours' notice. …" for the withdraw sheet. */
    fun withdrawLine(terms: BookingTerms, hours: Int): String {
        val notice = noticeText(hours)
        return if (isNeutral(terms, hours)) "$notice: this won't count against you."
        else if (terms.isPostRole) "$notice before delivery. Less than 7 days counts against your Delivered on time."
        else "$notice. Less than 72 hours counts against your Showed up record."
    }

    /** The honest line on the owner's Release sheet. */
    fun releaseLine(hours: Int): String? =
        if (hours < ON_SET_NEUTRAL_HOURS) "Less than 72 hours' notice. This will show in your Kept bookings." else null

    /** "4 days' notice", "30 hours' notice", "1 hour's notice". */
    fun noticeText(hours: Int): String = when {
        hours <= 0 -> "No notice"
        hours < 48 -> if (hours == 1) "1 hour's notice" else "$hours hours' notice"
        else -> { val d = hours / 24; "$d days' notice" }
    }

    private fun callStart(window: String?): LocalTime? {
        if (window.isNullOrBlank()) return null
        val m = Regex("""(\d{1,2})(?::(\d{2}))?""").find(window) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }
}
