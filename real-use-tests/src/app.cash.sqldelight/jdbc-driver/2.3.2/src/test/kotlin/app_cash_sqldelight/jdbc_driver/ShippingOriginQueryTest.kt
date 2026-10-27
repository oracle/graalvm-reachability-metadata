/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC-backed SQLDelight query materialization and cardinality for shipping-origin selection.
 */
@Timeout(60)
class ShippingOriginQueryTest {
    @Test
    fun selectsTheConfiguredOriginAndRejectsAmbiguousDispatchConfiguration() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:shipping_origin_queries")
        }
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    """
                    CREATE TABLE shipping_origins (
                        code VARCHAR(40) PRIMARY KEY,
                        postal_code VARCHAR(20) NOT NULL,
                        active BOOLEAN NOT NULL
                    )
                    """.trimIndent(),
                    0
                )
                val dispatch: DispatchConfiguration = DispatchConfiguration(driver)
                val fallback: Origin = Origin("retail-store", "10001")
                val east: Origin = Origin("east-depot", "02108")
                val west: Origin = Origin("west-depot", "94105")

                assertEquals(emptyList<Origin>(), dispatch.configuredOrigins())
                assertEquals(fallback, dispatch.shippingOrigin(fallback))

                dispatch.register(east)
                assertEquals(east, dispatch.shippingOrigin(fallback))
                assertEquals(listOf(east), dispatch.configuredOrigins())

                // Never choose an arbitrary warehouse if an operator activates two origins.
                dispatch.register(west)
                assertThrows(IllegalStateException::class.java) {
                    dispatch.shippingOrigin(fallback)
                }
                assertEquals(listOf(east, west), dispatch.configuredOrigins())

                // Reuse the same query after correcting the configuration; no stale rows remain.
                dispatch.deactivate(east.code)
                assertEquals(west, dispatch.shippingOrigin(fallback))
                assertEquals(listOf(east, west), dispatch.configuredOrigins())

                dispatch.deactivate(west.code)
                assertEquals(fallback, dispatch.shippingOrigin(fallback))
                assertEquals(listOf(east, west), dispatch.configuredOrigins())
            }
        }
    }

    private class DispatchConfiguration(private val driver: JdbcDriver) {
        private val activeOrigin: ExecutableQuery<Origin> = Query(
            111001,
            driver,
            "SELECT code, postal_code FROM shipping_origins WHERE active = TRUE ORDER BY code"
        ) { cursor ->
            Origin(requireNotNull(cursor.getString(0)), requireNotNull(cursor.getString(1)))
        }

        private val allOrigins: ExecutableQuery<Origin> = Query(
            111002,
            driver,
            "SELECT code, postal_code FROM shipping_origins ORDER BY code"
        ) { cursor ->
            Origin(requireNotNull(cursor.getString(0)), requireNotNull(cursor.getString(1)))
        }

        fun shippingOrigin(fallback: Origin): Origin = activeOrigin.executeAsOneOrNull() ?: fallback

        fun configuredOrigins(): List<Origin> = allOrigins.executeAsList()

        fun register(origin: Origin) {
            val inserted: Long = driver.execute(
                null,
                "INSERT INTO shipping_origins (code, postal_code, active) VALUES (?, ?, TRUE)",
                2
            ) {
                bindString(0, origin.code)
                bindString(1, origin.postalCode)
            }.value
            check(inserted == 1L)
        }

        fun deactivate(code: String) {
            val changed: Long = driver.execute(
                null,
                "UPDATE shipping_origins SET active = FALSE WHERE code = ?",
                1
            ) { bindString(0, code) }.value
            check(changed == 1L)
        }
    }

    private data class Origin(val code: String, val postalCode: String)
}
