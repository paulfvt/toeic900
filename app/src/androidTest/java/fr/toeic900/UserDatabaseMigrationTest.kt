package fr.toeic900

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.toeic900.data.user.LessonProgressEntity
import fr.toeic900.data.user.BadgeUnlockEntity
import fr.toeic900.data.user.GoalDayEntity
import fr.toeic900.data.user.MIGRATION_1_2
import fr.toeic900.data.user.MIGRATION_2_3
import fr.toeic900.data.user.MockAnswerEntity
import fr.toeic900.data.user.MockResultEntity
import fr.toeic900.data.user.UserDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La mise à jour de user.db de la Phase 1 (version 1) vers la Phase 2 (version 2), avec le vrai
 * SQLite d'Android. La base de départ est recréée depuis le schéma exporté à l'époque
 * (app/schemas/…UserDatabase/1.json) : c'est exactement celle que l'app de la Phase 1 a créée
 * sur le vrai téléphone. Même chose de la Phase 2 (version 2) vers la Phase 3 (version 3).
 */
@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test-user.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UserDatabase::class.java)

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Une progression de la Phase 1 : deux compétences, deux questions, une session de deux réponses. */
    private fun createPhase1Database() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO skill_state (tagId, box, dueDay, lastReviewedDay, lapses) VALUES ('verb-tense', 3, 20004, 20000, 1)")
            db.execSQL("INSERT INTO skill_state (tagId, box, dueDay, lastReviewedDay, lapses) VALUES ('pronouns', 1, 20001, NULL, 0)")
            db.execSQL(
                "INSERT INTO question_state (questionId, timesSeen, timesCorrect, lastSeenDay, lastCorrect, retryDueDay, retryDone) " +
                    "VALUES ('p5-0042', 1, 0, 20000, 0, 20007, 0)",
            )
            db.execSQL(
                "INSERT INTO question_state (questionId, timesSeen, timesCorrect, lastSeenDay, lastCorrect, retryDueDay, retryDone) " +
                    "VALUES ('p5-0007', 2, 2, 20000, 1, NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO session (id, startedAt, endedAt, part, focusTagId, plannedMinutes, questionCount, correctCount) " +
                    "VALUES (1, 1000, 2000, 5, 'verb-tense', 15, 2, 1)",
            )
            db.execSQL(
                "INSERT INTO attempt (id, sessionId, questionId, tagId, selectedIndex, correct, answeredAt, day, timeSpentMs, reason, boxBefore, boxAfter) " +
                    "VALUES (1, 1, 'p5-0042', 'verb-tense', 2, 0, 1500, 20000, 4000, 'NEW_SKILL', NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO attempt (id, sessionId, questionId, tagId, selectedIndex, correct, answeredAt, day, timeSpentMs, reason, boxBefore, boxAfter) " +
                    "VALUES (2, 1, 'p5-0007', 'verb-tense', 1, 1, 1800, 20000, 3000, 'DUE_SKILL', 1, 2)",
            )
        }
    }

    /** La base ouverte comme dans l'app : mêmes migrations que UserDatabase.build(). */
    private fun openLikeTheApp(): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, dbName).addMigrations(*UserDatabase.MIGRATIONS).build()

    @Test
    fun migratedSchemaIsTheOneRoomExpects() {
        createPhase1Database()
        // Lance la migration, puis compare la structure obtenue au schéma de la version 2 :
        // lève une exception au moindre écart (colonne manquante, type différent…).
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).close()
    }

    @Test
    fun phase1ProgressSurvivesTheUpdate() = runTest {
        createPhase1Database()
        val db = openLikeTheApp()
        try {
            val dao = db.userDao()

            val skills = dao.skillStates().associateBy { it.tagId }
            assertEquals(setOf("verb-tense", "pronouns"), skills.keys)
            assertEquals(Triple(3, 20004L, 1), skills.getValue("verb-tense").let { Triple(it.box, it.dueDay, it.lapses) })
            assertNull(skills.getValue("pronouns").lastReviewedDay)

            val questions = dao.questionStates().associateBy { it.questionId }
            assertEquals(20007L, questions.getValue("p5-0042").retryDueDay)
            assertFalse(questions.getValue("p5-0042").retryDone)
            assertTrue(questions.getValue("p5-0007").retryDone)

            val session = dao.session(1)!!
            assertEquals(listOf(5, 15, 2, 1), listOf(session.part, session.plannedMinutes, session.questionCount, session.correctCount))
            assertEquals("verb-tense", session.focusTagId)
            // Les nouvelles colonnes des anciennes lignes : aucune étape, session non « terminée au bout ».
            assertNull(session.stepId)
            assertFalse(session.exercisesCompleted)

            val attempts = dao.attempts(1)
            assertEquals(listOf("p5-0042", "p5-0007"), attempts.map { it.questionId })
            assertEquals(listOf(false, true), attempts.map { it.correct })
            assertEquals(listOf<Int?>(null, 1), attempts.map { it.boxBefore })
            assertTrue(attempts.all { it.stimulusId == null })
        } finally {
            db.close()
        }
    }

    @Test
    fun migratedDatabaseAcceptsPhase2Data() = runTest {
        createPhase1Database()
        val db = openLikeTheApp()
        try {
            val dao = db.userDao()
            assertTrue(dao.lessonProgress().isEmpty())
            dao.upsertLessonProgress(LessonProgressEntity("l-word-form-noun", attempts = 1, bestCorrect = 4, bestTotal = 5, lastAttemptDay = 20010, completedDay = 20010))
            assertEquals(20010L, dao.lessonProgress("l-word-form-noun")!!.completedDay)

            dao.finishSession(1, endedAt = 3000, exercisesCompleted = true)
            assertTrue(dao.session(1)!!.exercisesCompleted)
        } finally {
            db.close()
        }
        // La base rouverte une seconde fois est déjà à jour : plus aucune migration, rien de perdu.
        val reopened = openLikeTheApp()
        try {
            assertEquals(2, reopened.userDao().skillStates().size)
            assertEquals(1, reopened.userDao().lessonProgress().size)
            assertEquals(3, reopened.openHelper.readableDatabase.version)
        } finally {
            reopened.close()
        }
    }

    // ------------------------------------------------------------ Phase 2 -> Phase 3

    /** Une progression de la Phase 2 : une étape réussie, une session d'étape, une réponse Part 7. */
    private fun createPhase2Database() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO skill_state (tagId, box, dueDay, lastReviewedDay, lapses) VALUES ('detail', 2, 20012, 20010, 0)")
            db.execSQL(
                "INSERT INTO session (id, startedAt, endedAt, part, focusTagId, plannedMinutes, questionCount, correctCount, stepId, exercisesCompleted) " +
                    "VALUES (7, 1000, 2000, 7, NULL, 0, 1, 1, 'l-detail', 1)",
            )
            db.execSQL(
                "INSERT INTO attempt (id, sessionId, questionId, tagId, selectedIndex, correct, answeredAt, day, timeSpentMs, reason, boxBefore, boxAfter, stimulusId) " +
                    "VALUES (1, 7, 'p7-0003-1', 'detail', 0, 1, 1500, 20010, 40000, 'NEW_SKILL', NULL, 2, 'p7-0003')",
            )
            db.execSQL(
                "INSERT INTO lesson_progress (stepId, attempts, bestCorrect, bestTotal, lastAttemptDay, completedDay) VALUES ('l-detail', 1, 3, 4, 20010, 20010)",
            )
        }
    }

    @Test
    fun phase3SchemaIsTheOneRoomExpects() {
        createPhase2Database()
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).close()
    }

    @Test
    fun phase2ProgressSurvivesAndAcceptsPhase3Data() = runTest {
        createPhase2Database()
        val db = openLikeTheApp()
        try {
            val dao = db.userDao()
            assertEquals(2, dao.skillStates().single().box)
            assertEquals("l-detail", dao.session(7)!!.stepId)
            assertEquals("p7-0003", dao.attempts(7).single().stimulusId)
            assertEquals(20010L, dao.lessonProgress("l-detail")!!.completedDay)
            assertEquals(1, dao.answerRows().size)

            // Nouvelles tables : vides, puis utilisables.
            assertTrue(dao.completedMocks().isEmpty())
            val mockId = dao.insertMockResult(MockResultEntity(testId = "reading-1", startedAt = 5000, day = 20011, plannedMinutes = 38, questionCount = 2))
            dao.submitMock(
                mockId,
                listOf(
                    MockAnswerEntity(mockId = mockId, questionId = "p5-9001", tagId = "verb-tense", selectedIndex = 0, correct = true, day = 20011, timeSpentMs = 20000),
                    MockAnswerEntity(mockId = mockId, questionId = "p5-9002", tagId = "pronouns", selectedIndex = -1, correct = false, day = 20011, timeSpentMs = 0),
                ),
                endedAt = 9000, elapsedMs = 4000, timedOut = true,
            )
            val mock = dao.completedMocks().single()
            assertEquals(1, mock.correctCount)
            assertTrue(mock.timedOut)
            assertEquals(2, dao.mockAnswerRows().size)

            dao.insertGoalDay(GoalDayEntity(20011))
            dao.insertGoalDay(GoalDayEntity(20011))
            assertEquals(listOf(20011L), dao.goalDays())
            dao.insertBadgeUnlocks(listOf(BadgeUnlockEntity("mock-first", 20011)))
            dao.insertBadgeUnlocks(listOf(BadgeUnlockEntity("mock-first", 20099)))
            assertEquals(20011L, dao.badgeUnlocks().single().unlockedDay)
            dao.markBadgesSeen()
            assertTrue(dao.badgeUnlocks().single().seen)
        } finally {
            db.close()
        }
    }
}
