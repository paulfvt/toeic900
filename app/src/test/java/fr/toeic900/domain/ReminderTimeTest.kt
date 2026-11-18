package fr.toeic900.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimeTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, paris).toInstant().toEpochMilli()

    @Test
    fun `later today when the time has not passed yet`() {
        assertEquals(at(2026, 11, 18, 19, 0), ReminderTime.next(at(2026, 11, 18, 8, 30), 19 * 60, paris))
    }

    @Test
    fun `tomorrow when the time has passed or is now`() {
        assertEquals(at(2026, 11, 19, 19, 0), ReminderTime.next(at(2026, 11, 18, 21, 0), 19 * 60, paris))
        assertEquals(at(2026, 11, 19, 19, 0), ReminderTime.next(at(2026, 11, 18, 19, 0), 19 * 60, paris))
    }

    @Test
    fun `daylight saving change keeps the wall clock time`() {
        // Passage à l'heure d'été dans la nuit du 28 au 29 mars 2026.
        assertEquals(at(2026, 3, 29, 7, 30), ReminderTime.next(at(2026, 3, 28, 22, 0), 7 * 60 + 30, paris))
    }

    @Test
    fun `labels read like a French clock`() {
        assertEquals("19 h 00", ReminderTime.label(19 * 60))
        assertEquals("7 h 05", ReminderTime.label(7 * 60 + 5))
    }
}
