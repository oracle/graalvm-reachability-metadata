/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.parZip
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.ResultSet
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises wide parallel aggregation with Arrow's context-free `parZip` overloads at arities
 * five through nine, whose branches run concurrently on `Dispatchers.Default` whatever dispatcher
 * the caller is on, keep the caller's coroutine name, and are all cancelled as soon as one fails.
 *
 * A customer-support console builds a customer dashboard from many independent reads against an
 * in-memory H2 database behind a HikariCP pool, one `parZip` branch per dashboard card.
 */
public class CustomerDashboardWideParZipTest {

    /** The full dashboard: one field per card, nine cards in total. */
    public data class Dashboard(
        val name: String,
        val tier: String,
        val openTickets: Int,
        val orderCount: Int,
        val lifetimeSpend: Long,
        val loyaltyPoints: Int,
        val lastOrder: String,
        val addressCount: Int,
        val subscribed: Boolean,
    )

    /** The compact header card shown above the ticket thread. */
    public data class Header(
        val name: String,
        val tier: String,
        val openTickets: Int,
        val loyaltyPoints: Int,
        val subscribed: Boolean,
    )

    /** The billing panel shown to finance agents. */
    public data class BillingPanel(
        val name: String,
        val orderCount: Int,
        val lifetimeSpend: Long,
        val lastOrder: String,
        val addressCount: Int,
        val tier: String,
        val loyaltyPoints: Int,
    )

    /**
     * Holds every card read until [parties] branches have arrived, so a result is only produced
     * when the branches really run at the same time.
     */
    public class Rendezvous(private val parties: Int) {
        private val arrived: AtomicInteger = AtomicInteger()
        private val everyoneHere: CompletableDeferred<Unit> = CompletableDeferred()

        /** Registers one branch and suspends until all [parties] branches have registered. */
        public suspend fun arrive() {
            if (arrived.incrementAndGet() == parties) {
                everyoneHere.complete(Unit)
            }
            everyoneHere.await()
        }
    }

    /** Dashboard card reads; each one borrows its own pooled connection. */
    public class SupportConsole(private val pool: HikariDataSource) {

        /** The coroutine name and thread each card read observed, keyed by card. */
        public val observedContexts: MutableMap<String, String> =
            Collections.synchronizedMap(LinkedHashMap())

        public fun name(id: Int): String = single("SELECT name FROM customer WHERE id = ?", id) { it.getString(1) }

        public fun tier(id: Int): String = single("SELECT tier FROM customer WHERE id = ?", id) { it.getString(1) }

        public fun openTickets(id: Int): Int =
            single("SELECT COUNT(*) FROM ticket WHERE customer_id = ? AND status = 'OPEN'", id) { it.getInt(1) }

        public fun orderCount(id: Int): Int =
            single("SELECT COUNT(*) FROM orders WHERE customer_id = ?", id) { it.getInt(1) }

        public fun lifetimeSpend(id: Int): Long =
            single("SELECT COALESCE(SUM(total), 0) FROM orders WHERE customer_id = ?", id) { it.getLong(1) }

        public fun loyaltyPoints(id: Int): Int =
            single("SELECT points FROM loyalty WHERE customer_id = ?", id) { it.getInt(1) }

        public fun lastOrder(id: Int): String =
            single("SELECT COALESCE(MAX(placed_on), 'never') FROM orders WHERE customer_id = ?", id) {
                it.getString(1)
            }

        public fun addressCount(id: Int): Int =
            single("SELECT COUNT(*) FROM address WHERE customer_id = ?", id) { it.getInt(1) }

        public fun subscribed(id: Int): Boolean =
            single("SELECT newsletter FROM customer WHERE id = ?", id) { it.getBoolean(1) }

        /** Builds the nine-card dashboard with every card read in its own branch. */
        public suspend fun dashboard(id: Int): Dashboard {
            val rendezvous = Rendezvous(9)
            return parZip(
                { card("name", rendezvous) { name(id) } },
                { card("tier", rendezvous) { tier(id) } },
                { card("tickets", rendezvous) { openTickets(id) } },
                { card("orders", rendezvous) { orderCount(id) } },
                { card("spend", rendezvous) { lifetimeSpend(id) } },
                { card("loyalty", rendezvous) { loyaltyPoints(id) } },
                { card("lastOrder", rendezvous) { lastOrder(id) } },
                { card("addresses", rendezvous) { addressCount(id) } },
                { card("newsletter", rendezvous) { subscribed(id) } },
            ) { name, tier, tickets, orders, spend, points, last, addresses, newsletter ->
                Dashboard(name, tier, tickets, orders, spend, points, last, addresses, newsletter)
            }
        }

        /** Builds the five-card header. */
        public suspend fun header(id: Int): Header {
            val rendezvous = Rendezvous(5)
            return parZip(
                { card("name", rendezvous) { name(id) } },
                { card("tier", rendezvous) { tier(id) } },
                { card("tickets", rendezvous) { openTickets(id) } },
                { card("loyalty", rendezvous) { loyaltyPoints(id) } },
                { card("newsletter", rendezvous) { subscribed(id) } },
            ) { name, tier, tickets, points, newsletter -> Header(name, tier, tickets, points, newsletter) }
        }

        /** Builds the seven-card billing panel. */
        public suspend fun billing(id: Int): BillingPanel {
            val rendezvous = Rendezvous(7)
            return parZip(
                { card("name", rendezvous) { name(id) } },
                { card("orders", rendezvous) { orderCount(id) } },
                { card("spend", rendezvous) { lifetimeSpend(id) } },
                { card("lastOrder", rendezvous) { lastOrder(id) } },
                { card("addresses", rendezvous) { addressCount(id) } },
                { card("tier", rendezvous) { tier(id) } },
                { card("loyalty", rendezvous) { loyaltyPoints(id) } },
            ) { name, orders, spend, last, addresses, tier, points ->
                BillingPanel(name, orders, spend, last, addresses, tier, points)
            }
        }

        private suspend fun <T> CoroutineScope.card(label: String, rendezvous: Rendezvous, read: () -> T): T {
            val coroutineName: String = coroutineContext[CoroutineName]?.name ?: "unnamed"
            observedContexts[label] = "$coroutineName@${Thread.currentThread().name}"
            rendezvous.arrive()
            return read()
        }

        private fun <T> single(sql: String, id: Int, read: (ResultSet) -> T): T =
            pool.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, id)
                    statement.executeQuery().use { rows ->
                        check(rows.next()) { "no row for customer $id" }
                        read(rows)
                    }
                }
            }
    }

    private lateinit var pool: HikariDataSource

    @BeforeEach
    fun openDatabase() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:support-dashboard;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = 4
            poolName = "support-dashboard"
        }
        pool = HikariDataSource(config)
        pool.connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE customer(id INT PRIMARY KEY, name VARCHAR(32), tier VARCHAR(8), newsletter BOOLEAN);
                    CREATE TABLE ticket(id INT PRIMARY KEY, customer_id INT, status VARCHAR(8));
                    CREATE TABLE orders(id INT PRIMARY KEY, customer_id INT, total BIGINT, placed_on VARCHAR(10));
                    CREATE TABLE loyalty(customer_id INT PRIMARY KEY, points INT);
                    CREATE TABLE address(id INT PRIMARY KEY, customer_id INT, city VARCHAR(16));
                    INSERT INTO customer VALUES (7, 'Ada Byrne', 'GOLD', TRUE), (8, 'Lev Okafor', 'BASIC', FALSE);
                    INSERT INTO ticket VALUES (1, 7, 'OPEN'), (2, 7, 'CLOSED'), (3, 7, 'OPEN'), (4, 8, 'CLOSED');
                    INSERT INTO orders VALUES (10, 7, 4200, '2026-08-02'), (11, 7, 1350, '2026-10-01'),
                        (12, 7, 990, '2026-09-14'), (13, 8, 120, '2026-05-30');
                    INSERT INTO loyalty VALUES (7, 655), (8, 40);
                    INSERT INTO address VALUES (20, 7, 'Lyon'), (21, 7, 'Porto'), (22, 8, 'Graz');
                    """.trimIndent(),
                )
            }
        }
    }

    @AfterEach
    fun closeDatabase() {
        pool.connection.use { connection -> connection.createStatement().use { it.execute("DROP ALL OBJECTS") } }
        pool.close()
    }

    @Test
    fun nineCardDashboardIsReadConcurrentlyOnTheDefaultDispatcherUnderTheCallersName() {
        val console = SupportConsole(pool)

        val dashboard: Dashboard = runBlocking(CoroutineName("ticket-4711")) {
            withTimeout(30_000) { console.dashboard(7) }
        }

        assertEquals(Dashboard("Ada Byrne", "GOLD", 2, 3, 6540, 655, "2026-10-01", 2, true), dashboard)
        assertEquals(9, console.observedContexts.size)
        console.observedContexts.forEach { (card, context) ->
            assertTrue(context.startsWith("ticket-4711@DefaultDispatcher-worker"), "card $card ran as $context")
        }
    }

    @Test
    fun headerAndBillingPanelsLeaveTheCallersSingleThreadDispatcher() {
        val console = SupportConsole(pool)
        val uiExecutor: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "support-ui") }
        val uiDispatcher: ExecutorCoroutineDispatcher = uiExecutor.asCoroutineDispatcher()

        val callerThread: String
        val panels: Pair<Header, BillingPanel>
        try {
            val result: Pair<String, Pair<Header, BillingPanel>> = runBlocking {
                withTimeout(30_000) {
                    withContext(uiDispatcher + CoroutineName("finance-review")) {
                        Thread.currentThread().name to (console.header(8) to console.billing(7))
                    }
                }
            }
            callerThread = result.first
            panels = result.second
        } finally {
            uiDispatcher.close()
        }

        assertTrue(callerThread.startsWith("support-ui"), "panels were requested on $callerThread")

        assertEquals(Header("Lev Okafor", "BASIC", 0, 40, false), panels.first)
        assertEquals(BillingPanel("Ada Byrne", 3, 6540, "2026-10-01", 2, "GOLD", 655), panels.second)
        assertEquals(9, console.observedContexts.size)
        console.observedContexts.forEach { (card, context) ->
            assertTrue(context.startsWith("finance-review@DefaultDispatcher-worker"), "card $card ran as $context")
        }
    }

    @Test
    fun aFailingCardCancelsEveryOtherBranchAndSurfacesItsError() {
        val console = SupportConsole(pool)
        val waiting = Rendezvous(6)
        val cancelled: AtomicInteger = AtomicInteger()

        suspend fun slowCard(): Nothing {
            waiting.arrive()
            try {
                awaitCancellation()
            } catch (cancellation: CancellationException) {
                cancelled.incrementAndGet()
                throw cancellation
            }
        }

        val outcome: Result<String> = runBlocking {
            runCatching {
                withTimeout(30_000) {
                    parZip(
                        { slowCard() },
                        { slowCard() },
                        { slowCard() },
                        { slowCard() },
                        { slowCard() },
                        {
                            waiting.arrive()
                            console.loyaltyPoints(9)
                        },
                    ) { _: Nothing, _: Nothing, _: Nothing, _: Nothing, _: Nothing, points: Int -> "points $points" }
                }
            }
        }

        val failure: Throwable? = outcome.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "dashboard ended with $outcome")
        assertEquals("no row for customer 9", failure?.message)
        assertEquals(5, cancelled.get())
    }
}
