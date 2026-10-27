/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package app_cash_sqldelight.sqlite_driver

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Temporal data through `JdbcSqliteDriver`: `java.time` values persisted with custom column adapters
 * (`Instant` as epoch milliseconds, `LocalDate` as ISO-8601 TEXT, `Duration` as seconds, an enum
 * through `EnumColumnAdapter`) and queried with SQLite date and time functions.
 *
 * A clinic scheduling application stores appointments and follow-up tasks, then asks the database
 * for time windows, per-day and per-weekday buckets, month starts, due-date arithmetic and overdue
 * work. Every "now" is bound as a parameter, so no result depends on the wall clock.
 */
class TemporalDataTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var clinic: ClinicDatabase

    @BeforeEach
    fun openClinic() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        clinic = ClinicDatabase(driver)
        clinic.createSchema()
    }

    @AfterEach
    fun closeClinic() {
        driver.close()
    }

    @Test
    fun instantsAndDurationsRoundTripAndFilterByWindow() {
        val first = Appointment(1, "Ada", Instant.parse("2026-03-02T08:30:00.250Z"), Duration.ofMinutes(30), Status.BOOKED)
        val second = Appointment(2, "Ben", Instant.parse("2026-03-02T23:45:00Z"), Duration.ofMinutes(45), Status.DONE)
        val third = Appointment(3, "Cy", Instant.parse("2026-03-03T00:15:00Z"), Duration.ofHours(1), Status.CANCELLED)
        listOf(third, first, second).forEach { appointment: Appointment -> clinic.book(appointment) }

        assertEquals(first, clinic.appointment(1).executeAsOne())
        assertEquals(
            listOf(first, second),
            clinic.startingBetween(Instant.parse("2026-03-02T00:00:00Z"), Instant.parse("2026-03-03T00:00:00Z"))
                .executeAsList(),
        )
        assertEquals(listOf(3L), clinic.withStatus(Status.CANCELLED).executeAsList().map { it.id })
        assertEquals(Instant.parse("2026-03-03T01:15:00Z"), clinic.lastEnd())
    }

    @Test
    fun appointmentsBucketByUtcDayWeekdayAndMonth() {
        clinic.book(Appointment(1, "Ada", Instant.parse("2026-02-27T10:00:00Z"), Duration.ofMinutes(20), Status.DONE))
        clinic.book(Appointment(2, "Ben", Instant.parse("2026-03-02T09:00:00Z"), Duration.ofMinutes(20), Status.DONE))
        clinic.book(Appointment(3, "Cy", Instant.parse("2026-03-02T16:00:00Z"), Duration.ofMinutes(40), Status.BOOKED))
        clinic.book(Appointment(4, "Di", Instant.parse("2026-03-09T11:30:00Z"), Duration.ofMinutes(60), Status.BOOKED))

        assertEquals(
            listOf(
                DayLoad(LocalDate.of(2026, 2, 27), 1, Duration.ofMinutes(20)),
                DayLoad(LocalDate.of(2026, 3, 2), 2, Duration.ofMinutes(60)),
                DayLoad(LocalDate.of(2026, 3, 9), 1, Duration.ofMinutes(60)),
            ),
            clinic.dailyLoad(),
        )
        assertEquals(mapOf("Fri" to 1L, "Mon" to 3L), clinic.weekdayCounts())
        assertEquals(
            mapOf(LocalDate.of(2026, 2, 1) to 1L, LocalDate.of(2026, 3, 1) to 3L),
            clinic.monthlyCounts(),
        )
    }

    @Test
    fun dueDatesAreComputedAndComparedInSql() {
        clinic.book(Appointment(1, "Ada", Instant.parse("2026-01-30T10:00:00Z"), Duration.ofMinutes(30), Status.DONE))
        clinic.book(Appointment(2, "Ben", Instant.parse("2026-02-20T10:00:00Z"), Duration.ofMinutes(30), Status.DONE))
        clinic.scheduleFollowUp(10, appointmentId = 1, afterDays = 30)
        clinic.scheduleFollowUp(11, appointmentId = 2, afterDays = 7)
        clinic.scheduleFollowUp(12, appointmentId = 2, afterDays = 1)

        assertEquals(
            listOf(
                FollowUp(12, 2, LocalDate.of(2026, 2, 21), null),
                FollowUp(11, 2, LocalDate.of(2026, 2, 27), null),
                FollowUp(10, 1, LocalDate.of(2026, 3, 1), null),
            ),
            clinic.followUps().executeAsList(),
        )

        clinic.complete(12, LocalDate.of(2026, 2, 24))
        val today: LocalDate = LocalDate.of(2026, 2, 28)
        assertEquals(listOf(11L), clinic.overdue(today).executeAsList().map { it.id })
        assertEquals(listOf(11L, 10L), clinic.dueWithin(today, days = 2).executeAsList().map { it.id })
        assertEquals(mapOf(11L to 1L, 10L to -1L), clinic.daysOverdue(today))
        assertEquals(3L, clinic.daysLate(12))
        assertEquals(LocalDate.of(2026, 2, 28), clinic.lastDayOfMonth(LocalDate.of(2026, 2, 10)))
    }

    /** Appointment state, stored by name through `EnumColumnAdapter`. */
    enum class Status { BOOKED, DONE, CANCELLED }

    /** One appointment as the application models it. */
    data class Appointment(
        val id: Long,
        val patient: String,
        val startsAt: Instant,
        val length: Duration,
        val status: Status,
    )

    /** A follow-up task due on a calendar day, optionally completed. */
    data class FollowUp(val id: Long, val appointmentId: Long, val due: LocalDate, val completedOn: LocalDate?)

    /** Appointments and booked time on one UTC day. */
    data class DayLoad(val day: LocalDate, val appointments: Long, val booked: Duration)

    /** The clinic database, written the way SQLDelight generates queries with column adapters. */
    class ClinicDatabase(private val driver: SqlDriver) {
        private val instantAdapter: ColumnAdapter<Instant, Long> = object : ColumnAdapter<Instant, Long> {
            override fun decode(databaseValue: Long): Instant = Instant.ofEpochMilli(databaseValue)
            override fun encode(value: Instant): Long = value.toEpochMilli()
        }
        private val dateAdapter: ColumnAdapter<LocalDate, String> = object : ColumnAdapter<LocalDate, String> {
            override fun decode(databaseValue: String): LocalDate = LocalDate.parse(databaseValue)
            override fun encode(value: LocalDate): String = value.toString()
        }
        private val durationAdapter: ColumnAdapter<Duration, Long> = object : ColumnAdapter<Duration, Long> {
            override fun decode(databaseValue: Long): Duration = Duration.ofSeconds(databaseValue)
            override fun encode(value: Duration): Long = value.seconds
        }
        private val statusAdapter: ColumnAdapter<Status, String> = EnumColumnAdapter()

        fun createSchema() {
            driver.execute(
                null,
                """
                CREATE TABLE appointment (
                  id INTEGER PRIMARY KEY,
                  patient TEXT NOT NULL,
                  starts_at INTEGER NOT NULL,
                  length_seconds INTEGER NOT NULL,
                  status TEXT NOT NULL
                )
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                CREATE TABLE follow_up (
                  id INTEGER PRIMARY KEY,
                  appointment_id INTEGER NOT NULL REFERENCES appointment(id),
                  due TEXT NOT NULL,
                  completed_on TEXT
                )
                """.trimIndent(),
                0,
            )
        }

        fun book(appointment: Appointment) {
            driver.execute(
                BOOK,
                "INSERT INTO appointment (id, patient, starts_at, length_seconds, status) VALUES (?, ?, ?, ?, ?)",
                5,
            ) {
                bindLong(0, appointment.id)
                bindString(1, appointment.patient)
                bindLong(2, instantAdapter.encode(appointment.startsAt))
                bindLong(3, durationAdapter.encode(appointment.length))
                bindString(4, statusAdapter.encode(appointment.status))
            }
            driver.notifyListeners("appointment")
        }

        /** Schedules a follow-up [afterDays] after the appointment's UTC calendar day, computed by SQLite. */
        fun scheduleFollowUp(id: Long, appointmentId: Long, afterDays: Long) {
            driver.execute(
                SCHEDULE,
                """
                INSERT INTO follow_up (id, appointment_id, due)
                SELECT ?, id, date(starts_at / 1000, 'unixepoch', '+' || ? || ' days')
                FROM appointment WHERE id = ?
                """.trimIndent(),
                3,
            ) {
                bindLong(0, id)
                bindLong(1, afterDays)
                bindLong(2, appointmentId)
            }
            driver.notifyListeners("follow_up")
        }

        fun complete(id: Long, on: LocalDate) {
            driver.execute(COMPLETE, "UPDATE follow_up SET completed_on = ? WHERE id = ?", 2) {
                bindString(0, dateAdapter.encode(on))
                bindLong(1, id)
            }
            driver.notifyListeners("follow_up")
        }

        fun appointment(id: Long): Query<Appointment> =
            AppointmentsQuery("SELECT $APPOINTMENT FROM appointment WHERE id = ?", 1) { bindLong(0, id) }

        fun startingBetween(from: Instant, until: Instant): Query<Appointment> =
            AppointmentsQuery(
                "SELECT $APPOINTMENT FROM appointment WHERE starts_at >= ? AND starts_at < ? ORDER BY starts_at",
                2,
            ) {
                bindLong(0, instantAdapter.encode(from))
                bindLong(1, instantAdapter.encode(until))
            }

        fun withStatus(status: Status): Query<Appointment> =
            AppointmentsQuery("SELECT $APPOINTMENT FROM appointment WHERE status = ? ORDER BY id", 1) {
                bindString(0, statusAdapter.encode(status))
            }

        fun lastEnd(): Instant =
            single("SELECT max(starts_at + length_seconds * 1000) FROM appointment") { cursor: SqlCursor ->
                instantAdapter.decode(cursor.getLong(0)!!)
            }

        fun dailyLoad(): List<DayLoad> =
            rows(
                """
                SELECT date(starts_at / 1000, 'unixepoch') AS day, count(*), sum(length_seconds)
                FROM appointment GROUP BY day ORDER BY day
                """.trimIndent(),
            ) { cursor: SqlCursor ->
                DayLoad(
                    dateAdapter.decode(cursor.getString(0)!!),
                    cursor.getLong(1)!!,
                    durationAdapter.decode(cursor.getLong(2)!!),
                )
            }

        fun weekdayCounts(): Map<String, Long> =
            rows(
                """
                SELECT CAST(strftime('%w', starts_at / 1000, 'unixepoch') AS INTEGER) AS weekday, count(*)
                FROM appointment GROUP BY weekday ORDER BY weekday
                """.trimIndent(),
            ) { cursor: SqlCursor -> WEEKDAYS[cursor.getLong(0)!!.toInt()] to cursor.getLong(1)!! }.toMap()

        fun monthlyCounts(): Map<LocalDate, Long> =
            rows(
                """
                SELECT date(starts_at / 1000, 'unixepoch', 'start of month') AS month, count(*)
                FROM appointment GROUP BY month ORDER BY month
                """.trimIndent(),
            ) { cursor: SqlCursor -> dateAdapter.decode(cursor.getString(0)!!) to cursor.getLong(1)!! }.toMap()

        fun followUps(): Query<FollowUp> =
            FollowUpsQuery("SELECT $FOLLOW_UP FROM follow_up ORDER BY due, id", 0) {}

        fun overdue(today: LocalDate): Query<FollowUp> =
            FollowUpsQuery(
                "SELECT $FOLLOW_UP FROM follow_up WHERE completed_on IS NULL AND due < ? ORDER BY due",
                1,
            ) {
                bindString(0, dateAdapter.encode(today))
            }

        /** Open follow-ups due before the end of the window starting [days] after [today]. */
        fun dueWithin(today: LocalDate, days: Long): Query<FollowUp> =
            FollowUpsQuery(
                """
                SELECT $FOLLOW_UP FROM follow_up
                WHERE completed_on IS NULL AND due <= date(?, '+' || ? || ' days')
                ORDER BY due
                """.trimIndent(),
                2,
            ) {
                bindString(0, dateAdapter.encode(today))
                bindLong(1, days)
            }

        fun daysOverdue(today: LocalDate): Map<Long, Long> =
            rows(
                """
                SELECT id, CAST(julianday(?) - julianday(due) AS INTEGER)
                FROM follow_up WHERE completed_on IS NULL ORDER BY due
                """.trimIndent(),
                1,
                { bindString(0, dateAdapter.encode(today)) },
            ) { cursor: SqlCursor -> cursor.getLong(0)!! to cursor.getLong(1)!! }.toMap()

        fun daysLate(id: Long): Long =
            single(
                "SELECT CAST(julianday(completed_on) - julianday(due) AS INTEGER) FROM follow_up WHERE id = ?",
                1,
                { bindLong(0, id) },
            ) { cursor: SqlCursor -> cursor.getLong(0)!! }

        fun lastDayOfMonth(day: LocalDate): LocalDate =
            single("SELECT date(?, 'start of month', '+1 month', '-1 day')", 1, {
                bindString(0, dateAdapter.encode(day))
            }) { cursor: SqlCursor -> dateAdapter.decode(cursor.getString(0)!!) }

        private fun <T : Any> rows(
            sql: String,
            parameters: Int = 0,
            binder: SqlPreparedStatement.() -> Unit = {},
            mapper: (SqlCursor) -> T,
        ): List<T> =
            driver.executeQuery(null, sql, { cursor: SqlCursor ->
                val result: MutableList<T> = mutableListOf()
                while (cursor.next().value) result += mapper(cursor)
                QueryResult.Value(result.toList())
            }, parameters, binder).value

        private fun <T : Any> single(
            sql: String,
            parameters: Int = 0,
            binder: SqlPreparedStatement.() -> Unit = {},
            mapper: (SqlCursor) -> T,
        ): T = rows(sql, parameters, binder, mapper).single()

        private fun mapAppointment(cursor: SqlCursor): Appointment = Appointment(
            id = cursor.getLong(0)!!,
            patient = cursor.getString(1)!!,
            startsAt = instantAdapter.decode(cursor.getLong(2)!!),
            length = durationAdapter.decode(cursor.getLong(3)!!),
            status = statusAdapter.decode(cursor.getString(4)!!),
        )

        private fun mapFollowUp(cursor: SqlCursor): FollowUp = FollowUp(
            id = cursor.getLong(0)!!,
            appointmentId = cursor.getLong(1)!!,
            due = dateAdapter.decode(cursor.getString(2)!!),
            completedOn = cursor.getString(3)?.let { value: String -> dateAdapter.decode(value) },
        )

        /** A parameterized query shaped like the inner query classes SQLDelight generates. */
        private inner class AppointmentsQuery(
            sql: String,
            parameters: Int,
            binder: SqlPreparedStatement.() -> Unit,
        ) : TableQuery<Appointment>("appointment", sql, parameters, binder, { cursor: SqlCursor -> mapAppointment(cursor) })

        /** A parameterized follow-up query shaped like the ones SQLDelight generates. */
        private inner class FollowUpsQuery(
            sql: String,
            parameters: Int,
            binder: SqlPreparedStatement.() -> Unit,
        ) : TableQuery<FollowUp>("follow_up", sql, parameters, binder, { cursor: SqlCursor -> mapFollowUp(cursor) })

        /** A query over one table that registers its listeners on that table. */
        private open inner class TableQuery<T : Any>(
            private val table: String,
            private val sql: String,
            private val parameters: Int,
            private val binder: SqlPreparedStatement.() -> Unit,
            mapper: (SqlCursor) -> T,
        ) : Query<T>(mapper) {

            override fun addListener(listener: Query.Listener) {
                driver.addListener(table, listener = listener)
            }

            override fun removeListener(listener: Query.Listener) {
                driver.removeListener(table, listener = listener)
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
                driver.executeQuery(null, sql, mapper, parameters, binder)
        }

        private companion object {
            const val BOOK: Int = 1
            const val SCHEDULE: Int = 2
            const val COMPLETE: Int = 3
            const val APPOINTMENT: String = "id, patient, starts_at, length_seconds, status"
            const val FOLLOW_UP: String = "id, appointment_id, due, completed_on"
            val WEEKDAYS: List<String> = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        }
    }
}
