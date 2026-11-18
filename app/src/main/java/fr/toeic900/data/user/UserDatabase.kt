package fr.toeic900.data.user

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.toeic900.domain.QuestionState
import fr.toeic900.domain.SkillState
import fr.toeic900.domain.StepProgress
import kotlinx.coroutines.flow.Flow

/*
 * BASE UTILISATEUR (user.db) : la progression de l'utilisateur. Elle n'est JAMAIS détruite.
 * Elle ne contient aucun texte de question : seulement des identifiants stables (p5-0142,
 * verb-tense, l-verb-tense) qui pointent vers la base de contenu. On peut donc corriger ou
 * ajouter du contenu sans toucher à la progression.
 *
 * Si une table change dans une version future : incrémenter `version` ci-dessous ET écrire
 * une migration (Room compare avec les schémas exportés dans app/schemas). Ne jamais ajouter
 * fallbackToDestructiveMigration ici.
 *
 * Historique :
 *   1 (Phase 1) : skill_state, question_state, session, attempt.
 *   2 (Phase 2) : lesson_progress ; session.stepId et session.exercisesCompleted ; attempt.stimulusId.
 *   3 (Phase 3) : mock_result et mock_answer (demi-tests), badge_unlock, goal_day.
 */

/** Boîte de Leitner d'une compétence. */
@Entity(tableName = "skill_state")
data class SkillStateEntity(
    @PrimaryKey val tagId: String,
    val box: Int,
    val dueDay: Long,
    val lastReviewedDay: Long?,
    val lapses: Int,
) {
    fun toDomain() = SkillState(tagId, box, dueDay, lastReviewedDay, lapses)
}

fun SkillState.toEntity() = SkillStateEntity(tagId, box, dueDay, lastReviewedDay, lapses)

/** Historique résumé d'une question : vue, réussie, à représenter une fois si ratée. */
@Entity(tableName = "question_state")
data class QuestionStateEntity(
    @PrimaryKey val questionId: String,
    val timesSeen: Int,
    val timesCorrect: Int,
    val lastSeenDay: Long,
    val lastCorrect: Boolean,
    val retryDueDay: Long?,
    val retryDone: Boolean,
) {
    fun toDomain() = QuestionState(questionId, timesSeen, timesCorrect, lastSeenDay, lastCorrect, retryDueDay, retryDone)
}

fun QuestionState.toEntity() =
    QuestionStateEntity(questionId, timesSeen, timesCorrect, lastSeenDay, lastCorrect, retryDueDay, retryDone)

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** 5, 6 ou 7 ; 0 = toutes les parts. */
    val part: Int,
    /** Compétence choisie, ou null pour « toutes ». */
    val focusTagId: String?,
    /** Durée choisie en minutes ; 0 = libre. */
    val plannedMinutes: Int,
    val questionCount: Int = 0,
    val correctCount: Int = 0,
    /** Étape du chemin de leçons jouée par cette session ; null pour un entraînement libre. */
    val stepId: String? = null,
    /** Vrai si tous les exercices prévus par l'étape ont été faits (session non abandonnée). */
    @ColumnInfo(defaultValue = "0") val exercisesCompleted: Boolean = false,
)

/** Une réponse donnée. C'est la matière première des futures statistiques (Phase 3). */
@Entity(tableName = "attempt", indices = [Index("sessionId"), Index("questionId"), Index("day")])
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val questionId: String,
    /** Compétence au titre de laquelle la question a été posée. */
    val tagId: String,
    val selectedIndex: Int,
    val correct: Boolean,
    val answeredAt: Long,
    /** Jour de la réponse (epoch day), pour les comptes quotidiens. */
    val day: Long,
    val timeSpentMs: Long,
    /** Pourquoi la question a été posée : RETRY, DUE_SKILL, NEW_SKILL ou PRACTICE. */
    val reason: String,
    /** Boîte de la compétence avant la réponse ; null si elle n'avait jamais été travaillée. */
    val boxBefore: Int?,
    val boxAfter: Int,
    /** Parts 6 et 7 : le texte ou le jeu de documents de la question ; null en Part 5. */
    val stimulusId: String? = null,
)

/** Progression d'une étape du chemin de leçons. */
@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    /** Identifiant stable de l'étape (ex. l-verb-tense). */
    @PrimaryKey val stepId: String,
    val attempts: Int,
    val bestCorrect: Int,
    val bestTotal: Int,
    val lastAttemptDay: Long,
    /** Jour de la première réussite ; null tant que l'étape n'est pas réussie. */
    val completedDay: Long?,
) {
    fun toDomain() = StepProgress(stepId, attempts, bestCorrect, bestTotal, lastAttemptDay, completedDay)
}

fun StepProgress.toEntity() = LessonProgressEntity(stepId, attempts, bestCorrect, bestTotal, lastAttemptDay, completedDay)

/** Un test blanc (Phase 3 : demi-test Reading). Ses réponses ne touchent pas à la révision espacée. */
@Entity(tableName = "mock_result")
data class MockResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identifiant stable du test dans le contenu (ex. reading-1). */
    val testId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Jour du début (epoch day). */
    val day: Long,
    val plannedMinutes: Int,
    /** Temps réellement passé, pauses exclues. */
    val elapsedMs: Long = 0,
    /** Nombre de questions du test (réponses données ou non). */
    val questionCount: Int,
    val correctCount: Int = 0,
    /** Vrai si le chronomètre a expiré avant la fin. */
    val timedOut: Boolean = false,
    /** Vrai si le test a été rendu (fini ou temps écoulé) ; faux s'il a été abandonné. */
    val completed: Boolean = false,
)

/** Une réponse d'un test blanc ; selectedIndex = -1 pour une question laissée sans réponse. */
@Entity(tableName = "mock_answer", indices = [Index("mockId"), Index("day")])
data class MockAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mockId: Long,
    val questionId: String,
    val tagId: String,
    val selectedIndex: Int,
    val correct: Boolean,
    val day: Long,
    val timeSpentMs: Long,
)

/** Un badge débloqué : il le reste, même si sa condition n'est plus remplie. */
@Entity(tableName = "badge_unlock")
data class BadgeUnlockEntity(
    @PrimaryKey val badgeId: String,
    val unlockedDay: Long,
    /** Faux tant que l'utilisateur n'a pas vu l'annonce du badge. */
    val seen: Boolean = false,
)

/** Un jour où l'objectif quotidien a été atteint, enregistré avec l'objectif de ce jour-là. */
@Entity(tableName = "goal_day")
data class GoalDayEntity(@PrimaryKey val day: Long)

/** Une réponse réduite à ce dont les statistiques ont besoin. */
data class AnswerRow(val day: Long, val questionId: String, val tagId: String, val correct: Boolean, val timeSpentMs: Long)

data class AttemptTotals(val total: Int, val correct: Int)

@Dao
abstract class UserDao {
    @Query("SELECT * FROM skill_state")
    abstract suspend fun skillStates(): List<SkillStateEntity>

    @Query("SELECT * FROM skill_state")
    abstract fun observeSkillStates(): Flow<List<SkillStateEntity>>

    @Query("SELECT * FROM question_state")
    abstract suspend fun questionStates(): List<QuestionStateEntity>

    @Query("SELECT * FROM question_state WHERE questionId = :questionId")
    abstract suspend fun questionState(questionId: String): QuestionStateEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    abstract suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM attempt WHERE sessionId = :sessionId ORDER BY id")
    abstract suspend fun attempts(sessionId: Long): List<AttemptEntity>

    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(correct), 0) AS correct FROM attempt")
    abstract fun observeTotals(): Flow<AttemptTotals>

    @Query("SELECT COUNT(*) FROM attempt WHERE day = :day")
    abstract fun observeAnsweredOn(day: Long): Flow<Int>

    @Query("SELECT * FROM lesson_progress")
    abstract suspend fun lessonProgress(): List<LessonProgressEntity>

    @Query("SELECT * FROM lesson_progress")
    abstract fun observeLessonProgress(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress WHERE stepId = :stepId")
    abstract suspend fun lessonProgress(stepId: String): LessonProgressEntity?

    @Upsert
    abstract suspend fun upsertLessonProgress(progress: LessonProgressEntity)

    @Insert
    abstract suspend fun insertSession(session: SessionEntity): Long

    @Query("UPDATE session SET endedAt = :endedAt, exercisesCompleted = :exercisesCompleted WHERE id = :id")
    abstract suspend fun finishSession(id: Long, endedAt: Long, exercisesCompleted: Boolean)

    @Upsert
    protected abstract suspend fun upsertSkillStates(states: List<SkillStateEntity>)

    @Upsert
    protected abstract suspend fun upsertQuestionState(state: QuestionStateEntity)

    @Insert
    protected abstract suspend fun insertAttempt(attempt: AttemptEntity)

    @Query("UPDATE session SET questionCount = questionCount + 1, correctCount = correctCount + :correct WHERE id = :id")
    protected abstract suspend fun countAnswer(id: Long, correct: Int)

    /** Enregistre une réponse et tout ce qu'elle change, en une seule transaction. */
    @Transaction
    open suspend fun recordAnswer(
        attempt: AttemptEntity,
        skillStates: List<SkillStateEntity>,
        questionState: QuestionStateEntity,
    ) {
        insertAttempt(attempt)
        upsertSkillStates(skillStates)
        upsertQuestionState(questionState)
        countAnswer(attempt.sessionId, if (attempt.correct) 1 else 0)
    }

    // ------------------------------------------------------------ Phase 3 : progrès

    @Query("SELECT day, questionId, tagId, correct, timeSpentMs FROM attempt")
    abstract fun observeAnswerRows(): Flow<List<AnswerRow>>

    @Query("SELECT day, questionId, tagId, correct, timeSpentMs FROM mock_answer")
    abstract fun observeMockAnswerRows(): Flow<List<AnswerRow>>

    @Query("SELECT day, questionId, tagId, correct, timeSpentMs FROM attempt")
    abstract suspend fun answerRows(): List<AnswerRow>

    @Query("SELECT day, questionId, tagId, correct, timeSpentMs FROM mock_answer")
    abstract suspend fun mockAnswerRows(): List<AnswerRow>

    @Query("SELECT * FROM mock_result WHERE completed = 1 ORDER BY startedAt")
    abstract fun observeCompletedMocks(): Flow<List<MockResultEntity>>

    @Query("SELECT * FROM mock_result WHERE completed = 1 ORDER BY startedAt")
    abstract suspend fun completedMocks(): List<MockResultEntity>

    @Query("SELECT * FROM mock_result WHERE id = :id")
    abstract suspend fun mockResult(id: Long): MockResultEntity?

    @Query("SELECT * FROM mock_answer WHERE mockId = :mockId ORDER BY id")
    abstract suspend fun mockAnswers(mockId: Long): List<MockAnswerEntity>

    @Insert
    abstract suspend fun insertMockResult(result: MockResultEntity): Long

    @Insert
    protected abstract suspend fun insertMockAnswers(answers: List<MockAnswerEntity>)

    @Query(
        "UPDATE mock_result SET endedAt = :endedAt, elapsedMs = :elapsedMs, correctCount = :correct, " +
            "timedOut = :timedOut, completed = 1 WHERE id = :id",
    )
    protected abstract suspend fun completeMock(id: Long, endedAt: Long, elapsedMs: Long, correct: Int, timedOut: Boolean)

    /** Rend un test blanc : toutes ses réponses et son résultat, en une seule transaction. */
    @Transaction
    open suspend fun submitMock(id: Long, answers: List<MockAnswerEntity>, endedAt: Long, elapsedMs: Long, timedOut: Boolean) {
        insertMockAnswers(answers)
        completeMock(id, endedAt, elapsedMs, answers.count { it.correct }, timedOut)
    }

    @Query("SELECT * FROM badge_unlock")
    abstract fun observeBadgeUnlocks(): Flow<List<BadgeUnlockEntity>>

    @Query("SELECT * FROM badge_unlock")
    abstract suspend fun badgeUnlocks(): List<BadgeUnlockEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBadgeUnlocks(unlocks: List<BadgeUnlockEntity>)

    @Query("UPDATE badge_unlock SET seen = 1 WHERE seen = 0")
    abstract suspend fun markBadgesSeen()

    @Query("SELECT day FROM goal_day")
    abstract fun observeGoalDays(): Flow<List<Long>>

    @Query("SELECT day FROM goal_day")
    abstract suspend fun goalDays(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertGoalDay(day: GoalDayEntity)
}

/**
 * Passage de la base de la Phase 1 à celle de la Phase 2. Rien n'est supprimé ni recalculé :
 * on ajoute une table et trois colonnes. Les lignes existantes gardent toutes leurs valeurs ;
 * leurs nouvelles colonnes valent NULL (ou 0 pour exercisesCompleted).
 */
val MIGRATION_1_2_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `lesson_progress` (`stepId` TEXT NOT NULL, `attempts` INTEGER NOT NULL, " +
        "`bestCorrect` INTEGER NOT NULL, `bestTotal` INTEGER NOT NULL, `lastAttemptDay` INTEGER NOT NULL, " +
        "`completedDay` INTEGER, PRIMARY KEY(`stepId`))",
    "ALTER TABLE `session` ADD COLUMN `stepId` TEXT",
    "ALTER TABLE `session` ADD COLUMN `exercisesCompleted` INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE `attempt` ADD COLUMN `stimulusId` TEXT",
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_SQL.forEach(db::execSQL)
    }
}

/** Passage de la Phase 2 à la Phase 3 : quatre tables nouvelles, rien ne change dans les anciennes. */
val MIGRATION_2_3_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `mock_result` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `testId` TEXT NOT NULL, " +
        "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `day` INTEGER NOT NULL, `plannedMinutes` INTEGER NOT NULL, " +
        "`elapsedMs` INTEGER NOT NULL, `questionCount` INTEGER NOT NULL, `correctCount` INTEGER NOT NULL, " +
        "`timedOut` INTEGER NOT NULL, `completed` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `mock_answer` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mockId` INTEGER NOT NULL, " +
        "`questionId` TEXT NOT NULL, `tagId` TEXT NOT NULL, `selectedIndex` INTEGER NOT NULL, `correct` INTEGER NOT NULL, " +
        "`day` INTEGER NOT NULL, `timeSpentMs` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_mock_answer_mockId` ON `mock_answer` (`mockId`)",
    "CREATE INDEX IF NOT EXISTS `index_mock_answer_day` ON `mock_answer` (`day`)",
    "CREATE TABLE IF NOT EXISTS `badge_unlock` (`badgeId` TEXT NOT NULL, `unlockedDay` INTEGER NOT NULL, " +
        "`seen` INTEGER NOT NULL, PRIMARY KEY(`badgeId`))",
    "CREATE TABLE IF NOT EXISTS `goal_day` (`day` INTEGER NOT NULL, PRIMARY KEY(`day`))",
)

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_2_3_SQL.forEach(db::execSQL)
    }
}

@Database(
    entities = [
        SkillStateEntity::class,
        QuestionStateEntity::class,
        SessionEntity::class,
        AttemptEntity::class,
        LessonProgressEntity::class,
        MockResultEntity::class,
        MockAnswerEntity::class,
        BadgeUnlockEntity::class,
        GoalDayEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        const val FILE_NAME = "user.db"

        /** Toutes les migrations, dans l'ordre. À compléter à chaque changement de version. */
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun build(context: Context): UserDatabase =
            Room.databaseBuilder(context, UserDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
