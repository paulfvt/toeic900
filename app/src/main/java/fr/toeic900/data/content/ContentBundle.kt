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
    val questions: List<QuestionDto>,
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
data class QuestionDto(
    val id: String,
    val part: Int,
    val pool: String,
    val tags: List<String>,
    val difficulty: Int,
    val stem: String,
    val options: List<String>,
    /** Lettre de la bonne réponse : A, B, C ou D. */
    val answer: String,
    /** Clés : "fr" et "en". */
    val explanation: Map<String, ExplanationDto>,
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

class ContentFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ContentParser {
    const val SUPPORTED_FORMAT = 1
    const val LETTERS = "ABCD"
    val LANGUAGES = setOf("fr", "en")

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

        val knownTags = tagIds.toSet()
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
        }
    }

    private fun firstDuplicate(ids: List<String>): String? {
        val seen = HashSet<String>()
        return ids.firstOrNull { !seen.add(it) }
    }
}
