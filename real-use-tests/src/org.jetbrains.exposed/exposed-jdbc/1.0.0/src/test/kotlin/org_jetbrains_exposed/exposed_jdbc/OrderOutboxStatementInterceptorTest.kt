/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test

/**
 * Statement interceptors and raw SQL execution: an order service stages domain events in
 * transaction user data and publishes them from `StatementInterceptor` commit and rollback
 * hooks, reports through parameterized `exec` and `execInBatch`, and retries a failed
 * transaction with `maxAttempts`.
 */
class OrderOutboxStatementInterceptorTest {

    object Orders : Table("orders") {
        val id = integer("id").autoIncrement()
        val sku = varchar("sku", 32)
        val quantity = integer("quantity")

        override val primaryKey = PrimaryKey(id)
    }

    /** Publishes staged events only once the transaction that staged them has committed. */
    class OutboxPublisher : StatementInterceptor {
        val published: MutableList<String> = mutableListOf()
        val discarded: MutableList<String> = mutableListOf()
        val executed: MutableList<String> = mutableListOf()
        val timeouts: MutableList<Int?> = mutableListOf()
        private var staged: List<String> = emptyList()

        override fun afterExecution(
            transaction: Transaction,
            contexts: List<StatementContext>,
            executedStatement: PreparedStatementApi,
        ) {
            contexts.forEach { context ->
                val tables: String = context.statement.targets.joinToString(",") { it.tableName }
                executed += "${context.statement.type} $tables"
            }
            timeouts += (executedStatement as JdbcPreparedStatementApi).timeout
        }

        override fun beforeCommit(transaction: Transaction) {
            staged = transaction.getUserData(PENDING_EVENTS).orEmpty().toList()
        }

        override fun afterCommit(transaction: Transaction) {
            published += staged
            staged = emptyList()
        }

        override fun beforeRollback(transaction: Transaction) {
            staged = transaction.getUserData(PENDING_EVENTS).orEmpty().toList()
        }

        override fun afterRollback(transaction: Transaction) {
            discarded += staged
            staged = emptyList()
        }
    }

    @Test
    fun publishesOrderEventsOnlyAfterCommitAndReportsThroughRawSql() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:order_outbox;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
        )
        transaction(database) { SchemaUtils.create(Orders) }

        val publisher = OutboxPublisher()

        fun placeOrder(sku: String, quantity: Int, paymentAccepted: Boolean) {
            transaction(database) {
                registerInterceptor(publisher)
                queryTimeout = 7
                Orders.insert {
                    it[Orders.sku] = sku
                    it[Orders.quantity] = quantity
                }
                getOrCreate(PENDING_EVENTS) { mutableListOf() } += "OrderPlaced:$sku:$quantity"
                check(paymentAccepted) { "payment declined for $sku" }
            }
        }

        placeOrder("widget", 3, paymentAccepted = true)
        placeOrder("gear", 1, paymentAccepted = true)
        assertThatThrownBy { placeOrder("gadget", 5, paymentAccepted = false) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("payment declined for gadget")

        assertThat(publisher.published).containsExactly("OrderPlaced:widget:3", "OrderPlaced:gear:1")
        assertThat(publisher.discarded).containsExactly("OrderPlaced:gadget:5")
        assertThat(publisher.executed).containsExactly("INSERT orders", "INSERT orders", "INSERT orders")
        assertThat(publisher.timeouts).containsOnly(7)

        val report: List<Pair<String, Int>> = transaction(database) {
            execInBatch(
                listOf(
                    "UPDATE orders SET quantity = quantity + 1 WHERE sku = 'gear'",
                    "INSERT INTO orders (sku, quantity) VALUES ('bolt', 10)",
                ),
            )
            exec(
                """
                SELECT sku, SUM(quantity) AS total
                FROM orders
                WHERE quantity >= ? AND sku <> ?
                GROUP BY sku
                ORDER BY sku
                """.trimIndent(),
                listOf(IntegerColumnType() to 2, VarCharColumnType(32) to "widget"),
                StatementType.SELECT,
            ) { resultSet ->
                generateSequence {
                    if (resultSet.next()) resultSet.getString("sku") to resultSet.getInt("total") else null
                }.toList()
            }.orEmpty()
        }
        assertThat(report).containsExactly("bolt" to 10, "gear" to 2)

        var attempts = 0
        val retriedSkus: List<String> = transaction(database) {
            maxAttempts = 3
            minRetryDelay = 0
            maxRetryDelay = 0
            attempts++
            Orders.insert {
                it[sku] = "nut"
                it[quantity] = 4
            }
            if (attempts == 1) {
                exec("INSERT INTO orders (sku, quantity) VALUES ('nut', NULL)")
            }
            Orders.selectAll().where { Orders.sku eq "nut" }.map { it[Orders.sku] }
        }
        assertThat(attempts).isEqualTo(2)
        assertThat(retriedSkus).containsExactly("nut")

        val statistics: Pair<Int, Int> = transaction(database) {
            debug = true
            val skus: Int = Orders.selectAll().count().toInt()
            val widgets: Int = Orders.selectAll().where { Orders.sku eq "widget" }.count().toInt()
            assertThat(statementStats.keys).anySatisfy { sql -> assertThat(sql).contains("WHERE ORDERS.SKU = ?") }
            assertThat(statementCount).isEqualTo(2)
            skus to widgets
        }
        assertThat(statistics).isEqualTo(4 to 1)
    }

    companion object {
        val PENDING_EVENTS: Key<MutableList<String>> = Key()
    }
}
