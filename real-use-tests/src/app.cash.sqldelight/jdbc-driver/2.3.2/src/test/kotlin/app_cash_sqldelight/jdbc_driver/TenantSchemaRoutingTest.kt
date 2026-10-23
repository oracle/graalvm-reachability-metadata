/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC custom connection management for schema-based tenant isolation.
 */
@Timeout(60)
class TenantSchemaRoutingTest {
    @Test
    fun routesIdenticalTicketIdsToSeparateTenantSchemasOnASharedPool() {
        val config: HikariConfig = HikariConfig().apply {
            dataSource = JdbcDataSource().apply {
                setURL("jdbc:h2:mem:tenant_schema_routing")
            }
            maximumPoolSize = 1
            minimumIdle = 1
            connectionTimeout = 10_000L
            initializationFailTimeout = 10_000L
        }
        HikariDataSource(config).use { pool: HikariDataSource ->
            pool.asJdbcDriver().use { admin: JdbcDriver ->
                for (schema: String in listOf("NORTH", "SOUTH")) {
                    admin.execute(null, "CREATE SCHEMA $schema", 0)
                    admin.execute(
                        null,
                        """
                        CREATE TABLE $schema.tickets (
                            id BIGINT PRIMARY KEY,
                            subject VARCHAR(200) NOT NULL,
                            resolved BOOLEAN NOT NULL
                        )
                        """.trimIndent(),
                        0
                    )
                }
            }

            TenantDriver(pool, "NORTH").use { northDriver: TenantDriver ->
                TenantDriver(pool, "SOUTH").use { southDriver: TenantDriver ->
                    val north: TicketDesk = TicketDesk(northDriver)
                    val south: TicketDesk = TicketDesk(southDriver)
                    north.open(1L, "Replace scanner")
                    south.open(1L, "Reset account")
                    north.open(2L, "Install printer")

                    assertEquals(
                        listOf(Ticket(1L, "Replace scanner", false), Ticket(2L, "Install printer", false)),
                        north.list()
                    )
                    assertEquals(listOf(Ticket(1L, "Reset account", false)), south.list())

                    north.resolve(1L)
                    assertEquals(listOf(Ticket(1L, "Reset account", false)), south.list())
                    south.open(2L, "Update billing address")
                    assertEquals(
                        listOf(Ticket(1L, "Replace scanner", true), Ticket(2L, "Install printer", false)),
                        north.list()
                    )
                    assertEquals(
                        listOf(Ticket(1L, "Reset account", false), Ticket(2L, "Update billing address", false)),
                        south.list()
                    )
                }
            }

            // An unrelated borrower must not inherit either tenant's schema.
            pool.asJdbcDriver().use { admin: JdbcDriver ->
                val schema: String = admin.executeQuery(
                    null,
                    "SELECT CURRENT_SCHEMA",
                    mapper = { cursor ->
                        check(cursor.next().value)
                        QueryResult.Value(requireNotNull(cursor.getString(0)))
                    },
                    parameters = 0
                ).value
                assertEquals("PUBLIC", schema)
            }
        }
    }

    private class TenantDriver(private val source: DataSource, private val schema: String) : JdbcDriver() {
        override fun getConnection(): Connection {
            val connection: Connection = source.connection
            try {
                connection.schema = schema
                return connection
            } catch (failure: Exception) {
                connection.close()
                throw failure
            }
        }

        override fun closeConnection(connection: Connection) {
            try {
                connection.schema = "PUBLIC"
            } finally {
                connection.close()
            }
        }

        // This request/response adapter has no reactive query subscriptions.
        override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

        override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

        override fun notifyListeners(vararg queryKeys: String) = Unit
    }

    private class TicketDesk(private val driver: JdbcDriver) {
        fun open(id: Long, subject: String) {
            val changed: Long = driver.execute(
                null,
                "INSERT INTO tickets (id, subject, resolved) VALUES (?, ?, FALSE)",
                2
            ) {
                bindLong(0, id)
                bindString(1, subject)
            }.value
            check(changed == 1L)
        }

        fun resolve(id: Long) {
            val changed: Long = driver.execute(
                null,
                "UPDATE tickets SET resolved = TRUE WHERE id = ?",
                1
            ) { bindLong(0, id) }.value
            check(changed == 1L)
        }

        fun list(): List<Ticket> = driver.executeQuery(
            null,
            "SELECT id, subject, resolved FROM tickets ORDER BY id",
            mapper = { cursor ->
                val tickets: MutableList<Ticket> = mutableListOf()
                while (cursor.next().value) {
                    tickets.add(
                        Ticket(
                            requireNotNull(cursor.getLong(0)),
                            requireNotNull(cursor.getString(1)),
                            requireNotNull(cursor.getBoolean(2))
                        )
                    )
                }
                QueryResult.Value(tickets.toList())
            },
            parameters = 0
        ).value
    }

    private data class Ticket(val id: Long, val subject: String, val resolved: Boolean)
}
