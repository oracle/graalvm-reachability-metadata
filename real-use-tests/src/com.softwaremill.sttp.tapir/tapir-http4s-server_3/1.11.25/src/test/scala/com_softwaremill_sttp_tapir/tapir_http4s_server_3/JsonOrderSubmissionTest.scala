/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.decode
import org.http4s.{Header, Method, Request, Response}
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises JSON body decoding, validation, and response encoding in Tapir's http4s routes. */
class JsonOrderSubmissionTest {
  final case class Order(sku: String, quantity: Int)
  final case class Receipt(orderId: String, sku: String, quantity: Int, totalCents: Int)

  private given Encoder[Order] = deriveEncoder[Order]
  private given Decoder[Order] = deriveDecoder[Order]
  private given Schema[Order] = Schema.derived[Order]
    .modify(_.quantity)(_.validate(Validator.min(1)))
  private given Encoder[Receipt] = deriveEncoder[Receipt]
  private given Decoder[Receipt] = deriveDecoder[Receipt]
  private given Schema[Receipt] = Schema.derived[Receipt]

  @Test
  @Timeout(60)
  def acceptsValidOrdersAndRejectsInvalidJsonBeforeApplicationLogic(): Unit = {
    val submit: PublicEndpoint[Order, Unit, (String, Receipt), Any] = endpoint.post
      .in("orders")
      .in(jsonBody[Order])
      .out(statusCode(StatusCode.Created))
      .out(header[String]("Location"))
      .out(jsonBody[Receipt])

    val scenario: IO[Unit] = for {
      orders <- Ref.of[IO, Vector[Order]](Vector.empty)
      app = Http4sServerInterpreter[IO]().toRoutes(
        submit.serverLogicSuccess { (order: Order) =>
          orders.modify { (existing: Vector[Order]) =>
            val id: String = s"order-${existing.size + 1}"
            val receipt: Receipt = Receipt(id, order.sku, order.quantity, order.quantity * 1250)
            (existing :+ order, (s"/orders/$id", receipt))
          }
        }
      ).orNotFound
      accepted <- app.run(orderRequest("""{"sku":"coffee-beans","quantity":3}"""))
      acceptedBody <- accepted.as[String]
      _ <- IO {
        assertEquals(201, accepted.status.code)
        assertEquals(Some("/orders/order-1"), headerValue(accepted, "Location"))
        assertTrue(headerValue(accepted, "Content-Type").exists(_.startsWith("application/json")))
        assertEquals(
          Right(Receipt("order-1", "coffee-beans", 3, 3750)),
          decode[Receipt](acceptedBody)
        )
      }
      invalid <- app.run(orderRequest("""{"sku":"coffee-beans","quantity":0}"""))
      invalidBody <- invalid.as[String]
      malformed <- app.run(orderRequest("""{"sku":"coffee-beans","quantity":"three"}"""))
      malformedBody <- malformed.as[String]
      saved <- orders.get
      _ <- IO {
        assertEquals(400, invalid.status.code)
        assertTrue(invalidBody.nonEmpty)
        assertEquals(400, malformed.status.code)
        assertTrue(malformedBody.nonEmpty)
        assertEquals(Vector(Order("coffee-beans", 3)), saved)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def orderRequest(body: String): Request[IO] =
    Request[IO](Method.POST, uri"/orders")
      .withEntity(body)
      .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

  private def headerValue(response: Response[IO], name: String): Option[String] =
    response.headers.headers.find(_.name == CIString(name)).map(_.value)
}
