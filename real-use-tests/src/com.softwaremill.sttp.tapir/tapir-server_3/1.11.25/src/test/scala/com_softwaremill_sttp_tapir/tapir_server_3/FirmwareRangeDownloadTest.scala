/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_server_3

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Duration

import org.junit.jupiter.api.Assertions.{assertArrayEquals, assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.StatusCode
import sttp.model.headers.Range
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.jdkhttp.JdkHttpServer

/** Exercises file-range response-body encoding for resumable firmware downloads. */
class FirmwareRangeDownloadTest {
  @Test
  @Timeout(60)
  def downloadsAndResumesABinaryReleaseWithoutSendingBytesOutsideTheRequestedRange(): Unit = {
    val firmware: Array[Byte] = Array.tabulate[Byte](1024)(index => (index % 256).toByte)
    val release: Path = Files.createTempFile("firmware-release-", ".bin")
    try {
      Files.write(release, firmware)
      val download: ServerEndpoint[Any, Identity] = endpoint.get
        .in("releases" / "firmware.bin")
        .in(header[Option[Range]]("Range"))
        .errorOut(statusCode(StatusCode.RangeNotSatisfiable).and(stringBody))
        .out(statusCode.and(header[Option[String]]("Content-Range")).and(fileRangeBody))
        .out(header("Accept-Ranges", "bytes"))
        .out(header("Content-Disposition", "attachment; filename=firmware.bin"))
        .serverLogic[Identity] {
          case None => Right((StatusCode.Ok, None, FileRange(release.toFile)))
          case Some(requested) if requested.isValid(firmware.length.toLong) =>
            val range: RangeValue = RangeValue(requested.start, requested.end, firmware.length.toLong)
            val (start, end) = range.startAndEnd.get
            Right((StatusCode.PartialContent, Some(s"bytes $start-${end - 1}/${firmware.length}"),
              FileRange(release.toFile, Some(range))))
          case Some(_) => Left("Requested bytes are outside this release")
        }

      val server = JdkHttpServer().host("127.0.0.1").port(0).addEndpoint(download).start()
      try {
        val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        try {
          val uri: URI = URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/releases/firmware.bin")

          def fetch(range: Option[String] = None): HttpResponse[Array[Byte]] = {
            val request: HttpRequest.Builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET()
            range.foreach(value => request.header("Range", value))
            client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
          }

          val complete: HttpResponse[Array[Byte]] = fetch()
          assertEquals(200, complete.statusCode())
          assertEquals("1024", complete.headers().firstValue("Content-Length").orElseThrow())
          assertEquals("application/octet-stream", complete.headers().firstValue("Content-Type").orElseThrow())
          assertEquals("bytes", complete.headers().firstValue("Accept-Ranges").orElseThrow())
          assertEquals("attachment; filename=firmware.bin",
            complete.headers().firstValue("Content-Disposition").orElseThrow())
          assertFalse(complete.headers().firstValue("Content-Range").isPresent)
          assertArrayEquals(firmware, complete.body())

          val firstChunk: HttpResponse[Array[Byte]] = fetch(Some("bytes=0-299"))
          assertEquals(206, firstChunk.statusCode())
          assertEquals("bytes 0-299/1024", firstChunk.headers().firstValue("Content-Range").orElseThrow())
          assertEquals("300", firstChunk.headers().firstValue("Content-Length").orElseThrow())
          assertArrayEquals(firmware.take(300), firstChunk.body())

          val resumed: HttpResponse[Array[Byte]] = fetch(Some("bytes=300-"))
          assertEquals(206, resumed.statusCode())
          assertEquals("bytes 300-1023/1024", resumed.headers().firstValue("Content-Range").orElseThrow())
          assertEquals("724", resumed.headers().firstValue("Content-Length").orElseThrow())
          assertArrayEquals(firmware, firstChunk.body() ++ resumed.body())

          val suffix: HttpResponse[Array[Byte]] = fetch(Some("bytes=-17"))
          assertEquals(206, suffix.statusCode())
          assertEquals("bytes 1007-1023/1024", suffix.headers().firstValue("Content-Range").orElseThrow())
          assertEquals("17", suffix.headers().firstValue("Content-Length").orElseThrow())
          assertArrayEquals(firmware.takeRight(17), suffix.body())

          val unavailable: HttpResponse[Array[Byte]] = fetch(Some("bytes=1024-"))
          assertEquals(416, unavailable.statusCode())
          assertArrayEquals("Requested bytes are outside this release".getBytes(StandardCharsets.UTF_8),
            unavailable.body())
          assertFalse(unavailable.headers().firstValue("Content-Disposition").isPresent)

          val retry: HttpResponse[Array[Byte]] = fetch(Some("bytes=513-513"))
          assertEquals(206, retry.statusCode())
          assertEquals("bytes 513-513/1024", retry.headers().firstValue("Content-Range").orElseThrow())
          assertArrayEquals(firmware.slice(513, 514), retry.body())
        } finally client.close()
      } finally server.stop(0)
    } finally Files.deleteIfExists(release)
  }
}
