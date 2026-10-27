/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.JdbcCursor
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.net.ServerSocket
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Pooled JDBC driver on PostgreSQL: an invoice ledger runs SQLDelight over a HikariCP
 * `DataSource` wrapped with `asJdbcDriver()`, so each thread borrows its own pooled connection,
 * a transaction pins one connection until it commits or rolls back, and PostgreSQL-specific
 * values travel through `JdbcPreparedStatement` and `JdbcCursor`.
 *
 * `LedgerQueries` is written the way the SQLDelight compiler emits a queries class for a
 * `Ledger.sq` file under the PostgreSQL dialect, which casts statements and cursors to their
 * JDBC types to bind and read `NUMERIC`, `TIMESTAMP` and `TEXT[]` columns.
 */
class InvoiceLedgerPooledJdbcDriverTest {

    private lateinit var pool: HikariDataSource
    private lateinit var driver: SqlDriver
    private lateinit var ledger: LedgerQueries
    private lateinit var otherThread: ExecutorService

    @BeforeEach
    fun openLedger() {
        pool = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = Companion.jdbcUrl
                username = "postgres"
                password = PASSWORD
                maximumPoolSize = 3
                poolName = "invoice-ledger"
            },
        )
        driver = pool.asJdbcDriver()
        LedgerSchema.recreate(driver)
        ledger = LedgerQueries(driver)
        otherThread = Executors.newSingleThreadExecutor()
    }

    @AfterEach
    fun closeLedger() {
        otherThread.shutdownNow()
        driver.close()
        pool.close()
    }

    @Test
    fun issuedInvoiceRoundTripsPostgresTypes() {
        val issuedAt = LocalDateTime.of(2026, 3, 14, 9, 30, 15)

        val firstId = ledger.issue("ACME", BigDecimal("1249.90"), issuedAt, listOf("hardware", "priority"))
        val secondId = ledger.issue("ACME", BigDecimal("15.05"), issuedAt.plusDays(1), emptyList())
        ledger.issue("Globex", BigDecimal("300.00"), issuedAt, listOf("services"))

        assertThat(secondId).isEqualTo(firstId + 1)
        assertThat(ledger.invoicesFor("ACME").executeAsList()).containsExactly(
            Invoice(firstId, "ACME", BigDecimal("1249.90"), issuedAt, listOf("hardware", "priority")),
            Invoice(secondId, "ACME", BigDecimal("15.05"), issuedAt.plusDays(1), emptyList()),
        )
        assertThat(ledger.outstandingTotal().executeAsOne()).isEqualByComparingTo("1564.95")
    }

    @Test
    fun openTransactionStaysInvisibleToOtherPooledConnectionsUntilCommit() {
        val issuedAt = LocalDateTime.of(2026, 4, 1, 12, 0)
        var countSeenInsideTransaction = -1L
        var countSeenByOtherThreadDuringTransaction = -1L
        val commits = mutableListOf<String>()

        ledger.transaction {
            afterCommit { commits += "posted" }
            ledger.issue("Initech", BigDecimal("80.00"), issuedAt, listOf("consulting"))
            ledger.issue("Initech", BigDecimal("20.00"), issuedAt, emptyList())
            countSeenInsideTransaction = ledger.invoiceCount().executeAsOne()
            countSeenByOtherThreadDuringTransaction = countOnOtherThread()
        }

        assertThat(countSeenInsideTransaction).isEqualTo(2)
        assertThat(countSeenByOtherThreadDuringTransaction).isZero()
        assertThat(commits).containsExactly("posted")
        assertThat(countOnOtherThread()).isEqualTo(2)
        assertThat(ledger.outstandingTotal().executeAsOne()).isEqualByComparingTo("100.00")
    }

    @Test
    fun constraintViolationRollsBackEveryWriteOfTheTransaction() {
        val issuedAt = LocalDateTime.of(2026, 5, 2, 8, 45)
        val outcomes = mutableListOf<String>()
        ledger.issue("Umbrella", BigDecimal("10.00"), issuedAt, emptyList())

        assertThatThrownBy {
            ledger.transaction {
                afterCommit { outcomes += "committed" }
                afterRollback { outcomes += "rolled back" }
                ledger.issue("Umbrella", BigDecimal("99.99"), issuedAt, listOf("rush"))
                ledger.issue("Umbrella", BigDecimal("-5.00"), issuedAt, emptyList())
            }
        }.isInstanceOfSatisfying(SQLException::class.java) { failure ->
            assertThat(failure.sqlState).isEqualTo(CHECK_VIOLATION)
        }

        assertThat(outcomes).containsExactly("rolled back")
        assertThat(ledger.invoicesFor("Umbrella").executeAsList().map { it.amount })
            .containsExactly(BigDecimal("10.00"))
        assertThat(countOnOtherThread()).isEqualTo(1)
    }

    @Test
    fun transactionWithResultReturnsGeneratedIdsFromReturningClause() {
        val issuedAt = LocalDateTime.of(2026, 6, 30, 17, 5)

        val batchIds: List<Long> = ledger.transactionWithResult {
            listOf("Hooli", "Pied Piper", "Hooli").mapIndexed { index, customer ->
                ledger.issue(customer, BigDecimal(100 + index).setScale(2), issuedAt, listOf("batch"))
            }
        }

        assertThat(batchIds).hasSize(3).doesNotHaveDuplicates().isSorted()
        assertThat(ledger.invoicesFor("Hooli").executeAsList().map { it.id })
            .containsExactly(batchIds[0], batchIds[2])
        assertThat(countOnOtherThread()).isEqualTo(3)
    }

    /** Counts invoices from a second thread, which borrows its own connection from the pool. */
    private fun countOnOtherThread(): Long =
        otherThread.submit<Long> { ledger.invoiceCount().executeAsOne() }.get(30, TimeUnit.SECONDS)

    /** One row of the `invoice` table. */
    data class Invoice(
        val id: Long,
        val customer: String,
        val amount: BigDecimal,
        val issuedAt: LocalDateTime,
        val tags: List<String>,
    )

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE` in `Ledger.sq`. */
    object LedgerSchema {
        fun recreate(driver: SqlDriver) {
            driver.execute(null, "DROP TABLE IF EXISTS invoice", 0)
            driver.execute(
                null,
                """
                CREATE TABLE invoice (
                  id BIGSERIAL PRIMARY KEY,
                  customer TEXT NOT NULL,
                  amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
                  issued_at TIMESTAMP NOT NULL,
                  tags TEXT[] NOT NULL
                )
                """.trimIndent(),
                0,
            )
        }
    }

    /** Hand-written equivalent of the queries class generated for `Ledger.sq`. */
    class LedgerQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun issue(customer: String, amount: BigDecimal, issuedAt: LocalDateTime, tags: List<String>): Long =
            IssueQuery(customer, amount, issuedAt, tags) { cursor -> cursor.getLong(0)!! }.executeAsOne()

        fun invoicesFor(customer: String): Query<Invoice> = InvoicesForQuery(customer) { cursor ->
            check(cursor is JdbcCursor)
            Invoice(
                cursor.getLong(0)!!,
                cursor.getString(1)!!,
                cursor.getBigDecimal(2)!!,
                // getTimestamp reads the 1-based JDBC column index, unlike the other getters.
                cursor.getTimestamp(4)!!.toLocalDateTime(),
                cursor.getArray<String>(4)!!.toList(),
            )
        }

        fun invoiceCount(): Query<Long> = Query(
            INVOICE_COUNT_ID,
            arrayOf("invoice"),
            driver,
            "Ledger.sq",
            "invoiceCount",
            "SELECT count(*) FROM invoice",
        ) { cursor: SqlCursor -> cursor.getLong(0)!! }

        fun outstandingTotal(): Query<BigDecimal> = Query(
            OUTSTANDING_TOTAL_ID,
            arrayOf("invoice"),
            driver,
            "Ledger.sq",
            "outstandingTotal",
            "SELECT coalesce(sum(amount), 0) FROM invoice",
        ) { cursor: SqlCursor ->
            check(cursor is JdbcCursor)
            cursor.getBigDecimal(0)!!
        }

        private inner class IssueQuery<out T : Any>(
            private val customer: String,
            private val amount: BigDecimal,
            private val issuedAt: LocalDateTime,
            private val tags: List<String>,
            mapper: (SqlCursor) -> T,
        ) : ExecutableQuery<T>(mapper) {

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                val result = driver.executeQuery(
                    ISSUE_ID,
                    """
                    INSERT INTO invoice (customer, amount, issued_at, tags)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """.trimIndent(),
                    mapper,
                    4,
                ) {
                    check(this is JdbcPreparedStatement)
                    bindString(0, customer)
                    bindBigDecimal(1, amount)
                    // bindTimestamp takes the 1-based JDBC index, unlike the other binders.
                    bindTimestamp(3, Timestamp.valueOf(issuedAt))
                    bindObject(3, tags.toTypedArray())
                }
                notifyQueries(ISSUE_ID) { emit -> emit("invoice") }
                return result
            }

            override fun toString(): String = "Ledger.sq:issue"
        }

        private inner class InvoicesForQuery<out T : Any>(
            private val customer: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {

            override fun addListener(listener: Listener) {
                driver.addListener("invoice", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("invoice", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(
                INVOICES_FOR_ID,
                """
                SELECT id, customer, amount, issued_at, tags
                FROM invoice
                WHERE customer = ?
                ORDER BY id
                """.trimIndent(),
                mapper,
                1,
            ) {
                bindString(0, customer)
            }

            override fun toString(): String = "Ledger.sq:invoicesFor"
        }

        private companion object {
            const val ISSUE_ID: Int = 1
            const val INVOICES_FOR_ID: Int = 2
            const val INVOICE_COUNT_ID: Int = 3
            const val OUTSTANDING_TOTAL_ID: Int = 4
        }
    }

    companion object {
        private const val PASSWORD = "ledger-secret"
        private const val CHECK_VIOLATION = "23514"
        private val containerName: String = "real-use-sqldelight-" + UUID.randomUUID()
        private var port: Int = 0

        private val jdbcUrl: String
            get() = "jdbc:postgresql://127.0.0.1:$port/postgres"

        @BeforeAll
        @JvmStatic
        fun startPostgres() {
            port = ServerSocket(0).use { it.localPort }
            docker(
                "run", "--rm", "-d", "--name", containerName,
                "-e", "POSTGRES_PASSWORD=$PASSWORD",
                "-p", "127.0.0.1:$port:5432",
                "postgres:18-alpine",
            )
            awaitConnection(TimeUnit.SECONDS.toMillis(120))
        }

        @AfterAll
        @JvmStatic
        fun stopPostgres() {
            docker("rm", "-f", containerName)
        }

        private fun docker(vararg args: String) {
            val process = ProcessBuilder(listOf("docker") + args).inheritIO().start()
            check(process.waitFor(5, TimeUnit.MINUTES)) { "docker ${args.first()} timed out" }
            check(process.exitValue() == 0) { "docker ${args.first()} failed with ${process.exitValue()}" }
        }

        /** Polls with a bounded deadline until the server accepts connections. */
        private fun awaitConnection(timeoutMillis: Long) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            var lastFailure: Exception? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    DriverManager.getConnection(jdbcUrl, "postgres", PASSWORD).use { connection ->
                        connection.createStatement().use { it.execute("SELECT 1") }
                    }
                    return
                } catch (e: Exception) {
                    lastFailure = e
                    Thread.sleep(250)
                }
            }
            throw IllegalStateException("PostgreSQL did not become reachable", lastFailure)
        }
    }
}
