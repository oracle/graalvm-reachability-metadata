/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_ember_server_3

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Resource
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.HttpRoutes
import org.http4s.HttpVersion
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.h2.H2Keys.Http2PriorKnowledge
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.*

/** Exercises Ember's HTTP/2 multiplexing subsystem for independent dashboard report requests. */
class Http2DashboardMultiplexingTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  @Test
  @Timeout(60)
  def loadsStockWhileTheSalesReportIsStillBeingPrepared(): Unit = {
    val salesReport: String = "Sales report\n" + (1 to 5000).map { id =>
      s"order-$id,tea,24.00\n"
    }.mkString
    val program: IO[Unit] = for {
      preparing <- Deferred[IO, Unit]
      publish <- Deferred[IO, Unit]
      routes = HttpRoutes.of[IO] {
        case GET -> Root / "dashboard" / "ready" => Ok("ready")
        case GET -> Root / "dashboard" / "sales" =>
          preparing.complete(()).flatMap(_ => publish.get).flatMap(_ => Ok(salesReport))
        case GET -> Root / "dashboard" / "stock" => Ok("tea: 120 boxes")
      }
      _ <- EmberServerBuilder.default[IO]
        .withHost(Host.fromString("127.0.0.1").get)
        .withPort(Port.fromInt(0).get)
        .withHttp2
        // Both dashboard requests must make progress with only one accepted connection.
        .withMaxConnections(1)
        .withHttpApp(routes.orNotFound)
        .withShutdownTimeout(10.seconds)
        .build.use { server =>
          EmberClientBuilder.default[IO]
            .withHttp2
            .withTimeout(30.seconds)
            .withIdleConnectionTime(30.seconds)
            .build.use { client =>
              val base: Uri = Uri.unsafeFromString(
                s"http://127.0.0.1:${server.address.port.value}/dashboard"
              )
              // The internal service is configured for cleartext HTTP/2 prior knowledge.
              def fetch(report: String): IO[String] = {
                val request: Request[IO] = Request[IO](Method.GET, base / report)
                  .withAttribute(Http2PriorKnowledge, ())
                client.run(request).use { response =>
                  response.as[String].flatMap { body =>
                    IO {
                      assertEquals(Status.Ok, response.status)
                      assertEquals(HttpVersion.`HTTP/2`, response.httpVersion)
                      body
                    }
                  }
                }
              }
              for {
                // Establish the shared HTTP/2 session before issuing concurrent streams.
                ready <- fetch("ready")
                _ <- IO(assertEquals("ready", ready))
                _ <- Resource.make(fetch("sales").start)(_.cancel).use { sales =>
                  for {
                    _ <- preparing.get.timeout(15.seconds)
                    stock <- fetch("stock").timeout(15.seconds)
                    waiting <- publish.tryGet
                    _ <- IO {
                      assertEquals("tea: 120 boxes", stock)
                      assertTrue(waiting.isEmpty,
                        "The stock report must arrive before the sales report is released")
                    }
                    _ <- publish.complete(())
                    report <- sales.joinWithNever
                    _ <- IO(assertEquals(salesReport, report,
                      "The sales body must survive HTTP/2 framing and flow control intact"))
                  } yield ()
                }.guarantee(publish.complete(()).void)
              } yield ()
            }
        }
    } yield ()

    program.timeout(50.seconds).unsafeRunSync()
  }
}
