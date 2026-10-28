/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala Either data binding for successful and rejected seat reservations. */
@Timeout(60)
class ReservationOutcomeTest {
  @Test
  def publishesReservationOutcomesForCustomerNotifications(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // The service returns either a rejection reason or the confirmed seat count.
    val requestedSeats: Map[String, Int] = Map("booking-17" -> 2, "booking-18" -> 5)
    val outcomes: Map[String, Either[String, Int]] = requestedSeats.map {
      case (bookingId, count) =>
        val outcome: Either[String, Int] =
          if (count <= 3) Right(count) else Left("party-exceeds-table-capacity")
        bookingId -> outcome
    }
    val published: String = mapper.writeValueAsString(outcomes)
    val document: JsonNode = mapper.readTree(published)
    assertEquals(mapper.readTree(
      """{
        |  "booking-17": {"r": 2},
        |  "booking-18": {"l": "party-exceeds-table-capacity"}
        |}""".stripMargin), document)

    // The notification consumer preserves the success/error distinction across JSON.
    val received: Map[String, Either[String, Int]] =
      mapper.readValue[Map[String, Either[String, Int]]](published)
    assertEquals(Right(2), received("booking-17"))
    assertEquals(Left("party-exceeds-table-capacity"), received("booking-18"))
    assertEquals(outcomes, received)

    val notifications: Map[String, String] = received.map {
      case (bookingId, outcome) => bookingId -> outcome.fold(
        reason => s"Reservation rejected: $reason",
        seats => s"Reservation confirmed for $seats guests")
    }
    assertEquals("Reservation confirmed for 2 guests", notifications("booking-17"))
    assertEquals("Reservation rejected: party-exceeds-table-capacity",
      notifications("booking-18"))

    val confirmedGuests: Int = received.values.map(_.getOrElse(0)).sum
    assertEquals(2, confirmedGuests)
  }
}
