package fr.toeic900.domain

/** Une réponse, réduite à ce dont les statistiques ont besoin. */
data class AnswerRecord(
    val day: Long,
    val questionId: String,
    val tagId: String,
    val correct: Boolean,
    val timeSpentMs: Long,
    /** Faux pour les réponses d'un test blanc : elles ne comptent pas comme entraînement. */
    val training: Boolean = true,
)

object Stats {
    /** Au-delà, une réponse compte pour ce plafond dans le temps passé (téléphone posé, pause…). */
    const val MAX_COUNTED_MS_PER_ANSWER = 5 * 60_000L

    /** Fenêtre de l'entraînement pris en compte dans le score estimé. */
    const val TRAINING_WINDOW_DAYS = 30

    /** La part d'une question, lue dans son identifiant stable (p5-0142 -> 5). */
    fun partOf(questionId: String): Int = questionId.getOrNull(1)?.digitToIntOrNull() ?: 0

    fun tally(records: Iterable<AnswerRecord>): Tally {
        var total = 0
        var correct = 0
        for (r in records) {
            total++
            if (r.correct) correct++
        }
        return Tally(total, correct)
    }

    fun byPart(records: List<AnswerRecord>): Map<Int, Tally> =
        records.groupBy { partOf(it.questionId) }.mapValues { tally(it.value) }.toSortedMap()

    fun byTag(records: List<AnswerRecord>): Map<String, Tally> =
        records.groupBy { it.tagId }.mapValues { tally(it.value) }

    /** Activité jour par jour (seuls les jours avec au moins une réponse). */
    fun days(records: List<AnswerRecord>): Map<Long, DayActivity> =
        records.groupBy { it.day }.mapValues { (day, list) ->
            DayActivity(
                day = day,
                answers = list.size,
                correct = list.count { it.correct },
                activeMs = list.sumOf { it.timeSpentMs.coerceIn(0, MAX_COUNTED_MS_PER_ANSWER) },
            )
        }

    /** Les [count] derniers jours jusqu'à [today] compris, du plus ancien au plus récent, jours vides inclus. */
    fun lastDays(activity: Map<Long, DayActivity>, today: Long, count: Int): List<DayActivity> =
        (today - count + 1..today).map { activity[it] ?: DayActivity(it, 0, 0, 0) }

    /** Entraînement des [TRAINING_WINDOW_DAYS] derniers jours, pour le score estimé. */
    fun recentTraining(records: List<AnswerRecord>, today: Long): Tally =
        tally(records.filter { it.training && it.day > today - TRAINING_WINDOW_DAYS && it.day <= today })

    /**
     * Jours où l'objectif est atteint : ceux enregistrés au moment où il l'a été (l'objectif d'alors),
     * plus ceux qui l'atteignent avec l'objectif actuel. Monter l'objectif n'efface donc pas le passé.
     */
    fun goalDays(activity: Map<Long, DayActivity>, goal: DailyGoal, recorded: Set<Long>): Set<Long> =
        recorded + activity.values.filter(goal::isMet).map { it.day }
}
