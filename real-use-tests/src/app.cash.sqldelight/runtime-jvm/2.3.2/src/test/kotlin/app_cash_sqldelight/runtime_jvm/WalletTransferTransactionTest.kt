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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Transactions through the SQLDelight `Transacter`: a wallet service moves money between
 * accounts inside `transactionWithResult`, rolls a transfer back when the payer cannot cover it,
 * nests a fee-charging transaction inside a transfer, and records an audit trail from the
 * `afterCommit` and `afterRollback` callbacks.
 *
 * `WalletQueries` is written the way the SQLDelight compiler emits a queries class for a
 * `Wallet.sq` file, so the scenario drives the same runtime calls a generated database does.
 */
class WalletTransferTransactionTest {

    private lateinit var driver: SqlDriver
    private lateinit var wallet: WalletQueries
    private lateinit var service: TransferService

    @BeforeEach
    fun openDatabase() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WalletSchema.create(driver)
        wallet = WalletQueries(driver)
        service = TransferService(wallet)
        wallet.openAccount("alice", 100)
        wallet.openAccount("bob", 20)
    }

    @AfterEach
    fun closeDatabase() {
        driver.close()
    }

    @Test
    fun committedTransferMovesFundsAndRunsAfterCommitCallback() {
        val outcome: TransferOutcome = service.transfer(from = "alice", to = "bob", amount = 30)

        assertThat(outcome).isEqualTo(TransferOutcome.Completed(payerBalance = 70))
        assertThat(wallet.balances().executeAsList()).containsExactly(
            Account("alice", 70),
            Account("bob", 50),
        )
        assertThat(service.audit).containsExactly("committed alice->bob 30")
    }

    @Test
    fun rejectedTransferRollsBackDebitAndReturnsRollbackValue() {
        val outcome: TransferOutcome = service.transfer(from = "bob", to = "alice", amount = 45)

        assertThat(outcome).isEqualTo(TransferOutcome.Rejected(shortfall = 25))
        assertThat(wallet.balance("bob").executeAsOne()).isEqualTo(20L)
        assertThat(wallet.balance("alice").executeAsOne()).isEqualTo(100L)
        assertThat(service.audit).containsExactly("rolled back bob->alice 45")
    }

    @Test
    fun failureInNestedFeeTransactionRollsBackWholeTransfer() {
        wallet.openAccount("fees", 0)

        assertThatThrownBy {
            service.transferWithFee(from = "alice", to = "bob", amount = 10, fee = 5, feeAccount = "treasury")
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("unknown account treasury")

        assertThat(wallet.balances().executeAsList()).containsExactly(
            Account("alice", 100),
            Account("bob", 20),
            Account("fees", 0),
        )
        assertThat(service.audit).containsExactly("rolled back alice->bob 10 (fee 5)")
    }

    @Test
    fun nestedFeeTransactionCommitsOnceWithOuterTransfer() {
        wallet.openAccount("fees", 0)

        service.transferWithFee(from = "alice", to = "bob", amount = 10, fee = 5, feeAccount = "fees")

        assertThat(wallet.balances().executeAsList()).containsExactly(
            Account("alice", 85),
            Account("bob", 30),
            Account("fees", 5),
        )
        // The nested callback joins the outer transaction's list, after the hooks it already holds.
        assertThat(service.audit).containsExactly("committed alice->bob 10 (fee 5)", "fee 5 settled")
    }

    /** One row of the `account` table. */
    data class Account(val owner: String, val balance: Long)

    /** Result an application returns to its caller after a transfer attempt. */
    sealed interface TransferOutcome {
        data class Completed(val payerBalance: Long) : TransferOutcome

        data class Rejected(val shortfall: Long) : TransferOutcome
    }

    /** Application service that composes the queries into transactional transfers. */
    class TransferService(private val wallet: WalletQueries) {
        val audit: MutableList<String> = mutableListOf()

        fun transfer(from: String, to: String, amount: Long): TransferOutcome =
            wallet.transactionWithResult {
                val label = "$from->$to $amount"
                afterCommit { audit += "committed $label" }
                afterRollback { audit += "rolled back $label" }

                wallet.adjust(from, -amount)
                wallet.adjust(to, amount)
                val payerBalance: Long = wallet.balance(from).executeAsOne()
                if (payerBalance < 0) {
                    rollback(TransferOutcome.Rejected(shortfall = -payerBalance))
                }
                TransferOutcome.Completed(payerBalance)
            }

        fun transferWithFee(from: String, to: String, amount: Long, fee: Long, feeAccount: String) {
            wallet.transaction {
                val label = "$from->$to $amount (fee $fee)"
                afterCommit { audit += "committed $label" }
                afterRollback { audit += "rolled back $label" }

                wallet.adjust(from, -amount)
                wallet.adjust(to, amount)
                wallet.transaction {
                    afterCommit { audit += "fee $fee settled" }
                    wallet.adjust(from, -fee)
                    wallet.adjust(feeAccount, fee)
                }
            }
        }
    }

    /** Schema the SQLDelight compiler would generate from the `CREATE TABLE` in `Wallet.sq`. */
    object WalletSchema {
        fun create(driver: SqlDriver) {
            driver.execute(
                null,
                """
                CREATE TABLE account (
                  owner TEXT NOT NULL PRIMARY KEY,
                  balance INTEGER NOT NULL
                )
                """.trimIndent(),
                0,
            )
        }
    }

    /** Hand-written equivalent of the queries class generated for `Wallet.sq`. */
    class WalletQueries(driver: SqlDriver) : TransacterImpl(driver) {

        fun balances(): Query<Account> = Query(
            BALANCES_ID,
            arrayOf("account"),
            driver,
            "Wallet.sq",
            "balances",
            "SELECT owner, balance FROM account ORDER BY owner",
        ) { cursor: SqlCursor -> Account(cursor.getString(0)!!, cursor.getLong(1)!!) }

        fun balance(owner: String): Query<Long> = BalanceQuery(owner) { cursor: SqlCursor -> cursor.getLong(0)!! }

        fun openAccount(owner: String, balance: Long) {
            driver.execute(OPEN_ACCOUNT_ID, "INSERT INTO account (owner, balance) VALUES (?, ?)", 2) {
                bindString(0, owner)
                bindLong(1, balance)
            }
            notifyQueries(OPEN_ACCOUNT_ID) { emit -> emit("account") }
        }

        fun adjust(owner: String, delta: Long) {
            val updated: Long = driver.execute(
                ADJUST_ID,
                "UPDATE account SET balance = balance + ? WHERE owner = ?",
                2,
            ) {
                bindLong(0, delta)
                bindString(1, owner)
            }.value
            check(updated == 1L) { "unknown account $owner" }
            notifyQueries(ADJUST_ID) { emit -> emit("account") }
        }

        private inner class BalanceQuery<T : Any>(
            private val owner: String,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {
            override fun addListener(listener: Listener) {
                driver.addListener("account", listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener("account", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(BALANCE_ID, "SELECT balance FROM account WHERE owner = ?", mapper, 1) {
                    bindString(0, owner)
                }

            override fun toString(): String = "Wallet.sq:balance"
        }

        private companion object {
            const val BALANCES_ID: Int = 1
            const val BALANCE_ID: Int = 2
            const val OPEN_ACCOUNT_ID: Int = 3
            const val ADJUST_ID: Int = 4
        }
    }
}
