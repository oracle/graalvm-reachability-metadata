/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.runtime_jvm

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.adapter.primitive.FloatColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.adapter.primitive.ShortColumnAdapter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Column type mapping through `ColumnAdapter`: an inventory app stores enums with
 * `EnumColumnAdapter`, narrow numbers with the primitive `Int`, `Short` and `Float` adapters,
 * money as a value class and free-form attributes as a JSON document, and reads them back as
 * the same Kotlin types.
 *
 * `Item.Adapter` and `InventoryQueries` are written the way the SQLDelight compiler emits them
 * for a `Item.sq` file whose columns declare `AS` Kotlin types, so every value crosses the
 * driver through the adapters a generated database is constructed with.
 */
class InventoryColumnAdapterTest {

    private lateinit var driver: SqlDriver
    private lateinit var inventory: InventoryQueries

    @BeforeEach
    fun openDatabase() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        InventorySchema.create(driver)
        inventory = InventoryQueries(driver, ITEM_ADAPTER)
    }

    @AfterEach
    fun closeDatabase() {
        driver.close()
    }

    @Test
    fun itemRoundTripsEveryAdaptedColumn() {
        val lamp = Item(
            sku = "LMP-01",
            category = Category.LIGHTING,
            quantity = 12,
            shelf = 4,
            weightKg = 1.25f,
            price = Cents(3_999),
            attributes = buildJsonObject {
                put("color", "brass")
                put("bulbs", 2)
            },
        )

        inventory.insert(lamp)

        val stored = inventory.bySku("LMP-01").executeAsOne()
        assertThat(stored).isEqualTo(lamp)
        assertThat(stored.attributes!!["bulbs"]!!.jsonPrimitive.int).isEqualTo(2)
        assertThat(stored.price.formatted()).isEqualTo("39.99")
    }

    @Test
    fun adaptersStoreDatabaseFriendlyValues() {
        inventory.insert(
            Item(
                sku = "CHR-07",
                category = Category.FURNITURE,
                quantity = 3,
                shelf = 11,
                weightKg = 6.5f,
                price = Cents(12_950),
                attributes = buildJsonObject { put("material", "oak") },
            ),
        )

        val raw = driver.executeQuery(
            null,
            "SELECT category, quantity, shelf, weight_kg, price_cents, attributes FROM item",
            { cursor: SqlCursor ->
                cursor.next()
                QueryResult.Value(
                    listOf(
                        cursor.getString(0),
                        cursor.getLong(1),
                        cursor.getLong(2),
                        cursor.getDouble(3),
                        cursor.getLong(4),
                        cursor.getString(5),
                    ),
                )
            },
            0,
        ).value

        assertThat(raw).containsExactly("FURNITURE", 3L, 11L, 6.5, 12_950L, """{"material":"oak"}""")
    }

    @Test
    fun enumParameterIsEncodedForFiltering() {
        inventory.insert(item("LMP-01", Category.LIGHTING, quantity = 12))
        inventory.insert(item("CHR-07", Category.FURNITURE, quantity = 3))
        inventory.insert(item("LMP-02", Category.LIGHTING, quantity = 0))

        val lighting = inventory.inCategory(Category.LIGHTING).executeAsList()
        val furniture = inventory.inCategory(Category.FURNITURE).executeAsList()
        val outdoor = inventory.inCategory(Category.OUTDOOR).executeAsOneOrNull()

        assertThat(lighting.map { it.sku }).containsExactly("LMP-01", "LMP-02")
        assertThat(lighting.map { it.quantity }).containsExactly(12, 0)
        assertThat(furniture.single().category).isEqualTo(Category.FURNITURE)
        assertThat(outdoor).isNull()
    }

    @Test
    fun nullableColumnSkipsItsAdapter() {
        inventory.insert(item("RUG-03", Category.FURNITURE, quantity = 1))

        val rug = inventory.bySku("RUG-03").executeAsOne()

        assertThat(rug.attributes).isNull()
        assertThat(rug.shelf).isEqualTo(1.toShort())
    }

    @Test
    fun unknownEnumNameInDatabaseFailsToDecode() {
        inventory.insert(item("LMP-01", Category.LIGHTING, quantity = 12))
        driver.execute(null, "UPDATE item SET category = 'DISCONTINUED' WHERE sku = 'LMP-01'", 0)

        assertThatThrownBy { inventory.bySku("LMP-01").executeAsOne() }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    private fun item(sku: String, category: Category, quantity: Int): Item = Item(
        sku = sku,
        category = category,
        quantity = quantity,
        shelf = 1,
        weightKg = 0.5f,
        price = Cents(500),
        attributes = null,
    )

    /** Product category, stored by name through `EnumColumnAdapter`. */
    enum class Category { LIGHTING, FURNITURE, OUTDOOR }

    /** Price in cents, stored as an `INTEGER` column. */
    @JvmInline
    value class Cents(val amount: Long) {
        fun formatted(): String = "%d.%02d".format(amount / 100, amount % 100)
    }

    /** One row of the `item` table, with the Kotlin types its columns declare. */
    data class Item(
        val sku: String,
        val category: Category,
        val quantity: Int,
        val shelf: Short,
        val weightKg: Float,
        val price: Cents,
        val attributes: JsonObject?,
    ) {
        /** Adapters the generated `Item.Adapter` class asks for, one per column with an `AS` type. */
        class Adapter(
            val categoryAdapter: ColumnAdapter<Category, String>,
            val quantityAdapter: ColumnAdapter<Int, Long>,
            val shelfAdapter: ColumnAdapter<Short, Long>,
            val weight_kgAdapter: ColumnAdapter<Float, Double>,
            val price_centsAdapter: ColumnAdapter<Cents, Long>,
            val attributesAdapter: ColumnAdapter<JsonObject, String>,
        )
    }

    /** Application adapter mapping the `Cents` value class to its `INTEGER` column. */
    object CentsAdapter : ColumnAdapter<Cents, Long> {
        override fun decode(databaseValue: Long): Cents = Cents(databaseValue)

        override fun encode(value: Cents): Long = value.amount
    }

    /** Application adapter storing a JSON object in a `TEXT` column. */
    object JsonObjectAdapter : ColumnAdapter<JsonObject, String> {
        override fun decode(databaseValue: String): JsonObject =
            Json.parseToJsonElement(databaseValue) as JsonObject

        override fun encode(value: JsonObject): String = Json.encodeToString(JsonObject.serializer(), value)
    }

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE` in `Item.sq`. */
    object InventorySchema {
        fun create(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE item (
                  sku TEXT NOT NULL PRIMARY KEY,
                  category TEXT NOT NULL,
                  quantity INTEGER NOT NULL,
                  shelf INTEGER NOT NULL,
                  weight_kg REAL NOT NULL,
                  price_cents INTEGER NOT NULL,
                  attributes TEXT
                )
                """.trimIndent(),
                0,
            )
        }
    }

    /** Hand-written equivalent of the queries class generated for `Item.sq`. */
    class InventoryQueries(driver: SqlDriver, private val itemAdapter: Item.Adapter) : TransacterImpl(driver) {

        fun bySku(sku: String): Query<Item> = BySkuQuery(sku) { cursor -> mapItem(cursor) }

        fun inCategory(category: Category): Query<Item> = InCategoryQuery(category) { cursor -> mapItem(cursor) }

        fun insert(item: Item) {
            driver.execute(
                INSERT_ID,
                """
                INSERT INTO item (sku, category, quantity, shelf, weight_kg, price_cents, attributes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                7,
            ) {
                bindString(0, item.sku)
                bindString(1, itemAdapter.categoryAdapter.encode(item.category))
                bindLong(2, itemAdapter.quantityAdapter.encode(item.quantity))
                bindLong(3, itemAdapter.shelfAdapter.encode(item.shelf))
                bindDouble(4, itemAdapter.weight_kgAdapter.encode(item.weightKg))
                bindLong(5, itemAdapter.price_centsAdapter.encode(item.price))
                bindString(6, item.attributes?.let { itemAdapter.attributesAdapter.encode(it) })
            }
            notifyQueries(INSERT_ID) { emit -> emit("item") }
        }

        private fun mapItem(cursor: SqlCursor): Item = Item(
            cursor.getString(0)!!,
            itemAdapter.categoryAdapter.decode(cursor.getString(1)!!),
            itemAdapter.quantityAdapter.decode(cursor.getLong(2)!!),
            itemAdapter.shelfAdapter.decode(cursor.getLong(3)!!),
            itemAdapter.weight_kgAdapter.decode(cursor.getDouble(4)!!),
            itemAdapter.price_centsAdapter.decode(cursor.getLong(5)!!),
            cursor.getString(6)?.let { itemAdapter.attributesAdapter.decode(it) },
        )

        private inner class BySkuQuery<out T : Any>(
            private val sku: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Listener) {
                driver.addListener("item", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("item", listener = listener)
            }

            override fun <R> execute(
                mapper: (SqlCursor) -> QueryResult<R>,
            ): QueryResult<R> = driver.executeQuery(
                BY_SKU_ID,
                "$SELECT_COLUMNS WHERE sku = ?",
                mapper,
                1,
            ) {
                bindString(0, sku)
            }

            override fun toString(): String = "Item.sq:bySku"
        }

        private inner class InCategoryQuery<out T : Any>(
            private val category: Category,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Listener) {
                driver.addListener("item", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("item", listener = listener)
            }

            override fun <R> execute(
                mapper: (SqlCursor) -> QueryResult<R>,
            ): QueryResult<R> = driver.executeQuery(
                IN_CATEGORY_ID,
                "$SELECT_COLUMNS WHERE category = ? ORDER BY sku",
                mapper,
                1,
            ) {
                bindString(0, itemAdapter.categoryAdapter.encode(category))
            }

            override fun toString(): String = "Item.sq:inCategory"
        }

        private companion object {
            const val SELECT_COLUMNS: String =
                "SELECT sku, category, quantity, shelf, weight_kg, price_cents, attributes FROM item"
            const val BY_SKU_ID: Int = 1
            const val IN_CATEGORY_ID: Int = 2
            const val INSERT_ID: Int = 3
        }
    }

    private companion object {
        /** Adapters handed to the generated database constructor, as an application wires them. */
        val ITEM_ADAPTER: Item.Adapter = Item.Adapter(
            categoryAdapter = EnumColumnAdapter(),
            quantityAdapter = IntColumnAdapter,
            shelfAdapter = ShortColumnAdapter,
            weight_kgAdapter = FloatColumnAdapter,
            price_centsAdapter = CentsAdapter,
            attributesAdapter = JsonObjectAdapter,
        )
    }
}
