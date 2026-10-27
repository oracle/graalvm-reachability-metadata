/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.decode
import org.http4s.{Method, Request}
import org.http4s.implicits.{path as _, *}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises typed error-response variants in Tapir's http4s interpreter for reservation cancellation. */
class ReservationCancellationTest {
  sealed trait CancellationError
  final case class MissingReservation(reservationId: Long) extends CancellationError
  final case class DispatchedReservation(reservationId: Long, trackingNumber: String) extends CancellationError
  final case class AlreadyCancelled(reservationId: Long) extends CancellationError

  enum ReservationState {
    case Reserved, Cancelled
    case Dispatched(trackingNumber: String)
  }

  private given Encoder[MissingReservation] = deriveEncoder[MissingReservation]
  private given Decoder[MissingReservation] = deriveDecoder[MissingReservation]
  private given Schema[MissingReservation] = Schema.derived[MissingReservation]
  private given Encoder[DispatchedReservation] = deriveEncoder[DispatchedReservation]
  private given Decoder[DispatchedReservation] = deriveDecoder[DispatchedReservation]
  private given Schema[DispatchedReservation] = Schema.derived[DispatchedReservation]
  private given Encoder[AlreadyCancelled] = deriveEncoder[AlreadyCancelled]
  private given Decoder[AlreadyCancelled] = deriveDecoder[AlreadyCancelled]
  private given Schema[AlreadyCancelled] = Schema.derived[AlreadyCancelled]

  @Test
  @Timeout(60)
  def cancelsReservedStockAndReturnsActionableDomainErrors(): Unit = {
    val cancellation: PublicEndpoint[Long, CancellationError, Unit, Any] = endpoint.delete
      .in("reservations" / path[Long]("reservationId"))
      .errorOut(oneOf[CancellationError](
        oneOfVariant(StatusCode.NotFound, jsonBody[MissingReservation]),
        oneOfVariant(StatusCode.Conflict, jsonBody[DispatchedReservation]),
        oneOfVariant(StatusCode.Gone, jsonBody[AlreadyCancelled])
      ))
      .out(statusCode(StatusCode.NoContent))

    val scenario: IO[Unit] = for {
      reservations <- Ref.of[IO, Map[Long, ReservationState]](Map(
        41L -> ReservationState.Reserved,
        42L -> ReservationState.Dispatched("TRACK-42")
      ))
      route = cancellation.serverLogic[IO] { (id: Long) =>
        reservations.modify[Either[CancellationError, Unit]] { current =>
          current.get(id) match {
            case None => (current, Left(MissingReservation(id)))
            case Some(ReservationState.Dispatched(tracking)) =>
              (current, Left(DispatchedReservation(id, tracking)))
            case Some(ReservationState.Cancelled) => (current, Left(AlreadyCancelled(id)))
            case Some(ReservationState.Reserved) =>
              (current.updated(id, ReservationState.Cancelled), Right(()))
          }
        }
      }
      app = Http4sServerInterpreter[IO]().toRoutes(route).orNotFound
      missing <- app.run(Request[IO](Method.DELETE, uri"/reservations/99"))
      missingBody <- missing.as[String]
      dispatched <- app.run(Request[IO](Method.DELETE, uri"/reservations/42"))
      dispatchedBody <- dispatched.as[String]
      cancelled <- app.run(Request[IO](Method.DELETE, uri"/reservations/41"))
      cancelledBody <- cancelled.as[String]
      repeated <- app.run(Request[IO](Method.DELETE, uri"/reservations/41"))
      repeatedBody <- repeated.as[String]
      _ <- IO {
        assertEquals(404, missing.status.code)
        assertEquals(Right(MissingReservation(99L)), decode[MissingReservation](missingBody))
        assertEquals(409, dispatched.status.code)
        assertEquals(Right(DispatchedReservation(42L, "TRACK-42")), decode[DispatchedReservation](dispatchedBody))
        assertEquals(204, cancelled.status.code)
        assertEquals("", cancelledBody)
        assertEquals(410, repeated.status.code)
        assertEquals(Right(AlreadyCancelled(41L)), decode[AlreadyCancelled](repeatedBody))
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }
}
