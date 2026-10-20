/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.*
import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises automatic product derivation with locally supplied timestamp codecs for scheduling APIs. */
@Timeout(60)
class SchedulingTimestampTest {
  final case class TimeWindow(start: Instant, end: Instant)

  final case class Appointment(id: String, window: TimeWindow, reminders: List[Instant])

  final case class Schedule(owner: String, appointments: List[Appointment])

  @Test
  def importsAndReschedulesAppointmentsUsingEpochMillisecondsThroughout(): Unit = {
    // This integration uses numeric timestamps instead of Circe's standard ISO string format.
    given Codec[Instant] = Codec.from(
      Decoder.decodeLong.map(Instant.ofEpochMilli),
      Encoder.encodeLong.contramap[Instant](_.toEpochMilli)
    )

    val incoming: String = """
      {
        "owner": "dispatch-team",
        "appointments": [{
          "id": "collection-17",
          "window": {"start": 1735722000000, "end": 1735723800000},
          "reminders": [1735721100000]
        }]
      }
      """
    val schedule: Schedule = decode[Schedule](incoming).fold(
      error => throw new AssertionError(s"Schedule was rejected: $error"),
      identity
    )
    val start: Instant = Instant.parse("2025-01-01T09:00:00Z")
    val end: Instant = Instant.parse("2025-01-01T09:30:00Z")
    val reminder: Instant = Instant.parse("2025-01-01T08:45:00Z")
    assertThat(schedule).isEqualTo(Schedule("dispatch-team", List(
      Appointment("collection-17", TimeWindow(start, end), List(reminder))
    )))
    assertThat(schedule.asJson).isEqualTo(fixture(incoming))

    val appointment: Appointment = schedule.appointments.head
    assertThat(Duration.between(appointment.window.start, appointment.window.end))
      .isEqualTo(Duration.ofMinutes(30))
    assertThat(Duration.between(appointment.reminders.head, appointment.window.start))
      .isEqualTo(Duration.ofMinutes(15))

    val rescheduled: Schedule = schedule.copy(appointments = schedule.appointments.map { entry =>
      entry.copy(
        window = TimeWindow(entry.window.start.plusSeconds(3600), entry.window.end.plusSeconds(3600)),
        reminders = entry.reminders.map(_.plusSeconds(3600))
      )
    })
    val published: Json = rescheduled.asJson
    assertThat(published).isEqualTo(fixture("""
      {
        "owner": "dispatch-team",
        "appointments": [{
          "id": "collection-17",
          "window": {"start": 1735725600000, "end": 1735727400000},
          "reminders": [1735724700000]
        }]
      }
      """))
    assertThat(decode[Schedule](published.noSpaces)).isEqualTo(Right(rescheduled))
    assertThat(rescheduled.appointments.head.window.start)
      .isEqualTo(Instant.parse("2025-01-01T10:00:00Z"))
    assertThat(schedule.appointments.head.window.start).isEqualTo(start)

    // A writer using the wrong timestamp convention cannot silently enter this integration.
    val wrongFormat: String = incoming.replace("1735722000000", "\"2025-01-01T09:00:00Z\"")
    assertThat(decode[Schedule](wrongFormat).isLeft).isTrue()
  }

  private def fixture(input: String): Json = parse(input).fold(
    error => throw new AssertionError(s"Invalid schedule fixture: $error"),
    identity
  )
}
