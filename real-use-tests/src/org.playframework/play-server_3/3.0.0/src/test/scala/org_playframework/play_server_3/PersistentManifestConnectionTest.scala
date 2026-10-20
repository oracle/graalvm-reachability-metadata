/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.Mode
import play.api.mvc.Results.NoContent
import play.api.mvc.Results.Ok
import play.core.server.Server
import play.core.server.ServerConfig

/** Exercises HTTP/1.1 persistent-connection pipelining and bodyless response framing. */
class PersistentManifestConnectionTest {
  @Test
  @Timeout(60)
  def checksManifestMetadataAndDownloadsItOnTheSameConnection(): Unit = {
    val manifest: String = "shipment,item,units\nparcel-42,notebook,12\n"
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    Server.withRouterFromComponents(config) { components =>
      {
        case request if (request.method == "HEAD" || request.method == "GET") &&
            request.path == "/manifest" =>
          components.defaultActionBuilder { _ =>
            Ok(manifest).as("text/csv").withHeaders("ETag" -> "\"manifest-42\"")
          }
        case request if request.method == "GET" && request.path == "/health" =>
          components.defaultActionBuilder { _ => NoContent }
      }
    } { port =>
      val socket: Socket = new Socket()
      try {
        socket.connect(new InetSocketAddress("127.0.0.1", port.value), 10000)
        socket.setSoTimeout(10000)
        val input: BufferedInputStream = new BufferedInputStream(socket.getInputStream)
        val host: String = s"Host: 127.0.0.1:${port.value}\r\n"
        // Send all requests before reading: response boundaries must survive pipelining.
        val requests: String =
          s"HEAD /manifest HTTP/1.1\r\n$host\r\n" +
          s"GET /health HTTP/1.1\r\n$host\r\n" +
          s"GET /manifest HTTP/1.1\r\n${host}Connection: close\r\n\r\n"
        socket.getOutputStream.write(requests.getBytes(StandardCharsets.US_ASCII))
        socket.getOutputStream.flush()

        val metadata: ResponseHead = readHead(input)
        assertEquals(200, metadata.status)
        assertEquals(manifest.getBytes(StandardCharsets.UTF_8).length.toString,
          metadata.headers("content-length"))
        assertEquals("\"manifest-42\"", metadata.headers("etag"))

        // HEAD advertises the GET length but sends no body. The very next bytes are a status line.
        val health: ResponseHead = readHead(input)
        assertEquals(204, health.status)

        // A 204 response also has no body, so it must not consume the following download.
        val download: ResponseHead = readHead(input)
        assertEquals(200, download.status)
        assertEquals(metadata.headers("etag"), download.headers("etag"))
        assertEquals("text/csv", download.headers("content-type"))
        val length: Int = download.headers("content-length").toInt
        assertEquals(manifest.getBytes(StandardCharsets.UTF_8).length, length)
        assertEquals(manifest, new String(input.readNBytes(length), StandardCharsets.UTF_8))
        assertEquals(-1, input.read(), "Connection: close must terminate after the final response")
      } finally {
        socket.close()
      }
    }
  }

  private case class ResponseHead(status: Int, headers: Map[String, String])

  private def readHead(input: InputStream): ResponseHead = {
    val statusLine: String = readLine(input)
    assertTrue(statusLine.startsWith("HTTP/1.1 "), s"Expected a response boundary, got: $statusLine")
    val headers: Map[String, String] = Iterator.continually(readLine(input))
      .takeWhile(_.nonEmpty)
      .map { line =>
        val separator: Int = line.indexOf(':')
        assertTrue(separator > 0, s"Invalid HTTP header: $line")
        line.substring(0, separator).toLowerCase(Locale.ROOT) -> line.substring(separator + 1).trim
      }
      .toMap
    ResponseHead(statusLine.split(" ")(1).toInt, headers)
  }

  private def readLine(input: InputStream): String = {
    val line: StringBuilder = new StringBuilder()
    var next: Int = input.read()
    while (next != '\n') {
      assertTrue(next >= 0, "Connection closed before a complete response header")
      assertTrue(line.length < 8192, "HTTP header line exceeds the client limit")
      line.append(next.toChar)
      next = input.read()
    }
    assertTrue(line.endsWith("\r"), "HTTP header lines must end in CRLF")
    line.toString().stripSuffix("\r")
  }
}
