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
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC nested-transaction completion callbacks for checkout reservations.
 */
@Timeout(60)
class CheckoutCompletionCallbacksTest {
    @Test
    fun notifiesCustomersOnlyAfterTheWholeCheckoutCommits() {
        val source: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:checkout_completion_callbacks")
        }
        source.connection.use {
            source.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(null, "CREATE TABLE stock (sku VARCHAR(40) PRIMARY KEY, available BIGINT NOT NULL)", 0)
                driver.execute(null, "INSERT INTO stock VALUES ('coffee', 5)", 0)
                driver.execute(null, "CREATE TABLE purchases (id BIGINT PRIMARY KEY, quantity BIGINT NOT NULL)", 0)
                val inventory: Inventory = Inventory(driver)
                val checkout: Checkout = Checkout(driver, inventory)

                checkout.place(101L, 2L) {
                    // The nested reservation has finished, but checkout is still pending.
                    assertEquals(emptyList<Long>(), inventory.reservationNotices)
                    assertEquals(emptyList<Receipt>(), checkout.receipts)
                    true
                }
                assertEquals(listOf(101L), checkout.purchaseIds())
                assertEquals(3L, inventory.available())
                assertEquals(listOf(101L), inventory.reservationNotices)
                assertEquals(listOf(Receipt(101L, 3L)), checkout.receipts)
                assertEquals(emptyList<Long>(), inventory.cancelledReservations)

                checkout.place(102L, 2L) {
                    // Cancelling after reservation must discard both repositories' writes.
                    assertEquals(listOf(101L), inventory.reservationNotices)
                    assertEquals(listOf(Receipt(101L, 3L)), checkout.receipts)
                    false
                }
                assertEquals(listOf(101L), checkout.purchaseIds())
                assertEquals(3L, inventory.available())
                assertEquals(listOf(101L), inventory.reservationNotices)
                assertEquals(listOf(Receipt(101L, 3L)), checkout.receipts)
                assertEquals(listOf(102L), inventory.cancelledReservations)

                // The next checkout must neither replay nor inherit the cancelled one's callbacks.
                checkout.place(103L, 1L) { true }
                assertEquals(listOf(101L, 103L), checkout.purchaseIds())
                assertEquals(2L, inventory.available())
                assertEquals(listOf(101L, 103L), inventory.reservationNotices)
                assertEquals(listOf(Receipt(101L, 3L), Receipt(103L, 2L)), checkout.receipts)
                assertEquals(listOf(102L), inventory.cancelledReservations)
            }
        }
    }

    private class Checkout(
        private val jdbc: JdbcDriver,
        private val inventory: Inventory
    ) : TransacterImpl(jdbc) {
        val receipts: MutableList<Receipt> = mutableListOf()

        fun place(id: Long, quantity: Long, confirm: () -> Boolean) {
            transaction {
                inventory.reserve(id, quantity)
                val inserted: Long = jdbc.execute(
                    null,
                    "INSERT INTO purchases (id, quantity) VALUES (?, ?)",
                    2
                ) {
                    bindLong(0, id)
                    bindLong(1, quantity)
                }.value
                check(inserted == 1L)
                // Read committed stock for the receipt, not the tentative reservation.
                afterCommit { receipts.add(Receipt(id, inventory.available())) }
                if (!confirm()) {
                    rollback()
                }
            }
        }

        fun purchaseIds(): List<Long> = jdbc.executeQuery(
            null,
            "SELECT id FROM purchases ORDER BY id",
            mapper = { cursor ->
                val ids: MutableList<Long> = mutableListOf()
                while (cursor.next().value) {
                    ids.add(requireNotNull(cursor.getLong(0)))
                }
                QueryResult.Value(ids.toList())
            },
            parameters = 0
        ).value
    }

    private class Inventory(private val jdbc: JdbcDriver) : TransacterImpl(jdbc) {
        val reservationNotices: MutableList<Long> = mutableListOf()
        val cancelledReservations: MutableList<Long> = mutableListOf()

        fun reserve(orderId: Long, quantity: Long) {
            require(quantity > 0L)
            transaction {
                val changed: Long = jdbc.execute(
                    null,
                    "UPDATE stock SET available = available - ? WHERE sku = 'coffee' AND available >= ?",
                    2
                ) {
                    bindLong(0, quantity)
                    bindLong(1, quantity)
                }.value
                check(changed == 1L) { "Insufficient stock" }
                // These callbacks belong to the outer checkout's outcome, not this method's return.
                afterCommit { reservationNotices.add(orderId) }
                afterRollback { cancelledReservations.add(orderId) }
            }
        }

        fun available(): Long = jdbc.executeQuery(
            null,
            "SELECT available FROM stock WHERE sku = 'coffee'",
            mapper = { cursor ->
                check(cursor.next().value)
                QueryResult.Value(requireNotNull(cursor.getLong(0)))
            },
            parameters = 0
        ).value
    }

    private data class Receipt(val orderId: Long, val remainingStock: Long)
}
