/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Properties

/**
 * Schema creation and versioned migration of a file-backed SQLite database through the
 * `JdbcSqliteDriver` factory that tracks the schema version in `PRAGMA user_version`.
 *
 * A notes application ships version 1 of its schema, stores a note, and is later upgraded to
 * version 2, which adds a `pinned` column and backfills it from an `AfterVersion` callback.
 */
class SchemaMigrationTest {

    @Test
    fun upgradesAnExistingDatabaseAndKeepsItsRows(@TempDir dir: Path) {
        val url: String = "jdbc:sqlite:${dir.resolve("notes.db")}"

        val v1 = NotesSchema(version = 1)
        JdbcSqliteDriver(url, Properties(), v1).use { driver: JdbcSqliteDriver ->
            assertEquals(listOf("create"), v1.calls)
            assertEquals(1L, userVersion(driver))
            insertNote(driver, 1, "! release checklist")
            insertNote(driver, 2, "groceries")
        }

        val v2 = NotesSchema(version = 2)
        val backfill = AfterVersion(1) { driver: SqlDriver ->
            driver.execute(null, "UPDATE note SET pinned = 1 WHERE title LIKE '!%'", 0).value
        }
        JdbcSqliteDriver(url, Properties(), v2, callbacks = arrayOf(backfill)).use { driver: JdbcSqliteDriver ->
            assertEquals(listOf("migrate 1 -> 2"), v2.calls)
            assertEquals(2L, userVersion(driver))
            assertEquals(
                listOf(Note(1, "! release checklist", true), Note(2, "groceries", false)),
                notes(driver),
            )
        }

        val reopened = NotesSchema(version = 2)
        JdbcSqliteDriver(url, Properties(), reopened).use { driver: JdbcSqliteDriver ->
            assertEquals(emptyList<String>(), reopened.calls)
            assertEquals(2, notes(driver).size)
        }
    }

    @Test
    fun migratesAnEmptyDatabaseFromVersionZeroWhenAsked(@TempDir dir: Path) {
        val url: String = "jdbc:sqlite:${dir.resolve("fresh.db")}"

        val schema = NotesSchema(version = 2)
        JdbcSqliteDriver(url, Properties(), schema, migrateEmptySchema = true).use { driver: JdbcSqliteDriver ->
            assertEquals(listOf("migrate 0 -> 2"), schema.calls)
            assertEquals(2L, userVersion(driver))
            insertNote(driver, 7, "first note")
            assertEquals(listOf(Note(7, "first note", false)), notes(driver))
        }
    }

    @Test
    fun failedMigrationRollsBackToThePreviousVersion(@TempDir dir: Path) {
        val url: String = "jdbc:sqlite:${dir.resolve("broken.db")}"

        JdbcSqliteDriver(url, Properties(), NotesSchema(version = 1)).use { driver: JdbcSqliteDriver ->
            insertNote(driver, 1, "kept")
        }

        val failing = AfterVersion(1) { driver: SqlDriver ->
            driver.execute(null, "UPDATE missing_table SET x = 1", 0).value
        }
        assertThrows(Exception::class.java) {
            JdbcSqliteDriver(url, Properties(), NotesSchema(version = 2), callbacks = arrayOf(failing))
        }

        val v1 = NotesSchema(version = 1)
        JdbcSqliteDriver(url, Properties(), v1).use { driver: JdbcSqliteDriver ->
            assertEquals(emptyList<String>(), v1.calls)
            assertEquals(1L, userVersion(driver))
            assertEquals(listOf("id", "title"), columns(driver))
            assertEquals(listOf("kept"), titles(driver))
        }
    }

    /** One row of the `note` table as the version 2 application reads it. */
    data class Note(val id: Long, val title: String, val pinned: Boolean)

    /** The application's schema, written the way SQLDelight generates one; records each call. */
    class NotesSchema(override val version: Long) : SqlSchema<QueryResult.Value<Unit>> {
        val calls: MutableList<String> = mutableListOf()

        override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
            calls += "create"
            driver.execute(null, "CREATE TABLE note (id INTEGER PRIMARY KEY, title TEXT NOT NULL)", 0)
            if (version >= 2) addPinnedColumn(driver)
            return QueryResult.Unit
        }

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.Value<Unit> {
            calls += "migrate $oldVersion -> $newVersion"
            for (step: Long in oldVersion until newVersion) {
                when (step) {
                    0L -> driver.execute(null, "CREATE TABLE note (id INTEGER PRIMARY KEY, title TEXT NOT NULL)", 0)
                    1L -> addPinnedColumn(driver)
                }
                callbacks.filter { it.afterVersion == step }.forEach { it.block(driver) }
            }
            return QueryResult.Unit
        }

        private fun addPinnedColumn(driver: SqlDriver) {
            driver.execute(null, "ALTER TABLE note ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0", 0)
        }
    }

    private fun insertNote(driver: SqlDriver, id: Long, title: String) {
        driver.execute(null, "INSERT INTO note (id, title) VALUES (?, ?)", 2) {
            bindLong(0, id)
            bindString(1, title)
        }
    }

    private fun notes(driver: SqlDriver): List<Note> =
        driver.executeQuery(null, "SELECT id, title, pinned FROM note ORDER BY id", { cursor ->
            val rows: MutableList<Note> = mutableListOf()
            while (cursor.next().value) {
                rows += Note(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getBoolean(2)!!)
            }
            QueryResult.Value(rows.toList())
        }, 0).value

    private fun titles(driver: SqlDriver): List<String> =
        driver.executeQuery(null, "SELECT title FROM note ORDER BY id", { cursor ->
            val rows: MutableList<String> = mutableListOf()
            while (cursor.next().value) rows += cursor.getString(0)!!
            QueryResult.Value(rows.toList())
        }, 0).value

    private fun columns(driver: SqlDriver): List<String> =
        driver.executeQuery(null, "SELECT name FROM pragma_table_info('note') ORDER BY cid", { cursor ->
            val rows: MutableList<String> = mutableListOf()
            while (cursor.next().value) rows += cursor.getString(0)!!
            QueryResult.Value(rows.toList())
        }, 0).value

    private fun userVersion(driver: SqlDriver): Long =
        driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            cursor.next().value
            QueryResult.Value(cursor.getLong(0)!!)
        }, 0).value
}
