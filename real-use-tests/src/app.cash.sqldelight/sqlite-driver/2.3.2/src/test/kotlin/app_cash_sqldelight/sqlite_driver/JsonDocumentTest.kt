/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.SQLException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * JSON documents stored in TEXT columns and queried with SQLite's JSON1 functions through an
 * in-memory `JdbcSqliteDriver`.
 *
 * A shop keeps each order's line items as a JSON array, mapped to Kotlin objects by a
 * `ColumnAdapter`, and its shipping details as a JSON object. Reports explode the items with
 * `json_each`, filter on nested fields through an expression index, edit single items in place
 * with `json_set` and `json_insert`, and a `CHECK (json_valid(...))` constraint rejects broken
 * documents.
 */
class JsonDocumentTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var shop: ShopDatabase

    @BeforeEach
    fun openShop() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        shop = ShopDatabase(driver)
        shop.createSchema()
    }

    @AfterEach
    fun closeShop() {
        driver.close()
    }

    @Test
    fun lineItemsRoundTripThroughJsonColumnAdapter() {
        val items: List<LineItem> = listOf(LineItem("pen", 2, 150), LineItem("notebook", 1, 499))
        shop.placeOrder(1, items, Shipping("Berlin", express = true))

        val order: Order = shop.orderById(1).executeAsOne()

        assertEquals(items, order.items)
        assertEquals(Shipping("Berlin", express = true), order.shipping)
        assertEquals(2L, shop.itemCount(1).executeAsOne())
        assertEquals(
            """[{"sku":"pen","qty":2,"price":150},{"sku":"notebook","qty":1,"price":499}]""",
            ShopDatabase.itemsAdapter.encode(items),
        )
    }

    @Test
    fun jsonEachExplodesLineItemsIntoReportRows() {
        shop.transaction {
            shop.placeOrder(1, listOf(LineItem("pen", 2, 150), LineItem("ink", 3, 300)), Shipping("Berlin", false))
            shop.placeOrder(2, listOf(LineItem("pen", 5, 150)), Shipping("Paris", true))
            shop.placeOrder(3, listOf(LineItem("notebook", 1, 499), LineItem("ink", 1, 300)), Shipping("Berlin", true))
        }

        assertEquals(
            listOf(SkuTotal("pen", 7), SkuTotal("ink", 4), SkuTotal("notebook", 1)),
            shop.unitsSoldPerSku().executeAsList(),
        )
        assertEquals(listOf(1L to 1200L, 2L to 750L, 3L to 799L), shop.orderTotals().executeAsList())
    }

    @Test
    fun nestedShippingFieldsFilterOrdersThroughExpressionIndex() {
        shop.transaction {
            shop.placeOrder(1, listOf(LineItem("pen", 1, 150)), Shipping("Berlin", false))
            shop.placeOrder(2, listOf(LineItem("pen", 1, 150)), Shipping("Paris", true))
            shop.placeOrder(3, listOf(LineItem("pen", 1, 150)), Shipping("Berlin", true))
        }

        assertEquals(listOf(1L, 3L), shop.ordersShippedTo("Berlin").executeAsList().map { order: Order -> order.id })
        assertEquals(listOf(2L, 3L), shop.expressOrderIds().executeAsList())
        assertEquals(emptyList<Order>(), shop.ordersShippedTo("Rome").executeAsList())
        assertTrue(
            shop.cityLookupPlan().executeAsList().any { detail: String -> detail.contains("purchase_city") },
            "the city lookup uses the expression index",
        )
        assertEquals(
            listOf("""Berlin""" to """[1,3]""", """Paris""" to """[2]"""),
            shop.orderIdsPerCity().executeAsList(),
        )
    }

    @Test
    fun inPlaceJsonEditsUpdateItemsAndRefreshObservers() {
        shop.placeOrder(1, listOf(LineItem("pen", 2, 150)), Shipping("Berlin", false))
        val order: Query<Order> = shop.orderById(1)
        val refreshes: CountingListener = CountingListener()
        order.addListener(refreshes)

        shop.changeQuantity(1, index = 0, qty = 4)
        assertEquals(listOf(LineItem("pen", 4, 150)), order.executeAsOne().items)

        shop.appendItem(1, LineItem("ink", 1, 300))
        assertEquals(listOf(LineItem("pen", 4, 150), LineItem("ink", 1, 300)), order.executeAsOne().items)
        assertEquals(listOf(1L to 900L), shop.orderTotals().executeAsList())

        order.removeListener(refreshes)
        shop.changeQuantity(1, index = 1, qty = 2)
        assertEquals(LineItem("ink", 2, 300), order.executeAsOne().items[1])
        assertEquals(2, refreshes.changes, "only edits made while subscribed refresh the order screen")
    }

    @Test
    fun malformedJsonIsRejectedByCheckConstraint() {
        shop.placeOrder(1, listOf(LineItem("pen", 1, 150)), Shipping("Berlin", false))

        val failure: SQLException = assertThrows(SQLException::class.java) {
            shop.placeRawOrder(2, """[{"sku":"pen",""", """{"city":"Berlin","express":false}""")
        }

        assertTrue(failure.message.orEmpty().contains("CHECK constraint failed"), failure.message)
        assertEquals(listOf(1L), shop.allOrderIds().executeAsList())
    }

    data class LineItem(val sku: String, val qty: Long, val price: Long)

    data class Shipping(val city: String, val express: Boolean)

    data class Order(val id: Long, val items: List<LineItem>, val shipping: Shipping)

    data class SkuTotal(val sku: String, val units: Long)

    class CountingListener : Query.Listener {
        var changes: Int = 0
            private set

        override fun queryResultsChanged() {
            changes++
        }
    }

    /** Encodes line items as the compact JSON array SQLite's JSON functions also produce. */
    object LineItemsAdapter : ColumnAdapter<List<LineItem>, String> {
        private val itemPattern: Regex = Regex("""\{"sku":"([^"]*)","qty":(-?\d+),"price":(-?\d+)}""")

        override fun decode(databaseValue: String): List<LineItem> =
            itemPattern.findAll(databaseValue).map { match: MatchResult ->
                LineItem(match.groupValues[1], match.groupValues[2].toLong(), match.groupValues[3].toLong())
            }.toList()

        override fun encode(value: List<LineItem>): String =
            value.joinToString(",", "[", "]") { item: LineItem ->
                """{"sku":"${item.sku}","qty":${item.qty},"price":${item.price}}"""
            }
    }

    /** The shop database, written the way SQLDelight generates a database and its queries. */
    class ShopDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            driver.execute(
                null,
                """
                CREATE TABLE purchase (
                  id INTEGER PRIMARY KEY,
                  items TEXT NOT NULL CHECK (json_valid(items) AND json_type(items) = 'array'),
                  shipping TEXT NOT NULL CHECK (json_valid(shipping))
                )
                """.trimIndent(),
                0,
            )
            driver.execute(null, "CREATE INDEX purchase_city ON purchase(json_extract(shipping, '$.city'))", 0)
        }

        fun placeOrder(id: Long, items: List<LineItem>, shipping: Shipping) {
            driver.execute(
                PLACE,
                "INSERT INTO purchase (id, items, shipping) VALUES (?, ?, json_object('city', ?, 'express', json(?)))",
                4,
            ) {
                bindLong(0, id)
                bindString(1, itemsAdapter.encode(items))
                bindString(2, shipping.city)
                bindString(3, shipping.express.toString())
            }
            notifyQueries(PLACE) { emit: (String) -> Unit -> emit("purchase") }
        }

        fun placeRawOrder(id: Long, items: String, shipping: String) {
            driver.execute(PLACE_RAW, "INSERT INTO purchase (id, items, shipping) VALUES (?, ?, ?)", 3) {
                bindLong(0, id)
                bindString(1, items)
                bindString(2, shipping)
            }
            notifyQueries(PLACE_RAW) { emit: (String) -> Unit -> emit("purchase") }
        }

        fun changeQuantity(id: Long, index: Int, qty: Long) {
            driver.execute(CHANGE_QTY, "UPDATE purchase SET items = json_set(items, ?, ?) WHERE id = ?", 3) {
                bindString(0, "$[$index].qty")
                bindLong(1, qty)
                bindLong(2, id)
            }
            notifyQueries(CHANGE_QTY) { emit: (String) -> Unit -> emit("purchase") }
        }

        fun appendItem(id: Long, item: LineItem) {
            driver.execute(APPEND, "UPDATE purchase SET items = json_insert(items, '$[#]', json(?)) WHERE id = ?", 2) {
                bindString(0, itemsAdapter.encode(listOf(item)).removeSurrounding("[", "]"))
                bindLong(1, id)
            }
            notifyQueries(APPEND) { emit: (String) -> Unit -> emit("purchase") }
        }

        fun orderById(id: Long): Query<Order> = OrderByIdQuery(id)

        fun ordersShippedTo(city: String): Query<Order> = ShippedToQuery(city)

        fun allOrderIds(): Query<Long> = Query(ALL_IDS, arrayOf("purchase"), driver, "Shop.sq", "allOrderIds",
            "SELECT id FROM purchase ORDER BY id") { cursor: SqlCursor -> cursor.getLong(0)!! }

        fun itemCount(id: Long): Query<Long> = SingleOrderQuery(id, ITEM_COUNT,
            "SELECT json_array_length(items) FROM purchase WHERE id = ?") { cursor: SqlCursor -> cursor.getLong(0)!! }

        fun expressOrderIds(): Query<Long> = Query(EXPRESS, arrayOf("purchase"), driver, "Shop.sq", "expressOrderIds",
            "SELECT id FROM purchase WHERE json_extract(shipping, '$.express') ORDER BY id") { cursor: SqlCursor ->
            cursor.getLong(0)!!
        }

        fun unitsSoldPerSku(): Query<SkuTotal> = Query(
            UNITS_PER_SKU,
            arrayOf("purchase"),
            driver,
            "Shop.sq",
            "unitsSoldPerSku",
            """
            SELECT json_extract(item.value, '$.sku') AS sku, sum(json_extract(item.value, '$.qty')) AS units
            FROM purchase, json_each(purchase.items) AS item
            GROUP BY sku
            ORDER BY units DESC, sku
            """.trimIndent(),
        ) { cursor: SqlCursor -> SkuTotal(cursor.getString(0)!!, cursor.getLong(1)!!) }

        fun orderTotals(): Query<Pair<Long, Long>> = Query(
            ORDER_TOTALS,
            arrayOf("purchase"),
            driver,
            "Shop.sq",
            "orderTotals",
            """
            SELECT purchase.id, sum(json_extract(item.value, '$.qty') * json_extract(item.value, '$.price'))
            FROM purchase, json_each(purchase.items) AS item
            GROUP BY purchase.id
            ORDER BY purchase.id
            """.trimIndent(),
        ) { cursor: SqlCursor -> cursor.getLong(0)!! to cursor.getLong(1)!! }

        fun orderIdsPerCity(): Query<Pair<String, String>> = Query(
            IDS_PER_CITY,
            arrayOf("purchase"),
            driver,
            "Shop.sq",
            "orderIdsPerCity",
            """
            SELECT city, json_group_array(id)
            FROM (SELECT json_extract(shipping, '$.city') AS city, id FROM purchase ORDER BY id)
            GROUP BY city
            ORDER BY city
            """.trimIndent(),
        ) { cursor: SqlCursor -> cursor.getString(0)!! to cursor.getString(1)!! }

        fun cityLookupPlan(): Query<String> = Query(
            CITY_PLAN,
            arrayOf("purchase"),
            driver,
            "Shop.sq",
            "cityLookupPlan",
            "EXPLAIN QUERY PLAN SELECT id FROM purchase WHERE json_extract(shipping, '$.city') = 'Berlin'",
        ) { cursor: SqlCursor -> cursor.getString(3)!! }

        private fun mapOrder(cursor: SqlCursor): Order =
            Order(
                cursor.getLong(0)!!,
                itemsAdapter.decode(cursor.getString(1)!!),
                Shipping(cursor.getString(2)!!, cursor.getLong(3)!! == 1L),
            )

        /** A query for one order, shaped like the inner query classes SQLDelight generates. */
        private open inner class SingleOrderQuery<T : Any>(
            private val id: Long,
            private val identifier: Int,
            private val sql: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {

            override fun addListener(listener: Query.Listener) {
                driver.addListener("purchase", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("purchase", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(identifier, sql, mapper, 1) {
                    bindLong(0, id)
                }
        }

        private inner class OrderByIdQuery(id: Long) : SingleOrderQuery<Order>(
            id,
            ORDER_BY_ID,
            """
            SELECT id, items, json_extract(shipping, '$.city'), json_extract(shipping, '$.express')
            FROM purchase WHERE id = ?
            """.trimIndent(),
            { cursor: SqlCursor -> mapOrder(cursor) },
        )

        private inner class ShippedToQuery(private val city: String) : Query<Order>({ cursor: SqlCursor ->
            mapOrder(cursor)
        }) {

            override fun addListener(listener: Query.Listener) {
                driver.addListener("purchase", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("purchase", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(
                    SHIPPED_TO,
                    """
                    SELECT id, items, json_extract(shipping, '$.city'), json_extract(shipping, '$.express')
                    FROM purchase WHERE json_extract(shipping, '$.city') = ?
                    ORDER BY id
                    """.trimIndent(),
                    mapper,
                    1,
                ) {
                    bindString(0, city)
                }
        }

        companion object {
            val itemsAdapter: ColumnAdapter<List<LineItem>, String> = LineItemsAdapter

            private const val PLACE: Int = 1
            private const val PLACE_RAW: Int = 2
            private const val CHANGE_QTY: Int = 3
            private const val APPEND: Int = 4
            private const val ORDER_BY_ID: Int = 5
            private const val SHIPPED_TO: Int = 6
            private const val ALL_IDS: Int = 7
            private const val ITEM_COUNT: Int = 8
            private const val EXPRESS: Int = 9
            private const val UNITS_PER_SKU: Int = 10
            private const val ORDER_TOTALS: Int = 11
            private const val IDS_PER_CITY: Int = 12
            private const val CITY_PLAN: Int = 13
        }
    }
}
