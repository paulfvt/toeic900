package fr.toeic900.domain

import java.time.LocalDate

/** Une explication de correction, dans une langue. */
data class Explanation(
    /** La règle qui donne la bonne réponse. */
    val rule: String,
    /** Une note par option, dans l'ordre A-D ; vide à la position de la bonne réponse. */
    val distractorNotes: List<String>,
    /** Le piège à retenir. */
    val trap: String,
)

data class Question(
    val id: String,
    val part: Int,
    val tags: List<String>,
    val difficulty: Int,
    val stem: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanationFr: Explanation,
    val explanationEn: Explanation,
)

/** Une compétence, avec son état de révision (null = jamais travaillée). */
data class Skill(
    val tagId: String,
    val labelFr: String,
    val labelEn: String,
    val questionCount: Int,
    val state: SkillState?,
)

/** L'heure qu'il est. Une interface pour que les tests puissent choisir le jour. */
interface Clock {
    /** Aujourd'hui, en « epoch day ». */
    fun today(): Long
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun today(): Long = LocalDate.now().toEpochDay()
    override fun nowMillis(): Long = System.currentTimeMillis()
}
