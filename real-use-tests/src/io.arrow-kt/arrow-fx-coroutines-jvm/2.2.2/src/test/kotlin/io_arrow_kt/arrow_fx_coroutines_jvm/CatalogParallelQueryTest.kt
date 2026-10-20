/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.guaranteeCase
import arrow.fx.coroutines.parMap
import arrow.fx.coroutines.parZip
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's parallel composition operators: `parZip` joining independent queries into one
 * view and `parMap` fanning out over many keys with bounded concurrency, including the cancellation
 * of sibling branches when one of them fails.
 *
 * A storefront catalog reads products, stock and reviews from an in-memory H2 database through a
 * HikariCP pool and assembles product pages from those reads in parallel on `Dispatchers.IO`.
 */
public class CatalogParallelQueryTest {

    /** A product page assembled from three independent reads. */
    public data class ProductPage(val name: String, val inStock: Int, val averageRating: Double)

    /** Catalog reads that each borrow their own pooled connection. */
    public class Catalog(private val pool: HikariDataSource) {

        private val inFlight: AtomicInteger = AtomicInteger()

        /** The highest number of page loads that ran at the same time. */
        public val peakConcurrency: AtomicInteger = AtomicInteger()

        public fun productName(sku: String): String =
            query("SELECT name FROM product WHERE sku = ?", sku) { it.getString(1) }

        public fun stock(sku: String): Int =
            query("SELECT COALESCE(SUM(quantity), 0) FROM stock WHERE sku = ?", sku) { it.getInt(1) }

        public fun averageRating(sku: String): Double =
            query("SELECT COALESCE(AVG(CAST(stars AS DOUBLE)), 0) FROM review WHERE sku = ?", sku) {
                it.getDouble(1)
            }

        /** Loads one page by running its three reads concurrently. */
        public suspend fun page(sku: String): ProductPage {
            peakConcurrency.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try {
                return parZip(
                    Dispatchers.IO,
                    { productName(sku) },
                    { stock(sku) },
                    { averageRating(sku) },
                ) { name, inStock, rating -> ProductPage(name, inStock, rating) }
            } finally {
                inFlight.decrementAndGet()
            }
        }

        /** Loads every page, at most [concurrency] at a time, in the order of [skus]. */
        public suspend fun pages(skus: List<String>, concurrency: Int): List<ProductPage> =
            skus.parMap(Dispatchers.IO, concurrency) { sku -> page(sku) }

        private fun <T> query(sql: String, sku: String, read: (ResultSet) -> T): T =
            pool.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, sku)
                    statement.executeQuery().use { rows ->
                        check(rows.next()) { "unknown product $sku" }
                        read(rows)
                    }
                }
            }
    }

    private lateinit var pool: HikariDataSource

    @BeforeEach
    fun openCatalog() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:catalog;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = 6
            poolName = "catalog"
        }
        pool = HikariDataSource(config)
        pool.connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE product(sku VARCHAR(8) PRIMARY KEY, name VARCHAR(64) NOT NULL);
                    CREATE TABLE stock(sku VARCHAR(8), warehouse VARCHAR(8), quantity INT);
                    CREATE TABLE review(sku VARCHAR(8), stars INT);
                    INSERT INTO product VALUES ('K1', 'Kettle'), ('L2', 'Lamp'), ('M3', 'Mug'),
                        ('N4', 'Notebook'), ('P5', 'Pen'), ('R6', 'Rug');
                    INSERT INTO stock VALUES ('K1', 'east', 4), ('K1', 'west', 6), ('L2', 'east', 0),
                        ('M3', 'west', 12), ('N4', 'east', 7), ('R6', 'west', 1);
                    INSERT INTO review VALUES ('K1', 5), ('K1', 3), ('L2', 4), ('M3', 2), ('M3', 4),
                        ('M3', 3), ('P5', 5);
                    """.trimIndent(),
                )
            }
        }
    }

    @AfterEach
    fun closeCatalog() {
        pool.connection.use { connection -> connection.createStatement().use { it.execute("DROP ALL OBJECTS") } }
        pool.close()
    }

    @Test
    fun pagesAreAssembledInParallelInInputOrderWithBoundedConcurrency() {
        val catalog = Catalog(pool)
        val skus: List<String> = listOf("R6", "K1", "P5", "M3", "L2", "N4")

        val pages: List<ProductPage> = runBlocking { catalog.pages(skus, concurrency = 2) }

        assertEquals(
            listOf(
                ProductPage("Rug", 1, 0.0),
                ProductPage("Kettle", 10, 4.0),
                ProductPage("Pen", 0, 5.0),
                ProductPage("Mug", 12, 3.0),
                ProductPage("Lamp", 0, 4.0),
                ProductPage("Notebook", 7, 0.0),
            ),
            pages,
        )
        val peak: Int = catalog.peakConcurrency.get()
        assertTrue(peak in 1..2, "parMap ran $peak page loads at once with a limit of 2")
        assertEquals(0, pool.hikariPoolMXBean.activeConnections)
    }

    @Test
    fun failingBranchCancelsItsSiblingsAndPropagates() {
        val catalog = Catalog(pool)
        val siblingStarted: CompletableDeferred<Unit> = CompletableDeferred()
        val siblingExit: AtomicReference<ExitCase> = AtomicReference()

        val outcome: Result<String> = runCatching {
            runBlocking {
                parZip(
                    Dispatchers.IO,
                    {
                        guaranteeCase({
                            siblingStarted.complete(Unit)
                            awaitCancellation()
                        }) { exitCase -> siblingExit.set(exitCase) }
                    },
                    {
                        siblingStarted.await()
                        catalog.productName("Z9")
                    },
                ) { _: Nothing, name: String -> name }
            }
        }

        assertEquals("unknown product Z9", outcome.exceptionOrNull()?.message)
        assertTrue(siblingExit.get() is ExitCase.Cancelled, "sibling ended with ${siblingExit.get()}")
        assertEquals(0, pool.hikariPoolMXBean.activeConnections)

        val recovered: List<ProductPage> = runBlocking { catalog.pages(listOf("M3"), concurrency = 1) }
        assertEquals(listOf(ProductPage("Mug", 12, 3.0)), recovered)
    }
}
