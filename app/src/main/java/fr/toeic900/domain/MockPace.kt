package fr.toeic900.domain

/**
 * Indicateur de rythme d'un test blanc Reading : questions atteintes contre temps écoulé.
 *
 * Toutes les questions ne prennent pas le même temps. Repères habituels pour finir la section
 * Reading en 75 minutes : environ 22 s par question de Part 5, 30 s en Part 6 et 61 s en Part 7.
 * Le temps « attendu » à une question est la part du temps prévu qui correspond, avec ces poids,
 * aux questions qui la précèdent. Au-delà d'une marge, l'utilisateur est en retard et l'app
 * l'avertit : c'est la Part 7, en fin de test, qui en pâtit.
 */
object MockPace {
    private val SECONDS_PER_QUESTION = mapOf(5 to 22.0, 6 to 30.0, 7 to 61.0)

    /** Retard toléré avant l'alerte : 2 minutes, ou 5 % du temps prévu si c'est plus. */
    fun marginMs(plannedMs: Long): Long = maxOf(120_000L, plannedMs / 20)

    private fun weight(part: Int) = SECONDS_PER_QUESTION[part] ?: 45.0

    /** Temps attendu au moment d'aborder la question n° [reached] (0 = la première). */
    fun expectedMs(parts: List<Int>, reached: Int, plannedMs: Long): Long {
        val total = parts.sumOf(::weight)
        if (total == 0.0) return 0
        val done = parts.take(reached.coerceIn(0, parts.size)).sumOf(::weight)
        return (plannedMs * done / total).toLong()
    }

    fun status(parts: List<Int>, reached: Int, elapsedMs: Long, plannedMs: Long): Pace {
        val delta = elapsedMs - expectedMs(parts, reached, plannedMs)
        val margin = marginMs(plannedMs)
        val state = when {
            delta > margin -> PaceState.BEHIND
            delta < -margin -> PaceState.AHEAD
            else -> PaceState.ON_TRACK
        }
        val remaining = parts.drop(reached.coerceIn(0, parts.size))
        return Pace(
            state = state,
            deltaMs = delta,
            remainingMs = (plannedMs - elapsedMs).coerceAtLeast(0),
            remainingQuestions = remaining.size,
            remainingPart7 = remaining.count { it == 7 },
        )
    }
}

enum class PaceState { AHEAD, ON_TRACK, BEHIND }

data class Pace(
    val state: PaceState,
    /** Positif : en retard de tant de millisecondes sur le rythme attendu. */
    val deltaMs: Long,
    val remainingMs: Long,
    val remainingQuestions: Int,
    val remainingPart7: Int,
)
