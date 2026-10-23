/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.Codec
import io.circe.Json
import io.circe.parser.parse
import org.http4s.HttpApp
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises schema validation of Circe-decoded JSON bodies for seat reservations. */
class ReservationJsonValidationTest extends TapirJsonCirce {
  case class SeatBlock(zone: String, seats: Int) derives Codec.AsObject
  case class Reservation(eventId: String, blocks: List[SeatBlock]) derives Codec.AsObject
  case class Confirmation(eventId: String, reservedSeats: Int) derives Codec.AsObject, Schema

  given Schema[SeatBlock] = Schema.derived[SeatBlock]
    .modify(_.seats)(_.validate(Validator.min(1)))
    .modify(_.seats)(_.validate(Validator.max(6)))
  given Schema[Reservation] = Schema.derived[Reservation]
    .modify(_.eventId)(_.validate(Validator.minLength(1)))
    .modify(_.blocks)(_.validate(Validator.minSize(1)))

  @Test
  @Timeout(60)
  def rejectsInvalidReservationsBeforeRecordingValidSeatBlocks(): Unit = {
    val accepted: Ref[IO, List[Reservation]] = Ref.of[IO, List[Reservation]](Nil).unsafeRunSync()
    val reserve = endpoint.post
      .in("reservations")
      .in(jsonBody[Reservation])
      .out(statusCode(sttp.model.StatusCode.Created))
      .out(jsonBody[Confirmation])
      .serverLogicSuccess[IO] { reservation =>
        accepted.update(_ :+ reservation).as(Confirmation(
          reservation.eventId, reservation.blocks.map(_.seats).sum
        ))
      }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(reserve).orNotFound

    def submit(body: String): (Status, Option[MediaType], String) = {
      val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/reservations"))
        .withEntity(body)
        .putHeaders(`Content-Type`(MediaType.application.json))
      (for {
        response <- app.run(request)
        text <- response.bodyText.compile.string
      } yield (response.status, response.contentType.map(_.mediaType), text))
        .timeout(15.seconds).unsafeRunSync()
    }

    // All payloads are valid JSON with the correct field types; schema constraints reject them.
    val invalid: List[String] = List(
      """{"eventId":"concert-17","blocks":[{"zone":"balcony","seats":0}]}""",
      """{"eventId":"concert-17","blocks":[{"zone":"floor","seats":2},{"zone":"balcony","seats":7}]}""",
      """{"eventId":"concert-17","blocks":[]}""",
      """{"eventId":"","blocks":[{"zone":"floor","seats":2}]}"""
    )
    invalid.foreach { body =>
      assertEquals(Status.BadRequest, submit(body)._1)
      assertEquals(List.empty[Reservation], accepted.get.unsafeRunSync())
    }

    val confirmed: (Status, Option[MediaType], String) = submit("""{
      "eventId": "concert-17",
      "blocks": [{"zone":"floor","seats":1},{"zone":"balcony","seats":6}]
    }""")
    val expected: Json = parse(
      """{"eventId":"concert-17","reservedSeats":7}"""
    ).fold(throw _, identity)
    assertEquals(Status.Created, confirmed._1)
    assertEquals(Some(MediaType.application.json), confirmed._2)
    assertEquals(expected, parse(confirmed._3).fold(throw _, identity))
    assertEquals(
      List(Reservation("concert-17", List(SeatBlock("floor", 1), SeatBlock("balcony", 6)))),
      accepted.get.unsafeRunSync()
    )
  }
}
