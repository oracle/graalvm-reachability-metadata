/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.core.Either
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ExperimentalRacingApi
import arrow.fx.coroutines.Race3
import arrow.fx.coroutines.guaranteeCase
import arrow.fx.coroutines.race
import arrow.fx.coroutines.raceN
import arrow.fx.coroutines.racing
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's racing operators: `raceN` over two and three hedged lookups returning `Either`
 * and `Race3`, and the `racing` DSL whose `race` condition skips unusable results, with every
 * losing branch cancelled once a winner is chosen.
 *
 * A quote service hedges price reads across regional replicas of an in-memory H2 database reached
 * through a HikariCP pool; replicas that stall stand in for slow regions and must be cancelled.
 */
public class QuoteReplicaRaceTest {

    /** A price quote and the replica that served it. */
    public data class Quote(val symbol: String, val price: BigDecimal, val servedBy: String)

    /** Reads quotes from the pooled database on behalf of a named replica. */
    public class QuoteStore(private val pool: HikariDataSource) {

        public fun read(symbol: String, replica: String): Quote =
            pool.connection.use { connection ->
                connection.prepareStatement("SELECT price FROM quote WHERE symbol = ?").use { statement ->
                    statement.setString(1, symbol)
                    statement.executeQuery().use { rows ->
                        check(rows.next()) { "no quote for $symbol" }
                        Quote(symbol, rows.getBigDecimal(1), replica)
                    }
                }
            }
    }

    /** A replica that never answers; it records how it ended once the race cancels it. */
    public class StalledReplica {

        public val started: CompletableDeferred<Unit> = CompletableDeferred()

        public val exit: CompletableDeferred<ExitCase> = CompletableDeferred()

        public suspend fun read(): Quote =
            guaranteeCase({
                started.complete(Unit)
                awaitCancellation()
            }) { exitCase -> exit.complete(exitCase) }

        /** Waits, bounded, for the race to have finalized this replica. */
        public suspend fun awaitExit(): ExitCase = withTimeout(10_000) { exit.await() }
    }

    private lateinit var pool: HikariDataSource

    private lateinit var store: QuoteStore

    @BeforeEach
    fun openStore() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:quotes;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = 4
            poolName = "quotes"
        }
        pool = HikariDataSource(config)
        pool.connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE quote(symbol VARCHAR(8) PRIMARY KEY, price DECIMAL(10, 2) NOT NULL);
                    INSERT INTO quote VALUES ('ACME', 101.25), ('GLOBEX', 48.10), ('INITECH', 7.75);
                    """.trimIndent(),
                )
            }
        }
        store = QuoteStore(pool)
    }

    @AfterEach
    fun closeStore() {
        pool.connection.use { connection -> connection.createStatement().use { it.execute("DROP ALL OBJECTS") } }
        pool.close()
    }

    @Test
    fun primaryWinsTheHedgedReadAndTheStandbyIsCancelled() {
        val standby = StalledReplica()

        val winner: Either<Quote, Quote> = runBlocking {
            raceN(
                Dispatchers.IO,
                {
                    standby.started.await()
                    store.read("ACME", "primary")
                },
                { standby.read() },
            )
        }

        assertEquals(Either.Left(Quote("ACME", BigDecimal("101.25"), "primary")), winner)
        val served: String = winner.fold({ "left:${it.servedBy}" }, { "right:${it.servedBy}" })
        assertEquals("left:primary", served)
        val standbyExit: ExitCase = runBlocking { standby.awaitExit() }
        assertTrue(standbyExit is ExitCase.Cancelled, "standby ended with $standbyExit")
        assertEquals(0, pool.hikariPoolMXBean.activeConnections)
    }

    @Test
    fun fastestOfThreeRegionsWinsAndBothSlowRegionsAreCancelled() {
        val east = StalledReplica()
        val west = StalledReplica()

        val winner: Race3<Quote, Quote, Quote> = runBlocking {
            raceN(
                Dispatchers.IO,
                { east.read() },
                {
                    east.started.await()
                    west.started.await()
                    store.read("GLOBEX", "central")
                },
                { west.read() },
            )
        }

        assertTrue(winner is Race3.Second, "expected the central region to win, got $winner")
        val quote: Quote = winner.fold({ it }, { it }, { it })
        assertEquals(Quote("GLOBEX", BigDecimal("48.10"), "central"), quote)
        val regionIndex: Int = winner.fold({ 1 }, { 2 }, { 3 })
        assertEquals(2, regionIndex)
        val exits: List<ExitCase> = runBlocking { listOf(east.awaitExit(), west.awaitExit()) }
        assertTrue(exits.all { it is ExitCase.Cancelled }, "slow regions ended with $exits")
        assertEquals(0, pool.hikariPoolMXBean.activeConnections)
    }

    @Test
    fun failingReplicaAbortsTheRaceAndCancelsItsRival() {
        val rival = StalledReplica()

        val outcome: Result<Either<Quote, Quote>> = runCatching {
            runBlocking {
                raceN(
                    Dispatchers.IO,
                    { rival.read() },
                    {
                        rival.started.await()
                        store.read("UMBRELLA", "replica-b")
                    },
                )
            }
        }

        val failure: Throwable? = outcome.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "race ended with $outcome")
        assertEquals("no quote for UMBRELLA", failure?.message)
        val rivalExit: ExitCase = runBlocking { rival.awaitExit() }
        assertTrue(rivalExit is ExitCase.Cancelled, "rival ended with $rivalExit")
        assertEquals(0, pool.hikariPoolMXBean.activeConnections)
    }

    @OptIn(ExperimentalRacingApi::class)
    @Test
    fun racingSkipsACacheMissAndTakesTheDatabaseQuote() {
        val cache: Map<String, Quote> = mapOf("ACME" to Quote("ACME", BigDecimal("100.00"), "cache"))
        val cacheConsulted: CompletableDeferred<Unit> = CompletableDeferred()
        val mirror = StalledReplica()

        val quote: Quote? = runBlocking {
            racing<Quote?> {
                race(Dispatchers.IO, condition = { it != null }) {
                    cache["INITECH"].also { cacheConsulted.complete(Unit) }
                }
                race(Dispatchers.IO) { mirror.read() }
                race(Dispatchers.IO) {
                    cacheConsulted.await()
                    mirror.started.await()
                    store.read("INITECH", "database")
                }
            }
        }

        assertEquals(Quote("INITECH", BigDecimal("7.75"), "database"), quote)
        val mirrorExit: ExitCase = runBlocking { mirror.awaitExit() }
        assertTrue(mirrorExit is ExitCase.Cancelled, "mirror ended with $mirrorExit")
        assertEquals(0, pool.hikariPoolMXBean.activeConnections)
    }
}
