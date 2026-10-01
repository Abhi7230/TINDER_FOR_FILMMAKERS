package com.tff.app.data.demo

import com.tff.app.model.DemoPersona

/** Stable ids the QA script, tests and deep links rely on. Documented in data/demo/DEMO_IDS.md. */
object DemoIds {
    // People
    const val MEERA = "u_meera"
    const val ROHAN = "u_rohan"
    const val KABIR = "u_kabir"
    const val DANA = "u_dana"
    const val CASTING_HUB = "u_casting_hub"
    const val LAST_LOCAL_OWNER = "u_001"
    const val FARIDA = "u_002"            // strong owner; Meera's Rebook ask; owner of Cutting Chai
    const val WEAK_KEPT_OWNER = "u_028"   // Kept bookings 2/4, on Kabir's Keep list
    const val WEAK_FOOTAGE_OWNER = "u_022" // Footage 1/4 (Meera's overdue footage)
    const val WEAK_PAY_OWNER = "u_071"    // Paid on time 2/4
    const val ELENA = "u_063"             // Soft Light owner
    const val INDORE_OWNER = "u_057"

    // Open projects
    const val P_LAST_LOCAL = "p_last_local"
    const val P_ROOMMATES = "p_roommates"
    const val P_TIFFIN_WARS = "p_tiffin_wars"
    const val P_TWO_ROOMS = "p_two_rooms"
    const val P_PAPER_BOATS = "p_paper_boats"
    const val P_DEAD_DROP = "p_dead_drop"
    const val P_CUTTING_CHAI = "p_cutting_chai"
    const val P_FILTER_COFFEE = "p_filter_coffee"
    const val P_ROHAN_DRAFT = "p_rohan_draft"
    const val P_RAJWADA_RAIN = "p_rajwada_rain"
    const val P_SOFT_LIGHT = "p_soft_light"
    const val P_YELLOW_LINE = "p_yellow_line"
    const val P_MONSOON_STATIC = "p_001"
    const val P_SALT_PANS = "p_002"
    const val P_SECOND_DRAFT = "p_009"
    const val P_48_FRAMES = "p_010"
    const val P_VALLEY_HEAT = "p_012"
    const val P_PAWN = "p_013"
    // Wrapped (history)
    const val P_IRON_GATE = "p_iron_gate"
    const val P_RENT_DAY = "p_rent_day"
    const val P_HOLD_MUSIC = "p_hold_music"

    // Roles
    const val R_LL_LEAD = "r_ll_lead"
    const val R_TW_LEAD = "r_tw_lead"
    const val R_CC_LEAD = "r_cc_lead"
    const val R_FC_DP = "r_fc_dp"
    const val R_FC_SOUND = "r_fc_sound"
    const val R_FC_ACTOR = "r_fc_actor"
    const val R_FC_EDITOR = "r_fc_editor"
    const val R_FC_PD = "r_fc_pd"
    const val R_SL_SD = "r_sl_sd"
    const val R_SL_COMPOSER = "r_sl_composer"
    const val R_TR_DP = "r_tr_dp"
    const val R_DD_DP = "r_dd_dp"
    const val R_MS_DP = "r_001_1"
    const val R_RR_LEADS = "r_rr_leads"
    const val R_RR_WRITER = "r_rr_writer"

    // Matches
    const val M_MEERA_SCAM = "m_meera_scam"
    const val M_MEERA_CUTTING_CHAI = "m_meera_cutting_chai"
    const val M_MEERA_IRON_GATE = "m_meera_iron_gate"
    const val M_ROHAN_DP = "m_rohan_dp"
    const val M_FC_PD = "m_fc_pd"
    const val M_KABIR_RENT_DAY = "m_kabir_rent_day"
    const val M_KABIR_HOLD_MUSIC = "m_kabir_hold_music"
    const val M_DANA_SOFTLIGHT = "m_dana_softlight"

    // Applications / cards / days
    const val A_MEERA_REBOOK = "a_meera_rebook"
    const val A_MEERA_PAPER_BOATS = "a_meera_paper_boats"
    const val A_MEERA_DEAD_DROP = "a_meera_dead_drop"
    const val A_FC_PD_BACKUP = "a_fc_pd_backup"
    const val BC_DANA_SOFTLIGHT_1 = "bc_dana_softlight_1"
    const val SD_CUTTING_CHAI_1 = "sd_cutting_chai_1"
    const val WI_MEERA_IRON_GATE_FOOTAGE = "wi_m_meera_iron_gate_FOOTAGE"

    // Runtime-created (inject flagged example)
    const val U_SCAM_2 = "u_scam_bright_star"
    const val P_SCAM_2 = "p_scam_bright_star"

    fun personaUser(p: DemoPersona): String = when (p) {
        DemoPersona.MEERA -> MEERA
        DemoPersona.ROHAN -> ROHAN
        DemoPersona.KABIR -> KABIR
        DemoPersona.DANA -> DANA
    }

    val PERSONA_USERS: Set<String> = setOf(MEERA, ROHAN, KABIR, DANA)

    fun personaEmail(p: DemoPersona): String = "${p.name.lowercase()}@demo.calltime.app"
}
