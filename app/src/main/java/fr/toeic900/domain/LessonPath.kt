package fr.toeic900.domain

/** Une étape du chemin : une leçon (mini-fiche + exercices sur un tag) ou le bilan d'une unité. */
enum class StepKind { LESSON, REVIEW }

data class PathStep(
    /** Identifiant stable (ex. l-verb-tense) : le lien avec la progression de l'utilisateur. */
    val id: String,
    val unitId: String,
    val kind: StepKind,
    /** Leçon : son tag. Bilan : les tags de toutes les leçons de l'unité. */
    val tags: List<String>,
    /** La part d'où viennent les exercices. */
    val part: Int,
    /** Nombre d'exercices : des questions en Part 5, des textes en Part 6, des jeux de documents en Part 7. */
    val exerciseCount: Int,
)

/** Ce que l'utilisateur a fait d'une étape. */
data class StepProgress(
    val stepId: String,
    /** Nombre de passages, réussis ou non. */
    val attempts: Int,
    /** Meilleur passage mené jusqu'au bout. */
    val bestCorrect: Int,
    val bestTotal: Int,
    val lastAttemptDay: Long,
    /** Jour de la première réussite ; null tant que l'étape n'est pas réussie. */
    val completedDay: Long? = null,
) {
    val isCompleted: Boolean get() = completedDay != null
}

enum class StepStatus {
    /** Réussie au moins une fois : elle peut être rejouée librement. */
    DONE,
    /** La prochaine à faire. */
    AVAILABLE,
    /** L'étape précédente n'est pas encore réussie. */
    LOCKED,
}

/** Un passage sur une étape. */
data class LessonRun(
    val correct: Int,
    val answered: Int,
    /** Vrai si tous les exercices prévus ont été faits (session non abandonnée en route). */
    val finished: Boolean,
)

/**
 * Règles du chemin de leçons, en Kotlin pur.
 *
 *  - Les étapes se débloquent l'une après l'autre : une étape est disponible quand celle qui la
 *    précède dans le chemin est réussie. La toute première est toujours disponible.
 *  - Une étape est réussie quand un passage est mené jusqu'au bout avec au moins
 *    [PASS_PERCENT] % de bonnes réponses (4 sur 5, 5 sur 6, 7 sur 10).
 *  - Une étape réussie le reste : la rejouer et la rater ne reverrouille rien.
 *  - Si une nouvelle étape est insérée plus tard dans le chemin (nouveau contenu), les étapes
 *    déjà réussies le restent ; la nouvelle devient disponible dès que la précédente est réussie.
 */
object LessonPath {
    const val PASS_PERCENT = 70

    /** Nombre de bonnes réponses exigé sur [total] questions (arrondi vers le haut). */
    fun requiredCorrect(total: Int): Int {
        require(total >= 0) { "total négatif : $total" }
        return (total * PASS_PERCENT + 99) / 100
    }

    fun isPassed(run: LessonRun): Boolean =
        run.finished && run.answered > 0 && run.correct >= requiredCorrect(run.answered)

    /** L'état de chaque étape, dans l'ordre du chemin. */
    fun statuses(orderedStepIds: List<String>, completed: Set<String>): List<StepStatus> =
        orderedStepIds.mapIndexed { index, id ->
            when {
                id in completed -> StepStatus.DONE
                index == 0 || orderedStepIds[index - 1] in completed -> StepStatus.AVAILABLE
                else -> StepStatus.LOCKED
            }
        }

    /** La prochaine étape à faire : la première disponible, ou null si tout le chemin est réussi. */
    fun nextStep(orderedStepIds: List<String>, completed: Set<String>): String? =
        orderedStepIds.zip(statuses(orderedStepIds, completed)).firstOrNull { it.second == StepStatus.AVAILABLE }?.first

    /** La progression d'une étape après un passage. */
    fun afterRun(previous: StepProgress?, stepId: String, run: LessonRun, today: Long): StepProgress {
        val base = previous ?: StepProgress(stepId, attempts = 0, bestCorrect = 0, bestTotal = 0, lastAttemptDay = today)
        // « Meilleur passage » : le plus fort taux de réussite parmi les passages menés au bout.
        val better = run.finished && run.answered > 0 &&
            (base.bestTotal == 0 || run.correct.toLong() * base.bestTotal > base.bestCorrect.toLong() * run.answered)
        return base.copy(
            attempts = base.attempts + 1,
            bestCorrect = if (better) run.correct else base.bestCorrect,
            bestTotal = if (better) run.answered else base.bestTotal,
            lastAttemptDay = today,
            completedDay = base.completedDay ?: today.takeIf { isPassed(run) },
        )
    }
}
