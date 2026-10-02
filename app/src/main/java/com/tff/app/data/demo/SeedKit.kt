package com.tff.app.data.demo

import com.tff.app.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure helpers shared by DemoSeed and DemoEngine: booking terms and wrap items built the same way the
 * domain factories do, so seeded history and runtime bookings look identical. Kept independent of the
 * domain factories so the seed never depends on their internals.
 */
object SeedKit {
    const val DAY = 86_400_000L
    const val HOUR = 3_600_000L

    const val NOTICE_ON_SET = "If you can't make it, tell the production at least 72 hours before. " +
        "If the production releases you or cancels with less than 72 hours' notice, it shows on their record."
    const val NOTICE_POST = "If you can't deliver by the date, tell the production at least 7 days before. " +
        "If the production releases you or cancels with less than 72 hours' notice, it shows on their record."

    fun termsDays(t: PaymentTerms?): Long = when (t) {
        PaymentTerms.WITHIN_7_DAYS -> 7
        PaymentTerms.WITHIN_15_DAYS -> 15
        PaymentTerms.WITHIN_30_DAYS -> 30
        else -> 0
    }

    fun nextDow(from: LocalDate, dow: DayOfWeek): LocalDate {
        var d = from
        while (d.dayOfWeek != dow) d = d.plusDays(1)
        return d
    }

    fun millisAt(d: LocalDate, t: LocalTime, zone: ZoneId): Long = d.atTime(t).atZone(zone).toInstant().toEpochMilli()

    fun callWindow(p: Project): String? {
        val cap = p.hoursCap ?: return null
        val start = 7
        val end = (start + cap).coerceAtMost(23)
        return "~" + start.toString().padStart(2, '0') + ":00–" + end.toString().padStart(2, '0') + ":00"
    }

    /** Booking terms pre-filled from the role and project (PRD §6.4). */
    fun terms(role: ProjectRole, project: Project, payerId: String, payerName: String): BookingTerms {
        val post = role.isPostRole
        val dates = if (post) emptyList() else project.shootDates.sorted()
        val base: LocalDate? = if (post) role.postDeadline else dates.maxOrNull()
        val payBy = if (role.compType == CompType.UNPAID) null else base?.plusDays(termsDays(role.paymentTerms))
        val needsLock = role.roleCode == RoleCode.COMPOSER || role.roleCode == RoleCode.SOUND_DESIGNER
        val draft = BookingTerms(
            roleCode = role.roleCode,
            creditTitle = com.tff.app.domain.BookingTermsFactory.creditTitle(role),
            projectTitle = project.title,
            isPostRole = post,
            dates = dates,
            deliveryDate = if (post) role.postDeadline else null,
            pictureLockDate = if (needsLock) project.pictureLockDate else null,
            revisionRounds = if (post) (role.roleDetails["revisions"]?.toIntOrNull() ?: 2) else null,
            callWindow = if (post) null else callWindow(project),
            compType = role.compType,
            amount = if (role.compType == CompType.UNPAID) null else (role.amountMax ?: role.amountMin),
            currency = role.currency,
            kitFeeDay = role.kitFeeDay,
            paymentTerms = if (role.compType == CompType.UNPAID) null else role.paymentTerms,
            payByDate = payBy,
            perks = role.perks.filter { it in STORED_PERKS }.toSet(),
            meals = if (post) null else project.meals,
            transport = if (post) null else project.transport,
            travelAllowancePerDay = if (post) null else project.travelAllowancePerDay,
            footageDueWeeks = role.footageDueWeeks,
            finalFilmDueWeeks = role.finalFilmDueWeeks,
            payerId = payerId,
            payerName = payerName,
            noticePledge = if (post) NOTICE_POST else NOTICE_ON_SET,
            footageDefinition = com.tff.app.domain.BookingTermsFactory.footageDefinition(role.roleCode),
            rightsNote = com.tff.app.domain.BookingTermsFactory.rightsNote(role.roleCode),
        )
        return draft.copy(deliverables = deliverables(draft))
    }

    val STORED_PERKS = setOf(Perk.TRAVEL_COVERED, Perk.LODGING, Perk.FOOTAGE_BY_DATE, Perk.FINAL_FILM_BY_DATE, Perk.STEP_UP_CREDIT)

    /** Deliverables from terms: on-set PAID/FOOTAGE/FINAL_FILM/CREDIT; post DELIVERY (collaborator)/PAID/FINAL_FILM/CREDIT. */
    fun deliverables(t: BookingTerms): List<Deliverable> {
        val base: LocalDate? = if (t.isPostRole) t.deliveryDate else t.dates.maxOrNull()
        val out = mutableListOf<Deliverable>()
        if (t.isPostRole) out += Deliverable(WrapKind.DELIVERY, t.deliveryDate, OwedBy.COLLABORATOR)
        if (t.compType != CompType.UNPAID) out += Deliverable(WrapKind.PAID, t.payByDate ?: base, OwedBy.PRODUCTION)
        if (!t.isPostRole && Perk.FOOTAGE_BY_DATE in t.perks) out += Deliverable(WrapKind.FOOTAGE, base?.plusWeeks((t.footageDueWeeks ?: 8).toLong()), OwedBy.PRODUCTION)
        val finalDue = if (Perk.FINAL_FILM_BY_DATE in t.perks) base?.plusWeeks((t.finalFilmDueWeeks ?: 12).toLong()) else null
        if (finalDue != null) out += Deliverable(WrapKind.FINAL_FILM, finalDue, OwedBy.PRODUCTION)
        out += Deliverable(WrapKind.CREDIT, finalDue ?: base?.plusDays(60), OwedBy.PRODUCTION)
        return out
    }

    fun wrapItems(matchId: String, t: BookingTerms): List<WrapItem> =
        t.deliverables.distinctBy { it.kind }.map { WrapItem(id = "wi_${matchId}_${it.kind.name}", matchId = matchId, kind = it.kind, dueDate = it.dueDate, owedBy = it.owedBy) }

    fun mapsUrl(address: String): String = "https://maps.google.com/?q=" + java.net.URLEncoder.encode(address, "UTF-8")
}
