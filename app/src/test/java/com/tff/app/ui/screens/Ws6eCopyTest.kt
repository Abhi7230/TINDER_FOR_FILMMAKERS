package com.tff.app.ui.screens

import com.tff.app.domain.BookingTermsFactory
import com.tff.app.model.BookingStatus
import com.tff.app.model.BookingTerms
import com.tff.app.model.CompType
import com.tff.app.model.ContentFlag
import com.tff.app.model.OwedBy
import com.tff.app.model.Perk
import com.tff.app.model.Project
import com.tff.app.model.ProjectRole
import com.tff.app.model.RoleCode
import com.tff.app.model.ShootDay
import com.tff.app.model.WrapItem
import com.tff.app.model.WrapKind
import com.tff.app.ui.screens.booking.BookingCopy
import com.tff.app.ui.screens.booking.TermsText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** FIXE: state-aware booking summary, listing role line, call-window suggestion, pay owed, overdue days, late wrap. */
class Ws6eCopyTest {
    private val sat = LocalDate.of(2026, 10, 10)

    private fun terms(
        code: RoleCode = RoleCode.ACTOR,
        comp: CompType = CompType.UNPAID,
        amount: Int? = null,
        kit: Int? = null,
        dates: List<LocalDate> = listOf(sat, sat.plusDays(7)),
        perks: Set<Perk> = setOf(Perk.FOOTAGE_BY_DATE),
    ) = BookingTermsFactory.normalize(
        BookingTerms(
            roleCode = code, creditTitle = "Cast", projectTitle = "Last Local", dates = dates, compType = comp, amount = amount,
            currency = "INR", kitFeeDay = kit, paymentTerms = if (comp == CompType.UNPAID) null else com.tff.app.model.PaymentTerms.ON_WRAP,
            perks = perks, footageDueWeeks = 6, payerId = "u_001", payerName = "Aniket D.", noticePledge = BookingTermsFactory.noticePledge(false),
        ),
    )

    private val lead = ProjectRole(
        id = "r_ll_lead", projectId = "p_last_local", roleCode = RoleCode.ACTOR, creditTitle = "Cast", compType = CompType.UNPAID,
        currency = "INR", roleDetails = mapOf("size" to "LEAD", "character" to "Asha, the pickpocket"),
    )

    private fun text(t: BookingTerms, status: BookingStatus?, rebooking: Boolean = false, role: ProjectRole? = lead) =
        BookingCopy.summarySpans(t, status, rebooking, role).joinToString("") { it.text }

    @Test fun offerNeverReadsBookedUntilAccepted() {
        val t = terms()
        assertTrue(text(t, BookingStatus.SENT).startsWith("If you accept, you're booked as Lead Actor playing 'Asha, the pickpocket' (credit: 'Cast') on Last Local"))
        assertTrue(text(t, null).startsWith("If you accept, you're booked as "))
        assertTrue(text(t, BookingStatus.SENT, rebooking = true).startsWith("If you accept this version, you're booked as "))
        assertTrue(text(t, BookingStatus.ACCEPTED).startsWith("You're booked as Lead Actor"))
        assertTrue(text(t, BookingStatus.DECLINED).startsWith("This version: booked as "))
        assertFalse(text(t, BookingStatus.SENT).contains("You're booked"))
        // The rest of the sentence is CardText's, untouched.
        assertTrue(text(t, BookingStatus.SENT).endsWith("Payer: Aniket D."))
    }

    @Test fun roleLineUsesListingSizeAndCharacter() {
        val t = terms()
        assertEquals("Lead Actor · playing 'Asha, the pickpocket'", BookingCopy.roleLine(t, lead))
        assertEquals("Actor", BookingCopy.roleLine(t, null))
        // A crew role keeps its plain name and gets no character.
        val sound = lead.copy(roleCode = RoleCode.SOUND_MIXER, roleDetails = mapOf("character" to "x"))
        assertEquals("Sound Mixer", BookingCopy.roleLine(terms(code = RoleCode.SOUND_MIXER), sound))
        assertTrue(text(terms(code = RoleCode.SOUND_MIXER), BookingStatus.SENT, role = sound).startsWith("If you accept, you're booked as Sound Mixer (credit:"))
    }

    @Test fun perksDropFootageOnceItIsADeliverable() {
        val t = terms(perks = setOf(Perk.FOOTAGE_BY_DATE, Perk.STEP_UP_CREDIT))
        assertTrue(t.deliverables.any { it.kind == WrapKind.FOOTAGE })
        assertEquals(com.tff.app.domain.CardText.perkLabel(Perk.STEP_UP_CREDIT), TermsText.perksShown(t))
        assertNull(TermsText.perksShown(terms()))
    }

    private val project = Project(id = "p", ownerId = "o", payerId = "o", title = "Last Local", logline = "", cityId = "mumbai", areaId = "a")

    @Test fun callWindowSuggestion() {
        val day = ShootDay("sd", "p", sat, LocalTime.of(16, 0), LocalTime.of(23, 0), "Somewhere", "")
        assertEquals("~16:00–23:00", BookingCopy.suggestCallWindow(project, listOf(day)))
        assertEquals("~20:00–06:00", BookingCopy.suggestCallWindow(project.copy(contentFlags = setOf(ContentFlag.NIGHT_SHOOT)), emptyList()))
        assertEquals("~07:00–15:00", BookingCopy.suggestCallWindow(project.copy(hoursCap = 8), emptyList()))
        assertNull(BookingCopy.suggestCallWindow(project, emptyList()))
        // Cancelled sheets don't count.
        assertNull(BookingCopy.suggestCallWindow(project, listOf(day.copy(cancelledAt = 1L))))
    }

    @Test fun payOwedFromAcceptedTerms() {
        assertEquals("₹7,000 · 2 days × ₹2,500 + kit ₹1,000 × 2", BookingCopy.payOwed(terms(comp = CompType.PAID_DAY, amount = 2500, kit = 1000)))
        assertEquals("₹1,500 · 1 day", BookingCopy.payOwed(terms(comp = CompType.STIPEND, amount = 1500, dates = listOf(sat))))
        assertEquals("₹3,000 · 2 days × ₹1,500", BookingCopy.payOwed(terms(comp = CompType.STIPEND, amount = 1500)))
        assertEquals("₹15,000 · flat", BookingCopy.payOwed(terms(comp = CompType.PAID_FLAT, amount = 15000)))
        assertNull(BookingCopy.payOwed(terms()))
    }

    @Test fun overdueDaysAndLateWrap() {
        val item = WrapItem("wi", "m", WrapKind.FOOTAGE, LocalDate.of(2026, 8, 30), OwedBy.PRODUCTION)
        assertEquals(33, BookingCopy.daysOverdue(item, LocalDate.of(2026, 10, 2)))
        assertEquals(0, BookingCopy.daysOverdue(item, LocalDate.of(2026, 8, 30)))
        assertEquals(0, BookingCopy.daysOverdue(item.copy(dueDate = null), LocalDate.of(2026, 10, 2)))
        val day = ShootDay("sd", "p", sat, LocalTime.of(16, 0), LocalTime.of(23, 0), "Somewhere", "")
        assertTrue(BookingCopy.isLateWrap(day))
        assertTrue(BookingCopy.isLateWrap(day.copy(generalCall = LocalTime.of(20, 0), wrapBy = LocalTime.of(2, 0))))
        assertFalse(BookingCopy.isLateWrap(day.copy(generalCall = LocalTime.of(7, 0), wrapBy = LocalTime.of(19, 0))))
    }
}
