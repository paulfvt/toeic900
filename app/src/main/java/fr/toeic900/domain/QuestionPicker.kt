package fr.toeic900.domain

import kotlin.random.Random

/** Ce que le sélecteur a besoin de savoir d'une question. La compétence principale est le premier tag. */
data class QuestionRef(val id: String, val tags: List<String>, val difficulty: Int) {
    val primaryTag: String get() = tags.first()
}

/**
 * L'unité servie à l'utilisateur.
 *  - Part 5 : une question seule (l'unité porte l'identifiant de la question).
 *  - Parts 6 et 7 : un texte ou un jeu de documents, avec TOUTES ses questions. On ne relit pas
 *    un texte pour une seule question : le texte est toujours servi en entier.
 */
data class UnitRef(val id: String, val questions: List<QuestionRef>) {
    init {
        require(questions.isNotEmpty()) { "unité sans question : $id" }
    }

    /** Les compétences principales travaillées par l'unité, dans l'ordre de ses questions. */
    val primaryTags: List<String> get() = questions.map { it.primaryTag }.distinct()

    fun hasTag(tag: String): Boolean = questions.any { tag in it.tags }
}

enum class PickReason {
    /** Question (ou texte) ratée il y a au moins 7 jours, qui revient une fois pour contrôle. */
    RETRY,
    /** Compétence arrivée à échéance dans sa boîte de Leitner. */
    DUE_SKILL,
    /** Compétence jamais travaillée. */
    NEW_SKILL,
    /** Rien n'est dû : entraînement libre. */
    PRACTICE,
}

data class Pick(val questionId: String, val tagId: String, val reason: PickReason)

data class UnitPick(val unitId: String, val tagId: String, val reason: PickReason)

/**
 * Choisit la prochaine unité d'une session d'entraînement. Fonction pure : elle reçoit
 * l'état, renvoie un choix et ne modifie rien.
 *
 * Ordre de priorité :
 *  1. une unité dont le retour de contrôle est dû (une question ratée il y a 7 jours ou plus) ;
 *  2. une compétence due, servie par une unité jamais vue : la plus en retard d'abord, parmi
 *     celles qui ont encore une unité neuve ; à défaut, la plus en retard, sur l'unité vue il y
 *     a le plus longtemps ;
 *  3. une compétence jamais travaillée ;
 *  4. sinon, entraînement libre sur la compétence la moins travaillée de la session.
 *
 * Après chaque réponse, l'état de la compétence change (elle n'est plus due, ou plus nouvelle) :
 * la session passe donc naturellement d'une compétence à l'autre.
 *
 * QUAND L'UNITÉ EST UN TEXTE (Parts 6 et 7)
 *  - Leitner reste au niveau de la compétence : chaque question du texte met à jour la boîte de
 *    son propre tag, exactement comme une question de Part 5. Un texte est choisi parce qu'UNE
 *    de ses questions travaille la compétence due ; ses autres questions comptent aussi, chacune
 *    pour son tag.
 *  - Un texte est « jamais vu » tant qu'aucune de ses questions n'a reçu de réponse.
 *  - Retour unique : si au moins une question d'un texte a été ratée, le texte ENTIER revient
 *    une fois, au plus tôt 7 jours après la première erreur. Ce jour-là, toutes ses questions
 *    sont marquées « retour fait » (voir [QuestionReview.afterAnswer], paramètre controlReturn) :
 *    le texte n'est plus jamais reprogrammé, même si une autre question est ratée au retour.
 *    Entre-temps, la compétence se révise sur d'autres textes.
 */
class UnitPicker(private val random: Random = Random.Default) {

    /**
     * @param candidates les unités du périmètre de la session
     * @param focusTag la compétence choisie par l'utilisateur, ou null pour toutes
     * @param askedThisSession les unités déjà servies dans la session : jamais resservies
     * @param tagOrder l'ordre de la taxonomie : il départage les compétences jamais travaillées
     * @return null quand toutes les unités du périmètre ont été servies
     */
    fun pick(
        candidates: List<UnitRef>,
        focusTag: String?,
        skillStates: Map<String, SkillState>,
        questionStates: Map<String, QuestionState>,
        askedThisSession: Collection<String>,
        today: Long,
        tagOrder: List<String> = emptyList(),
    ): UnitPick? {
        val asked = askedThisSession.toSet()
        val scope = if (focusTag == null) candidates else candidates.filter { it.hasTag(focusTag) }
        val remaining = scope.filter { it.id !in asked }
        if (remaining.isEmpty()) return null

        val retry = remaining
            .mapNotNull { unit -> dueRetry(unit, questionStates, today)?.let { unit to it } }
            .minByOrNull { (_, failed) -> questionStates.getValue(failed.id).retryDueDay!! }
        if (retry != null) {
            val (unit, failed) = retry
            return UnitPick(unit.id, focusTag ?: failed.primaryTag, PickReason.RETRY)
        }

        val (tag, reason) = chooseTag(scope, remaining, focusTag, skillStates, questionStates, asked, today, tagOrder)
        // Avec une compétence choisie, « remaining » ne contient déjà que ses unités.
        val pool = if (focusTag != null) remaining else remaining.filter { tag in it.primaryTags }
        return UnitPick(chooseUnit(pool, questionStates).id, tag, reason)
    }

    /** La question de l'unité dont le retour de contrôle est dû depuis le plus longtemps, s'il y en a une. */
    private fun dueRetry(unit: UnitRef, questionStates: Map<String, QuestionState>, today: Long): QuestionRef? =
        unit.questions
            .filter { q -> questionStates[q.id]?.let { QuestionReview.isRetryDue(it, today) } == true }
            .minByOrNull { questionStates.getValue(it.id).retryDueDay!! }

    private fun chooseTag(
        scope: List<UnitRef>,
        remaining: List<UnitRef>,
        focusTag: String?,
        skillStates: Map<String, SkillState>,
        questionStates: Map<String, QuestionState>,
        asked: Set<String>,
        today: Long,
        tagOrder: List<String>,
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

        val rank = tagOrder.withIndex().associate { (index, tag) -> tag to index }
        // Tri stable : sans ordre de taxonomie, on garde l'ordre des unités.
        val tags = remaining.flatMap { it.primaryTags }.distinct().sortedBy { rank[it] ?: Int.MAX_VALUE }

        // Parmi les compétences dues, d'abord celles qu'une unité jamais vue peut servir : on ne
        // repose une unité déjà vue que lorsqu'il n'y a plus rien de neuf à proposer.
        val freshTags = remaining
            .filter { unit -> unit.questions.none { it.id in questionStates } }
            .flatMap { it.primaryTags }
            .toSet()
        val due = tags
            .mapNotNull { skillStates[it] }
            .filter { Leitner.isDue(it, today) }
            .minWithOrNull(compareBy<SkillState> { if (it.tagId in freshTags) 0 else 1 }.thenBy { it.dueDay }.thenBy { it.box })
        if (due != null) return due.tagId to PickReason.DUE_SKILL

        val unseenTag = tags.firstOrNull { it !in skillStates }
        if (unseenTag != null) return unseenTag to PickReason.NEW_SKILL

        val askedPerTag = scope.filter { it.id in asked }.flatMap { it.primaryTags }.groupingBy { it }.eachCount()
        val practice = tags.minWithOrNull(
            compareBy<String> { askedPerTag[it] ?: 0 }
                .thenBy { skillStates.getValue(it).box }
                .thenBy { skillStates.getValue(it).dueDay },
        )!!
        return practice to PickReason.PRACTICE
    }

    /** Une unité jamais vue si possible ; sinon celle vue il y a le plus longtemps. */
    private fun chooseUnit(pool: List<UnitRef>, questionStates: Map<String, QuestionState>): UnitRef {
        val unseen = pool.filter { unit -> unit.questions.none { it.id in questionStates } }
        if (unseen.isNotEmpty()) return unseen.random(random)
        fun states(unit: UnitRef) = unit.questions.mapNotNull { questionStates[it.id] }
        return pool.minWith(
            compareBy<UnitRef> { unit -> states(unit).maxOf { it.lastSeenDay } }
                .thenBy { unit -> states(unit).maxOf { it.timesSeen } },
        )
    }
}

/**
 * Le même choix pour des questions seules (Part 5) : chaque question est une unité à elle seule.
 */
class QuestionPicker(random: Random = Random.Default) {
    private val units = UnitPicker(random)

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
    ): Pick? = units.pick(
        candidates = candidates.map { UnitRef(it.id, listOf(it)) },
        focusTag = focusTag,
        skillStates = skillStates,
        questionStates = questionStates,
        askedThisSession = askedThisSession,
        today = today,
    )?.let { Pick(it.unitId, it.tagId, it.reason) }
}
