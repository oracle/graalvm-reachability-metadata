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

/**
 * Failure propagation through `Transacter` transactions: a nested failure that the enclosing
 * transaction catches still rolls the whole unit back, a nested `rollback()` silently undoes the
 * enclosing work, and exceptions thrown from `afterCommit` and `afterRollback` callbacks reach
 * the caller after the database outcome is already settled.
 *
 * A payroll service pays a batch of payslips and exports them to a bank gateway once committed.
 * `PayrollQueries` is written the way the SQLDelight compiler emits a queries class for a
 * `Payroll.sq` file, so the scenario drives the same runtime calls a generated database does.
 */
class PayrollRunTransactionFailureTest {

    private lateinit var driver: SqlDriver
    private lateinit var payroll: PayrollQueries
    private lateinit var gateway: BankGateway
    private lateinit var service: PayrollService
    private lateinit var payslipChanges: CountingListener

    @BeforeEach
    fun openDatabase() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PayrollSchema.create(driver)
        payroll = PayrollQueries(driver)
        gateway = BankGateway()
        service = PayrollService(payroll, gateway)
        payroll.hire("ana", 4_000)
        payroll.hire("ben", 3_500)
        payroll.hire("cleo", 5_200)
        payslipChanges = CountingListener()
        payroll.payslips().addListener(payslipChanges)
    }

    @AfterEach
    fun closeDatabase() {
        driver.close()
    }

    @Test
    fun caughtNestedFailureStillRollsBackTheWholeBatch() {
        val summary: BatchSummary = service.payBatchSkippingFailures(
            period = "2026-09",
            employees = listOf("ana", "cleo", "zed"),
        )

        // The batch body returned normally, yet its last nested transaction failed, which marks
        // the enclosing transaction for rollback.
        assertThat(summary).isEqualTo(BatchSummary(paid = listOf("ana", "cleo"), skipped = listOf("zed")))
        assertThat(payroll.payslips().executeAsList()).isEmpty()
        assertThat(service.journal).containsExactly("batch 2026-09 rolled back")
        assertThat(gateway.exported).isEmpty()
        assertThat(payslipChanges.notifications).isZero()
        assertThat(driver.currentTransaction()).isNull()
    }

    @Test
    fun independentTopLevelTransactionsKeepTheSuccessfulPayslips() {
        val summary: BatchSummary = service.payEachIndependently(
            period = "2026-09",
            employees = listOf("ana", "zed", "cleo"),
        )

        assertThat(summary).isEqualTo(BatchSummary(paid = listOf("ana", "cleo"), skipped = listOf("zed")))
        assertThat(payroll.payslips().executeAsList()).containsExactly(
            Payslip("ana", "2026-09", 4_000),
            Payslip("cleo", "2026-09", 5_200),
        )
        assertThat(service.journal).containsExactly(
            "payslip ana 2026-09 committed",
            "payslip zed 2026-09 rolled back",
            "payslip cleo 2026-09 committed",
        )
        assertThat(gateway.exported).containsExactly("ana:4000", "cleo:5200")
        assertThat(payslipChanges.notifications).isEqualTo(2)
    }

    @Test
    fun nestedRollbackUndoesEnclosingWorkWithoutAnException() {
        payroll.placeOnHold("ben")

        service.payBatchWithHoldCheck(period = "2026-09", employees = listOf("ana", "ben", "cleo"))

        assertThat(payroll.payslips().executeAsList()).isEmpty()
        // Rollback hooks of the nested transaction join the enclosing list after its own hooks.
        assertThat(service.journal).containsExactly(
            "batch 2026-09 rolled back",
            "ben on hold, payslip 2026-09 withdrawn",
        )
        assertThat(gateway.exported).isEmpty()
        assertThat(payslipChanges.notifications).isZero()
    }

    @Test
    fun nestedHoldCheckCommitsWithTheBatchWhenNobodyIsOnHold() {
        service.payBatchWithHoldCheck(period = "2026-09", employees = listOf("ana", "ben"))

        assertThat(payroll.payslips().executeAsList()).containsExactly(
            Payslip("ana", "2026-09", 4_000),
            Payslip("ben", "2026-09", 3_500),
        )
        assertThat(service.journal).containsExactly("batch 2026-09 committed")
        assertThat(gateway.exported).containsExactly("ana:4000", "ben:3500")
        // Both nested inserts notify the payslip table once, when the outer transaction commits.
        assertThat(payslipChanges.notifications).isEqualTo(1)
    }

    @Test
    fun failingAfterCommitCallbackSurfacesAfterTheDataIsCommitted() {
        gateway.online = false

        assertThatThrownBy {
            service.payBatch(period = "2026-10", employees = listOf("ana", "ben"))
        }.isInstanceOf(BankGateway.GatewayUnavailable::class.java)
            .hasMessage("bank gateway offline, 2 transfers not sent")

        assertThat(payroll.payslips().executeAsList()).containsExactly(
            Payslip("ana", "2026-10", 4_000),
            Payslip("ben", "2026-10", 3_500),
        )
        // Listeners hear about the commit before the callbacks run; the journal callback that
        // was registered after the failing export never runs.
        assertThat(payslipChanges.notifications).isEqualTo(1)
        assertThat(service.journal).isEmpty()
        assertThat(driver.currentTransaction()).isNull()

        gateway.online = true
        assertThat(service.resendUnexported(period = "2026-10")).isEqualTo(2)
        assertThat(gateway.exported).containsExactly("ana:4000", "ben:3500")
    }

    @Test
    fun failingAfterRollbackCallbackIsReportedWithTheOriginalFailure() {
        service.alertingOnline = false

        val thrown: Throwable = catchFailure {
            service.payBatch(period = "2026-11", employees = listOf("ana", "zed"))
        }

        assertThat(thrown).hasCauseInstanceOf(IllegalStateException::class.java)
        assertThat(thrown.cause).hasMessage("alerting service unreachable")
        assertThat(thrown.message)
            .startsWith("Exception while rolling back from an exception.")
            .contains("unknown employee zed")
        assertThat(payroll.payslips().executeAsList()).isEmpty()
        assertThat(payslipChanges.notifications).isZero()
        assertThat(driver.currentTransaction()).isNull()

        // The connection is usable again for the next batch once the failure is handled.
        service.alertingOnline = true
        service.payBatch(period = "2026-11", employees = listOf("ana"))
        assertThat(payroll.payslips().executeAsList()).containsExactly(Payslip("ana", "2026-11", 4_000))
    }

    private fun catchFailure(block: () -> Unit): Throwable {
        try {
            block()
        } catch (failure: Throwable) {
            return failure
        }
        throw AssertionError("expected the payroll run to fail")
    }

    /** One row of the `payslip` table. */
    data class Payslip(val employee: String, val period: String, val amount: Long)

    /** What the service reports to the payroll clerk after a batch run. */
    data class BatchSummary(val paid: List<String>, val skipped: List<String>)

    /** Counts how often SQLDelight told the application that the payslip table changed. */
    class CountingListener : Query.Listener {
        var notifications: Int = 0

        override fun queryResultsChanged() {
            notifications++
        }
    }

    /** Stand-in for the bank's transfer endpoint that payroll calls only for committed payslips. */
    class BankGateway {
        var online: Boolean = true
        val exported: MutableList<String> = mutableListOf()

        fun send(payslips: List<Payslip>) {
            if (!online) {
                throw GatewayUnavailable("bank gateway offline, ${payslips.size} transfers not sent")
            }
            payslips.forEach { payslip: Payslip -> exported += "${payslip.employee}:${payslip.amount}" }
        }

        class GatewayUnavailable(message: String) : RuntimeException(message)
    }

    /** Application service that turns employee lists into committed, exported payslips. */
    class PayrollService(private val payroll: PayrollQueries, private val gateway: BankGateway) {
        val journal: MutableList<String> = mutableListOf()
        var alertingOnline: Boolean = true

        fun payBatch(period: String, employees: List<String>) {
            payroll.transaction {
                afterCommit { exportPeriod(period) }
                afterCommit { journal += "batch $period committed" }
                afterRollback {
                    check(alertingOnline) { "alerting service unreachable" }
                    journal += "batch $period rolled back"
                }
                employees.forEach { employee: String -> payroll.issuePayslip(employee, period) }
            }
        }

        fun payBatchSkippingFailures(period: String, employees: List<String>): BatchSummary =
            payroll.transactionWithResult {
                afterCommit { journal += "batch $period committed" }
                afterRollback { journal += "batch $period rolled back" }
                val paid: MutableList<String> = mutableListOf()
                val skipped: MutableList<String> = mutableListOf()
                employees.forEach { employee: String ->
                    try {
                        payroll.transaction { payroll.issuePayslip(employee, period) }
                        paid += employee
                    } catch (failure: IllegalStateException) {
                        skipped += employee
                    }
                }
                BatchSummary(paid, skipped)
            }

        fun payEachIndependently(period: String, employees: List<String>): BatchSummary {
            val paid: MutableList<String> = mutableListOf()
            val skipped: MutableList<String> = mutableListOf()
            employees.forEach { employee: String ->
                try {
                    payroll.transaction {
                        afterCommit {
                            journal += "payslip $employee $period committed"
                            exportPeriodFor(employee, period)
                        }
                        afterRollback { journal += "payslip $employee $period rolled back" }
                        payroll.issuePayslip(employee, period)
                    }
                    paid += employee
                } catch (failure: IllegalStateException) {
                    skipped += employee
                }
            }
            return BatchSummary(paid, skipped)
        }

        fun payBatchWithHoldCheck(period: String, employees: List<String>) {
            payroll.transaction {
                afterCommit { exportPeriod(period) }
                afterCommit { journal += "batch $period committed" }
                afterRollback { journal += "batch $period rolled back" }
                employees.forEach { employee: String ->
                    payroll.transaction {
                        payroll.issuePayslip(employee, period)
                        if (payroll.isOnHold(employee)) {
                            afterRollback { journal += "$employee on hold, payslip $period withdrawn" }
                            rollback()
                        }
                    }
                }
            }
        }

        fun resendUnexported(period: String): Int {
            val pending: List<Payslip> = payroll.payslipsFor(period).executeAsList()
            exportPeriod(period)
            return pending.size
        }

        private fun exportPeriod(period: String) {
            gateway.send(payroll.payslipsFor(period).executeAsList())
        }

        private fun exportPeriodFor(employee: String, period: String) {
            gateway.send(payroll.payslipsFor(period).executeAsList().filter { it.employee == employee })
        }
    }

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE`s in `Payroll.sq`. */
    object PayrollSchema {
        fun create(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE employee (
                  name TEXT NOT NULL PRIMARY KEY,
                  salary INTEGER NOT NULL,
                  on_hold INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                CREATE TABLE payslip (
                  employee TEXT NOT NULL REFERENCES employee(name),
                  period TEXT NOT NULL,
                  amount INTEGER NOT NULL,
                  PRIMARY KEY (employee, period)
                )
                """.trimIndent(),
                0,
            )
        }
    }

    /** Hand-written equivalent of the queries class generated for `Payroll.sq`. */
    class PayrollQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun payslips(): Query<Payslip> = Query(
            PAYSLIPS_ID,
            arrayOf("payslip"),
            driver,
            "Payroll.sq",
            "payslips",
            "SELECT employee, period, amount FROM payslip ORDER BY period, employee",
            ::mapPayslip,
        )

        fun payslipsFor(period: String): Query<Payslip> = PayslipsForQuery(period, ::mapPayslip)

        fun isOnHold(name: String): Boolean = OnHoldQuery(name) { cursor: SqlCursor ->
            cursor.getBoolean(0)!!
        }.executeAsOne()

        fun hire(name: String, salary: Long) {
            driver.execute(HIRE_ID, "INSERT INTO employee (name, salary) VALUES (?, ?)", 2) {
                bindString(0, name)
                bindLong(1, salary)
            }
            notifyQueries(HIRE_ID) { emit -> emit("employee") }
        }

        fun placeOnHold(name: String) {
            driver.execute(HOLD_ID, "UPDATE employee SET on_hold = 1 WHERE name = ?", 1) {
                bindString(0, name)
            }
            notifyQueries(HOLD_ID) { emit -> emit("employee") }
        }

        fun issuePayslip(name: String, period: String) {
            val inserted: Long = driver.execute(
                ISSUE_ID,
                """
                INSERT INTO payslip (employee, period, amount)
                SELECT name, ?, salary FROM employee WHERE name = ?
                """.trimIndent(),
                2,
            ) {
                bindString(0, period)
                bindString(1, name)
            }.value
            check(inserted == 1L) { "unknown employee $name" }
            notifyQueries(ISSUE_ID) { emit -> emit("payslip") }
        }

        private fun mapPayslip(cursor: SqlCursor): Payslip =
            Payslip(cursor.getString(0)!!, cursor.getString(1)!!, cursor.getLong(2)!!)

        private inner class PayslipsForQuery<T : Any>(
            private val period: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Listener) {
                driver.addListener("payslip", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("payslip", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(
                    PAYSLIPS_FOR_ID,
                    "SELECT employee, period, amount FROM payslip WHERE period = ? ORDER BY employee",
                    mapper,
                    1,
                ) {
                    bindString(0, period)
                }

            override fun toString(): String = "Payroll.sq:payslipsFor"
        }

        private inner class OnHoldQuery<T : Any>(
            private val name: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Listener) {
                driver.addListener("employee", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("employee", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(ON_HOLD_ID, "SELECT on_hold FROM employee WHERE name = ?", mapper, 1) {
                    bindString(0, name)
                }

            override fun toString(): String = "Payroll.sq:isOnHold"
        }

        private companion object {
            const val PAYSLIPS_ID: Int = 1
            const val PAYSLIPS_FOR_ID: Int = 2
            const val ON_HOLD_ID: Int = 3
            const val HIRE_ID: Int = 4
            const val HOLD_ID: Int = 5
            const val ISSUE_ID: Int = 6
        }
    }
}
