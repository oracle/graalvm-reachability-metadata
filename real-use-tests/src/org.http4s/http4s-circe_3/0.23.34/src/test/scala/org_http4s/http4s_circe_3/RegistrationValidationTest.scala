/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.CursorOp
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.DecodingFailures
import org.http4s.circe.accumulatingJsonOf
import org.http4s.circe.jsonDecoder
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises accumulating JSON validation for a registration API returning every invalid field. */
class RegistrationValidationTest {
  private final case class Registration(email: String, seats: Int, newsletter: Boolean)

  private given Decoder[Registration] = deriveDecoder[Registration]
  private given EntityDecoder[IO, Registration] = accumulatingJsonOf[IO, Registration]

  @Test
  @Timeout(60)
  def reportAllInvalidFieldsBeforeAcceptingACorrectedRegistration(): Unit = {
    val result: IO[Unit] = for {
      registrations <- Ref.of[IO, List[Registration]](Nil)
      routes = HttpRoutes.of[IO] {
        case request @ POST -> Root / "registrations" =>
          request.attemptAs[Registration].value.flatMap {
            case Right(registration) =>
              registrations.update(_ :+ registration) *>
                Created(Json.obj("email" -> Json.fromString(registration.email)))
            case Left(failure) =>
              Option(failure.getCause) match {
                case Some(errors: DecodingFailures) =>
                  UnprocessableEntity(validationReport(errors.failures.toList))
                case Some(error: DecodingFailure) =>
                  UnprocessableEntity(validationReport(List(error)))
                case _ => IO.pure(failure.toHttpResponse[IO](request.httpVersion))
              }
          }
      }
      rejected <- routes.orNotFound.run(submission(Json.obj(
        "email" -> Json.fromInt(42),
        "seats" -> Json.fromString("many"),
        "newsletter" -> Json.fromString("yes")
      )))
      errors <- rejected.as[Json]
      storedAfterRejection <- registrations.get
      _ <- IO {
        assertEquals(Status.UnprocessableEntity, rejected.status)
        assertEquals(expectedFields("email", "seats", "newsletter"), errors)
        assertEquals(List.empty[Registration], storedAfterRejection)
      }
      partiallyCorrected <- routes.orNotFound.run(submission(Json.obj(
        "email" -> Json.fromString("buyer@example.test"),
        "seats" -> Json.fromString("many"),
        "newsletter" -> Json.True
      )))
      remainingErrors <- partiallyCorrected.as[Json]
      _ <- IO {
        assertEquals(Status.UnprocessableEntity, partiallyCorrected.status)
        assertEquals(expectedFields("seats"), remainingErrors)
      }
      accepted <- routes.orNotFound.run(submission(Json.obj(
        "email" -> Json.fromString("buyer@example.test"),
        "seats" -> Json.fromInt(3),
        "newsletter" -> Json.True
      )))
      receipt <- accepted.as[Json]
      stored <- registrations.get
      _ <- IO {
        assertEquals(Status.Created, accepted.status)
        assertEquals(Json.obj("email" -> Json.fromString("buyer@example.test")), receipt)
        assertEquals(List(Registration("buyer@example.test", 3, true)), stored)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }

  private def submission(document: Json): Request[IO] =
    Request[IO](Method.POST, Uri.unsafeFromString("/registrations")).withEntity(document)

  private def validationReport(errors: List[DecodingFailure]): Json = {
    val fields: List[String] = errors.map { error =>
      error.history.collect { case CursorOp.DownField(name) => name }.reverse.mkString(".")
    }
    expectedFields(fields.sorted*)
  }

  private def expectedFields(fields: String*): Json =
    Json.obj("invalidFields" -> Json.arr(fields.sorted.map(Json.fromString)*))
}
