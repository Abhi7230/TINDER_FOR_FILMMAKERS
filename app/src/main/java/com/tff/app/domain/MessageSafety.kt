package com.tff.app.domain

import com.tff.app.model.MessageFlag

enum class BannerLevel { DANGER, WARN, INFO }

data class SafetyBannerSpec(
    val flag: MessageFlag,
    val level: BannerLevel,
    val text: String,
    val offerReport: Boolean,
    val offerBlock: Boolean,
)

/** PRD §5.2 + Appendix C. Pure Kotlin; patterns are case-insensitive, English + Hinglish. */
object MessageSafety {
    const val MASK = "•••• shared contact hidden"

    const val TEXT_MONEY = "Calltime never allows charging cast or crew. This is a common scam."
    const val TEXT_BODY = "You never need to send body photos or measurements to apply. Report?"
    const val TEXT_BODY_TO_OWNER = "This message asks for body photos or measurements. That isn't allowed on Calltime. Report?"
    const val TEXT_OFF_PLATFORM = "Keeping talks on Calltime until you're booked protects you. Here's why."
    const val TEXT_PRIVATE = "Normal auditions are self-tape, video call, or a public or studio space with others present."

    const val DRAFT_CONTACT = "Contact details stay hidden until you're booked."
    const val DRAFT_MONEY = "Calltime never allows charging cast or crew. This message will be flagged."
    const val DRAFT_BODY = "Don't ask for body photos or measurements. This message will be flagged."

    private val I = setOf(RegexOption.IGNORE_CASE)

    // ── CONTACT (Appendix C) ──
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val HANDLE = Regex("""(?<![\w@])@[A-Za-z0-9_.]{3,30}\b""")
    /** 10–13 digits with optional spaces or dashes, not glued to other digits. */
    private val PHONE = Regex("""(?<![\d])(?:\+?\d[\s-]?){9,12}\d(?![\d])""")
    /** "@5pm", "@10:30" are times, not handles. */
    private val TIME_LIKE_HANDLE = Regex("""^@\d{1,2}([:.]\d{2})?(am|pm|hrs?)?$""", I)

    // ── OFF_PLATFORM: a platform name only counts when it's used as a channel ("WhatsApp me", "on Telegram"),
    // so "the WhatsApp scene in act 2" stays clean. ──
    private const val PLATFORMS = """whats\s?app|watsapp|whatsap|wa|telegram|signal|insta(?:gram)?|snap(?:chat)?|facebook|fb|messenger|gmail|email|e-mail|mail|phone|sms"""
    private val OFF_PLATFORM = listOf(
        Regex("""\b(?:wa\.me|t\.me|chat\.whatsapp\.com)/""", I),
        Regex("""\b(?:on|via|over|pe|par|per|through)\s+(?:my\s+)?(?:$PLATFORMS)\b(?!\s+(?:scene|sequence|shot|screen|booth))""", I),
        // "move to WhatsApp", "in my Insta": only messaging apps after to/in, so "need to mail the contract" stays clean.
        Regex("""\b(?:to|in)\s+(?:my\s+)?(?:whats\s?app|watsapp|telegram|signal|insta(?:gram)?|snap(?:chat)?|messenger)\b(?!\s+(?:scene|sequence|shot|screen))""", I),
        Regex("""\b(?:whats\s?app|watsapp|whatsap|telegram|signal|insta(?:gram)?|snap(?:chat)?)\s+(?:me|karo|kar\s*do|kardo|kar|pe|par|number|no\.?|num|id|dm|msg|message|group|chat|call|handle|pls|please)\b""", I),
        Regex("""\b(?:dm\s+me|dm\s+karo|text\s+me|call\s+me|email\s+me|e-mail\s+me|mail\s+me|ping\s+me|message\s+me\s+on|msg\s+me\s+on|reach\s+me\s+(?:on|at)|my\s+number|my\s+no\.|my\s+cell|mera\s+number|mera\s+no|apna\s+number|number\s+(?:do|de|dedo|de\s*do|bhejo|bhej\s*do|send|share)|call\s+karo|call\s+kar\s*lo|phone\s+karo|phone\s+kar\s*lo|contact\s+number|insta\s+id|instagram\s+id|gmail)\b""", I),
    )
    /** A bare platform name in a very short message ("WhatsApp?", "Telegram ok?"). */
    private val BARE_PLATFORM = Regex("""\b(?:whats\s?app|telegram|signal|instagram|insta)\b""", I)

    // ── MONEY_FROM_TALENT ──
    private val FEE_NOUN = Regex(
        """\b(?:registration|security|portfolio|audition|processing|joining|membership|profile|casting|workshop|training|selection|form|application|enrolment|enrollment)(?:\s+shoot)?\s*(?:fee|fees|charges?|deposit|amount|cost)\b""", I)
    private val FEE_NEGATION = Regex("""\b(?:no|never|zero|without|not|don't|dont|doesn't|free\s+of|nahi|nahin)\s+(?:\w+\s+){0,2}$""", I)
    private val PAY_VERB = Regex(
        """\b(pay|send|transfer|deposit|bharna|bharni|bharo|jama)\b(.{0,24}?)(₹|\brs\.?|\binr\b|\$|\busd\b|£|\bupi\b|zelle|paypal|venmo|gpay|google\s*pay|paytm|phonepe|cash\s*app)""", I)
    /** "we pay ₹2,500", "I'll send ₹500 for travel": the production paying, not asking. */
    private val PAYER_IS_US = Regex("""\b(?:i|we|i'll|we'll|ill|i\s+will|we\s+will|will|shall|i\s+can|we\s+can|i'd|we'd|production\s+will|me\s+to|us\s+to)\s*$""", I)
    private val PAYER_IS_YOU = Regex("""\b(?:you|u)\s+(?:need|have|must|will\s+have|'ll\s+have|ll\s+have|should|got)\s+to\s*$|\b(?:you|u)\s+(?:must|should)\s*$""", I)
    private val PAY_TO_YOU = Regex("""^\s*(?:you|u|ya|aapko|tumhe|tumko|tujhe|your)\b""", I)
    /** "Pay: ₹2,500/day", "pay is $350", "pay of ₹800": describing pay, not asking for it. */
    private val PAY_DESCRIBED = Regex("""^\s*(?:[:=\-–—]|is\b|was\b|will\s+be\b|would\s+be\b|of\b|rate\b|per\b|range\b|scale\b)""", I)
    private val MONEY_OTHER = listOf(
        Regex("""\bpay\s+(?:to|for)\s+(?:the\s+|your\s+|an?\s+)?(?:audition|role|slot|registration|portfolio|casting|workshop|selection|form)\b""", I),
        Regex("""\b(?:fee|fees|paise|paisa|amount|charges?|deposit)\s+(?:bharni|bharna|bharo|jama|dena|dene|do|bhejo|bhej\s*do)\b""", I),
        Regex("""\bpaise\s*(?:bhejo|do|dena)\b""", I),
        Regex("""\brefundable\s+(?:deposit|amount|fee|security)\b""", I),
        Regex("""\b(?:my|our|mera|hamara)\s+(?:upi|gpay|paytm|phonepe|zelle|venmo|paypal|cash\s*app)(?:\s+(?:id|number|no\.?|handle))?\b""", I),
        Regex("""\bscan\b.{0,20}\bqr\b""", I),
    )

    // ── BODY_REQUEST ──
    private val BODY = listOf(
        Regex("""\b(?:bikini|swimsuit|swimwear|lingerie|topless|bold\s*(?:photos?|pics?|pictures?|shoot|look)|measurements?|bra\s*size|vital\s*stats|body\s*(?:pics?|photos?|pictures?|shots?)|figure\s*(?:pics?|photos?)|nude\s*(?:pics?|photos?|shoot))\b""", I),
        Regex("""\b(?:hot|sexy|revealing)\s*(?:photos?|pics?|pictures?|shoot)\b""", I),
        Regex("""\b(?:photos?|pics?|pictures?|selfies?)\s*(?:bhejo|bhej\s*do|send\s*karo)\b""", I),
    )

    // ── PRIVATE_MEETING ──
    private val PRIVATE = Regex(
        """\b(?:my\s*(?:place|flat|apartment|room|hotel)|hotel\s*room|come\s*alone|meet\s*alone|alone\s+with\s+me|just\s+the\s+two\s+of\s+us|ghar\s*(?:aa\s*jao|aao|pe\s*aao|par\s*aao)|mere\s*(?:ghar|flat|room)|akele\s*(?:aana|aao|milo))\b""", I)

    fun analyze(text: String): Set<MessageFlag> {
        if (text.isBlank()) return emptySet()
        val out = mutableSetOf<MessageFlag>()
        if (hasContact(text)) out += MessageFlag.CONTACT
        if (isOffPlatform(text)) out += MessageFlag.OFF_PLATFORM
        if (isMoney(text)) out += MessageFlag.MONEY_FROM_TALENT
        if (BODY.any { it.containsMatchIn(text) }) out += MessageFlag.BODY_REQUEST
        if (PRIVATE.containsMatchIn(text)) out += MessageFlag.PRIVATE_MEETING
        return out
    }

    fun hasContact(text: String): Boolean =
        EMAIL.containsMatchIn(text) || PHONE.containsMatchIn(text) || handles(text).isNotEmpty()

    private fun handles(text: String): List<MatchResult> {
        // Handles inside emails are covered by EMAIL; strip emails first so "a@b.com" doesn't double count.
        val noEmails = EMAIL.replace(text) { " ".repeat(it.value.length) }
        return HANDLE.findAll(noEmails).filterNot { TIME_LIKE_HANDLE.matches(it.value) }.toList()
    }

    private fun isOffPlatform(text: String): Boolean {
        if (OFF_PLATFORM.any { it.containsMatchIn(text) }) return true
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size <= 3 && BARE_PLATFORM.containsMatchIn(text)
    }

    /** True when the text asks cast or crew to pay (fees, deposits, "send ₹500 on GPay"). */
    fun isMoney(text: String): Boolean {
        for (m in FEE_NOUN.findAll(text)) {
            val before = text.substring(maxOf(0, m.range.first - 24), m.range.first)
            if (!FEE_NEGATION.containsMatchIn(before)) return true
        }
        if (MONEY_OTHER.any { it.containsMatchIn(text) }) return true
        for (m in PAY_VERB.findAll(text)) {
            val verbStart = m.groups[1]!!.range.first
            val before = text.substring(maxOf(0, verbStart - 28), verbStart)
            val after = m.groups[2]?.value ?: ""
            if (PAYER_IS_YOU.containsMatchIn(before)) return true
            if (PAY_TO_YOU.containsMatchIn(after) || PAY_DESCRIBED.containsMatchIn(after)) continue
            if (PAYER_IS_US.containsMatchIn(before)) continue
            return true
        }
        return false
    }

    /** Replace phone numbers / emails / @handles with "•••• shared contact hidden". */
    fun mask(text: String): String {
        var out = EMAIL.replace(text, MASK)
        out = HANDLE.replace(out) { if (TIME_LIKE_HANDLE.matches(it.value)) it.value else MASK }
        out = PHONE.replace(out, MASK)
        return out
    }

    /**
     * Banners for the RECIPIENT. MONEY_FROM_TALENT is red only when the sender is on the owner side
     * (or unknown); a collaborator asking "paid via UPI?" gets no banner.
     */
    fun bannersFor(flags: Set<MessageFlag>, senderIsOwnerSide: Boolean): List<SafetyBannerSpec> {
        val out = mutableListOf<SafetyBannerSpec>()
        if (MessageFlag.MONEY_FROM_TALENT in flags && senderIsOwnerSide) {
            out += SafetyBannerSpec(MessageFlag.MONEY_FROM_TALENT, BannerLevel.DANGER, TEXT_MONEY, offerReport = true, offerBlock = true)
        }
        if (MessageFlag.BODY_REQUEST in flags) {
            out += SafetyBannerSpec(MessageFlag.BODY_REQUEST, BannerLevel.DANGER,
                if (senderIsOwnerSide) TEXT_BODY else TEXT_BODY_TO_OWNER, offerReport = true, offerBlock = true)
        }
        if (MessageFlag.PRIVATE_MEETING in flags) {
            out += SafetyBannerSpec(MessageFlag.PRIVATE_MEETING, BannerLevel.WARN, TEXT_PRIVATE, offerReport = true, offerBlock = false)
        }
        if (MessageFlag.OFF_PLATFORM in flags) {
            out += SafetyBannerSpec(MessageFlag.OFF_PLATFORM, BannerLevel.INFO, TEXT_OFF_PLATFORM, offerReport = false, offerBlock = false)
        }
        return out
    }

    /** Sender-side inline warning while typing (null = none). Assumes the sender may be the owner side. */
    fun draftWarning(text: String, booked: Boolean): String? = draftWarning(text, booked, senderIsOwnerSide = true)

    /** As [draftWarning]; a collaborator talking about their own pay ("send it on GPay after wrap") isn't warned. */
    fun draftWarning(text: String, booked: Boolean, senderIsOwnerSide: Boolean): String? {
        if (text.isBlank()) return null
        if (senderIsOwnerSide && isMoney(text)) return DRAFT_MONEY
        if (senderIsOwnerSide && BODY.any { it.containsMatchIn(text) }) return DRAFT_BODY
        if (!booked && hasContact(text)) return DRAFT_CONTACT
        return null
    }
}
