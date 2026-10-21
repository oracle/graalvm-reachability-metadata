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

/** Exercises Ember's application-error handling subsystem for rejected checkout reservations. */
class CheckoutErrorHandlingTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private class InventoryShortage(available: Int)
    extends RuntimeException(s"Private warehouse stock: $available units")

  private def assertResponse(response: Response[IO], status: Status, body: String): IO[Unit] =
    response.as[String].flatMap { actual =>
      IO {
        assertEquals(status, response.status)
        assertEquals(body, actual)
      }
    }

  @Test
  @Timeout(60)
  def rejectsAnOversizedReservationWithoutBreakingSubsequentCheckouts(): Unit = {
    val program: IO[Unit] = for {
      stock <- Ref.of[IO, Int](3)
      routes = HttpRoutes.of[IO] {
        case POST -> Root / "checkout" / IntVar(quantity) if quantity > 0 =>
          stock.modify[Either[InventoryShortage, Int]] { available =>
            if (quantity > available) (available, Left(new InventoryShortage(available)))
            else (available - quantity, Right(quantity))
          }.flatMap(result => IO.fromEither(result))
            .flatMap(reserved => Created(s"Reserved $reserved units"))
        case GET -> Root / "inventory" =>
          stock.get.flatMap(available => Ok(s"$available units available"))
      }
      _ <- EmberServerBuilder.default[IO]
        .withHost(Host.fromString("127.0.0.1").get)
        .withPort(Port.fromInt(0).get)
        .withHttpApp(routes.orNotFound)
        .withErrorHandler {
          case _: InventoryShortage =>
            Conflict("Not enough inventory to complete checkout").map(_.putHeaders(
              Header.Raw(CIString("X-Checkout-Error"), "insufficient-stock")
            ))
        }
        .withShutdownTimeout(10.seconds)
        .build.use { server =>
          EmberClientBuilder.default[IO]
            .withTimeout(15.seconds)
            .withIdleConnectionTime(15.seconds)
            .build.use { client =>
              val base: Uri = Uri.unsafeFromString(s"http://127.0.0.1:${server.address.port.value}")
              for {
                _ <- client.run(Request[IO](Method.POST, base / "checkout" / "5")).use { response =>
                  for {
                    _ <- assertResponse(response, Status.Conflict,
                      "Not enough inventory to complete checkout")
                    _ <- IO(assertEquals(Some("insufficient-stock"),
                      response.headers.get(CIString("X-Checkout-Error")).map(_.head.value)))
                  } yield ()
                }
                _ <- client.run(Request[IO](Method.GET, base / "inventory")).use { response =>
                  assertResponse(response, Status.Ok, "3 units available")
                }
                _ <- client.run(Request[IO](Method.POST, base / "checkout" / "2")).use { response =>
                  assertResponse(response, Status.Created, "Reserved 2 units")
                }
                _ <- client.run(Request[IO](Method.GET, base / "inventory")).use { response =>
                  assertResponse(response, Status.Ok, "1 units available")
                }
              } yield ()
            }
        }
    } yield ()

    program.timeout(50.seconds).unsafeRunSync()
  }
}
