/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import fs2.Stream
import fs2.text
import org.http4s.Header
import org.http4s.Headers
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import scala.concurrent.duration.*

/** Exercises Ember's HTTP response trailer subsystem for streamed report integrity receipts. */
class ReportIntegrityTrailersTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  @Test
  @Timeout(60)
  def verifiesTheChecksumAndRowCountDeliveredAfterAStreamedReport(): Unit = {
    val rowCount: Int = 128
    val expected: String = "invoice,total\n" + (1 to rowCount).map { id =>
      s"INV-$id,${id * 25}\n"
    }.mkString
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "reports" / "invoices.csv" =>
        for {
          digest <- IO(MessageDigest.getInstance("SHA-256"))
          emitted <- Ref.of[IO, Int](0)
          rows = Stream.emits(1 to rowCount).covary[IO].evalMap { id =>
            emitted.update(_ + 1).as(s"INV-$id,${id * 25}\n")
          }
          body = (Stream.emit("invoice,total\n").covary[IO] ++ rows)
            .through(text.utf8.encode).chunks.evalTap { chunk =>
              IO(digest.update(chunk.toArray))
            }.unchunks
          trailers = emitted.get.flatMap { count =>
            IO(Headers(
              Header.Raw(CIString("X-Report-SHA256"), HexFormat.of().formatHex(digest.digest())),
              Header.Raw(CIString("X-Report-Rows"), count.toString)
            ))
          }
          response <- Ok(body).map(_.putHeaders(
            Header.Raw(CIString("Content-Type"), "text/csv; charset=UTF-8"),
            Header.Raw(CIString("Trailer"), "X-Report-SHA256, X-Report-Rows")
          ).withTrailerHeaders(trailers))
        } yield response
    }
    val program: IO[Unit] = EmberServerBuilder.default[IO]
      .withHost(Host.fromString("127.0.0.1").get)
      .withPort(Port.fromInt(0).get)
      .withHttpApp(routes.orNotFound)
      .withShutdownTimeout(10.seconds)
      .build.use { server =>
        EmberClientBuilder.default[IO]
          .withTimeout(15.seconds)
          .withIdleConnectionTime(15.seconds)
          .build.use { client =>
            val uri: Uri = Uri.unsafeFromString(
              s"http://127.0.0.1:${server.address.port.value}/reports/invoices.csv"
            )
            val request: Request[IO] = Request[IO](Method.GET, uri)
              .putHeaders(Header.Raw(CIString("TE"), "trailers"))
            client.run(request).use { response =>
              for {
                _ <- IO {
                  assertEquals(Status.Ok, response.status)
                  assertTrue(response.isChunked, "The report has no known length before streaming")
                  assertTrue(response.headers.get(CIString("X-Report-SHA256")).isEmpty,
                    "The checksum is a trailer, not an initial response header")
                  assertEquals(Some("X-Report-SHA256, X-Report-Rows"),
                    response.headers.get(CIString("Trailer")).map(_.head.value))
                }
                bytes <- response.body.compile.toVector.map(_.toArray)
                // Trailer values are available only after the complete report has been consumed.
                trailers <- response.trailerHeaders
                _ <- IO {
                  assertEquals(expected, new String(bytes, StandardCharsets.UTF_8))
                  val checksum: String = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
                  )
                  assertEquals(Some(checksum),
                    trailers.get(CIString("X-Report-SHA256")).map(_.head.value))
                  assertEquals(Some(rowCount.toString),
                    trailers.get(CIString("X-Report-Rows")).map(_.head.value))
                }
              } yield ()
            }
          }
      }

    program.timeout(50.seconds).unsafeRunSync()
  }
}
