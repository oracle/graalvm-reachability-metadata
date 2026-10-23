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
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC pooled-connection lifecycle and recovery after contact-directory mapping fails.
 */
@Timeout(60)
class PooledContactDirectoryTest {
    @Test
    fun returnsBorrowedConnectionsAfterSuccessfulAndRejectedDirectoryRequests() {
        val config: HikariConfig = HikariConfig().apply {
            dataSource = JdbcDataSource().apply {
                setURL("jdbc:h2:mem:pooled_contact_directory")
            }
            maximumPoolSize = 1
            minimumIdle = 1
            connectionTimeout = 10_000L
            initializationFailTimeout = 10_000L
        }
        HikariDataSource(config).use { pool: HikariDataSource ->
            pool.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    """
                    CREATE TABLE contacts (
                        id BIGINT PRIMARY KEY,
                        email VARCHAR(200) NOT NULL
                    )
                    """.trimIndent(),
                    0
                )
                val directory: ContactDirectory = ContactDirectory(driver)
                directory.save(1L, "alice@example.test")
                assertEquals(listOf(Contact(1L, "alice@example.test")), directory.list())

                // A legacy import contains an invalid address. Reject it while mapping the result.
                directory.save(2L, "missing-domain")
                assertThrows(InvalidContact::class.java) { directory.list() }

                // With only one pool slot, this request can succeed only if the failed read released it.
                directory.save(2L, "bob@example.test")
                assertEquals(
                    listOf(Contact(1L, "alice@example.test"), Contact(2L, "bob@example.test")),
                    directory.list()
                )

                directory.save(3L, "carol@example.test")
                assertEquals(
                    listOf(
                        Contact(1L, "alice@example.test"),
                        Contact(2L, "bob@example.test"),
                        Contact(3L, "carol@example.test")
                    ),
                    directory.list()
                )
            }
        }
    }

    private class ContactDirectory(private val driver: JdbcDriver) {
        fun save(id: Long, email: String) {
            val changed: Long = driver.execute(
                null,
                "MERGE INTO contacts (id, email) KEY (id) VALUES (?, ?)",
                2
            ) {
                bindLong(0, id)
                bindString(1, email)
            }.value
            check(changed == 1L)
        }

        fun list(): List<Contact> = driver.executeQuery(
            null,
            "SELECT id, email FROM contacts ORDER BY id",
            mapper = { cursor ->
                val contacts: MutableList<Contact> = mutableListOf()
                while (cursor.next().value) {
                    val id: Long = requireNotNull(cursor.getLong(0))
                    val email: String = requireNotNull(cursor.getString(1))
                    if (!email.contains('@')) {
                        throw InvalidContact(id)
                    }
                    contacts.add(Contact(id, email))
                }
                QueryResult.Value(contacts.toList())
            },
            parameters = 0
        ).value
    }

    private data class Contact(val id: Long, val email: String)

    private class InvalidContact(id: Long) : IllegalArgumentException("Invalid email for contact $id")
}
