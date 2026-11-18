package fr.toeic900.data.content

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un petit contenu au format 3 : une question Part 5, un texte Part 6, une fiche, un chemin (le test blanc a ses propres tests). */
private fun explanation(answer: String): Map<String, ExplanationDto> {
    val wrong = "ABCD".map { it.toString() }.filter { it != answer }
    return mapOf(
        "fr" to ExplanationDto("règle", wrong.associateWith { "note $it" }, "piège"),
        "en" to ExplanationDto("rule", wrong.associateWith { "note $it" }, "trap"),
    )
}

private fun question(id: String, part: Int, tag: String, stimulusId: String? = null, position: Int = 0, evidence: List<String> = emptyList(), pool: String = "training") =
    QuestionDto(
        id = id,
        part = part,
        pool = pool,
        tags = listOf(tag),
        difficulty = 2,
        stem = if (part == 6) "" else "The manager ------- the report yesterday.",
        options = listOf("send", "sent", "sending", "sends"),
        answer = "B",
        explanation = explanation("B"),
        stimulusId = stimulusId,
        position = position,
        evidence = evidence,
    )

private val lessonContent = LessonContentDto(
    title = "Temps des verbes",
    summary = "Le marqueur de temps décide.",
    keyPoints = listOf("Repère le marqueur.", "Puis choisis le temps."),
    rules = listOf(LessonRuleDto("Prétérit", "Avec « yesterday ».", listOf(LessonExampleDto("She **left** yesterday.", "Action terminée.")))),
    traps = listOf(LessonTrapDto("Since", "Avec le present perfect.")),
)

private fun sample() = ContentBundle(
    format = 3,
    version = "v2",
    tags = listOf(
        TagDto("verb-tense", listOf(5, 6), "grammar", "Temps des verbes", "Verb tenses", 0),
        TagDto("sentence-insertion", listOf(6, 7), "reading", "Insertion de phrase", "Sentence insertion", 1),
    ),
    stimuli = listOf(StimulusDto("p6-0001", 6, "training", listOf(DocumentDto("email", null, "Dear team, the office [[1]] closed on Friday. [[2]] Thank you.")))),
    questions = listOf(
        question("p5-0001", 5, "verb-tense"),
        question("p6-0001-1", 6, "verb-tense", stimulusId = "p6-0001", position = 0),
        question("p6-0001-2", 6, "sentence-insertion", stimulusId = "p6-0001", position = 1, evidence = listOf("Thank you.")),
    ),
    lessons = listOf(LessonDto("verb-tense", lessonContent, lessonContent.copy(title = "Verb tenses"))),
    path = PathDto(
        listOf(
            PathUnitDto("u1", "Formes verbales", "Verb forms", listOf(
                PathStepDto("l-verb-tense", "lesson", listOf("verb-tense"), 5, 5),
                PathStepDto("r-u1", "review", listOf("verb-tense"), 5, 10),
            )),
            PathUnitDto("u2", "Textes à trous", "Text completion", listOf(
                PathStepDto("r-u2", "review", listOf("sentence-insertion"), 6, 2),
            )),
        ),
    ),
)

class ContentFormatTest {
    private fun parse(bundle: ContentBundle) = ContentParser.parse(Json.encodeToString(bundle))

    private fun assertRejected(bundle: ContentBundle, fragment: String) {
        val error = assertThrows(ContentFormatException::class.java) { parse(bundle) }
        assertTrue("message « ${error.message} » sans « $fragment »", error.message!!.contains(fragment))
    }

    @Test
    fun `a bundle with texts lessons and a path is parsed`() {
        val parsed = parse(sample())
        assertEquals(sample(), parsed)
        assertEquals(listOf("Thank you."), parsed.questions.last().evidence)
    }

    @Test
    fun `a content without texts lessons or path is still valid`() {
        val minimal = """{"format":3,"version":"v","tags":[{"id":"verb-tense","parts":[5],"group":"grammar","fr":"T","en":"T","order":0}],
            "questions":[${Json.encodeToString(question("p5-0001", 5, "verb-tense"))}]}"""
        val parsed = ContentParser.parse(minimal)
        assertTrue(parsed.stimuli.isEmpty() && parsed.lessons.isEmpty() && parsed.path.units.isEmpty())
    }

    @Test
    fun `a question must point to a known text`() {
        val bundle = sample().let { it.copy(questions = it.questions + question("p6-0002-1", 6, "verb-tense", stimulusId = "p6-0002")) }
        assertRejected(bundle, "texte inconnu")
    }

    @Test
    fun `a text without question is rejected`() =
        assertRejected(sample().let { it.copy(questions = it.questions.take(1)) }, "aucune question")

    @Test
    fun `a text with an empty document is rejected`() {
        val empty = sample().let { it.copy(stimuli = listOf(it.stimuli.single().copy(documents = emptyList()))) }
        assertRejected(empty, "document vide")
    }

    @Test
    fun `a question outside a text needs a stem`() {
        val bundle = sample().let { it.copy(questions = listOf(it.questions.first().copy(stem = "")) + it.questions.drop(1)) }
        assertRejected(bundle, "énoncé vide")
    }

    @Test
    fun `a question and its text belong to the same part`() {
        val bundle = sample().let { it.copy(questions = it.questions.dropLast(1) + it.questions.last().copy(part = 7)) }
        assertRejected(bundle, "part différente")
    }

    @Test
    fun `two lessons for one skill are rejected`() =
        assertRejected(sample().let { it.copy(lessons = it.lessons + it.lessons) }, "fiche en double")

    @Test
    fun `a lesson step needs its lesson`() =
        assertRejected(sample().copy(lessons = emptyList()), "pas de fiche")

    @Test
    fun `step ids are unique across the path`() {
        val path = sample().path.let { p -> p.copy(units = p.units.dropLast(1) + p.units.last().copy(steps = listOf(PathStepDto("r-u1", "review", listOf("verb-tense"), 5, 3)))) }
        assertRejected(sample().copy(path = path), "identifiant en double")
    }

    @Test
    fun `an unknown step kind is rejected`() {
        val path = PathDto(listOf(PathUnitDto("u1", "U", "U", listOf(PathStepDto("q1", "quiz", listOf("verb-tense"), 5, 3)))))
        assertRejected(sample().copy(path = path), "type inconnu")
    }

    @Test
    fun `rows keep each question inside its text`() {
        val rows = sample().toRows()
        assertEquals(listOf("p6-0001"), rows.stimuli.map { it.id })
        assertEquals("email", rows.stimuli.single().documents.single().genre)
        val byId = rows.questions.associateBy { it.id }
        assertNull(byId.getValue("p5-0001").stimulusId)
        assertEquals("p6-0001" to 1, byId.getValue("p6-0001-2").let { it.stimulusId to it.position })
        assertEquals(listOf("Thank you."), byId.getValue("p6-0001-2").evidence)
        assertEquals("", byId.getValue("p6-0001-1").stem)
    }

    @Test
    fun `rows number the steps along the whole path`() {
        val rows = sample().toRows()
        assertEquals(listOf("u1" to 0, "u2" to 1), rows.pathUnits.map { it.id to it.sortOrder })
        // L'ordre de déblocage traverse les unités : 0, 1, puis 2 dans l'unité suivante.
        assertEquals(listOf("l-verb-tense" to 0, "r-u1" to 1, "r-u2" to 2), rows.pathSteps.map { it.id to it.sortOrder })
        assertEquals(listOf("u1", "u1", "u2"), rows.pathSteps.map { it.unitId })
        assertEquals(5 to 10, rows.pathSteps[1].let { it.part to it.exerciseCount })
        assertEquals("Verb tenses", rows.lessons.single().content.en.title)
    }
}

/** Le vrai fichier embarqué : Parts 6 et 7, fiches et chemin (les tests tournent depuis app/). */
class EmbeddedReadingContentTest {
    private val bundle = ContentParser.parse(File("src/main/assets/content/content.json").readText())
    private val training = bundle.questions.filter { it.pool == "training" }

    @Test
    fun `the embedded content holds texts for parts 6 and 7`() {
        assertTrue(bundle.stimuli.count { it.part == 6 } >= 8)
        assertTrue(bundle.stimuli.count { it.part == 7 } >= 12)
    }

    @Test
    fun `every part 6 text has four blanks and four questions`() {
        for (text in bundle.stimuli.filter { it.part == 6 }) {
            val document = text.documents.single().text
            assertEquals(text.id, listOf(1, 2, 3, 4), Regex("""\[\[([1-4])]]""").findAll(document).map { it.groupValues[1].toInt() }.toList())
            val questions = bundle.questions.filter { it.stimulusId == text.id }.sortedBy { it.position }
            assertEquals(text.id, listOf(0, 1, 2, 3), questions.map { it.position })
            assertEquals(text.id, 1, questions.count { it.tags.first() == "sentence-insertion" })
        }
    }

    @Test
    fun `every part 7 question quotes its evidence from the documents`() {
        val texts = bundle.stimuli.associateBy { it.id }
        for (q in bundle.questions.filter { it.part == 7 }) {
            val documents = texts.getValue(q.stimulusId!!).documents
            assertTrue(q.id, q.evidence.isNotEmpty() && q.stem.isNotBlank())
            for (quote in q.evidence) {
                assertTrue("${q.id} : « $quote »", documents.any { quote in it.text || quote in it.title.orEmpty() })
            }
        }
    }

    @Test
    fun `every skill has a lesson in both languages`() {
        assertEquals(bundle.tags.map { it.id }, bundle.lessons.map { it.tag })
        for (lesson in bundle.lessons) {
            assertEquals(lesson.tag, lesson.fr.rules.map { r -> r.examples.map { it.en } }, lesson.en.rules.map { r -> r.examples.map { it.en } })
            assertEquals(lesson.tag, lesson.fr.keyPoints.size to lesson.fr.traps.size, lesson.en.keyPoints.size to lesson.en.traps.size)
        }
    }

    @Test
    fun `every step of the path has enough exercises`() {
        // Un exercice = une question (Part 5) ou un texte entier (Parts 6 et 7).
        data class Exercise(val part: Int, val tags: Set<String>, val primaryTags: Set<String>)
        val exercises = training.groupBy { it.stimulusId ?: it.id }.values.map { questions ->
            Exercise(questions.first().part, questions.flatMap { it.tags }.toSet(), questions.map { it.tags.first() }.toSet())
        }
        val steps = bundle.path.units.flatMap { it.steps }
        assertTrue(steps.isNotEmpty())
        for (step in steps) {
            val available = exercises.count { e ->
                e.part == step.part && if (step.kind == "lesson") step.tags.first() in e.tags else e.primaryTags.any { it in step.tags }
            }
            assertTrue("${step.id} : $available exercice(s) pour ${step.units} demandés", available >= step.units)
        }
    }

    @Test
    fun `the path starts with the grammar of parts 5 and 6`() {
        val first = bundle.path.units.first().steps.first()
        assertEquals("lesson" to 5, first.kind to first.part)
        assertEquals("grammar", bundle.tags.first { it.id == first.tags.single() }.group)
    }

    // ------------------------------------------------------------ tests blancs (format 3)

    private fun withMock(units: List<String>, extra: List<QuestionDto> = listOf(question("p5-9001", 5, "verb-tense", pool = "mock"))) =
        sample().let { it.copy(questions = it.questions + extra, mockTests = listOf(MockTestDto("reading-1", "reading", "Demi-test", "Half-test", 38, units))) }

    @Test
    fun `a mock test made of mock questions is parsed and stored`() {
        val bundle = withMock(listOf("p5-9001"))
        assertEquals(bundle, ContentParser.parse(Json.encodeToString(bundle)))
        val row = bundle.toRows().mockTests.single()
        assertEquals(listOf("p5-9001"), row.units)
        assertEquals(38, row.minutes)
    }

    @Test
    fun `a mock test cannot use training questions or unknown units`() {
        for ((units, fragment) in listOf(listOf("p5-0001") to "pas une question de test blanc", listOf("p5-9999") to "unité inconnue")) {
            val error = assertThrows(ContentFormatException::class.java) { ContentParser.parse(Json.encodeToString(withMock(units))) }
            assertTrue(error.message, error.message!!.contains(fragment))
        }
    }
}
