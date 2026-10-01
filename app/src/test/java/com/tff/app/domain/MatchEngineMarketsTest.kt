package com.tff.app.domain

import com.tff.app.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** G4 never compares a min rate across currencies; G2 keeps remote work inside the viewer's country. */
class MatchEngineMarketsTest {
    private val today = Fx.TODAY
    private val now = Fx.NOW

    // Atlanta sound mixer, PAID_ONLY, private min $350 (Dana).
    private val dana = Fx.profile(id = "u_dana", primaryRole = RoleCode.SOUND_MIXER, compMode = CompMode.PAID_ONLY, remoteOk = true,
        secondaryRoles = listOf(SecondaryRole(RoleCode.SOUND_DESIGNER, 2)), languages = listOf("en"),
        cityId = "atlanta", areaId = "atlanta_midtown", radius = 40)
    private val danaV = Fx.viewer(dana, minDayRate = 350)

    // Mumbai editor, remote OK.
    private val editor = Fx.profile(id = "u_ed", primaryRole = RoleCode.EDITOR, remoteOk = true, languages = listOf("en", "hi"),
        cityId = "mumbai", areaId = "mumbai_andheri_west", compMode = CompMode.PAID_AND_SELECTIVE)

    private fun usSound(amount: Int) = Fx.item(
        role = Fx.role(id = "r_us", projectId = "p_us", amountMin = amount, currency = "USD"),
        project = Fx.project(id = "p_us", cityId = "atlanta", areaId = "atlanta_midtown", languages = listOf("en")),
        lat = Fx.VERSOVA_LAT, lng = Fx.VERSOVA_LNG,
    )

    private fun inrSound(amount: Int) = Fx.item(
        role = Fx.role(id = "r_in", projectId = "p_in", amountMin = amount, currency = "INR"),
        project = Fx.project(id = "p_in", cityId = "mumbai", areaId = "mumbai_andheri_west", languages = listOf("hi")),
        lat = Fx.VERSOVA_LAT, lng = Fx.VERSOVA_LNG,
    )

    private fun remotePost(city: String, currency: String, code: RoleCode = RoleCode.EDITOR) = Fx.item(
        role = Fx.role(id = "r_post_$city", projectId = "p_post_$city", roleCode = code, compType = CompType.PAID_FLAT, amountMin = 2000,
            currency = currency, paymentTerms = PaymentTerms.WITHIN_7_DAYS, remoteOk = true, postDeadline = today.plusDays(40)),
        project = Fx.postProject(id = "p_post_$city", pictureLockDate = today.plusDays(10)).copy(cityId = city, areaId = "${city}_x"),
        lat = 0.0, lng = 0.0,
    )

    @Test fun viewerCurrencyAndCountryDefaultFromTheCity() {
        assertEquals("USD", danaV.currency)
        assertEquals("US", danaV.country)
        assertEquals("INR", Fx.viewer(editor).currency)
        assertEquals("IN", Fx.viewer(editor).country)
        // Unknown city: both unknown, so neither rule applies.
        val unknown = Fx.viewer(Fx.profile(cityId = "mum"))
        assertNull(unknown.currency)
        assertNull(unknown.country)
        assertEquals("GBP", Markets.currencyOfCity("london"))
        assertEquals("US", Markets.countryOfCurrency("usd"))
    }

    @Test fun paidOnlyMinRateIsComparedInItsOwnCurrencyOnly() {
        // Same currency: the rate is compared.
        assertFalse(Gate.G4_MONEY in MatchEngine.failedGates(danaV, usSound(400), today))
        assertTrue(Gate.G4_MONEY in MatchEngine.failedGates(danaV, usSound(300), today))
        // ₹5,000/day is a bigger number than 350 but it is not dollars: with a $ minimum set, the gate fails.
        assertTrue(Gate.G4_MONEY in MatchEngine.failedGates(danaV, inrSound(5000), today))
        assertFalse(MatchEngine.moneyOk(CompMode.PAID_ONLY, emptyList(), 350, inrSound(5000).role, inrSound(5000).project, "USD"))
        assertTrue(MatchEngine.moneyOk(CompMode.PAID_ONLY, emptyList(), 350, usSound(350).role, usSound(350).project, "usd"))
        // No min rate set: any paid role passes, whatever the currency.
        assertTrue(MatchEngine.moneyOk(CompMode.PAID_ONLY, emptyList(), null, inrSound(5000).role, inrSound(5000).project, "USD"))
        // A flat-fee post role has no day rate, but the currency still has to match the minimum's.
        val inrPost = remotePost("mumbai", "INR", RoleCode.SOUND_DESIGNER)
        val usdPost = remotePost("los_angeles", "USD", RoleCode.SOUND_DESIGNER)
        assertFalse(MatchEngine.moneyOk(CompMode.PAID_ONLY, emptyList(), 350, inrPost.role, inrPost.project, "USD"))
        assertTrue(MatchEngine.moneyOk(CompMode.PAID_ONLY, emptyList(), 350, usdPost.role, usdPost.project, "USD"))
        // Unknown rate currency (legacy callers / reverse gates): the old comparison, no currency check.
        assertTrue(MatchEngine.moneyOk(CompMode.PAID_ONLY, emptyList(), 350, inrSound(5000).role, inrSound(5000).project))
        assertTrue(MatchEngine.moneyOk(CompMode.PAID_ONLY, emptyList(), 350, inrSound(5000).role, inrSound(5000).project, null))
    }

    @Test fun selectiveModesNeverLookAtTheRateAndCompScoreSkipsCrossCurrencyRatios() {
        val selective = Fx.viewer(dana.copy(compMode = CompMode.PAID_AND_SELECTIVE), minDayRate = 350)
        assertFalse(Gate.G4_MONEY in MatchEngine.failedGates(selective, inrSound(5000), today))
        // ₹5,000 vs $350 must not read as "14× your rate": the no-min-rate base (16) + ON_WRAP bonus (2) applies.
        assertEquals(18.0, MatchEngine.fit(selective, inrSound(5000), today).comp, 1e-9)
        // Same currency: the ratio is used (300/350 × 20 + 2).
        assertEquals(20.0 * 300 / 350 + 2.0, MatchEngine.fit(selective, usSound(300), today).comp, 1e-9)
    }

    @Test fun remoteRolesStayInTheViewersCountry() {
        val inPost = remotePost("bengaluru", "INR")
        val usPost = remotePost("los_angeles", "USD")
        val edV = Fx.viewer(editor)
        assertTrue(MatchEngine.failedGates(edV, inPost, today).isEmpty())
        assertTrue(Gate.G2_PLACE in MatchEngine.failedGates(edV, usPost, today))
        // The small-market fallback ("include all remote roles") keeps the country rule too.
        assertTrue(Gate.G2_PLACE in MatchEngine.failedGates(edV, usPost, today, MatchEngine.FALLBACK_RADIUS, includeAllRemote = true))
        val deck = MatchEngine.buildDeck(edV, listOf(inPost, usPost), today, now)
        assertEquals(listOf(inPost.role.id), deck.map { it.item.role.id })
        // A US editor gets the US role and not the Indian one.
        val usEd = Fx.viewer(editor.copy(cityId = "los_angeles", areaId = "los_angeles_hollywood", languages = listOf("en")))
        assertTrue(MatchEngine.failedGates(usEd, usPost, today).isEmpty())
        assertTrue(Gate.G2_PLACE in MatchEngine.failedGates(usEd, inPost, today))
        // Unknown project city falls back to the role's currency to tell the country.
        val byCurrency = usPost.copy(project = usPost.project.copy(cityId = "somewhere"))
        assertEquals("US", Markets.countryOf(byCurrency))
        assertTrue(Gate.G2_PLACE in MatchEngine.failedGates(edV, byCurrency, today))
        // Dana (US, PAID_ONLY $350) passes a US flat-fee sound design role and never an Indian one.
        val usSd = remotePost("los_angeles", "USD", RoleCode.SOUND_DESIGNER)
        val inSd = remotePost("mumbai", "INR", RoleCode.SOUND_DESIGNER)
        assertTrue(MatchEngine.failedGates(danaV, usSd, today).isEmpty())
        assertTrue(MatchEngine.failedGates(danaV, inSd, today).containsAll(setOf(Gate.G2_PLACE, Gate.G4_MONEY)))
    }
}
