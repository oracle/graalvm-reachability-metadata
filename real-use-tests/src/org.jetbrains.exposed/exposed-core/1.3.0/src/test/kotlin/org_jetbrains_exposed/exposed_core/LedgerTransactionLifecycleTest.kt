/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.core.statements.expandArgs
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test

/**
 * Transaction lifecycle: a ledger runs money transfers through committed, explicitly rolled
 * back and savepoint-nested transactions, observing them with a `StatementInterceptor`,
 * transaction user data and a `SqlLogger`.
 */
class LedgerTransactionLifecycleTest {

    object Accounts : Table("accounts") {
        val id: Column<String> = varchar("id", 8)
        val balanceCents: Column<Long> = long("balance_cents")

        override val primaryKey = PrimaryKey(id)
    }

    object Transfers : Table("transfers") {
        val id: Column<Int> = integer("id").autoIncrement()
        val fromAccount: Column<String> = varchar("from_account", 8).references(Accounts.id)
        val toAccount: Column<String> = varchar("to_account", 8).references(Accounts.id)
        val amountCents: Column<Long> = long("amount_cents")

        override val primaryKey = PrimaryKey(id)
    }

    class InsufficientFundsException(account: String) : RuntimeException("Insufficient funds on $account")

    /** Records the statement types and commit outcome of every transaction it is registered on. */
    class AuditTrail(private val requestKey: Key<String>) : StatementInterceptor {
        val events: MutableList<String> = mutableListOf()

        override fun afterExecution(
            transaction: Transaction,
            contexts: List<StatementContext>,
            executedStatement: PreparedStatementApi,
        ) {
            contexts.forEach { events += "exec:${it.statement.type}" }
        }

        override fun beforeCommit(transaction: Transaction) {
            events += "beforeCommit:${transaction.getUserData(requestKey)}"
        }

        override fun afterCommit(transaction: Transaction) {
            events += "afterCommit"
        }

        override fun afterRollback(transaction: Transaction) {
            events += "afterRollback"
        }
    }

    /** Keeps the fully expanded SQL of data-changing statements, as an application audit log would. */
    class WriteLogger : SqlLogger {
        val writes: MutableList<String> = mutableListOf()

        override fun log(context: StatementContext, transaction: Transaction) {
            if (context.statement.type == StatementType.UPDATE || context.statement.type == StatementType.INSERT) {
                writes += context.expandArgs(transaction)
            }
        }
    }

    private fun JdbcTransaction.transfer(from: String, to: String, amountCents: Long) {
        val available: Long = Accounts.selectAll().where { Accounts.id eq from }.single()[Accounts.balanceCents]
        if (available < amountCents) {
            throw InsufficientFundsException(from)
        }
        Accounts.update({ Accounts.id eq from }) { it[balanceCents] = balanceCents - amountCents }
        Accounts.update({ Accounts.id eq to }) { it[balanceCents] = balanceCents + amountCents }
        Transfers.insert {
            it[fromAccount] = from
            it[toAccount] = to
            it[Transfers.amountCents] = amountCents
        }
    }

    private fun balances(database: Database): Map<String, Long> = transaction(database) {
        Accounts.selectAll()
            .orderBy(Accounts.id to SortOrder.ASC)
            .associate { it[Accounts.id] to it[Accounts.balanceCents] }
    }

    private fun transferCount(database: Database): Long = transaction(database) {
        Transfers.selectAll().count()
    }

    @Test
    fun committedRolledBackAndNestedTransfersLeaveConsistentBalances() {
        val writeLogger: WriteLogger = WriteLogger()
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:ledger_lifecycle;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            databaseConfig = DatabaseConfig {
                useNestedTransactions = true
                sqlLogger = writeLogger
            },
        )
        val requestKey: Key<String> = Key()

        transaction(database) {
            SchemaUtils.create(Accounts, Transfers)
            Accounts.insert {
                it[id] = "alice"
                it[balanceCents] = 10_000
            }
            Accounts.insert {
                it[id] = "bob"
                it[balanceCents] = 2_500
            }
        }
        writeLogger.writes.clear()

        val committedAudit: AuditTrail = AuditTrail(requestKey)
        transaction(database) {
            registerInterceptor(committedAudit)
            putUserData(requestKey, "req-1")
            transfer("alice", "bob", 3_000)
        }
        assertThat(committedAudit.events).containsExactly(
            "exec:SELECT", "exec:UPDATE", "exec:UPDATE", "exec:INSERT", "beforeCommit:req-1", "afterCommit",
        )
        assertThat(balances(database)).containsExactlyEntriesOf(mapOf("alice" to 7_000L, "bob" to 5_500L))
        assertThat(writeLogger.writes).containsExactly(
            "UPDATE ACCOUNTS SET BALANCE_CENTS=(ACCOUNTS.BALANCE_CENTS - 3000) WHERE ACCOUNTS.ID = 'alice'",
            "UPDATE ACCOUNTS SET BALANCE_CENTS=(ACCOUNTS.BALANCE_CENTS + 3000) WHERE ACCOUNTS.ID = 'bob'",
            "INSERT INTO TRANSFERS (FROM_ACCOUNT, TO_ACCOUNT, AMOUNT_CENTS) VALUES ('alice', 'bob', 3000)",
        )

        val rolledBackAudit: AuditTrail = AuditTrail(requestKey)
        transaction(database) {
            registerInterceptor(rolledBackAudit)
            transfer("bob", "alice", 1_000)
            rollback()
        }
        // The block still ends with a commit, which finds nothing left after the explicit rollback.
        assertThat(rolledBackAudit.events).containsExactly(
            "exec:SELECT", "exec:UPDATE", "exec:UPDATE", "exec:INSERT",
            "afterRollback", "beforeCommit:null", "afterCommit",
        )
        assertThat(balances(database)).containsExactlyEntriesOf(mapOf("alice" to 7_000L, "bob" to 5_500L))

        val failedAudit: AuditTrail = AuditTrail(requestKey)
        assertThatThrownBy {
            transaction(database) {
                registerInterceptor(failedAudit)
                transfer("bob", "alice", 50_000)
            }
        }.isInstanceOf(InsufficientFundsException::class.java)
            .hasMessage("Insufficient funds on bob")
        assertThat(failedAudit.events).containsExactly("exec:SELECT", "afterRollback")

        val outcomes: List<String> = transaction(database) {
            val results: MutableList<String> = mutableListOf()
            listOf(Triple("bob", "alice", 500L), Triple("alice", "bob", 99_000L), Triple("alice", "bob", 200L))
                .forEach { (from: String, to: String, amount: Long) ->
                    try {
                        transaction(database) {
                            transfer(from, to, amount)
                        }
                        results += "ok:$from->$to:$amount"
                    } catch (e: InsufficientFundsException) {
                        results += "rejected:$from->$to:$amount"
                    }
                }
            results
        }
        assertThat(outcomes).containsExactly(
            "ok:bob->alice:500",
            "rejected:alice->bob:99000",
            "ok:alice->bob:200",
        )
        assertThat(balances(database)).containsExactlyEntriesOf(mapOf("alice" to 7_300L, "bob" to 5_200L))
        assertThat(transferCount(database)).isEqualTo(3L)

        val savepointOutcome: Map<String, Long> = transaction(database) {
            transfer("alice", "bob", 1_000)
            try {
                transaction(database) {
                    transfer("bob", "alice", 400)
                    throw IllegalStateException("fraud check failed")
                }
            } catch (e: IllegalStateException) {
                assertThat(e).hasMessage("fraud check failed")
            }
            Accounts.selectAll().associate { it[Accounts.id] to it[Accounts.balanceCents] }
        }
        assertThat(savepointOutcome).containsExactlyInAnyOrderEntriesOf(mapOf("alice" to 6_300L, "bob" to 6_200L))
        assertThat(balances(database)).containsExactlyEntriesOf(mapOf("alice" to 6_300L, "bob" to 6_200L))
        assertThat(transferCount(database)).isEqualTo(4L)

        transaction(database) {
            SchemaUtils.drop(Transfers, Accounts)
        }
    }
}
