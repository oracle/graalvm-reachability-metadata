/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{Header, HttpApp, HttpRoutes, MediaType, Method, Request, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.headers.{`Content-Type`, Location}
import org.http4s.implicits.*
import org.http4s.server.middleware.DefaultHead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

import java.nio.charset.StandardCharsets.UTF_8

/** Exercises default HEAD middleware for clients inspecting shipment exports before downloading them. */
class ShipmentExportHeadTest {
  @Test
  @Timeout(60)
  def exposesExportMetadataWithoutSendingCsvAndPreservesExplicitHeadRoutes(): Unit = {
    val csv: String = "order,tracking\nbook-42,parcel-731\npen-17,parcel-732\n"
    val exportUri: Uri = Uri.unsafeFromString("/exports/shipments.csv")
    val latestUri: Uri = Uri.unsafeFromString("/exports/latest")
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "exports" / "shipments.csv" =>
        Ok(csv).map(_.putHeaders(
          `Content-Type`(MediaType.text.csv),
          Header.Raw(ci"ETag", "\"shipments-42\""),
          Header.Raw(ci"Content-Disposition", "attachment; filename=shipments.csv")
        ))
      // The discovery URL has its own cheap HEAD handler instead of generating an export.
      case HEAD -> Root / "exports" / "latest" => TemporaryRedirect(Location(exportUri))
      case GET -> Root / "exports" / "latest" => TemporaryRedirect(Location(exportUri))
      case GET -> Root / "exports" / _ => NotFound("Export not published")
    }
    val application: HttpApp[IO] = DefaultHead.httpRoutes(routes).orNotFound

    val checks: IO[Unit] = for {
      metadata <- application.run(Request[IO](method = Method.HEAD, uri = exportUri))
      metadataBody <- metadata.body.compile.toVector
      download <- application.run(Request[IO](uri = exportUri))
      downloadedCsv <- download.as[String]
      discovery <- application.run(Request[IO](method = Method.HEAD, uri = latestUri))
      discoveryBody <- discovery.body.compile.toVector
      missing <- application.run(Request[IO](
        method = Method.HEAD, uri = Uri.unsafeFromString("/exports/unpublished.csv")
      ))
      missingBody <- missing.body.compile.toVector
      missingDownload <- application.run(Request[IO](uri = Uri.unsafeFromString("/exports/unpublished.csv")))
      missingMessage <- missingDownload.as[String]
    } yield {
      assertEquals(Status.Ok, metadata.status)
      assertEquals(Vector.empty[Byte], metadataBody)
      assertEquals(Some(csv.getBytes(UTF_8).length.toLong), metadata.contentLength)
      assertEquals(Some(MediaType.text.csv), metadata.contentType.map(_.mediaType))
      assertEquals(Some("\"shipments-42\""), metadata.headers.get(ci"ETag").map(_.head.value))
      assertEquals(
        Some("attachment; filename=shipments.csv"),
        metadata.headers.get(ci"Content-Disposition").map(_.head.value)
      )
      assertEquals(Status.Ok, download.status)
      assertEquals(csv, downloadedCsv)
      assertEquals(download.headers, metadata.headers)
      assertEquals(Status.TemporaryRedirect, discovery.status)
      assertEquals(Some(Location(exportUri)), discovery.headers.get[Location])
      assertEquals(Vector.empty[Byte], discoveryBody)
      assertEquals(Status.NotFound, missing.status)
      assertEquals(Vector.empty[Byte], missingBody)
      assertEquals(Status.NotFound, missingDownload.status)
      assertEquals("Export not published", missingMessage)
      assertEquals(missingDownload.contentLength, missing.contentLength)
    }

    checks.unsafeRunSync()
  }
}
