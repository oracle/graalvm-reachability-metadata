/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.withSuspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.UUID

/**
 * Coroutine transactions: an order fulfilment service runs its database work from suspend functions
 * with `suspendTransaction`, `newSuspendedTransaction` on a dispatcher, parallel
 * `suspendedTransactionAsync` reads, `withSuspendTransaction` hops and an
 * `inTopLevelSuspendTransaction` audit trail that outlives a rolled-back outer transaction.
 */
class FulfilmentCoroutineTransactionsTest {

    object Orders : Table("orders") {
        val id = integer("id").autoIncrement()
        val warehouse = varchar("warehouse", 16)
        val totalCents = long("total_cents")
        val status = varchar("status", 16)

        override val primaryKey = PrimaryKey(id)
    }

    object AuditLog : Table("audit_log") {
        val id = integer("id").autoIncrement()
        val message = varchar("message", 128)

        override val primaryKey = PrimaryKey(id)
    }

    data class NewOrder(val warehouse: String, val totalCents: Long)

    /** A fulfilment service whose every operation is a suspend function backed by Exposed. */
    class FulfilmentService(private val database: Database) {

        suspend fun importOrders(orders: List<NewOrder>): Int = suspendTransaction(database) {
            Orders.batchInsert(orders) { order ->
                this[Orders.warehouse] = order.warehouse
                this[Orders.totalCents] = order.totalCents
                this[Orders.status] = "NEW"
            }.size
        }

        suspend fun revenueByWarehouse(warehouses: List<String>): Map<String, Long> {
            val reads = warehouses.map { warehouse ->
                suspendedTransactionAsync(Dispatchers.IO, database, readOnly = true) {
                    warehouse to Orders.selectAll()
                        .where { Orders.warehouse eq warehouse }
                        .sumOf { it[Orders.totalCents] }
                }
            }
            return reads.awaitAll().toMap()
        }

        /** Ships one warehouse's orders and returns the isolation level and the transaction identity check. */
        suspend fun shipWarehouse(warehouse: String): ShipmentResult = newSuspendedTransaction(
            Dispatchers.Default,
            database,
            transactionIsolation = Connection.TRANSACTION_SERIALIZABLE,
        ) {
            val outer: JdbcTransaction = this
            val shipped = Orders.update({ Orders.warehouse eq warehouse }) { it[Orders.status] = "SHIPPED" }
            val (sameTransaction, visibleShipped) = withSuspendTransaction(Dispatchers.IO) {
                val stillCurrent = this === outer && TransactionManager.current() === outer
                stillCurrent to Orders.selectAll().where { Orders.status eq "SHIPPED" }.count()
            }
            ShipmentResult(shipped, sameTransaction, visibleShipped, connection.transactionIsolation)
        }

        /** Cancels an order but fails mid-way; the audit entry is written in its own top-level transaction. */
        suspend fun cancelWithFailure(orderId: Int): Nothing = suspendTransaction(database) {
            Orders.update({ Orders.id eq orderId }) { it[Orders.status] = "CANCELLED" }
            inTopLevelSuspendTransaction(db = database) {
                AuditLog.insert { it[AuditLog.message] = "cancel requested for order $orderId" }
            }
            throw IllegalStateException("payment provider rejected refund for order $orderId")
        }

        suspend fun statuses(): List<String> = suspendTransaction(database) {
            Orders.selectAll().orderBy(Orders.id to SortOrder.ASC).map { it[Orders.status] }
        }

        suspend fun auditMessages(): List<String> = suspendTransaction(database) {
            AuditLog.selectAll().orderBy(AuditLog.id to SortOrder.ASC).map { it[AuditLog.message] }
        }
    }

    data class ShipmentResult(val shipped: Int, val sameTransaction: Boolean, val visibleShipped: Long, val isolation: Int)

    @Test
    fun runsFulfilmentWorkInsideCoroutineTransactions() {
        val database = Database.connect(
            url = "jdbc:h2:mem:fulfilment-${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val service = FulfilmentService(database)

        runBlocking {
            suspendTransaction(database) { SchemaUtils.create(Orders, AuditLog) }

            val imported = service.importOrders(
                listOf(
                    NewOrder("BER", 1_200L),
                    NewOrder("BER", 800L),
                    NewOrder("LIS", 4_500L),
                    NewOrder("OSL", 300L),
                    NewOrder("LIS", 500L),
                ),
            )
            assertThat(imported).isEqualTo(5)

            assertThat(service.revenueByWarehouse(listOf("BER", "LIS", "OSL", "ZRH")))
                .containsExactlyInAnyOrderEntriesOf(mapOf("BER" to 2_000L, "LIS" to 5_000L, "OSL" to 300L, "ZRH" to 0L))

            val shipment = service.shipWarehouse("LIS")
            assertThat(shipment.shipped).isEqualTo(2)
            assertThat(shipment.sameTransaction).isTrue()
            assertThat(shipment.visibleShipped).isEqualTo(2L)
            assertThat(shipment.isolation).isEqualTo(Connection.TRANSACTION_SERIALIZABLE)

            val failure: Throwable? = runCatching { service.cancelWithFailure(1) }.exceptionOrNull()
            assertThat(failure)
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("payment provider rejected refund for order 1")

            assertThat(service.statuses()).containsExactly("NEW", "NEW", "SHIPPED", "NEW", "SHIPPED")
            assertThat(service.auditMessages()).containsExactly("cancel requested for order 1")
        }
    }
}
