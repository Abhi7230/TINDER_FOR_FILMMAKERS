package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WrapItemsFactoryTest {

    private val today = Fx.TODAY

    private fun onSetTerms(): BookingTerms {
        val role = Fx.role(id = "r_lead", roleCode = RoleCode.ACTOR, compType = CompType.STIPEND, amountMin = 800,
            paymentTerms = PaymentTerms.WITHIN_7_DAYS, perks = setOf(Perk.FOOTAGE_BY_DATE, Perk.FINAL_FILM_BY_DATE),
            footageDueWeeks = 6, finalFilmDueWeeks = 12)
        return BookingTermsFactory.fromRole(Fx.item(role = role), "Rohan K.")
    }

    private fun postTerms(terms: PaymentTerms = PaymentTerms.WITHIN_7_DAYS): BookingTerms {
        val role = Fx.role(id = "r_sd", projectId = "p_soft", roleCode = RoleCode.SOUND_DESIGNER, compType = CompType.PAID_FLAT, amountMin = 2400,
            currency = "USD", paymentTerms = terms, remoteOk = true, postDeadline = LocalDate.of(2027, 2, 7),
            perks = setOf(Perk.FINAL_FILM_BY_DATE), finalFilmDueWeeks = 8)
        return BookingTermsFactory.fromRole(Fx.item(role = role, project = Fx.postProject()), "Rohan K.")
    }

    private fun items(t: BookingTerms) = WrapItemsFactory.fromTerms("m_1", t) { "w_${it.name.lowercase()}" }

    @Test fun onSetBookingGetsPaidFootageFinalFilmCredit() {
        val list = items(onSetTerms())
        assertEquals(listOf(WrapKind.PAID, WrapKind.FOOTAGE, WrapKind.FINAL_FILM, WrapKind.CREDIT), list.map { it.kind })
        assertTrue(list.all { it.owedBy == OwedBy.PRODUCTION && it.status == WrapStatus.OPEN && it.matchId == "m_1" })
        val byKind = list.associateBy { it.kind }
        assertEquals(LocalDate.of(2026, 11, 22), byKind[WrapKind.PAID]!!.dueDate)          // last day 15 Nov + 7
        assertEquals(LocalDate.of(2026, 12, 27), byKind[WrapKind.FOOTAGE]!!.dueDate)       // + 6 weeks
        assertEquals(LocalDate.of(2027, 2, 7), byKind[WrapKind.FINAL_FILM]!!.dueDate)      // + 12 weeks
        assertEquals(byKind[WrapKind.FINAL_FILM]!!.dueDate, byKind[WrapKind.CREDIT]!!.dueDate)
        assertEquals("w_footage", byKind[WrapKind.FOOTAGE]!!.id)
        assertTrue(list.none { it.kind == WrapKind.DELIVERY })
    }

    @Test fun postRoleGetsDeliveryOwedByCollaboratorAndNoFootage() {
        val list = items(postTerms())
        assertEquals(listOf(WrapKind.DELIVERY, WrapKind.PAID, WrapKind.FINAL_FILM, WrapKind.CREDIT), list.map { it.kind })
        val delivery = list.first { it.kind == WrapKind.DELIVERY }
        assertEquals(OwedBy.COLLABORATOR, delivery.owedBy)
        assertEquals(LocalDate.of(2027, 2, 7), delivery.dueDate)
        assertTrue(list.filter { it.kind != WrapKind.DELIVERY }.all { it.owedBy == OwedBy.PRODUCTION })
        assertTrue(list.none { it.kind == WrapKind.FOOTAGE })
    }

    @Test fun postRolePayByDateCountsFromDelivery() {
        val t = postTerms()
        assertEquals(LocalDate.of(2027, 2, 14), t.payByDate)                               // delivery + 7 days
        assertEquals(LocalDate.of(2027, 2, 14), items(t).first { it.kind == WrapKind.PAID }.dueDate)
        assertEquals(LocalDate.of(2027, 2, 7), postTerms(PaymentTerms.ON_WRAP).payByDate)
        assertEquals(LocalDate.of(2027, 3, 9), postTerms(PaymentTerms.WITHIN_30_DAYS).payByDate)
        assertTrue(CardText.bookingSummary(t).contains("deliver by 7 Feb"))
        assertTrue(CardText.bookingSummary(t).contains("paid within 7 days of delivery"))
        assertTrue(t.dates.isEmpty())
    }

    @Test fun unpaidBookingWithoutPromisesOnlyOwesCredit() {
        val role = Fx.role(roleCode = RoleCode.PA, compType = CompType.UNPAID, perks = setOf(Perk.STEP_UP_CREDIT))
        val t = BookingTermsFactory.fromRole(Fx.item(role = role), "Rohan K.")
        assertNull(t.payByDate)
        assertEquals(listOf(WrapKind.CREDIT), items(t).map { it.kind })
    }

    @Test fun overdueLogic() {
        val due = LocalDate.of(2026, 12, 27)
        val item = WrapItem("w", "m_1", WrapKind.FOOTAGE, due, OwedBy.PRODUCTION)
        assertFalse(WrapItemsFactory.isOverdue(item, due))
        assertTrue(WrapItemsFactory.isOverdue(item, due.plusDays(1)))
        assertTrue(WrapItemsFactory.isOverdue(item.copy(status = WrapStatus.NOT_RECEIVED), due.plusDays(1)))
        assertFalse(WrapItemsFactory.isOverdue(item.copy(status = WrapStatus.DONE), due.plusDays(1)))
        assertFalse(WrapItemsFactory.isOverdue(item.copy(status = WrapStatus.CONFIRMED), due.plusDays(30)))
        assertFalse(WrapItemsFactory.isOverdue(item.copy(dueDate = null), today.plusYears(1)))
    }

    @Test fun deliveryAutoConfirmsAfterSevenDays() {
        val marked = Fx.NOW
        val d = WrapItem("w", "m_1", WrapKind.DELIVERY, LocalDate.of(2027, 2, 7), OwedBy.COLLABORATOR, status = WrapStatus.DONE, markedAt = marked)
        assertFalse(WrapItemsFactory.shouldAutoConfirm(d, marked + 6 * Fx.DAY_MS))
        assertTrue(WrapItemsFactory.shouldAutoConfirm(d, marked + 7 * Fx.DAY_MS))
        assertFalse(WrapItemsFactory.shouldAutoConfirm(d.copy(kind = WrapKind.PAID), marked + 30 * Fx.DAY_MS))
    }
}
