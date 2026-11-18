package fr.toeic900.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Heure du rappel quotidien. */
object ReminderTime {
    /** Prochain déclenchement strictement après [nowMillis] à l'heure [minuteOfDay] (minutes depuis minuit). */
    fun next(nowMillis: Long, minuteOfDay: Int, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var candidate = now.toLocalDate().atTime(time).atZone(zone)
        if (!candidate.isAfter(now)) candidate = now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return candidate.toInstant().toEpochMilli()
    }

    /** Le jour (epoch day) d'un instant, dans le fuseau du téléphone. */
    fun dayOf(millis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun label(minuteOfDay: Int): String = "%d h %02d".format(minuteOfDay / 60, minuteOfDay % 60)
}
