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
    /** Part 5 : la phrase à trou. Part 7 : la question. Part 6 : vide, la question est le trou du texte. */
    val stem: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanationFr: Explanation,
    val explanationEn: Explanation,
    /** Parts 6 et 7 : le texte ou le jeu de documents auquel la question appartient. */
    val stimulusId: String? = null,
    /** Parts 6 et 7 : les passages des documents qui justifient la réponse, mot pour mot. */
    val evidence: List<String> = emptyList(),
)

/** Un document d'examen : le texte à trous d'une Part 6, ou l'un des documents d'une Part 7. */
data class Document(
    /** Nature du document (email, notice, chat…). */
    val genre: String,
    val title: String?,
    val text: String,
)

/** Une compétence, avec son état de révision (null = jamais travaillée). */
data class Skill(
    val tagId: String,
    val labelFr: String,
    val labelEn: String,
    /** Nombre d'exercices disponibles : questions (Part 5), textes (Part 6) ou jeux de documents (Part 7). */
    val unitCount: Int,
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
