/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcCursor
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import java.math.BigDecimal
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC-specific decimal and temporal type mapping for an unpaid-invoice report.
 */
@Timeout(60)
class InvoiceDueDateReportTest {
    @Test
    fun reportsExactOutstandingAmountsAndRemovesSettledInvoices() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:invoice_due_date_report")
        }
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    """
                    CREATE TABLE invoices (
                        reference VARCHAR(40) PRIMARY KEY,
                        amount DECIMAL(19, 2) NOT NULL,
                        due_date DATE NOT NULL,
                        paid_at TIMESTAMP(6)
                    )
                    """.trimIndent(),
                    0
                )
                val cutoff: LocalDate = LocalDate.of(2025, 3, 31)
                val paidAt: LocalDateTime = LocalDateTime.of(2025, 3, 15, 10, 30, 45, 123456000)
                val overdue: Invoice = Invoice("INV-101", BigDecimal("1234567890123.45"), cutoff.minusDays(1))
                val dueToday: Invoice = Invoice("INV-102", BigDecimal("0.10"), cutoff)
                val future: Invoice = Invoice("INV-103", BigDecimal("99.99"), cutoff.plusDays(1))
                val paid: Invoice = Invoice("INV-104", BigDecimal("20.00"), cutoff.minusDays(2), paidAt)
                listOf(overdue, dueToday, future, paid).forEach { invoice -> insert(driver, invoice) }

                assertEquals(listOf(overdue, dueToday), outstanding(driver, cutoff))
                assertEquals(BigDecimal("1234567890123.55"), totalDue(driver, cutoff))
                assertEquals(listOf(paid), settled(driver))

                val settlementTime: LocalDateTime = LocalDateTime.of(2025, 3, 31, 16, 0)
                val changed: Long = driver.execute(
                    null,
                    "UPDATE invoices SET paid_at = ? WHERE reference = ?",
                    2
                ) {
                    (this as JdbcPreparedStatement).bindTimestamp(1, Timestamp.valueOf(settlementTime))
                    bindString(1, overdue.reference)
                }.value
                assertEquals(1L, changed)
                assertEquals(listOf(dueToday), outstanding(driver, cutoff))
                assertEquals(BigDecimal("0.10"), totalDue(driver, cutoff))
                assertEquals(listOf(overdue.copy(paidAt = settlementTime), paid), settled(driver))
            }
        }
    }

    private fun insert(driver: JdbcDriver, invoice: Invoice) {
        val changed: Long = driver.execute(
            null,
            "INSERT INTO invoices (reference, amount, due_date, paid_at) VALUES (?, ?, ?, ?)",
            4
        ) {
            val statement: JdbcPreparedStatement = this as JdbcPreparedStatement
            statement.bindString(0, invoice.reference)
            statement.bindBigDecimal(1, invoice.amount)
            // Date and timestamp extensions take JDBC's one-based positions.
            statement.bindDate(3, Date.valueOf(invoice.dueDate))
            statement.bindTimestamp(4, invoice.paidAt?.let { Timestamp.valueOf(it) })
        }.value
        check(changed == 1L)
    }

    private fun outstanding(driver: JdbcDriver, cutoff: LocalDate): List<Invoice> = driver.executeQuery(
        null,
        """
        SELECT reference, amount, due_date, paid_at FROM invoices
        WHERE paid_at IS NULL AND due_date <= ? ORDER BY reference
        """.trimIndent(),
        mapper = { cursor -> QueryResult.Value(readInvoices(cursor as JdbcCursor)) },
        parameters = 1,
        binders = { (this as JdbcPreparedStatement).bindDate(1, Date.valueOf(cutoff)) }
    ).value

    private fun settled(driver: JdbcDriver): List<Invoice> = driver.executeQuery(
        null,
        """
        SELECT reference, amount, due_date, paid_at FROM invoices
        WHERE paid_at IS NOT NULL ORDER BY reference
        """.trimIndent(),
        mapper = { cursor -> QueryResult.Value(readInvoices(cursor as JdbcCursor)) },
        parameters = 0
    ).value

    private fun totalDue(driver: JdbcDriver, cutoff: LocalDate): BigDecimal = driver.executeQuery(
        null,
        "SELECT SUM(amount) FROM invoices WHERE paid_at IS NULL AND due_date <= ?",
        mapper = { cursor ->
            check(cursor.next().value)
            QueryResult.Value(requireNotNull((cursor as JdbcCursor).getBigDecimal(0)))
        },
        parameters = 1,
        binders = { (this as JdbcPreparedStatement).bindDate(1, Date.valueOf(cutoff)) }
    ).value

    private fun readInvoices(cursor: JdbcCursor): List<Invoice> {
        val invoices: MutableList<Invoice> = mutableListOf()
        while (cursor.next().value) {
            invoices.add(
                Invoice(
                    reference = requireNotNull(cursor.getString(0)),
                    amount = requireNotNull(cursor.getBigDecimal(1)),
                    dueDate = requireNotNull(cursor.getDate(3)).toLocalDate(),
                    paidAt = cursor.getTimestamp(4)?.toLocalDateTime()
                )
            )
        }
        return invoices.toList()
    }

    private data class Invoice(
        val reference: String,
        val amount: BigDecimal,
        val dueDate: LocalDate,
        val paidAt: LocalDateTime? = null
    )
}
