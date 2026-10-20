/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import org.http4s.{Header, HttpApp, MediaType, Request, Status, Uri}
import org.http4s.headers.ETag
import org.http4s.implicits.*
import org.http4s.server.staticcontent.{FileService, fileService}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files as NioFiles, Path}

/** Exercises static file serving for resumable and cache-validated document downloads. */
class DocumentDownloadTest {
  @Test
  @Timeout(60)
  def downloadsPublishedDocumentsWithRangesAndCacheValidation(): Unit = {
    val document: String = "Order book-42\nShipment: dispatched\nTracking: parcel-731\nThank you for your order.\n"
    val directory: Resource[IO, Path] = Resource.make(
      IO.blocking(NioFiles.createTempDirectory("http4s-documents-"))
    )(root => IO.blocking {
      NioFiles.deleteIfExists(root.resolve("shipment.txt"))
      NioFiles.deleteIfExists(root)
      ()
    })
    val request: Request[IO] = Request[IO](uri = Uri.unsafeFromString("/downloads/shipment.txt"))

    val checks: IO[Unit] = directory.use { root =>
      for {
        _ <- IO.blocking(NioFiles.writeString(root.resolve("shipment.txt"), document, UTF_8))
        application: HttpApp[IO] = fileService[IO](FileService.Config[IO](
          systemPath = root.toString,
          pathPrefix = "/downloads"
        )).orNotFound
        download <- application.run(request)
        downloadedText <- download.as[String]
        etag <- IO.fromOption(download.headers.get[ETag])(
          new IllegalStateException("Published documents must have a cache validator")
        )
        resumed <- application.run(request.putHeaders(Header.Raw(ci"Range", "bytes=20-")))
        resumedText <- resumed.as[String]
        cached <- application.run(request.putHeaders(Header.Raw(ci"If-None-Match", etag.value)))
        cachedBody <- cached.as[String]
        stale <- application.run(request.putHeaders(Header.Raw(ci"If-None-Match", "\"old-version\"")))
        staleBody <- stale.as[String]
        beyondEnd <- application.run(request.putHeaders(Header.Raw(ci"Range", "bytes=1000-")))
        missing <- application.run(Request[IO](uri = Uri.unsafeFromString("/downloads/missing.txt")))
      } yield {
        assertEquals(Status.Ok, download.status)
        assertEquals(document, downloadedText)
        assertEquals(Some(document.getBytes(UTF_8).length.toLong), download.contentLength)
        assertEquals(Some(MediaType.text.plain), download.contentType.map(_.mediaType))
        assertEquals(Some("bytes"), download.headers.get(ci"Accept-Ranges").map(_.head.value))
        assertEquals(Status.PartialContent, resumed.status)
        assertEquals(document.substring(20), resumedText)
        assertEquals(Some((document.length - 20).toLong), resumed.contentLength)
        assertEquals(
          Some(s"bytes 20-${document.length - 1}/${document.length}"),
          resumed.headers.get(ci"Content-Range").map(_.head.value)
        )
        assertEquals(Status.NotModified, cached.status)
        assertEquals("", cachedBody)
        assertEquals(Status.Ok, stale.status)
        assertEquals(document, staleBody)
        assertEquals(download.headers.get[ETag], stale.headers.get[ETag])
        assertEquals(Status.RangeNotSatisfiable, beyondEnd.status)
        assertEquals(Status.NotFound, missing.status)
      }
    }

    checks.unsafeRunSync()
  }
}
