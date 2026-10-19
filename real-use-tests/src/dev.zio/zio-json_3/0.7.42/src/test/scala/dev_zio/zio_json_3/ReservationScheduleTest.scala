/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import java.time.{Duration, Instant, ZonedDateTime}
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.*

/** Exercises Java time codecs for scheduling room reservations across daylight-saving changes. */
class ReservationScheduleTest {
  final case class Reservation(
    room: String,
    startsAt: ZonedDateTime,
    duration: Duration,
    confirmBy: Instant
  )
  final case class Confirmation(room: String, startsAt: ZonedDateTime, endsAt: ZonedDateTime)

  given JsonCodec[Reservation] = DeriveJsonCodec.gen[Reservation]
  given JsonCodec[Confirmation] = DeriveJsonCodec.gen[Confirmation]

  @Test
  @Timeout(30)
  def importReservationsAndPublishUnambiguousConfirmationTimes(): Unit = {
    val incoming: String = """
      [
        {
          "room": "east",
          "startsAt": "2025-03-09T01:30:00-05:00[America/New_York]",
          "duration": "PT2H",
          "confirmBy": "2025-03-08T18:00:00Z"
        },
        {
          "room": "west",
          "startsAt": "2025-11-02T01:30:00-04:00[America/New_York]",
          "duration": "PT2H",
          "confirmBy": "2025-11-01T18:00:00Z"
        }
      ]
      """
    val reservations: List[Reservation] = incoming.fromJson[List[Reservation]].fold(
      error => fail[List[Reservation]](error),
      identity
    )
    assertEquals(2, reservations.size)
    assertEquals(List("east", "west"), reservations.map(_.room))
    assertEquals(List.fill(2)(Duration.ofHours(2)), reservations.map(_.duration))
    assertEquals(
      List(Instant.parse("2025-03-08T18:00:00Z"), Instant.parse("2025-11-01T18:00:00Z")),
      reservations.map(_.confirmBy)
    )
    assertTrue(reservations.forall(r => r.confirmBy.isBefore(r.startsAt.toInstant)))

    val confirmations: List[Confirmation] = reservations.map { reservation =>
      Confirmation(reservation.room, reservation.startsAt, reservation.startsAt.plus(reservation.duration))
    }
    assertEquals(
      List(
        ZonedDateTime.parse("2025-03-09T04:30:00-04:00[America/New_York]"),
        ZonedDateTime.parse("2025-11-02T02:30:00-05:00[America/New_York]")
      ),
      confirmations.map(_.endsAt),
      "Two elapsed hours must not be confused with two wall-clock hours at a clock change"
    )
    assertEquals(
      List(Instant.parse("2025-03-09T08:30:00Z"), Instant.parse("2025-11-02T07:30:00Z")),
      confirmations.map(_.endsAt.toInstant)
    )

    val published: String = confirmations.toJson
    assertEquals(Right(confirmations), published.fromJson[List[Confirmation]])
    val customerDocuments: List[Map[String, String]] = published.fromJson[List[Map[String, String]]].fold(
      error => fail[List[Map[String, String]]](error),
      identity
    )
    assertTrue(customerDocuments.head("endsAt").endsWith("-04:00[America/New_York]"), published)
    assertTrue(customerDocuments(1)("endsAt").endsWith("-05:00[America/New_York]"), published)
    assertEquals(Right(reservations), reservations.toJson.fromJson[List[Reservation]])

    val invalidStart: String = incoming.replace("2025-03-09T01:30:00-05:00[America/New_York]", "next Sunday")
    val invalidDuration: String = incoming.replace("PT2H", "two hours")
    val rejectedStart: Either[String, List[Reservation]] = invalidStart.fromJson[List[Reservation]]
    val rejectedDuration: Either[String, List[Reservation]] = invalidDuration.fromJson[List[Reservation]]
    assertTrue(rejectedStart.isLeft, "A non-ISO start must not enter the reservation schedule")
    assertTrue(rejectedDuration.isLeft, "A non-ISO duration must not enter the reservation schedule")
    assertTrue(rejectedStart.swap.toOption.get.contains("startsAt"))
    assertTrue(rejectedDuration.swap.toOption.get.contains("duration"))
  }
}
