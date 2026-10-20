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
import fs2.Chunk
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.io.net.unixsocket.UnixSockets
import fs2.text
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path as FilePath
import scala.concurrent.duration.*

/** Exercises Ember's Unix-domain-socket transport subsystem for a local administration API. */
class UnixSocketAdministrationTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private def awaitSocket(address: UnixSocketAddress): IO[Unit] =
    UnixSockets[IO].client(address).use(_ => IO.unit).handleErrorWith {
      case _: SocketException => IO.cede.flatMap(_ => awaitSocket(address))
      case error => IO.raiseError(error)
    }

  private def callAdmin(address: UnixSocketAddress, method: String, path: String,
      expectedStatus: Int, expectedBody: String): IO[Unit] =
    UnixSockets[IO].client(address).use { socket =>
      val request: String = s"$method $path HTTP/1.1\r\n" +
        "Host: localhost\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
      for {
        _ <- socket.write(Chunk.array(request.getBytes(StandardCharsets.US_ASCII)))
        response <- socket.reads.through(text.utf8.decode).compile.string
        _ <- IO {
          val boundary: Int = response.indexOf("\r\n\r\n")
          assertTrue(boundary > 0, "The local API must return complete HTTP headers")
          assertTrue(response.startsWith(s"HTTP/1.1 $expectedStatus "), response)
          assertEquals(expectedBody, response.substring(boundary + 4))
        }
      } yield ()
    }.timeout(15.seconds)

  @Test
  @Timeout(60)
  def switchesMaintenanceModeThroughALocalSocketAndRemovesTheSocketOnShutdown(): Unit = {
    val program: IO[Unit] = Resource.make(IO.blocking {
      Files.createTempDirectory("ember-admin-")
    })(directory => IO.blocking {
      Files.deleteIfExists(directory.resolve("admin.sock"))
      Files.delete(directory)
    }).use { directory =>
      val socketPath: FilePath = directory.resolve("admin.sock")
      val address: UnixSocketAddress = UnixSocketAddress(socketPath)
      for {
        maintenance <- Ref.of[IO, Boolean](false)
        routes = HttpRoutes.of[IO] {
          case GET -> Root / "admin" / "status" =>
            maintenance.get.flatMap { enabled =>
              Ok(if (enabled) "maintenance" else "accepting-orders")
            }
          case POST -> Root / "admin" / "maintenance" / "enable" =>
            maintenance.set(true).flatMap(_ => Accepted("Maintenance enabled"))
        }
        _ <- EmberServerBuilder.default[IO]
          .withUnixSocketConfig(UnixSockets[IO], address,
            deleteIfExists = false, deleteOnClose = true)
          .withHttpApp(routes.orNotFound)
          .withShutdownTimeout(10.seconds)
          .build.use { _ =>
            for {
              // The Unix listener binds asynchronously; poll readiness, not elapsed time.
              _ <- awaitSocket(address).timeout(15.seconds)
              _ <- IO.blocking(assertTrue(Files.exists(socketPath)))
              _ <- callAdmin(address, "GET", "/admin/status", 200, "accepting-orders")
              _ <- callAdmin(address, "POST", "/admin/maintenance/enable", 202,
                "Maintenance enabled")
              // A fresh client connection observes the command's application-level effect.
              _ <- callAdmin(address, "GET", "/admin/status", 200, "maintenance")
            } yield ()
          }
        _ <- IO.blocking(assertFalse(Files.exists(socketPath),
          "Releasing the admin server must remove its Unix socket"))
      } yield ()
    }

    program.timeout(50.seconds).unsafeRunSync()
  }
}
