package com.tff.app.domain

import com.tff.app.model.*
import java.time.LocalDate

/** PRD §6.4: pre-fills booking terms from the role + project (two-tap booking card). */
object BookingTermsFactory {
    fun fromRole(item: RoleWithProject, payerName: String): BookingTerms = TODO()
    /** pay_by_date: on-set = last shoot day + terms; post role = delivery date + terms. */
    fun payByDate(terms: BookingTerms): LocalDate? = TODO()
    /** Recompute derived fields (payByDate, deliverables) after an edit. */
    fun normalize(terms: BookingTerms): BookingTerms = TODO()
}

/** Changed-field keys between two versions: "dates", "deliveryDate", "amount", "kitFeeDay", "paymentTerms", "perks", "meals", "transport", "callWindow", "creditTitle", "notes", "revisionRounds", "pictureLockDate". */
object BookingDiff {
    fun changedFields(old: BookingTerms, new: BookingTerms): Set<String> = TODO()
    /** True when the change cuts days or pay (counts against Kept bookings if within 72h of the first day). */
    fun isCut(old: BookingTerms, new: BookingTerms): Boolean = TODO()
}

/** Wrap checklist items from accepted terms (PRD F13). */
object WrapItemsFactory {
    fun fromTerms(matchId: String, terms: BookingTerms, idFor: (WrapKind) -> String): List<WrapItem> = TODO()
    fun isOverdue(item: WrapItem, today: LocalDate): Boolean = TODO()
}

/** Audition request validation (F20). Null = valid, else the error to show. */
object AuditionRules {
    const val SAFETY_LINES = "No nudity at auditions · No fees · Bring a friend if you like · You never have to meet alone"
    fun validate(req: AuditionRequest, listingMode: AuditionMode?): String? = TODO()
}

/** Notice computed for withdraw / release sheets. */
object Notice {
    /** Hours from [nowMillis] to the first shoot day (on-set) or the delivery date (post role). */
    fun hoursUntil(terms: BookingTerms, nowMillis: Long): Int = TODO()
    /** On-set: ≥72h is neutral. Post role: ≥7 days (168h) before delivery is neutral. */
    fun isNeutral(terms: BookingTerms, hours: Int): Boolean = TODO()
}
