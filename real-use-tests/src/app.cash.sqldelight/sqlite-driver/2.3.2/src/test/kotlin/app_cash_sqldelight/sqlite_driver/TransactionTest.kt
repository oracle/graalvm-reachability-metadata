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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Transactions and deferred query-change notification on an in-memory `JdbcSqliteDriver`.
 *
 * A small ledger application, shaped like a SQLDelight-generated database, moves money between
 * accounts inside transactions. A screen observes the account list through a `Query` listener and
 * must only be told about changes that were committed, while commit and rollback hooks drive
 * side effects such as an outbox of sent receipts.
 */
class TransactionTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var ledger: LedgerDatabase
    private lateinit var accounts: Query<Account>
    private lateinit var screen: CountingListener

    @BeforeEach
    fun openLedger() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ledger = LedgerDatabase(driver)
        ledger.createSchema()
        ledger.openAccount("alice", 100)
        ledger.openAccount("bob", 20)
        accounts = ledger.selectAccounts()
        screen = CountingListener()
        accounts.addListener(screen)
    }

    @AfterEach
    fun closeLedger() {
        accounts.removeListener(screen)
        driver.close()
    }

    @Test
    fun committedTransferIsVisibleAndNotifiesObserversOnce() {
        val outbox: MutableList<String> = mutableListOf()

        ledger.transaction {
            ledger.transfer("alice", "bob", 30)
            ledger.transfer("alice", "bob", 5)
            afterCommit { outbox += "receipt for bob" }
            assertEquals(0, screen.changes, "observers must wait for the commit")
        }

        assertEquals(listOf(Account("alice", 65), Account("bob", 55)), accounts.executeAsList())
        assertEquals(1, screen.changes)
        assertEquals(listOf("receipt for bob"), outbox)
    }

    @Test
    fun explicitRollbackDiscardsChangesAndSkipsNotification() {
        val outbox: MutableList<String> = mutableListOf()

        ledger.transaction {
            ledger.transfer("bob", "alice", 15)
            afterCommit { outbox += "receipt" }
            afterRollback { outbox += "transfer cancelled" }
            if (ledger.balance("bob") < 10) rollback()
            outbox += "unreachable"
        }

        assertEquals(listOf(Account("alice", 100), Account("bob", 20)), accounts.executeAsList())
        assertEquals(0, screen.changes)
        assertEquals(listOf("transfer cancelled"), outbox)
    }

    @Test
    fun failureInsideTransactionRollsBackAndPropagates() {
        val failure: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            ledger.transaction {
                ledger.transfer("alice", "bob", 40)
                check(ledger.balance("carol") >= 0) { "unknown account carol" }
            }
        }

        assertEquals("unknown account carol", failure.message)
        assertEquals(listOf(Account("alice", 100), Account("bob", 20)), accounts.executeAsList())
        assertEquals(0, screen.changes)
    }

    @Test
    fun transactionWithResultReturnsTheBodyOrTheRollbackValue() {
        val committed: Long = ledger.transactionWithResult {
            ledger.transfer("alice", "bob", 10)
            ledger.balance("bob")
        }
        val refused: Long = ledger.transactionWithResult {
            ledger.transfer("bob", "alice", 500)
            if (ledger.balance("bob") < 0) rollback(-1L)
            ledger.balance("bob")
        }

        assertEquals(30L, committed)
        assertEquals(-1L, refused)
        assertEquals(listOf(Account("alice", 90), Account("bob", 30)), accounts.executeAsList())
        assertEquals(1, screen.changes)
    }

    @Test
    fun nestedTransactionsCommitTogetherWithTheOutermost() {
        val events: MutableList<String> = mutableListOf()

        ledger.transaction {
            ledger.transfer("alice", "bob", 10)
            transaction {
                ledger.transfer("bob", "alice", 3)
                afterCommit { events += "inner committed" }
            }
            assertEquals(0, screen.changes, "nested commit must not notify before the outer one")
            afterCommit { events += "outer committed" }
        }

        assertEquals(listOf(Account("alice", 93), Account("bob", 27)), accounts.executeAsList())
        assertEquals(1, screen.changes)
        assertEquals(listOf("inner committed", "outer committed"), events)
    }

    @Test
    fun aTransactionThatMustNotBeNestedRejectsAnEnclosingOne() {
        assertThrows(IllegalStateException::class.java) {
            ledger.transaction {
                ledger.transfer("alice", "bob", 1)
                ledger.transaction(noEnclosing = true) {
                    ledger.transfer("alice", "bob", 1)
                }
            }
        }

        assertEquals(listOf(Account("alice", 100), Account("bob", 20)), accounts.executeAsList())
        assertEquals(0, screen.changes)
    }

    /** One row of the `account` table. */
    data class Account(val owner: String, val balance: Long)

    /** Counts how often the observed query reported changed results. */
    class CountingListener : Query.Listener {
        var changes: Int = 0
            private set

        override fun queryResultsChanged() {
            changes++
        }
    }

    /** The ledger's database, written the way SQLDelight generates a database and its queries. */
    class LedgerDatabase(driver: SqlDriver) : TransacterImpl(driver) {

        fun createSchema() {
            driver.execute(
                null,
                "CREATE TABLE account (owner TEXT PRIMARY KEY, balance INTEGER NOT NULL)",
                0,
            )
        }

        fun openAccount(owner: String, balance: Long) {
            driver.execute(OPEN_ACCOUNT, "INSERT INTO account (owner, balance) VALUES (?, ?)", 2) {
                bindString(0, owner)
                bindLong(1, balance)
            }
            notifyQueries(OPEN_ACCOUNT) { emit: (String) -> Unit -> emit("account") }
        }

        fun selectAccounts(): Query<Account> =
            Query(SELECT_ACCOUNTS, arrayOf("account"), driver, "Ledger.sq", "selectAccounts",
                "SELECT owner, balance FROM account ORDER BY owner") { cursor: SqlCursor ->
                Account(cursor.getString(0)!!, cursor.getLong(1)!!)
            }

        fun balance(owner: String): Long =
            driver.executeQuery(SELECT_BALANCE, "SELECT balance FROM account WHERE owner = ?", { cursor: SqlCursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else -1L)
            }, 1) {
                bindString(0, owner)
            }.value

        fun transfer(from: String, to: String, amount: Long) {
            driver.execute(ADJUST, "UPDATE account SET balance = balance + ? WHERE owner = ?", 2) {
                bindLong(0, -amount)
                bindString(1, from)
            }
            driver.execute(ADJUST, "UPDATE account SET balance = balance + ? WHERE owner = ?", 2) {
                bindLong(0, amount)
                bindString(1, to)
            }
            notifyQueries(ADJUST) { emit: (String) -> Unit -> emit("account") }
        }

        private companion object {
            const val OPEN_ACCOUNT: Int = 1
            const val SELECT_ACCOUNTS: Int = 2
            const val SELECT_BALANCE: Int = 3
            const val ADJUST: Int = 4
        }
    }
}
