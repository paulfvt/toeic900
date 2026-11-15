package fr.toeic900.data.content

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json

/*
 * BASE DE CONTENU (content.db) : questions, textes, tags, explications, fiches, chemin de leçons.
 * Elle est jetable : reconstruite depuis content.json à chaque nouvelle version du contenu.
 * Rien de ce que fait l'utilisateur n'y est écrit (voir data/user pour sa progression).
 */

@Entity(tableName = "question", indices = [Index("stimulusId")])
data class QuestionEntity(
    /** Identifiant stable (ex. p5-0142, p7-0031-2) : le lien avec la base utilisateur. */
    @PrimaryKey val id: String,
    val part: Int,
    /** training, mock ou diagnostic. */
    val pool: String,
    val difficulty: Int,
    /** Vide en Part 6 : la question est le trou du texte. */
    val stem: String,
    val options: List<String>,
    val answerIndex: Int,
    val ruleFr: String,
    val ruleEn: String,
    /** Une note par option, dans l'ordre A-D ; vide à la position de la bonne réponse. */
    val distractorNotesFr: List<String>,
    val distractorNotesEn: List<String>,
    val trapFr: String,
    val trapEn: String,
    /** Parts 6 et 7 : le texte ou le jeu de documents de la question ; null en Part 5. */
    val stimulusId: String?,
    /** Rang de la question dans son texte (0 pour la première). */
    val position: Int,
    /** Passages des documents qui justifient la réponse. */
    val evidence: List<String>,
)

/** Un « stimulus » : le texte à trous d'une Part 6, ou le jeu de documents d'une Part 7. */
@Entity(tableName = "stimulus")
data class StimulusEntity(
    @PrimaryKey val id: String,
    val part: Int,
    val pool: String,
    val documents: List<DocumentDto>,
)

@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey val id: String,
    val groupName: String,
    val labelFr: String,
    val labelEn: String,
    val sortOrder: Int,
)

/** Lien question ↔ tag. position 0 = compétence principale. */
@Entity(tableName = "question_tag", primaryKeys = ["questionId", "tagId"], indices = [Index("tagId")])
data class QuestionTagEntity(
    val questionId: String,
    val tagId: String,
    val position: Int,
)

/** Une fiche de cours : le contenu des deux langues est gardé tel quel, en JSON. */
@Entity(tableName = "lesson")
data class LessonEntity(
    @PrimaryKey val tagId: String,
    val content: LessonDto,
)

@Entity(tableName = "path_unit")
data class PathUnitEntity(
    @PrimaryKey val id: String,
    val sortOrder: Int,
    val titleFr: String,
    val titleEn: String,
)

@Entity(tableName = "path_step", indices = [Index("unitId")])
data class PathStepEntity(
    @PrimaryKey val id: String,
    val unitId: String,
    /** Rang de l'étape dans tout le chemin, toutes unités confondues. */
    val sortOrder: Int,
    /** "lesson" ou "review". */
    val kind: String,
    val tags: List<String>,
    val part: Int,
    val exerciseCount: Int,
)

@Entity(tableName = "content_meta")
data class ContentMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Une ligne par couple (question, tag). questionPosition = rang de la question dans son texte. */
data class QuestionTagRow(
    val questionId: String,
    val part: Int,
    val stimulusId: String?,
    val questionPosition: Int,
    val difficulty: Int,
    val tagId: String,
    val position: Int,
)

class Converters {
    @TypeConverter
    fun listToJson(list: List<String>): String = Json.encodeToString(list)

    @TypeConverter
    fun jsonToList(text: String): List<String> = Json.decodeFromString(text)

    @TypeConverter
    fun documentsToJson(documents: List<DocumentDto>): String = Json.encodeToString(documents)

    @TypeConverter
    fun jsonToDocuments(text: String): List<DocumentDto> = Json.decodeFromString(text)

    @TypeConverter
    fun lessonToJson(lesson: LessonDto): String = Json.encodeToString(lesson)

    @TypeConverter
    fun jsonToLesson(text: String): LessonDto = Json.decodeFromString(text)
}

@Dao
abstract class ContentDao {
    @Query("SELECT value FROM content_meta WHERE `key` = :key")
    abstract suspend fun meta(key: String): String?

    @Query("SELECT COUNT(*) FROM question")
    abstract suspend fun questionCount(): Int

    @Query("SELECT COUNT(*) FROM stimulus")
    abstract suspend fun stimulusCount(): Int

    @Query("SELECT * FROM question WHERE id = :id")
    abstract suspend fun question(id: String): QuestionEntity?

    @Query("SELECT * FROM stimulus WHERE id = :id")
    abstract suspend fun stimulus(id: String): StimulusEntity?

    /** Les questions d'un texte, dans l'ordre du texte. */
    @Query("SELECT * FROM question WHERE stimulusId = :stimulusId ORDER BY position")
    abstract suspend fun questionsOfStimulus(stimulusId: String): List<QuestionEntity>

    @Query("SELECT * FROM tag ORDER BY sortOrder")
    abstract suspend fun tags(): List<TagEntity>

    /** Une ligne par couple (question, tag) du périmètre. part = 0 : toutes les parts. */
    @Query(
        """
        SELECT q.id AS questionId, q.part AS part, q.stimulusId AS stimulusId, q.position AS questionPosition,
               q.difficulty AS difficulty, qt.tagId AS tagId, qt.position AS position
        FROM question q JOIN question_tag qt ON qt.questionId = q.id
        WHERE (:part = 0 OR q.part = :part) AND q.pool = :pool
        ORDER BY q.id, qt.position
        """,
    )
    abstract suspend fun questionTagRows(part: Int, pool: String): List<QuestionTagRow>

    @Query("SELECT * FROM lesson WHERE tagId = :tagId")
    abstract suspend fun lesson(tagId: String): LessonEntity?

    @Query("SELECT * FROM lesson")
    abstract suspend fun lessons(): List<LessonEntity>

    @Query("SELECT tagId FROM lesson")
    abstract suspend fun lessonTagIds(): List<String>

    @Query("SELECT * FROM path_unit ORDER BY sortOrder")
    abstract suspend fun pathUnits(): List<PathUnitEntity>

    @Query("SELECT * FROM path_step ORDER BY sortOrder")
    abstract suspend fun pathSteps(): List<PathStepEntity>

    @Query("SELECT * FROM path_step WHERE id = :id")
    abstract suspend fun pathStep(id: String): PathStepEntity?

    @Query("DELETE FROM question")
    protected abstract suspend fun deleteQuestions()

    @Query("DELETE FROM stimulus")
    protected abstract suspend fun deleteStimuli()

    @Query("DELETE FROM tag")
    protected abstract suspend fun deleteTags()

    @Query("DELETE FROM question_tag")
    protected abstract suspend fun deleteQuestionTags()

    @Query("DELETE FROM lesson")
    protected abstract suspend fun deleteLessons()

    @Query("DELETE FROM path_unit")
    protected abstract suspend fun deletePathUnits()

    @Query("DELETE FROM path_step")
    protected abstract suspend fun deletePathSteps()

    @Query("DELETE FROM content_meta")
    protected abstract suspend fun deleteMeta()

    @Insert
    protected abstract suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Insert
    protected abstract suspend fun insertStimuli(stimuli: List<StimulusEntity>)

    @Insert
    protected abstract suspend fun insertTags(tags: List<TagEntity>)

    @Insert
    protected abstract suspend fun insertQuestionTags(links: List<QuestionTagEntity>)

    @Insert
    protected abstract suspend fun insertLessons(lessons: List<LessonEntity>)

    @Insert
    protected abstract suspend fun insertPathUnits(units: List<PathUnitEntity>)

    @Insert
    protected abstract suspend fun insertPathSteps(steps: List<PathStepEntity>)

    @Insert
    protected abstract suspend fun insertMeta(meta: List<ContentMetaEntity>)

    /** Remplace tout le contenu d'un coup : soit l'ancienne version, soit la nouvelle, jamais un mélange. */
    @Transaction
    open suspend fun replaceAll(rows: ContentRows, meta: List<ContentMetaEntity>) {
        deleteQuestionTags()
        deleteQuestions()
        deleteStimuli()
        deleteTags()
        deleteLessons()
        deletePathSteps()
        deletePathUnits()
        deleteMeta()
        insertTags(rows.tags)
        insertStimuli(rows.stimuli)
        insertQuestions(rows.questions)
        insertQuestionTags(rows.links)
        insertLessons(rows.lessons)
        insertPathUnits(rows.pathUnits)
        insertPathSteps(rows.pathSteps)
        insertMeta(meta)
    }
}

@Database(
    entities = [
        QuestionEntity::class,
        StimulusEntity::class,
        TagEntity::class,
        QuestionTagEntity::class,
        LessonEntity::class,
        PathUnitEntity::class,
        PathStepEntity::class,
        ContentMetaEntity::class,
    ],
    // 2 : textes et documents (stimulus), fiches (lesson), chemin (path_unit, path_step).
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao

    companion object {
        const val FILE_NAME = "content.db"

        fun build(context: Context): ContentDatabase =
            Room.databaseBuilder(context, ContentDatabase::class.java, FILE_NAME)
                // Jetable : si sa structure change, on la vide et l'import la reconstruit.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
