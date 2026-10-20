/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import org.http4s.{HttpApp, Method, Request}
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.model.ValuedEndpointOutput

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, NoSuchFileException, Path}
import scala.concurrent.duration.*

/** Exercises exception interception and retryable failure responses in Tapir's http4s interpreter. */
class RetryableExportFailureTest {
  @Test
  @Timeout(60)
  def hidesMissingArchiveDetailsAndServesTheExportWhenPublished(): Unit = {
    val handler: ExceptionHandler[IO] = ExceptionHandler.pure[IO] { context =>
      context.e match {
        case _: NoSuchFileException =>
          Some(ValuedEndpointOutput(
            statusCode(StatusCode.ServiceUnavailable).and(header[String]("Retry-After")).and(stringBody),
            ("30", "Daily export is not ready; please retry")
          ))
        case _ => None
      }
    }
    val options: Http4sServerOptions[IO] = Http4sServerOptions.customiseInterceptors[IO]
      .exceptionHandler(handler)
      .options
    val download: PublicEndpoint[Unit, Unit, String, Any] = endpoint.get
      .in("exports" / "daily")
      .out(stringBody)
    val archive: Resource[IO, Path] = Resource.make(
      IO.blocking(Files.createTempDirectory("daily-export-"))
    ) { directory =>
      IO.blocking {
        Files.deleteIfExists(directory.resolve("daily.csv"))
        Files.delete(directory)
      }
    }

    val scenario: IO[Unit] = archive.use { directory =>
      val report: Path = directory.resolve("daily.csv")
      val contents: String = "account,total\nnorth,4200\nsouth,1700\n"
      val app: HttpApp[IO] = Http4sServerInterpreter[IO](options).toRoutes(
        download.serverLogicSuccess[IO] { _ =>
          IO.blocking(Files.readString(report, StandardCharsets.UTF_8))
        }
      ).orNotFound

      for {
        unavailable <- app.run(Request[IO](Method.GET, uri"/exports/daily"))
        failureBody <- unavailable.as[String]
        _ <- IO {
          assertEquals(503, unavailable.status.code)
          assertEquals("Daily export is not ready; please retry", failureBody)
          assertEquals(Some("30"), unavailable.headers.headers
            .find(_.name == CIString("Retry-After")).map(_.value))
          assertFalse(failureBody.contains(directory.toString))
          assertFalse(failureBody.contains("NoSuchFileException"))
        }
        _ <- IO.blocking(Files.writeString(report, contents, StandardCharsets.UTF_8))
        available <- app.run(Request[IO](Method.GET, uri"/exports/daily"))
        exported <- available.as[String]
        _ <- IO {
          assertEquals(200, available.status.code)
          assertEquals(contents, exported)
          assertFalse(available.headers.headers.exists(_.name == CIString("Retry-After")))
        }
      } yield ()
    }

    scenario.timeout(45.seconds).unsafeRunSync()
  }
}
