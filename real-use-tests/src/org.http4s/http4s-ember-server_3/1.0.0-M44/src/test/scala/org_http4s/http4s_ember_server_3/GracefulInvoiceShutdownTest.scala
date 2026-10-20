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
import org.http4s.Header
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import scala.concurrent.duration.*

/** Exercises Ember's graceful shutdown subsystem while an invoice download is in flight. */
class GracefulInvoiceShutdownTest extends Http4sDsl[IO] {
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private def acceptsConnections(port: Int): IO[Boolean] =
    Resource.make(IO(new Socket()))(socket => IO.blocking(socket.close())).use { socket =>
      IO.blocking {
        socket.connect(new InetSocketAddress("127.0.0.1", port), 10000)
        true
      }.recover { case _: ConnectException => false }
    }

  private def awaitListenerClosed(port: Int): IO[Unit] =
    acceptsConnections(port).flatMap { accepting =>
      if (accepting) IO.cede.flatMap(_ => awaitListenerClosed(port))
      else IO.unit
    }

  @Test
  @Timeout(60)
  def finishesAnAcceptedInvoiceBeforeReleasingTheServer(): Unit = {
    val invoice: String = "Invoice INV-17\nGreen tea: 3 boxes\nTotal: 24.00 USD\n"
    val program: IO[Unit] = for {
      bound <- Deferred[IO, Int]
      entered <- Deferred[IO, Unit]
      finishInvoice <- Deferred[IO, Unit]
      stop <- Deferred[IO, Unit]
      released <- Deferred[IO, Unit]
      routes = HttpRoutes.of[IO] {
        case GET -> Root / "invoices" / "INV-17" =>
          entered.complete(()).flatMap(_ => finishInvoice.get).flatMap(_ => Ok(invoice))
      }
      serve = EmberServerBuilder.default[IO]
        .withHost(Host.fromString("127.0.0.1").get)
        .withPort(Port.fromInt(0).get)
        .withHttpApp(routes.orNotFound)
        .withShutdownTimeout(20.seconds)
        .build.use { server =>
          bound.complete(server.address.port.value).flatMap(_ => stop.get)
        }.guarantee(released.complete(()).void)
      _ <- EmberClientBuilder.default[IO]
        .withTimeout(30.seconds)
        .withIdleConnectionTime(40.seconds)
        .build.use { client =>
          Resource.make(serve.start)(_.cancel).use { serverFiber =>
            for {
              port <- bound.get
              uri = Uri.unsafeFromString(s"http://127.0.0.1:$port/invoices/INV-17")
              request = Request[IO](Method.GET, uri)
                .putHeaders(Header.Raw(CIString("Connection"), "close"))
              download = client.run(request).use { response =>
                response.as[String].flatMap { body =>
                  IO {
                    assertEquals(Status.Ok, response.status)
                    assertEquals(invoice, body)
                  }
                }
              }
              _ <- Resource.make(download.start)(_.cancel).use { downloadFiber =>
                for {
                  _ <- entered.get.timeout(15.seconds)
                  _ <- stop.complete(())
                  // Observe shutdown through the network, not scheduler timing or a sleep.
                  _ <- awaitListenerClosed(port).timeout(15.seconds)
                  done <- released.tryGet
                  _ <- IO(assertTrue(done.isEmpty,
                    "Server release must wait for the accepted invoice request"))
                  _ <- finishInvoice.complete(())
                  _ <- downloadFiber.joinWithNever
                  _ <- serverFiber.joinWithNever
                  closed <- released.tryGet
                  _ <- IO(assertTrue(closed.isDefined,
                    "The server must finish shutting down after the invoice completes"))
                } yield ()
              }.guarantee(finishInvoice.complete(()).void)
            } yield ()
          }
        }
    } yield ()

    program.timeout(50.seconds).unsafeRunSync()
  }
}
