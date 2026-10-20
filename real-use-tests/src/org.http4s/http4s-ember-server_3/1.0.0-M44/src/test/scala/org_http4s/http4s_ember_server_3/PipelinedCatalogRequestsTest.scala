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
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/** Exercises Ember's HTTP/1.1 persistent-connection pipelining subsystem for catalog lookups. */
class PipelinedCatalogRequestsTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private case class CatalogResponse(status: Int, body: String)

  private def readResponse(reader: BufferedReader): CatalogResponse = {
    val statusLine: String = reader.readLine()
    assertNotNull(statusLine, "Every pipelined request must receive a response")
    assertTrue(statusLine.startsWith("HTTP/1.1 "))
    val status: Int = statusLine.split(" ", 3)(1).toInt
    var length: Option[Int] = None
    var header: String = reader.readLine()
    while (header != null && header.nonEmpty) {
      val separator: Int = header.indexOf(':')
      assertTrue(separator > 0, s"Malformed response header: $header")
      if (header.substring(0, separator).equalsIgnoreCase("Content-Length")) {
        length = Some(header.substring(separator + 1).trim.toInt)
      }
      header = reader.readLine()
    }
    assertNotNull(header, "Response headers must be complete")
    assertTrue(length.isDefined, "Catalog responses must have a bounded content length")
    val body: Array[Char] = new Array[Char](length.get)
    var offset: Int = 0
    while (offset < body.length) {
      val count: Int = reader.read(body, offset, body.length - offset)
      assertTrue(count > 0, "Response body must not be truncated")
      offset += count
    }
    CatalogResponse(status, new String(body))
  }

  @Test
  @Timeout(60)
  def retrievesCatalogEntriesInOrderOnOneConnection(): Unit = {
    val catalog: Map[String, String] = Map("tea" -> "Green tea: 12 boxes", "coffee" -> "Coffee: 7 bags")
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "catalog" / sku =>
        catalog.get(sku) match {
          case Some(description) => Ok(description)
          case None => NotFound(s"Unknown product: $sku")
        }
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
            val requests: String = List("tea", "missing", "coffee").zipWithIndex.map {
              case (sku, index) =>
                val connection: String = if (index == 2) "close" else "keep-alive"
                s"GET /catalog/$sku HTTP/1.1\r\nHost: localhost\r\nConnection: $connection\r\n\r\n"
            }.mkString
            // Send the complete pipeline before reading any response.
            socket.getOutputStream.write(requests.getBytes(StandardCharsets.US_ASCII))
            socket.getOutputStream.flush()
            val reader: BufferedReader = new BufferedReader(
              new InputStreamReader(socket.getInputStream, StandardCharsets.US_ASCII)
            )
            assertEquals(CatalogResponse(200, "Green tea: 12 boxes"), readResponse(reader))
            assertEquals(CatalogResponse(404, "Unknown product: missing"), readResponse(reader))
            assertEquals(CatalogResponse(200, "Coffee: 7 bags"), readResponse(reader))
            assertEquals(-1, reader.read(), "The final Connection: close must terminate the connection")
          }
        }
      }

    program.timeout(50.seconds).unsafeRunSync()
  }
}
