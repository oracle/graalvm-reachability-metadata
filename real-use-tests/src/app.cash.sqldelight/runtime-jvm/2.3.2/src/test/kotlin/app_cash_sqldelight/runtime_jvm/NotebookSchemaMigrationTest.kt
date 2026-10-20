/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Properties

/**
 * Schema versioning and migration through `SqlSchema` and `AfterVersion`: a notebook app ships
 * three schema versions, and opening its database file creates the newest schema on first launch,
 * upgrades an older file step by step while running data-fix callbacks between versions, and
 * leaves an up-to-date file alone.
 *
 * `NotebookSchema` is written the way the SQLDelight compiler emits a database schema for a
 * project with `1.sqm` and `2.sqm` migration files, so upgrades run through the same runtime
 * contract a generated database uses.
 */
class NotebookSchemaMigrationTest {

    @TempDir
    lateinit var dataDir: Path

    @Test
    fun firstLaunchCreatesNewestSchemaWithoutMigrating() {
        val schema = NotebookSchema(version = 3)
        val backfill = BodyBackfill()

        openNotebook(schema, backfill.callback).use { driver ->
            addNote(driver, "groceries", body = "milk, eggs")
            tagNote(driver, "groceries", "home")

            assertThat(userVersion(driver)).isEqualTo(3)
            assertThat(notes(driver)).containsExactly(Note("groceries", "milk, eggs", listOf("home")))
        }
        assertThat(schema.createCount).isEqualTo(1)
        assertThat(schema.appliedMigrations).isEmpty()
        assertThat(backfill.runs).isZero()
    }

    @Test
    fun outdatedFileIsUpgradedStepByStepWithCallbackBetweenVersions() {
        openNotebook(NotebookSchema(version = 1)).use { driver ->
            driver.execute(null, "INSERT INTO note (title) VALUES ('groceries')", 0)
            driver.execute(null, "INSERT INTO note (title) VALUES ('ideas')", 0)
            assertThat(userVersion(driver)).isEqualTo(1)
        }

        val schema = NotebookSchema(version = 3)
        val backfill = BodyBackfill()
        openNotebook(schema, backfill.callback).use { driver ->
            assertThat(userVersion(driver)).isEqualTo(3)
            assertThat(notes(driver)).containsExactly(
                Note("groceries", "(empty)", emptyList()),
                Note("ideas", "(empty)", emptyList()),
            )
            tagNote(driver, "ideas", "work")
            assertThat(notes(driver).last().tags).containsExactly("work")
        }
        assertThat(schema.createCount).isZero()
        assertThat(schema.appliedMigrations).containsExactly(1L, 2L)
        assertThat(backfill.runs).isEqualTo(1)
        assertThat(backfill.notesFixed).isEqualTo(2)
        assertThat(backfill.tagTableExistedWhenRun).isFalse()
    }

    @Test
    fun callbacksOutsideTheUpgradeRangeAreSkipped() {
        openNotebook(NotebookSchema(version = 2)).use { driver ->
            addNote(driver, "groceries", body = "milk")
        }

        val schema = NotebookSchema(version = 3)
        val backfill = BodyBackfill()
        openNotebook(schema, backfill.callback).use { driver ->
            assertThat(userVersion(driver)).isEqualTo(3)
            assertThat(notes(driver)).containsExactly(Note("groceries", "milk", emptyList()))
        }
        assertThat(schema.appliedMigrations).containsExactly(2L)
        assertThat(backfill.runs).isZero()
    }

    @Test
    fun upToDateFileIsOpenedWithoutTouchingTheSchema() {
        openNotebook(NotebookSchema(version = 3)).use { driver ->
            addNote(driver, "groceries", body = "milk")
        }

        val schema = NotebookSchema(version = 3)
        val backfill = BodyBackfill()
        openNotebook(schema, backfill.callback).use { driver ->
            assertThat(notes(driver)).containsExactly(Note("groceries", "milk", emptyList()))
        }
        assertThat(schema.createCount).isZero()
        assertThat(schema.appliedMigrations).isEmpty()
        assertThat(backfill.runs).isZero()
    }

    private fun openNotebook(schema: NotebookSchema, vararg callbacks: AfterVersion): JdbcSqliteDriver =
        JdbcSqliteDriver(
            url = "jdbc:sqlite:${dataDir.resolve("notebook.db")}",
            properties = Properties(),
            schema = schema,
            callbacks = callbacks,
        )

    private fun addNote(driver: SqlDriver, title: String, body: String) {
        driver.execute(null, "INSERT INTO note (title, body) VALUES (?, ?)", 2) {
            bindString(0, title)
            bindString(1, body)
        }
    }

    private fun tagNote(driver: SqlDriver, title: String, tag: String) {
        driver.execute(
            null,
            "INSERT INTO tag (note_id, name) SELECT id, ? FROM note WHERE title = ?",
            2,
        ) {
            bindString(0, tag)
            bindString(1, title)
        }
    }

    private fun notes(driver: SqlDriver): List<Note> {
        val rows: List<Pair<String, String>> = driver.executeQuery(
            null,
            "SELECT title, body FROM note ORDER BY id",
            { cursor: SqlCursor ->
                val rows = mutableListOf<Pair<String, String>>()
                while (cursor.next().value) {
                    rows += cursor.getString(0)!! to cursor.getString(1)!!
                }
                QueryResult.Value(rows)
            },
            0,
        ).value
        return rows.map { (title, body) -> Note(title, body, tags(driver, title)) }
    }

    private fun tags(driver: SqlDriver, title: String): List<String> = driver.executeQuery(
        null,
        "SELECT t.name FROM tag t JOIN note n ON n.id = t.note_id WHERE n.title = ? ORDER BY t.name",
        { cursor: SqlCursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) {
                names += cursor.getString(0)!!
            }
            QueryResult.Value(names)
        },
        1,
    ) {
        bindString(0, title)
    }.value

    private fun userVersion(driver: SqlDriver): Long = driver.executeQuery(
        null,
        "PRAGMA user_version",
        { cursor: SqlCursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0)!!)
        },
        0,
    ).value

    /** One note as the notebook screen shows it. */
    data class Note(val title: String, val body: String, val tags: List<String>)

    /** Data fix the app registers to run right after the database reaches version 2. */
    class BodyBackfill {
        var runs: Int = 0
            private set
        var notesFixed: Long = 0
            private set
        var tagTableExistedWhenRun: Boolean = true
            private set

        val callback: AfterVersion = AfterVersion(1) { driver ->
            runs++
            notesFixed = driver.execute(null, "UPDATE note SET body = '(empty)' WHERE body = ''", 0).value
            tagTableExistedWhenRun = driver.executeQuery(
                null,
                "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'tag'",
                { cursor: SqlCursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0)!! > 0)
                },
                0,
            ).value
        }
    }

    /**
     * Schema the SQLDelight compiler would generate: `create` builds the newest tables for a
     * fresh file, and `migrate` replays `<n>.sqm` files, running each callback once the
     * database has reached the version after it.
     */
    class NotebookSchema(override val version: Long) : SqlSchema<QueryResult.Value<Unit>> {
        var createCount: Int = 0
            private set
        val appliedMigrations: MutableList<Long> = mutableListOf()

        override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
            createCount++
            val bodyColumn: String = if (version >= 2) ", body TEXT NOT NULL DEFAULT ''" else ""
            driver.execute(
                null,
                """
                CREATE TABLE note (
                  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  title TEXT NOT NULL UNIQUE$bodyColumn
                )
                """.trimIndent(),
                0,
            )
            if (version >= 3) {
                createTagTable(driver)
            }
            return QueryResult.Unit
        }

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.Value<Unit> {
            var lastVersion = oldVersion
            callbacks.filter { it.afterVersion in oldVersion until newVersion }
                .sortedBy { it.afterVersion }
                .forEach { callback ->
                    migrateInternal(driver, lastVersion, callback.afterVersion + 1)
                    callback.block(driver)
                    lastVersion = callback.afterVersion + 1
                }
            if (lastVersion < newVersion) {
                migrateInternal(driver, lastVersion, newVersion)
            }
            return QueryResult.Unit
        }

        private fun migrateInternal(driver: SqlDriver, oldVersion: Long, newVersion: Long) {
            if (oldVersion <= 1 && newVersion > 1) {
                driver.execute(null, "ALTER TABLE note ADD COLUMN body TEXT NOT NULL DEFAULT ''", 0)
                appliedMigrations += 1
            }
            if (oldVersion <= 2 && newVersion > 2) {
                createTagTable(driver)
                appliedMigrations += 2
            }
        }

        private fun createTagTable(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE tag (
                  note_id INTEGER NOT NULL REFERENCES note(id),
                  name TEXT NOT NULL,
                  PRIMARY KEY (note_id, name)
                )
                """.trimIndent(),
                0,
            )
        }
    }
}
