/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.SQLException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/**
 * Named shared-cache in-memory databases addressed by SQLite URI filenames through `JdbcSqliteDriver`.
 *
 * A session store keeps its data only in memory, but an ingest module and a dashboard module each
 * open their own driver on the same `file:<name>?mode=memory&cache=shared` URL. The scenario checks
 * what such an application relies on: both drivers see one database, other names and private
 * in-memory drivers stay isolated, the database disappears with its last driver, shared-cache
 * table locks keep a reader out of uncommitted writes unless it opts into `read_uncommitted`, and
 * query listeners of one driver are refreshed only when the application forwards the change.
 */
class SharedMemoryDatabaseTest {

    private val opened: MutableList<JdbcSqliteDriver> = mutableListOf()

    @AfterEach
    fun closeDrivers() {
        opened.forEach { driver: JdbcSqliteDriver -> driver.close() }
    }

    @Test
    fun driversOnTheSameNamedUrlShareOneDatabase(info: TestInfo) {
        val url: String = sharedUrl("sessions", info)
        val ingest: SessionStore = SessionStore(open(url))
        val dashboard: SessionStore = SessionStore(open(url))

        ingest.createSchema()
        ingest.startSession("s-1", "alice")
        dashboard.startSession("s-2", "bob")

        assertEquals(listOf(Session("s-1", "alice"), Session("s-2", "bob")), ingest.sessions().executeAsList())
        assertEquals(ingest.sessions().executeAsList(), dashboard.sessions().executeAsList())
        assertEquals(2L, dashboard.count())
    }

    @Test
    fun otherNamesAndPrivateInMemoryDriversAreIsolated(info: TestInfo) {
        val main: SessionStore = SessionStore(open(sharedUrl("main", info)))
        val audit: SessionStore = SessionStore(open(sharedUrl("audit", info)))
        val scratch: SessionStore = SessionStore(open(JdbcSqliteDriver.IN_MEMORY))
        val otherScratch: SessionStore = SessionStore(open(JdbcSqliteDriver.IN_MEMORY))

        main.createSchema()
        main.startSession("s-1", "alice")
        scratch.createSchema()
        scratch.startSession("tmp", "carol")

        assertEquals(listOf("session"), main.tables())
        assertEquals(emptyList<String>(), audit.tables())
        assertEquals(listOf(Session("tmp", "carol")), scratch.sessions().executeAsList())
        val missing: SQLException = assertThrows(SQLException::class.java) { otherScratch.count() }
        assertTrue(missing.message!!.contains("no such table"), missing.message)
    }

    @Test
    fun theDatabaseLivesUntilItsLastDriverCloses(info: TestInfo) {
        val url: String = sharedUrl("cache", info)
        val first: JdbcSqliteDriver = open(url)
        val second: JdbcSqliteDriver = open(url)
        SessionStore(first).createSchema()
        SessionStore(first).startSession("s-1", "alice")

        first.close()
        opened.remove(first)
        assertEquals(1L, SessionStore(second).count(), "a remaining driver keeps the database alive")

        second.close()
        opened.remove(second)
        val reopened: SessionStore = SessionStore(open(url))
        assertEquals(emptyList<String>(), reopened.tables(), "closing the last driver drops the data")
    }

    @Test
    fun uncommittedWritesLockTheTableForOtherDriversUntilCommit(info: TestInfo) {
        val url: String = sharedUrl("locks", info)
        val ingest: SessionStore = SessionStore(open(url))
        val dashboard: SessionStore = SessionStore(open(url))
        ingest.createSchema()
        ingest.startSession("s-1", "alice")

        ingest.transaction {
            ingest.startSession("s-2", "bob")
            val locked: SQLException = assertThrows(SQLException::class.java) { dashboard.count() }
            assertTrue(locked.message!!.contains("locked"), locked.message)
        }

        assertEquals(2L, dashboard.count())
    }

    @Test
    fun readUncommittedLetsADashboardPeekAtAnOpenTransaction(info: TestInfo) {
        val url: String = sharedUrl("peek", info)
        val ingest: SessionStore = SessionStore(open(url))
        val dashboard: SessionStore = SessionStore(open(url))
        ingest.createSchema()
        ingest.startSession("s-1", "alice")
        dashboard.readUncommitted()

        ingest.transaction {
            ingest.startSession("s-2", "bob")
            assertEquals(2L, dashboard.count(), "the dirty read sees the pending session")
            rollback()
        }

        assertEquals(listOf(Session("s-1", "alice")), dashboard.sessions().executeAsList())
    }

    @Test
    fun listenersOfAnotherDriverOnlyHearForwardedChanges(info: TestInfo) {
        val url: String = sharedUrl("events", info)
        val ingestDriver: JdbcSqliteDriver = open(url)
        val dashboardDriver: JdbcSqliteDriver = open(url)
        val ingest: SessionStore = SessionStore(ingestDriver)
        val dashboard: SessionStore = SessionStore(dashboardDriver)
        ingest.createSchema()
        val ingestView: Query<Session> = ingest.sessions()
        val dashboardView: Query<Session> = dashboard.sessions()
        val ingestScreen: CountingListener = CountingListener()
        val dashboardScreen: CountingListener = CountingListener()
        ingestView.addListener(ingestScreen)
        dashboardView.addListener(dashboardScreen)

        ingest.startSession("s-1", "alice")
        assertEquals(1, ingestScreen.changes)
        assertEquals(0, dashboardScreen.changes, "notifications stay within the writing driver")

        dashboardDriver.notifyListeners(SessionStore.TABLE)
        assertEquals(1, dashboardScreen.changes)
        assertEquals(listOf(Session("s-1", "alice")), dashboardView.executeAsList())

        ingestView.removeListener(ingestScreen)
        dashboardView.removeListener(dashboardScreen)
    }

    private fun open(url: String): JdbcSqliteDriver {
        val driver: JdbcSqliteDriver = JdbcSqliteDriver(url)
        opened += driver
        return driver
    }

    /** A URI filename naming a shared in-memory database unique to the running test. */
    private fun sharedUrl(name: String, info: TestInfo): String =
        "jdbc:sqlite:file:$name-${info.testMethod.get().name}?mode=memory&cache=shared"

    /** One row of the `session` table. */
    data class Session(val id: String, val user: String)

    /** Counts how often the observed query reported changed results. */
    class CountingListener : Query.Listener {
        var changes: Int = 0
            private set

        override fun queryResultsChanged() {
            changes++
        }
    }

    /** The session store's database, written the way SQLDelight generates one. */
    class SessionStore(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            driver.execute(null, "CREATE TABLE session (id TEXT PRIMARY KEY, user TEXT NOT NULL)", 0)
        }

        fun readUncommitted() {
            driver.execute(null, "PRAGMA read_uncommitted = true", 0)
        }

        fun startSession(id: String, user: String) {
            driver.execute(START_SESSION, "INSERT INTO session (id, user) VALUES (?, ?)", 2) {
                bindString(0, id)
                bindString(1, user)
            }
            notifyQueries(START_SESSION) { emit: (String) -> Unit -> emit(TABLE) }
        }

        fun sessions(): Query<Session> =
            Query(SELECT_SESSIONS, arrayOf(TABLE), driver, "Session.sq", "sessions",
                "SELECT id, user FROM session ORDER BY id") { cursor: SqlCursor ->
                Session(cursor.getString(0)!!, cursor.getString(1)!!)
            }

        fun count(): Long =
            driver.executeQuery(COUNT, "SELECT count(*) FROM session", { cursor: SqlCursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            }, 0).value

        fun tables(): List<String> =
            driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name", { cursor: SqlCursor ->
                val names: MutableList<String> = mutableListOf()
                while (cursor.next().value) names += cursor.getString(0)!!
                QueryResult.Value(names)
            }, 0).value

        companion object {
            const val TABLE: String = "session"
            private const val START_SESSION: Int = 1
            private const val SELECT_SESSIONS: Int = 2
            private const val COUNT: Int = 3
        }
    }
}
