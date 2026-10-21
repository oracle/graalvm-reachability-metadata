/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.SQLException
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Per-thread connections of a file-backed `JdbcSqliteDriver` configured through JDBC `Properties`.
 *
 * A warehouse application opens its on-disk database with foreign keys, write-ahead logging and a
 * busy timeout, then lets request-handling worker threads record stock movements concurrently. Each
 * thread gets its own connection carrying that configuration, and a transaction open on one thread
 * stays invisible to readers on another until it commits.
 */
class ThreadedConnectionTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var warehouse: WarehouseDatabase
    private lateinit var workers: ExecutorService

    @BeforeEach
    fun openWarehouse() {
        val properties: Properties = Properties()
        properties.setProperty("foreign_keys", "true")
        properties.setProperty("journal_mode", "WAL")
        properties.setProperty("busy_timeout", "30000")
        driver = JdbcSqliteDriver("jdbc:sqlite:${dir.resolve("warehouse.db")}", properties)
        warehouse = WarehouseDatabase(driver)
        warehouse.createSchema()
        warehouse.addProduct("bolt")
        warehouse.addProduct("nut")
        workers = Executors.newFixedThreadPool(WORKERS)
    }

    @AfterEach
    fun closeWarehouse() {
        workers.shutdown()
        assertTrue(workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        driver.close()
    }

    @Test
    fun everyWorkerConnectionCarriesTheConfiguredPragmas() {
        val settings: Future<Pair<Long, String>> = workers.submit<Pair<Long, String>> {
            warehouse.pragmaLong("foreign_keys") to warehouse.pragmaString("journal_mode")
        }

        assertEquals(1L to "wal", settings.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val rejected: ExecutionException = assertThrows(ExecutionException::class.java) {
            workers.submit { warehouse.recordMovement("washer", 10) }.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        assertTrue(rejected.cause is SQLException, "unknown products must violate the foreign key")
        assertEquals(0L, warehouse.stock("washer"))
    }

    @Test
    fun uncommittedMovementOnOneThreadIsHiddenFromOtherThreads() {
        val written: CountDownLatch = CountDownLatch(1)
        val inspected: CountDownLatch = CountDownLatch(1)
        val committed: CountDownLatch = CountDownLatch(1)
        val ledger: Query<Movement> = warehouse.selectMovements()
        val listener: Query.Listener = Query.Listener { committed.countDown() }
        ledger.addListener(listener)

        val delivery: Future<*> = workers.submit {
            warehouse.transaction {
                warehouse.recordMovement("bolt", 500)
                warehouse.recordMovement("nut", 250)
                written.countDown()
                check(inspected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "reader never inspected" }
            }
        }

        assertTrue(written.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(0L, warehouse.stock("bolt"), "another thread must not see the open transaction")
        assertEquals(emptyList<Movement>(), ledger.executeAsList())
        inspected.countDown()
        delivery.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertTrue(committed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(500L, warehouse.stock("bolt"))
        assertEquals(listOf(Movement("bolt", 500), Movement("nut", 250)), ledger.executeAsList())
        ledger.removeListener(listener)
    }

    @Test
    fun concurrentWorkersRecordEveryMovement() {
        val start: CountDownLatch = CountDownLatch(1)
        val shipments: List<Future<*>> = (1..WORKERS).map { worker: Int ->
            workers.submit {
                check(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "workers never started" }
                repeat(MOVEMENTS_PER_WORKER) { index: Int ->
                    warehouse.transaction {
                        warehouse.recordMovement("bolt", worker.toLong())
                        warehouse.recordMovement("nut", -index.toLong())
                    }
                }
            }
        }

        start.countDown()
        shipments.forEach { shipment: Future<*> -> shipment.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }

        val expectedBolts: Long = (1..WORKERS).sumOf { worker: Int -> worker.toLong() * MOVEMENTS_PER_WORKER }
        val expectedNuts: Long = -WORKERS.toLong() * (0 until MOVEMENTS_PER_WORKER).sum()
        assertEquals(expectedBolts, warehouse.stock("bolt"))
        assertEquals(expectedNuts, warehouse.stock("nut"))
        assertEquals((2 * WORKERS * MOVEMENTS_PER_WORKER).toLong(), warehouse.movementCount())
    }

    /** One row of the `movement` table. */
    data class Movement(val product: String, val quantity: Long)

    /** The warehouse database, written the way SQLDelight generates a database and its queries. */
    class WarehouseDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            driver.execute(null, "CREATE TABLE product (name TEXT PRIMARY KEY)", 0)
            driver.execute(
                null,
                """
                CREATE TABLE movement (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  product TEXT NOT NULL REFERENCES product (name),
                  quantity INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            )
        }

        fun addProduct(name: String) {
            driver.execute(ADD_PRODUCT, "INSERT INTO product (name) VALUES (?)", 1) {
                bindString(0, name)
            }
        }

        fun recordMovement(product: String, quantity: Long) {
            driver.execute(RECORD_MOVEMENT, "INSERT INTO movement (product, quantity) VALUES (?, ?)", 2) {
                bindString(0, product)
                bindLong(1, quantity)
            }
            notifyQueries(RECORD_MOVEMENT) { emit: (String) -> Unit -> emit("movement") }
        }

        fun selectMovements(): Query<Movement> =
            Query(SELECT_MOVEMENTS, arrayOf("movement"), driver, "Warehouse.sq", "selectMovements",
                "SELECT product, quantity FROM movement ORDER BY id") { cursor: SqlCursor ->
                Movement(cursor.getString(0)!!, cursor.getLong(1)!!)
            }

        fun stock(product: String): Long =
            driver.executeQuery(STOCK, "SELECT coalesce(sum(quantity), 0) FROM movement WHERE product = ?",
                { cursor: SqlCursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else 0L) },
                1) {
                bindString(0, product)
            }.value

        fun movementCount(): Long =
            driver.executeQuery(null, "SELECT count(*) FROM movement", { cursor: SqlCursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else 0L)
            }, 0).value

        fun pragmaLong(name: String): Long =
            driver.executeQuery(null, "PRAGMA $name", { cursor: SqlCursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else -1L)
            }, 0).value

        fun pragmaString(name: String): String =
            driver.executeQuery(null, "PRAGMA $name", { cursor: SqlCursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getString(0)!! else "")
            }, 0).value

        private companion object {
            const val ADD_PRODUCT: Int = 1
            const val RECORD_MOVEMENT: Int = 2
            const val SELECT_MOVEMENTS: Int = 3
            const val STOCK: Int = 4
        }
    }

    private companion object {
        const val WORKERS: Int = 4
        const val MOVEMENTS_PER_WORKER: Int = 25
        const val TIMEOUT_SECONDS: Long = 60
    }
}
