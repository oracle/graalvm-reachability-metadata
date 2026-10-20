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
import io.circe.Decoder
import io.circe.Encoder
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.jsonEncoderOf
import org.http4s.circe.jsonOf
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Location
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.*

/** Serves a stateful JSON order API through Ember's HTTP/1.1 request-response subsystem. */
class JsonOrderApiTest extends Http4sDsl[IO] {
  private case class Order(id: String, product: String, quantity: Int)

  private given Encoder[Order] = Encoder.forProduct3("id", "product", "quantity") { order =>
    (order.id, order.product, order.quantity)
  }
  private given Decoder[Order] = Decoder.forProduct3("id", "product", "quantity")(Order.apply)
  private given EntityEncoder[IO, Order] = jsonEncoderOf[Order]
  private given EntityDecoder[IO, Order] = jsonOf[IO, Order]
  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  @Test
  @Timeout(60)
  def submitsAnOrderAndFollowsItsLocation(): Unit = {
    val order: Order = Order("order-17", "green tea", 3)
    val program: IO[Unit] = for {
      orders <- Ref.of[IO, Map[String, Order]](Map.empty)
      routes = HttpRoutes.of[IO] {
        case request @ POST -> Root / "orders" =>
          for {
            submitted <- request.as[Order]
            _ <- orders.update(_.updated(submitted.id, submitted))
            response <- Created(submitted, Location(Uri.unsafeFromString(s"/orders/${submitted.id}")))
          } yield response
        case GET -> Root / "orders" / id =>
          orders.get.flatMap { saved =>
            saved.get(id) match {
              case Some(found) => Ok(found)
              case None => NotFound()
            }
          }
      }
      _ <- EmberServerBuilder.default[IO]
        .withHost(Host.fromString("127.0.0.1").get)
        .withPort(Port.fromInt(0).get)
        .withHttpApp(routes.orNotFound)
        .withShutdownTimeout(10.seconds)
        .build.use { server =>
          EmberClientBuilder.default[IO]
            .withTimeout(15.seconds)
            .withIdleConnectionTime(15.seconds)
            .build.use { client =>
              val base: Uri = Uri.unsafeFromString(s"http://127.0.0.1:${server.address.port.value}")
              val create: Request[IO] = Request[IO](Method.POST, base / "orders").withEntity(order)
              for {
                location <- client.run(create).use { response =>
                  for {
                    returned <- response.as[Order]
                    next <- IO {
                      assertEquals(Status.Created, response.status)
                      assertEquals(Some(MediaType.application.json), response.contentType.map(_.mediaType))
                      assertEquals(order, returned)
                      val location: Uri = response.headers.get[Location].get.uri
                      assertEquals("/orders/order-17", location.renderString)
                      base.resolve(location)
                    }
                  } yield next
                }
                _ <- client.run(Request[IO](Method.GET, location)).use { response =>
                  response.as[Order].flatMap { saved =>
                    IO {
                      assertEquals(Status.Ok, response.status)
                      assertEquals(order, saved)
                    }
                  }
                }
              } yield ()
            }
        }
    } yield ()

    program.timeout(50.seconds).unsafeRunSync()
  }
}
