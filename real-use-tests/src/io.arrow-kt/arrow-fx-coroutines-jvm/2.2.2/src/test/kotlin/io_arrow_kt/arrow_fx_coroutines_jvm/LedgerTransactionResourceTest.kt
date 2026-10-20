/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_arrow_kt.arrow_fx_coroutines_jvm

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.resource
import arrow.fx.coroutines.resourceScope
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.Collections
import javax.sql.DataSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises Arrow's `Resource` lifecycle: acquiring nested resources in a `resourceScope` and
 * releasing them in reverse order with the `ExitCase` that ended the scope.
 *
 * A small ledger service owns a HikariCP pool over an in-memory H2 database as an application
 * resource. Each transfer runs in its own scope that borrows a connection and installs a
 * transaction whose release commits on `ExitCase.Completed` and rolls back on any failure.
 */
public class LedgerTransactionResourceTest {

    /** Ledger operations that run against a connection owned by an enclosing transaction. */
    public class Ledger(private val connection: Connection) {

        public fun open(account: String, balance: Long) {
            connection.prepareStatement("INSERT INTO account(id, balance) VALUES (?, ?)").use {
                it.setString(1, account)
                it.setLong(2, balance)
                it.executeUpdate()
            }
        }

        public fun transfer(from: String, to: String, amount: Long) {
            adjust(from, -amount)
            adjust(to, amount)
            check(balance(from) >= 0) { "insufficient funds in $from" }
        }

        public fun balance(account: String): Long =
            connection.prepareStatement("SELECT balance FROM account WHERE id = ?").use {
                it.setString(1, account)
                it.executeQuery().use { rows ->
                    check(rows.next()) { "unknown account $account" }
                    rows.getLong(1)
                }
            }

        private fun adjust(account: String, delta: Long) {
            connection.prepareStatement("UPDATE account SET balance = balance + ? WHERE id = ?").use {
                it.setLong(1, delta)
                it.setString(2, account)
                check(it.executeUpdate() == 1) { "unknown account $account" }
            }
        }
    }

    /** Lifecycle events in the order the resources reported them. */
    private val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private fun connectionPool(databaseName: String): Resource<HikariDataSource> =
        resource({
            val config = HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:$databaseName;DB_CLOSE_DELAY=-1"
                username = "sa"
                password = ""
                maximumPoolSize = 2
                poolName = "ledger-$databaseName"
            }
            HikariDataSource(config).also { events += "pool opened" }
        }) { pool, exitCase ->
            pool.close()
            events += "pool closed: ${describe(exitCase)}"
        }

    /** Borrows a pooled connection and ties its commit or rollback to how the scope ends. */
    private suspend fun ResourceScope.transaction(dataSource: DataSource): Ledger {
        val connection: Connection = autoCloseable { dataSource.connection }
        onRelease { events += "connection returned" }
        install({ connection.apply { autoCommit = false } }) { tx, exitCase ->
            when (exitCase) {
                ExitCase.Completed -> tx.commit()
                else -> tx.rollback()
            }
            events += "transaction ${describe(exitCase)}"
        }
        return Ledger(connection)
    }

    private fun describe(exitCase: ExitCase): String = when (exitCase) {
        ExitCase.Completed -> "completed"
        is ExitCase.Cancelled -> "cancelled"
        is ExitCase.Failure -> "failed (${exitCase.failure.message})"
    }

    @Test
    fun committedAndFailedTransfersReleaseResourcesInReverseOrder() {
        val pool: HikariDataSource = runBlocking {
            resourceScope {
                val pool: HikariDataSource = connectionPool("transfers").bind()
                createSchema(pool)

                resourceScope {
                    val ledger: Ledger = transaction(pool)
                    ledger.open("alice", 100)
                    ledger.open("bob", 20)
                }
                resourceScope { transaction(pool).transfer("alice", "bob", 30) }

                val rejected: Result<Unit> = runCatching {
                    resourceScope { transaction(pool).transfer("bob", "alice", 500) }
                }
                assertEquals("insufficient funds in bob", rejected.exceptionOrNull()?.message)

                val balances: Pair<Long, Long> = resourceScope {
                    val ledger: Ledger = transaction(pool)
                    ledger.balance("alice") to ledger.balance("bob")
                }
                assertEquals(70L to 50L, balances)
                assertEquals(0, pool.hikariPoolMXBean.activeConnections)
                pool
            }
        }

        assertTrue(pool.isClosed)
        assertEquals(
            listOf(
                "pool opened",
                "transaction completed",
                "connection returned",
                "transaction completed",
                "connection returned",
                "transaction failed (insufficient funds in bob)",
                "connection returned",
                "transaction completed",
                "connection returned",
                "pool closed: completed",
            ),
            events.toList(),
        )
    }

    @Test
    fun failureEscapingTheServiceScopeRollsBackAndClosesThePool() {
        lateinit var pool: HikariDataSource
        val outcome: Result<Unit> = runCatching {
            runBlocking {
                resourceScope {
                    pool = connectionPool("outage").bind()
                    createSchema(pool)
                    resourceScope {
                        val ledger: Ledger = transaction(pool)
                        ledger.open("carol", 10)
                        throw IllegalStateException("downstream outage")
                    }
                }
            }
        }

        assertEquals("downstream outage", outcome.exceptionOrNull()?.message)
        assertTrue(pool.isClosed)
        assertEquals(
            listOf(
                "pool opened",
                "transaction failed (downstream outage)",
                "connection returned",
                "pool closed: failed (downstream outage)",
            ),
            events.toList(),
        )

        val accounts: Int = runBlocking {
            resourceScope {
                val reopened: HikariDataSource = connectionPool("outage").bind()
                val connection: Connection = autoCloseable { reopened.connection }
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT COUNT(*) FROM account").use { rows ->
                        check(rows.next())
                        rows.getInt(1)
                    }
                }
            }
        }
        assertEquals(0, accounts)
    }

    private fun createSchema(pool: DataSource) {
        pool.connection.use { connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE account(id VARCHAR(16) PRIMARY KEY, balance BIGINT NOT NULL)")
            }
        }
    }
}
