/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC repeatable-read transaction snapshots for consistent stock reports.
 */
@Timeout(60)
class StockSnapshotReportTest {
    @Test
    fun keepsReportDetailsAndTotalsConsistentAcrossAnInterleavedCommittedRestock() {
        val config: HikariConfig = HikariConfig().apply {
            dataSource = JdbcDataSource().apply {
                setURL("jdbc:h2:mem:stock_snapshot_report")
            }
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            maximumPoolSize = 2
            minimumIdle = 1
            connectionTimeout = 10_000L
            initializationFailTimeout = 10_000L
        }
        HikariDataSource(config).use { pool: HikariDataSource ->
            pool.asJdbcDriver().use { reader: JdbcDriver ->
                pool.asJdbcDriver().use { writer: JdbcDriver ->
                    writer.execute(
                        null,
                        "CREATE TABLE report_stock (sku VARCHAR(40) PRIMARY KEY, quantity BIGINT NOT NULL)",
                        0
                    )
                    writer.execute(null, "INSERT INTO report_stock VALUES ('coffee', 10), ('tea', 20)", 0)
                    val reports: StockReports = StockReports(reader)
                    val original: StockReport = StockReport(
                        listOf(StockLine("coffee", 10L), StockLine("tea", 20L)),
                        30L
                    )
                    val restocked: StockReport = StockReport(
                        listOf(StockLine("coffee", 15L), StockLine("tea", 25L)),
                        40L
                    )

                    val first: StockReport = reports.generate {
                        // The detail query established a snapshot. Commit a separate request
                        // before the total query, without depending on scheduling or sleeps.
                        val changed: Long = writer.execute(
                            null,
                            "UPDATE report_stock SET quantity = quantity + ?",
                            1
                        ) { bindLong(0, 5L) }.value
                        assertEquals(2L, changed)
                        assertEquals(restocked.lines, StockReports(writer).lines())
                    }
                    assertEquals(original, first)
                    assertEquals(first.lines.sumOf { line -> line.quantity }, first.total)

                    // Ending the report transaction must release the old snapshot.
                    assertEquals(restocked.lines, reports.lines())
                    assertEquals(restocked, reports.generate {})
                }
            }
        }
    }

    private class StockReports(private val jdbc: JdbcDriver) : TransacterImpl(jdbc) {
        fun generate(betweenQueries: () -> Unit): StockReport = transactionWithResult {
            val details: List<StockLine> = lines()
            betweenQueries()
            StockReport(details, total())
        }

        fun lines(): List<StockLine> = jdbc.executeQuery(
            null,
            "SELECT sku, quantity FROM report_stock ORDER BY sku",
            mapper = { cursor ->
                val rows: MutableList<StockLine> = mutableListOf()
                while (cursor.next().value) {
                    rows.add(
                        StockLine(requireNotNull(cursor.getString(0)), requireNotNull(cursor.getLong(1)))
                    )
                }
                QueryResult.Value(rows.toList())
            },
            parameters = 0
        ).value

        private fun total(): Long = jdbc.executeQuery(
            null,
            "SELECT SUM(quantity) FROM report_stock",
            mapper = { cursor ->
                check(cursor.next().value)
                QueryResult.Value(requireNotNull(cursor.getLong(0)))
            },
            parameters = 0
        ).value
    }

    private data class StockLine(val sku: String, val quantity: Long)

    private data class StockReport(val lines: List<StockLine>, val total: Long)
}
