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
    fun analyze(text: String): Set<MessageFlag> = TODO()
    /** Replace phone numbers / emails / @handles with "•••• shared contact hidden". */
    fun mask(text: String): String = TODO()
    /**
     * Banners for the RECIPIENT. MONEY_FROM_TALENT is red only when the sender is on the owner side
     * (or unknown); a collaborator asking "paid via UPI?" gets no banner.
     */
    fun bannersFor(flags: Set<MessageFlag>, senderIsOwnerSide: Boolean): List<SafetyBannerSpec> = TODO()
    /** Sender-side inline warning while typing (null = none). */
    fun draftWarning(text: String, booked: Boolean): String? = TODO()
}
