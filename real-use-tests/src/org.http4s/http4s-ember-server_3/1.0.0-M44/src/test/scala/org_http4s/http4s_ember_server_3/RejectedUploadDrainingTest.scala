/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.IO
import cats.effect.Ref
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
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/** Exercises Ember's request-body completion subsystem when discarding rejected inventory uploads. */
class RejectedUploadDrainingTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private case class UploadResponse(status: Int, body: String)

  private def readResponse(reader: BufferedReader): UploadResponse = {
    val statusLine: String = reader.readLine()
    assertNotNull(statusLine, "The connection must remain usable after rejecting the upload")
    assertTrue(statusLine.startsWith("HTTP/1.1 "), statusLine)
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
    assertNotNull(header, "The response headers must be complete")
    assertTrue(length.isDefined, "Upload receipts must include their content length")
    val body: Array[Char] = new Array[Char](length.get)
    var offset: Int = 0
    while (offset < body.length) {
      val count: Int = reader.read(body, offset, body.length - offset)
      assertTrue(count > 0, "The response body must be complete")
      offset += count
    }
    UploadResponse(statusLine.split(" ", 3)(1).toInt, new String(body))
  }

  private def sendUpload(socket: Socket, token: String, body: String): Unit = {
    val request: String =
      "POST /inventory/import HTTP/1.1\r\n" +
        "Host: localhost\r\n" +
        s"Authorization: Bearer $token\r\n" +
        "Content-Type: text/csv; charset=UTF-8\r\n" +
        s"Content-Length: ${body.getBytes(StandardCharsets.UTF_8).length}\r\n\r\n" + body
    socket.getOutputStream.write(request.getBytes(StandardCharsets.UTF_8))
    socket.getOutputStream.flush()
  }

  @Test
  @Timeout(60)
  def retriesAnAuthorizedImportOnTheConnectionThatRejectedAnUnreadUpload(): Unit = {
    val acceptedCsv: String = "sku,quantity\ntea,7\ncoffee,4\n"
    val program: IO[Unit] = for {
      imported <- Ref.of[IO, String]("No inventory imported")
      routes = HttpRoutes.of[IO] {
        case request @ POST -> Root / "inventory" / "import" =>
          if (!request.headers.get(CIString("Authorization")).map(_.head.value)
              .contains("Bearer inventory-writer")) {
            // Discard rather than import the upload, completing the body so the client can retry.
            request.body.compile.drain.flatMap(_ => Forbidden("Inventory writer credentials required"))
          } else {
            request.as[String].flatMap(csv => imported.set(csv))
              .flatMap(_ => Created("Inventory imported"))
          }
        case GET -> Root / "inventory" / "export" =>
          imported.get.flatMap(csv => Ok(csv))
      }
      _ <- EmberServerBuilder.default[IO]
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
                new InputStreamReader(socket.getInputStream, StandardCharsets.UTF_8)
              )
              val rejectedCsv: String = "sku,quantity\n" + "untrusted,999\n" * 10000
              sendUpload(socket, "expired-token", rejectedCsv)
              assertEquals(UploadResponse(403, "Inventory writer credentials required"),
                readResponse(reader))

              // Send only after the rejection, so this is a retry, not an HTTP pipeline.
              val exportRequest: String = "GET /inventory/export HTTP/1.1\r\nHost: localhost\r\n\r\n"
              socket.getOutputStream.write(exportRequest.getBytes(StandardCharsets.US_ASCII))
              socket.getOutputStream.flush()
              assertEquals(UploadResponse(200, "No inventory imported"), readResponse(reader))

              sendUpload(socket, "inventory-writer", acceptedCsv)
              assertEquals(UploadResponse(201, "Inventory imported"), readResponse(reader))
              val finalRequest: String =
                "GET /inventory/export HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
              socket.getOutputStream.write(finalRequest.getBytes(StandardCharsets.US_ASCII))
              socket.getOutputStream.flush()
              assertEquals(UploadResponse(200, acceptedCsv), readResponse(reader))
              assertEquals(-1, reader.read(), "The final response must close the connection")
            }
          }
        }
    } yield ()

    program.timeout(50.seconds).unsafeRunSync()
  }
}
