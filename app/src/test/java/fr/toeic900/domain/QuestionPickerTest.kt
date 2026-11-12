package fr.toeic900.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionPickerTest {
    private val today = 20_000L
    private val picker = QuestionPicker(Random(42))

    // Trois compétences, quatre questions chacune.
    private val bank: List<QuestionRef> = listOf("verbs", "pronouns", "preps").flatMapIndexed { t, tag ->
        (1..4).map { n -> QuestionRef("p5-%04d".format(t * 10 + n), listOf(tag), difficulty = 2) }
    }

    private fun ids(tag: String) = bank.filter { it.primaryTag == tag }.map { it.id }

    private fun seen(id: String, day: Long = today - 1, correct: Boolean = true) =
        QuestionState(id, timesSeen = 1, timesCorrect = if (correct) 1 else 0, lastSeenDay = day, lastCorrect = correct)

    private fun pick(
        skills: Map<String, SkillState> = emptyMap(),
        questions: Map<String, QuestionState> = emptyMap(),
        asked: List<String> = emptyList(),
        focus: String? = null,
    ) = picker.pick(bank, focus, skills, questions, asked, today)

    @Test
    fun `nothing left to ask ends the session`() {
        assertNull(pick(asked = bank.map { it.id }))
        assertNull(pick(asked = ids("pronouns"), focus = "pronouns"))
    }

    @Test
    fun `a first session starts with the first skill of the taxonomy`() {
        val pick = pick()!!
        assertEquals("verbs", pick.tagId)
        assertEquals(PickReason.NEW_SKILL, pick.reason)
    }

    @Test
    fun `a due skill comes before a new one, the most overdue first`() {
        val skills = mapOf(
            "pronouns" to SkillState("pronouns", box = 2, dueDay = today - 1),
            "preps" to SkillState("preps", box = 3, dueDay = today - 5),
        )
        val pick = pick(skills)!!
        assertEquals("preps", pick.tagId)
        assertEquals(PickReason.DUE_SKILL, pick.reason)
    }

    @Test
    fun `a due skill brings a question never seen on that skill`() {
        val skills = mapOf("pronouns" to SkillState("pronouns", box = 2, dueDay = today))
        val (first, second, third, unseen) = ids("pronouns")
        val questions = listOf(first, second, third).associateWith { seen(it) }
        repeat(20) {
            assertEquals(unseen, pick(skills, questions)!!.questionId)
        }
    }

    @Test
    fun `when every question of the skill was seen, the oldest one returns`() {
        val skills = mapOf("pronouns" to SkillState("pronouns", box = 2, dueDay = today))
        val ids = ids("pronouns")
        val questions = ids.mapIndexed { i, id -> id to seen(id, day = today - 10 + i) }.toMap()
        assertEquals(ids.first(), pick(skills, questions)!!.questionId)
    }

    @Test
    fun `a failed question returns once its seven days have passed, before anything else`() {
        val failedId = ids("preps")[2]
        val skills = mapOf("pronouns" to SkillState("pronouns", box = 2, dueDay = today - 3))
        val notYet = mapOf(failedId to seen(failedId, correct = false).copy(retryDueDay = today + 1))
        assertEquals(PickReason.DUE_SKILL, pick(skills, notYet)!!.reason)

        val due = mapOf(failedId to seen(failedId, correct = false).copy(retryDueDay = today))
        val pick = pick(skills, due)!!
        assertEquals(failedId, pick.questionId)
        assertEquals(PickReason.RETRY, pick.reason)
    }

    @Test
    fun `a question is never asked twice in a session`() {
        val failedId = ids("verbs")[0]
        val questions = mapOf(failedId to seen(failedId, correct = false).copy(retryDueDay = today))
        val pick = pick(questions = questions, asked = listOf(failedId))!!
        assertTrue(pick.questionId != failedId)
    }

    @Test
    fun `a chosen skill limits the session to its questions`() {
        val asked = mutableListOf<String>()
        repeat(4) {
            val pick = pick(asked = asked, focus = "preps")!!
            assertTrue(pick.questionId in ids("preps"))
            assertEquals("preps", pick.tagId)
            asked += pick.questionId
        }
        assertEquals(ids("preps").toSet(), asked.toSet())
    }

    @Test
    fun `the reason reflects the state of the chosen skill`() {
        assertEquals(PickReason.NEW_SKILL, pick(focus = "preps")!!.reason)
        val due = mapOf("preps" to SkillState("preps", box = 2, dueDay = today))
        assertEquals(PickReason.DUE_SKILL, pick(due, focus = "preps")!!.reason)
        val later = mapOf("preps" to SkillState("preps", box = 2, dueDay = today + 2))
        assertEquals(PickReason.PRACTICE, pick(later, focus = "preps")!!.reason)
    }

    @Test
    fun `free practice goes to the skill least worked in the session, weakest box first`() {
        val skills = mapOf(
            "verbs" to SkillState("verbs", box = 4, dueDay = today + 8),
            "pronouns" to SkillState("pronouns", box = 2, dueDay = today + 2),
            "preps" to SkillState("preps", box = 3, dueDay = today + 4),
        )
        val first = pick(skills)!!
        assertEquals(PickReason.PRACTICE, first.reason)
        assertEquals("pronouns", first.tagId)

        val second = pick(skills, asked = listOf(first.questionId))!!
        assertEquals("preps", second.tagId)
    }

    @Test
    fun `a whole session visits every skill before coming back to one`() {
        // On joue une session en répondant juste à tout, comme le fera l'application.
        val skills = mutableMapOf<String, SkillState>()
        val questions = mutableMapOf<String, QuestionState>()
        val asked = mutableListOf<String>()
        val tagsInOrder = mutableListOf<String>()
        while (true) {
            val pick = picker.pick(bank, null, skills, questions, asked, today) ?: break
            asked += pick.questionId
            tagsInOrder += pick.tagId
            skills[pick.tagId] = Leitner.review(skills[pick.tagId] ?: Leitner.newSkill(pick.tagId, today), true, today)
            questions[pick.questionId] = QuestionReview.afterAnswer(questions[pick.questionId], pick.questionId, true, today)
        }
        assertEquals(bank.size, asked.toSet().size)
        tagsInOrder.chunked(3).forEach { round -> assertEquals(3, round.toSet().size) }
        assertNotNull(skills["preps"])
        // Une seule promotion par jour, même avec quatre bonnes réponses sur la compétence.
        assertTrue(skills.values.all { it.box == 2 })
    }
}
