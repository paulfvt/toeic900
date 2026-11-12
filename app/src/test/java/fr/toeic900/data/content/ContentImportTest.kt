package fr.toeic900.data.content

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun question(id: String = "p5-0001", answer: String = "B", tag: String = "verb-tense"): String {
    val wrong = "ABCD".filter { it.toString() != answer }
    fun explanation(prefix: String) =
        """{"rule":"$prefix rule","distractors":{${wrong.map { """"$it":"$prefix note $it"""" }.joinToString(",")}},"trap":"$prefix trap"}"""
    return """{"id":"$id","part":5,"pool":"training","tags":["$tag"],"difficulty":2,
        "stem":"The manager ------- the report yesterday.","options":["send","sent","sending","sends"],
        "answer":"$answer","explanation":{"fr":${explanation("fr")},"en":${explanation("en")}}}"""
}

private fun bundle(
    version: String = "v1",
    format: Int = 1,
    questions: List<String> = listOf(question()),
): String = """{"format":$format,"version":"$version",
    "tags":[{"id":"verb-tense","parts":[5,6],"group":"grammar","fr":"Temps des verbes","en":"Verb tenses","order":0}],
    "questions":[${questions.joinToString(",")}]}"""

class ContentParserTest {
    private fun assertRejected(text: String, fragment: String) {
        val error = assertThrows(ContentFormatException::class.java) { ContentParser.parse(text) }
        assertTrue("message « ${error.message} » sans « $fragment »", error.message!!.contains(fragment))
    }

    @Test
    fun `a valid bundle is parsed`() {
        val parsed = ContentParser.parse(bundle())
        assertEquals("v1", parsed.version)
        assertEquals(1, parsed.questions.size)
        assertEquals(1, parsed.questions.single().answerIndex)
    }

    @Test
    fun `broken json is rejected`() = assertRejected("{ not json", "illisible")

    @Test
    fun `an unknown format is rejected`() = assertRejected(bundle(format = 2), "format de contenu 2")

    @Test
    fun `an empty bank is rejected`() = assertRejected(bundle(questions = emptyList()), "aucune question")

    @Test
    fun `duplicate question ids are rejected`() =
        assertRejected(bundle(questions = listOf(question(), question())), "question en double")

    @Test
    fun `an answer outside A-D is rejected`() =
        assertRejected(bundle(questions = listOf(question(answer = "E"))), "réponse invalide")

    @Test
    fun `an unknown tag is rejected`() =
        assertRejected(bundle(questions = listOf(question(tag = "time-travel"))), "tag inconnu")

    @Test
    fun `a missing distractor note is rejected`() =
        assertRejected(bundle().replace(""""A":"fr note A",""", ""), "une note par distracteur")

    @Test
    fun `a missing language is rejected`() {
        val withoutEnglish = bundle().replace(Regex(""","en":\{"rule".*?"trap":"en trap"\}"""), "")
        assertRejected(withoutEnglish, "fr et en")
    }

    @Test
    fun `rows keep the answer position and one note per distractor`() {
        val rows = ContentParser.parse(bundle(questions = listOf(question(answer = "C")))).toRows()
        val q = rows.questions.single()
        assertEquals(2, q.answerIndex)
        assertEquals(listOf("fr note A", "fr note B", "", "fr note D"), q.distractorNotesFr)
        assertEquals(listOf("en note A", "en note B", "", "en note D"), q.distractorNotesEn)
        assertEquals(listOf(QuestionTagEntity("p5-0001", "verb-tense", 0)), rows.links)
        assertEquals("Temps des verbes", rows.tags.single().labelFr)
    }
}

/** Le vrai fichier embarqué dans l'app (les tests tournent depuis le dossier app/). */
class EmbeddedContentTest {
    private val bundle = ContentParser.parse(File("src/main/assets/content/content.json").readText())

    @Test
    fun `the embedded content holds the first Part 5 batch`() {
        val part5 = bundle.questions.filter { it.part == 5 && it.pool == "training" }
        assertTrue("seulement ${part5.size} questions Part 5", part5.size >= 120)
    }

    @Test
    fun `every embedded question becomes a complete row`() {
        val rows = bundle.toRows()
        assertEquals(bundle.questions.size, rows.questions.size)
        assertEquals(bundle.questions.sumOf { it.tags.size }, rows.links.size)
        for (q in rows.questions) {
            assertEquals(4, q.options.size)
            assertTrue(q.answerIndex in 0..3)
            for (notes in listOf(q.distractorNotesFr, q.distractorNotesEn)) {
                assertEquals("", notes[q.answerIndex])
                assertEquals(3, notes.count { it.isNotBlank() })
            }
            assertTrue(q.ruleFr.isNotBlank() && q.ruleEn.isNotBlank() && q.trapFr.isNotBlank() && q.trapEn.isNotBlank())
        }
    }

    @Test
    fun `every skill has enough questions to vary the reviews`() {
        val perTag = bundle.questions.groupingBy { it.tags.first() }.eachCount()
        assertTrue(perTag.toString(), perTag.values.all { it >= 5 })
    }
}

class ContentImporterTest {
    private class FakeStore(var version: String? = null) : ContentStore {
        var stored: ContentBundle? = null
        var writes = 0

        override suspend fun currentVersion() = version

        override suspend fun replaceAll(bundle: ContentBundle) {
            stored = bundle
            version = bundle.version
            writes++
        }
    }

    @Test
    fun `the first launch imports the content`() = runTest {
        val store = FakeStore()
        val result = ContentImporter({ bundle("v1") }, store).importIfNeeded()
        assertEquals(ImportResult.Imported("v1", questionCount = 1), result)
        assertEquals("v1", store.version)
    }

    @Test
    fun `the same version is not imported twice`() = runTest {
        val store = FakeStore()
        val importer = ContentImporter({ bundle("v1") }, store)
        importer.importIfNeeded()
        assertEquals(ImportResult.UpToDate("v1"), importer.importIfNeeded())
        assertEquals(1, store.writes)
    }

    @Test
    fun `a new content version replaces the old one`() = runTest {
        val store = FakeStore(version = "v1")
        val result = ContentImporter({ bundle("v2", questions = listOf(question("p5-0001"), question("p5-0002"))) }, store)
            .importIfNeeded()
        assertEquals(ImportResult.Imported("v2", questionCount = 2), result)
        assertEquals(2, store.stored!!.questions.size)
    }

    @Test
    fun `invalid content never reaches the database`() = runTest {
        val store = FakeStore(version = "v1")
        val importer = ContentImporter({ bundle("v2", questions = listOf(question(answer = "Z"))) }, store)
        val failure = runCatching { importer.importIfNeeded() }.exceptionOrNull()
        assertTrue(failure is ContentFormatException)
        assertNull(store.stored)
        assertEquals("v1", store.version)
    }
}
