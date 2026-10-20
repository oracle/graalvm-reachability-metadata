/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.logs.LogSqliteDriver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Statement logging through `LogSqliteDriver`: an order desk wraps its SQLite driver so every
 * statement, its bound parameters, each transaction's begin, commit and rollback, listener
 * registration and change notification, and the final connection close land in a SQL trace.
 *
 * `OrderQueries` is written the way the SQLDelight compiler emits a queries class for an
 * `Order.sq` file, so the trace records the same driver calls a generated database makes.
 */
class OrderDeskStatementLogTest {

    private val trace: MutableList<String> = mutableListOf()
    private lateinit var driver: SqlDriver
    private lateinit var orders: OrderQueries

    @BeforeEach
    fun openDatabase() {
        driver = LogSqliteDriver(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)) { line: String -> trace += line }
        OrderSchema.create(driver)
        orders = OrderQueries(driver)
        trace.clear()
    }

    @AfterEach
    fun closeDatabase() {
        driver.close()
    }

    @Test
    fun writesAndReadsAreLoggedWithTheirBoundParameters() {
        orders.place(customer = "ada", total = 12.5, express = true, note = null)
        val placed: List<Order> = orders.forCustomer("ada").executeAsList()

        assertThat(placed).containsExactly(Order(1, "ada", 12.5, express = true, note = null))
        assertThat(trace).containsExactly(
            "EXECUTE\n $PLACE_SQL",
            " [ada, 12.5, true, null]",
            "NOTIFYING LISTENERS OF [orders]",
            "QUERY\n $FOR_CUSTOMER_SQL",
            " [ada]",
        )
    }

    @Test
    fun statementWithoutParametersLogsOnlyItsSql() {
        orders.place(customer = "ada", total = 3.0, express = false, note = "gift wrap")
        orders.place(customer = "grace", total = 7.0, express = false, note = null)
        trace.clear()

        val count: Long = orders.countAll().executeAsOne()

        assertThat(count).isEqualTo(2L)
        assertThat(trace).containsExactly("QUERY\n $COUNT_SQL")
    }

    @Test
    fun committedTransactionNotifiesListenersOnceBeforeCommitHook() {
        val dashboard = Dashboard(orders.countAll())
        dashboard.open()

        orders.transaction {
            orders.place(customer = "ada", total = 4.0, express = false, note = null)
            orders.place(customer = "linus", total = 9.0, express = true, note = "call first")
        }

        assertThat(dashboard.refreshes).isEqualTo(1)
        assertThat(trace).containsExactly(
            "BEGIN dashboard LISTENING TO [orders]",
            "TRANSACTION BEGIN",
            "EXECUTE\n $PLACE_SQL",
            " [ada, 4.0, false, null]",
            "EXECUTE\n $PLACE_SQL",
            " [linus, 9.0, true, call first]",
            // Listeners hear about the batched writes before the commit hooks run.
            "NOTIFYING LISTENERS OF [orders]",
            "TRANSACTION COMMIT",
        )
        dashboard.close()
        assertThat(trace.last()).isEqualTo("END dashboard LISTENING TO [orders]")
    }

    @Test
    fun rolledBackTransactionIsLoggedWithoutNotifyingListeners() {
        val dashboard = Dashboard(orders.countAll())
        dashboard.open()
        trace.clear()

        orders.transaction {
            orders.place(customer = "ada", total = 99.0, express = false, note = null)
            rollback()
        }

        assertThat(orders.countAll().executeAsOne()).isZero()
        assertThat(dashboard.refreshes).isZero()
        assertThat(trace).containsExactly(
            "TRANSACTION BEGIN",
            "EXECUTE\n $PLACE_SQL",
            " [ada, 99.0, false, null]",
            "TRANSACTION ROLLBACK",
            "QUERY\n $COUNT_SQL",
        )
        dashboard.close()
    }

    @Test
    fun closingTheDriverIsTheLastTraceEntry() {
        val standalone: SqlDriver = LogSqliteDriver(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)) { line: String ->
            trace += line
        }
        OrderSchema.create(standalone)

        standalone.close()

        assertThat(trace).containsExactly("EXECUTE\n ${OrderSchema.CREATE_SQL}", "CLOSE CONNECTION")
    }

    /** One row of the `orders` table. */
    data class Order(val id: Long, val customer: String, val total: Double, val express: Boolean, val note: String?)

    /** Screen that re-reads the order count whenever the `orders` table changes. */
    class Dashboard(private val count: Query<Long>) : Query.Listener {
        var refreshes: Int = 0
            private set

        fun open() {
            count.addListener(this)
        }

        fun close() {
            count.removeListener(this)
        }

        override fun queryResultsChanged() {
            refreshes++
        }

        override fun toString(): String = "dashboard"
    }

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE` in `Order.sq`. */
    object OrderSchema {
        const val CREATE_SQL: String =
            "CREATE TABLE orders (id INTEGER PRIMARY KEY, customer TEXT NOT NULL, total REAL NOT NULL, " +
                "express INTEGER NOT NULL, note TEXT)"

        fun create(driver: SqlDriver) {
            driver.execute(null, CREATE_SQL, 0)
        }
    }

    /** Hand-written equivalent of the queries class generated for `Order.sq`. */
    class OrderQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun place(customer: String, total: Double, express: Boolean, note: String?) {
            driver.execute(PLACE_ID, PLACE_SQL, 4) {
                bindString(0, customer)
                bindDouble(1, total)
                bindBoolean(2, express)
                bindString(3, note)
            }
            notifyQueries(PLACE_ID) { emit -> emit("orders") }
        }

        fun forCustomer(customer: String): Query<Order> = ForCustomerQuery(customer) { cursor: SqlCursor ->
            Order(
                cursor.getLong(0)!!,
                cursor.getString(1)!!,
                cursor.getDouble(2)!!,
                cursor.getBoolean(3)!!,
                cursor.getString(4),
            )
        }

        fun countAll(): Query<Long> = Query(COUNT_ID, arrayOf("orders"), driver, "Order.sq", "countAll", COUNT_SQL) {
            cursor: SqlCursor -> cursor.getLong(0)!!
        }

        private inner class ForCustomerQuery<out T : Any>(
            private val customer: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Listener) {
                driver.addListener("orders", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("orders", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>) =
                driver.executeQuery(FOR_CUSTOMER_ID, FOR_CUSTOMER_SQL, mapper, 1) {
                    bindString(0, customer)
                }

            override fun toString(): String = "Order.sq:forCustomer"
        }
    }

    companion object {
        private const val PLACE_ID: Int = 101
        private const val FOR_CUSTOMER_ID: Int = 102
        private const val COUNT_ID: Int = 103

        private const val PLACE_SQL: String =
            "INSERT INTO orders (customer, total, express, note) VALUES (?, ?, ?, ?)"
        private const val FOR_CUSTOMER_SQL: String =
            "SELECT id, customer, total, express, note FROM orders WHERE customer = ? ORDER BY id"
        private const val COUNT_SQL: String = "SELECT count(*) FROM orders"
    }
}
