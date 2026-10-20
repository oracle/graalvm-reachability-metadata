/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.*

/** Exercises Ember's request-header size enforcement subsystem for an authenticated account API. */
class OversizedAuthenticationHeaderTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  @Test
  @Timeout(60)
  def rejectsAnOversizedTokenBeforeDispatchAndStillServesNormalRequests(): Unit = {
    val program: IO[Unit] = for {
      dispatched <- Ref.of[IO, Int](0)
      routes = HttpRoutes.of[IO] {
        case request @ GET -> Root / "account" =>
          dispatched.update(_ + 1).flatMap { _ =>
            if (request.headers.get(CIString("Authorization")).map(_.head.value)
                .contains("Bearer account-reader")) Ok("Account: tea-shop")
            else Forbidden("Invalid account token")
          }
      }
      _ <- EmberServerBuilder.default[IO]
        .withHost(Host.fromString("127.0.0.1").get)
        .withPort(Port.fromInt(0).get)
        .withHttpApp(routes.orNotFound)
        .withMaxHeaderSize(1024)
        .withMaxHeaderSizeErrorHandler { _ =>
          IO.pure(Response[IO](Status.RequestHeaderFieldsTooLarge)
            .withEntity("Account request headers exceed the configured limit"))
        }
        .withShutdownTimeout(10.seconds)
        .build.use { server =>
          EmberClientBuilder.default[IO]
            .withTimeout(15.seconds)
            .withIdleConnectionTime(15.seconds)
            .build.use { client =>
              val uri: Uri = Uri.unsafeFromString(
                s"http://127.0.0.1:${server.address.port.value}/account"
              )
              val oversized: Request[IO] = Request[IO](Method.GET, uri).putHeaders(
                Header.Raw(CIString("Authorization"), "Bearer " + "x" * 4096)
              )
              val normal: Request[IO] = Request[IO](Method.GET, uri).putHeaders(
                Header.Raw(CIString("Authorization"), "Bearer account-reader")
              )
              for {
                _ <- client.run(oversized).use { response =>
                  response.as[String].flatMap { body =>
                    IO {
                      assertEquals(Status.RequestHeaderFieldsTooLarge, response.status)
                      assertEquals("Account request headers exceed the configured limit", body)
                    }
                  }
                }
                rejectedCount <- dispatched.get
                _ <- IO(assertEquals(0, rejectedCount,
                  "Oversized headers must be rejected before account handlers run"))
                _ <- client.run(normal).use { response =>
                  response.as[String].flatMap { body =>
                    IO {
                      assertEquals(Status.Ok, response.status)
                      assertEquals("Account: tea-shop", body)
                    }
                  }
                }
                acceptedCount <- dispatched.get
                _ <- IO(assertEquals(1, acceptedCount))
              } yield ()
            }
        }
    } yield ()

    program.timeout(50.seconds).unsafeRunSync()
  }
}
