/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.util.Properties

/**
 * Guarded database access configured through the JDBC `Properties` of a `JdbcSqliteDriver`.
 *
 * A product catalog service writes its on-disk catalog through one driver and serves reports
 * through a second driver opened read-only. Operators freeze writes with the `query_only` pragma
 * during maintenance, cap the catalog's size with `max_page_count`, and bound statement inputs
 * with `limit_length` and `limit_variable_number`, which the bulk lookup respects by chunking.
 */
class GuardedAccessTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var catalogFile: Path
    private lateinit var writerDriver: JdbcSqliteDriver
    private lateinit var writer: CatalogDatabase

    @BeforeEach
    fun openCatalog() {
        catalogFile = dir.resolve("catalog.db")
        writerDriver = JdbcSqliteDriver(url(catalogFile))
        writer = CatalogDatabase(writerDriver)
        writer.createSchema()
        writer.addProduct(Product("BOLT-1", "Hex bolt", 25))
        writer.addProduct(Product("NUT-1", "Hex nut", 10))
    }

    @AfterEach
    fun closeCatalog() {
        writerDriver.close()
    }

    @Test
    fun readOnlyReplicaSeesCommittedWritesAndRejectsItsOwn() {
        JdbcSqliteDriver(url(catalogFile), readOnly()).use { replicaDriver: JdbcSqliteDriver ->
            val replica = CatalogDatabase(replicaDriver)
            assertEquals(listOf("BOLT-1", "NUT-1"), replica.products().map { it.sku })

            writer.addProduct(Product("WASHER-1", "Flat washer", 5))
            assertEquals(listOf("BOLT-1", "NUT-1", "WASHER-1"), replica.products().map { it.sku })
            assertEquals(40L, replica.inventoryValue())

            val rejected: SQLException = assertThrows(SQLException::class.java) {
                replica.addProduct(Product("SCREW-1", "Wood screw", 7))
            }
            assertTrue(rejected.message!!.contains("SQLITE_READONLY"), rejected.message)

            val rolledBack: SQLException = assertThrows(SQLException::class.java) {
                replica.transaction {
                    assertEquals(3, replica.products().size)
                    replica.reprice("BOLT-1", 30)
                }
            }
            assertTrue(rolledBack.message!!.contains("SQLITE_READONLY"), rolledBack.message)
        }
        assertEquals(listOf(25L, 10L, 5L), writer.products().map { it.cents })
    }

    @Test
    fun readOnlyDriverNeverCreatesAMissingCatalog() {
        val missing: Path = dir.resolve("missing.db")

        JdbcSqliteDriver(url(missing), readOnly()).use { replicaDriver: JdbcSqliteDriver ->
            val failure: SQLException = assertThrows(SQLException::class.java) {
                CatalogDatabase(replicaDriver).products()
            }
            assertTrue(failure.message!!.contains("SQLITE_CANTOPEN"), failure.message)
        }
        assertFalse(Files.exists(missing), "a read-only open must not create the database file")
    }

    @Test
    fun queryOnlyPragmaFreezesWritesDuringMaintenance() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { memoryDriver: JdbcSqliteDriver ->
            val catalog = CatalogDatabase(memoryDriver)
            catalog.createSchema()
            catalog.addProduct(Product("BOLT-1", "Hex bolt", 25))

            catalog.setQueryOnly(true)
            assertTrue(catalog.isQueryOnly())
            val frozen: SQLException = assertThrows(SQLException::class.java) {
                catalog.reprice("BOLT-1", 99)
            }
            assertTrue(frozen.message!!.contains("SQLITE_READONLY"), frozen.message)
            assertEquals(25L, catalog.inventoryValue(), "reads keep working while writes are frozen")

            catalog.setQueryOnly(false)
            assertFalse(catalog.isQueryOnly())
            catalog.reprice("BOLT-1", 99)
            assertEquals(99L, catalog.inventoryValue())
        }
    }

    @Test
    fun pageQuotaRejectsTheImportThatWouldOutgrowIt() {
        val quota: Properties = Properties()
        quota.setProperty("page_size", PAGE_SIZE.toString())
        quota.setProperty("max_page_count", MAX_PAGES.toString())
        val quotaFile: Path = dir.resolve("quota.db")

        JdbcSqliteDriver(url(quotaFile), quota).use { quotaDriver: JdbcSqliteDriver ->
            val catalog = CatalogDatabase(quotaDriver)
            catalog.createSchema()
            catalog.attachImage("BOLT-1", ByteArray(PAGE_SIZE) { 1 })
            assertEquals(MAX_PAGES.toLong(), catalog.pragmaLong("max_page_count"))

            val full: SQLException = assertThrows(SQLException::class.java) {
                catalog.attachImage("WASHER-1", ByteArray(PAGE_SIZE * MAX_PAGES) { 3 })
            }
            assertTrue(full.message!!.contains("SQLITE_FULL"), full.message)
            assertEquals(listOf("BOLT-1"), catalog.imageOwners())
            assertTrue(catalog.pragmaLong("page_count") <= MAX_PAGES)

            catalog.attachImage("NUT-1", ByteArray(PAGE_SIZE) { 2 })
            assertEquals(listOf("BOLT-1", "NUT-1"), catalog.imageOwners())
        }
    }

    @Test
    fun statementLimitsBoundInputsAndBulkLookupChunksToThem() {
        val limits: Properties = Properties()
        limits.setProperty("limit_length", MAX_LENGTH.toString())
        limits.setProperty("limit_variable_number", MAX_VARIABLES.toString())

        JdbcSqliteDriver(url(dir.resolve("limited.db")), limits).use { limitedDriver: JdbcSqliteDriver ->
            val catalog = CatalogDatabase(limitedDriver)
            catalog.createSchema()
            val skus: List<String> = (1..25).map { "PART-%02d".format(it) }
            catalog.transaction {
                skus.forEachIndexed { index: Int, sku: String ->
                    catalog.addProduct(Product(sku, "Part $sku", index.toLong() + 1))
                }
            }

            val tooLong: SQLException = assertThrows(SQLException::class.java) {
                catalog.addProduct(Product("LONG-1", "x".repeat(MAX_LENGTH + 1), 1))
            }
            assertTrue(tooLong.message!!.contains("SQLITE_TOOBIG"), tooLong.message)
            catalog.addProduct(Product("LONG-2", "x".repeat(MAX_LENGTH / 2), 1))

            val tooMany: SQLException = assertThrows(SQLException::class.java) {
                catalog.findBySku(skus.take(MAX_VARIABLES + 1))
            }
            assertTrue(tooMany.message!!.contains("too many SQL variables"), tooMany.message)

            val wanted: List<String> = skus.filterIndexed { index: Int, _: String -> index % 2 == 0 } + "UNKNOWN"
            val found: List<Product> = catalog.findAllBySku(wanted, MAX_VARIABLES)
            assertEquals(skus.filterIndexed { index: Int, _: String -> index % 2 == 0 }, found.map { it.sku })
            assertEquals((1L..25L step 2).toList(), found.map { it.cents })
        }
    }

    /** One catalog entry, priced in cents. */
    data class Product(val sku: String, val name: String, val cents: Long)

    /** The catalog database, written the way SQLDelight generates a database and its queries. */
    class CatalogDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            driver.execute(
                null,
                """
                CREATE TABLE product (
                  sku TEXT NOT NULL PRIMARY KEY,
                  name TEXT NOT NULL,
                  cents INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                "CREATE TABLE product_image (sku TEXT NOT NULL PRIMARY KEY, data BLOB NOT NULL)",
                0,
            )
        }

        fun addProduct(product: Product) {
            driver.execute(null, "INSERT INTO product (sku, name, cents) VALUES (?, ?, ?)", 3) {
                bindString(0, product.sku)
                bindString(1, product.name)
                bindLong(2, product.cents)
            }
            notifyQueries(1) { emit -> emit("product") }
        }

        fun reprice(sku: String, cents: Long) {
            driver.execute(null, "UPDATE product SET cents = ? WHERE sku = ?", 2) {
                bindLong(0, cents)
                bindString(1, sku)
            }
            notifyQueries(2) { emit -> emit("product") }
        }

        fun products(): List<Product> =
            driver.executeQuery(null, "SELECT sku, name, cents FROM product ORDER BY rowid", ::readProducts, 0).value

        fun inventoryValue(): Long =
            driver.executeQuery(null, "SELECT coalesce(sum(cents), 0) FROM product", { cursor: SqlCursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            }, 0).value

        fun findBySku(skus: List<String>): List<Product> {
            val placeholders: String = skus.joinToString(", ") { "?" }
            return driver.executeQuery(
                null,
                "SELECT sku, name, cents FROM product WHERE sku IN ($placeholders) ORDER BY sku",
                ::readProducts,
                skus.size,
            ) {
                skus.forEachIndexed { index: Int, sku: String -> bindString(index, sku) }
            }.value
        }

        /** Looks up any number of SKUs while staying under the connection's variable limit. */
        fun findAllBySku(skus: List<String>, maxVariables: Int): List<Product> =
            skus.chunked(maxVariables).flatMap { chunk: List<String> -> findBySku(chunk) }.sortedBy { it.sku }

        fun attachImage(sku: String, data: ByteArray) {
            driver.execute(null, "INSERT INTO product_image (sku, data) VALUES (?, ?)", 2) {
                bindString(0, sku)
                bindBytes(1, data)
            }
        }

        fun imageOwners(): List<String> =
            driver.executeQuery(null, "SELECT sku FROM product_image ORDER BY sku", { cursor: SqlCursor ->
                val owners: MutableList<String> = mutableListOf()
                while (cursor.next().value) {
                    owners += cursor.getString(0)!!
                }
                QueryResult.Value(owners)
            }, 0).value

        fun setQueryOnly(enabled: Boolean) {
            driver.execute(null, "PRAGMA query_only = ${if (enabled) "ON" else "OFF"}", 0)
        }

        fun isQueryOnly(): Boolean = pragmaLong("query_only") == 1L

        fun pragmaLong(name: String): Long =
            driver.executeQuery(null, "PRAGMA $name", { cursor: SqlCursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            }, 0).value

        private fun readProducts(cursor: SqlCursor): QueryResult<List<Product>> {
            val rows: MutableList<Product> = mutableListOf()
            while (cursor.next().value) {
                rows += Product(cursor.getString(0)!!, cursor.getString(1)!!, cursor.getLong(2)!!)
            }
            return QueryResult.Value(rows)
        }
    }

    private companion object {
        const val PAGE_SIZE: Int = 4096
        const val MAX_PAGES: Int = 16
        const val MAX_LENGTH: Int = 256
        const val MAX_VARIABLES: Int = 4

        /** `SQLITE_OPEN_READONLY`, the flag sqlite-jdbc reads from the `open_mode` property. */
        const val OPEN_READONLY: Int = 1

        fun url(file: Path): String = "jdbc:sqlite:$file"

        fun readOnly(): Properties {
            val properties: Properties = Properties()
            properties.setProperty("open_mode", OPEN_READONLY.toString())
            return properties
        }
    }
}
