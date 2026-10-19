/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import org.eclipse.jetty.http.HttpTester
import org.eclipse.jetty.server.{LocalConnector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.junit.jupiter.api.Assertions.{assertEquals, assertNull}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.{ContentEncodingSupport, ScalatraServlet}

/** Exercises HTTP request decompression and response compression for a telemetry batch endpoint. */
class CompressedTelemetryTest {
  @Test
  @Timeout(60)
  def acceptsCompressedReadingsAndReturnsTheNegotiatedReceipt(): Unit = {
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    context.addServlet(new ServletHolder(new TelemetryServlet()), "/*")
    server.setHandler(context)

    try {
      server.start()

      val compressed: HttpTester.Response = submit(connector, "12\n18\n15\n", true)
      assertEquals(200, compressed.getStatus)
      assertEquals("gzip", compressed.get("Content-Encoding"))
      val decoded: GZIPInputStream = new GZIPInputStream(new ByteArrayInputStream(compressed.getContentBytes))
      try {
        assertEquals("accepted=3; total=45", new String(decoded.readAllBytes(), UTF_8))
      } finally decoded.close()

      val plain: HttpTester.Response = submit(connector, "7\n9\n", false)
      assertEquals(200, plain.getStatus)
      assertNull(plain.get("Content-Encoding"))
      assertEquals("accepted=2; total=16", plain.getContent)
    } finally {
      try server.stop()
      finally server.destroy()
    }
  }

  private def submit(connector: LocalConnector, readings: String, compressed: Boolean): HttpTester.Response = {
    val request: HttpTester.Request = HttpTester.newRequest()
    request.setMethod("POST")
    request.setURI("/telemetry/batches")
    request.setVersion("HTTP/1.1")
    request.put("Host", "localhost")
    request.put("Connection", "close")
    request.put("Content-Type", "text/plain; charset=UTF-8")
    val payload: Array[Byte] = readings.getBytes(UTF_8)
    if (compressed) {
      val buffer: ByteArrayOutputStream = new ByteArrayOutputStream()
      val gzip: GZIPOutputStream = new GZIPOutputStream(buffer)
      try gzip.write(payload)
      finally gzip.close()
      request.put("Content-Encoding", "gzip")
      request.put("Accept-Encoding", "gzip")
      request.setContent(buffer.toByteArray)
    } else {
      request.setContent(payload)
    }
    HttpTester.parseResponse(connector.getResponse(request.generate(), 10, TimeUnit.SECONDS))
  }

  private class TelemetryServlet extends ScalatraServlet with ContentEncodingSupport {
    post("/telemetry/batches") {
      contentType = "text/plain"
      val readings: Vector[Int] = request.body.linesIterator.filter(_.nonEmpty).map(_.toInt).toVector
      s"accepted=${readings.size}; total=${readings.sum}"
    }
  }
}
