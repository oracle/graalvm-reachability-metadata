/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_exposed.exposed_core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.dateParam
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.hour
import org.jetbrains.exposed.v1.datetime.month
import org.jetbrains.exposed.v1.datetime.time
import org.jetbrains.exposed.v1.datetime.timeLiteral
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.datetime.timestampLiteral
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.year
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Temporal column types and date/time SQL functions: a clinic scheduler stores appointments in
 * date, time, datetime, timestamp, time-zone-aware timestamp and duration columns, then filters,
 * groups and reschedules them with date and time literals and extracted year, month and hour values.
 */
@OptIn(ExperimentalTime::class)
class ClinicScheduleTemporalColumnsTest {

    object Appointments : Table("appointments") {
        val id = integer("id").autoIncrement()
        val patient = varchar("patient", 64)
        val clinicDay = date("clinic_day")
        val opensAt = time("opens_at")
        val startsAt = datetime("starts_at")
        val length = duration("length")
        val bookedAt = timestamp("booked_at")
        val remoteSlot = timestampWithTimeZone("remote_slot").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    data class Booking(
        val patient: String,
        val clinicDay: LocalDate,
        val opensAt: LocalTime,
        val startsAt: LocalDateTime,
        val length: Duration,
        val bookedAt: Instant,
        val remoteSlot: OffsetDateTime?,
    )

    @Test
    fun schedulesAndReschedulesAppointmentsThroughTemporalColumns() {
        val database: Database = Database.connect(
            url = "jdbc:h2:mem:clinic_schedule;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        val tokyoCall: OffsetDateTime = OffsetDateTime.of(2026, 3, 10, 18, 0, 0, 0, ZoneOffset.ofHours(9))
        val bookings: List<Booking> = listOf(
            Booking(
                "Ada", LocalDate(2026, 3, 9), LocalTime(8, 0), LocalDateTime(2026, 3, 9, 9, 30),
                30.minutes, Instant.parse("2026-02-20T10:15:00Z"), null,
            ),
            Booking(
                "Grace", LocalDate(2026, 3, 9), LocalTime(8, 0), LocalDateTime(2026, 3, 9, 14, 0),
                45.minutes, Instant.parse("2026-02-27T16:40:30Z"), null,
            ),
            Booking(
                "Linus", LocalDate(2026, 3, 10), LocalTime(9, 0), LocalDateTime(2026, 3, 10, 10, 15),
                20.minutes, Instant.parse("2026-03-01T08:00:00Z"), tokyoCall,
            ),
            Booking(
                "Margaret", LocalDate(2026, 4, 2), LocalTime(8, 0), LocalDateTime(2026, 4, 2, 16, 45),
                60.minutes, Instant.parse("2026-03-15T12:00:00Z"), null,
            ),
        )

        val ids: Map<String, Int> = transaction(database) {
            SchemaUtils.create(Appointments)
            bookings.associate { booking ->
                booking.patient to Appointments.insert {
                    it[patient] = booking.patient
                    it[clinicDay] = booking.clinicDay
                    it[opensAt] = booking.opensAt
                    it[startsAt] = booking.startsAt
                    it[length] = booking.length
                    it[bookedAt] = booking.bookedAt
                    it[remoteSlot] = booking.remoteSlot
                }[Appointments.id]
            }
        }

        // Every temporal value comes back as the Kotlin type it was written as.
        val stored: List<Booking> = transaction(database) {
            Appointments.selectAll()
                .orderBy(Appointments.startsAt to SortOrder.ASC)
                .map { row ->
                    Booking(
                        row[Appointments.patient], row[Appointments.clinicDay], row[Appointments.opensAt],
                        row[Appointments.startsAt], row[Appointments.length], row[Appointments.bookedAt],
                        row[Appointments.remoteSlot],
                    )
                }
        }
        assertThat(stored.map { it.copy(remoteSlot = null) }).isEqualTo(bookings.map { it.copy(remoteSlot = null) })
        assertThat(stored.single { it.patient == "Linus" }.remoteSlot!!.toInstant()).isEqualTo(tokyoCall.toInstant())

        // Afternoon visits during the first clinic week, matched by date range and extracted hour.
        val afternoonFirstWeek: List<String> = transaction(database) {
            Appointments.select(Appointments.patient)
                .where {
                    (Appointments.clinicDay.between(LocalDate(2026, 3, 9), LocalDate(2026, 3, 15))) and
                        (Appointments.startsAt.hour() greaterEq 12)
                }
                .map { row -> row[Appointments.patient] }
        }
        assertThat(afternoonFirstWeek).containsExactly("Grace")

        // Monthly load, grouped by the year and month extracted from the appointment start.
        val startYear = Appointments.startsAt.year()
        val startMonth = Appointments.startsAt.month()
        val visits = Appointments.id.count()
        val monthlyLoad: List<Triple<Int, Int, Long>> = transaction(database) {
            Appointments.select(startYear, startMonth, visits)
                .groupBy(startYear, startMonth)
                .orderBy(startYear to SortOrder.ASC, startMonth to SortOrder.ASC)
                .map { row -> Triple(row[startYear], row[startMonth], row[visits]) }
        }
        assertThat(monthlyLoad).containsExactly(Triple(2026, 3, 3L), Triple(2026, 4, 1L))

        // Early openings on a given day, comparing the time column and the date part of a datetime.
        val earlyOnMarchNinth: Long = transaction(database) {
            Appointments.selectAll()
                .where {
                    (Appointments.startsAt.date() eq dateParam(LocalDate(2026, 3, 9))) and
                        (Appointments.opensAt less timeLiteral(LocalTime(8, 30)))
                }
                .count()
        }
        assertThat(earlyOnMarchNinth).isEqualTo(2)

        // Bookings made before March, compared against a timestamp literal.
        val earlyBookers: List<String> = transaction(database) {
            Appointments.select(Appointments.patient)
                .where { Appointments.bookedAt less timestampLiteral(Instant.parse("2026-03-01T00:00:00Z")) }
                .orderBy(Appointments.bookedAt to SortOrder.ASC)
                .map { row -> row[Appointments.patient] }
        }
        assertThat(earlyBookers).containsExactly("Ada", "Grace")

        // Margaret moves to the next morning and asks for a shorter slot.
        transaction(database) {
            val updated: Int = Appointments.update({ Appointments.id eq ids.getValue("Margaret") }) {
                it[clinicDay] = LocalDate(2026, 4, 3)
                it[startsAt] = LocalDateTime(2026, 4, 3, 8, 15)
                it[length] = 40.minutes
            }
            assertThat(updated).isEqualTo(1)
        }
        val rescheduled: Triple<LocalDateTime, Int, Duration> = transaction(database) {
            val clinicMonth = Appointments.clinicDay.month()
            Appointments.select(Appointments.startsAt, clinicMonth, Appointments.length)
                .where { Appointments.patient eq "Margaret" }
                .single()
                .let { row -> Triple(row[Appointments.startsAt], row[clinicMonth], row[Appointments.length]) }
        }
        assertThat(rescheduled).isEqualTo(Triple(LocalDateTime(2026, 4, 3, 8, 15), 4, 40.minutes))

        val totalBooked: Duration = transaction(database) {
            Appointments.selectAll().map { row -> row[Appointments.length] }.fold(Duration.ZERO, Duration::plus)
        }
        assertThat(totalBooked).isEqualTo(135.minutes)

        transaction(database) {
            SchemaUtils.drop(Appointments)
        }
    }
}
