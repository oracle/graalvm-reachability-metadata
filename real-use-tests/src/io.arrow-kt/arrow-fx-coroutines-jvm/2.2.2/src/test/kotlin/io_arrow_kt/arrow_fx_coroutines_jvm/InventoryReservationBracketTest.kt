/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.bracket
import arrow.fx.coroutines.bracketCase
import arrow.fx.coroutines.guarantee
import arrow.fx.coroutines.onCancel
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's bracket combinators: `bracket`, `bracketCase`, `guarantee` and `onCancel`
 * releasing what an operation acquired whether it completes, fails or is cancelled.
 *
 * An inventory service reserves stock in an in-memory H2 database behind a HikariCP pool. Each
 * reservation borrows a connection with `bracket`, opens a transaction with `bracketCase` that
 * commits on `ExitCase.Completed` and rolls back otherwise, writes an audit line with `guarantee`,
 * and records abandoned checkouts with `onCancel`.
 */
public class InventoryReservationBracketTest {

    /** Stock operations that run on a connection owned by an enclosing transaction. */
    public class Stock(private val connection: Connection) {

        public fun reserve(sku: String, quantity: Int) {
            connection.prepareStatement(
                "UPDATE stock SET available = available - ? WHERE sku = ?",
            ).use {
                it.setInt(1, quantity)
                it.setString(2, sku)
                check(it.executeUpdate() == 1) { "unknown sku $sku" }
            }
            check(available(sku) >= 0) { "insufficient stock for $sku" }
        }

        public fun available(sku: String): Int =
            connection.prepareStatement("SELECT available FROM stock WHERE sku = ?").use {
                it.setString(1, sku)
                it.executeQuery().use { rows ->
                    check(rows.next()) { "unknown sku $sku" }
                    rows.getInt(1)
                }
            }
    }

    private lateinit var pool: HikariDataSource

    /** Lifecycle and audit events in the order the service reported them. */
    private val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @BeforeEach
    fun openPool() {
        val config: HikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:inventory-${System.nanoTime()};DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = 2
            poolName = "inventory"
        }
        pool = HikariDataSource(config)
        pool.connection.use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE stock(sku VARCHAR(16) PRIMARY KEY, available INT NOT NULL)")
                it.execute("INSERT INTO stock(sku, available) VALUES ('lamp', 5), ('desk', 1)")
            }
        }
    }

    @AfterEach
    fun closePool() {
        pool.close()
    }

    /** Borrows a pooled connection for [use] and returns it to the pool however [use] ends. */
    private suspend fun <A> withConnection(use: suspend (Connection) -> A): A =
        bracket(
            acquire = { pool.connection.also { events += "connection borrowed" } },
            use = use,
            release = { connection ->
                connection.close()
                events += "connection returned"
            },
        )

    /** Runs [block] in a transaction that commits only on `ExitCase.Completed`. */
    private suspend fun <A> inTransaction(block: suspend (Stock) -> A): A = withConnection { connection ->
        bracketCase(
            acquire = { connection.apply { autoCommit = false } },
            use = { tx -> block(Stock(tx)) },
            release = { tx, exitCase ->
                when (exitCase) {
                    ExitCase.Completed -> tx.commit().also { events += "committed" }
                    is ExitCase.Cancelled -> tx.rollback().also { events += "rolled back: cancelled" }
                    is ExitCase.Failure -> tx.rollback().also {
                        events += "rolled back: ${exitCase.failure.message}"
                    }
                }
                tx.autoCommit = true
            },
        )
    }

    /** Reserves stock and writes an audit line whether or not the reservation succeeded. */
    private suspend fun reserve(orderId: String, sku: String, quantity: Int): Unit =
        guarantee(
            fa = { inTransaction { stock -> stock.reserve(sku, quantity) } },
            finalizer = { events += "audit $orderId" },
        )

    private fun availableNow(sku: String): Int = pool.connection.use { Stock(it).available(sku) }

    @Test
    fun completedAndFailedReservationsCommitOrRollBackAndReturnTheirConnection() {
        runBlocking {
            reserve("order-1", "lamp", 2)
            val rejected: Result<Unit> = runCatching { reserve("order-2", "desk", 3) }
            assertEquals("insufficient stock for desk", rejected.exceptionOrNull()?.message)
        }

        assertEquals(3, availableNow("lamp"))
        assertEquals(1, availableNow("desk"))
        assertEquals(0, pool.hikariPoolMXBean.activeConnections)
        assertEquals(
            listOf(
                "connection borrowed",
                "committed",
                "connection returned",
                "audit order-1",
                "connection borrowed",
                "rolled back: insufficient stock for desk",
                "connection returned",
                "audit order-2",
            ),
            events.toList(),
        )
    }

    @Test
    fun cancelledCheckoutRollsBackItsHoldAndRunsTheCancellationHandler() {
        runBlocking {
            val holdPlaced: CompletableDeferred<Unit> = CompletableDeferred()
            val checkout: Job = launch {
                onCancel(
                    fa = {
                        guarantee(
                            fa = {
                                inTransaction { stock ->
                                    stock.reserve("lamp", 4)
                                    holdPlaced.complete(Unit)
                                    awaitCancellation()
                                }
                            },
                            finalizer = { events += "audit order-3" },
                        )
                    },
                    onCancel = { events += "checkout abandoned" },
                )
            }

            holdPlaced.await()
            assertEquals(5, availableNow("lamp"))
            checkout.cancelAndJoin()
            assertTrue(checkout.isCancelled)

            val confirmed: String = onCancel(
                fa = { withConnection { Stock(it).available("lamp") }.let { "lamp: $it" } },
                onCancel = { events += "unexpected cancellation" },
            )
            assertEquals("lamp: 5", confirmed)
        }

        assertEquals(0, pool.hikariPoolMXBean.activeConnections)
        assertEquals(
            listOf(
                "connection borrowed",
                "rolled back: cancelled",
                "connection returned",
                "audit order-3",
                "checkout abandoned",
                "connection borrowed",
                "connection returned",
            ),
            events.toList(),
        )
    }
}
