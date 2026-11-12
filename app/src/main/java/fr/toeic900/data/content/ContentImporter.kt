package fr.toeic900.data.content

/** Où l'import écrit. Une interface pour pouvoir tester l'import sans base de données. */
interface ContentStore {
    suspend fun currentVersion(): String?
    suspend fun replaceAll(bundle: ContentBundle)
}

sealed interface ImportResult {
    val version: String

    data class Imported(override val version: String, val questionCount: Int) : ImportResult
    data class UpToDate(override val version: String) : ImportResult
}

/**
 * Importe le contenu embarqué dans la base de contenu, seulement si sa version a changé.
 * La base utilisateur n'est jamais concernée : elle ne connaît les questions que par leur identifiant.
 */
class ContentImporter(
    private val readBundleText: () -> String,
    private val store: ContentStore,
) {
    suspend fun importIfNeeded(): ImportResult {
        val bundle = ContentParser.parse(readBundleText())
        if (store.currentVersion() == bundle.version) return ImportResult.UpToDate(bundle.version)
        store.replaceAll(bundle)
        return ImportResult.Imported(bundle.version, bundle.questions.size)
    }
}

/** Les lignes de base de données correspondant à un contenu embarqué. */
data class ContentRows(
    val tags: List<TagEntity>,
    val questions: List<QuestionEntity>,
    val links: List<QuestionTagEntity>,
)

fun ContentBundle.toRows(): ContentRows = ContentRows(
    tags = tags.map { TagEntity(id = it.id, groupName = it.group, labelFr = it.fr, labelEn = it.en, sortOrder = it.order) },
    questions = questions.map { it.toEntity() },
    links = questions.flatMap { q -> q.tags.mapIndexed { position, tag -> QuestionTagEntity(q.id, tag, position) } },
)

private fun QuestionDto.toEntity(): QuestionEntity {
    val fr = explanation.getValue("fr")
    val en = explanation.getValue("en")
    fun notes(e: ExplanationDto) = ContentParser.LETTERS.map { letter -> e.distractors[letter.toString()].orEmpty() }
    return QuestionEntity(
        id = id,
        part = part,
        pool = pool,
        difficulty = difficulty,
        stem = stem,
        options = options,
        answerIndex = answerIndex,
        ruleFr = fr.rule,
        ruleEn = en.rule,
        distractorNotesFr = notes(fr),
        distractorNotesEn = notes(en),
        trapFr = fr.trap,
        trapEn = en.trap,
    )
}

class RoomContentStore(private val dao: ContentDao) : ContentStore {
    override suspend fun currentVersion(): String? = dao.meta(KEY_VERSION)

    override suspend fun replaceAll(bundle: ContentBundle) {
        val rows = bundle.toRows()
        dao.replaceAll(rows.tags, rows.questions, rows.links, listOf(ContentMetaEntity(KEY_VERSION, bundle.version)))
    }

    private companion object {
        const val KEY_VERSION = "version"
    }
}
