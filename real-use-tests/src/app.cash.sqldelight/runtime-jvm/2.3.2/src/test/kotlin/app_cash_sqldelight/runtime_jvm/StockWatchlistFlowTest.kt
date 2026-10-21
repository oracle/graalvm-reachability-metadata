/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneNotNull
import app.cash.sqldelight.coroutines.mapToOneOrDefault
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Coroutine integration: a stock watchlist observes its queries as Kotlin `Flow`s through
 * `asFlow` with `mapToList`, `mapToOne`, `mapToOneOrNull`, `mapToOneOrDefault` and
 * `mapToOneNotNull`, and writes through a `SuspendingTransacterImpl` whose suspending
 * transactions commit, return results, roll back and fire their callbacks.
 *
 * `WatchlistQueries` is written the way the SQLDelight compiler emits a queries class for a
 * `Watchlist.sq` file with `generateAsync = true`, so every write is a suspend function.
 */
class StockWatchlistFlowTest {

    private lateinit var driver: SqlDriver
    private lateinit var watchlist: WatchlistQueries

    @BeforeEach
    fun openDatabase() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """
            CREATE TABLE quote (
              symbol TEXT NOT NULL PRIMARY KEY,
              price REAL NOT NULL
            )
            """.trimIndent(),
            0,
        )
        watchlist = WatchlistQueries(driver)
    }

    @AfterEach
    fun closeDatabase() {
        driver.close()
    }

    @Test
    fun listFlowEmitsCurrentRowsAndReEmitsAfterEveryWrite(): Unit = runBlocking {
        val screen = Screen(this, watchlist.allQuotes().asFlow().mapToList(Dispatchers.IO))

        assertThat(screen.next()).isEmpty()

        watchlist.upsert("MSFT", 410.25)
        assertThat(screen.next()).containsExactly(Quote("MSFT", 410.25))

        watchlist.upsert("AAPL", 189.5)
        assertThat(screen.next()).containsExactly(Quote("AAPL", 189.5), Quote("MSFT", 410.25))

        watchlist.upsert("MSFT", 415.0)
        assertThat(screen.next()).containsExactly(Quote("AAPL", 189.5), Quote("MSFT", 415.0))

        watchlist.remove("AAPL")
        assertThat(screen.next()).containsExactly(Quote("MSFT", 415.0))

        screen.close()
    }

    @Test
    fun singleRowFlowsMapMissingRowsToNullDefaultOrSkip(): Unit = runBlocking {
        val tickerPrice = Screen(this, watchlist.priceOf("NVDA").asFlow().mapToOneOrNull(Dispatchers.IO))
        assertThat(tickerPrice.next()).isNull()
        assertThat(watchlist.priceOf("NVDA").asFlow().mapToOneOrDefault(0.0, Dispatchers.IO).first())
            .isEqualTo(0.0)

        val firstKnownPrice = async { watchlist.priceOf("NVDA").asFlow().mapToOneNotNull(Dispatchers.IO).first() }

        watchlist.upsert("NVDA", 120.75)
        assertThat(tickerPrice.next()).isEqualTo(120.75)
        assertThat(withTimeout(TIMEOUT_MILLIS) { firstKnownPrice.await() }).isEqualTo(120.75)

        watchlist.upsert("NVDA", 131.0)
        assertThat(tickerPrice.next()).isEqualTo(131.0)

        watchlist.remove("NVDA")
        assertThat(tickerPrice.next()).isNull()
        assertThat(watchlist.priceOf("NVDA").asFlow().mapToOneOrDefault(-1.0, Dispatchers.IO).first())
            .isEqualTo(-1.0)

        tickerPrice.close()
    }

    @Test
    fun suspendingTransactionReachesObserversOnlyOnceCommitted(): Unit = runBlocking {
        val tracked = Screen(this, watchlist.trackedCount().asFlow().mapToOne(Dispatchers.IO))
        assertThat(tracked.next()).isZero()
        val events = mutableListOf<String>()

        val imported = watchlist.transactionWithResult {
            afterCommit { events += "committed" }
            for ((symbol, price) in listOf("AMZN" to 178.0, "GOOG" to 165.5, "TSLA" to 242.25)) {
                watchlist.upsert(symbol, price)
            }
            watchlist.trackedCount().executeAsOne()
        }

        assertThat(imported).isEqualTo(3L)
        assertThat(events).containsExactly("committed")
        assertThat(tracked.next()).isEqualTo(3L)

        tracked.close()
    }

    @Test
    fun rolledBackSuspendingTransactionLeavesObservedDataUnchanged(): Unit = runBlocking {
        watchlist.upsert("IBM", 190.0)
        val events = mutableListOf<String>()

        watchlist.transaction {
            afterCommit { events += "committed" }
            afterRollback { events += "rolled back" }
            watchlist.upsert("IBM", 1.0)
            watchlist.remove("IBM")
            rollback()
        }

        assertThat(events).containsExactly("rolled back")
        assertThat(watchlist.allQuotes().asFlow().mapToList(Dispatchers.IO).first())
            .containsExactly(Quote("IBM", 190.0))
        assertThat(watchlist.priceOf("IBM").asFlow().mapToOne(Dispatchers.IO).first()).isEqualTo(190.0)
    }

    /** One row of the `quote` table. */
    data class Quote(val symbol: String, val price: Double)

    /** UI-side collector that buffers every emission of a query flow until the screen reads it. */
    class Screen<T>(scope: CoroutineScope, flow: Flow<T>) {
        private val emissions: Channel<T> = Channel(Channel.UNLIMITED)
        private val job: Job = scope.launch(Dispatchers.Default) {
            flow.collect { emissions.send(it) }
        }

        suspend fun next(): T = withTimeout(TIMEOUT_MILLIS) { emissions.receive() }

        suspend fun close() {
            job.cancelAndJoin()
        }
    }

    /** Hand-written equivalent of the async queries class generated for `Watchlist.sq`. */
    class WatchlistQueries(driver: SqlDriver) : SuspendingTransacterImpl(driver) {

        fun allQuotes(): Query<Quote> = Query(
            ALL_QUOTES_ID,
            arrayOf("quote"),
            driver,
            "Watchlist.sq",
            "allQuotes",
            "SELECT symbol, price FROM quote ORDER BY symbol",
        ) { cursor: SqlCursor -> Quote(cursor.getString(0)!!, cursor.getDouble(1)!!) }

        fun trackedCount(): Query<Long> = Query(
            TRACKED_COUNT_ID,
            arrayOf("quote"),
            driver,
            "Watchlist.sq",
            "trackedCount",
            "SELECT count(*) FROM quote",
        ) { cursor: SqlCursor -> cursor.getLong(0)!! }

        fun priceOf(symbol: String): Query<Double> = PriceOfQuery(symbol) { cursor: SqlCursor ->
            cursor.getDouble(0)!!
        }

        suspend fun upsert(symbol: String, price: Double) {
            driver.execute(
                UPSERT_ID,
                """
                INSERT INTO quote (symbol, price) VALUES (?, ?)
                ON CONFLICT (symbol) DO UPDATE SET price = excluded.price
                """.trimIndent(),
                2,
            ) {
                bindString(0, symbol)
                bindDouble(1, price)
            }.await()
            notifyQueries(UPSERT_ID) { emit -> emit("quote") }
        }

        suspend fun remove(symbol: String) {
            driver.execute(REMOVE_ID, "DELETE FROM quote WHERE symbol = ?", 1) {
                bindString(0, symbol)
            }.await()
            notifyQueries(REMOVE_ID) { emit -> emit("quote") }
        }

        /** Parameterized query, generated as an inner class because it binds `symbol`. */
        private inner class PriceOfQuery<out T : Any>(
            private val symbol: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Query.Listener) {
                driver.addListener("quote", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("quote", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(PRICE_OF_ID, "SELECT price FROM quote WHERE symbol = ?", mapper, 1) {
                    bindString(0, symbol)
                }

            override fun toString(): String = "Watchlist.sq:priceOf"
        }

        private companion object {
            const val ALL_QUOTES_ID: Int = 1
            const val TRACKED_COUNT_ID: Int = 2
            const val PRICE_OF_ID: Int = 3
            const val UPSERT_ID: Int = 4
            const val REMOVE_ID: Int = 5
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS: Long = 10_000
    }
}
