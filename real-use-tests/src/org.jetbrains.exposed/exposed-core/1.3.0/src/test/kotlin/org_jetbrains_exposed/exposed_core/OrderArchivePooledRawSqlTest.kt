/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Connection

/**
 * Pooled DataSource connectivity and raw SQL execution: an order service runs Exposed over two
 * HikariCP pools, overrides transaction isolation and read-only mode, issues parameterized raw
 * SQL through `exec`, loads a batch with `execInBatch` and pages orders into an archive database
 * with `fetchBatchedResults`.
 */
class OrderArchivePooledRawSqlTest {

    object Orders : Table("orders") {
        val id: Column<Int> = integer("id").autoIncrement()
        val customer: Column<String> = varchar("customer", 40)
        val totalCents: Column<Int> = integer("total_cents")

        override val primaryKey = PrimaryKey(id)
    }

    object ArchivedOrders : Table("archived_orders") {
        val orderId: Column<Int> = integer("order_id")
        val customer: Column<String> = varchar("customer", 40)
        val totalCents: Column<Int> = integer("total_cents")
        val archiveBatch: Column<Int> = integer("archive_batch")

        override val primaryKey = PrimaryKey(orderId)
    }

    data class CustomerSpend(val customer: String, val orders: Int, val total: BigDecimal)

    private fun pool(name: String): HikariDataSource {
        val config: HikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            poolName = "$name-pool"
            maximumPoolSize = 2
            isAutoCommit = false
        }
        return HikariDataSource(config)
    }

    @Test
    fun archivesOrdersBetweenPooledDatabasesUsingRawSqlAndBatchedPaging() {
        pool("orders_ops").use { opsPool: HikariDataSource ->
            pool("orders_archive").use { archivePool: HikariDataSource ->
                val ops: Database = Database.connect(
                    opsPool,
                    databaseConfig = DatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED },
                )
                val archive: Database = Database.connect(archivePool)

                assertThat(ops.vendor).isEqualTo("H2")
                assertThat(ops.dialect).isInstanceOf(H2Dialect::class.java)
                assertThat(ops.url).startsWith("jdbc:h2:mem:orders_ops")

                transaction(ops) {
                    SchemaUtils.create(Orders)
                    val jdbc: Connection = connection.connection as Connection
                    assertThat(jdbc.transactionIsolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
                    assertThat(opsPool.hikariPoolMXBean.activeConnections).isEqualTo(1)

                    execInBatch(
                        listOf(
                            "INSERT INTO orders (customer, total_cents) VALUES ('acme', 1250)",
                            "INSERT INTO orders (customer, total_cents) VALUES ('globex', 4000)",
                            "INSERT INTO orders (customer, total_cents) VALUES ('acme', 750)",
                        ),
                    )
                    Orders.batchInsert(
                        listOf("initech" to 990, "globex" to 1010, "acme" to 3000, "umbrella" to 5),
                    ) { (customer: String, cents: Int) ->
                        this[Orders.customer] = customer
                        this[Orders.totalCents] = cents
                    }
                }
                assertThat(opsPool.hikariPoolMXBean.activeConnections).isZero()

                val spend: List<CustomerSpend> = transaction(ops) {
                    exec(
                        """
                        SELECT customer, COUNT(*) AS orders, SUM(total_cents) / 100.0 AS total
                        FROM orders
                        WHERE total_cents >= ? AND customer <> ?
                        GROUP BY customer
                        ORDER BY customer
                        """.trimIndent(),
                        listOf(IntegerColumnType() to 100, VarCharColumnType(40) to "initech"),
                    ) { rs ->
                        val rows: MutableList<CustomerSpend> = mutableListOf()
                        while (rs.next()) {
                            rows += CustomerSpend(
                                rs.getString("customer"),
                                rs.getInt("orders"),
                                rs.getBigDecimal("total").setScale(2),
                            )
                        }
                        rows
                    } ?: emptyList()
                }
                assertThat(spend).containsExactly(
                    CustomerSpend("acme", 3, BigDecimal("50.00")),
                    CustomerSpend("globex", 2, BigDecimal("50.10")),
                )

                transaction(ops, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                    val jdbc: Connection = connection.connection as Connection
                    assertThat(jdbc.transactionIsolation).isEqualTo(Connection.TRANSACTION_SERIALIZABLE)
                    exec(
                        "UPDATE orders SET total_cents = total_cents + ? WHERE customer = ?",
                        listOf(IntegerColumnType() to 95, VarCharColumnType(40) to "umbrella"),
                        explicitStatementType = StatementType.UPDATE,
                    )
                }

                val umbrellaTotal: BigDecimal? = transaction(ops, readOnly = true) {
                    assertThat(connection.readOnly).isTrue()
                    exec(
                        "SELECT CAST(total_cents AS DECIMAL(10, 2)) / 100 FROM orders WHERE customer = ?",
                        listOf(VarCharColumnType(40) to "umbrella"),
                    ) { rs -> if (rs.next()) rs.getBigDecimal(1) else null }
                }
                assertThat(umbrellaTotal).isEqualByComparingTo("1.00")

                transaction(archive) {
                    SchemaUtils.create(ArchivedOrders)
                }

                val batchSizes: List<Int> = transaction(ops) {
                    val sizes: MutableList<Int> = mutableListOf()
                    Orders.selectAll()
                        .fetchBatchedResults(batchSize = 3, sortOrder = SortOrder.ASC)
                        .forEachIndexed { batchNumber: Int, page: Iterable<ResultRow> ->
                            val rows: List<ResultRow> = page.toList()
                            sizes += rows.size
                            transaction(archive) {
                                ArchivedOrders.batchInsert(rows) { row ->
                                    this[ArchivedOrders.orderId] = row[Orders.id]
                                    this[ArchivedOrders.customer] = row[Orders.customer]
                                    this[ArchivedOrders.totalCents] = row[Orders.totalCents]
                                    this[ArchivedOrders.archiveBatch] = batchNumber + 1
                                }
                            }
                        }
                    exec("DELETE FROM orders", explicitStatementType = StatementType.DELETE)
                    sizes
                }
                assertThat(batchSizes).containsExactly(3, 3, 1)

                val archivedByBatch: Map<Int, List<Int>> = transaction(archive) {
                    ArchivedOrders.selectAll()
                        .orderBy(ArchivedOrders.orderId)
                        .groupBy({ it[ArchivedOrders.archiveBatch] }, { it[ArchivedOrders.orderId] })
                }
                assertThat(archivedByBatch).isEqualTo(
                    mapOf(1 to listOf(1, 2, 3), 2 to listOf(4, 5, 6), 3 to listOf(7)),
                )

                val archivedCents: BigDecimal? = transaction(archive) {
                    exec(
                        "SELECT SUM(total_cents) FROM archived_orders WHERE archive_batch <= ?",
                        listOf(IntegerColumnType() to 3),
                    ) { rs -> if (rs.next()) rs.getBigDecimal(1) else null }
                }
                assertThat(archivedCents).isEqualByComparingTo("11100")
                assertThat(transaction(ops) { Orders.selectAll().empty() }).isTrue()
                assertThat(opsPool.hikariPoolMXBean.activeConnections).isZero()
                assertThat(archivePool.hikariPoolMXBean.activeConnections).isZero()
            }
        }
    }
}
