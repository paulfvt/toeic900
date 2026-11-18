package fr.toeic900.data.user

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La migration de user.db comparée aux schémas exportés par Room (app/schemas), sur le PC.
 * Chaque étape doit mener exactement d'un schéma au suivant, sans rien retirer. Le test sur appareil
 * (UserDatabaseMigrationTest) vérifie ensuite la même chose avec le vrai SQLite et de vraies données.
 */
class UserMigrationSqlTest {
    private data class Column(val name: String, val affinity: String, val notNull: Boolean, val defaultValue: String?)

    private class Table(val name: String, val createSql: String, val columns: List<Column>)

    private fun schema(version: Int): Map<String, Table> {
        val root = Json.parseToJsonElement(File("schemas/fr.toeic900.data.user.UserDatabase/$version.json").readText()).jsonObject
        return root.getValue("database").jsonObject.getValue("entities").jsonArray.map { it.jsonObject }.associate { entity ->
            val name = entity.text("tableName")
            name to Table(
                name = name,
                createSql = entity.text("createSql").replace("\${TABLE_NAME}", name),
                columns = entity.getValue("fields").jsonArray.map { it.jsonObject }.map { field ->
                    Column(
                        name = field.text("columnName"),
                        affinity = field.text("affinity"),
                        notNull = field["notNull"]?.jsonPrimitive?.boolean ?: false,
                        defaultValue = field["defaultValue"]?.jsonPrimitive?.content,
                    )
                },
            )
        }
    }

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    /** Une étape de migration et ce qu'elle doit créer. */
    private class Step(val from: Int, val to: Int, val sql: List<String>, val newTables: Set<String>, val newColumns: Int) {
        val label = "$from -> $to"
    }

    private val steps = listOf(
        Step(1, 2, MIGRATION_1_2_SQL, setOf("lesson_progress"), newColumns = 3),
        Step(2, 3, MIGRATION_2_3_SQL, setOf("mock_result", "mock_answer", "badge_unlock", "goal_day"), newColumns = 0),
    )

    @Test
    fun `the migrations never remove or rewrite anything`() {
        for (step in steps) {
            for (sql in step.sql) {
                val statement = sql.uppercase()
                assertFalse("${step.label} : $sql", listOf("DROP ", "DELETE ", "UPDATE ", "RENAME ", "REPLACE ").any { it in statement })
            }
        }
    }

    @Test
    fun `every table and column of the previous version is kept as it was`() {
        for (step in steps) {
            val before = schema(step.from)
            val after = schema(step.to)
            for ((name, table) in before) {
                val kept = after[name] ?: error("${step.label} : la table $name a disparu")
                val columns = kept.columns.associateBy { it.name }
                for (column in table.columns) {
                    assertEquals("${step.label} : $name.${column.name}", column, columns[column.name])
                }
            }
        }
    }

    @Test
    fun `new tables are created with the statement room expects`() {
        for (step in steps) {
            val before = schema(step.from)
            val after = schema(step.to)
            val created = after.keys - before.keys
            assertEquals(step.label, step.newTables, created)
            for (name in created) {
                assertTrue("${step.label} : CREATE TABLE manquant pour $name", after.getValue(name).createSql in step.sql)
            }
        }
    }

    @Test
    fun `each new column has its alter table statement and no other is added`() {
        for (step in steps) {
            val before = schema(step.from)
            val after = schema(step.to)
            val expected = before.keys.flatMap { name ->
                val known = before.getValue(name).columns.map { it.name }.toSet()
                after.getValue(name).columns.filter { it.name !in known }.map { column ->
                    val constraint = if (column.notNull) " NOT NULL DEFAULT ${column.defaultValue}" else ""
                    "ALTER TABLE `$name` ADD COLUMN `${column.name}` ${column.affinity}$constraint"
                }
            }
            assertEquals(step.label, expected.toSet(), step.sql.filter { it.startsWith("ALTER TABLE") }.toSet())
            assertEquals(step.label, step.newColumns, expected.size)
        }
    }

    @Test
    fun `a column added to existing rows is nullable or has a default value`() {
        // SQLite refuse d'ajouter une colonne NOT NULL sans valeur par défaut à une table non vide.
        for (step in steps) {
            val before = schema(step.from)
            val after = schema(step.to)
            for ((name, table) in before) {
                val known = table.columns.map { it.name }.toSet()
                for (column in after.getValue(name).columns.filter { it.name !in known }) {
                    assertTrue("${step.label} : $name.${column.name}", !column.notNull || column.defaultValue != null)
                }
            }
        }
    }

    @Test
    fun `migrations cover every version up to the current one`() {
        val pairs = UserDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(steps.map { it.from to it.to }, pairs)
    }
}
