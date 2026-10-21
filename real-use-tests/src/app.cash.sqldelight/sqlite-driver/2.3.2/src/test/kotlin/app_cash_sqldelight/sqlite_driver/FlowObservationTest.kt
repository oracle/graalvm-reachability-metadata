/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneNotNull
import app.cash.sqldelight.coroutines.mapToOneOrDefault
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Reactive observation of SQLDelight queries as Kotlin coroutine Flows (`asFlow` with `mapToList`,
 * `mapToOne`, `mapToOneOrNull`, `mapToOneOrDefault` and `mapToOneNotNull`) over `JdbcSqliteDriver`.
 *
 * A task-inbox application renders its screens from flows: the task list, the open-task counter
 * and a detail view re-query the database on `Dispatchers.IO` whenever a write notifies them, and
 * a whole transaction reaches the observers as committed state only.
 */
class FlowObservationTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var inbox: InboxDatabase

    @BeforeEach
    fun openInbox() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        inbox = InboxDatabase(driver)
        inbox.createSchema()
    }

    @AfterEach
    fun closeInbox() {
        driver.close()
    }

    @Test
    fun taskListFlowEmitsTheCurrentListAfterEveryWrite() = runBlocking {
        val screen = Screen(this, inbox.selectOpenTasks().asFlow().mapToList(Dispatchers.IO))

        assertEquals(emptyList<Task>(), screen.next())

        inbox.addTask(1, "write report")
        assertEquals(listOf(Task(1, "write report", false)), screen.next())

        inbox.addTask(2, "book flights")
        assertEquals(listOf(Task(1, "write report", false), Task(2, "book flights", false)), screen.next())

        inbox.complete(1)
        assertEquals(listOf(Task(2, "book flights", false)), screen.next())

        screen.close()
    }

    @Test
    fun transactionReachesObserversOnlyAsCommittedState() = runBlocking {
        inbox.addTask(1, "pay rent")
        val counter = Screen(this, inbox.countOpenTasks().asFlow().mapToOne(Dispatchers.IO))
        assertEquals(1L, counter.next())

        inbox.transaction {
            inbox.addTask(2, "call plumber")
            inbox.addTask(3, "renew passport")
            inbox.complete(1)
        }
        assertEquals(2L, counter.next())

        inbox.transaction {
            inbox.addTask(4, "discarded")
            rollback()
        }
        inbox.addTask(5, "water plants")
        assertEquals(3L, counter.next())

        counter.close()
    }

    @Test
    fun detailViewFollowsOneTaskThroughItsLifecycle() = runBlocking {
        val detail = Screen(this, inbox.selectTask(7).asFlow().mapToOneOrNull(Dispatchers.IO))
        assertNull(detail.next())

        inbox.addTask(7, "fix bike")
        assertEquals(Task(7, "fix bike", false), detail.next())

        inbox.complete(7)
        assertEquals(Task(7, "fix bike", true), detail.next())

        inbox.delete(7)
        assertNull(detail.next())

        detail.close()
    }

    @Test
    fun defaultsAndNonNullObservationsHandleMissingRows() = runBlocking {
        val missing = Task(0, "no task selected", false)
        val placeholder: Task = inbox.selectTask(9).asFlow().mapToOneOrDefault(missing, Dispatchers.IO).first()
        assertEquals(missing, placeholder)

        val firstSeen = Screen(this, inbox.selectTask(9).asFlow().mapToOneNotNull(Dispatchers.IO))
        inbox.addTask(8, "unrelated")
        inbox.addTask(9, "prepare talk")
        assertEquals(Task(9, "prepare talk", false), firstSeen.next())

        firstSeen.close()
    }

    /** One row of the `task` table. */
    data class Task(val id: Long, val title: String, val done: Boolean)

    /**
     * A screen collecting a flow in the background and handing over each rendered value.
     *
     * The collector starts undispatched, so the query listener is registered before the
     * constructor returns and no later write can slip past it.
     */
    class Screen<T>(scope: CoroutineScope, flow: Flow<T>) {
        private val rendered: Channel<T> = Channel(Channel.UNLIMITED)
        private val job: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { value: T -> rendered.send(value) }
        }

        suspend fun next(): T = withTimeout(TIMEOUT_MILLIS) { rendered.receive() }

        fun close() {
            job.cancel()
        }

        private companion object {
            const val TIMEOUT_MILLIS: Long = 10_000
        }
    }

    /** The inbox database, written the way SQLDelight generates a database and its queries. */
    class InboxDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            driver.execute(
                null,
                "CREATE TABLE task (id INTEGER PRIMARY KEY, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0)",
                0,
            )
        }

        fun selectOpenTasks(): Query<Task> =
            Query(SELECT_OPEN, arrayOf("task"), driver, "Inbox.sq", "selectOpenTasks",
                "SELECT id, title, done FROM task WHERE done = 0 ORDER BY id") { cursor: SqlCursor ->
                mapTask(cursor)
            }

        fun countOpenTasks(): Query<Long> =
            Query(COUNT_OPEN, arrayOf("task"), driver, "Inbox.sq", "countOpenTasks",
                "SELECT count(*) FROM task WHERE done = 0") { cursor: SqlCursor ->
                cursor.getLong(0)!!
            }

        fun selectTask(id: Long): Query<Task> = TaskByIdQuery(id)

        fun addTask(id: Long, title: String) {
            driver.execute(ADD, "INSERT INTO task (id, title) VALUES (?, ?)", 2) {
                bindLong(0, id)
                bindString(1, title)
            }
            notifyQueries(ADD) { emit: (String) -> Unit -> emit("task") }
        }

        fun complete(id: Long) {
            driver.execute(COMPLETE, "UPDATE task SET done = 1 WHERE id = ?", 1) {
                bindLong(0, id)
            }
            notifyQueries(COMPLETE) { emit: (String) -> Unit -> emit("task") }
        }

        fun delete(id: Long) {
            driver.execute(DELETE, "DELETE FROM task WHERE id = ?", 1) {
                bindLong(0, id)
            }
            notifyQueries(DELETE) { emit: (String) -> Unit -> emit("task") }
        }

        private fun mapTask(cursor: SqlCursor): Task =
            Task(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getBoolean(2)!!)

        /** A parameterized query shaped like the inner query classes SQLDelight generates. */
        private inner class TaskByIdQuery(private val id: Long) : Query<Task>({ cursor: SqlCursor -> mapTask(cursor) }) {

            override fun addListener(listener: Query.Listener) {
                driver.addListener("task", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("task", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(SELECT_BY_ID, "SELECT id, title, done FROM task WHERE id = ?", mapper, 1) {
                    bindLong(0, id)
                }
        }

        private companion object {
            const val SELECT_OPEN: Int = 1
            const val COUNT_OPEN: Int = 2
            const val SELECT_BY_ID: Int = 3
            const val ADD: Int = 4
            const val COMPLETE: Int = 5
            const val DELETE: Int = 6
        }
    }
}
