package com.tff.app.data.demo

import com.tff.app.core.Dates
import com.tff.app.core.Money
import com.tff.app.domain.CardText
import com.tff.app.model.*
import kotlin.random.Random

/**
 * Every line of copy the demo world produces at runtime: closures, system messages, simulated replies,
 * notification titles. Plain, crew-to-crew, no hype (PRD Appendix D). Pure Kotlin.
 */
object DemoText {

    // ── Closures (the "every applicant gets an answer" guarantee) ──
    const val CLOSURE_DECLINED = "This role went another way. Thanks for applying."
    const val CLOSURE_FILLED = "This role has been filled. Thanks for applying."
    const val CLOSURE_EXPIRED = "No answer from this project. That counts against their response rate."
    const val CLOSURE_ROLE_CLOSED = "This role closed before the production decided. Thanks for applying."
    const val CLOSURE_CANCELLED = "The production cancelled this project. Thanks for applying."
    const val BACKUP_LINE = "You're on the backup list. If a slot opens, you'll hear first."

    const val MATCH_AUTO_CLOSED = "No booking card after 7 days, so this conversation has closed. No one is marked down for it."
    const val BLOCK_ENDED = "This conversation has ended."
    const val SAFETY_EXAMPLE_NOTE = "Registration fee of ₹2,500 to lock your slot. WhatsApp me on 98201 47736 for the payment details."

    /** Seeded scam opener (tagged safetyExample; the only seed text allowed to carry contact info). */
    fun scamOpener(firstName: String): String =
        "Hi $firstName, you're shortlisted for the lead. To confirm your slot there is a one-time registration fee of ₹2,500 " +
            "for the portfolio shoot. WhatsApp me on 98201 47736 and I'll send the payment details today."

    // ── Role names (short crew-facing names; sentence case for chat copy) ──
    fun roleName(code: RoleCode): String = when (code) {
        RoleCode.FIRST_AD -> "1st AD"
        RoleCode.SECOND_AD -> "2nd AD"
        RoleCode.DP -> "DP"
        RoleCode.FIRST_AC -> "1st AC"
        RoleCode.SOUND_MIXER -> "Sound mixer"
        RoleCode.PA -> "Production assistant"
        RoleCode.SOUND_DESIGNER -> "Sound designer"
        RoleCode.HMU -> "Hair & makeup"
        RoleCode.COSTUME -> "Costume"
        RoleCode.LOCATION_MANAGER -> "Location manager"
        else -> Taxonomy.label(code)
    }

    fun roleName(role: ProjectRole): String {
        if (role.roleCode == RoleCode.ACTOR) {
            when (role.roleDetails["size"]?.uppercase()) {
                "LEAD" -> return "Lead actor"
                "SUPPORTING" -> return "Supporting actor"
                "DAY_PLAYER" -> return "Day player"
            }
        }
        return roleName(role.roleCode)
    }

    fun firstName(p: Profile?): String = p?.displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull() ?: "there"

    /** Pay line from CardText, with a plain fallback so the demo never shows a blank. */
    fun payLine(role: ProjectRole, project: Project): String = runCatching { CardText.payLine(role, project) }.getOrElse {
        when (role.compType) {
            CompType.PAID_DAY -> Money.range(role.amountMin, role.amountMax, role.currency) + "/day" +
                (role.kitFeeDay?.let { " + ${Money.format(it, role.currency)} kit" } ?: "")
            CompType.PAID_FLAT -> Money.range(role.amountMin, role.amountMax, role.currency) + " flat"
            CompType.STIPEND -> "Stipend " + Money.range(role.amountMin, role.amountMax, role.currency) + if (role.isPostRole) "" else "/day"
            CompType.UNPAID -> "Unpaid" + (if (Perk.FOOTAGE_BY_DATE in role.perks) " · your scenes in ${role.footageDueWeeks ?: 8} wks" else "")
        }
    }

    /** System message that opens every chat: the terms, so nobody has to ask "what's the rate?". */
    fun termsSummary(role: ProjectRole, project: Project, areaName: String, cityName: String): String {
        val where = if (role.isPostRole) "Remote" else "$areaName, $cityName"
        return "In talks for ${roleName(role)} on ${project.title}. Terms on the listing: ${payLine(role, project)} · " +
            "${whenPhrase(role, project)} · $where. The booking card comes next."
    }

    /** "Sat 14 – Sun 15 Nov · 2 days · ≤10h" or "deliver by 7 Feb". */
    fun whenPhrase(role: ProjectRole, project: Project): String {
        if (role.isPostRole) {
            val lock = project.pictureLockDate?.takeIf { role.roleCode == RoleCode.COMPOSER || role.roleCode == RoleCode.SOUND_DESIGNER }
            return listOfNotNull(lock?.let { "picture lock ${Dates.dayMonth(it)}" }, role.postDeadline?.let { "deliver by ${Dates.dayMonth(it)}" })
                .joinToString(" · ").ifBlank { "remote" }
        }
        val s = project.shootStart ?: return "dates to be confirmed"
        val days = project.shootDays
        return listOfNotNull(Dates.span(s, project.shootEnd ?: s), if (days == 1) "1 day" else "$days days", project.hoursCap?.let { "≤${it}h" })
            .joinToString(" · ")
    }

    // ── Seeded counterpart chat ──
    fun ownerOpener(collabFirst: String, project: Project, role: ProjectRole, rnd: Random, hinglish: Boolean): String {
        val days = when {
            role.isPostRole -> null
            project.shootDays >= 2 && project.shootDates.all { it.dayOfWeek.value == 6 } -> "both Saturdays"
            project.shootDays >= 2 -> "both days"
            project.shootDays == 1 -> "the date"
            else -> null
        }
        val pool = when {
            role.isPostRole -> listOf(
                "Hi $collabFirst, liked your reel. Does the delivery date work for you?",
                "Hi $collabFirst, thanks for applying. Can you hit the delivery date on the listing?",
            )
            role.isCast -> listOf(
                "Hey $collabFirst, saw your reel. Are ${days ?: "the dates"} OK?",
                "Hi $collabFirst, thanks for applying. I'd like to see a short tape first. Are ${days ?: "the dates"} OK?",
            )
            else -> listOf(
                "Hi $collabFirst, saw your work. Are ${days ?: "the dates"} OK for you?",
                "Hey $collabFirst, your reel is what we need. Can you do ${days ?: "the dates"}?",
            )
        }
        val hi = listOf(
            "Hi $collabFirst, aapka reel dekha. ${days?.let { "Dono din" } ?: "Dates"} theek hain?",
            "Hey $collabFirst, reel achha laga. Dates chalega?",
        )
        return if (hinglish && rnd.nextDouble() < 0.4) hi.random(rnd) else pool.random(rnd)
    }

    fun collaboratorOpener(ownerFirst: String, rnd: Random): String = listOf(
        "Thanks $ownerFirst. Happy to talk. Are the dates fixed?",
        "Hi $ownerFirst, thanks. The dates work for me. What's the call time like?",
        "Thanks for the yes. Send the booking card when you're ready.",
    ).random(rnd)

    enum class Intent { DATES, PAY, LOCATION, CALL_TIME, YES, THANKS, CANT, OTHER }

    fun intentOf(text: String): Intent {
        val t = text.lowercase()
        return when {
            Regex("\\b(can'?t|cannot|won'?t|unable|not able|busy)\\b").containsMatchIn(t) -> Intent.CANT
            Regex("call time|what time|\\btiming|kitne baje").containsMatchIn(t) -> Intent.CALL_TIME
            Regex("\\b(pay|paid|rate|fee|stipend|money|budget|₹|\\$|kit)\\b").containsMatchIn(t) || t.contains("₹") -> Intent.PAY
            Regex("\\b(where|location|address|place|area|parking)\\b").containsMatchIn(t) -> Intent.LOCATION
            Regex("\\b(date|dates|day|days|saturday|sunday|weekend|when)\\b").containsMatchIn(t) -> Intent.DATES
            Regex("\\b(thanks|thank you|thx|shukriya|dhanyavad)\\b").containsMatchIn(t) -> Intent.THANKS
            Regex("\\b(yes|yeah|sure|ok|okay|in|done|haan|ha|great|perfect)\\b").containsMatchIn(t) -> Intent.YES
            else -> Intent.OTHER
        }
    }

    /** Reply from a seeded counterpart (owner or collaborator side). Never contains contact details. */
    fun reply(intent: Intent, fromOwner: Boolean, project: Project, role: ProjectRole, rnd: Random, hinglish: Boolean): String {
        val dates = project.shootStart?.let { s -> Dates.span(s, project.shootEnd ?: s) }
        val en: List<String> = when (intent) {
            Intent.DATES -> if (fromOwner) listOf(
                dates?.let { "Dates are $it, ${project.dateCertainty?.let { c -> if (c == DateCertainty.LOCKED) "locked" else "still tentative" } ?: "as listed"}." }
                    ?: "No shoot dates for this one. It's all about the delivery date on the card.",
                "Same dates as the listing. If anything moves you'll get a new booking card to accept.",
            ) else listOf("Those dates work for me.", "I'm free on both days. Blocking them now.")
            Intent.PAY -> if (fromOwner) listOf(
                "Pay is what's on the card: ${payLine(role, project)}.",
                "It's exactly as listed. I'll put it on the booking card so it's in writing.",
            ) else listOf("The listed terms are fine for me.", "That works. Please keep it on the booking card.")
            Intent.LOCATION -> if (fromOwner) listOf(
                "Exact address goes on the day sheet once you're booked. It's near the station, easy by train.",
                "Address comes with the day sheet after booking. There's parking nearby.",
            ) else listOf("I can get there by train, no problem.", "That's close to me. Fine.")
            Intent.CALL_TIME -> if (fromOwner) listOf(
                "General call around 7, wrap by ${project.hoursCap?.let { 7 + it }?.coerceAtMost(22) ?: 17}:00. Exact time goes on the day sheet.",
                "Call is early, around 7. You'll get your own call time on the day sheet.",
            ) else listOf("7 is fine for me.", "I can do an early call.")
            Intent.YES -> if (fromOwner) listOf("Great. I'll send the booking card shortly.", "Good. Booking card is next.")
            else listOf("Sounds good.", "I'm in.")
            Intent.THANKS -> if (fromOwner) listOf("Thank you. See you on set.", "Thanks, talk soon.") else listOf("Thanks.", "Thank you, see you then.")
            Intent.CANT -> if (fromOwner) listOf("No problem, thanks for telling me early.", "Understood. Thanks for being upfront.")
            else listOf("Sorry, I can't make that work.", "I'll have to pass on that, sorry.")
            Intent.OTHER -> if (fromOwner) listOf("Noted. Anything else before I send the card?", "Got it. I'll keep that in mind.")
            else listOf("Noted.", "Okay, got it.")
        }
        val hi: List<String> = when (intent) {
            Intent.DATES -> listOf(if (fromOwner) "Dates wahi hain jo listing pe hain." else "Dates theek hain, block kar liya.")
            Intent.PAY -> listOf(if (fromOwner) "Pay listing jaisa hi hai, booking card pe likh denge." else "Terms theek hain.")
            Intent.LOCATION -> listOf(if (fromOwner) "Address day sheet pe aayega booking ke baad." else "Main train se aa jaungi, no problem.")
            Intent.CALL_TIME -> listOf(if (fromOwner) "Call 7 baje ke aas paas hai. Exact time day sheet pe." else "7 baje chalega.")
            Intent.YES -> listOf(if (fromOwner) "Badhiya. Booking card bhejta hoon." else "Done, main in hoon.")
            Intent.THANKS -> listOf("Shukriya.")
            Intent.CANT -> listOf(if (fromOwner) "Koi baat nahi, bataane ke liye thanks." else "Sorry, ye nahi ho payega.")
            Intent.OTHER -> listOf(if (fromOwner) "Theek hai, noted." else "Okay, samajh gaya.")
        }
        return if (hinglish && rnd.nextDouble() < 0.35) hi.random(rnd) else en.random(rnd)
    }

    val ASK_TO_CHANGE = listOf(
        "Could call be 8 am? Last train timing.",
        "Could we add one more meal? It's a long day.",
        "Could the kit fee go on the card? I'm bringing my own kit.",
    )

    fun askDeclineLabel(r: AskDeclineReason): String = when (r) {
        AskDeclineReason.DATES_DONT_WORK -> "Dates don't work"
        AskDeclineReason.TOO_FAR -> "Too far"
        AskDeclineReason.NOT_MY_KIND -> "Not my kind of project"
        AskDeclineReason.PAID_ONLY_NOW -> "Paid work only right now"
    }

    fun withdrawLabel(r: WithdrawReason): String = when (r) {
        WithdrawReason.DATES_CHANGED -> "Dates no longer work"
        WithdrawReason.PAID_WORK -> "Paid work came up"
        WithdrawReason.HEALTH_FAMILY -> "Health or family"
        WithdrawReason.TERMS_CHANGED -> "Terms changed"
        WithdrawReason.SAFETY -> "Safety concern"
        WithdrawReason.OTHER -> "Other"
    }

    fun releaseLabel(r: ReleaseReason): String = when (r) {
        ReleaseReason.ROLE_CUT -> "Role cut from the script"
        ReleaseReason.SCHEDULE_CHANGED -> "Schedule changed"
        ReleaseReason.FOUND_SOMEONE_ELSE -> "Went with someone else"
        ReleaseReason.PROJECT_CANCELLED -> "Project cancelled"
        ReleaseReason.CONDUCT -> "Conduct"
        ReleaseReason.OTHER -> "Other"
    }

    fun noticeText(hours: Int): String = when {
        hours >= 48 -> "${hours / 24} days' notice"
        hours == 1 -> "1 hour's notice"
        hours >= 0 -> "$hours hours' notice"
        else -> "after the start date"
    }

    // ── Applications written by seeded people ──
    fun applicantNote(p: Profile, role: ProjectRole, rnd: Random): String {
        val pool = when (Taxonomy.dept(role.roleCode)) {
            Dept.CAST -> listOf(
                "Theatre background, comfortable with long takes. The dates work.",
                "I can be off-book a week before. Happy to self-tape.",
                "Free on the shoot dates. I live close to the location.",
                "Read the logline twice. I'd love to play this.",
            )
            Dept.SOUND -> if (p.gear.isNotEmpty()) listOf(
                "Own kit with lavs and a boom. Can bring a boom op for longer days.",
                "I'll bring my recorder and two wireless lavs. Dates work.",
            ) else listOf("Comfortable on boom and lav wiring. Dates work.", "I can recce the location before the shoot.")
            Dept.CAMERA -> listOf(
                "Night exteriors are my favourite. Happy to share a look reference.",
                "Can bring my own kit if it helps the budget.",
                "Free both days. I'd like to see the shot list early.",
            )
            Dept.GE -> listOf("Small LED kit, can run power off a genny or house supply.", "Free both days, can bring a grip.")
            Dept.POST, Dept.MUSIC -> listOf(
                "Remote is fine. I'll need picture lock and a delivery date in writing.",
                "I can deliver a first cut inside the window. Two revision rounds is fine.",
                "Happy to do a quick call about references before starting.",
            )
            Dept.ART -> listOf("I can source on a small budget. Happy to do a recce.", "Free on the shoot dates. Can bring basic kit.")
            else -> listOf("The dates work and I'm close by.", "Happy to help keep the day on time.")
        }
        return pool.random(rnd)
    }

    fun screeningAnswer(rnd: Random): String = listOf(
        "Yes, both dates are clear and I've done night shoots before.",
        "I've worked on four shorts like this one. Happy to share the links.",
        "Comfortable with that. I'd want to read the full scene first.",
        "Yes. I'm free all weekend and can rehearse on the Friday evening.",
    ).random(rnd)

    // ── Wrap / feedback ──
    const val DEMO_TAPE = "https://demo.calltime.app/tape/"
    const val DEMO_DELIVERY = "https://demo.calltime.app/delivery/"
    const val DEMO_FOOTAGE = "https://demo.calltime.app/footage/"
    const val DEMO_SIDES = "https://demo.calltime.app/sides/"

    // ── Notifications ──
    fun applicationReceivedTitle(roleName: String) = "New applicant · $roleName"
}
