package fr.toeic900.domain

/**
 * Révision espacée de Leitner, appliquée à la COMPÉTENCE (tag) et non à la question.
 *
 * Cinq boîtes. Une compétence en boîte n est revue après 1, 2, 4, 8 ou 16 jours.
 *  - Bonne réponse le jour où la compétence est due : elle monte d'une boîte.
 *  - Bonne réponse alors qu'elle n'est pas encore due : rien ne change. S'entraîner en
 *    avance ne prouve pas qu'on s'en souviendra dans huit jours.
 *  - Mauvaise réponse, à n'importe quel moment : retour en boîte 1, à revoir demain.
 *
 * Tout est en Kotlin pur, sans Android : les dates sont des « epoch days »
 * (LocalDate.toEpochDay(), le nombre de jours depuis le 1er janvier 1970).
 */
data class SkillState(
    val tagId: String,
    /** Boîte de 1 à 5. */
    val box: Int,
    /** Premier jour où la compétence est à revoir. */
    val dueDay: Long,
    /** Dernier jour où une réponse a compté, ou null si la compétence n'a jamais été travaillée. */
    val lastReviewedDay: Long? = null,
    /** Nombre de retours en boîte 1. */
    val lapses: Int = 0,
)

object Leitner {
    const val FIRST_BOX = 1
    const val LAST_BOX = 5
    private val INTERVAL_DAYS = intArrayOf(1, 2, 4, 8, 16)

    fun intervalFor(box: Int): Int {
        require(box in FIRST_BOX..LAST_BOX) { "boîte hors limites : $box" }
        return INTERVAL_DAYS[box - 1]
    }

    /** Une compétence jamais travaillée : boîte 1, due tout de suite. */
    fun newSkill(tagId: String, today: Long) = SkillState(tagId = tagId, box = FIRST_BOX, dueDay = today)

    fun isDue(state: SkillState, today: Long): Boolean = state.dueDay <= today

    fun review(state: SkillState, correct: Boolean, today: Long): SkillState = when {
        !correct -> state.copy(
            box = FIRST_BOX,
            dueDay = today + intervalFor(FIRST_BOX),
            lastReviewedDay = today,
            lapses = state.lapses + 1,
        )
        isDue(state, today) -> {
            val box = minOf(state.box + 1, LAST_BOX)
            state.copy(box = box, dueDay = today + intervalFor(box), lastReviewedDay = today)
        }
        else -> state
    }
}

/**
 * Suivi d'une question pour un utilisateur.
 *
 * Une question ratée revient UNE fois, au plus tôt [QuestionReview.RETRY_DELAY_DAYS] jours
 * plus tard, pour contrôle. Entre-temps, la révision passe par d'autres questions de la même
 * compétence : on apprend la règle, pas la réponse.
 */
data class QuestionState(
    val questionId: String,
    val timesSeen: Int,
    val timesCorrect: Int,
    val lastSeenDay: Long,
    val lastCorrect: Boolean,
    /** Non nul tant que la question doit revenir : premier jour possible de son retour. */
    val retryDueDay: Long? = null,
    /** Vrai une fois la question revenue : elle ne sera plus jamais reprogrammée. */
    val retryDone: Boolean = false,
)

object QuestionReview {
    const val RETRY_DELAY_DAYS = 7

    fun isRetryDue(state: QuestionState, today: Long): Boolean =
        state.retryDueDay != null && state.retryDueDay <= today

    fun afterAnswer(previous: QuestionState?, questionId: String, correct: Boolean, today: Long): QuestionState {
        val seen = QuestionState(
            questionId = questionId,
            timesSeen = (previous?.timesSeen ?: 0) + 1,
            timesCorrect = (previous?.timesCorrect ?: 0) + if (correct) 1 else 0,
            lastSeenDay = today,
            lastCorrect = correct,
            retryDueDay = previous?.retryDueDay,
            retryDone = previous?.retryDone ?: false,
        )
        return when {
            // C'était le retour de contrôle : quel que soit le résultat, la question a fait son tour.
            previous != null && isRetryDue(previous, today) -> seen.copy(retryDueDay = null, retryDone = true)
            // Retour déjà programmé mais pas encore dû (question revue par hasard) : on n'y touche pas.
            seen.retryDueDay != null -> seen
            // Première erreur : la question reviendra une fois.
            !correct && !seen.retryDone -> seen.copy(retryDueDay = today + RETRY_DELAY_DAYS)
            else -> seen
        }
    }
}
