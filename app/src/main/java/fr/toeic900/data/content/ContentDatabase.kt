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
 * BASE DE CONTENU (content.db) : questions, tags, explications.
 * Elle est jetable : reconstruite depuis content.json à chaque nouvelle version du contenu.
 * Rien de ce que fait l'utilisateur n'y est écrit (voir data/user pour sa progression).
 */

@Entity(tableName = "question")
data class QuestionEntity(
    /** Identifiant stable (ex. p5-0142) : le lien avec la base utilisateur. */
    @PrimaryKey val id: String,
    val part: Int,
    /** training, mock ou diagnostic. */
    val pool: String,
    val difficulty: Int,
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

@Entity(tableName = "content_meta")
data class ContentMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class QuestionTagRow(val questionId: String, val difficulty: Int, val tagId: String, val position: Int)

data class TagWithCount(
    val id: String,
    val groupName: String,
    val labelFr: String,
    val labelEn: String,
    val sortOrder: Int,
    val questionCount: Int,
)

class Converters {
    @TypeConverter
    fun listToJson(list: List<String>): String = Json.encodeToString(list)

    @TypeConverter
    fun jsonToList(text: String): List<String> = Json.decodeFromString(text)
}

@Dao
abstract class ContentDao {
    @Query("SELECT value FROM content_meta WHERE `key` = :key")
    abstract suspend fun meta(key: String): String?

    @Query("SELECT COUNT(*) FROM question")
    abstract suspend fun questionCount(): Int

    @Query("SELECT * FROM question WHERE id = :id")
    abstract suspend fun question(id: String): QuestionEntity?

    @Query("SELECT * FROM tag ORDER BY sortOrder")
    abstract suspend fun tags(): List<TagEntity>

    /** Une ligne par couple (question, tag) du périmètre, dans l'ordre de la taxonomie. */
    @Query(
        """
        SELECT q.id AS questionId, q.difficulty AS difficulty, qt.tagId AS tagId, qt.position AS position
        FROM question q JOIN question_tag qt ON qt.questionId = q.id
        WHERE q.part = :part AND q.pool = :pool
        ORDER BY q.id, qt.position
        """,
    )
    abstract suspend fun questionTagRows(part: Int, pool: String): List<QuestionTagRow>

    /** Les compétences d'une part, avec le nombre de questions dont elles sont la compétence principale. */
    @Query(
        """
        SELECT t.id AS id, t.groupName AS groupName, t.labelFr AS labelFr, t.labelEn AS labelEn,
               t.sortOrder AS sortOrder, COUNT(q.id) AS questionCount
        FROM tag t
        JOIN question_tag qt ON qt.tagId = t.id AND qt.position = 0
        JOIN question q ON q.id = qt.questionId
        WHERE q.part = :part AND q.pool = :pool
        GROUP BY t.id
        ORDER BY t.sortOrder
        """,
    )
    abstract suspend fun tagsWithCount(part: Int, pool: String): List<TagWithCount>

    @Query("DELETE FROM question")
    protected abstract suspend fun deleteQuestions()

    @Query("DELETE FROM tag")
    protected abstract suspend fun deleteTags()

    @Query("DELETE FROM question_tag")
    protected abstract suspend fun deleteQuestionTags()

    @Query("DELETE FROM content_meta")
    protected abstract suspend fun deleteMeta()

    @Insert
    protected abstract suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Insert
    protected abstract suspend fun insertTags(tags: List<TagEntity>)

    @Insert
    protected abstract suspend fun insertQuestionTags(links: List<QuestionTagEntity>)

    @Insert
    protected abstract suspend fun insertMeta(meta: List<ContentMetaEntity>)

    /** Remplace tout le contenu d'un coup : soit l'ancienne version, soit la nouvelle, jamais un mélange. */
    @Transaction
    open suspend fun replaceAll(
        tags: List<TagEntity>,
        questions: List<QuestionEntity>,
        links: List<QuestionTagEntity>,
        meta: List<ContentMetaEntity>,
    ) {
        deleteQuestionTags()
        deleteQuestions()
        deleteTags()
        deleteMeta()
        insertTags(tags)
        insertQuestions(questions)
        insertQuestionTags(links)
        insertMeta(meta)
    }
}

@Database(
    entities = [QuestionEntity::class, TagEntity::class, QuestionTagEntity::class, ContentMetaEntity::class],
    version = 1,
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
