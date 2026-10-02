package com.tff.app.ui.screens.post

import com.tff.app.domain.RuleResult
import com.tff.app.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** FIXB: [Edit role] opens the wizard on that role, and the "N TO ANSWER" counter counts one per field. */
class WizardFixbTest {
    @Test fun `the edit-role hint is taken once and only for its project`() {
        WizardLaunch.editRole("p_filter_coffee", "r_fc_dp")
        assertNull(WizardLaunch.take("p_other"))
        assertNull(WizardLaunch.take("p_filter_coffee"))      // cleared by the first take
        WizardLaunch.editRole("p_filter_coffee", "r_fc_dp")
        assertEquals("r_fc_dp", WizardLaunch.take("p_filter_coffee"))
        WizardLaunch.addRole("p_filter_coffee")
        assertEquals(PostProjectViewModel.NEW_ROLE, WizardLaunch.take("p_filter_coffee"))
        WizardLaunch.addRole("p_filter_coffee")
        assertNull(WizardLaunch.take(null))                   // a new project never takes it
    }

    @Test fun `to-answer counts each unanswered field once`() {
        val rules = listOf(
            RuleResult("R05", Severity.BLOCK, "Say how firm the dates are.", field = Fields.CERTAINTY),
            RuleResult("R02", Severity.BLOCK, "Say what food is on set.", field = Fields.MEALS),
            RuleResult("R02", Severity.BLOCK, "Unpaid roles need 2+ meals.", field = Fields.MEALS),
            RuleResult("R03", Severity.BLOCK, "Say how people get to set.", field = Fields.TRANSPORT),
            RuleResult("R06", Severity.WARN, "Long day.", field = Fields.HOURS),
            RuleResult("R01", Severity.BLOCK, "Add a title.", field = Fields.TITLE),
        )
        assertEquals(3, WizardLogic.toAnswer(WizardStep.WHEN_WHERE, rules))
        assertEquals(1, WizardLogic.toAnswer(WizardStep.BASICS, rules))
        assertEquals(0, WizardLogic.toAnswer(WizardStep.SAFETY, rules))
    }
}
