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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/** Exercises Ember's connection metadata subsystem for direct-peer audit receipts. */
class DirectConnectionAuditTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  @Test
  @Timeout(60)
  def identifiesTheActualPeerRatherThanUntrustedForwardingHeaders(): Unit = {
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case request @ POST -> Root / "audit" / "events" =>
        request.as[String].flatMap { event =>
          // This service is directly exposed, so audit the transport peer, not proxy headers.
          val peer: String = request.remote.map(_.toString).getOrElse("unknown")
          val listener: String = request.server.map(_.toString).getOrElse("unknown")
          val secure: String = request.isSecure.map(_.toString).getOrElse("unknown")
          Created(s"event=$event\npeer=$peer\nlistener=$listener\nsecure=$secure\n")
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
            val serverPort: Int = server.address.port.value
            socket.connect(new InetSocketAddress("127.0.0.1", serverPort), 15000)
            socket.setSoTimeout(15000)
            val event: String = "operator-17 downloaded invoice INV-42"
            val request: String =
              "POST /audit/events HTTP/1.1\r\n" +
                "Host: audit.example:8443\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                s"Content-Length: ${event.length}\r\n" +
                "Forwarded: for=203.0.113.17;proto=https;host=audit.example\r\n" +
                "X-Forwarded-For: 203.0.113.17\r\n" +
                "X-Forwarded-Proto: https\r\n" +
                "Connection: close\r\n\r\n" + event
            socket.getOutputStream.write(request.getBytes(StandardCharsets.US_ASCII))
            socket.getOutputStream.flush()

            val response: String = new String(
              socket.getInputStream.readAllBytes(), StandardCharsets.UTF_8
            )
            val boundary: Int = response.indexOf("\r\n\r\n")
            assertTrue(boundary > 0, "The audit service must return complete HTTP headers")
            val headers: String = response.substring(0, boundary)
            assertTrue(headers.startsWith("HTTP/1.1 201 "), headers)
            val expected: String =
              s"event=$event\npeer=127.0.0.1:${socket.getLocalPort}\n" +
                s"listener=127.0.0.1:$serverPort\nsecure=false\n"
            assertEquals(expected, response.substring(boundary + 4),
              "The audit receipt must describe the socket, not client-controlled proxy headers")
          }
        }
      }

    program.timeout(50.seconds).unsafeRunSync()
  }
}
