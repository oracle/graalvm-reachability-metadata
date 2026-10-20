/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Printer
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.CirceInstances
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises configurable JSON printing for a human-readable settings download. */
class PrettySettingsDownloadTest {
  private final case class Settings(
      displayName: String,
      destination: String,
      enabled: Boolean,
      notificationEmail: Option[String]
  )

  private given Encoder[Settings] = deriveEncoder[Settings]
  private given Decoder[Settings] = deriveDecoder[Settings]
  private given EntityEncoder[IO, Settings] = CirceInstances
    .withPrinter(Printer.spaces2.copy(dropNullValues = true, sortKeys = true))
    .build
    .jsonEncoderOf[IO, Settings]
  private given EntityDecoder[IO, Settings] = jsonOf[IO, Settings]

  @Test
  @Timeout(60)
  def downloadReadableSettingsThatCanBeImportedAgain(): Unit = {
    val settings: Settings = Settings("Nightly \"archive\"", "C:\\exports", true, None)
    val expectedBody: String =
      """{
        |  "destination" : "C:\\exports",
        |  "displayName" : "Nightly \"archive\"",
        |  "enabled" : true
        |}""".stripMargin
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "settings" / "export" => Ok(settings)
    }
    val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)
    val request: Request[IO] = Request[IO](uri = Uri.unsafeFromString("/settings/export"))

    val result: IO[Unit] = for {
      _ <- client.run(request).use { response =>
        response.as[String].flatMap { body =>
          IO {
            assertEquals(Status.Ok, response.status)
            assertEquals(Some(`Content-Type`(MediaType.application.json)), response.contentType)
            // The downloaded file is readable, stable, escaped, and omits unset options.
            assertEquals(expectedBody, body)
          }
        }
      }
      imported <- client.expect[Settings](request)
      _ <- IO(assertEquals(settings, imported))
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }
}
