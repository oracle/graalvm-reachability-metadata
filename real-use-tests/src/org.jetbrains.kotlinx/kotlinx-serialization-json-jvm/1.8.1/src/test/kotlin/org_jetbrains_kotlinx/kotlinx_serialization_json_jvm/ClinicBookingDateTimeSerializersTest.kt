/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.serializers.DayOfWeekSerializer
import kotlinx.datetime.serializers.InstantComponentSerializer
import kotlinx.datetime.serializers.LocalDateComponentSerializer
import kotlinx.datetime.serializers.LocalTimeComponentSerializer
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Exercises the kotlinx-datetime serializers in `Json`: the default ISO-8601 serializers of
 * `Instant`, `LocalDate`, `LocalTime`, `TimeZone`, `UtcOffset` and `DateTimePeriod`, the
 * polymorphic `DateTimeUnit` serializer, `LocalDate` map keys, and the component serializers
 * that write dates and instants as objects of numeric fields.
 *
 * A clinic stores bookings as JSON, computes visit ends and reminder times from the decoded
 * values, and exports appointments to a partner system that expects numeric date fields.
 */
public class ClinicBookingDateTimeSerializersTest {

    @Serializable
    public data class Booking(
        val patient: String,
        val startsAt: Instant,
        val clinicZone: TimeZone,
        val visitLength: DateTimePeriod,
        val remindBefore: DateTimeUnit,
        val clinicOffset: UtcOffset,
    )

    @Serializable
    public data class Agenda(
        val clinic: String,
        val openOn: List<@Serializable(with = DayOfWeekSerializer::class) DayOfWeek>,
        val opensAt: LocalTime,
        val days: Map<LocalDate, List<String>>,
    )

    @Serializable
    public data class PartnerAppointment(
        val patient: String,
        @Serializable(with = LocalDateComponentSerializer::class) val day: LocalDate,
        @Serializable(with = LocalTimeComponentSerializer::class) val time: LocalTime,
        @Serializable(with = InstantComponentSerializer::class) val bookedAt: Instant,
    )

    /** Keeps bookings as JSON text and answers questions about their times. */
    public class BookingBook(private val json: Json) {
        private val rows: MutableMap<String, String> = linkedMapOf()

        public fun save(booking: Booking): String {
            val text: String = json.encodeToString(booking)
            rows[booking.patient] = text
            return text
        }

        public fun load(patient: String): Booking = json.decodeFromString(rows.getValue(patient))

        public fun visitEnd(patient: String): LocalDateTime {
            val booking: Booking = load(patient)
            return booking.startsAt.plus(booking.visitLength, booking.clinicZone)
                .toLocalDateTime(booking.clinicZone)
        }

        public fun reminderAt(patient: String): LocalDateTime {
            val booking: Booking = load(patient)
            return booking.startsAt.minus(1, booking.remindBefore, booking.clinicZone)
                .toLocalDateTime(booking.clinicZone)
        }
    }

    private val json: Json = Json

    private val berlin: TimeZone = TimeZone.of("Europe/Berlin")

    private fun booking(patient: String, at: LocalDateTime, length: DateTimePeriod, remind: DateTimeUnit): Booking =
        Booking(patient, at.toInstant(berlin), berlin, length, remind, UtcOffset(hours = 1))

    @Test
    fun bookingsTravelAsIsoStringsAndDriveScheduleArithmetic() {
        val book: BookingBook = BookingBook(json)
        val text: String = book.save(
            booking(
                "ana",
                LocalDateTime(2025, 3, 30, 1, 30),
                DateTimePeriod(hours = 1, minutes = 15),
                DateTimeUnit.DAY,
            ),
        )
        assertEquals(
            """{"patient":"ana","startsAt":"2025-03-30T00:30:00Z","clinicZone":"Europe/Berlin",""" +
                """"visitLength":"PT1H15M","remindBefore":{"type":"kotlinx.datetime.DayBased","days":1},""" +
                """"clinicOffset":"+01:00"}""",
            text,
        )

        val loaded: Booking = book.load("ana")
        assertEquals(Instant.parse("2025-03-30T00:30:00Z"), loaded.startsAt)
        assertEquals(berlin, loaded.clinicZone)
        assertEquals(DateTimePeriod(hours = 1, minutes = 15), loaded.visitLength)
        assertEquals(DateTimeUnit.DAY, loaded.remindBefore)
        assertEquals(UtcOffset(hours = 1), loaded.clinicOffset)

        // The visit crosses the switch to summer time, so local wall time jumps an extra hour.
        assertEquals(LocalDateTime(2025, 3, 30, 3, 45), book.visitEnd("ana"))
        assertEquals(LocalDateTime(2025, 3, 29, 1, 30), book.reminderAt("ana"))

        val benText: String = book.save(
            booking(
                "ben",
                LocalDateTime(2025, 3, 30, 4, 0),
                DateTimePeriod(minutes = 40),
                DateTimeUnit.HOUR * 3,
            ),
        )
        val unit: JsonObject = json.parseToJsonElement(benText).jsonObject.getValue("remindBefore").jsonObject
        assertEquals("kotlinx.datetime.TimeBased", unit.getValue("type").jsonPrimitive.content)
        assertEquals(10_800_000_000_000L, unit.getValue("nanoseconds").jsonPrimitive.long)
        assertEquals(DateTimeUnit.HOUR * 3, book.load("ben").remindBefore)
        assertEquals(LocalDateTime(2025, 3, 30, 0, 0), book.reminderAt("ben"))
    }

    @Test
    fun agendaKeysDaysByIsoDateAndListsOpeningDays() {
        val agenda: Agenda = Agenda(
            clinic = "north",
            openOn = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            opensAt = LocalTime(8, 30),
            days = linkedMapOf(
                LocalDate(2025, 3, 3) to listOf("ana", "ben"),
                LocalDate(2025, 3, 5) to listOf("cleo"),
            ),
        )

        val text: String = json.encodeToString(agenda)
        assertEquals(
            """{"clinic":"north","openOn":["MONDAY","WEDNESDAY","FRIDAY"],"opensAt":"08:30",""" +
                """"days":{"2025-03-03":["ana","ben"],"2025-03-05":["cleo"]}}""",
            text,
        )

        val decoded: Agenda = json.decodeFromString(text)
        assertEquals(agenda, decoded)
        val firstDay: LocalDate = decoded.days.keys.first()
        assertEquals(DayOfWeek.MONDAY, firstDay.dayOfWeek)
        assertEquals(LocalDate(2025, 3, 5), firstDay.plus(2, DateTimeUnit.DAY))
        assertEquals(LocalDateTime(2025, 3, 3, 8, 30), firstDay.atTime(decoded.opensAt))
    }

    @Test
    fun partnerExportWritesNumericDateComponents() {
        val appointment: PartnerAppointment = PartnerAppointment(
            patient = "cleo",
            day = LocalDate(2025, 3, 5),
            time = LocalTime(14, 5),
            bookedAt = Instant.fromEpochSeconds(1740787200, 250_000_000),
        )

        val text: String = json.encodeToString(appointment)
        assertEquals(
            """{"patient":"cleo","day":{"year":2025,"month":3,"day":5},"time":{"hour":14,"minute":5},""" +
                """"bookedAt":{"epochSeconds":1740787200,"nanosecondsOfSecond":250000000}}""",
            text,
        )
        assertEquals(appointment, json.decodeFromString<PartnerAppointment>(text))

        // The partner omits zero seconds and nanoseconds; fields arrive in any order.
        val inbound: PartnerAppointment = json.decodeFromString(
            """{"bookedAt":{"epochSeconds":1740787200},"time":{"minute":0,"hour":9},""" +
                """"day":{"day":7,"month":3,"year":2025},"patient":"dan"}""",
        )
        assertEquals(LocalDateTime(2025, 3, 7, 9, 0), inbound.day.atTime(inbound.time))
        assertEquals(Instant.parse("2025-03-01T00:00:00Z"), inbound.bookedAt)
    }

    @Test
    fun malformedDatesAreRejected() {
        val badDate: String = """{"clinic":"north","openOn":[],"opensAt":"08:30","days":{"2025-02-30":[]}}"""
        assertThrows(IllegalArgumentException::class.java) { json.decodeFromString<Agenda>(badDate) }

        val badZone: String = """
            {"patient":"dan","startsAt":"2025-03-01T10:00:00Z","clinicZone":"Mars/Olympus",
             "visitLength":"PT30M","remindBefore":{"type":"kotlinx.datetime.DayBased","days":1},
             "clinicOffset":"+01:00"}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) { json.decodeFromString<Booking>(badZone) }
    }
}
