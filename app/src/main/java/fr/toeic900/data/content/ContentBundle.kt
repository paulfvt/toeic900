package fr.toeic900.data.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Forme du fichier embarqué app/src/main/assets/content/content.json, produit par
 * content/tools/build.py. Kotlin pur : se teste sur le PC, sans émulateur.
 */
@Serializable
data class ContentBundle(
    val format: Int,
    /** Empreinte du contenu : si elle change, la base de contenu est reconstruite. */
    val version: String,
    val tags: List<TagDto>,
    /** Textes de la Part 6 et jeux de documents de la Part 7. */
    val stimuli: List<StimulusDto> = emptyList(),
    val questions: List<QuestionDto>,
    /** Fiches de cours : une par compétence. */
    val lessons: List<LessonDto> = emptyList(),
    /** Chemin de leçons. */
    val path: PathDto = PathDto(),
    /** Tests blancs (format 3). */
    val mockTests: List<MockTestDto> = emptyList(),
)

@Serializable
data class TagDto(
    val id: String,
    val parts: List<Int>,
    val group: String,
    val fr: String,
    val en: String,
    val order: Int,
)

@Serializable
data class StimulusDto(
    /** Identifiant stable (ex. p6-0012, p7-0031). */
    val id: String,
    val part: Int,
    val pool: String,
    /** Un seul document en Part 6 ; un à trois en Part 7. */
    val documents: List<DocumentDto>,
)

@Serializable
data class DocumentDto(
    val genre: String,
    val title: String? = null,
    /** Part 6 : les trous sont notés [[1]] à [[4]]. */
    val text: String,
)

@Serializable
data class QuestionDto(
    val id: String,
    val part: Int,
    val pool: String,
    val tags: List<String>,
    val difficulty: Int,
    /** Vide en Part 6 : la question est le trou du texte. */
    val stem: String,
    val options: List<String>,
    /** Lettre de la bonne réponse : A, B, C ou D. */
    val answer: String,
    /** Clés : "fr" et "en". */
    val explanation: Map<String, ExplanationDto>,
    /** Parts 6 et 7 : le texte ou le jeu de documents de la question. */
    val stimulusId: String? = null,
    /** Rang de la question dans son texte (0 pour la première). */
    val position: Int = 0,
    /** Passages des documents qui justifient la réponse, mot pour mot. */
    val evidence: List<String> = emptyList(),
) {
    val answerIndex: Int get() = ContentParser.LETTERS.indexOf(answer)
}

@Serializable
data class ExplanationDto(
    val rule: String,
    /** Lettre du distracteur → pourquoi il est faux. */
    val distractors: Map<String, String>,
    val trap: String,
)

/** Une fiche de cours, dans ses deux langues. */
@Serializable
data class LessonDto(
    val tag: String,
    val fr: LessonContentDto,
    val en: LessonContentDto,
)

@Serializable
data class LessonContentDto(
    val title: String,
    val summary: String,
    /** La mini-fiche montrée avant les exercices d'une leçon. */
    val keyPoints: List<String>,
    val rules: List<LessonRuleDto>,
    val traps: List<LessonTrapDto>,
)

@Serializable
data class LessonRuleDto(val title: String, val text: String, val examples: List<LessonExampleDto>)

@Serializable
data class LessonExampleDto(val en: String, val note: String)

@Serializable
data class LessonTrapDto(val title: String, val text: String)

@Serializable
data class PathDto(val units: List<PathUnitDto> = emptyList())

@Serializable
data class PathUnitDto(val id: String, val fr: String, val en: String, val steps: List<PathStepDto>)

@Serializable
data class PathStepDto(
    val id: String,
    /** "lesson" ou "review". */
    val kind: String,
    val tags: List<String>,
    val part: Int,
    /** Nombre d'exercices de l'étape. */
    val units: Int,
)

/** Un test blanc : ses unités (question Part 5, texte Part 6, jeu Part 7) dans l'ordre de l'examen. */
@Serializable
data class MockTestDto(
    val id: String,
    /** "reading" (Phase 3) ; "listening" et "full" plus tard. */
    val section: String,
    val fr: String,
    val en: String,
    val minutes: Int,
    val units: List<String>,
)

class ContentFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ContentParser {
    const val SUPPORTED_FORMAT = 3
    const val LETTERS = "ABCD"
    val LANGUAGES = setOf("fr", "en")
    val STEP_KINDS = setOf("lesson", "review")

    private val json = Json { ignoreUnknownKeys = false }

    /**
     * Lit et contrôle le contenu embarqué. Le vrai garde-fou est validate.py, en amont ; ici on
     * refuse seulement ce qui ferait planter l'app ou afficher une correction fausse.
     */
    fun parse(text: String): ContentBundle {
        val bundle = try {
            json.decodeFromString<ContentBundle>(text)
        } catch (e: SerializationException) {
            throw ContentFormatException("content.json illisible : ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ContentFormatException("content.json illisible : ${e.message}", e)
        }
        check(bundle)
        return bundle
    }

    private fun check(bundle: ContentBundle) {
        if (bundle.format != SUPPORTED_FORMAT) {
            throw ContentFormatException("format de contenu ${bundle.format} inconnu (attendu : $SUPPORTED_FORMAT)")
        }
        if (bundle.version.isBlank()) throw ContentFormatException("version de contenu vide")
        if (bundle.questions.isEmpty()) throw ContentFormatException("aucune question dans le contenu")

        val tagIds = bundle.tags.map { it.id }
        firstDuplicate(tagIds)?.let { throw ContentFormatException("tag en double : $it") }
        firstDuplicate(bundle.questions.map { it.id })?.let { throw ContentFormatException("question en double : $it") }
        firstDuplicate(bundle.stimuli.map { it.id })?.let { throw ContentFormatException("texte en double : $it") }

        val knownTags = tagIds.toSet()
        val stimuli = bundle.stimuli.associateBy { it.id }
        for (q in bundle.questions) {
            fun fail(reason: String): Nothing = throw ContentFormatException("question ${q.id} : $reason")
            if (q.options.size != LETTERS.length) fail("${q.options.size} options au lieu de 4")
            if (q.answer.length != 1 || q.answerIndex < 0) fail("réponse invalide « ${q.answer} »")
            if (q.tags.isEmpty()) fail("aucun tag")
            q.tags.firstOrNull { it !in knownTags }?.let { fail("tag inconnu « $it »") }
            if (q.explanation.keys != LANGUAGES) fail("explication attendue en fr et en en")
            val wrongLetters = LETTERS.map { it.toString() }.toSet() - q.answer
            for ((lang, explanation) in q.explanation) {
                if (explanation.distractors.keys != wrongLetters) fail("explication $lang : il faut une note par distracteur")
            }
            if (q.stimulusId == null) {
                if (q.stem.isBlank()) fail("énoncé vide")
            } else {
                val stimulus = stimuli[q.stimulusId] ?: fail("texte inconnu « ${q.stimulusId} »")
                if (stimulus.part != q.part) fail("part différente de celle de son texte")
            }
        }

        val used = bundle.questions.mapNotNull { it.stimulusId }.toSet()
        for (s in bundle.stimuli) {
            if (s.documents.isEmpty() || s.documents.any { it.text.isBlank() }) {
                throw ContentFormatException("texte ${s.id} : document vide")
            }
            if (s.id !in used) throw ContentFormatException("texte ${s.id} : aucune question")
        }

        firstDuplicate(bundle.lessons.map { it.tag })?.let { throw ContentFormatException("fiche en double : $it") }
        bundle.lessons.firstOrNull { it.tag !in knownTags }?.let { throw ContentFormatException("fiche : tag inconnu « ${it.tag} »") }

        val lessonTags = bundle.lessons.map { it.tag }.toSet()
        val steps = bundle.path.units.flatMap { it.steps }
        firstDuplicate(bundle.path.units.map { it.id } + steps.map { it.id })
            ?.let { throw ContentFormatException("chemin : identifiant en double « $it »") }
        for (step in steps) {
            fun fail(reason: String): Nothing = throw ContentFormatException("étape ${step.id} : $reason")
            if (step.kind !in STEP_KINDS) fail("type inconnu « ${step.kind} »")
            if (step.tags.isEmpty()) fail("aucun tag")
            step.tags.firstOrNull { it !in knownTags }?.let { fail("tag inconnu « $it »") }
            if (step.units < 1) fail("aucun exercice")
            if (step.kind == "lesson" && step.tags.first() !in lessonTags) fail("pas de fiche pour « ${step.tags.first()} »")
        }

        firstDuplicate(bundle.mockTests.map { it.id })?.let { throw ContentFormatException("test blanc en double : $it") }
        val poolOfUnit = bundle.questions.associate { (it.stimulusId ?: it.id) to it.pool }
        for (test in bundle.mockTests) {
            fun fail(reason: String): Nothing = throw ContentFormatException("test blanc ${test.id} : $reason")
            if (test.minutes < 1) fail("durée nulle")
            if (test.units.isEmpty()) fail("aucune question")
            for (unit in test.units) {
                val pool = poolOfUnit[unit] ?: fail("unité inconnue « $unit »")
                if (pool != "mock") fail("« $unit » n'est pas une question de test blanc")
            }
        }
    }

    private fun firstDuplicate(ids: List<String>): String? {
        val seen = HashSet<String>()
        return ids.firstOrNull { !seen.add(it) }
    }
}
