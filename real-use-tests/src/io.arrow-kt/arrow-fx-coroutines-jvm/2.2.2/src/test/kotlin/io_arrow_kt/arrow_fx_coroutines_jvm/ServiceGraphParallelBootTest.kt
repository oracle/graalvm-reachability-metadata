/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.parZip
import arrow.fx.coroutines.resource
import arrow.fx.coroutines.resourceScope
import arrow.fx.coroutines.use
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's parallel resource acquisition: `parZip` binding several `Resource`s concurrently
 * inside one `resourceScope`, `release`, `releaseCase` and `onClose` adding shutdown steps to that
 * scope, and `Resource.use` borrowing a single dependency for one operation.
 *
 * An order service boots its dependency graph at startup: two HikariCP pools over in-memory H2
 * databases and an audit journal. The dependencies come up side by side to shorten startup, and a
 * dependency that fails to boot must not leak the ones that already came up.
 */
public class ServiceGraphParallelBootTest {

    /** An append-only journal of audited actions, sealed when the service shuts down. */
    public class AuditJournal {

        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        @Volatile
        public var sealed: Boolean = false
            private set

        public fun record(entry: String) {
            check(!sealed) { "journal is sealed" }
            entries += entry
        }

        public fun seal(): List<String> {
            sealed = true
            return entries.toList()
        }
    }

    /** The booted dependency graph the order service runs on. */
    public data class Services(
        val orders: HikariDataSource,
        val reporting: HikariDataSource,
        val journal: AuditJournal,
    )

    /**
     * Holds every dependency at the start of its boot until all of them have started, so the boot
     * only completes when the dependencies are really acquired side by side.
     */
    public class BootGate(private val expected: Int) {

        private val arrived: AtomicInteger = AtomicInteger()

        private val allArrived: CompletableDeferred<Unit> = CompletableDeferred()

        public suspend fun arrive() {
            if (arrived.incrementAndGet() == expected) allArrived.complete(Unit)
            withTimeout(10_000) { allArrived.await() }
        }
    }

    /** Shutdown events in the order the scope reported them. */
    private val shutdown: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Every pool any boot opened, so a test can check none of them leaked. */
    private val opened: MutableList<HikariDataSource> = Collections.synchronizedList(mutableListOf())

    private fun pool(name: String, gate: BootGate): Resource<HikariDataSource> =
        resource({
            gate.arrive()
            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1"
                username = "sa"
                password = ""
                maximumPoolSize = 2
                poolName = name
            }
            HikariDataSource(config).also { opened += it }
        }) { pool, exitCase ->
            pool.connection.use { connection -> connection.createStatement().use { it.execute("DROP ALL OBJECTS") } }
            pool.close()
            shutdown += "${pool.poolName} closed: ${describe(exitCase)}"
        }

    private fun journal(gate: BootGate): Resource<AuditJournal> =
        resource({
            gate.arrive()
            AuditJournal()
        }) { journal, exitCase ->
            shutdown += "journal sealed with ${journal.seal().size} entries: ${describe(exitCase)}"
        }

    private fun describe(exitCase: ExitCase): String = when (exitCase) {
        ExitCase.Completed -> "completed"
        is ExitCase.Cancelled -> "cancelled"
        is ExitCase.Failure -> "failed"
    }

    private fun placeOrder(services: Services, id: String, total: Long) {
        services.orders.connection.use { connection ->
            connection.prepareStatement("INSERT INTO orders(id, total) VALUES (?, ?)").use {
                it.setString(1, id)
                it.setLong(2, total)
                it.executeUpdate()
            }
        }
        services.journal.record("placed $id")
    }

    private fun countRows(pool: HikariDataSource, table: String): Long =
        pool.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM $table").use { rows ->
                    check(rows.next())
                    rows.getLong(1)
                }
            }
        }

    private fun execute(pool: HikariDataSource, sql: String) {
        pool.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    @Test
    fun dependenciesBootSideBySideAndShutDownWithEveryRegisteredStep() {
        val gate = BootGate(3)

        val services: Services = runBlocking {
            resourceScope {
                val services: Services = parZip(
                    Dispatchers.IO,
                    { pool("orders", gate).bind() },
                    { pool("reporting", gate).bind() },
                    { journal(gate).bind() },
                ) { orders, reporting, journal -> Services(orders, reporting, journal) }
                onClose { failure -> shutdown += "service stopped, failure: ${failure?.message}" }

                execute(services.orders, "CREATE TABLE orders(id VARCHAR(16) PRIMARY KEY, total BIGINT NOT NULL)")
                execute(services.reporting, "CREATE TABLE daily_total(business_day VARCHAR(10) PRIMARY KEY, total BIGINT)")
                placeOrder(services, "A-1", 1200)
                placeOrder(services, "A-2", 800)
                execute(services.reporting, "INSERT INTO daily_total VALUES ('2026-10-17', 2000)")

                assertEquals(2, countRows(services.orders, "orders"))
                assertEquals(1, countRows(services.reporting, "daily_total"))
                services
            }
        }

        assertEquals("service stopped, failure: null", shutdown.first())
        assertEquals(
            setOf(
                "orders closed: completed",
                "reporting closed: completed",
                "journal sealed with 2 entries: completed",
            ),
            shutdown.drop(1).toSet(),
        )
        assertEquals(4, shutdown.size)
        assertTrue(services.orders.isClosed, "orders pool left open")
        assertTrue(services.reporting.isClosed, "reporting pool left open")
        assertTrue(services.journal.sealed, "journal left open")
    }

    @Test
    fun aDependencyThatFailsToBootReleasesTheOnesAlreadyUp() {
        val gate = BootGate(3)
        val brokenJournal: Resource<AuditJournal> = resource({
            gate.arrive()
            throw IllegalStateException("audit volume is read-only")
        }) { _, _ -> shutdown += "broken journal released" }

        val outcome: Result<Services> = runCatching {
            runBlocking {
                resourceScope {
                    parZip(
                        Dispatchers.IO,
                        { pool("orders", gate).bind() },
                        { pool("reporting", gate).bind() },
                        { brokenJournal.bind() },
                    ) { orders, reporting, journal -> Services(orders, reporting, journal) }
                }
            }
        }

        val failure: Throwable? = outcome.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "boot ended with $outcome")
        assertEquals("audit volume is read-only", failure?.message)
        assertEquals(
            setOf("orders closed: failed", "reporting closed: failed"),
            shutdown.toSet(),
        )
        assertEquals(2, shutdown.size)
        assertEquals(setOf("orders", "reporting"), opened.map { it.poolName }.toSet())
        assertTrue(opened.all { it.isClosed }, "pools left open: ${opened.filterNot { it.isClosed }}")
    }

    @Test
    fun shutdownStepsAttachedToABoundResourceRunBeforeItIsReleased() {
        val drained: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val journalExit: CompletableDeferred<ExitCase> = CompletableDeferred()

        val journal: AuditJournal = runBlocking {
            resourceScope {
                val orders: HikariDataSource = pool("ledger", BootGate(1)) release { pool ->
                    drained += "drained ${pool.poolName} with ${pool.hikariPoolMXBean.activeConnections} active"
                }
                val journal: AuditJournal = journal(BootGate(1)) releaseCase { journal, exitCase ->
                    journal.record("shutdown")
                    journalExit.complete(exitCase)
                }

                execute(orders, "CREATE TABLE orders(id VARCHAR(16) PRIMARY KEY, total BIGINT NOT NULL)")
                placeOrder(Services(orders, orders, journal), "B-1", 450)
                journal
            }
        }

        assertEquals(listOf("drained ledger with 0 active"), drained)
        assertEquals(ExitCase.Completed, runBlocking { withTimeout(10_000) { journalExit.await() } })
        assertEquals(
            listOf("journal sealed with 2 entries: completed", "ledger closed: completed"),
            shutdown,
        )
        assertTrue(journal.sealed)
    }

    @Test
    fun aSingleDependencyIsBorrowedForOneReportWithUse() {
        val reportRows: Long = runBlocking {
            pool("monthly", BootGate(1)).use { pool ->
                execute(pool, "CREATE TABLE invoice(id INT PRIMARY KEY, amount BIGINT)")
                execute(pool, "INSERT INTO invoice VALUES (1, 300), (2, 450), (3, 125)")
                countRows(pool, "invoice")
            }
        }

        assertEquals(3, reportRows)
        assertEquals(listOf("monthly closed: completed"), shutdown)
    }
}
