package com.tff.app.domain

import com.tff.app.model.*
import java.time.LocalDate

/**
 * Card copy (PRD §6.5, Appendix D). Pure formatting so RoleCard, RoleDetail, share text and the booking
 * card all say exactly the same thing.
 */
object CardText {
    /** "₹2,500/day + ₹1,000 kit · paid on wrap" / "Stipend ₹800/day · 2 meals · TA ₹300" / "Unpaid · 2 meals · your scenes in 6 wks · step-up credit". */
    fun payLine(role: ProjectRole, project: Project): String = TODO()
    /** "SAT 14 – SUN 15 NOV · 2 DAYS · ≤10H · LOCKED" or "REMOTE · LOCK 10 JAN → DUE 7 FEB · 4 WKS" or "REMOTE · DUE 30 JAN". */
    fun whenLine(role: ProjectRole, project: Project): String = TODO()
    /** "Jayanagar, Bengaluru · Kannada/English". */
    fun whereLine(item: RoleWithProject): String = TODO()
    /** "SOUND MIXER ×1". */
    fun roleLabel(role: ProjectRole): String = TODO()
    /** "SHORT · 12 MIN". */
    fun formatChip(project: Project): String = TODO()
    /** "Open · 6/15 applied · replies in ~1 day". */
    fun footer(item: RoleWithProject): String = TODO()
    /** Perks with dates, for "What you get". */
    fun perksDetailed(role: ProjectRole, project: Project): List<String> = TODO()
    /** Plain-language booking summary ("You're booked as Sound Mixer (credit: 'Sound Recordist') on …"). */
    fun bookingSummary(t: BookingTerms): String = TODO()
    fun languageNames(codes: List<String>): String = TODO()     // "Kannada/English"
    fun levelLabel(l: Level): String = TODO()                   // "Building a reel"
    fun formatLabel(f: Format): String = TODO()
    fun termsLabel(t: PaymentTerms): String = TODO()            // "paid on wrap"
    fun perkLabel(p: Perk): String = TODO()
    fun contentFlagLabel(f: ContentFlag): String = TODO()
    fun mealsLabel(m: Meals): String = TODO()
    fun transportLabel(t: Transport): String = TODO()
}

/** Share-out text (F17, S11, S16V). No dead links: blank base URL → Play Store line. */
object ShareText {
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.tff.app"
    fun roleCard(item: RoleWithProject, baseUrl: String): String = TODO()
    fun daySheet(project: Project, day: ShootDay, dayIndex: Int, dayCount: Int, baseUrl: String): String = TODO()
    fun trustedContact(project: Project, day: ShootDay, myCall: java.time.LocalTime?): String = TODO()
    fun mapsUrl(address: String): String = TODO()
    /** Calendar event description for Add to calendar. */
    fun calendarDescription(project: Project, day: ShootDay): String = TODO()
}
