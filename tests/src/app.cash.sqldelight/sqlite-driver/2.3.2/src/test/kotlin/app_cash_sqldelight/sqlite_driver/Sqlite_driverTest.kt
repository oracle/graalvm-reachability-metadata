/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

public class Sqlite_driverTest {
    @Test
    fun inMemoryDriverBindsQueriesAndReadsSqliteTypes(): Unit = withDriver { driver: JdbcSqliteDriver ->
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE notes (
                  id INTEGER PRIMARY KEY,
                  title TEXT,
                  rating REAL,
                  archived INTEGER,
                  payload BLOB
                )
            """.trimIndent(),
            parameters = 0,
            binders = null,
        )

        val insertedRows: Long = driver.execute(
            identifier = 1,
            sql = "INSERT INTO notes (id, title, rating, archived, payload) VALUES (?, ?, ?, ?, ?)",
            parameters = 5,
        ) {
            bindLong(0, 10L)
            bindString(1, "first")
            bindDouble(2, 4.5)
            bindBoolean(3, true)
            bindBytes(4, byteArrayOf(9, 8, 7))
        }.value

        driver.execute(
            identifier = 2,
            sql = "INSERT INTO notes (id, title, rating, archived, payload) VALUES (?, ?, ?, ?, ?)",
            parameters = 5,
        ) {
            bindLong(0, 11L)
            bindString(1, null)
            bindDouble(2, null)
            bindBoolean(3, null)
            bindBytes(4, null)
        }

        val rows: List<NoteRow> = driver.executeQuery(
            identifier = 3,
            sql = "SELECT id, title, rating, archived, payload FROM notes ORDER BY id",
            mapper = { cursor ->
                val values: MutableList<NoteRow> = mutableListOf()
                while (cursor.next().value) {
                    values += NoteRow(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        rating = cursor.getDouble(2),
                        archived = cursor.getBoolean(3),
                        payload = cursor.getBytes(4),
                    )
                }
                QueryResult.Value(values)
            },
            parameters = 0,
            binders = null,
        ).value

        assertThat(insertedRows).isEqualTo(1L)
        assertThat(rows).hasSize(2)
        assertThat(rows[0].id).isEqualTo(10L)
        assertThat(rows[0].title).isEqualTo("first")
        assertThat(rows[0].rating).isEqualTo(4.5)
        assertThat(rows[0].archived).isTrue()
        assertThat(rows[0].payload).containsExactly(9.toByte(), 8.toByte(), 7.toByte())
        assertThat(rows[1]).isEqualTo(NoteRow(11L, null, null, null, null))
    }

    @Test
    fun schemaConstructorCreatesDatabaseSetsUserVersionAndMigratesFileDatabase(): Unit = withDatabaseFile { databaseFile: Path ->
        val events: MutableList<String> = mutableListOf()
        val schemaV1: SqlSchema<QueryResult.Value<Unit>> = VersionedSchema(
            version = 1L,
            events = events,
            createStatements = listOf(
                "CREATE TABLE projects (id INTEGER PRIMARY KEY, name TEXT NOT NULL)",
            ),
            migrationStatements = emptyList(),
        )
        JdbcSqliteDriver(url = sqliteUrl(databaseFile), schema = schemaV1).use { driver: JdbcSqliteDriver ->
            driver.execute(
                identifier = 10,
                sql = "INSERT INTO projects (id, name) VALUES (?, ?)",
                parameters = 2,
            ) {
                bindLong(0, 1L)
                bindString(1, "metadata")
            }

            assertThat(projectNames(driver)).containsExactly("metadata")
            assertThat(userVersion(driver)).isEqualTo(1L)
        }

        val schemaV3: SqlSchema<QueryResult.Value<Unit>> = VersionedSchema(
            version = 3L,
            events = events,
            createStatements = emptyList(),
            migrationStatements = listOf(
                "ALTER TABLE projects ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
                "CREATE TABLE project_labels (project_id INTEGER NOT NULL, label TEXT NOT NULL)",
                "INSERT INTO project_labels (project_id, label) VALUES (1, 'native')",
            ),
        )
        val afterVersion2: AfterVersion = AfterVersion(2) { callbackDriver: SqlDriver ->
            callbackDriver.execute(
                identifier = 20,
                sql = "INSERT INTO project_labels (project_id, label) VALUES (?, ?)",
                parameters = 2,
            ) {
                bindLong(0, 1L)
                bindString(1, "callback")
            }
            events += "after-2"
        }

        JdbcSqliteDriver(
            sqliteUrl(databaseFile),
            Properties(),
            schemaV3,
            false,
            afterVersion2,
        ).use { driver: JdbcSqliteDriver ->
            assertThat(userVersion(driver)).isEqualTo(3L)
            assertThat(projectNames(driver)).containsExactly("metadata")
            assertThat(projectLabels(driver)).containsExactly("native", "callback")
        }

        assertThat(events).containsExactly("create-1", "migrate-1-to-3", "after-2")
    }

    @Test
    fun migrateEmptySchemaRunsMigrationInsteadOfCreate(): Unit {
        val events: MutableList<String> = mutableListOf()
        val schema: SqlSchema<QueryResult.Value<Unit>> = VersionedSchema(
            version = 2L,
            events = events,
            createStatements = listOf("CREATE TABLE created (value TEXT NOT NULL)"),
            migrationStatements = listOf(
                "CREATE TABLE migrated (value TEXT NOT NULL)",
                "INSERT INTO migrated (value) VALUES ('from migration')",
            ),
        )

        JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = schema,
            migrateEmptySchema = true,
        ).use { migratedDriver: JdbcSqliteDriver ->
            assertThat(userVersion(migratedDriver)).isEqualTo(2L)
            assertThat(tableExists(migratedDriver, "created")).isFalse()
            assertThat(tableExists(migratedDriver, "migrated")).isTrue()
            assertThat(singleString(migratedDriver, "SELECT value FROM migrated")).isEqualTo("from migration")
        }

        assertThat(events).containsExactly("migrate-0-to-2")
    }

    @Test
    fun constructorAppliesSQLiteConnectionProperties(): Unit {
        val properties: Properties = Properties().apply {
            setProperty("foreign_keys", "true")
        }

        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, properties).use { driver: JdbcSqliteDriver ->
            val foreignKeysEnabled: Boolean = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA foreign_keys",
                mapper = { cursor ->
                    assertThat(cursor.next().value).isTrue()
                    QueryResult.Value(cursor.getLong(0) == 1L)
                },
                parameters = 0,
                binders = null,
            ).value

            driver.execute(
                identifier = null,
                sql = "CREATE TABLE owners (id INTEGER PRIMARY KEY)",
                parameters = 0,
                binders = null,
            )
            driver.execute(
                identifier = null,
                sql = """
                    CREATE TABLE pets (
                      id INTEGER PRIMARY KEY,
                      owner_id INTEGER NOT NULL REFERENCES owners(id)
                    )
                """.trimIndent(),
                parameters = 0,
                binders = null,
            )

            assertThat(foreignKeysEnabled).isTrue()
            assertThatThrownBy {
                driver.execute(
                    identifier = 50,
                    sql = "INSERT INTO pets (id, owner_id) VALUES (?, ?)",
                    parameters = 2,
                ) {
                    bindLong(0, 1L)
                    bindLong(1, 100L)
                }
            }.isInstanceOf(SQLException::class.java)
        }
    }

    @Test
    fun transactionsCommitRollbackAndRunCallbacksWithSqliteBeginStatements(): Unit = withDriver { driver: JdbcSqliteDriver ->
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE audit_events (id INTEGER PRIMARY KEY, message TEXT NOT NULL)",
            parameters = 0,
            binders = null,
        )
        val transacter: DriverBackedTransacter = DriverBackedTransacter(driver)
        val callbacks: MutableList<String> = mutableListOf()

        transacter.transaction {
            afterCommit { callbacks += "committed" }
            afterRollback { callbacks += "unexpected rollback" }
            driver.execute(
                identifier = 30,
                sql = "INSERT INTO audit_events (id, message) VALUES (?, ?)",
                parameters = 2,
            ) {
                bindLong(0, 1L)
                bindString(1, "kept")
            }
        }

        val result: String = transacter.transactionWithResult {
            afterCommit { callbacks += "result committed" }
            driver.execute(
                identifier = 31,
                sql = "INSERT INTO audit_events (id, message) VALUES (?, ?)",
                parameters = 2,
            ) {
                bindLong(0, 2L)
                bindString(1, "returned")
            }
            "ok"
        }

        assertThatThrownBy {
            transacter.transaction {
                afterCommit { callbacks += "unexpected commit" }
                afterRollback { callbacks += "rolled back" }
                driver.execute(
                    identifier = 32,
                    sql = "INSERT INTO audit_events (id, message) VALUES (?, ?)",
                    parameters = 2,
                ) {
                    bindLong(0, 3L)
                    bindString(1, "discarded")
                }
                throw IllegalStateException("abort sqlite transaction")
            }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("abort sqlite transaction")

        assertThat(result).isEqualTo("ok")
        assertThat(callbacks).containsExactly("committed", "result committed", "rolled back")
        assertThat(auditMessages(driver)).containsExactly("kept", "returned")
    }

    @Test
    fun listenersAreDeduplicatedAndCanBeRemovedPerQueryKey(): Unit = withDriver { driver: JdbcSqliteDriver ->
        val firstCount: AtomicInteger = AtomicInteger()
        val secondCount: AtomicInteger = AtomicInteger()
        val firstListener: Query.Listener = Query.Listener { firstCount.incrementAndGet() }
        val secondListener: Query.Listener = Query.Listener { secondCount.incrementAndGet() }

        driver.addListener("notes", "projects", listener = firstListener)
        driver.addListener("notes", listener = secondListener)

        driver.notifyListeners("notes", "projects")
        driver.removeListener("notes", listener = firstListener)
        driver.notifyListeners("notes")
        driver.notifyListeners("projects")
        driver.removeListener("projects", listener = firstListener)
        driver.removeListener("notes", listener = secondListener)
        driver.notifyListeners("notes", "projects")

        assertThat(firstCount.get()).isEqualTo(2)
        assertThat(secondCount.get()).isEqualTo(2)
    }

    @Test
    fun transacterDefersQueryNotificationsUntilSuccessfulTransactionCompletes(): Unit =
        withDriver { driver: JdbcSqliteDriver ->
            val transacter: DriverBackedTransacter = DriverBackedTransacter(driver)
            val notificationCount: AtomicInteger = AtomicInteger()
            val listener: Query.Listener = Query.Listener { notificationCount.incrementAndGet() }
            driver.addListener("deferred_items", listener = listener)

            transacter.transaction {
                transacter.notifyTables(60, "deferred_items")
                transacter.notifyTables(60, "deferred_items")
                assertThat(notificationCount.get()).isZero()
            }

            assertThat(notificationCount.get()).isEqualTo(1)

            assertThatThrownBy {
                transacter.transaction {
                    transacter.notifyTables(61, "deferred_items")
                    assertThat(notificationCount.get()).isEqualTo(1)
                    throw IllegalStateException("rollback deferred notification")
                }
            }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("rollback deferred notification")

            assertThat(notificationCount.get()).isEqualTo(1)
            driver.removeListener("deferred_items", listener = listener)
        }

    @Test
    fun fileBackedDriverUsesConnectionsAcrossThreadsAndPersistsData(): Unit = withDatabaseFile { databaseFile: Path ->
        JdbcSqliteDriver(sqliteUrl(databaseFile)).use { driver: JdbcSqliteDriver ->
            driver.execute(
                identifier = null,
                sql = "CREATE TABLE thread_items (id INTEGER PRIMARY KEY, name TEXT NOT NULL)",
                parameters = 0,
                binders = null,
            )

            val executor: ExecutorService = Executors.newSingleThreadExecutor()
            try {
                val insertedName: String = executor.submit(
                    Callable {
                        driver.execute(
                            identifier = 40,
                            sql = "INSERT INTO thread_items (id, name) VALUES (?, ?)",
                            parameters = 2,
                        ) {
                            bindLong(0, 1L)
                            bindString(1, "worker")
                        }
                        singleString(driver, "SELECT name FROM thread_items WHERE id = 1")
                    },
                ).get(10, TimeUnit.SECONDS)

                assertThat(insertedName).isEqualTo("worker")
                assertThat(singleString(driver, "SELECT name FROM thread_items WHERE id = 1")).isEqualTo("worker")
            } finally {
                executor.shutdownNow()
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
            }
        }

        JdbcSqliteDriver(sqliteUrl(databaseFile)).use { reopenedDriver: JdbcSqliteDriver ->
            assertThat(singleString(reopenedDriver, "SELECT name FROM thread_items WHERE id = 1")).isEqualTo("worker")
        }
    }

    private data class NoteRow(
        val id: Long?,
        val title: String?,
        val rating: Double?,
        val archived: Boolean?,
        val payload: ByteArray?,
    )

    private class DriverBackedTransacter(driver: JdbcSqliteDriver) : TransacterImpl(driver) {
        fun notifyTables(queryIdentifier: Int, vararg tableNames: String): Unit {
            notifyQueries(queryIdentifier) { emit: (String) -> Unit ->
                tableNames.forEach { tableName: String -> emit(tableName) }
            }
        }
    }

    private class VersionedSchema(
        override val version: Long,
        private val events: MutableList<String>,
        private val createStatements: List<String>,
        private val migrationStatements: List<String>,
    ) : SqlSchema<QueryResult.Value<Unit>> {
        override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
            events += "create-$version"
            createStatements.forEach { statement: String ->
                driver.execute(null, statement, 0, null)
            }
            return QueryResult.Unit
        }

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.Value<Unit> {
            events += "migrate-$oldVersion-to-$newVersion"
            migrationStatements.forEach { statement: String ->
                driver.execute(null, statement, 0, null)
            }
            callbacks.sortedBy { callback: AfterVersion -> callback.afterVersion }
                .filter { callback: AfterVersion -> callback.afterVersion in (oldVersion + 1)..newVersion }
                .forEach { callback: AfterVersion -> callback.block(driver) }
            return QueryResult.Unit
        }
    }

    private companion object {
        private fun withDriver(block: (JdbcSqliteDriver) -> Unit): Unit {
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver: JdbcSqliteDriver ->
                block(driver)
            }
        }

        private fun withDatabaseFile(block: (Path) -> Unit): Unit {
            val databaseFile: Path = Files.createTempFile("sqldelight-sqlite-driver", ".db")
            try {
                block(databaseFile)
            } finally {
                Files.deleteIfExists(databaseFile)
                Files.deleteIfExists(databaseFile.resolveSibling("${databaseFile.fileName}-journal"))
                Files.deleteIfExists(databaseFile.resolveSibling("${databaseFile.fileName}-wal"))
                Files.deleteIfExists(databaseFile.resolveSibling("${databaseFile.fileName}-shm"))
            }
        }

        private fun sqliteUrl(databaseFile: Path): String = "jdbc:sqlite:${databaseFile.toAbsolutePath()}"

        private fun userVersion(driver: JdbcSqliteDriver): Long {
            return driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor ->
                    assertThat(cursor.next().value).isTrue()
                    QueryResult.Value(cursor.getLong(0) ?: 0L)
                },
                parameters = 0,
                binders = null,
            ).value
        }

        private fun projectNames(driver: JdbcSqliteDriver): List<String> {
            return strings(driver, "SELECT name FROM projects ORDER BY id")
        }

        private fun projectLabels(driver: JdbcSqliteDriver): List<String> {
            return strings(driver, "SELECT label FROM project_labels ORDER BY label DESC")
        }

        private fun auditMessages(driver: JdbcSqliteDriver): List<String> {
            return strings(driver, "SELECT message FROM audit_events ORDER BY id")
        }

        private fun strings(driver: JdbcSqliteDriver, sql: String): List<String> {
            return driver.executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor ->
                    val values: MutableList<String> = mutableListOf()
                    while (cursor.next().value) {
                        values += cursor.getString(0) ?: error("Expected non-null text value")
                    }
                    QueryResult.Value(values)
                },
                parameters = 0,
                binders = null,
            ).value
        }

        private fun singleString(driver: JdbcSqliteDriver, sql: String): String {
            return driver.executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor ->
                    assertThat(cursor.next().value).isTrue()
                    QueryResult.Value(cursor.getString(0) ?: error("Expected a string row"))
                },
                parameters = 0,
                binders = null,
            ).value
        }

        private fun tableExists(driver: JdbcSqliteDriver, tableName: String): Boolean {
            return driver.executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                mapper = { cursor ->
                    assertThat(cursor.next().value).isTrue()
                    QueryResult.Value((cursor.getLong(0) ?: 0L) > 0L)
                },
                parameters = 1,
            ) {
                bindString(0, tableName)
            }.value
        }
    }
}
