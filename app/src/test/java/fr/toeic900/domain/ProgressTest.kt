package fr.toeic900.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ProgressTest {
    /** Lundi 16 novembre 2026. */
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()

    @Test
    fun `weeks start on Monday`() {
        for (offset in -20L..20L) {
            val day = monday + offset
            val date = LocalDate.ofEpochDay(day)
            assertEquals(DayOfWeek.MONDAY, LocalDate.ofEpochDay(Weeks.monday(day)).dayOfWeek)
            assertEquals(date.dayOfWeek.value - 1, Weeks.dayOfWeek(day))
        }
        assertEquals(DayOfWeek.MONDAY, LocalDate.ofEpochDay(monday).dayOfWeek)
    }

    // ------------------------------------------------------------------ objectif quotidien

    @Test
    fun `daily goal in questions or minutes`() {
        val activity = DayActivity(monday, answers = 18, correct = 12, activeMs = 16 * 60_000L + 59_000)
        assertFalse(DailyGoal(GoalType.QUESTIONS, 20).isMet(activity))
        assertTrue(DailyGoal(GoalType.QUESTIONS, 18).isMet(activity))
        assertEquals(16, DailyGoal(GoalType.MINUTES, 15).progress(activity))
        assertTrue(DailyGoal(GoalType.MINUTES, 15).isMet(activity))
        assertFalse(DailyGoal(GoalType.MINUTES, 15).isMet(null))
        assertThrows(IllegalArgumentException::class.java) { DailyGoal(GoalType.QUESTIONS, 0) }
    }

    // ------------------------------------------------------------------ XP

    @Test
    fun `xp rewards correct answers, effort and milestones`() {
        assertEquals(10 * 7 + 2 * 3 + 20 + 50 + 15 * 2, Xp.total(correct = 7, wrong = 3, stepsCompleted = 1, mocksCompleted = 1, goalDays = 2))
        assertEquals(0, Xp.total(0, 0, 0, 0, 0))
    }

    @Test
    fun `levels need 100, 200, 300 more xp each time`() {
        assertEquals(listOf(0, 100, 300, 600, 1000), (1..5).map(Xp::xpForLevel))
        assertEquals(1, Xp.level(0).number)
        assertEquals(1, Xp.level(99).number)
        val two = Xp.level(100)
        assertEquals(2, two.number)
        assertEquals(0, two.xpInLevel)
        assertEquals(200, two.xpToNext)
        val mid = Xp.level(450)
        assertEquals(3, mid.number)
        assertEquals(150, mid.xpInLevel)
        assertEquals(0.5f, mid.fraction, 1e-6f)
    }

    // ------------------------------------------------------------------ série hebdomadaire

    private fun days(firstMonday: Long, vararg weekDays: Int) = weekDays.map { firstMonday + it }.toSet()

    @Test
    fun `a week is validated with at least 5 goal days by default`() {
        val lastWeek = monday - 7
        val four = WeeklyStreak.compute(days(lastWeek, 0, 1, 2, 3), today = monday + 2)
        assertEquals(0, four.weeks)
        val five = WeeklyStreak.compute(days(lastWeek, 0, 1, 2, 3, 6), today = monday + 2)
        assertEquals(1, five.weeks)
    }

    @Test
    fun `the threshold is adjustable`() {
        val lastWeek = monday - 7
        val goal = days(lastWeek, 0, 2, 4)
        assertEquals(0, WeeklyStreak.compute(goal, monday, threshold = 5).weeks)
        assertEquals(1, WeeklyStreak.compute(goal, monday, threshold = 3).weeks)
        assertThrows(IllegalArgumentException::class.java) { WeeklyStreak.compute(goal, monday, threshold = 8) }
    }

    @Test
    fun `the current unfinished week never breaks the streak`() {
        val goal = days(monday - 14, 0, 1, 2, 3, 4) + days(monday - 7, 0, 1, 2, 3, 4)
        val s = WeeklyStreak.compute(goal, today = monday + 3)
        assertEquals(2, s.weeks)
        assertFalse(s.currentWeekValidated)
        assertEquals(0, s.daysThisWeek)
        assertEquals(4, s.daysLeft(monday + 3))
    }

    @Test
    fun `the current week counts as soon as it is validated`() {
        val goal = days(monday - 7, 0, 1, 2, 3, 4) + days(monday, 0, 1, 2, 3, 4)
        val s = WeeklyStreak.compute(goal, today = monday + 4)
        assertTrue(s.currentWeekValidated)
        assertEquals(2, s.weeks)
        assertEquals(5, s.daysThisWeek)
    }

    @Test
    fun `a missed full week breaks the streak but best is remembered`() {
        val goal = days(monday - 35, 0, 1, 2, 3, 4) + days(monday - 28, 0, 1, 2, 3, 4) +
            days(monday - 21, 0, 1, 2, 3, 4) + days(monday - 7, 0, 1, 2, 3, 4)
        val s = WeeklyStreak.compute(goal, today = monday + 1)
        assertEquals(1, s.weeks)
        assertEquals(3, s.best)
    }

    @Test
    fun `days in the future are ignored`() {
        val s = WeeklyStreak.compute(days(monday, 0, 1, 2, 3, 4), today = monday + 1)
        assertEquals(2, s.daysThisWeek)
        assertEquals(0, s.weeks)
    }

    @Test
    fun `no goal day means no streak`() {
        val s = WeeklyStreak.compute(emptySet(), today = monday)
        assertEquals(0, s.weeks)
        assertEquals(0, s.best)
        assertEquals(7, s.daysLeft(monday))
    }
}
