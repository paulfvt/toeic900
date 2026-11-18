package fr.toeic900.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MockPaceTest {
    /** Le demi-test : 15 questions Part 5, 8 Part 6, 27 Part 7, en 38 minutes. */
    private val parts = List(15) { 5 } + List(8) { 6 } + List(27) { 7 }
    private val planned = 38 * 60_000L

    @Test
    fun `expected time grows faster in part 7`() {
        assertEquals(0L, MockPace.expectedMs(parts, 0, planned))
        assertEquals(planned, MockPace.expectedMs(parts, parts.size, planned))
        val endOfPart5 = MockPace.expectedMs(parts, 15, planned)
        // 15 × 22 s sur 2 217 s de poids total : environ 15 % du temps pour 30 % des questions.
        assertEquals(planned * 330 / 2217, endOfPart5)
    }

    @Test
    fun `on track, ahead or behind with a margin`() {
        val expected = MockPace.expectedMs(parts, 23, planned)
        assertEquals(PaceState.ON_TRACK, MockPace.status(parts, 23, expected + 60_000, planned).state)
        assertEquals(PaceState.BEHIND, MockPace.status(parts, 23, expected + 150_000, planned).state)
        assertEquals(PaceState.AHEAD, MockPace.status(parts, 23, expected - 150_000, planned).state)
    }

    @Test
    fun `the margin is at least two minutes`() {
        assertEquals(120_000L, MockPace.marginMs(10 * 60_000L))
        assertEquals(225_000L, MockPace.marginMs(75 * 60_000L))
    }

    @Test
    fun `remaining questions and part 7 questions are counted from the current one`() {
        val pace = MockPace.status(parts, 20, 10 * 60_000L, planned)
        assertEquals(30, pace.remainingQuestions)
        assertEquals(27, pace.remainingPart7)
        assertEquals(28 * 60_000L, pace.remainingMs)
        val late = MockPace.status(parts, 49, planned + 5_000, planned)
        assertEquals(0L, late.remainingMs)
        assertEquals(1, late.remainingPart7)
    }
}
