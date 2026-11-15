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
 * Elle doit mener exactement du schéma 1 au schéma 2, sans rien retirer. Le test sur appareil
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

    private val before = schema(1)
    private val after = schema(2)

    @Test
    fun `the migration never removes or rewrites anything`() {
        for (sql in MIGRATION_1_2_SQL) {
            val statement = sql.uppercase()
            assertFalse(sql, listOf("DROP ", "DELETE ", "UPDATE ", "RENAME ", "REPLACE ").any { it in statement })
        }
    }

    @Test
    fun `every table and column of phase 1 is kept as it was`() {
        for ((name, table) in before) {
            val kept = after[name] ?: error("la table $name a disparu")
            val columns = kept.columns.associateBy { it.name }
            for (column in table.columns) {
                assertEquals("$name.${column.name}", column, columns[column.name])
            }
        }
    }

    @Test
    fun `new tables are created with the statement room expects`() {
        val created = after.keys - before.keys
        assertEquals(setOf("lesson_progress"), created)
        for (name in created) {
            assertTrue("CREATE TABLE manquant pour $name", after.getValue(name).createSql in MIGRATION_1_2_SQL)
        }
    }

    @Test
    fun `each new column has its alter table statement and no other is added`() {
        val expected = before.keys.flatMap { name ->
            val known = before.getValue(name).columns.map { it.name }.toSet()
            after.getValue(name).columns.filter { it.name !in known }.map { column ->
                val constraint = if (column.notNull) " NOT NULL DEFAULT ${column.defaultValue}" else ""
                "ALTER TABLE `$name` ADD COLUMN `${column.name}` ${column.affinity}$constraint"
            }
        }
        assertEquals(expected.toSet(), MIGRATION_1_2_SQL.filter { it.startsWith("ALTER TABLE") }.toSet())
        assertEquals(3, expected.size)
    }

    @Test
    fun `a column added to existing rows is nullable or has a default value`() {
        // SQLite refuse d'ajouter une colonne NOT NULL sans valeur par défaut à une table non vide.
        for ((name, table) in before) {
            val known = table.columns.map { it.name }.toSet()
            for (column in after.getValue(name).columns.filter { it.name !in known }) {
                assertTrue("$name.${column.name}", !column.notNull || column.defaultValue != null)
            }
        }
    }

    @Test
    fun `migrations cover every version up to the current one`() {
        val steps = UserDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(listOf(1 to 2), steps)
    }
}
