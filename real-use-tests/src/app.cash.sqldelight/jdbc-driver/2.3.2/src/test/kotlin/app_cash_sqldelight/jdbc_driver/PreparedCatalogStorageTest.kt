/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC prepared-statement binding and cursor mapping for a product catalog.
 */
@Timeout(60)
class PreparedCatalogStorageTest {
    @Test
    fun storesPhysicalAndDigitalProductsWithNullableShippingDetails() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:prepared_catalog")
        }
        // Keep the in-memory database alive while the driver borrows short-lived connections.
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    """
                    CREATE TABLE catalog (
                        id BIGINT PRIMARY KEY,
                        title VARCHAR(200) NOT NULL,
                        shipping_weight DOUBLE PRECISION,
                        available BOOLEAN NOT NULL,
                        thumbnail VARBINARY(100)
                    )
                    """.trimIndent(),
                    0
                )
                val photo: ByteArray = byteArrayOf(0, 1, 127, -128, -1)
                val physical: CatalogItem = CatalogItem(101L, "Maker's kit", 1.25, true, photo)
                val digital: CatalogItem = CatalogItem(102L, "Guide ' OR 1=1 --", null, false, null)
                insert(driver, physical)
                insert(driver, digital)

                val items: List<CatalogItem> = driver.executeQuery(
                    null,
                    """
                    SELECT id, title, shipping_weight, available, thumbnail
                    FROM catalog
                    WHERE id >= ?
                    ORDER BY id
                    """.trimIndent(),
                    mapper = { cursor ->
                        val rows: MutableList<CatalogItem> = mutableListOf()
                        while (cursor.next().value) {
                            rows.add(
                                CatalogItem(
                                    id = requireNotNull(cursor.getLong(0)),
                                    title = requireNotNull(cursor.getString(1)),
                                    shippingWeight = cursor.getDouble(2),
                                    available = requireNotNull(cursor.getBoolean(3)),
                                    thumbnail = cursor.getBytes(4)
                                )
                            )
                        }
                        QueryResult.Value(rows.toList())
                    },
                    parameters = 1,
                    binders = { bindLong(0, 101L) }
                ).value

                assertEquals(2, items.size)
                val storedPhysical: CatalogItem = items[0]
                assertEquals(physical.id, storedPhysical.id)
                assertEquals(physical.title, storedPhysical.title)
                assertEquals(physical.shippingWeight, storedPhysical.shippingWeight)
                assertTrue(storedPhysical.available)
                assertArrayEquals(photo, storedPhysical.thumbnail)

                val storedDigital: CatalogItem = items[1]
                assertEquals(digital.id, storedDigital.id)
                assertEquals(digital.title, storedDigital.title)
                assertNull(storedDigital.shippingWeight)
                assertFalse(storedDigital.available)
                assertNull(storedDigital.thumbnail)
            }
        }
    }

    private fun insert(driver: JdbcDriver, item: CatalogItem) {
        val inserted: Long = driver.execute(
            null,
            """
            INSERT INTO catalog (id, title, shipping_weight, available, thumbnail)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            5
        ) {
            bindLong(0, item.id)
            bindString(1, item.title)
            bindDouble(2, item.shippingWeight)
            bindBoolean(3, item.available)
            bindBytes(4, item.thumbnail)
        }.value
        assertEquals(1L, inserted)
    }

    private data class CatalogItem(
        val id: Long,
        val title: String,
        val shippingWeight: Double?,
        val available: Boolean,
        val thumbnail: ByteArray?
    )
}
