/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Query change notification through `Query.Listener`: a task board keeps live views of its
 * queries that refresh when `notifyQueries` reports a write to the tables they read, batch the
 * refresh until a transaction commits, skip it on rollback, and stop once a view is closed.
 *
 * `BoardQueries` is written the way the SQLDelight compiler emits a queries class for a
 * `Board.sq` file, so writes notify listeners through the same runtime path a generated
 * database uses.
 */
class TaskBoardLiveQueryTest {

    private lateinit var driver: SqlDriver
    private lateinit var board: BoardQueries

    @BeforeEach
    fun openDatabase() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BoardSchema.create(driver)
        board = BoardQueries(driver)
        board.addTask("write release notes")
    }

    @AfterEach
    fun closeDatabase() {
        driver.close()
    }

    @Test
    fun liveViewRefreshesAfterEachWriteOutsideTransaction() {
        val openTasks = LiveView(board.openTasks())

        board.addTask("tag release")
        board.complete("write release notes")

        assertThat(openTasks.refreshes).isEqualTo(2)
        assertThat(openTasks.rows).containsExactly(Task(2, "tag release", done = false))
        openTasks.close()
    }

    @Test
    fun writesInsideTransactionRefreshOnceAfterCommit() {
        val openTasks = LiveView(board.openTasks())
        var refreshesSeenInsideTransaction = -1

        board.transaction {
            board.addTask("tag release")
            board.addTask("publish artifacts")
            board.complete("write release notes")
            refreshesSeenInsideTransaction = openTasks.refreshes
        }

        assertThat(refreshesSeenInsideTransaction).isZero()
        assertThat(openTasks.refreshes).isEqualTo(1)
        assertThat(openTasks.rows).containsExactly(
            Task(2, "tag release", done = false),
            Task(3, "publish artifacts", done = false),
        )
        openTasks.close()
    }

    @Test
    fun rolledBackTransactionLeavesLiveViewUntouched() {
        val openTasks = LiveView(board.openTasks())

        board.transaction {
            board.addTask("abandoned idea")
            rollback()
        }

        assertThat(openTasks.refreshes).isZero()
        assertThat(openTasks.rows).containsExactly(Task(1, "write release notes", done = false))
        assertThat(board.openTasks().executeAsList()).isEqualTo(openTasks.rows)
        openTasks.close()
    }

    @Test
    fun viewOnlyHearsItsOwnTablesAndStopsAfterClose() {
        val openTasks = LiveView(board.openTasks())
        val labels = LiveView(board.labels())

        board.addLabel("urgent")
        assertThat(labels.refreshes).isEqualTo(1)
        assertThat(labels.rows).containsExactly("urgent")
        assertThat(openTasks.refreshes).isZero()

        openTasks.close()
        board.addTask("tag release")

        assertThat(openTasks.refreshes).isZero()
        assertThat(openTasks.rows).containsExactly(Task(1, "write release notes", done = false))
        assertThat(labels.refreshes).isEqualTo(1)
        labels.close()
    }

    /** One row of the `task` table. */
    data class Task(val id: Long, val title: String, val done: Boolean)

    /** Screen-model holder that re-runs its query whenever SQLDelight reports a change. */
    class LiveView<T : Any>(private val query: Query<T>) : Query.Listener {
        var rows: List<T> = query.executeAsList()
            private set
        var refreshes: Int = 0
            private set

        init {
            query.addListener(this)
        }

        override fun queryResultsChanged() {
            refreshes++
            rows = query.executeAsList()
        }

        fun close() {
            query.removeListener(this)
        }
    }

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE`s in `Board.sq`. */
    object BoardSchema {
        fun create(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE task (
                  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  title TEXT NOT NULL UNIQUE,
                  done INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
                0,
            )
            driver.execute(null, "CREATE TABLE label (name TEXT NOT NULL PRIMARY KEY)", 0)
        }
    }

    /** Hand-written equivalent of the queries class generated for `Board.sq`. */
    class BoardQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun openTasks(): Query<Task> = Query(
            OPEN_TASKS_ID,
            arrayOf("task"),
            driver,
            "Board.sq",
            "openTasks",
            "SELECT id, title, done FROM task WHERE done = 0 ORDER BY id",
        ) { cursor: SqlCursor -> Task(cursor.getLong(0)!!, cursor.getString(1)!!, cursor.getBoolean(2)!!) }

        fun labels(): Query<String> = Query(
            LABELS_ID,
            arrayOf("label"),
            driver,
            "Board.sq",
            "labels",
            "SELECT name FROM label ORDER BY name",
        ) { cursor: SqlCursor -> cursor.getString(0)!! }

        fun addTask(title: String) {
            driver.execute(ADD_TASK_ID, "INSERT INTO task (title) VALUES (?)", 1) {
                bindString(0, title)
            }
            notifyQueries(ADD_TASK_ID) { emit -> emit("task") }
        }

        fun complete(title: String) {
            driver.execute(COMPLETE_ID, "UPDATE task SET done = 1 WHERE title = ?", 1) {
                bindString(0, title)
            }
            notifyQueries(COMPLETE_ID) { emit -> emit("task") }
        }

        fun addLabel(name: String) {
            driver.execute(ADD_LABEL_ID, "INSERT INTO label (name) VALUES (?)", 1) {
                bindString(0, name)
            }
            notifyQueries(ADD_LABEL_ID) { emit -> emit("label") }
        }

        private companion object {
            const val OPEN_TASKS_ID: Int = 1
            const val LABELS_ID: Int = 2
            const val ADD_TASK_ID: Int = 3
            const val COMPLETE_ID: Int = 4
            const val ADD_LABEL_ID: Int = 5
        }
    }
}
