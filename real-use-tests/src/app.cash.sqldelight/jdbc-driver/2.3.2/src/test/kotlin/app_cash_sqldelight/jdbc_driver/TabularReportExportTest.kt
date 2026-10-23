/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.JdbcCursor
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import java.sql.ResultSetMetaData
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC result-set metadata for schema-agnostic tabular inventory report exports.
 */
@Timeout(60)
class TabularReportExportTest {
    @Test
    fun exportsColumnLabelsAndNullableCellsAcrossDifferentReportProjections() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:tabular_report_exports")
        }
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    """
                    CREATE TABLE inventory_report_items (
                        sku VARCHAR(40) PRIMARY KEY,
                        description VARCHAR(200) NOT NULL,
                        supplier VARCHAR(80),
                        quantity BIGINT NOT NULL
                    )
                    """.trimIndent(),
                    0
                )
                save(driver, Item("A-1", "Maker's kit, large", "North", 2L))
                save(driver, Item("B-2", "Replacement \"clip\"", "", 0L))
                save(driver, Item("C-3", "Unassigned\ncomponent", null, 1L))
                save(driver, Item("D-4", "Bulk stock", "North", 20L))
                val exporter: ReportExporter = ReportExporter(driver)
                val lowStockSql: String = """
                    SELECT sku AS "Item", description AS "Description",
                           supplier AS "Supplier", quantity AS "On hand"
                    FROM inventory_report_items WHERE quantity <= ? ORDER BY sku
                """.trimIndent()

                val lowStock: TabularReport = exporter.export(lowStockSql, 1) { bindLong(0, 2L) }
                assertEquals(listOf("Item", "Description", "Supplier", "On hand"), lowStock.headers)
                assertEquals(
                    listOf(
                        listOf("A-1", "Maker's kit, large", "North", "2"),
                        listOf("B-2", "Replacement \"clip\"", "", "0"),
                        listOf("C-3", "Unassigned\ncomponent", null, "1")
                    ),
                    lowStock.rows
                )

                // A different projection must supply its own headers, not reuse the previous shape.
                val totals: TabularReport = exporter.export(
                    """
                    SELECT supplier AS "Supplier", SUM(quantity) AS "Units"
                    FROM inventory_report_items WHERE supplier = 'North' GROUP BY supplier
                    """.trimIndent()
                )
                assertEquals(TabularReport(listOf("Supplier", "Units"), listOf(listOf("North", "22"))), totals)

                // An empty result still contains the column labels needed to render the report.
                assertEquals(
                    TabularReport(lowStock.headers, emptyList()),
                    exporter.export(lowStockSql, 1) { bindLong(0, -1L) }
                )

                val changed: Long = driver.execute(
                    null,
                    "UPDATE inventory_report_items SET quantity = ? WHERE sku = ?",
                    2
                ) {
                    bindLong(0, 12L)
                    bindString(1, "A-1")
                }.value
                assertEquals(1L, changed)
                assertEquals(
                    lowStock.copy(rows = lowStock.rows.drop(1)),
                    exporter.export(lowStockSql, 1) { bindLong(0, 2L) }
                )
            }
        }
    }

    private fun save(driver: JdbcDriver, item: Item) {
        val inserted: Long = driver.execute(
            null,
            "INSERT INTO inventory_report_items (sku, description, supplier, quantity) VALUES (?, ?, ?, ?)",
            4
        ) {
            bindString(0, item.sku)
            bindString(1, item.description)
            bindString(2, item.supplier)
            bindLong(3, item.quantity)
        }.value
        check(inserted == 1L)
    }

    private class ReportExporter(private val driver: JdbcDriver) {
        fun export(
            sql: String,
            parameters: Int = 0,
            binders: (SqlPreparedStatement.() -> Unit)? = null
        ): TabularReport = driver.executeQuery(
            null,
            sql,
            mapper = { cursor ->
                val jdbcCursor: JdbcCursor = cursor as JdbcCursor
                val metadata: ResultSetMetaData = jdbcCursor.resultSet.metaData
                val headers: List<String> = (1..metadata.columnCount).map { column ->
                    metadata.getColumnLabel(column)
                }
                val rows: MutableList<List<String?>> = mutableListOf()
                while (jdbcCursor.next().value) {
                    rows.add(headers.indices.map { column -> jdbcCursor.getString(column) })
                }
                // Copy all cells before the driver closes its JDBC result set and connection.
                QueryResult.Value(TabularReport(headers, rows.toList()))
            },
            parameters = parameters,
            binders = binders
        ).value
    }

    private data class Item(val sku: String, val description: String, val supplier: String?, val quantity: Long)

    private data class TabularReport(val headers: List<String>, val rows: List<List<String?>>)
}
