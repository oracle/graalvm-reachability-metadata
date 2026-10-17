/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.await.ExperimentalAwaitAllApi
import arrow.fx.coroutines.await.awaitAll
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's `awaitAll` DSL: `async` tasks registered in an `AwaitAllScope` are awaited
 * together on the first `await`, and a failing task cancels its siblings and fails the block.
 *
 * A checkout page loads the customer, the cart and the shipping rate of an order from an in-memory
 * H2 database through a HikariCP pool, each read running as its own task on `Dispatchers.IO`.
 */
@OptIn(ExperimentalAwaitAllApi::class)
public class CheckoutSummaryAwaitAllTest {

    /** The checkout page an application renders for one order. */
    public data class CheckoutSummary(
        val customer: String,
        val items: Int,
        val subtotalCents: Long,
        val shippingCents: Long,
    ) {
        val totalCents: Long
            get() = subtotalCents + shippingCents
    }

    /** One line of a cart. */
    public data class CartLine(val sku: String, val quantity: Int, val unitPriceCents: Long)

    /** Checkout reads that each borrow their own pooled connection. */
    public class CheckoutStore(private val pool: HikariDataSource) {

        public fun customerName(orderId: Long): String =
            single(
                """
                SELECT c.name FROM customer c JOIN orders o ON o.customer_id = c.id
                WHERE o.id = ?
                """.trimIndent(),
                orderId,
            ) { it.getString(1) }

        public fun cart(orderId: Long): List<CartLine> =
            pool.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT sku, quantity, unit_price_cents FROM cart_line WHERE order_id = ? ORDER BY sku",
                ).use { statement ->
                    statement.setLong(1, orderId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(CartLine(rows.getString(1), rows.getInt(2), rows.getLong(3)))
                            }
                        }
                    }
                }
            }

        public fun shippingCents(orderId: Long): Long =
            single(
                """
                SELECT r.cents FROM shipping_rate r JOIN orders o ON o.region = r.region
                WHERE o.id = ?
                """.trimIndent(),
                orderId,
            ) { it.getLong(1) }

        /** Reads a stock table that this deployment has not migrated yet. */
        public fun reservedStock(orderId: Long): Int =
            single("SELECT COUNT(*) FROM stock_reservation WHERE order_id = ?", orderId) {
                it.getInt(1)
            }

        private fun <T> single(sql: String, orderId: Long, read: (ResultSet) -> T): T =
            pool.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, orderId)
                    statement.executeQuery().use { rows ->
                        check(rows.next()) { "No row for order $orderId" }
                        read(rows)
                    }
                }
            }
    }

    /** The handles of one page load, kept so the test can observe every task after the first await. */
    public class PageLoad(
        public val customer: Deferred<String>,
        public val cart: Deferred<List<CartLine>>,
        public val shipping: Deferred<Long>,
        public val summary: CheckoutSummary,
        public val siblingsDoneAfterFirstAwait: Boolean,
    )

    private lateinit var pool: HikariDataSource
    private lateinit var store: CheckoutStore

    @BeforeEach
    public fun openDatabase() {
        val config: HikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:checkout-${System.nanoTime()};DB_CLOSE_DELAY=-1"
            maximumPoolSize = 4
        }
        pool = HikariDataSource(config)
        pool.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE customer (id BIGINT PRIMARY KEY, name VARCHAR(64))")
                statement.execute(
                    "CREATE TABLE orders (id BIGINT PRIMARY KEY, customer_id BIGINT, region VARCHAR(8))",
                )
                statement.execute(
                    """
                    CREATE TABLE cart_line (
                        order_id BIGINT, sku VARCHAR(16), quantity INT, unit_price_cents BIGINT
                    )
                    """.trimIndent(),
                )
                statement.execute("CREATE TABLE shipping_rate (region VARCHAR(8) PRIMARY KEY, cents BIGINT)")
                statement.execute("INSERT INTO customer VALUES (1, 'Ada Lovelace'), (2, 'Alan Turing')")
                statement.execute("INSERT INTO orders VALUES (100, 1, 'EU'), (200, 2, 'US')")
                statement.execute(
                    """
                    INSERT INTO cart_line VALUES
                        (100, 'BOOK-1', 2, 1500), (100, 'PEN-7', 3, 250),
                        (200, 'LAMP-2', 1, 4200)
                    """.trimIndent(),
                )
                statement.execute("INSERT INTO shipping_rate VALUES ('EU', 499), ('US', 899)")
            }
        }
        store = CheckoutStore(pool)
    }

    @AfterEach
    public fun closeDatabase() {
        pool.close()
    }

    /** Loads the page with the suspending `awaitAll`, which opens its own coroutine scope. */
    private suspend fun loadPage(orderId: Long): PageLoad = awaitAll {
        val customer: Deferred<String> = async(Dispatchers.IO) { store.customerName(orderId) }
        val cart: Deferred<List<CartLine>> = async(Dispatchers.IO) { store.cart(orderId) }
        val shipping: Deferred<Long> = async(Dispatchers.IO) { store.shippingCents(orderId) }

        val name: String = customer.await()
        val siblingsDone: Boolean = cart.isCompleted && shipping.isCompleted
        val lines: List<CartLine> = cart.await()
        val summary = CheckoutSummary(
            customer = name,
            items = lines.sumOf { it.quantity },
            subtotalCents = lines.sumOf { it.quantity * it.unitPriceCents },
            shippingCents = shipping.await(),
        )
        PageLoad(customer, cart, shipping, summary, siblingsDone)
    }

    @Test
    public fun firstAwaitWaitsForEveryRegisteredRead(): Unit = runBlocking {
        val page: PageLoad = loadPage(100L)

        assertTrue(page.siblingsDoneAfterFirstAwait, "awaiting the customer awaited the cart and rate")
        assertEquals(
            CheckoutSummary(customer = "Ada Lovelace", items = 5, subtotalCents = 3750, shippingCents = 499),
            page.summary,
        )
        assertEquals(4249L, page.summary.totalCents)
        assertTrue(page.customer.isCompleted && page.cart.isCompleted && page.shipping.isCompleted)
    }

    @Test
    public fun failingReadCancelsSiblingsAndFailsThePage(): Unit = runBlocking {
        val holdStarted = CompletableDeferred<Unit>()
        val holdCancelled = AtomicBoolean(false)
        lateinit var hold: Deferred<Unit>

        val failure: SQLException = assertThrows(SQLException::class.java) {
            runBlocking {
                awaitAll {
                    hold = async(Dispatchers.IO) {
                        try {
                            holdStarted.complete(Unit)
                            awaitCancellation()
                        } finally {
                            holdCancelled.set(true)
                        }
                    }
                    val customer: Deferred<String> = async(Dispatchers.IO) { store.customerName(100L) }
                    val reserved: Deferred<Int> = async(Dispatchers.IO) {
                        holdStarted.await()
                        store.reservedStock(100L)
                    }
                    customer.await() to reserved.await()
                }
            }
        }

        assertTrue(failure.message!!.contains("STOCK_RESERVATION", ignoreCase = true), failure.message)
        assertTrue(holdCancelled.get(), "the sibling waiting on the page was cancelled")
        assertTrue(hold.isCancelled)
    }

    @Test
    public fun scopeExtensionBatchesReadsInsideAnExistingScope(): Unit = runBlocking {
        val summaries: Map<Long, CheckoutSummary> = coroutineScope {
            listOf(100L, 200L).associateWith { orderId ->
                this.awaitAll {
                    val customer: Deferred<String> = async(Dispatchers.IO) { store.customerName(orderId) }
                    val cart: Deferred<List<CartLine>> = async(Dispatchers.IO) { store.cart(orderId) }
                    val shipping: Deferred<Long> = async(Dispatchers.IO) { store.shippingCents(orderId) }
                    val lines: List<CartLine> = cart.await()
                    assertTrue(customer.isCompleted && shipping.isCompleted)
                    CheckoutSummary(
                        customer = customer.await(),
                        items = lines.sumOf { it.quantity },
                        subtotalCents = lines.sumOf { it.quantity * it.unitPriceCents },
                        shippingCents = shipping.await(),
                    )
                }
            }
        }

        assertEquals(setOf(100L, 200L), summaries.keys)
        assertEquals("Alan Turing", summaries.getValue(200L).customer)
        assertEquals(1, summaries.getValue(200L).items)
        assertEquals(5099L, summaries.getValue(200L).totalCents)
        assertEquals(4249L, summaries.getValue(100L).totalCents)
        assertFalse(summaries.values.any { it.shippingCents == 0L })
    }
}
