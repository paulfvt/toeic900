package fr.toeic900

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.toeic900.data.PickedUnit
import fr.toeic900.data.SessionScope
import fr.toeic900.data.TrainingRepository
import fr.toeic900.data.content.ContentDatabase
import fr.toeic900.data.content.ContentImporter
import fr.toeic900.data.content.ContentParser
import fr.toeic900.data.content.ImportResult
import fr.toeic900.data.content.RoomContentStore
import fr.toeic900.data.user.UserDatabase
import fr.toeic900.domain.Clock
import fr.toeic900.domain.PickReason
import fr.toeic900.domain.StepKind
import fr.toeic900.domain.StepStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests « instrumentés » : ils tournent sur l'émulateur ou le téléphone, avec le vrai SQLite
 * d'Android et le vrai contenu embarqué. Lancer : voir le README, section « Tests ».
 */
@RunWith(AndroidJUnit4::class)
class DatabasesInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val userDbName = "test-user.db"
    private lateinit var contentDb: ContentDatabase
    private lateinit var userDb: UserDatabase

    private class FakeClock(var day: Long = 20_000) : Clock {
        override fun today() = day
        override fun nowMillis() = day * 86_400_000L
    }

    private val clock = FakeClock()

    private fun assetText() = context.assets.open("content/content.json").bufferedReader().use { it.readText() }

    private fun openUserDb() = Room.databaseBuilder(context, UserDatabase::class.java, userDbName).addMigrations(*UserDatabase.MIGRATIONS).build()

    private fun repository() = TrainingRepository(contentDb.contentDao(), userDb.userDao(), clock)

    private suspend fun importContent() =
        ContentImporter(::assetText, RoomContentStore(contentDb.contentDao())).importIfNeeded()

    /** Répond à toutes les questions d'un exercice ; [wrong] = rangs des questions à rater. */
    private suspend fun answerAll(sessionId: Long, unit: PickedUnit, wrong: Set<Int> = emptySet()) {
        unit.questions.forEachIndexed { index, picked ->
            val answer = picked.question.answerIndex
            repository().submitAnswer(sessionId, unit, index, if (index in wrong) (answer + 1) % 4 else answer, timeSpentMs = 1_000)
        }
    }

    @Before
    fun setUp() {
        context.deleteDatabase(userDbName)
        contentDb = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java).build()
        userDb = openUserDb()
    }

    @After
    fun tearDown() {
        contentDb.close()
        userDb.close()
        context.deleteDatabase(userDbName)
    }

    @Test
    fun embeddedContentIsImportedOnce() = runTest {
        val first = importContent()
        assertTrue(first is ImportResult.Imported)
        val dao = contentDb.contentDao()
        assertTrue(dao.questionCount() >= 196)
        assertTrue(dao.stimulusCount() >= 20)
        assertEquals(24, dao.lessons().size)
        assertEquals(16, repository().skills(5).size)
        assertEquals(listOf(5, 6, 7), repository().availableParts())
        assertTrue(importContent() is ImportResult.UpToDate)
    }

    @Test
    fun answersSurviveAnAppRestart() = runTest {
        importContent()
        val scope = SessionScope(part = 5, focusTag = "pronouns")

        val sessionId = repository().startSession(scope, plannedMinutes = 5)
        val first = repository().nextUnit(scope, emptyList())!!
        assertFalse(first.isStimulus)
        val right = repository().submitAnswer(sessionId, first, 0, first.questions[0].question.answerIndex, timeSpentMs = 4_000)
        assertNull(right.boxBefore)
        assertEquals(2, right.boxAfter)

        val second = repository().nextUnit(scope, listOf(first.id))!!
        assertTrue(second.id != first.id)
        val wrongIndex = (second.questions[0].question.answerIndex + 1) % 4
        val wrong = repository().submitAnswer(sessionId, second, 0, wrongIndex, timeSpentMs = 6_000)
        assertEquals(1, wrong.boxAfter)

        // « Redémarrage » : on ferme la base utilisateur et on la rouvre depuis son fichier.
        userDb.close()
        userDb = openUserDb()

        val skill = repository().skills(5).first { it.tagId == "pronouns" }
        assertEquals(1, skill.state!!.box)
        assertEquals(clock.day + 1, skill.state!!.dueDay)

        val failed = userDb.userDao().questionState(second.id)!!
        assertEquals(clock.day + 7, failed.retryDueDay)

        val summary = repository().summary(sessionId)!!
        assertEquals(2, summary.questionCount)
        assertEquals(1, summary.correctCount)
        assertNull(summary.lesson)
    }

    @Test
    fun aNewContentVersionKeepsTheUserProgress() = runTest {
        val store = RoomContentStore(contentDb.contentDao())
        importContent()
        val scope = SessionScope(part = 5)
        val sessionId = repository().startSession(scope, 5)
        val unit = repository().nextUnit(scope, emptyList())!!
        answerAll(sessionId, unit)
        val before = userDb.userDao().skillStates()

        // Une nouvelle version du contenu : mêmes questions, sans la dernière.
        val bundle = ContentParser.parse(assetText())
        val smaller = bundle.copy(version = "nouvelle-version", questions = bundle.questions.dropLast(1))
        val result = ContentImporter({ Json.encodeToString(smaller) }, store).importIfNeeded()

        assertEquals(ImportResult.Imported("nouvelle-version", bundle.questions.size - 1), result)
        assertEquals(bundle.questions.size - 1, contentDb.contentDao().questionCount())
        assertEquals(before, userDb.userDao().skillStates())
        assertNotNull(userDb.userDao().session(sessionId))
    }

    @Test
    fun theFailedQuestionComesBackAfterSevenDays() = runTest {
        importContent()
        val scope = SessionScope(part = 5, focusTag = "prepositions")
        val sessionId = repository().startSession(scope, 5)
        val failed = repository().nextUnit(scope, emptyList())!!
        answerAll(sessionId, failed, wrong = setOf(0))

        clock.day += 6
        assertTrue(repository().nextUnit(scope, emptyList())!!.id != failed.id)

        clock.day += 1
        val retry = repository().nextUnit(scope, emptyList())!!
        assertEquals(failed.id, retry.id)
        assertEquals(PickReason.RETRY, retry.reason)
    }

    @Test
    fun aPart6TextIsServedWholeAndComesBackOnce() = runTest {
        importContent()
        val scope = SessionScope(part = 6)
        val sessionId = repository().startSession(scope, 15)

        val text = repository().nextUnit(scope, emptyList())!!
        assertTrue(text.isStimulus)
        assertEquals(6, text.part)
        assertEquals(1, text.documents.size)
        assertEquals(4, text.questions.size)
        assertEquals(1, text.questions.count { it.tagId == "sentence-insertion" })
        assertTrue(text.questions.all { it.question.stimulusId == text.id })

        // Une seule erreur, sur le deuxième trou : chaque question met à jour sa propre compétence.
        answerAll(sessionId, text, wrong = setOf(1))
        val attempts = userDb.userDao().attempts(sessionId)
        assertEquals(4, attempts.size)
        assertTrue(attempts.all { it.stimulusId == text.id })
        assertEquals(text.questions.map { it.tagId }, attempts.map { it.tagId })
        val states = userDb.userDao().skillStates().associateBy { it.tagId }
        val failedTags = text.questions[1].question.tags.toSet()
        val untouched = text.questions.first { picked -> picked.question.tags.none { it in failedTags } }
        assertEquals(1, states.getValue(text.questions[1].tagId).box)
        assertEquals(2, states.getValue(untouched.tagId).box)

        // Avant 7 jours, rien ne revient au titre du retour de contrôle.
        clock.day += 6
        assertTrue(repository().nextUnit(scope, emptyList())!!.reason != PickReason.RETRY)

        // Au septième jour, le texte entier revient pour contrôle.
        clock.day += 1
        val back = repository().nextUnit(scope, emptyList())!!
        assertEquals(text.id, back.id)
        assertEquals(PickReason.RETRY, back.reason)
        assertEquals(4, back.questions.size)

        // Même raté de nouveau, il a fait son tour : aucune de ses questions n'est reprogrammée.
        answerAll(sessionId, back, wrong = setOf(0, 1))
        for (picked in back.questions) {
            val state = userDb.userDao().questionState(picked.question.id)!!
            assertTrue(picked.question.id, state.retryDone)
            assertNull(picked.question.id, state.retryDueDay)
        }
        clock.day += 30
        assertTrue(repository().nextUnit(scope, emptyList())!!.reason != PickReason.RETRY)
    }

    @Test
    fun aPart7SetCarriesItsDocumentsAndEvidence() = runTest {
        importContent()
        val scope = SessionScope(part = 7, focusTag = "cross-reference")
        val set = repository().nextUnit(scope, emptyList())!!
        assertEquals(7, set.part)
        assertTrue(set.documents.size in 2..3)
        assertEquals(5, set.questions.size)
        for (picked in set.questions) {
            assertTrue(picked.question.stem.isNotBlank())
            assertTrue(picked.question.evidence.all { quote -> set.documents.any { quote in it.text || quote in it.title.orEmpty() } })
        }
        assertTrue(set.questions.any { "cross-reference" in it.question.tags })
        // Toutes les parts mélangées : questions seules et textes dans le même périmètre.
        val mixed = repository().skills(TrainingRepository.ALL_PARTS)
        assertEquals(24, mixed.size)
    }

    @Test
    fun aPassedLessonUnlocksTheNextStep() = runTest {
        importContent()
        val path = repository().path()
        assertEquals(31, path.totalCount)
        val first = path.steps.first()
        assertEquals(StepStatus.AVAILABLE, first.status)
        assertEquals(StepKind.LESSON, first.step.kind)
        assertTrue(path.steps.drop(1).all { it.status == StepStatus.LOCKED })
        assertNotNull(repository().lesson(first.step.tags.single()))

        // La leçon : ses exercices portent tous sur son tag ; on répond juste à tout.
        val scope = repository().scopeOf(first.step)
        val sessionId = repository().startSession(scope, plannedMinutes = 0, stepId = first.step.id)
        val asked = mutableListOf<String>()
        repeat(first.step.exerciseCount) {
            val unit = repository().nextUnit(scope, asked)!!
            assertTrue(unit.questions.all { first.step.tags.single() in it.question.tags })
            answerAll(sessionId, unit)
            asked += unit.id
        }
        repository().finishSession(sessionId, exercisesCompleted = true)

        val after = repository().path()
        assertEquals(listOf(StepStatus.DONE, StepStatus.AVAILABLE, StepStatus.LOCKED), after.steps.take(3).map { it.status })
        assertEquals(after.steps[1].step.id, after.next!!.step.id)
        val lesson = repository().summary(sessionId)!!.lesson!!
        assertTrue(lesson.passed)
        assertEquals(after.steps[1].step.id, lesson.nextStepId)

        // Terminer la session une seconde fois ne compte pas un passage de plus.
        repository().finishSession(sessionId, exercisesCompleted = true)
        assertEquals(1, userDb.userDao().lessonProgress(first.step.id)!!.attempts)
    }

    @Test
    fun aLessonLeftBeforeTheEndIsNotValidated() = runTest {
        importContent()
        val first = repository().path().steps.first()
        val scope = repository().scopeOf(first.step)
        val sessionId = repository().startSession(scope, plannedMinutes = 0, stepId = first.step.id)
        answerAll(sessionId, repository().nextUnit(scope, emptyList())!!)
        repository().finishSession(sessionId, exercisesCompleted = false)

        assertEquals(StepStatus.AVAILABLE, repository().path().steps.first().status)
        val lesson = repository().summary(sessionId)!!.lesson!!
        assertFalse(lesson.passed)
        assertFalse(lesson.finished)
    }
}
