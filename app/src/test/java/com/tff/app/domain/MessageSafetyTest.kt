package com.tff.app.domain

import com.tff.app.model.MessageFlag
import com.tff.app.model.MessageFlag.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSafetyTest {

    private fun flags(s: String) = MessageSafety.analyze(s)
    private fun has(s: String, f: MessageFlag) = assertTrue("expected $f in: $s → ${flags(s)}", f in flags(s))
    private fun hasNot(s: String, f: MessageFlag) = assertFalse("unexpected $f in: $s → ${flags(s)}", f in flags(s))

    @Test fun contactPositives() {
        has("Call me on 98201 23456", CONTACT)
        has("my no is +91 98201-23456", CONTACT)
        has("9820123456", CONTACT)
        has("mail rohan.k@gmail.com for the script", CONTACT)
        has("find me @meera.acts", CONTACT)
        has("text +1 404 555 0134 after 6", CONTACT)
    }

    @Test fun contactNegatives() {
        hasNot("₹2,500/day + ₹1,000 kit", CONTACT)
        hasNot("Call time 07:30, wrap by 17:00", CONTACT)
        hasNot("See you @5pm at the studio", CONTACT)
        hasNot("Scene 12, take 3, shot on 14-11", CONTACT)
        hasNot("₹1,20,000 total budget", CONTACT)
    }

    @Test fun offPlatformEnglishAndHinglish() {
        has("whatsapp karo", OFF_PLATFORM)
        has("WhatsApp me the sides", OFF_PLATFORM)
        has("Let's move to WhatsApp", OFF_PLATFORM)
        has("ping me on telegram", OFF_PLATFORM)
        has("email me your reel", OFF_PLATFORM)
        has("apna number do", OFF_PLATFORM)
        has("number bhejo", OFF_PLATFORM)
        has("call karo kal", OFF_PLATFORM)
        has("insta id do", OFF_PLATFORM)
        has("WhatsApp?", OFF_PLATFORM)
        has("wa.me/919820123456", OFF_PLATFORM)
        has("DM me on insta", OFF_PLATFORM)
    }

    @Test fun offPlatformNegatives() {
        hasNot("the WhatsApp scene in act 2 needs a phone insert", OFF_PLATFORM)
        hasNot("We need to mail the release forms to the producer", OFF_PLATFORM)
        hasNot("Her character keeps texting her ex on the bus", OFF_PLATFORM)
        hasNot("Sides are attached here in the audition card", OFF_PLATFORM)
    }

    @Test fun moneyEnglishAndHinglish() {
        has("There is a registration fee of ₹2000", MONEY_FROM_TALENT)
        has("Refundable security deposit needed", MONEY_FROM_TALENT)
        has("portfolio shoot fee is 3500", MONEY_FROM_TALENT)
        has("Please send ₹1500 to confirm your slot", MONEY_FROM_TALENT)
        has("You need to pay 500 rs on GPay first", MONEY_FROM_TALENT)
        has("transfer via Zelle, $50", MONEY_FROM_TALENT)
        has("pay to audition, it's standard", MONEY_FROM_TALENT)
        has("form fees bharni padegi", MONEY_FROM_TALENT)
        has("paise bhejo pehle", MONEY_FROM_TALENT)
        has("audition charges 999 only", MONEY_FROM_TALENT)
        has("my UPI id is rohan@okicici", MONEY_FROM_TALENT)
    }

    @Test fun moneyNegatives() {
        hasNot("the WhatsApp scene in act 2", MONEY_FROM_TALENT)
        hasNot("We pay ₹2,500/day on wrap", MONEY_FROM_TALENT)
        hasNot("I'll send ₹500 for your travel tonight", MONEY_FROM_TALENT)
        hasNot("We'll pay you via UPI on wrap", MONEY_FROM_TALENT)
        hasNot("Pay: ₹2,500/day + ₹1,000 kit", MONEY_FROM_TALENT)
        hasNot("No registration fee, ever.", MONEY_FROM_TALENT)
        hasNot("paid via UPI?", MONEY_FROM_TALENT)
        hasNot("Send your reel and the sides by Friday", MONEY_FROM_TALENT)
    }

    @Test fun bodyRequest() {
        has("send bikini pics for the role", BODY_REQUEST)
        has("Need your measurements and bra size", BODY_REQUEST)
        has("photos bhejo full length", BODY_REQUEST)
        has("bold photoshoot first, bold pics needed", BODY_REQUEST)
        hasNot("Send a self-tape of the scene", BODY_REQUEST)
        hasNot("The film is bold in its storytelling", BODY_REQUEST)
    }

    @Test fun privateMeeting() {
        has("come to my place tonight for the audition", PRIVATE_MEETING)
        has("we can do it in my hotel room", PRIVATE_MEETING)
        has("come alone please", PRIVATE_MEETING)
        has("ghar aa jao kal", PRIVATE_MEETING)
        hasNot("We meet at Prithvi Cafe with the casting assistant", PRIVATE_MEETING)
    }

    @Test fun scamExampleFromTheSeed() {
        val msg = "Hi Meera, selected! Registration fee ₹1,500 to lock your role. WhatsApp me on 98201 23456"
        assertEquals(setOf(CONTACT, OFF_PLATFORM, MONEY_FROM_TALENT), flags(msg))
        val masked = MessageSafety.mask(msg)
        assertEquals("Hi Meera, selected! Registration fee ₹1,500 to lock your role. WhatsApp me on •••• shared contact hidden", masked)
    }

    @Test fun masking() {
        assertEquals("write to •••• shared contact hidden or •••• shared contact hidden",
            MessageSafety.mask("write to meera@example.com or @meera.acts"))
        assertEquals("call •••• shared contact hidden", MessageSafety.mask("call +91 98201 23456"))
        assertEquals("See you @5pm", MessageSafety.mask("See you @5pm"))
        assertEquals("₹2,500/day", MessageSafety.mask("₹2,500/day"))
        assertFalse(MessageSafety.hasContact(MessageSafety.mask("ring 9820123456 or a@b.co")))
    }

    @Test fun bannersRespectDirection() {
        val money = setOf(MONEY_FROM_TALENT)
        val owner = MessageSafety.bannersFor(money, senderIsOwnerSide = true)
        assertEquals(1, owner.size)
        assertEquals(BannerLevel.DANGER, owner[0].level)
        assertEquals("Calltime never allows charging cast or crew. This is a common scam.", owner[0].text)
        assertTrue(owner[0].offerReport && owner[0].offerBlock)
        // A collaborator asking "paid via UPI?" gets no banner.
        assertTrue(MessageSafety.bannersFor(money, senderIsOwnerSide = false).isEmpty())

        val all = MessageSafety.bannersFor(setOf(CONTACT, OFF_PLATFORM, BODY_REQUEST, PRIVATE_MEETING, MONEY_FROM_TALENT), true)
        assertEquals(listOf(MONEY_FROM_TALENT, BODY_REQUEST, PRIVATE_MEETING, OFF_PLATFORM), all.map { it.flag })
        assertEquals("Keeping talks on Calltime until you're booked protects you. Here's why.", all.last().text)
        assertEquals(BannerLevel.INFO, all.last().level)
        assertEquals(BannerLevel.WARN, all[2].level)
        assertEquals("You never need to send body photos or measurements to apply. Report?", all[1].text)
        // Off-platform goes to both sides.
        assertEquals(1, MessageSafety.bannersFor(setOf(OFF_PLATFORM), false).size)
    }

    @Test fun draftWarnings() {
        assertEquals("Contact details stay hidden until you're booked.", MessageSafety.draftWarning("my number is 9820123456", booked = false))
        assertNull(MessageSafety.draftWarning("my number is 9820123456", booked = true))
        assertEquals(MessageSafety.DRAFT_MONEY, MessageSafety.draftWarning("registration fee ₹500", booked = false))
        assertNull(MessageSafety.draftWarning("Send it on GPay after wrap, ₹2,500", booked = true, senderIsOwnerSide = false))
        assertNull(MessageSafety.draftWarning("What are the call times?", booked = false))
        assertNull(MessageSafety.draftWarning("", booked = false))
    }
}
