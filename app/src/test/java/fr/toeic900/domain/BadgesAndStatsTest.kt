package fr.toeic900.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgesAndStatsTest {
    private fun estimate(score: Int, provisional: Boolean = false) =
        ScoreEstimate(score, score - 30, score + 30, provisional, if (provisional) 0 else 1, 100)

    @Test
    fun `badge ids are unique`() {
        assertEquals(Badges.ALL.size, Badges.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `reading badges need a real estimate, not a provisional one`() {
        val provisional = BadgeSnapshot(reading = estimate(420, provisional = true))
        assertFalse(Badges.byId.getValue("reading-400").isReached(provisional))
        val real = BadgeSnapshot(reading = estimate(420))
        assertTrue(Badges.byId.getValue("reading-400").isReached(real))
        assertFalse(Badges.byId.getValue("reading-425").isReached(real))
    }

    @Test
    fun `total score badges wait for a listening estimate`() {
        val readingOnly = BadgeSnapshot(reading = estimate(495))
        assertFalse(Badges.byId.getValue("score-750").isReached(readingOnly))
        val both = BadgeSnapshot(reading = estimate(450), listening = estimate(455))
        assertTrue(Badges.byId.getValue("score-900").isReached(both))
    }

    @Test
    fun `newly unlocked skips badges already recorded`() {
        val s = BadgeSnapshot(totalAnswers = 120, goalDays = 1)
        val ids = Badges.newlyUnlocked(s, alreadyUnlocked = setOf("goal-first")).map { it.id }
        assertEquals(listOf("answers-100"), ids)
    }

    @Test
    fun `mastery and path badges`() {
        val s = BadgeSnapshot(masteredSkills = 24, skillCount = 24, stepsCompleted = 16, stepCount = 31)
        val ids = Badges.newlyUnlocked(s, emptySet()).map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("mastery-1", "mastery-5", "mastery-all", "path-half")))
        assertFalse("path-done" in ids)
    }

    // ------------------------------------------------------------------ statistiques

    private fun record(day: Long, id: String, tag: String, correct: Boolean, ms: Long = 30_000, training: Boolean = true) =
        AnswerRecord(day, id, tag, correct, ms, training)

    @Test
    fun `part is read from the stable id`() {
        assertEquals(5, Stats.partOf("p5-0142"))
        assertEquals(7, Stats.partOf("p7-9003-2"))
        assertEquals(0, Stats.partOf("x"))
    }

    @Test
    fun `tallies by part and by tag`() {
        val records = listOf(
            record(1, "p5-0001", "pronouns", true),
            record(1, "p5-0002", "pronouns", false),
            record(2, "p7-0001-1", "detail", true),
        )
        assertEquals(mapOf(5 to Tally(2, 1), 7 to Tally(1, 1)), Stats.byPart(records))
        assertEquals(Tally(2, 1), Stats.byTag(records)["pronouns"])
    }

    @Test
    fun `daily activity caps idle time per answer`() {
        val records = listOf(
            record(3, "p5-0001", "pronouns", true, ms = 60_000),
            record(3, "p5-0002", "pronouns", false, ms = 60 * 60_000),
        )
        val day = Stats.days(records).getValue(3)
        assertEquals(2, day.answers)
        assertEquals(1, day.correct)
        assertEquals(60_000 + Stats.MAX_COUNTED_MS_PER_ANSWER, day.activeMs)
    }

    @Test
    fun `last days fills empty days in order`() {
        val activity = Stats.days(listOf(record(10, "p5-0001", "pronouns", true)))
        val last = Stats.lastDays(activity, today = 11, count = 3)
        assertEquals(listOf(9L, 10L, 11L), last.map { it.day })
        assertEquals(listOf(0, 1, 0), last.map { it.answers })
    }

    @Test
    fun `recent training excludes mock answers and old answers`() {
        val today = 100L
        val records = listOf(
            record(today, "p5-0001", "pronouns", true),
            record(today - 29, "p5-0002", "pronouns", false),
            record(today - 30, "p5-0003", "pronouns", true),
            record(today, "p5-9001", "pronouns", true, training = false),
        )
        assertEquals(Tally(2, 1), Stats.recentTraining(records, today))
    }

    @Test
    fun `goal days keep days recorded under an older, lower goal`() {
        val activity = Stats.days(
            List(12) { record(5, "p5-00$it", "pronouns", true) } + List(25) { record(6, "p5-01$it", "pronouns", true) },
        )
        val goal = DailyGoal(GoalType.QUESTIONS, 20)
        assertEquals(setOf(6L), Stats.goalDays(activity, goal, recorded = emptySet()))
        assertEquals(setOf(5L, 6L), Stats.goalDays(activity, goal, recorded = setOf(5L)))
    }
}
