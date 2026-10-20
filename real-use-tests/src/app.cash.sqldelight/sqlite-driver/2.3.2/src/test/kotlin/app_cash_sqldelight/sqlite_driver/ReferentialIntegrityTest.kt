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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.util.Properties

/**
 * Declarative data integrity enforced by SQLite through `JdbcSqliteDriver`: cascading and
 * nullifying foreign keys, deferred foreign keys, CHECK constraints, generated columns and views.
 *
 * An order-management application keeps customers, sales representatives, orders and order lines.
 * It leaves the bookkeeping to the schema: deleting a customer removes their orders and lines,
 * leaving a representative unassigns their orders, line totals are generated columns, and the
 * order summary screen reads a view that aggregates them. The screen observes the view through a
 * `Query` keyed on the tables behind it, so writes to any of them refresh it.
 */
class ReferentialIntegrityTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var shop: ShopDatabase
    private lateinit var summaries: Query<OrderSummary>
    private lateinit var screen: CountingListener

    @BeforeEach
    fun openShop() {
        val properties: Properties = Properties()
        properties.setProperty("foreign_keys", "true")
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, properties)
        shop = ShopDatabase(driver)
        shop.createSchema()
        shop.addRep(1, "Rita")
        shop.addCustomer(10, "Acme")
        shop.addCustomer(20, "Globex")
        shop.placeOrder(100, 10, 1)
        shop.addLine(100, "anvil", 2, 1250)
        shop.addLine(100, "rope", 3, 199)
        shop.placeOrder(200, 20, 1)
        shop.addLine(200, "rocket", 1, 9900)
        summaries = shop.selectSummaries()
        screen = CountingListener()
        summaries.addListener(screen)
    }

    @AfterEach
    fun closeShop() {
        summaries.removeListener(screen)
        driver.close()
    }

    @Test
    fun viewAggregatesGeneratedLineTotals() {
        assertEquals(
            listOf(
                OrderSummary(100, "Acme", "Rita", 2, 3097),
                OrderSummary(200, "Globex", "Rita", 1, 9900),
            ),
            summaries.executeAsList(),
        )
        assertEquals(2 * 1250L, shop.lineTotal(100, "anvil"))

        shop.changeQuantity(100, "anvil", 5)

        assertEquals(5 * 1250L, shop.lineTotal(100, "anvil"))
        assertEquals(OrderSummary(100, "Acme", "Rita", 2, 6847), summaries.executeAsList().first())
        assertEquals(1, screen.changes)
    }

    @Test
    fun deletingACustomerCascadesToOrdersAndLines() {
        shop.removeCustomer(10)

        assertEquals(listOf(OrderSummary(200, "Globex", "Rita", 1, 9900)), summaries.executeAsList())
        assertEquals(0L, shop.count("orders WHERE customer_id = 10"))
        assertEquals(0L, shop.count("order_line WHERE order_id = 100"))
        assertEquals(1L, shop.count("order_line"))
        assertEquals(1, screen.changes)
    }

    @Test
    fun removingARepresentativeUnassignsTheirOrders() {
        shop.removeRep(1)

        val unassigned: List<OrderSummary> = summaries.executeAsList()
        assertEquals(listOf(100L, 200L), unassigned.map { summary: OrderSummary -> summary.orderId })
        unassigned.forEach { summary: OrderSummary -> assertNull(summary.rep) }
        assertEquals(1, screen.changes)
    }

    @Test
    fun orderForAnUnknownCustomerIsRejected() {
        val rejected: SQLException = assertThrows(SQLException::class.java) {
            shop.placeOrder(300, 99, 1)
        }

        assertTrue(rejected.message!!.contains("FOREIGN KEY"), rejected.message)
        assertEquals(2, summaries.executeAsList().size)
        assertEquals(0, screen.changes)
    }

    @Test
    fun checkConstraintRejectsNonPositiveQuantities() {
        val rejected: SQLException = assertThrows(SQLException::class.java) {
            shop.addLine(200, "fuse", 0, 50)
        }

        assertTrue(rejected.message!!.contains("positive_quantity"), rejected.message)
        assertEquals(OrderSummary(200, "Globex", "Rita", 1, 9900), summaries.executeAsList().last())
    }

    @Test
    fun deferredForeignKeyAllowsLinesBeforeTheirOrderWithinATransaction() {
        shop.transaction {
            shop.addLine(300, "magnet", 4, 300)
            shop.placeOrder(300, 20, 1)
        }

        assertEquals(OrderSummary(300, "Globex", "Rita", 1, 1200), summaries.executeAsList().last())
        assertEquals(1, screen.changes)
    }

    /** One row of the `order_summary` view. */
    data class OrderSummary(
        val orderId: Long,
        val customer: String,
        val rep: String?,
        val lines: Long,
        val total: Long,
    )

    /** Counts how often the observed query reported changed results. */
    class CountingListener : Query.Listener {
        var changes: Int = 0
            private set

        override fun queryResultsChanged() {
            changes++
        }
    }

    /** The shop's database, written the way SQLDelight generates a database and its queries. */
    class ShopDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            listOf(
                "CREATE TABLE rep (id INTEGER PRIMARY KEY, name TEXT NOT NULL)",
                "CREATE TABLE customer (id INTEGER PRIMARY KEY, name TEXT NOT NULL)",
                """
                CREATE TABLE orders (
                  id INTEGER PRIMARY KEY,
                  customer_id INTEGER NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
                  rep_id INTEGER REFERENCES rep(id) ON DELETE SET NULL
                )
                """.trimIndent(),
                """
                CREATE TABLE order_line (
                  order_id INTEGER NOT NULL
                    REFERENCES orders(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
                  product TEXT NOT NULL,
                  quantity INTEGER NOT NULL CONSTRAINT positive_quantity CHECK (quantity > 0),
                  unit_cents INTEGER NOT NULL,
                  total_cents INTEGER GENERATED ALWAYS AS (quantity * unit_cents) STORED,
                  PRIMARY KEY (order_id, product)
                )
                """.trimIndent(),
                """
                CREATE VIEW order_summary AS
                SELECT o.id AS order_id, c.name AS customer, r.name AS rep,
                       count(l.product) AS lines, coalesce(sum(l.total_cents), 0) AS total
                FROM orders o
                JOIN customer c ON c.id = o.customer_id
                LEFT JOIN rep r ON r.id = o.rep_id
                LEFT JOIN order_line l ON l.order_id = o.id
                GROUP BY o.id
                """.trimIndent(),
            ).forEach { sql: String -> driver.execute(null, sql, 0) }
        }

        fun addRep(id: Long, name: String) {
            driver.execute(ADD_REP, "INSERT INTO rep (id, name) VALUES (?, ?)", 2) {
                bindLong(0, id)
                bindString(1, name)
            }
            notifyQueries(ADD_REP) { emit: (String) -> Unit -> emit("rep") }
        }

        fun addCustomer(id: Long, name: String) {
            driver.execute(ADD_CUSTOMER, "INSERT INTO customer (id, name) VALUES (?, ?)", 2) {
                bindLong(0, id)
                bindString(1, name)
            }
            notifyQueries(ADD_CUSTOMER) { emit: (String) -> Unit -> emit("customer") }
        }

        fun placeOrder(id: Long, customerId: Long, repId: Long) {
            driver.execute(PLACE_ORDER, "INSERT INTO orders (id, customer_id, rep_id) VALUES (?, ?, ?)", 3) {
                bindLong(0, id)
                bindLong(1, customerId)
                bindLong(2, repId)
            }
            notifyQueries(PLACE_ORDER) { emit: (String) -> Unit -> emit("orders") }
        }

        fun addLine(orderId: Long, product: String, quantity: Long, unitCents: Long) {
            driver.execute(
                ADD_LINE,
                "INSERT INTO order_line (order_id, product, quantity, unit_cents) VALUES (?, ?, ?, ?)",
                4,
            ) {
                bindLong(0, orderId)
                bindString(1, product)
                bindLong(2, quantity)
                bindLong(3, unitCents)
            }
            notifyQueries(ADD_LINE) { emit: (String) -> Unit -> emit("order_line") }
        }

        fun changeQuantity(orderId: Long, product: String, quantity: Long) {
            driver.execute(
                CHANGE_QUANTITY,
                "UPDATE order_line SET quantity = ? WHERE order_id = ? AND product = ?",
                3,
            ) {
                bindLong(0, quantity)
                bindLong(1, orderId)
                bindString(2, product)
            }
            notifyQueries(CHANGE_QUANTITY) { emit: (String) -> Unit -> emit("order_line") }
        }

        fun removeCustomer(id: Long) {
            driver.execute(REMOVE_CUSTOMER, "DELETE FROM customer WHERE id = ?", 1) {
                bindLong(0, id)
            }
            notifyQueries(REMOVE_CUSTOMER) { emit: (String) -> Unit ->
                emit("customer")
                emit("orders")
                emit("order_line")
            }
        }

        fun removeRep(id: Long) {
            driver.execute(REMOVE_REP, "DELETE FROM rep WHERE id = ?", 1) {
                bindLong(0, id)
            }
            notifyQueries(REMOVE_REP) { emit: (String) -> Unit ->
                emit("rep")
                emit("orders")
            }
        }

        fun selectSummaries(): Query<OrderSummary> =
            Query(
                SELECT_SUMMARIES,
                arrayOf("orders", "customer", "rep", "order_line"),
                driver,
                "Shop.sq",
                "selectSummaries",
                "SELECT order_id, customer, rep, lines, total FROM order_summary ORDER BY order_id",
            ) { cursor: SqlCursor ->
                OrderSummary(
                    cursor.getLong(0)!!,
                    cursor.getString(1)!!,
                    cursor.getString(2),
                    cursor.getLong(3)!!,
                    cursor.getLong(4)!!,
                )
            }

        fun lineTotal(orderId: Long, product: String): Long =
            driver.executeQuery(
                SELECT_LINE_TOTAL,
                "SELECT total_cents FROM order_line WHERE order_id = ? AND product = ?",
                { cursor: SqlCursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else -1L) },
                2,
            ) {
                bindLong(0, orderId)
                bindString(1, product)
            }.value

        fun count(from: String): Long =
            driver.executeQuery(null, "SELECT count(*) FROM $from", { cursor: SqlCursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            }, 0).value

        private companion object {
            const val ADD_REP: Int = 1
            const val ADD_CUSTOMER: Int = 2
            const val PLACE_ORDER: Int = 3
            const val ADD_LINE: Int = 4
            const val CHANGE_QUANTITY: Int = 5
            const val REMOVE_CUSTOMER: Int = 6
            const val REMOVE_REP: Int = 7
            const val SELECT_SUMMARIES: Int = 8
            const val SELECT_LINE_TOTAL: Int = 9
        }
    }
}
