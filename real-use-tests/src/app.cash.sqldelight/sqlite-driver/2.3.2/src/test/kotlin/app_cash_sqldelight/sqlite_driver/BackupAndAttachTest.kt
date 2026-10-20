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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exporting, reopening and attaching database files through `JdbcSqliteDriver` with
 * `VACUUM INTO` and `ATTACH DATABASE`.
 *
 * An order-tracking application keeps its working set in an in-memory database. It exports
 * snapshots to disk that a fresh file-backed driver reopens, and it attaches an on-disk archive
 * to move old orders into it inside one transaction and to report across both databases.
 */
class BackupAndAttachTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var shop: OrderDatabase

    @BeforeEach
    fun openShop() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        shop = OrderDatabase(driver)
        shop.createSchema("main")
        shop.placeOrder("main", Order(1, "alice", "2024-01-15", 1200))
        shop.placeOrder("main", Order(2, "bob", "2024-03-02", 450))
        shop.placeOrder("main", Order(3, "alice", "2025-06-30", 3000))
    }

    @AfterEach
    fun closeShop() {
        driver.close()
    }

    @Test
    fun snapshotExportedWithVacuumIntoReopensAsAFileDatabase() {
        val snapshot: Path = dir.resolve("snapshot.db")

        shop.exportTo(snapshot)
        shop.placeOrder("main", Order(4, "carol", "2025-07-01", 99))

        assertTrue(Files.size(snapshot) > 0, "the snapshot file must have been written")
        JdbcSqliteDriver("jdbc:sqlite:$snapshot").use { restored: JdbcSqliteDriver ->
            val restoredShop = OrderDatabase(restored)
            assertEquals(listOf(1L, 2L, 3L), restoredShop.orders("main").map { it.id })
            assertEquals(4650L, restoredShop.revenue("main"))
        }
        assertEquals(listOf(1L, 2L, 3L, 4L), shop.orders("main").map { it.id })
    }

    @Test
    fun attachedArchiveReceivesOldOrdersInsideOneTransaction() {
        val archiveFile: Path = dir.resolve("archive.db")
        JdbcSqliteDriver("jdbc:sqlite:$archiveFile").use { archive: JdbcSqliteDriver ->
            val archiveDb = OrderDatabase(archive)
            archiveDb.createSchema("main")
            archiveDb.placeOrder("main", Order(-1, "dave", "2019-11-11", 700))
        }

        shop.attach(archiveFile, "archive")
        assertEquals(listOf("main", "archive"), shop.databaseNames())

        shop.transaction {
            shop.archiveOrdersBefore("2025-01-01", "archive")
        }

        assertEquals(listOf(3L), shop.orders("main").map { it.id })
        assertEquals(listOf(-1L, 1L, 2L), shop.orders("archive").map { it.id })
        assertEquals(
            listOf(CustomerTotal("alice", 4200), CustomerTotal("bob", 450), CustomerTotal("dave", 700)),
            shop.lifetimeTotals("archive"),
        )

        shop.detach("archive")
        assertEquals(listOf("main"), shop.databaseNames())
        JdbcSqliteDriver("jdbc:sqlite:$archiveFile").use { archive: JdbcSqliteDriver ->
            assertEquals(listOf(-1L, 1L, 2L), OrderDatabase(archive).orders("main").map { it.id })
        }
    }

    @Test
    fun failedArchivingLeavesBothDatabasesUntouched() {
        val archiveFile: Path = dir.resolve("archive.db")
        JdbcSqliteDriver("jdbc:sqlite:$archiveFile").use { archive: JdbcSqliteDriver ->
            val archiveDb = OrderDatabase(archive)
            archiveDb.createSchema("main")
            archiveDb.placeOrder("main", Order(2, "bob", "2024-03-02", 450))
        }
        shop.attach(archiveFile, "archive")

        val failure: Exception = runCatching {
            shop.transaction {
                shop.archiveOrdersBefore("2025-01-01", "archive")
            }
        }.exceptionOrNull() as Exception

        assertTrue(failure.message!!.contains("UNIQUE constraint failed"), failure.message)
        assertEquals(listOf(1L, 2L, 3L), shop.orders("main").map { it.id })
        assertEquals(listOf(2L), shop.orders("archive").map { it.id })
    }

    /** One row of the `orders` table, with the amount in cents. */
    data class Order(val id: Long, val customer: String, val placedOn: String, val cents: Long)

    /** The amount a customer spent across the working set and the archive. */
    data class CustomerTotal(val customer: String, val cents: Long)

    /** The order database, written the way SQLDelight generates a database and its queries. */
    class OrderDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema(schema: String) {
            driver.execute(
                null,
                """
                CREATE TABLE $schema.orders (
                  id INTEGER NOT NULL PRIMARY KEY,
                  customer TEXT NOT NULL,
                  placed_on TEXT NOT NULL,
                  cents INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            )
        }

        fun placeOrder(schema: String, order: Order) {
            driver.execute(
                null,
                "INSERT INTO $schema.orders (id, customer, placed_on, cents) VALUES (?, ?, ?, ?)",
                4,
            ) {
                bindLong(0, order.id)
                bindString(1, order.customer)
                bindString(2, order.placedOn)
                bindLong(3, order.cents)
            }
        }

        fun orders(schema: String): List<Order> =
            driver.executeQuery(
                null,
                "SELECT id, customer, placed_on, cents FROM $schema.orders ORDER BY id",
                { cursor: SqlCursor ->
                    val rows: MutableList<Order> = mutableListOf()
                    while (cursor.next().value) {
                        rows += Order(
                            cursor.getLong(0)!!,
                            cursor.getString(1)!!,
                            cursor.getString(2)!!,
                            cursor.getLong(3)!!,
                        )
                    }
                    QueryResult.Value(rows)
                },
                0,
            ).value

        fun revenue(schema: String): Long =
            driver.executeQuery(null, "SELECT total(cents) FROM $schema.orders", { cursor: SqlCursor ->
                cursor.next()
                QueryResult.Value(cursor.getDouble(0)!!.toLong())
            }, 0).value

        fun lifetimeTotals(archive: String): List<CustomerTotal> =
            driver.executeQuery(
                null,
                """
                SELECT customer, sum(cents) FROM (
                  SELECT customer, cents FROM main.orders
                  UNION ALL
                  SELECT customer, cents FROM $archive.orders
                ) GROUP BY customer ORDER BY customer
                """.trimIndent(),
                { cursor: SqlCursor ->
                    val rows: MutableList<CustomerTotal> = mutableListOf()
                    while (cursor.next().value) {
                        rows += CustomerTotal(cursor.getString(0)!!, cursor.getLong(1)!!)
                    }
                    QueryResult.Value(rows)
                },
                0,
            ).value

        fun exportTo(file: Path) {
            driver.execute(null, "VACUUM INTO ?", 1) {
                bindString(0, file.toString())
            }
        }

        fun attach(file: Path, schema: String) {
            driver.execute(null, "ATTACH DATABASE ? AS $schema", 1) {
                bindString(0, file.toString())
            }
        }

        fun detach(schema: String) {
            driver.execute(null, "DETACH DATABASE $schema", 0)
        }

        fun databaseNames(): List<String> =
            driver.executeQuery(null, "PRAGMA database_list", { cursor: SqlCursor ->
                val names: MutableList<String> = mutableListOf()
                while (cursor.next().value) {
                    names += cursor.getString(1)!!
                }
                QueryResult.Value(names)
            }, 0).value

        fun archiveOrdersBefore(day: String, archive: String) {
            driver.execute(
                null,
                "INSERT INTO $archive.orders SELECT * FROM main.orders WHERE placed_on < ?",
                1,
            ) {
                bindString(0, day)
            }
            driver.execute(null, "DELETE FROM main.orders WHERE placed_on < ?", 1) {
                bindString(0, day)
            }
        }
    }
}
