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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises JDBC thread-confined transaction connections for concurrent order-intake requests.
 */
@Timeout(60)
class ConcurrentOrderIntakeTest {
    @Test
    fun concurrentRequestsCommitAndCancelIndependentlyOnASharedDriver() {
        val dataSource: JdbcDataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:concurrent_order_intake")
        }
        // Keep the in-memory database alive between borrowed connections.
        dataSource.connection.use {
            dataSource.asJdbcDriver().use { driver: JdbcDriver ->
                driver.execute(
                    null,
                    "CREATE TABLE orders (id BIGINT PRIMARY KEY, customer VARCHAR(100) NOT NULL)",
                    0
                )
                val intake: OrderIntake = OrderIntake(driver)
                val staged: CountDownLatch = CountDownLatch(2)
                val decisions: CountDownLatch = CountDownLatch(1)
                val workers: ExecutorService = Executors.newFixedThreadPool(2)
                try {
                    val accepted: Future<Unit> = workers.submit(Callable {
                        intake.transaction {
                            intake.insert(101L, "alice")
                            staged.countDown()
                            check(decisions.await(10, TimeUnit.SECONDS))
                        }
                    })
                    val cancelled: Future<Unit> = workers.submit(Callable {
                        intake.transaction {
                            intake.insert(202L, "bob")
                            staged.countDown()
                            check(decisions.await(10, TimeUnit.SECONDS))
                            rollback()
                        }
                    })

                    assertTrue(staged.await(10, TimeUnit.SECONDS), "Both requests must reach their decision")
                    // A third request must not borrow either worker's transaction connection.
                    assertEquals(emptyList<Order>(), intake.list())
                    decisions.countDown()
                    accepted.get(10, TimeUnit.SECONDS)
                    cancelled.get(10, TimeUnit.SECONDS)
                    assertEquals(listOf(Order(101L, "alice")), intake.list())

                    // New work on the original thread is unaffected by either worker's outcome.
                    intake.transaction {
                        intake.insert(303L, "carol")
                    }
                    assertEquals(
                        listOf(Order(101L, "alice"), Order(303L, "carol")),
                        intake.list()
                    )
                } finally {
                    decisions.countDown()
                    workers.shutdownNow()
                    assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS))
                }
            }
        }
    }

    private class OrderIntake(private val jdbc: JdbcDriver) : TransacterImpl(jdbc) {
        fun insert(id: Long, customer: String) {
            val changed: Long = jdbc.execute(
                null,
                "INSERT INTO orders (id, customer) VALUES (?, ?)",
                2
            ) {
                bindLong(0, id)
                bindString(1, customer)
            }.value
            check(changed == 1L)
        }

        fun list(): List<Order> = jdbc.executeQuery(
            null,
            "SELECT id, customer FROM orders ORDER BY id",
            mapper = { cursor ->
                val orders: MutableList<Order> = mutableListOf()
                while (cursor.next().value) {
                    orders.add(
                        Order(requireNotNull(cursor.getLong(0)), requireNotNull(cursor.getString(1)))
                    )
                }
                QueryResult.Value(orders.toList())
            },
            parameters = 0
        ).value
    }

    private data class Order(val id: Long, val customer: String)
}
