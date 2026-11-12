package fr.toeic900.domain

import kotlin.random.Random

/** Ce que le sélecteur a besoin de savoir d'une question. La compétence principale est le premier tag. */
data class QuestionRef(val id: String, val tags: List<String>, val difficulty: Int) {
    val primaryTag: String get() = tags.first()
}

enum class PickReason {
    /** Question ratée il y a au moins 7 jours, qui revient une fois pour contrôle. */
    RETRY,
    /** Compétence arrivée à échéance dans sa boîte de Leitner. */
    DUE_SKILL,
    /** Compétence jamais travaillée. */
    NEW_SKILL,
    /** Rien n'est dû : entraînement libre. */
    PRACTICE,
}

data class Pick(val questionId: String, val tagId: String, val reason: PickReason)

/**
 * Choisit la prochaine question d'une session d'entraînement. Fonction pure : elle reçoit
 * l'état, renvoie un choix et ne modifie rien.
 *
 * Ordre de priorité :
 *  1. une question ratée dont le retour de contrôle est dû ;
 *  2. une compétence due (la plus en retard d'abord), servie par une question jamais vue ;
 *  3. une compétence jamais travaillée ;
 *  4. sinon, entraînement libre sur la compétence la moins travaillée de la session.
 *
 * Après chaque réponse, l'état de la compétence change (elle n'est plus due, ou plus nouvelle) :
 * la session passe donc naturellement d'une compétence à l'autre.
 */
class QuestionPicker(private val random: Random = Random.Default) {

    /**
     * @param candidates les questions du périmètre de la session, dans l'ordre de la taxonomie
     * @param focusTag la compétence choisie par l'utilisateur, ou null pour toutes
     * @param askedThisSession les questions déjà posées dans la session : jamais reposées
     * @return null quand toutes les questions du périmètre ont été posées
     */
    fun pick(
        candidates: List<QuestionRef>,
        focusTag: String?,
        skillStates: Map<String, SkillState>,
        questionStates: Map<String, QuestionState>,
        askedThisSession: Collection<String>,
        today: Long,
    ): Pick? {
        val asked = askedThisSession.toSet()
        val scope = if (focusTag == null) candidates else candidates.filter { focusTag in it.tags }
        val remaining = scope.filter { it.id !in asked }
        if (remaining.isEmpty()) return null

        val retry = remaining
            .filter { q -> questionStates[q.id]?.let { QuestionReview.isRetryDue(it, today) } == true }
            .minByOrNull { questionStates.getValue(it.id).retryDueDay!! }
        if (retry != null) return Pick(retry.id, focusTag ?: retry.primaryTag, PickReason.RETRY)

        val (tag, reason) = chooseTag(scope, remaining, focusTag, skillStates, asked, today)
        // Avec une compétence choisie, « remaining » ne contient déjà que ses questions.
        val pool = if (focusTag != null) remaining else remaining.filter { it.primaryTag == tag }
        return Pick(chooseQuestion(pool, questionStates).id, tag, reason)
    }

    private fun chooseTag(
        scope: List<QuestionRef>,
        remaining: List<QuestionRef>,
        focusTag: String?,
        skillStates: Map<String, SkillState>,
        asked: Set<String>,
        today: Long,
    ): Pair<String, PickReason> {
        if (focusTag != null) {
            val state = skillStates[focusTag]
            val reason = when {
                state == null -> PickReason.NEW_SKILL
                Leitner.isDue(state, today) -> PickReason.DUE_SKILL
                else -> PickReason.PRACTICE
            }
            return focusTag to reason
        }

        val tags = remaining.map { it.primaryTag }.distinct()

        val due = tags
            .mapNotNull { skillStates[it] }
            .filter { Leitner.isDue(it, today) }
            .minWithOrNull(compareBy<SkillState> { it.dueDay }.thenBy { it.box })
        if (due != null) return due.tagId to PickReason.DUE_SKILL

        val unseenTag = tags.firstOrNull { it !in skillStates }
        if (unseenTag != null) return unseenTag to PickReason.NEW_SKILL

        val askedPerTag = scope.filter { it.id in asked }.groupingBy { it.primaryTag }.eachCount()
        val practice = tags.minWithOrNull(
            compareBy<String> { askedPerTag[it] ?: 0 }
                .thenBy { skillStates.getValue(it).box }
                .thenBy { skillStates.getValue(it).dueDay },
        )!!
        return practice to PickReason.PRACTICE
    }

    /** Une question jamais vue si possible ; sinon celle vue il y a le plus longtemps. */
    private fun chooseQuestion(pool: List<QuestionRef>, questionStates: Map<String, QuestionState>): QuestionRef {
        val unseen = pool.filter { it.id !in questionStates }
        if (unseen.isNotEmpty()) return unseen.random(random)
        return pool.minWith(
            compareBy<QuestionRef> { questionStates.getValue(it.id).lastSeenDay }
                .thenBy { questionStates.getValue(it.id).timesSeen },
        )
    }
}
