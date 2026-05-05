/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.jdbc_driver

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.JdbcCursor
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.JdbcPreparedStatement
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.sql.Types
import java.util.concurrent.atomic.AtomicInteger

public class Jdbc_driverTest {
    @Test
    fun executeAndExecuteQueryBindAndReadSqlDelightTypes(): Unit = withDriver { driver: JdbcDriver ->
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE people (
                  id BIGINT PRIMARY KEY,
                  name VARCHAR(100),
                  active BOOLEAN,
                  score DOUBLE PRECISION,
                  payload VARBINARY(16)
                )
            """.trimIndent(),
            parameters = 0,
            binders = null,
        )

        val insertedRows: Long = driver.execute(
            identifier = 1,
            sql = "INSERT INTO people (id, name, active, score, payload) VALUES (?, ?, ?, ?, ?)",
            parameters = 5,
        ) {
            bindLong(0, 1L)
            bindString(1, "Ada")
            bindBoolean(2, true)
            bindDouble(3, 98.5)
            bindBytes(4, byteArrayOf(1, 2, 3))
        }.value

        driver.execute(
            identifier = 2,
            sql = "INSERT INTO people (id, name, active, score, payload) VALUES (?, ?, ?, ?, ?)",
            parameters = 5,
        ) {
            bindLong(0, 2L)
            bindString(1, null)
            bindBoolean(2, null)
            bindDouble(3, null)
            bindBytes(4, null)
        }

        val people: List<PersonRow> = driver.executeQuery(
            identifier = 3,
            sql = "SELECT id, name, active, score, payload FROM people ORDER BY id",
            mapper = { cursor ->
                val rows: MutableList<PersonRow> = mutableListOf()
                while (cursor.next().value) {
                    rows += PersonRow(
                        id = cursor.getLong(0),
                        name = cursor.getString(1),
                        active = cursor.getBoolean(2),
                        score = cursor.getDouble(3),
                        payload = cursor.getBytes(4),
                    )
                }
                QueryResult.Value(rows)
            },
            parameters = 0,
            binders = null,
        ).value

        assertThat(insertedRows).isEqualTo(1L)
        assertThat(people).containsExactly(
            PersonRow(1L, "Ada", true, 98.5, byteArrayOf(1, 2, 3)),
            PersonRow(2L, null, null, null, null),
        )
    }

    @Test
    fun jdbcPreparedStatementAndCursorExposeExtendedJdbcTypes(): Unit = withDriver { driver: JdbcDriver ->
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE measurements (
                  id INTEGER PRIMARY KEY,
                  tiny TINYINT,
                  small SMALLINT,
                  amount DECIMAL(10, 2),
                  label VARCHAR(50),
                  ratio REAL
                )
            """.trimIndent(),
            parameters = 0,
            binders = null,
        )

        driver.execute(
            identifier = 10,
            sql = "INSERT INTO measurements (id, tiny, small, amount, label, ratio) VALUES (?, ?, ?, ?, ?, ?)",
            parameters = 6,
        ) {
            val statement: JdbcPreparedStatement = this as JdbcPreparedStatement
            statement.bindInt(0, 7)
            statement.bindByte(1, 3)
            statement.bindShort(2, 12)
            statement.bindBigDecimal(3, BigDecimal("123.45"))
            statement.bindObject(4, "calibrated", Types.VARCHAR)
            statement.bindFloat(5, 1.25f)
        }

        val measurement: MeasurementRow = driver.executeQuery(
            identifier = 11,
            sql = """
                SELECT id, tiny, small, amount, label, ratio, ARRAY[4, 5, 6]
                FROM measurements
            """.trimIndent(),
            mapper = { cursor ->
                assertThat(cursor.next().value).isTrue()
                val jdbcCursor: JdbcCursor = cursor as JdbcCursor
                QueryResult.Value(
                    MeasurementRow(
                        id = jdbcCursor.getInt(0),
                        tiny = jdbcCursor.getByte(1),
                        small = jdbcCursor.getShort(2),
                        amount = jdbcCursor.getBigDecimal(3),
                        label = jdbcCursor.getObject<String>(4),
                        ratio = jdbcCursor.getFloat(5),
                        arrayValues = (jdbcCursor.getArray<Any>(6) ?: emptyArray()).map { value ->
                            (value as Number).toInt()
                        },
                    ),
                )
            },
            parameters = 0,
            binders = null,
        ).value

        assertThat(measurement.id).isEqualTo(7)
        assertThat(measurement.tiny).isEqualTo(3.toByte())
        assertThat(measurement.small).isEqualTo(12.toShort())
        assertThat(measurement.amount).isEqualByComparingTo(BigDecimal("123.45"))
        assertThat(measurement.label).isEqualTo("calibrated")
        assertThat(measurement.ratio).isEqualTo(1.25f)
        assertThat(measurement.arrayValues).containsExactly(4, 5, 6)
    }

    @Test
    fun jdbcPreparedStatementAndCursorBindAndReadTemporalTypes(): Unit = withDriver { driver: JdbcDriver ->
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE scheduled_events (
                  id INTEGER PRIMARY KEY,
                  event_date DATE,
                  event_time TIME,
                  created_at TIMESTAMP
                )
            """.trimIndent(),
            parameters = 0,
            binders = null,
        )

        val eventDate: Date = Date.valueOf("2025-03-14")
        val eventTime: Time = Time.valueOf("09:26:53")
        val createdAt: Timestamp = Timestamp.valueOf("2025-03-14 09:26:53")

        driver.execute(
            identifier = 30,
            sql = "INSERT INTO scheduled_events (id, event_date, event_time, created_at) VALUES (?, ?, ?, ?)",
            parameters = 4,
        ) {
            val statement: JdbcPreparedStatement = this as JdbcPreparedStatement
            statement.bindInt(0, 1)
            statement.bindDate(2, eventDate)
            statement.bindTime(3, eventTime)
            statement.bindTimestamp(4, createdAt)
        }

        val scheduledEvent: ScheduledEventRow = driver.executeQuery(
            identifier = 31,
            sql = "SELECT id, event_date, event_time, created_at FROM scheduled_events",
            mapper = { cursor ->
                assertThat(cursor.next().value).isTrue()
                val jdbcCursor: JdbcCursor = cursor as JdbcCursor
                QueryResult.Value(
                    ScheduledEventRow(
                        id = jdbcCursor.getInt(0),
                        eventDate = jdbcCursor.getDate(2),
                        eventTime = jdbcCursor.getTime(3),
                        createdAt = jdbcCursor.getTimestamp(4),
                    ),
                )
            },
            parameters = 0,
            binders = null,
        ).value

        assertThat(scheduledEvent).isEqualTo(
            ScheduledEventRow(
                id = 1,
                eventDate = eventDate,
                eventTime = eventTime,
                createdAt = createdAt,
            ),
        )
    }

    @Test
    fun transacterCommitsRollsBackAndRunsCallbacksOnJdbcConnection(): Unit = withDriver { driver: JdbcDriver ->
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE audit_events (id INTEGER PRIMARY KEY, message VARCHAR(100))",
            parameters = 0,
            binders = null,
        )
        val transacter: DriverBackedTransacter = DriverBackedTransacter(driver)
        val events: MutableList<String> = mutableListOf()

        transacter.transaction {
            afterCommit { events += "committed" }
            afterRollback { events += "unexpected rollback" }
            driver.execute(
                identifier = 20,
                sql = "INSERT INTO audit_events (id, message) VALUES (?, ?)",
                parameters = 2,
            ) {
                bindLong(0, 1L)
                bindString(1, "created")
            }
        }

        assertThat(events).containsExactly("committed")
        assertThat(messages(driver)).containsExactly("created")

        assertThatThrownBy {
            transacter.transaction {
                afterCommit { events += "unexpected commit" }
                afterRollback { events += "rolled back" }
                driver.execute(
                    identifier = 21,
                    sql = "INSERT INTO audit_events (id, message) VALUES (?, ?)",
                    parameters = 2,
                ) {
                    bindLong(0, 2L)
                    bindString(1, "discarded")
                }
                throw IllegalStateException("abort transaction")
            }
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("abort transaction")

        assertThat(events).containsExactly("committed", "rolled back")
        assertThat(messages(driver)).containsExactly("created")
    }

    @Test
    fun jdbcDriverReusesTransactionConnectionAndClosesItAfterCommit(): Unit =
        withCountingDriver { driver: CountingJdbcDriver ->
            driver.execute(
                identifier = null,
                sql = "CREATE TABLE transaction_items (id INTEGER PRIMARY KEY, name VARCHAR(100))",
                parameters = 0,
                binders = null,
            )
            assertThat(driver.openConnectionCount()).isEqualTo(1)
            assertThat(driver.closeConnectionCount()).isEqualTo(1)

            val transacter: DriverBackedTransacter = DriverBackedTransacter(driver)
            val rowCountInTransaction: Long = transacter.transactionWithResult {
                driver.execute(
                    identifier = 40,
                    sql = "INSERT INTO transaction_items (id, name) VALUES (?, ?)",
                    parameters = 2,
                ) {
                    bindLong(0, 1L)
                    bindString(1, "first")
                }
                driver.execute(
                    identifier = 41,
                    sql = "INSERT INTO transaction_items (id, name) VALUES (?, ?)",
                    parameters = 2,
                ) {
                    bindLong(0, 2L)
                    bindString(1, "second")
                }
                transactionItemCount(driver)
            }

            assertThat(rowCountInTransaction).isEqualTo(2L)
            assertThat(driver.openConnectionCount()).isEqualTo(2)
            assertThat(driver.closeConnectionCount()).isEqualTo(2)
            assertThat(transactionItemCount(driver)).isEqualTo(2L)
            assertThat(driver.openConnectionCount()).isEqualTo(3)
            assertThat(driver.closeConnectionCount()).isEqualTo(3)
        }

    private data class PersonRow(
        val id: Long?,
        val name: String?,
        val active: Boolean?,
        val score: Double?,
        val payload: ByteArray?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PersonRow) return false
            return id == other.id &&
                name == other.name &&
                active == other.active &&
                score == other.score &&
                payload.contentEqualsNullable(other.payload)
        }

        override fun hashCode(): Int {
            var result: Int = id?.hashCode() ?: 0
            result = 31 * result + (name?.hashCode() ?: 0)
            result = 31 * result + (active?.hashCode() ?: 0)
            result = 31 * result + (score?.hashCode() ?: 0)
            result = 31 * result + (payload?.contentHashCode() ?: 0)
            return result
        }
    }

    private data class MeasurementRow(
        val id: Int?,
        val tiny: Byte?,
        val small: Short?,
        val amount: BigDecimal?,
        val label: String?,
        val ratio: Float?,
        val arrayValues: List<Int>,
    )

    private data class ScheduledEventRow(
        val id: Int?,
        val eventDate: Date?,
        val eventTime: Time?,
        val createdAt: Timestamp?,
    )

    private class DriverBackedTransacter(driver: JdbcDriver) : TransacterImpl(driver)

    private class CountingJdbcDriver(private val dataSource: JdbcDataSource) : JdbcDriver() {
        private val openConnections: AtomicInteger = AtomicInteger()
        private val closedConnections: AtomicInteger = AtomicInteger()

        override fun getConnection(): Connection {
            openConnections.incrementAndGet()
            return dataSource.connection
        }

        override fun closeConnection(connection: Connection): Unit {
            closedConnections.incrementAndGet()
            connection.close()
        }

        override fun addListener(vararg queryKeys: String, listener: Query.Listener): Unit = Unit

        override fun removeListener(vararg queryKeys: String, listener: Query.Listener): Unit = Unit

        override fun notifyListeners(vararg queryKeys: String): Unit = Unit

        fun openConnectionCount(): Int = openConnections.get()

        fun closeConnectionCount(): Int = closedConnections.get()
    }

    private companion object {
        private val databaseCounter: AtomicInteger = AtomicInteger()

        private fun withDriver(block: (JdbcDriver) -> Unit): Unit {
            val dataSource: JdbcDataSource = JdbcDataSource().apply {
                setURL("jdbc:h2:mem:sqldelight_jdbc_${databaseCounter.incrementAndGet()};DB_CLOSE_DELAY=-1")
                setUser("sa")
                setPassword("")
            }
            val driver: JdbcDriver = dataSource.asJdbcDriver()
            try {
                block(driver)
            } finally {
                runCatching {
                    driver.execute(null, "DROP ALL OBJECTS", 0, null)
                }
                driver.close()
            }
        }

        private fun withCountingDriver(block: (CountingJdbcDriver) -> Unit): Unit {
            val dataSource: JdbcDataSource = JdbcDataSource().apply {
                setURL("jdbc:h2:mem:sqldelight_jdbc_${databaseCounter.incrementAndGet()};DB_CLOSE_DELAY=-1")
                setUser("sa")
                setPassword("")
            }
            val driver: CountingJdbcDriver = CountingJdbcDriver(dataSource)
            try {
                block(driver)
            } finally {
                runCatching {
                    driver.execute(null, "DROP ALL OBJECTS", 0, null)
                }
                driver.close()
            }
        }

        private fun messages(driver: JdbcDriver): List<String> {
            return driver.executeQuery(
                identifier = null,
                sql = "SELECT message FROM audit_events ORDER BY id",
                mapper = { cursor ->
                    val values: MutableList<String> = mutableListOf()
                    while (cursor.next().value) {
                        values += cursor.getString(0) ?: error("message was null")
                    }
                    QueryResult.Value(values)
                },
                parameters = 0,
                binders = null,
            ).value
        }

        private fun transactionItemCount(driver: JdbcDriver): Long {
            return driver.executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM transaction_items",
                mapper = { cursor ->
                    assertThat(cursor.next().value).isTrue()
                    QueryResult.Value(cursor.getLong(0) ?: 0L)
                },
                parameters = 0,
                binders = null,
            ).value
        }

        private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
            return when {
                this == null -> other == null
                other == null -> false
                else -> contentEquals(other)
            }
        }
    }
}
