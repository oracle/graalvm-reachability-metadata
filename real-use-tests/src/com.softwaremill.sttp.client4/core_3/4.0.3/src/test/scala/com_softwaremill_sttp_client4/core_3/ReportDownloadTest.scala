/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode

/** Exercises file response handling by downloading a binary report while keeping HTTP errors out of local files. */
class ReportDownloadTest {
  @Test
  @Timeout(60)
  def savesReportAndReturnsMissingReportError(): Unit = {
    val directory: Path = Files.createTempDirectory("sttp-reports-")
    val reportPath: Path = directory.resolve("monthly.bin")
    val missingPath: Path = directory.resolve("missing.bin")
    val report: Array[Byte] = Array.tabulate[Byte](16384)(index => (index % 256).toByte)
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/exports/monthly", (exchange: HttpExchange) => {
        try {
          exchange.getResponseHeaders.set("Content-Type", "application/octet-stream")
          exchange.getResponseHeaders.set("ETag", "\"monthly-17\"")
          exchange.sendResponseHeaders(200, report.length.toLong)
          exchange.getResponseBody.write(report)
        } finally exchange.close()
      })
      server.createContext("/exports/missing", (exchange: HttpExchange) => {
        try {
          val error: Array[Byte] = "No export exists for this period".getBytes(UTF_8)
          exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
          exchange.sendResponseHeaders(404, error.length.toLong)
          exchange.getResponseBody.write(error)
        } finally exchange.close()
      })
      server.start()

      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val downloaded: Response[Either[String, Path]] = basicRequest
          .get(uri"http://127.0.0.1:$port/exports/monthly")
          .readTimeout(20.seconds)
          .response(asPath(reportPath))
          .send(backend)

        assertEquals(StatusCode.Ok, downloaded.code)
        assertEquals(Right(reportPath), downloaded.body)
        assertEquals(Some("\"monthly-17\""), downloaded.header("ETag"))
        assertEquals(report.length.toLong, Files.size(reportPath))
        assertArrayEquals(report, Files.readAllBytes(reportPath))

        val missing: Response[Either[String, Path]] = basicRequest
          .get(uri"http://127.0.0.1:$port/exports/missing")
          .readTimeout(20.seconds)
          .response(asPath(missingPath))
          .send(backend)

        assertEquals(StatusCode.NotFound, missing.code)
        assertEquals(Left("No export exists for this period"), missing.body)
        assertFalse(Files.exists(missingPath), "An HTTP error must not be saved as a report")
        assertArrayEquals(report, Files.readAllBytes(reportPath))
      } finally backend.close()
    } finally {
      server.stop(0)
      Files.deleteIfExists(reportPath)
      Files.deleteIfExists(missingPath)
      Files.deleteIfExists(directory)
    }
  }
}
