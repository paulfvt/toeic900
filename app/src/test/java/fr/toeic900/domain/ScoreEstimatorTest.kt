package fr.toeic900.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEstimatorTest {
    private fun mock(day: Long, correct: Int, total: Int = 50) = MockScore(day, Tally(total, correct))

    @Test
    fun `the table goes from 5 to 495 and never decreases`() {
        assertEquals(5.0, ScoreEstimator.convert(0.0), 0.0)
        assertEquals(495.0, ScoreEstimator.convert(1.0), 0.0)
        var previous = 0.0
        for (i in 0..1000) {
            val score = ScoreEstimator.convert(i / 1000.0)
            assertTrue("décroît à ${i / 10.0} %", score >= previous)
            previous = score
        }
    }

    @Test
    fun `the table interpolates between its points and clamps out of range rates`() {
        assertEquals(320.0, ScoreEstimator.convert(0.70), 1e-9)
        assertEquals(350.0, ScoreEstimator.convert(0.75), 1e-9)
        assertEquals(5.0, ScoreEstimator.convert(-0.3), 0.0)
        assertEquals(495.0, ScoreEstimator.convert(1.4), 0.0)
    }

    @Test
    fun `scores are rounded to a multiple of 5 within 5-495`() {
        assertEquals(320, ScoreEstimator.roundScore(321.4))
        assertEquals(325, ScoreEstimator.roundScore(322.6))
        assertEquals(5, ScoreEstimator.roundScore(-12.0))
        assertEquals(495, ScoreEstimator.roundScore(503.0))
    }

    @Test
    fun `no estimate without a mock test and with too little training`() {
        assertNull(ScoreEstimator.estimate(emptyList(), Tally(29, 20)))
    }

    @Test
    fun `training alone gives a provisional estimate with a wide range`() {
        val e = ScoreEstimator.estimate(emptyList(), Tally(200, 140))!!
        assertTrue(e.provisional)
        assertEquals(320, e.score)
        assertTrue(e.low < e.score && e.score < e.high)
        assertTrue("fourchette trop étroite : ${e.low}-${e.high}", e.high - e.low >= 100)
    }

    @Test
    fun `a mock test weighs 80 percent and training 20 percent`() {
        // Test blanc à 90 %, entraînement à 70 % : 0,8 × 0,9 + 0,2 × 0,7 = 0,86 -> 416.
        val e = ScoreEstimator.estimate(listOf(mock(10, 45)), Tally(100, 70))!!
        assertFalse(e.provisional)
        assertEquals(ScoreEstimator.roundScore(ScoreEstimator.convert(0.86)), e.score)
        assertEquals(415, e.score)
    }

    @Test
    fun `too little training leaves the mock tests alone`() {
        val e = ScoreEstimator.estimate(listOf(mock(10, 45)), Tally(10, 0))!!
        assertEquals(440, e.score)
    }

    @Test
    fun `recent mock tests count more than old ones`() {
        val old = mock(1, 25)       // 50 %
        val recent = mock(20, 45)   // 90 %
        val e = ScoreEstimator.estimate(listOf(old, recent), Tally.EMPTY)!!
        // (1 × 0,9 + 0,6 × 0,5) / 1,6 = 0,75 -> 350, plus proche du récent que la moyenne simple (0,70).
        assertEquals(350, e.score)
        val reversed = ScoreEstimator.estimate(listOf(mock(1, 45), mock(20, 25)), Tally.EMPTY)!!
        assertTrue(reversed.score < e.score)
    }

    @Test
    fun `more data narrows the range`() {
        val one = ScoreEstimator.estimate(listOf(mock(1, 40)), Tally.EMPTY)!!
        val three = ScoreEstimator.estimate(listOf(mock(1, 40), mock(2, 40), mock(3, 40)), Tally.EMPTY)!!
        assertEquals(one.score, three.score)
        assertTrue(three.high - three.low < one.high - one.low)
    }

    @Test
    fun `a mock test removes the provisional label and its extra margin`() {
        val provisional = ScoreEstimator.estimate(emptyList(), Tally(400, 280))!!
        val withMock = ScoreEstimator.estimate(listOf(mock(5, 35)), Tally(400, 280))!!
        assertTrue(provisional.provisional)
        assertFalse(withMock.provisional)
        assertEquals(1, withMock.mockCount)
    }

    @Test
    fun `the range stays within 5-495 even at the extremes`() {
        val perfect = ScoreEstimator.estimate(listOf(mock(1, 50)), Tally.EMPTY)!!
        assertEquals(495, perfect.score)
        assertEquals(495, perfect.high)
        val zero = ScoreEstimator.estimate(listOf(mock(1, 0)), Tally.EMPTY)!!
        assertEquals(5, zero.score)
        assertEquals(5, zero.low)
        assertNotNull(zero)
    }

    @Test
    fun `two mock tests on the same day are ordered by their order field`() {
        val e = ScoreEstimator.estimate(
            listOf(MockScore(5, Tally(50, 25), order = 2), MockScore(5, Tally(50, 45), order = 1)), Tally.EMPTY,
        )!!
        // Le plus récent (order 2, 50 %) pèse 1, l'autre 0,6 : (0,5 + 0,54) / 1,6 = 0,65.
        assertEquals(ScoreEstimator.roundScore(ScoreEstimator.convert(0.65)), e.score)
    }
}
