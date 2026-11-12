package fr.toeic900

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.toeic900.data.TrainingRepository
import fr.toeic900.data.content.ContentDatabase
import fr.toeic900.data.content.ContentImporter
import fr.toeic900.data.content.ContentParser
import fr.toeic900.data.content.ImportResult
import fr.toeic900.data.content.RoomContentStore
import fr.toeic900.data.user.UserDatabase
import fr.toeic900.domain.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests « instrumentés » : ils tournent sur l'émulateur ou le téléphone, avec le vrai SQLite
 * d'Android et le vrai contenu embarqué. Lancer : gradlew connectedDebugAndroidTest
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

    private fun openUserDb() = Room.databaseBuilder(context, UserDatabase::class.java, userDbName).build()

    private fun repository() = TrainingRepository(contentDb.contentDao(), userDb.userDao(), clock)

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
        val importer = ContentImporter(::assetText, RoomContentStore(contentDb.contentDao()))
        val first = importer.importIfNeeded()
        assertTrue(first is ImportResult.Imported)
        assertTrue(contentDb.contentDao().questionCount() >= 120)
        assertEquals(16, contentDb.contentDao().tagsWithCount(5, "training").size)
        assertTrue(importer.importIfNeeded() is ImportResult.UpToDate)
    }

    @Test
    fun answersSurviveAnAppRestart() = runTest {
        ContentImporter(::assetText, RoomContentStore(contentDb.contentDao())).importIfNeeded()

        val sessionId = repository().startSession(part = 5, focusTag = "pronouns", plannedMinutes = 5)
        val first = repository().nextQuestion(5, "pronouns", emptyList())!!
        val right = repository().submitAnswer(sessionId, first, first.question.answerIndex, timeSpentMs = 4_000)
        assertNull(right.boxBefore)
        assertEquals(2, right.boxAfter)

        val second = repository().nextQuestion(5, "pronouns", listOf(first.question.id))!!
        assertTrue(second.question.id != first.question.id)
        val wrongIndex = (second.question.answerIndex + 1) % 4
        val wrong = repository().submitAnswer(sessionId, second, wrongIndex, timeSpentMs = 6_000)
        assertEquals(1, wrong.boxAfter)

        // « Redémarrage » : on ferme la base utilisateur et on la rouvre depuis son fichier.
        userDb.close()
        userDb = openUserDb()

        val skill = repository().skills(5).first { it.tagId == "pronouns" }
        assertEquals(1, skill.state!!.box)
        assertEquals(clock.day + 1, skill.state!!.dueDay)

        val failed = userDb.userDao().questionStates().first { it.questionId == second.question.id }
        assertEquals(clock.day + 7, failed.retryDueDay)

        val summary = repository().summary(sessionId)!!
        assertEquals(2, summary.questionCount)
        assertEquals(1, summary.correctCount)
    }

    @Test
    fun aNewContentVersionKeepsTheUserProgress() = runTest {
        val store = RoomContentStore(contentDb.contentDao())
        ContentImporter(::assetText, store).importIfNeeded()
        val sessionId = repository().startSession(5, null, 5)
        val picked = repository().nextQuestion(5, null, emptyList())!!
        repository().submitAnswer(sessionId, picked, picked.question.answerIndex, 1_000)
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
        ContentImporter(::assetText, RoomContentStore(contentDb.contentDao())).importIfNeeded()
        val sessionId = repository().startSession(5, "prepositions", 5)
        val failed = repository().nextQuestion(5, "prepositions", emptyList())!!
        repository().submitAnswer(sessionId, failed, (failed.question.answerIndex + 1) % 4, 1_000)

        clock.day += 6
        assertTrue(repository().nextQuestion(5, "prepositions", emptyList())!!.question.id != failed.question.id)

        clock.day += 1
        val retry = repository().nextQuestion(5, "prepositions", emptyList())!!
        assertEquals(failed.question.id, retry.question.id)
    }
}
