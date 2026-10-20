/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import io.circe.Decoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.jsonDecoderAdaptive
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._

/** Exercises adaptive JSON entity decoding for live and backlogged telemetry intake. */
class AdaptiveTelemetryIntakeTest {
  private final case class Reading(device: String, watts: Int)
  private final case class Batch(readings: List[Reading])

  private given Decoder[Reading] = deriveDecoder[Reading]
  private given Decoder[Batch] = deriveDecoder[Batch]

  // Buffer short live reports, but parse larger or unbounded uploads incrementally.
  private val bufferingCutoff: Long = 256L
  private given EntityDecoder[IO, Json] =
    jsonDecoderAdaptive[IO](bufferingCutoff, MediaType.application.json)

  @Test
  @Timeout(60)
  def summarizeLiveReportsAndGatewayBacklogs(): Unit = {
    val live: Json = document(List(Reading("meter-a", 120), Reading("meter-b", 80)))
    val backlog: Json = document((1 to 40).toList.map { index =>
      Reading(s"meter-${index % 4}", index)
    })
    val liveRequest: Request[IO] = knownLengthRequest(live)
    val backlogRequest: Request[IO] = knownLengthRequest(backlog)
    val streamedRequest: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/telemetry"))
      .withBodyStream(Stream.emits(backlog.noSpaces.getBytes(StandardCharsets.UTF_8).toSeq)
        .covary[IO].chunkN(17).flatMap(Stream.chunk))
      .putHeaders(`Content-Type`(MediaType.application.json))

    assertTrue(liveRequest.contentLength.exists(_ < bufferingCutoff))
    assertTrue(backlogRequest.contentLength.exists(_ >= bufferingCutoff))
    assertEquals(None, streamedRequest.contentLength)

    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case request @ POST -> Root / "telemetry" =>
        for {
          json <- request.as[Json]
          batch <- IO.fromEither(json.as[Batch])
          response <- Ok(Json.obj(
            "samples" -> Json.fromInt(batch.readings.size),
            "devices" -> Json.fromInt(batch.readings.map(_.device).distinct.size),
            "totalWatts" -> Json.fromInt(batch.readings.map(_.watts).sum)
          ))
        } yield response
    }

    val result: IO[Unit] = for {
      _ <- verifySummary(routes, liveRequest, 2, 2, 200)
      _ <- verifySummary(routes, backlogRequest, 40, 4, 820)
      // A gateway may send the same backlog without knowing its encoded length.
      _ <- verifySummary(routes, streamedRequest, 40, 4, 820)
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }

  private def document(readings: List[Reading]): Json = Json.obj(
    "readings" -> Json.fromValues(readings.map { reading =>
      Json.obj("device" -> Json.fromString(reading.device), "watts" -> Json.fromInt(reading.watts))
    })
  )

  private def knownLengthRequest(document: Json): Request[IO] =
    Request[IO](Method.POST, Uri.unsafeFromString("/telemetry"))
      .withEntity(document.noSpaces)
      .putHeaders(`Content-Type`(MediaType.application.json))

  private def verifySummary(
      routes: HttpRoutes[IO],
      request: Request[IO],
      samples: Int,
      devices: Int,
      totalWatts: Int
  ): IO[Unit] = for {
    response <- routes.orNotFound.run(request)
    summary <- response.as[Json]
    _ <- IO {
      assertEquals(Status.Ok, response.status)
      assertEquals(Some(`Content-Type`(MediaType.application.json)), response.contentType)
      assertEquals(Json.obj(
        "samples" -> Json.fromInt(samples),
        "devices" -> Json.fromInt(devices),
        "totalWatts" -> Json.fromInt(totalWatts)
      ), summary)
    }
  } yield ()
}
