package com.tff.app.model

import kotlinx.serialization.Serializable

@Serializable
enum class RoleCode {
    DIRECTOR, WRITER, PRODUCER, FIRST_AD, SECOND_AD, SCRIPT_SUPERVISOR, PA,
    DP, CAMERA_OP, FIRST_AC, GAFFER, GRIP, SOUND_MIXER, BOOM_OP,
    PRODUCTION_DESIGNER, ART_ASSISTANT, COSTUME, HMU, ACTOR, VOICE_ACTOR,
    EDITOR, COLORIST, VFX_ARTIST, SOUND_DESIGNER, COMPOSER, BTS_STILLS,
    INTIMACY_COORDINATOR, STUNT_COORDINATOR, LOCATION_MANAGER,
}

/** Which "role details" form (PRD Appendix A.2) a role uses. */
enum class DetailsFamily { CAST, SOUND, COMPOSER, EDIT_COLOR, VFX, CAMERA_GE, NONE }

data class RoleInfo(
    val code: RoleCode,
    val dept: Dept,
    val label: String,
    val duties: String,
    val scarcity: Scarcity,
    val remoteDefault: Boolean,
    val defaultCredit: String,
    val scopeSuggestions: List<String>,
    val detailsFamily: DetailsFamily,
)

/** PRD Appendix A. The single source for role labels, duties lines, scarcity and remote defaults. */
object Taxonomy {
    private fun r(code: RoleCode, dept: Dept, label: String, duties: String, scarcity: Scarcity, remote: Boolean,
                  credit: String, scope: List<String>, fam: DetailsFamily = DetailsFamily.NONE) =
        RoleInfo(code, dept, label, duties, scarcity, remote, credit, scope, fam)

    val all: List<RoleInfo> = listOf(
        r(RoleCode.DIRECTOR, Dept.DIRECTION, "Director", "Leads the creative vision; directs cast and camera", Scarcity.OVERSUPPLIED, false, "Director", listOf("Shot list", "Rehearsals", "Casting input")),
        r(RoleCode.WRITER, Dept.WRITING, "Writer", "Writes or rewrites the script", Scarcity.OVERSUPPLIED, true, "Written by", listOf("Rewrite", "Dialogue polish", "Treatment")),
        r(RoleCode.PRODUCER, Dept.PRODUCTION, "Producer", "Finds money, locations and people; owns schedule and budget", Scarcity.SCARCE, false, "Producer", listOf("Budget", "Locations", "Permits", "Scheduling")),
        r(RoleCode.FIRST_AD, Dept.DIRECTION, "1st Assistant Director", "Builds the schedule and runs the set day. Not a PA", Scarcity.SCARCE, false, "1st Assistant Director", listOf("Shooting schedule", "Call sheets", "Runs the floor")),
        r(RoleCode.SECOND_AD, Dept.DIRECTION, "2nd Assistant Director", "Call sheets, cast wrangling, paperwork", Scarcity.BALANCED, false, "2nd Assistant Director", listOf("Call sheets", "Cast wrangling", "Releases")),
        r(RoleCode.SCRIPT_SUPERVISOR, Dept.DIRECTION, "Script supervisor", "Continuity and script notes", Scarcity.BALANCED, false, "Script Supervisor", listOf("Continuity notes", "Lined script")),
        r(RoleCode.PA, Dept.PRODUCTION, "Production assistant", "General help: setup, runs, lock-ups", Scarcity.OVERSUPPLIED, false, "Production Assistant", listOf("Runs", "Lock-ups", "Craft services")),
        r(RoleCode.DP, Dept.CAMERA, "Director of photography", "Designs the look; leads camera and lighting", Scarcity.BALANCED, false, "Director of Photography", listOf("Camera", "Lighting design", "Shot list with director"), DetailsFamily.CAMERA_GE),
        r(RoleCode.CAMERA_OP, Dept.CAMERA, "Camera operator", "Operates camera under the DP", Scarcity.BALANCED, false, "Camera Operator", listOf("Operating", "Handheld", "Gimbal"), DetailsFamily.CAMERA_GE),
        r(RoleCode.FIRST_AC, Dept.CAMERA, "1st AC / focus puller", "Focus, lenses, camera build", Scarcity.BALANCED, false, "1st Assistant Camera", listOf("Focus pulling", "Camera build", "Data wrangling"), DetailsFamily.CAMERA_GE),
        r(RoleCode.GAFFER, Dept.GE, "Gaffer", "Head of lighting; runs lights with the DP", Scarcity.SCARCE, false, "Gaffer", listOf("Lighting", "Power distribution"), DetailsFamily.CAMERA_GE),
        r(RoleCode.GRIP, Dept.GE, "Grip", "Rigging, stands, flags, dolly", Scarcity.BALANCED, false, "Grip", listOf("Rigging", "Dolly", "Flags & stands"), DetailsFamily.CAMERA_GE),
        r(RoleCode.SOUND_MIXER, Dept.SOUND, "Production sound mixer", "Records dialogue on set; owns the sound kit", Scarcity.SCARCE, false, "Sound Recordist", listOf("Dialogue recording", "Lav wiring", "Room tone"), DetailsFamily.SOUND),
        r(RoleCode.BOOM_OP, Dept.SOUND, "Boom operator", "Positions the boom mic; assists the mixer", Scarcity.SCARCE, false, "Boom Operator", listOf("Boom", "Lav wiring"), DetailsFamily.SOUND),
        r(RoleCode.PRODUCTION_DESIGNER, Dept.ART, "Production designer", "Sets, props, the visual world", Scarcity.BALANCED, false, "Production Designer", listOf("Set dressing", "Props", "Colour palette")),
        r(RoleCode.ART_ASSISTANT, Dept.ART, "Art assistant", "Dressing, props, builds", Scarcity.BALANCED, false, "Art Assistant", listOf("Dressing", "Props", "Builds")),
        r(RoleCode.COSTUME, Dept.ART, "Costume / wardrobe", "Sources, fits and maintains costumes", Scarcity.BALANCED, false, "Costume", listOf("Sourcing", "Fittings", "Continuity")),
        r(RoleCode.HMU, Dept.ART, "Hair & makeup", "Hair and makeup looks and continuity", Scarcity.BALANCED, false, "Hair & Makeup", listOf("Makeup", "Hair", "Continuity")),
        r(RoleCode.ACTOR, Dept.CAST, "Actor", "Performs a role", Scarcity.OVERSUPPLIED, false, "Cast", listOf("Rehearsals", "Off-book by shoot"), DetailsFamily.CAST),
        r(RoleCode.VOICE_ACTOR, Dept.CAST, "Voice actor", "Voice-over and dubbing", Scarcity.BALANCED, true, "Voice", listOf("Voice-over", "ADR"), DetailsFamily.CAST),
        r(RoleCode.EDITOR, Dept.POST, "Editor", "Cuts picture; delivers versions", Scarcity.BALANCED, true, "Editor", listOf("Picture cut", "Assembly", "Revisions"), DetailsFamily.EDIT_COLOR),
        r(RoleCode.COLORIST, Dept.POST, "Colorist", "Grades the picture", Scarcity.BALANCED, true, "Colorist", listOf("Grade", "Look development"), DetailsFamily.EDIT_COLOR),
        r(RoleCode.VFX_ARTIST, Dept.POST, "VFX artist", "Compositing, cleanup, effects shots", Scarcity.BALANCED, true, "Visual Effects", listOf("Compositing", "Cleanup", "Titles"), DetailsFamily.VFX),
        r(RoleCode.SOUND_DESIGNER, Dept.POST, "Sound designer / re-recording mixer", "Edits, designs and mixes sound in post", Scarcity.BALANCED, true, "Sound Design", listOf("Dialogue edit", "Sound design", "Final mix"), DetailsFamily.SOUND),
        r(RoleCode.COMPOSER, Dept.MUSIC, "Composer", "Writes original score", Scarcity.OVERSUPPLIED, true, "Music by", listOf("Original score", "Stems", "Revisions"), DetailsFamily.COMPOSER),
        r(RoleCode.BTS_STILLS, Dept.OTHER, "BTS / stills", "Behind-the-scenes video and stills", Scarcity.OVERSUPPLIED, false, "Stills", listOf("Stills", "BTS video")),
        r(RoleCode.INTIMACY_COORDINATOR, Dept.SAFETY, "Intimacy coordinator", "Choreographs and safeguards intimate scenes", Scarcity.SCARCE, false, "Intimacy Coordinator", listOf("Choreography", "Consent check-ins")),
        r(RoleCode.STUNT_COORDINATOR, Dept.SAFETY, "Fight / stunt coordinator", "Designs and safeguards fights and stunts", Scarcity.SCARCE, false, "Stunt Coordinator", listOf("Fight choreography", "Safety plan")),
        r(RoleCode.LOCATION_MANAGER, Dept.PRODUCTION, "Locations", "Finds and secures locations and permissions", Scarcity.SCARCE, false, "Locations", listOf("Scouting", "Permissions", "Recce")),
    )

    private val byCode: Map<RoleCode, RoleInfo> = all.associateBy { it.code }

    fun info(code: RoleCode): RoleInfo = byCode.getValue(code)
    fun label(code: RoleCode): String = info(code).label
    fun dept(code: RoleCode): Dept = info(code).dept
    fun isCast(code: RoleCode): Boolean = dept(code) == Dept.CAST

    /** Departments whose remote roles follow the delivery-based post loop (PRD §3.4). */
    val POST_DEPTS: Set<Dept> = setOf(Dept.POST, Dept.MUSIC, Dept.WRITING)

    /** Departments whose roles need a gear policy (PRD §6.3). */
    val GEAR_DEPTS: Set<Dept> = setOf(Dept.CAMERA, Dept.SOUND, Dept.GE)

    /** Default application cap by scarcity (PRD §3.5). Actors: 20. */
    fun defaultCap(code: RoleCode): Int = when {
        code == RoleCode.ACTOR -> 20
        else -> when (info(code).scarcity) { Scarcity.SCARCE -> 25; Scarcity.BALANCED -> 15; Scarcity.OVERSUPPLIED -> 12 }
    }

    val byDept: Map<Dept, List<RoleInfo>> = all.groupBy { it.dept }

    /** Fields for the role details form (Appendix A.2): key to label. Values are free text in role_details. */
    fun detailFields(family: DetailsFamily): List<Pair<String, String>> = when (family) {
        DetailsFamily.CAST -> listOf("character" to "Character name", "playing_age" to "Playing age range", "size" to "Size (lead / supporting / day player)", "sides" to "Sides available (Y/N)")
        DetailsFamily.SOUND -> listOf("speaking_max" to "Speaking actors per scene (max)", "int_ext" to "INT/EXT split", "boom_op" to "Boom op budgeted (Y/N)")
        DetailsFamily.COMPOSER -> listOf("minutes" to "Minutes of music (or cues)", "temp_track" to "Temp track (Y/N)")
        DetailsFamily.EDIT_COLOR -> listOf("footage_hours" to "Hours of footage", "revisions" to "Revision rounds", "specs" to "Delivery specs")
        DetailsFamily.VFX -> listOf("shots" to "Number of shots", "previz" to "Boards / previz (Y/N)", "supervisor" to "Supervisor on set (Y/N)")
        DetailsFamily.CAMERA_GE -> listOf("package" to "Camera / lighting package", "crew_under" to "Crew under this role", "prep_days" to "Prep days")
        DetailsFamily.NONE -> emptyList()
    }
}
