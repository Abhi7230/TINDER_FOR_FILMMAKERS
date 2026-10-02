package com.tff.app.ui.screens.projects

import com.tff.app.data.demo.DemoIds
import com.tff.app.data.demo.DemoQueries
import com.tff.app.data.demo.T
import com.tff.app.model.DemoPersona
import com.tff.app.model.OwedBy
import com.tff.app.model.Project
import com.tff.app.model.ProjectStatus
import com.tff.app.model.ProjectSummary
import com.tff.app.model.WrapItem
import com.tff.app.model.WrapKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** FIXB: deleted drafts leave Projects, Joined is sorted by what needs me, and the day sheet's "Getting home" line. */
class ProjectsFixbTest {
    private val today = T.anchor

    private fun project(id: String, status: ProjectStatus, dates: List<LocalDate> = emptyList(), publishedAt: Long? = 1L) =
        Project(id = id, ownerId = "u", payerId = "u", title = id, logline = "", cityId = "c", areaId = "a", status = status,
            shootDates = dates, publishedAt = publishedAt)

    private fun row(p: Project, next: LocalDate? = null, overdue: Boolean = false): JoinedRow {
        val items = if (overdue) listOf(WrapItem("w_${p.id}", "m", WrapKind.FOOTAGE, today.minusDays(3), OwedBy.PRODUCTION)) else emptyList()
        return JoinedRow(ProjectSummary(p, emptyList(), 0, 0, nextShootDate = next), items)
    }

    @Test fun `deleting Rohan's draft takes it out of Mine and the dashboard`() {
        val (e, _) = T.engine()
        e.startDemo(DemoPersona.ROHAN)
        e.cancelProject(DemoIds.ROHAN, DemoIds.P_ROHAN_DRAFT, ProjectDashboardViewModel.DRAFT_DELETED).getOrThrow()
        val mine = DemoQueries(e).myProjects(e.state.value, DemoIds.ROHAN)
        val draft = mine.first { it.project.id == DemoIds.P_ROHAN_DRAFT }.project
        assertTrue(ProjectDashboardViewModel.isDeletedDraft(draft))
        val visible = ProjectsViewModel.visibleMine(mine).map { it.project.id }
        assertFalse(DemoIds.P_ROHAN_DRAFT in visible)
        assertTrue(DemoIds.P_FILTER_COFFEE in visible)
    }

    @Test fun `a cancelled project that was live stays in Mine`() {
        val live = ProjectSummary(project("p_live", ProjectStatus.CANCELLED, publishedAt = 5L), emptyList(), 0, 0)
        assertEquals(listOf("p_live"), ProjectsViewModel.visibleMine(listOf(live)).map { it.project.id })
        assertFalse(ProjectDashboardViewModel.isDeletedDraft(live.project))
    }

    @Test fun `Joined puts overdue first, then the next call, then wrapped`() {
        val wrappedLate = row(project("wrapped_late", ProjectStatus.WRAPPED, listOf(today.minusDays(40))), overdue = true)
        val wrappedOld = row(project("wrapped_old", ProjectStatus.WRAPPED, listOf(today.minusDays(90))))
        val wrappedRecent = row(project("wrapped_recent", ProjectStatus.WRAPPED, listOf(today.minusDays(10))))
        val soon = row(project("soon", ProjectStatus.OPEN, listOf(today.plusDays(6))), next = today.plusDays(6))
        val later = row(project("later", ProjectStatus.OPEN, listOf(today.plusDays(15))), next = today.plusDays(15))
        val sorted = ProjectsViewModel.sortJoined(listOf(later, wrappedOld, soon, wrappedRecent, wrappedLate), today)
        assertEquals(listOf("wrapped_late", "soon", "later", "wrapped_recent", "wrapped_old"), sorted.map { it.summary.project.id })
    }

    @Test fun `a wrap at 22 00 or later, or past midnight, needs a getting-home line`() {
        val base = DayForm(day = today, generalCall = LocalTime.of(16, 0))
        assertFalse(base.copy(wrapBy = LocalTime.of(21, 59)).lateWrap)
        assertTrue(base.copy(wrapBy = LocalTime.of(22, 0)).lateWrap)
        assertTrue(base.copy(wrapBy = LocalTime.of(23, 0)).lateWrap)
        assertTrue(base.copy(wrapBy = LocalTime.of(1, 30)).lateWrap)          // overnight
        assertFalse(DayForm(day = today, generalCall = LocalTime.of(7, 0), wrapBy = LocalTime.of(17, 0)).lateWrap)
    }

    @Test fun `getting home is stored as the first line of the notes and read back`() {
        val stored = DayNotes.join("Cab paid for anyone after 22:00", "Bring a dark jacket")
        assertEquals("Getting home: Cab paid for anyone after 22:00\nBring a dark jacket", stored)
        assertEquals("Cab paid for anyone after 22:00" to "Bring a dark jacket", DayNotes.split(stored))
        assertEquals("Getting home: Van to Dadar station", DayNotes.join("Van to Dadar station", " "))
        assertEquals("Van to Dadar station" to "", DayNotes.split("Getting home: Van to Dadar station"))
        assertEquals("" to "Wardrobe at 16:00", DayNotes.split("Wardrobe at 16:00"))
        assertEquals("Wardrobe at 16:00", DayNotes.join("", "Wardrobe at 16:00"))
        // The combined notes still fit the 500-character limit the backend checks.
        val home = "x".repeat(DayForm.HOME_MAX)
        val notes = "y".repeat(DayForm.notesBudget(home))
        assertEquals(DayForm.NOTES_MAX, DayNotes.join(home, notes).length)
    }
}
