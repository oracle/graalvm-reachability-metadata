/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread confinement of transactions on a file-backed `JdbcSqliteDriver`: a ticket desk opens a
 * WAL database configured through driver `Properties`, sells tickets from several worker threads
 * that each run their own transaction on a per-thread connection, keeps an open transaction
 * invisible to other threads until it commits, rejects a day-close job started inside another
 * transaction with `noEnclosing`, and refuses transaction callbacks registered from a thread
 * the transaction was not started on.
 *
 * `TicketQueries` is written the way the SQLDelight compiler emits a queries class for a
 * `Ticket.sq` file, so the scenario drives the same runtime calls a generated database does.
 */
class TicketDeskThreadedTransactionTest {

    @TempDir
    lateinit var dataDir: Path

    private lateinit var driver: SqlDriver
    private lateinit var tickets: TicketQueries
    private lateinit var workers: ExecutorService

    @BeforeEach
    fun openDesk() {
        driver = openDriver()
        TicketSchema.create(driver)
        tickets = TicketQueries(driver)
        val workerIds = AtomicInteger()
        workers = Executors.newFixedThreadPool(WORKERS) { task: Runnable ->
            Thread(task, "ticket-worker-${workerIds.incrementAndGet()}")
        }
    }

    @AfterEach
    fun closeDesk() {
        workers.shutdownNow()
        assertThat(workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
        driver.close()
    }

    @Test
    fun driverPropertiesConfigureTheDatabaseFile() {
        val journalMode: String = driver.executeQuery(
            null,
            "PRAGMA journal_mode",
            { cursor: SqlCursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            0,
        ).value!!

        assertThat(journalMode).isEqualToIgnoringCase("wal")
        assertThatThrownBy { tickets.sell(show = "missing-show", seat = "A1") }
            .hasMessageContaining("FOREIGN KEY")
        assertThat(tickets.soldCount().executeAsOne()).isZero()
    }

    @Test
    fun openTransactionOnWorkerStaysPrivateUntilCommit() {
        val sold = CountDownLatch(1)
        val release = CountDownLatch(1)
        val commitThreads: MutableSet<String> = ConcurrentHashMap.newKeySet()

        val sale: Future<Boolean> = workers.submit<Boolean> {
            tickets.transactionWithResult {
                afterCommit { commitThreads += Thread.currentThread().name }
                tickets.sell(show = "opera", seat = "B7")
                sold.countDown()
                check(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "desk never released the sale" }
                driver.currentTransaction() != null
            }
        }

        assertThat(sold.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(driver.currentTransaction()).isNull()
        assertThat(tickets.soldCount().executeAsOne()).isZero()
        assertThat(tickets.seatsFor("opera").executeAsList()).isEmpty()

        release.countDown()

        assertThat(sale.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
        assertThat(tickets.seatsFor("opera").executeAsList()).containsExactly("B7")
        assertThat(commitThreads).containsExactly("ticket-worker-1")
    }

    @Test
    fun concurrentWorkerTransactionsAllCommitAndPersistToFile() {
        val start = CountDownLatch(1)
        val notifiedThreads: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val notifications = AtomicInteger()
        val listener = Query.Listener {
            notifications.incrementAndGet()
            notifiedThreads += Thread.currentThread().name
        }
        val soldCount: Query<Long> = tickets.soldCount()
        soldCount.addListener(listener)

        val sales: List<Future<Int>> = (1..WORKERS).map { worker: Int ->
            workers.submit<Int> {
                check(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "sales never opened" }
                (1..SALES_PER_WORKER).forEach { sale: Int ->
                    tickets.transaction {
                        tickets.sell(show = "concert", seat = "W$worker-S$sale")
                        tickets.recordRevenue(show = "concert", cents = TICKET_PRICE_CENTS)
                    }
                }
                SALES_PER_WORKER
            }
        }
        start.countDown()
        val totalSold: Int = sales.sumOf { sale: Future<Int> -> sale.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }

        val expected: Long = (WORKERS * SALES_PER_WORKER).toLong()
        assertThat(totalSold.toLong()).isEqualTo(expected)
        assertThat(soldCount.executeAsOne()).isEqualTo(expected)
        assertThat(tickets.revenue("concert").executeAsOne()).isEqualTo(expected * TICKET_PRICE_CENTS)
        assertThat(notifications.get().toLong()).isEqualTo(expected)
        assertThat(notifiedThreads).allMatch { name: String -> name.startsWith("ticket-worker-") }
        soldCount.removeListener(listener)

        driver.close()
        driver = openDriver()
        val reopened = TicketQueries(driver)
        assertThat(reopened.soldCount().executeAsOne()).isEqualTo(expected)
        assertThat(reopened.seatsFor("concert").executeAsList()).hasSize(expected.toInt()).doesNotHaveDuplicates()
    }

    @Test
    fun dayCloseRefusesToJoinAnEnclosingTransaction() {
        tickets.sell(show = "ballet", seat = "C1")
        tickets.recordRevenue(show = "ballet", cents = TICKET_PRICE_CENTS)

        assertThatThrownBy {
            tickets.transaction {
                tickets.sell(show = "ballet", seat = "C2")
                tickets.closeDay()
            }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Already in a transaction")

        assertThat(tickets.seatsFor("ballet").executeAsList()).containsExactly("C1")
        assertThat(tickets.archivedCount().executeAsOne()).isZero()

        val archived: Long = tickets.closeDay()

        assertThat(archived).isEqualTo(1L)
        assertThat(tickets.soldCount().executeAsOne()).isZero()
        assertThat(tickets.archivedCount().executeAsOne()).isEqualTo(1L)
        assertThat(tickets.revenue("ballet").executeAsOne()).isZero()
    }

    @Test
    fun callbacksRegisteredFromAnotherThreadAreRejected() {
        val audit: MutableList<String> = mutableListOf()

        val handoffFailure: Throwable? = tickets.transactionWithResult {
            afterCommit { audit += "sold D4" }
            tickets.sell(show = "jazz", seat = "D4")
            val handoff: Future<Unit> = workers.submit<Unit> {
                afterCommit { audit += "receipt mailed for D4" }
            }
            runCatching { handoff.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }.exceptionOrNull()
        }

        assertThat(handoffFailure).isInstanceOf(ExecutionException::class.java)
        assertThat(handoffFailure!!.cause)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("only within the transaction lambda scope")
        assertThat(audit).containsExactly("sold D4")
        assertThat(tickets.seatsFor("jazz").executeAsList()).containsExactly("D4")
    }

    private fun openDriver(): JdbcSqliteDriver {
        val properties = Properties()
        properties["journal_mode"] = "WAL"
        properties["foreign_keys"] = "true"
        properties["busy_timeout"] = BUSY_TIMEOUT_MILLIS.toString()
        return JdbcSqliteDriver("jdbc:sqlite:${dataDir.resolve("tickets.db")}", properties)
    }

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE`s in `Ticket.sq`. */
    object TicketSchema {
        fun create(driver: SqlDriver) {
            driver.execute(null, "CREATE TABLE show (name TEXT NOT NULL PRIMARY KEY, revenue INTEGER NOT NULL)", 0)
            driver.execute(
                null,
                """
                CREATE TABLE ticket (
                  show TEXT NOT NULL REFERENCES show(name),
                  seat TEXT NOT NULL,
                  PRIMARY KEY (show, seat)
                )
                """.trimIndent(),
                0,
            )
            driver.execute(null, "CREATE TABLE archived_ticket (show TEXT NOT NULL, seat TEXT NOT NULL)", 0)
            listOf("opera", "concert", "ballet", "jazz").forEach { show: String ->
                driver.execute(null, "INSERT INTO show (name, revenue) VALUES (?, 0)", 1) { bindString(0, show) }
            }
        }
    }

    /** Hand-written equivalent of the queries class generated for `Ticket.sq`. */
    class TicketQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun soldCount(): Query<Long> =
            Query(SOLD_COUNT_ID, arrayOf("ticket"), driver, "Ticket.sq", "soldCount", "SELECT count(*) FROM ticket") {
                cursor: SqlCursor -> cursor.getLong(0)!!
            }

        fun archivedCount(): Query<Long> = Query(
            ARCHIVED_COUNT_ID,
            arrayOf("archived_ticket"),
            driver,
            "Ticket.sq",
            "archivedCount",
            "SELECT count(*) FROM archived_ticket",
        ) { cursor: SqlCursor -> cursor.getLong(0)!! }

        fun seatsFor(show: String): Query<String> = ShowQuery(SEATS_ID, "ticket", "SELECT seat FROM ticket WHERE show = ? ORDER BY seat", show) {
            cursor: SqlCursor -> cursor.getString(0)!!
        }

        fun revenue(show: String): Query<Long> = ShowQuery(REVENUE_ID, "show", "SELECT revenue FROM show WHERE name = ?", show) {
            cursor: SqlCursor -> cursor.getLong(0)!!
        }

        fun sell(show: String, seat: String) {
            driver.execute(SELL_ID, "INSERT INTO ticket (show, seat) VALUES (?, ?)", 2) {
                bindString(0, show)
                bindString(1, seat)
            }
            notifyQueries(SELL_ID) { emit -> emit("ticket") }
        }

        fun recordRevenue(show: String, cents: Long) {
            driver.execute(RECORD_REVENUE_ID, "UPDATE show SET revenue = revenue + ? WHERE name = ?", 2) {
                bindLong(0, cents)
                bindString(1, show)
            }
            notifyQueries(RECORD_REVENUE_ID) { emit -> emit("show") }
        }

        /** Moves every sold ticket to the archive; must run as its own top-level transaction. */
        fun closeDay(): Long = transactionWithResult(noEnclosing = true) {
            val archived: Long = driver.execute(
                CLOSE_DAY_ARCHIVE_ID,
                "INSERT INTO archived_ticket (show, seat) SELECT show, seat FROM ticket",
                0,
            ).value
            driver.execute(CLOSE_DAY_CLEAR_ID, "DELETE FROM ticket", 0)
            driver.execute(CLOSE_DAY_RESET_ID, "UPDATE show SET revenue = 0", 0)
            notifyQueries(CLOSE_DAY_ARCHIVE_ID) { emit ->
                emit("archived_ticket")
                emit("ticket")
                emit("show")
            }
            archived
        }

        private inner class ShowQuery<T : Any>(
            private val identifier: Int,
            private val table: String,
            private val sql: String,
            private val show: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Listener) {
                driver.addListener(table, listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener(table, listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(identifier, sql, mapper, 1) { bindString(0, show) }

            override fun toString(): String = "Ticket.sq:$identifier"
        }

        private companion object {
            const val SOLD_COUNT_ID: Int = 1
            const val ARCHIVED_COUNT_ID: Int = 2
            const val SEATS_ID: Int = 3
            const val REVENUE_ID: Int = 4
            const val SELL_ID: Int = 5
            const val RECORD_REVENUE_ID: Int = 6
            const val CLOSE_DAY_ARCHIVE_ID: Int = 7
            const val CLOSE_DAY_CLEAR_ID: Int = 8
            const val CLOSE_DAY_RESET_ID: Int = 9
        }
    }

    private companion object {
        const val WORKERS: Int = 4
        const val SALES_PER_WORKER: Int = 25
        const val TICKET_PRICE_CENTS: Long = 4_500
        const val TIMEOUT_SECONDS: Long = 60
        const val BUSY_TIMEOUT_MILLIS: Long = 30_000
    }
}
