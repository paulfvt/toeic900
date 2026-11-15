package fr.toeic900.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonPathTest {
    private val today = 20_000L
    private val steps = listOf("l-noun", "l-adjective", "l-adverb", "r-unit-1", "l-tense")

    private fun run(correct: Int, answered: Int, finished: Boolean = true) = LessonRun(correct, answered, finished)

    // ── Seuil de réussite ──

    @Test
    fun `seventy percent rounded up is required`() {
        assertEquals(1, LessonPath.requiredCorrect(1))
        assertEquals(4, LessonPath.requiredCorrect(5))
        assertEquals(5, LessonPath.requiredCorrect(6))
        assertEquals(5, LessonPath.requiredCorrect(7))
        assertEquals(6, LessonPath.requiredCorrect(8))
        assertEquals(7, LessonPath.requiredCorrect(10))
        assertEquals(14, LessonPath.requiredCorrect(20))
        assertEquals(0, LessonPath.requiredCorrect(0))
    }

    @Test
    fun `a run is passed at the threshold and failed just below`() {
        assertTrue(LessonPath.isPassed(run(4, 5)))
        assertTrue(LessonPath.isPassed(run(5, 5)))
        assertFalse(LessonPath.isPassed(run(3, 5)))
        assertTrue(LessonPath.isPassed(run(7, 10)))
        assertFalse(LessonPath.isPassed(run(6, 10)))
    }

    @Test
    fun `a run left before the end is never passed`() {
        assertFalse(LessonPath.isPassed(run(2, 2, finished = false)))
        assertFalse(LessonPath.isPassed(run(0, 0, finished = true)))
    }

    // ── Déblocage ──

    @Test
    fun `at the start only the first step is available`() {
        assertEquals(
            listOf(StepStatus.AVAILABLE, StepStatus.LOCKED, StepStatus.LOCKED, StepStatus.LOCKED, StepStatus.LOCKED),
            LessonPath.statuses(steps, emptySet()),
        )
        assertEquals("l-noun", LessonPath.nextStep(steps, emptySet()))
    }

    @Test
    fun `completing a step unlocks the next one only`() {
        assertEquals(
            listOf(StepStatus.DONE, StepStatus.DONE, StepStatus.AVAILABLE, StepStatus.LOCKED, StepStatus.LOCKED),
            LessonPath.statuses(steps, setOf("l-noun", "l-adjective")),
        )
        assertEquals("l-adverb", LessonPath.nextStep(steps, setOf("l-noun", "l-adjective")))
    }

    @Test
    fun `the review of a unit unlocks the first lesson of the next unit`() {
        val done = setOf("l-noun", "l-adjective", "l-adverb", "r-unit-1")
        assertEquals(StepStatus.AVAILABLE, LessonPath.statuses(steps, done).last())
    }

    @Test
    fun `a finished path has no next step`() {
        assertTrue(LessonPath.statuses(steps, steps.toSet()).all { it == StepStatus.DONE })
        assertNull(LessonPath.nextStep(steps, steps.toSet()))
        assertNull(LessonPath.nextStep(emptyList(), emptySet()))
    }

    @Test
    fun `a step inserted later does not lock what was already done`() {
        // Nouveau contenu : « l-comparative » est insérée entre deux étapes déjà réussies.
        val newPath = listOf("l-noun", "l-adjective", "l-comparative", "l-adverb", "r-unit-1", "l-tense")
        val done = setOf("l-noun", "l-adjective", "l-adverb")
        assertEquals(
            listOf(StepStatus.DONE, StepStatus.DONE, StepStatus.AVAILABLE, StepStatus.DONE, StepStatus.AVAILABLE, StepStatus.LOCKED),
            LessonPath.statuses(newPath, done),
        )
        // La prochaine étape est la première disponible dans l'ordre du chemin.
        assertEquals("l-comparative", LessonPath.nextStep(newPath, done))
    }

    @Test
    fun `progress on a step removed from the path is ignored`() {
        assertEquals(StepStatus.AVAILABLE, LessonPath.statuses(steps, setOf("l-old-step")).first())
    }

    // ── Progression d'une étape ──

    @Test
    fun `a first passed run completes the step`() {
        val progress = LessonPath.afterRun(null, "l-noun", run(4, 5), today)
        assertEquals(StepProgress("l-noun", attempts = 1, bestCorrect = 4, bestTotal = 5, lastAttemptDay = today, completedDay = today), progress)
        assertTrue(progress.isCompleted)
    }

    @Test
    fun `a failed run counts as an attempt and leaves the step open`() {
        val progress = LessonPath.afterRun(null, "l-noun", run(2, 5), today)
        assertEquals(1, progress.attempts)
        assertEquals(2 to 5, progress.bestCorrect to progress.bestTotal)
        assertFalse(progress.isCompleted)
    }

    @Test
    fun `a step is completed on the day of its first success`() {
        val failed = LessonPath.afterRun(null, "l-noun", run(2, 5), today)
        val passed = LessonPath.afterRun(failed, "l-noun", run(5, 5), today + 1)
        assertEquals(today + 1, passed.completedDay)
        assertEquals(2, passed.attempts)
        assertEquals(5 to 5, passed.bestCorrect to passed.bestTotal)
    }

    @Test
    fun `a completed step stays completed when it is replayed and failed`() {
        val passed = LessonPath.afterRun(null, "l-noun", run(5, 5), today)
        val replayed = LessonPath.afterRun(passed, "l-noun", run(1, 5), today + 3)
        assertEquals(today, replayed.completedDay)
        assertEquals(5 to 5, replayed.bestCorrect to replayed.bestTotal)
        assertEquals(today + 3, replayed.lastAttemptDay)
        assertEquals(2, replayed.attempts)
    }

    @Test
    fun `the best run is the one with the highest success rate`() {
        // 5 sur 7 (71 %) est meilleur que 4 sur 6 (67 %), mais moins bon que 6 sur 7.
        val first = LessonPath.afterRun(null, "l-detail", run(4, 6), today)
        val second = LessonPath.afterRun(first, "l-detail", run(5, 7), today)
        assertEquals(5 to 7, second.bestCorrect to second.bestTotal)
        val third = LessonPath.afterRun(second, "l-detail", run(4, 6), today)
        assertEquals(5 to 7, third.bestCorrect to third.bestTotal)
    }

    @Test
    fun `an unfinished run is an attempt but never a best score`() {
        val progress = LessonPath.afterRun(null, "l-noun", run(3, 3, finished = false), today)
        assertEquals(1, progress.attempts)
        assertEquals(0 to 0, progress.bestCorrect to progress.bestTotal)
        assertFalse(progress.isCompleted)
    }
}
