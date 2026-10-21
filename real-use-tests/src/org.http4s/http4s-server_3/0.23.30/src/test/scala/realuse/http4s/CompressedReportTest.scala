/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import org.http4s.{Header, HttpApp, HttpRoutes, Request, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.middleware.GZip
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.GZIPInputStream

/** Exercises GZip response middleware for clients downloading a text sales report. */
class CompressedReportTest {
  @Test
  @Timeout(60)
  def negotiatesCompressionAndPreservesTheDownloadedReport(): Unit = {
    val report: String = "sku,quantity,total\n" + "book-42,3,59.97\n".repeat(200)
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "reports" / "sales" =>
        Ok(report).map(_.putHeaders(Header.Raw(ci"X-Report-Id", "sales-42")))
    }
    val application: HttpApp[IO] = GZip(routes.orNotFound)
    val request: Request[IO] = Request[IO](uri = Uri.unsafeFromString("/reports/sales"))

    def decompress(bytes: Array[Byte]): IO[String] =
      Resource.fromAutoCloseable(IO.blocking(new GZIPInputStream(new ByteArrayInputStream(bytes))))
        .use(stream => IO.blocking(new String(stream.readAllBytes(), UTF_8)))

    val checks: IO[Unit] = for {
      compressed <- application.run(request.putHeaders(Header.Raw(ci"Accept-Encoding", "gzip")))
      compressedBytes <- compressed.body.compile.toVector.map(_.toArray)
      downloadedReport <- decompress(compressedBytes)
      plain <- application.run(request)
      plainReport <- plain.as[String]
      declined <- application.run(
        request.putHeaders(Header.Raw(ci"Accept-Encoding", "gzip;q=0, identity;q=1"))
      )
      declinedReport <- declined.as[String]
    } yield {
      assertEquals(Status.Ok, compressed.status)
      assertEquals(Some("gzip"), compressed.headers.get(ci"Content-Encoding").map(_.head.value))
      assertEquals(None, compressed.contentLength)
      assertEquals(Some("sales-42"), compressed.headers.get(ci"X-Report-Id").map(_.head.value))
      assertEquals(report, downloadedReport)
      assertTrue(compressedBytes.length < report.getBytes(UTF_8).length)
      assertEquals(Status.Ok, plain.status)
      assertEquals(None, plain.headers.get(ci"Content-Encoding"))
      assertEquals(report, plainReport)
      assertEquals(plain.contentType, compressed.contentType)
      assertEquals(Status.Ok, declined.status)
      assertEquals(None, declined.headers.get(ci"Content-Encoding"))
      assertEquals(report, declinedReport)
    }

    checks.unsafeRunSync()
  }
}
