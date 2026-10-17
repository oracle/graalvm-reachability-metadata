/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_jdbc

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.expandArgs
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.sql.Connection

/**
 * JDBC transaction management: a ledger service on a HikariCP pool nests transfers as savepoints,
 * so a failed transfer rolls back alone while a failed batch or an explicit rollback undoes all of it.
 */
class LedgerNestedTransactionRollbackTest {

    object Accounts : Table("accounts") {
        val id = varchar("id", 16)
        val balanceCents = long("balance_cents").check("non_negative_balance") { it greaterEq 0L }

        override val primaryKey = PrimaryKey(id)
    }

    class StatementRecorder : SqlLogger {
        val statements: MutableList<String> = mutableListOf()

        override fun log(context: StatementContext, transaction: Transaction) {
            statements += context.expandArgs(transaction)
        }
    }

    class AuditUnavailable : RuntimeException("audit log unavailable")

    @Test
    fun nestedTransfersRollBackIndependentlyOfTheBatch() {
        val pool = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:ledger;DB_CLOSE_DELAY=-1"
                username = "sa"
                password = ""
                maximumPoolSize = 2
            },
        )
        pool.use { dataSource ->
            val database = Database.connect(
                datasource = dataSource,
                databaseConfig = DatabaseConfig {
                    useNestedTransactions = true
                    defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
                },
            )

            val recorder = StatementRecorder()

            fun transfer(from: String, to: String, amountCents: Long) {
                transaction {
                    addLogger(recorder)
                    Accounts.update({ Accounts.id eq from }) {
                        it[balanceCents] = balanceCents - amountCents
                    }
                    Accounts.update({ Accounts.id eq to }) {
                        it[balanceCents] = balanceCents + amountCents
                    }
                }
            }

            fun balances(): Map<String, Long> = transaction(database) {
                Accounts.selectAll().associate { it[Accounts.id] to it[Accounts.balanceCents] }
            }

            transaction(database) {
                SchemaUtils.create(Accounts)
                listOf("alice" to 10_000L, "bob" to 5_000L, "carol" to 0L).forEach { (owner, cents) ->
                    Accounts.insert {
                        it[id] = owner
                        it[balanceCents] = cents
                    }
                }
            }

            val rejected: Throwable? = transaction(database) {
                transfer("alice", "bob", 2_500L)
                val overdraft = runCatching { transfer("carol", "alice", 1_000L) }.exceptionOrNull()
                transfer("bob", "carol", 4_000L)
                overdraft
            }

            assertThat(rejected).isInstanceOf(ExposedSQLException::class.java)
            assertThat(balances()).containsExactlyInAnyOrderEntriesOf(
                mapOf("alice" to 7_500L, "bob" to 3_500L, "carol" to 4_000L),
            )
            assertThat(recorder.statements).containsExactly(
                "UPDATE ACCOUNTS SET BALANCE_CENTS=(ACCOUNTS.BALANCE_CENTS - 2500) WHERE ACCOUNTS.ID = 'alice'",
                "UPDATE ACCOUNTS SET BALANCE_CENTS=(ACCOUNTS.BALANCE_CENTS + 2500) WHERE ACCOUNTS.ID = 'bob'",
                "UPDATE ACCOUNTS SET BALANCE_CENTS=(ACCOUNTS.BALANCE_CENTS - 4000) WHERE ACCOUNTS.ID = 'bob'",
                "UPDATE ACCOUNTS SET BALANCE_CENTS=(ACCOUNTS.BALANCE_CENTS + 4000) WHERE ACCOUNTS.ID = 'carol'",
            )

            assertThatThrownBy {
                transaction(database) {
                    transfer("alice", "carol", 7_500L)
                    throw AuditUnavailable()
                }
            }.isInstanceOf(AuditUnavailable::class.java)
            assertThat(balances()).containsEntry("alice", 7_500L).containsEntry("carol", 4_000L)

            transaction(database) {
                transfer("bob", "alice", 3_500L)
                rollback()
            }
            assertThat(balances()).containsEntry("alice", 7_500L).containsEntry("bob", 3_500L)

            val defaultLevel: Int = transaction(database) { connection.transactionIsolation }
            val auditLevel: Int = transaction(database, transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                connection.transactionIsolation
            }
            assertThat(defaultLevel).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
            assertThat(auditLevel).isEqualTo(Connection.TRANSACTION_SERIALIZABLE)

            val totalCents: Long? = transaction(database) {
                exec("SELECT SUM(balance_cents) FROM accounts") { result ->
                    result.next()
                    result.getLong(1)
                }
            }
            assertThat(totalCents).isEqualTo(15_000L)

            transaction(database) { SchemaUtils.drop(Accounts) }
        }
    }
}
