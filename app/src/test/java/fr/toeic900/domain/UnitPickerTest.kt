package fr.toeic900.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sélection quand l'unité servie est un texte (Parts 6 et 7) et non une question seule. */
class UnitPickerTest {
    private val today = 20_000L
    private val picker = UnitPicker(Random(7))

    /** Un texte : ses questions s'appellent id-1, id-2… et portent chacune un tag principal. */
    private fun text(id: String, vararg tags: String) =
        UnitRef(id, tags.mapIndexed { index, tag -> QuestionRef("$id-${index + 1}", listOf(tag), 2) })

    private fun single(id: String, tag: String) = UnitRef(id, listOf(QuestionRef(id, listOf(tag), 2)))

    private fun skill(tag: String, box: Int, dueIn: Long) = tag to SkillState(tag, box, today + dueIn, today - 1)

    private fun seen(id: String, daysAgo: Long = 1, retryDueIn: Long? = null, retryDone: Boolean = false) =
        id to QuestionState(id, 1, if (retryDueIn == null) 1 else 0, today - daysAgo, retryDueIn == null, retryDueIn?.let { today + it }, retryDone)

    private fun pick(
        units: List<UnitRef>,
        focusTag: String? = null,
        skills: Map<String, SkillState> = emptyMap(),
        questions: Map<String, QuestionState> = emptyMap(),
        asked: List<String> = emptyList(),
        tagOrder: List<String> = emptyList(),
    ) = picker.pick(units, focusTag, skills, questions, asked, today, tagOrder)

    @Test
    fun `a text lists the skills of its questions once each`() {
        val unit = text("p7-0001", "detail", "inference", "detail")
        assertEquals(listOf("detail", "inference"), unit.primaryTags)
        assertTrue(unit.hasTag("inference"))
        assertFalse(unit.hasTag("main-idea"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a unit needs at least one question`() {
        UnitRef("p6-0001", emptyList())
    }

    @Test
    fun `a due skill is served by a text that works on it`() {
        val units = listOf(text("p7-0001", "main-idea", "detail"), text("p7-0002", "inference", "not-true"))
        val result = pick(units, skills = mapOf(skill("main-idea", 2, 3), skill("detail", 2, 5), skill("inference", 1, -2), skill("not-true", 3, 4)))
        assertEquals(UnitPick("p7-0002", "inference", PickReason.DUE_SKILL), result)
    }

    @Test
    fun `a text never seen is preferred to one already read`() {
        val units = listOf(text("p6-0001", "verb-tense", "pronouns"), text("p6-0002", "verb-tense", "prepositions"))
        val result = pick(units, focusTag = "verb-tense", questions = mapOf(seen("p6-0001-2")))
        assertEquals("p6-0002", result!!.unitId)
    }

    @Test
    fun `a text counts as seen as soon as one of its questions was answered`() {
        // p6-0001 a été quitté après une seule question : ce n'est plus un texte neuf.
        val units = listOf(text("p6-0001", "verb-tense", "pronouns"), text("p6-0002", "verb-tense", "prepositions"))
        repeat(20) {
            assertEquals("p6-0002", UnitPicker(Random(it)).pick(units, null, emptyMap(), mapOf(seen("p6-0001-1")), emptyList(), today)!!.unitId)
        }
    }

    @Test
    fun `when every text was read the one read longest ago comes back`() {
        val units = listOf(text("p6-0001", "verb-tense", "pronouns"), text("p6-0002", "verb-tense", "prepositions"))
        val questions = mapOf(seen("p6-0001-1", daysAgo = 9), seen("p6-0001-2", daysAgo = 2), seen("p6-0002-1", daysAgo = 5), seen("p6-0002-2", daysAgo = 5))
        // La dernière lecture de p6-0001 date de 2 jours, celle de p6-0002 de 5 jours.
        assertEquals("p6-0002", pick(units, focusTag = "verb-tense", questions = questions)!!.unitId)
    }

    @Test
    fun `a text with a failed question comes back whole once its retry is due`() {
        val units = listOf(text("p7-0001", "main-idea", "detail"), text("p7-0002", "main-idea", "inference"))
        val questions = mapOf(seen("p7-0001-1"), seen("p7-0001-2", daysAgo = 7, retryDueIn = 0))
        val result = pick(units, skills = mapOf(skill("main-idea", 1, -3)), questions = questions)
        // Le retour de contrôle passe avant la compétence due, et il est servi au titre de la question ratée.
        assertEquals(UnitPick("p7-0001", "detail", PickReason.RETRY), result)
    }

    @Test
    fun `a retry that is not due yet does not bring the text back`() {
        val units = listOf(text("p7-0001", "main-idea", "detail"), text("p7-0002", "main-idea", "inference"))
        val questions = mapOf(seen("p7-0001-1"), seen("p7-0001-2", retryDueIn = 6))
        assertEquals("p7-0002", pick(units, questions = questions)!!.unitId)
    }

    @Test
    fun `the most overdue retry comes first`() {
        val units = listOf(text("p7-0001", "detail", "inference"), text("p7-0002", "detail", "not-true"))
        val questions = mapOf(seen("p7-0001-1", retryDueIn = -1), seen("p7-0002-2", retryDueIn = -4))
        assertEquals(UnitPick("p7-0002", "not-true", PickReason.RETRY), pick(units, questions = questions))
    }

    @Test
    fun `a chosen skill keeps only the texts that work on it`() {
        val units = listOf(text("p7-0001", "main-idea", "detail"), text("p7-0002", "inference", "not-true"), text("p7-0003", "detail", "not-true"))
        val asked = mutableListOf<String>()
        repeat(2) { asked += pick(units, focusTag = "not-true", asked = asked)!!.unitId }
        assertEquals(setOf("p7-0002", "p7-0003"), asked.toSet())
        assertNull(pick(units, focusTag = "not-true", asked = asked))
    }

    @Test
    fun `a text is never served twice in a session`() {
        val units = listOf(text("p6-0001", "verb-tense", "pronouns"), text("p6-0002", "verb-tense", "prepositions"))
        val first = pick(units)!!.unitId
        val second = pick(units, asked = listOf(first))!!.unitId
        assertNotEquals(first, second)
        assertNull(pick(units, asked = listOf(first, second)))
    }

    @Test
    fun `new skills follow the order of the taxonomy`() {
        val units = listOf(text("p7-0001", "inference", "detail"), single("p5-0001", "verb-tense"))
        val result = pick(units, tagOrder = listOf("verb-tense", "detail", "inference"))
        assertEquals(UnitPick("p5-0001", "verb-tense", PickReason.NEW_SKILL), result)
        // Sans ordre de taxonomie, c'est l'ordre des unités qui décide.
        assertEquals("inference", pick(units)!!.tagId)
    }

    @Test
    fun `questions and texts mix in one session`() {
        // verb-tense est due : elle peut être servie par une question Part 5 ou par un texte Part 6.
        val units = listOf(single("p5-0001", "verb-tense"), text("p6-0001", "verb-tense", "sentence-insertion"), single("p5-0002", "pronouns"))
        val skills = mapOf(skill("verb-tense", 2, 0), skill("pronouns", 2, 4), skill("sentence-insertion", 2, 4))
        val served = (0 until 30).map { UnitPicker(Random(it)).pick(units, null, skills, emptyMap(), emptyList(), today)!! }
        assertTrue(served.all { it.tagId == "verb-tense" && it.reason == PickReason.DUE_SKILL })
        assertEquals(setOf("p5-0001", "p6-0001"), served.map { it.unitId }.toSet())
    }

    @Test
    fun `a due skill with a fresh text goes before a more overdue one without any`() {
        // « inference » est la plus en retard, mais son seul texte a déjà été lu ; « detail » a un texte neuf.
        val units = listOf(text("p7-0001", "inference"), text("p7-0002", "detail"))
        val skills = mapOf(skill("inference", 1, -5), skill("detail", 1, -1))
        val result = pick(units, skills = skills, questions = mapOf(seen("p7-0001-1", daysAgo = 5)))
        assertEquals(UnitPick("p7-0002", "detail", PickReason.DUE_SKILL), result)
        // Quand plus rien n'est neuf, la plus en retard reprend la main, sur son texte déjà lu.
        val allRead = mapOf(seen("p7-0001-1", daysAgo = 5), seen("p7-0002-1", daysAgo = 1))
        assertEquals(UnitPick("p7-0001", "inference", PickReason.DUE_SKILL), pick(units, skills = skills, questions = allRead))
    }

    @Test
    fun `practice turns to the skill least worked in the session`() {
        val units = listOf(text("p7-0001", "detail"), text("p7-0002", "detail"), text("p7-0003", "inference"))
        val skills = mapOf(skill("detail", 2, 3), skill("inference", 2, 3))
        val result = pick(units, skills = skills, asked = listOf("p7-0001"))
        assertEquals(UnitPick("p7-0003", "inference", PickReason.PRACTICE), result)
    }
}

/** Le retour unique d'une question ratée, quand elle appartient à un texte. */
class TextReviewTest {
    private val today = 20_000L

    @Test
    fun `a failed question of a text is scheduled to come back in seven days`() {
        val state = QuestionReview.afterAnswer(null, "p7-0001-2", correct = false, today = today, controlReturn = false)
        assertEquals(today + 7, state.retryDueDay)
        assertFalse(state.retryDone)
    }

    @Test
    fun `on the control return every question of the text has had its turn`() {
        // Question réussie la première fois, puis de nouveau servie parce que le texte revient pour contrôle.
        val passed = QuestionReview.afterAnswer(null, "p7-0001-1", correct = true, today = today)
        val back = QuestionReview.afterAnswer(passed, "p7-0001-1", correct = false, today = today + 7, controlReturn = true)
        assertNull(back.retryDueDay)
        assertTrue(back.retryDone)
        assertEquals(2, back.timesSeen)

        // Même ratée au retour, la question n'est plus jamais reprogrammée.
        val later = QuestionReview.afterAnswer(back, "p7-0001-1", correct = false, today = today + 30, controlReturn = false)
        assertNull(later.retryDueDay)
    }

    @Test
    fun `a question read again before its retry is due keeps its appointment`() {
        val failed = QuestionReview.afterAnswer(null, "p6-0001-3", correct = false, today = today, controlReturn = false)
        val early = QuestionReview.afterAnswer(failed, "p6-0001-3", correct = true, today = today + 2, controlReturn = false)
        assertEquals(today + 7, early.retryDueDay)
        assertFalse(early.retryDone)
    }

    @Test
    fun `for a single question the control return is detected from its own state`() {
        val failed = QuestionReview.afterAnswer(null, "p5-0001", correct = false, today = today)
        val back = QuestionReview.afterAnswer(failed, "p5-0001", correct = true, today = today + 7)
        assertTrue(back.retryDone)
        assertNull(back.retryDueDay)
    }
}
