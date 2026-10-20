/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsNumber
import play.api.libs.json.JsPath
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import play.api.libs.json.Writes

/**
 * Built-in java.time and UUID Reads and Writes, including pattern-based and epoch-millisecond codecs.
 *
 * A clinic booking service exchanges appointments with a partner calendar. Appointments carry a
 * UUID, an instant, a zoned start time, a local time slot, an ISO duration and a time zone, all
 * encoded by play-json's default temporal formats. The service also reads a legacy feed that
 * writes dates as `dd/MM/yyyy` and instants as epoch milliseconds, and rejects malformed values
 * with errors at the offending paths.
 */
class ClinicSchedulingTemporalFormatsTest {
  import ClinicSchedulingTemporalFormatsTest.*

  @Test
  def appointmentRoundTripsThroughDefaultTemporalFormats(): Unit = {
    val appointment: Appointment = Appointment(
      id = UUID.fromString("3f2a8c1e-5b7d-4e0f-9a6b-1c2d3e4f5a6b"),
      bookedAt = Instant.parse("2026-03-01T08:15:30Z"),
      startsAt = ZonedDateTime.of(2026, 3, 29, 9, 30, 0, 0, ZoneId.of("Europe/Berlin")),
      day = LocalDate.of(2026, 3, 29),
      slot = LocalTime.of(9, 30),
      length = Duration.ofMinutes(45),
      clinicZone = ZoneId.of("Europe/Berlin"),
      confirmedAt = OffsetDateTime.of(2026, 3, 2, 10, 0, 0, 0, ZoneOffset.ofHours(1)),
      reminderAt = LocalDateTime.of(2026, 3, 28, 18, 0)
    )

    val written: JsValue = Json.toJson(appointment)

    assertEquals(JsString("3f2a8c1e-5b7d-4e0f-9a6b-1c2d3e4f5a6b"), (written \ "id").get)
    assertEquals(JsString("2026-03-01T08:15:30Z"), (written \ "bookedAt").get)
    assertEquals(JsString("2026-03-29"), (written \ "day").get)
    assertEquals(JsString("09:30:00"), (written \ "slot").get)
    assertEquals(JsString("PT45M"), (written \ "length").get)
    assertEquals(JsString("Europe/Berlin"), (written \ "clinicZone").get)
    assertEquals(JsString("2026-03-28T18:00:00"), (written \ "reminderAt").get)

    val decoded: JsResult[Appointment] = Json.parse(Json.stringify(written)).validate[Appointment]
    assertEquals(JsSuccess(appointment), decoded)

    val endsAt: ZonedDateTime = decoded.get.startsAt.plus(decoded.get.length)
    assertEquals(Instant.parse("2026-03-29T08:15:00Z"), endsAt.toInstant)
  }

  @Test
  def partnerCalendarDocumentIsDecodedFromIsoText(): Unit = {
    val document: JsValue = Json.parse(
      """{
        |  "id": "0b9e7d6c-5a4f-4321-8edc-ba9876543210",
        |  "bookedAt": "2026-06-10T07:00:00Z",
        |  "startsAt": "2026-06-12T14:00:00-04:00[America/New_York]",
        |  "day": "2026-06-12",
        |  "slot": "14:00",
        |  "length": "PT1H30M",
        |  "clinicZone": "America/New_York",
        |  "confirmedAt": "2026-06-10T09:00:00+02:00",
        |  "reminderAt": "2026-06-11T20:00:00"
        |}""".stripMargin
    )

    val appointment: Appointment = document.as[Appointment]

    assertEquals(UUID.fromString("0b9e7d6c-5a4f-4321-8edc-ba9876543210"), appointment.id)
    assertEquals(ZoneId.of("America/New_York"), appointment.startsAt.getZone)
    assertEquals(Instant.parse("2026-06-12T18:00:00Z"), appointment.startsAt.toInstant)
    assertEquals(LocalTime.of(14, 0), appointment.slot)
    assertEquals(Duration.ofMinutes(90), appointment.length)
    assertEquals(Instant.parse("2026-06-10T07:00:00Z"), appointment.confirmedAt.toInstant)
    assertEquals(LocalDateTime.of(2026, 6, 11, 20, 0), appointment.reminderAt)
  }

  @Test
  def legacyFeedUsesCustomDatePatternAndEpochMillis(): Unit = {
    val feed: JsValue = Json.parse(
      """[
        |  {"patient": "P-100", "visitDate": "05/11/2026", "recordedAt": 1793865600000},
        |  {"patient": "P-101", "visitDate": "31/12/2026", "recordedAt": 1798732800000}
        |]""".stripMargin
    )

    val visits: List[LegacyVisit] = feed.as[List[LegacyVisit]]

    assertEquals(
      List(
        LegacyVisit("P-100", LocalDate.of(2026, 11, 5), Instant.parse("2026-11-05T08:00:00Z")),
        LegacyVisit("P-101", LocalDate.of(2026, 12, 31), Instant.parse("2026-12-31T16:00:00Z"))
      ),
      visits
    )

    val exported: JsValue = Json.toJson(visits)
    assertEquals(JsNumber(BigDecimal(1793865600000L)), (exported(0) \ "recordedAt").get)
    assertEquals(feed, exported)
  }

  @Test
  def malformedTemporalValuesAreReportedAtTheirPaths(): Unit = {
    val document: JsValue = Json.parse(
      """{
        |  "id": "not-a-uuid",
        |  "bookedAt": "2026-06-10T07:00:00Z",
        |  "startsAt": "2026-06-12T14:00:00-04:00[America/New_York]",
        |  "day": "2026-02-30",
        |  "slot": "14:00",
        |  "length": "ninety minutes",
        |  "clinicZone": "Mars/Olympus_Mons",
        |  "confirmedAt": "2026-06-10T09:00:00+02:00",
        |  "reminderAt": "2026-06-11T20:00:00"
        |}""".stripMargin
    )

    val result: JsResult[Appointment] = document.validate[Appointment]

    result match {
      case JsError(errors) =>
        val failedPaths: Set[JsPath] = errors.map(_._1).toSet
        assertEquals(
          Set(JsPath \ "id", JsPath \ "day", JsPath \ "length", JsPath \ "clinicZone"),
          failedPaths
        )
      case JsSuccess(value, _) =>
        throw new AssertionError(s"expected an error but decoded $value")
    }

    val wrongLegacyDate: JsResult[LegacyVisit] = Json
      .parse("""{"patient": "P-102", "visitDate": "2026-11-05", "recordedAt": 0}""")
      .validate[LegacyVisit]
    assertTrue(wrongLegacyDate.isError, s"expected an error but got $wrongLegacyDate")
  }
}

object ClinicSchedulingTemporalFormatsTest {

  final case class Appointment(
      id: UUID,
      bookedAt: Instant,
      startsAt: ZonedDateTime,
      day: LocalDate,
      slot: LocalTime,
      length: Duration,
      clinicZone: ZoneId,
      confirmedAt: OffsetDateTime,
      reminderAt: LocalDateTime
  )

  given OFormat[Appointment] = Json.format[Appointment]

  final case class LegacyVisit(patient: String, visitDate: LocalDate, recordedAt: Instant)

  private val legacyDatePattern: String = "dd/MM/yyyy"

  /** Legacy dates use a day-first pattern instead of ISO-8601. */
  private val legacyDateFormat: Format[LocalDate] = Format(
    Reads.localDateReads(legacyDatePattern),
    Writes.temporalWrites[LocalDate, DateTimeFormatter](DateTimeFormatter.ofPattern(legacyDatePattern))
  )

  /** Legacy instants are epoch milliseconds, which the default instant reads accept as numbers. */
  private val legacyInstantFormat: Format[Instant] = Format(
    Reads.DefaultInstantReads,
    Writes.InstantEpochMilliWrites
  )

  given OFormat[LegacyVisit] = {
    given Format[LocalDate] = legacyDateFormat
    given Format[Instant] = legacyInstantFormat
    Json.format[LegacyVisit]
  }
}
