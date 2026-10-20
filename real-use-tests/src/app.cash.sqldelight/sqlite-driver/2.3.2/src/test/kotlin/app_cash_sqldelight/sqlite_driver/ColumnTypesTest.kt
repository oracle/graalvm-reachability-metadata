/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.Query
import app.cash.sqldelight.adapter.primitive.FloatColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.adapter.primitive.ShortColumnAdapter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Binding and reading every SQLite column type (INTEGER, REAL, TEXT, BLOB, boolean, NULL) through
 * the `JdbcSqliteDriver` statement and cursor, with primitive column adapters for Kotlin types.
 *
 * A weather-station application stores sensor readings whose Kotlin model uses `Int`, `Short`,
 * `Float`, `Boolean`, `ByteArray`, and nullable fields, mapped to SQLite storage classes the way
 * SQLDelight-generated queries map them, and reads them back through parameterized queries.
 */
class ColumnTypesTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var station: StationDatabase

    @BeforeEach
    fun openStation() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        station = StationDatabase(driver)
        station.createSchema()
    }

    @AfterEach
    fun closeStation() {
        driver.close()
    }

    @Test
    fun everyColumnTypeRoundTripsThroughTheDriver() {
        val raw = byteArrayOf(0x00, 0x7F, -0x80, -0x01, 0x2A)
        val reading = Reading(
            id = 1,
            sensor = "roof-north",
            humidity = 63,
            level = 4,
            temperature = 21.5f,
            calibrated = true,
            raw = raw,
            note = "after storm",
        )

        station.insert(reading)

        val stored: Reading = station.readingsFor("roof-north").executeAsOne()
        assertEquals(reading.copy(raw = null), stored.copy(raw = null))
        assertArrayEquals(raw, stored.raw)
        assertEquals(
            listOf("integer", "text", "integer", "integer", "real", "integer", "blob", "text"),
            station.storageClasses(1),
        )
    }

    @Test
    fun nullableColumnsBindAndReadNull() {
        station.insert(
            Reading(2, "cellar", humidity = 80, level = 1, temperature = 12.25f,
                calibrated = false, raw = null, note = null),
        )

        val stored: Reading = station.readingsFor("cellar").executeAsOne()
        assertNull(stored.raw)
        assertNull(stored.note)
        assertEquals(false, stored.calibrated)
        assertEquals(listOf("integer", "text", "integer", "integer", "real", "integer", "null", "null"),
            station.storageClasses(2))
    }

    @Test
    fun adaptedValuesFilterAndAggregateInSql() {
        station.insert(Reading(1, "roof-north", 40, 2, -3.5f, true, null, null))
        station.insert(Reading(2, "roof-north", 55, 3, 0.75f, true, null, null))
        station.insert(Reading(3, "roof-north", 70, 5, 8.0f, false, null, "suspect"))
        station.insert(Reading(4, "garden", 90, 1, 15.5f, true, null, null))

        assertEquals(listOf(2L, 3L), station.humidAbove(50, "roof-north").executeAsList().map { it.id })
        assertEquals(1.75f, station.averageTemperature("roof-north"))
        assertEquals(listOf(1L, 2L, 4L), station.calibratedIds())
        assertEquals(null, station.readingsFor("attic").executeAsOneOrNull())
    }

    /** One sensor reading as the application models it. */
    data class Reading(
        val id: Long,
        val sensor: String,
        val humidity: Int,
        val level: Short,
        val temperature: Float,
        val calibrated: Boolean,
        val raw: ByteArray?,
        val note: String?,
    )

    /** The station's database, written the way SQLDelight generates queries with column adapters. */
    class StationDatabase(private val driver: SqlDriver) {
        private val humidityAdapter: ColumnAdapter<Int, Long> = IntColumnAdapter
        private val levelAdapter: ColumnAdapter<Short, Long> = ShortColumnAdapter
        private val temperatureAdapter: ColumnAdapter<Float, Double> = FloatColumnAdapter

        fun createSchema() {
            driver.execute(
                null,
                """
                CREATE TABLE reading (
                  id INTEGER PRIMARY KEY,
                  sensor TEXT NOT NULL,
                  humidity INTEGER NOT NULL,
                  level INTEGER NOT NULL,
                  temperature REAL NOT NULL,
                  calibrated INTEGER NOT NULL,
                  raw BLOB,
                  note TEXT
                )
                """.trimIndent(),
                0,
            )
        }

        fun insert(reading: Reading) {
            driver.execute(
                INSERT,
                """
                INSERT INTO reading (id, sensor, humidity, level, temperature, calibrated, raw, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                8,
            ) {
                bindLong(0, reading.id)
                bindString(1, reading.sensor)
                bindLong(2, humidityAdapter.encode(reading.humidity))
                bindLong(3, levelAdapter.encode(reading.level))
                bindDouble(4, temperatureAdapter.encode(reading.temperature))
                bindBoolean(5, reading.calibrated)
                bindBytes(6, reading.raw)
                bindString(7, reading.note)
            }
        }

        fun readingsFor(sensor: String): Query<Reading> =
            ReadingsQuery("SELECT $COLUMNS FROM reading WHERE sensor = ? ORDER BY id", 1) {
                bindString(0, sensor)
            }

        fun humidAbove(humidity: Int, sensor: String): Query<Reading> =
            ReadingsQuery("SELECT $COLUMNS FROM reading WHERE humidity > ? AND sensor = ? ORDER BY id", 2) {
                bindLong(0, humidityAdapter.encode(humidity))
                bindString(1, sensor)
            }

        fun averageTemperature(sensor: String): Float =
            driver.executeQuery(null, "SELECT avg(temperature) FROM reading WHERE sensor = ?", { cursor: SqlCursor ->
                cursor.next()
                QueryResult.Value(temperatureAdapter.decode(cursor.getDouble(0)!!))
            }, 1) {
                bindString(0, sensor)
            }.value

        fun calibratedIds(): List<Long> =
            driver.executeQuery(null, "SELECT id FROM reading WHERE calibrated = ? ORDER BY id", { cursor: SqlCursor ->
                val ids: MutableList<Long> = mutableListOf()
                while (cursor.next().value) ids += cursor.getLong(0)!!
                QueryResult.Value(ids.toList())
            }, 1) {
                bindBoolean(0, true)
            }.value

        fun storageClasses(id: Long): List<String> =
            driver.executeQuery(
                null,
                """
                SELECT typeof(id), typeof(sensor), typeof(humidity), typeof(level),
                       typeof(temperature), typeof(calibrated), typeof(raw), typeof(note)
                FROM reading WHERE id = ?
                """.trimIndent(),
                { cursor: SqlCursor ->
                    cursor.next()
                    QueryResult.Value((0 until 8).map { column: Int -> cursor.getString(column)!! })
                },
                1,
            ) {
                bindLong(0, id)
            }.value

        private fun mapReading(cursor: SqlCursor): Reading = Reading(
            id = cursor.getLong(0)!!,
            sensor = cursor.getString(1)!!,
            humidity = humidityAdapter.decode(cursor.getLong(2)!!),
            level = levelAdapter.decode(cursor.getLong(3)!!),
            temperature = temperatureAdapter.decode(cursor.getDouble(4)!!),
            calibrated = cursor.getBoolean(5)!!,
            raw = cursor.getBytes(6),
            note = cursor.getString(7),
        )

        /** A parameterized query shaped like the inner query classes SQLDelight generates. */
        private inner class ReadingsQuery(
            private val sql: String,
            private val parameters: Int,
            private val binder: SqlPreparedStatement.() -> Unit,
        ) : Query<Reading>({ cursor: SqlCursor -> mapReading(cursor) }) {

            override fun addListener(listener: Query.Listener) {
                driver.addListener("reading", listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener("reading", listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(null, sql, mapper, parameters, binder)
        }

        private companion object {
            const val INSERT: Int = 1
            const val COLUMNS: String = "id, sensor, humidity, level, temperature, calibrated, raw, note"
        }
    }
}
