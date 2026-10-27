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
import java.nio.file.StandardOpenOption.APPEND
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.logging.LogConfig
import sttp.client4.logging.Logger
import sttp.client4.logging.LoggingBackend
import sttp.client4.logging.LogLevel
import sttp.model.HeaderNames
import sttp.model.StatusCode
import sttp.shared.Identity

/** Exercises backend request/response logging with credential redaction for a parcel-tracking application's audit log. */
class ParcelAuditLoggingTest {
  @Test
  @Timeout(60)
  def recordsTrackingResultsWithoutLeakingCredentials(): Unit = {
    val auditFile: Path = Files.createTempFile("sttp-parcel-audit-", ".log")
    val accessToken: String = "private-tracking-token-A17"
    val apiKey: String = "private-customer-key-K42"
    val sessionCookie: String = "private-tracking-session-S91"
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/parcels/", (exchange: HttpExchange) => {
        try {
          val authorized: Boolean =
            exchange.getRequestHeaders.getFirst("Authorization") == s"Bearer $accessToken" &&
              exchange.getRequestHeaders.getFirst("X-Api-Key") == apiKey &&
              exchange.getRequestURI.getQuery == s"access_token=$accessToken"
          val (status, text): (Int, String) =
            if (!authorized) (401, "Tracking credentials required")
            else if (exchange.getRequestURI.getPath == "/parcels/P17") (200, "Parcel P17 is ready for collection")
            else (404, "No parcel found for P99")
          val bytes: Array[Byte] = text.getBytes(UTF_8)
          exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
          exchange.getResponseHeaders.set("Set-Cookie", s"tracking=$sessionCookie; HttpOnly; Path=/")
          exchange.getResponseHeaders.set("X-Tracking-Region", "west")
          exchange.sendResponseHeaders(status, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
        } finally exchange.close()
      })
      server.start()

      val config: LogConfig = LogConfig(
        logResponseBody = true,
        includeTimings = false,
        sensitiveHeaders = HeaderNames.SensitiveHeaders + "X-Api-Key",
        sensitiveQueryParams = Set("access_token")
      )
      val backend: WebSocketSyncBackend = LoggingBackend(
        HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds)),
        new AuditFileLogger(auditFile),
        config
      )
      try {
        val port: Int = server.getAddress.getPort
        val trackingRequest = basicRequest
          .auth.bearer(accessToken)
          .header("X-Api-Key", apiKey)
          .readTimeout(20.seconds)

        val tracked: Response[Either[String, String]] = trackingRequest
          .get(uri"http://127.0.0.1:$port/parcels/P17?access_token=$accessToken")
          .send(backend)
        val missing: Response[Either[String, String]] = trackingRequest
          .get(uri"http://127.0.0.1:$port/parcels/P99?access_token=$accessToken")
          .send(backend)

        assertEquals(StatusCode.Ok, tracked.code)
        assertEquals(Right("Parcel P17 is ready for collection"), tracked.body)
        assertEquals(StatusCode.NotFound, missing.code)
        assertEquals(Left("No parcel found for P99"), missing.body)
        assertEquals(Some(s"tracking=$sessionCookie; HttpOnly; Path=/"), tracked.header("Set-Cookie"))

        val entries: List[String] = Files.readAllLines(auditFile, UTF_8).asScala.toList
        assertEquals(4, entries.size, "Each request must produce a send event and a response event")
        assertTrue(entries.head.contains("Sending request:"))
        assertTrue(entries.head.contains("http.request.method=GET"))
        assertTrue(entries(1).startsWith("[Debug]"))
        assertTrue(entries(1).contains("Parcel P17 is ready for collection"))
        assertTrue(entries(1).contains("http.response.status_code=200"))
        assertTrue(entries(1).contains("west"))
        assertTrue(entries(3).startsWith("[Error]"))
        assertTrue(entries(3).contains("No parcel found for P99"))
        assertTrue(entries(3).contains("http.response.status_code=404"))

        val audit: String = entries.mkString("\n")
        assertTrue(audit.contains("/parcels/P17"))
        assertTrue(audit.contains("/parcels/P99"))
        List(accessToken, apiKey, sessionCookie).foreach { secret =>
          assertFalse(audit.contains(secret), "Credentials must be absent from messages and structured context")
        }
      } finally backend.close()
    } finally {
      server.stop(0)
      Files.deleteIfExists(auditFile)
    }
  }

  private class AuditFileLogger(file: Path) extends Logger[Identity] {
    override def apply(
        level: LogLevel,
        message: => String,
        exception: Option[Throwable],
        context: Map[String, Any]
    ): Unit = {
      val fields: String = context.toList.sortBy(_._1).map { case (key, value) => s"$key=$value" }.mkString(" ")
      val failure: String = exception.fold("")(error => s" exception=$error")
      Files.writeString(file, s"[$level] $message | $fields$failure\n", UTF_8, APPEND)
      ()
    }
  }
}
