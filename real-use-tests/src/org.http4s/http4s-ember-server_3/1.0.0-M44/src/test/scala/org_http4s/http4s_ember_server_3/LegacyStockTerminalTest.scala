/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import scala.concurrent.duration.*

/** Exercises Ember's HTTP/1.0 connection negotiation subsystem for a legacy stock terminal. */
class LegacyStockTerminalTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private case class TerminalDownload(headers: Map[String, String], body: String)

  private def readDownload(reader: BufferedReader): TerminalDownload = {
    val status: String = reader.readLine()
    assertNotNull(status, "The terminal must receive a complete HTTP response")
    assertTrue(status.startsWith("HTTP/1.0 200 "), status)
    var headers: Map[String, String] = Map.empty
    var line: String = reader.readLine()
    while (line != null && line.nonEmpty) {
      val separator: Int = line.indexOf(':')
      assertTrue(separator > 0, s"Malformed response header: $line")
      headers = headers.updated(line.substring(0, separator).toLowerCase(Locale.ROOT),
        line.substring(separator + 1).trim)
      line = reader.readLine()
    }
    assertNotNull(line, "The response headers must be complete")
    assertFalse(headers.contains("transfer-encoding"), "The HTTP/1.0 terminal cannot decode chunks")
    assertEquals("text/csv; charset=UTF-8", headers("content-type"))
    val body: Array[Char] = new Array[Char](headers("content-length").toInt)
    var offset: Int = 0
    while (offset < body.length) {
      val count: Int = reader.read(body, offset, body.length - offset)
      assertTrue(count > 0, "The terminal must receive the entire download")
      offset += count
    }
    TerminalDownload(headers, new String(body))
  }

  @Test
  @Timeout(60)
  def downloadsStockAndPricesWithOptInHttp10Persistence(): Unit = {
    val stock: String = "sku,quantity\ntea,120\ncoffee,45\n"
    val prices: String = "sku,price\ntea,8.00\ncoffee,12.50\n"
    val downloads: Map[String, String] = Map("stock.csv" -> stock, "prices.csv" -> prices)
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case request @ GET -> Root / "terminal" / file if downloads.contains(file) =>
        // Serve a known-length snapshot in the protocol version the legacy terminal speaks.
        Ok(downloads(file)).map(_.withHttpVersion(request.httpVersion).putHeaders(
          Header.Raw(CIString("Content-Type"), "text/csv; charset=UTF-8")
        ))
    }
    val program: IO[Unit] = EmberServerBuilder.default[IO]
      .withHost(Host.fromString("127.0.0.1").get)
      .withPort(Port.fromInt(0).get)
      .withHttpApp(routes.orNotFound)
      .withShutdownTimeout(10.seconds)
      .build.use { server =>
        Resource.make(IO(new Socket()))(socket => IO.blocking(socket.close())).use { socket =>
          IO.blocking {
            socket.connect(new InetSocketAddress("127.0.0.1", server.address.port.value), 15000)
            socket.setSoTimeout(15000)
            val reader: BufferedReader = new BufferedReader(
              new InputStreamReader(socket.getInputStream, StandardCharsets.US_ASCII)
            )
            // HTTP/1.0 does not require Host, and persistence must be requested explicitly.
            val firstRequest: String =
              "GET /terminal/stock.csv HTTP/1.0\r\nConnection: keep-alive\r\n\r\n"
            socket.getOutputStream.write(firstRequest.getBytes(StandardCharsets.US_ASCII))
            socket.getOutputStream.flush()
            val first: TerminalDownload = readDownload(reader)
            assertEquals(stock, first.body)
            assertEquals("keep-alive", first.headers("connection"))

            // Request the next snapshot only after consuming the first, on the same socket.
            val secondRequest: String = "GET /terminal/prices.csv HTTP/1.0\r\n\r\n"
            socket.getOutputStream.write(secondRequest.getBytes(StandardCharsets.US_ASCII))
            socket.getOutputStream.flush()
            val second: TerminalDownload = readDownload(reader)
            assertEquals(prices, second.body)
            assertEquals("close", second.headers("connection"))
            assertEquals(-1, reader.read(),
              "Without opt-in persistence, HTTP/1.0 must close after the complete price download")
          }
        }
      }

    program.timeout(50.seconds).unsafeRunSync()
  }
}
