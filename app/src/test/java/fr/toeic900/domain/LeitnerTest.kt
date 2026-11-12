package fr.toeic900.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LeitnerTest {
    private val today = 20_000L

    @Test
    fun `a new skill starts in box 1 and is due immediately`() {
        val state = Leitner.newSkill("pronouns", today)
        assertEquals(1, state.box)
        assertTrue(Leitner.isDue(state, today))
        assertNull(state.lastReviewedDay)
    }

    @Test
    fun `intervals are 1, 2, 4, 8 and 16 days`() {
        assertEquals(listOf(1, 2, 4, 8, 16), (1..5).map(Leitner::intervalFor))
        assertThrows(IllegalArgumentException::class.java) { Leitner.intervalFor(0) }
        assertThrows(IllegalArgumentException::class.java) { Leitner.intervalFor(6) }
    }

    @Test
    fun `correct answers on due days climb one box at a time`() {
        var state = Leitner.newSkill("pronouns", today)
        var day = today
        val expected = listOf(2 to 2, 3 to 4, 4 to 8, 5 to 16)
        for ((box, interval) in expected) {
            state = Leitner.review(state, correct = true, today = day)
            assertEquals(box, state.box)
            assertEquals(day + interval, state.dueDay)
            assertEquals(day, state.lastReviewedDay)
            day = state.dueDay
        }
    }

    @Test
    fun `box 5 is the ceiling`() {
        val state = SkillState("pronouns", box = 5, dueDay = today)
        val next = Leitner.review(state, correct = true, today = today)
        assertEquals(5, next.box)
        assertEquals(today + 16, next.dueDay)
    }

    @Test
    fun `a wrong answer sends the skill back to box 1, due tomorrow`() {
        for (box in 1..5) {
            val next = Leitner.review(SkillState("pronouns", box, dueDay = today), correct = false, today = today)
            assertEquals(1, next.box)
            assertEquals(today + 1, next.dueDay)
            assertEquals(1, next.lapses)
        }
    }

    @Test
    fun `a correct answer before the due day changes nothing`() {
        val state = SkillState("pronouns", box = 3, dueDay = today + 2, lastReviewedDay = today - 2)
        assertSame(state, Leitner.review(state, correct = true, today = today))
    }

    @Test
    fun `a wrong answer before the due day still resets the skill`() {
        val state = SkillState("pronouns", box = 4, dueDay = today + 5)
        val next = Leitner.review(state, correct = false, today = today)
        assertEquals(1, next.box)
        assertEquals(today + 1, next.dueDay)
    }

    @Test
    fun `due means the due day has been reached`() {
        val state = SkillState("pronouns", box = 2, dueDay = today)
        assertFalse(Leitner.isDue(state, today - 1))
        assertTrue(Leitner.isDue(state, today))
        assertTrue(Leitner.isDue(state, today + 30))
    }

    @Test
    fun `a late review climbs a single box, counted from the review day`() {
        val state = SkillState("pronouns", box = 2, dueDay = today - 40)
        val next = Leitner.review(state, correct = true, today = today)
        assertEquals(3, next.box)
        assertEquals(today + 4, next.dueDay)
    }

    @Test
    fun `several correct answers the same day promote only once`() {
        var state = Leitner.newSkill("pronouns", today)
        repeat(5) { state = Leitner.review(state, correct = true, today = today) }
        assertEquals(2, state.box)
        assertEquals(today + 2, state.dueDay)
    }
}

class QuestionReviewTest {
    private val today = 20_000L

    @Test
    fun `a question answered correctly the first time never comes back`() {
        val state = QuestionReview.afterAnswer(null, "p5-0001", correct = true, today = today)
        assertEquals(1, state.timesSeen)
        assertEquals(1, state.timesCorrect)
        assertNull(state.retryDueDay)
        assertFalse(state.retryDone)
    }

    @Test
    fun `a failed question is scheduled once, seven days later`() {
        val state = QuestionReview.afterAnswer(null, "p5-0001", correct = false, today = today)
        assertEquals(today + 7, state.retryDueDay)
        assertFalse(QuestionReview.isRetryDue(state, today + 6))
        assertTrue(QuestionReview.isRetryDue(state, today + 7))
        assertTrue(QuestionReview.isRetryDue(state, today + 30))
    }

    @Test
    fun `the retry closes the loop whatever the result`() {
        for (correctOnRetry in listOf(true, false)) {
            val failed = QuestionReview.afterAnswer(null, "p5-0001", correct = false, today = today)
            val retried = QuestionReview.afterAnswer(failed, "p5-0001", correctOnRetry, today + 9)
            assertNull(retried.retryDueDay)
            assertTrue(retried.retryDone)
            assertEquals(2, retried.timesSeen)
            assertEquals(correctOnRetry, retried.lastCorrect)

            // Même ratée encore plus tard, elle n'est plus reprogrammée : « revient une fois ».
            val later = QuestionReview.afterAnswer(retried, "p5-0001", correct = false, today = today + 40)
            assertNull(later.retryDueDay)
            assertTrue(later.retryDone)
        }
    }

    @Test
    fun `seeing the question again before the retry day keeps the retry pending`() {
        val failed = QuestionReview.afterAnswer(null, "p5-0001", correct = false, today = today)
        val early = QuestionReview.afterAnswer(failed, "p5-0001", correct = true, today = today + 3)
        assertEquals(today + 7, early.retryDueDay)
        assertFalse(early.retryDone)
        assertEquals(2, early.timesSeen)
    }

    @Test
    fun `a first failure after earlier successes is scheduled too`() {
        val ok = QuestionReview.afterAnswer(null, "p5-0001", correct = true, today = today)
        val failed = QuestionReview.afterAnswer(ok, "p5-0001", correct = false, today = today + 20)
        assertEquals(today + 27, failed.retryDueDay)
        assertEquals(1, failed.timesCorrect)
    }
}
