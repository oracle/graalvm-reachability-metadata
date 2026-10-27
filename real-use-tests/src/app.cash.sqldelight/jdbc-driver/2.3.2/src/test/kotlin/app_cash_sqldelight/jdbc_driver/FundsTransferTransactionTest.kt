/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC transaction commit and rollback for atomic account transfers.
 */
@Timeout(60)
class FundsTransferTransactionTest {
    @Test
    fun commitsTransfersAndRollsBackAnOverdraftWithoutLeavingAnAuditRecord() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:funds_transfer_transactions")
        }
        // Retain the database while the driver returns borrowed connections.
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                val ledger: Ledger = Ledger(driver)
                ledger.createAccounts()

                ledger.transfer("alice", "bob", 250L)
                assertEquals(mapOf("alice" to 750L, "bob" to 450L), ledger.balances())
                assertEquals(1L, ledger.completedTransfers())

                assertThrows(InsufficientFunds::class.java) {
                    ledger.transfer("alice", "bob", 900L)
                }
                // Both balance changes and the audit insert preceded the rejection.
                assertEquals(mapOf("alice" to 750L, "bob" to 450L), ledger.balances())
                assertEquals(1L, ledger.completedTransfers())

                // The connection remains usable after rollback for the next request.
                ledger.transfer("bob", "alice", 100L)
                assertEquals(mapOf("alice" to 850L, "bob" to 350L), ledger.balances())
                assertEquals(2L, ledger.completedTransfers())
            }
        }
    }

    private class Ledger(private val jdbc: JdbcDriver) : TransacterImpl(jdbc) {
        fun createAccounts() {
            jdbc.execute(
                null,
                "CREATE TABLE accounts (owner VARCHAR(40) PRIMARY KEY, balance BIGINT NOT NULL)",
                0
            )
            jdbc.execute(
                null,
                """
                CREATE TABLE transfers (
                    sender VARCHAR(40) NOT NULL,
                    recipient VARCHAR(40) NOT NULL,
                    amount BIGINT NOT NULL
                )
                """.trimIndent(),
                0
            )
            jdbc.execute(null, "INSERT INTO accounts VALUES ('alice', 1000), ('bob', 200)", 0)
        }

        fun transfer(sender: String, recipient: String, amount: Long) {
            require(amount > 0L)
            transaction {
                changeBalance(sender, -amount)
                changeBalance(recipient, amount)
                jdbc.execute(
                    null,
                    "INSERT INTO transfers (sender, recipient, amount) VALUES (?, ?, ?)",
                    3
                ) {
                    bindString(0, sender)
                    bindString(1, recipient)
                    bindLong(2, amount)
                }
                if (balances().getValue(sender) < 0L) {
                    throw InsufficientFunds()
                }
            }
        }

        fun balances(): Map<String, Long> = jdbc.executeQuery(
            null,
            "SELECT owner, balance FROM accounts ORDER BY owner",
            mapper = { cursor ->
                val balances: MutableMap<String, Long> = linkedMapOf()
                while (cursor.next().value) {
                    balances[requireNotNull(cursor.getString(0))] = requireNotNull(cursor.getLong(1))
                }
                QueryResult.Value(balances.toMap())
            },
            parameters = 0
        ).value

        fun completedTransfers(): Long = jdbc.executeQuery(
            null,
            "SELECT COUNT(*) FROM transfers",
            mapper = { cursor ->
                check(cursor.next().value)
                QueryResult.Value(requireNotNull(cursor.getLong(0)))
            },
            parameters = 0
        ).value

        private fun changeBalance(owner: String, delta: Long) {
            val changed: Long = jdbc.execute(
                null,
                "UPDATE accounts SET balance = balance + ? WHERE owner = ?",
                2
            ) {
                bindLong(0, delta)
                bindString(1, owner)
            }.value
            check(changed == 1L) { "Account not found: $owner" }
        }
    }

    private class InsufficientFunds : IllegalStateException("Insufficient funds")
}
