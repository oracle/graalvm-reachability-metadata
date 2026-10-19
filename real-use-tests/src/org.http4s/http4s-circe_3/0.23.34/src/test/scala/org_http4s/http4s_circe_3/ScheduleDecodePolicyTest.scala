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
import fs2.Stream
import io.circe.Decoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes
import org.http4s.InvalidMessageBodyFailure
import org.http4s.MalformedMessageBodyFailure
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.CirceInstances
import org.http4s.circe.jsonDecoder
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

/** Exercises configurable JSON decode-error policies for a scheduling API with stable client errors. */
class ScheduleDecodePolicyTest {
  private final case class Schedule(job: String, hour: Int)

  private given Decoder[Schedule] = deriveDecoder[Schedule]
  private val codecs: CirceInstances = CirceInstances.builder
    .withEmptyBodyMessage(MalformedMessageBodyFailure("body-required"))
    .withCirceParseExceptionMessage(_ => MalformedMessageBodyFailure("invalid-json"))
    .withJawnParseExceptionMessage(_ => MalformedMessageBodyFailure("invalid-json"))
    .withJsonDecodeError((_, _) => InvalidMessageBodyFailure("invalid-schedule"))
    .build
  private given EntityDecoder[IO, Schedule] = codecs.jsonOf[IO, Schedule]

  @Test
  @Timeout(60)
  def returnStableErrorsBeforeAcceptingCorrectedSchedules(): Unit = {
    val expected: Schedule = Schedule("daily-backup", 3)
    val result: IO[Unit] = for {
      saved <- Ref.of[IO, List[Schedule]](Nil)
      routes = HttpRoutes.of[IO] {
        case request @ POST -> Root / "schedules" =>
          request.attemptAs[Schedule].value.flatMap {
            case Right(schedule) =>
              saved.update(_ :+ schedule) *>
                Created(Json.obj("job" -> Json.fromString(schedule.job)))
            // Only configured, public error codes are sent to the client.
            case Left(failure: MalformedMessageBodyFailure) =>
              BadRequest(problem(failure.details))
            case Left(failure: InvalidMessageBodyFailure) =>
              UnprocessableContent(problem(failure.details))
            case Left(failure) => IO.pure(failure.toHttpResponse[IO](request.httpVersion))
          }
      }
      // The API accepts both buffered clients and gateways forwarding streamed bodies.
      _ <- List(false, true).foldLeft(IO.unit) { (previous, streamed) =>
        previous *> (for {
          before <- saved.get
          _ <- verifyFailure(routes, submission("", streamed), Status.BadRequest, "body-required")
          _ <- verifyFailure(routes, submission("{\"job\":", streamed), Status.BadRequest, "invalid-json")
          _ <- verifyFailure(
            routes,
            submission("""{"job":"daily-backup","hour":"overnight"}""", streamed),
            Status.UnprocessableContent,
            "invalid-schedule"
          )
          afterRejections <- saved.get
          accepted <- routes.orNotFound.run(submission(
            """{"job":"daily-backup","hour":3}""", streamed
          ))
          receipt <- accepted.as[Json]
          afterAcceptance <- saved.get
          _ <- IO {
            assertEquals(before, afterRejections)
            assertEquals(Status.Created, accepted.status)
            assertEquals(Json.obj("job" -> Json.fromString(expected.job)), receipt)
            assertEquals(before :+ expected, afterAcceptance)
          }
        } yield ())
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }

  private def problem(code: String): Json = Json.obj("code" -> Json.fromString(code))

  private def submission(body: String, streamed: Boolean): Request[IO] = {
    val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/schedules"))
    val withBody: Request[IO] = if (streamed) {
      request.withBodyStream(Stream.emits(body.getBytes(StandardCharsets.UTF_8))
        .covary[IO].chunkN(8).flatMap(Stream.chunk))
    } else {
      request.withEntity(body)
    }
    withBody.withContentType(`Content-Type`(MediaType.application.json))
  }

  private def verifyFailure(
      routes: HttpRoutes[IO],
      request: Request[IO],
      expectedStatus: Status,
      expectedCode: String
  ): IO[Unit] = for {
    response <- routes.orNotFound.run(request)
    document <- response.as[Json]
    _ <- IO {
      assertEquals(expectedStatus, response.status)
      assertEquals(Some(`Content-Type`(MediaType.application.json)), response.contentType)
      assertEquals(problem(expectedCode), document)
    }
  } yield ()
}
