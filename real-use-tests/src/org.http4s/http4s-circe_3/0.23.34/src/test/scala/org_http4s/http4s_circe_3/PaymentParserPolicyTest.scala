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
import io.circe.Json
import io.circe.jawn.CirceSupportParser
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.CirceInstances
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

/** Exercises configurable JSON parser policies for an unambiguous payment authorization API. */
class PaymentParserPolicyTest {
  private val codecs: CirceInstances = CirceInstances.builder
    .withCirceSupportParser(new CirceSupportParser(
      maxValueSize = Some(64),
      allowDuplicateKeys = false
    ))
    .build
  // Payment documents are small; buffer them for a complete parse before authorizing.
  private given EntityDecoder[IO, Json] = codecs.jsonDecoderByteBuffer[IO]

  @Test
  @Timeout(60)
  def rejectAmbiguousOrOversizedPaymentsBeforeRecordingAuthorization(): Unit = {
    val valid: String = """{"account":"merchant-42","amountCents":2500}"""
    val duplicateAmount: String =
      """{"account":"merchant-42","amountCents":2500,"amountCents":1}"""
    val oversizedAccount: String =
      s"""{"account":"${"x" * 128}","amountCents":2500}"""
    val expected: Json = Json.obj(
      "account" -> Json.fromString("merchant-42"),
      "amountCents" -> Json.fromInt(2500)
    )

    val result: IO[Unit] = for {
      authorized <- Ref.of[IO, List[Json]](Nil)
      routes = HttpRoutes.of[IO] {
        case request @ POST -> Root / "authorizations" =>
          request.decodeStrict[Json] { payment =>
            authorized.update(_ :+ payment) *> Created(payment)
          }
      }
      // A gateway can forward a buffered body or a stream without Content-Length.
      _ <- List(false, true).foldLeft(IO.unit) { (previous, streamed) =>
        previous *> (for {
          duplicate <- routes.orNotFound.run(submission(duplicateAmount, streamed))
          _ <- duplicate.body.compile.drain
          oversized <- routes.orNotFound.run(submission(oversizedAccount, streamed))
          _ <- oversized.body.compile.drain
          afterRejections <- authorized.get
          _ <- IO {
            assertEquals(Status.BadRequest, duplicate.status)
            assertEquals(Status.BadRequest, oversized.status)
            assertEquals(List.empty[Json], afterRejections)
          }
        } yield ())
      }
      accepted <- routes.orNotFound.run(submission(valid, streamed = true))
      receipt <- accepted.as[Json]
      saved <- authorized.get
      _ <- IO {
        assertEquals(Status.Created, accepted.status)
        assertEquals(Some(`Content-Type`(MediaType.application.json)), accepted.contentType)
        assertEquals(expected, receipt)
        assertEquals(List(expected), saved)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }

  private def submission(body: String, streamed: Boolean): Request[IO] = {
    val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/authorizations"))
    val withBody: Request[IO] = if (streamed) {
      request.withBodyStream(Stream.emits(body.getBytes(StandardCharsets.UTF_8))
        .covary[IO].chunkN(7).flatMap(Stream.chunk))
    } else {
      request.withEntity(body)
    }
    withBody.withContentType(`Content-Type`(MediaType.application.json))
  }
}
