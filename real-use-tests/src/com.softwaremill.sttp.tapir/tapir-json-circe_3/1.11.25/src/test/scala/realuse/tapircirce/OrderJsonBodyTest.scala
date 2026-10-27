/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Codec
import io.circe.Json
import io.circe.parser.parse
import org.http4s.HttpApp
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises Circe JSON body integration for nested order requests and receipts. */
class OrderJsonBodyTest extends TapirJsonCirce {
  case class Line(sku: String, quantity: Int, unitPriceCents: Int) derives Codec.AsObject, Schema
  case class Delivery(city: String, instructions: Option[String]) derives Codec.AsObject, Schema
  case class Order(customer: String, lines: List[Line], delivery: Delivery) derives Codec.AsObject, Schema
  case class Receipt(customer: String, totalCents: Int, itemCount: Int, delivery: Delivery)
      derives Codec.AsObject, Schema

  @Test
  @Timeout(60)
  def createsReceiptFromNestedJsonOrder(): Unit = {
    val createOrder = endpoint.post
      .in("orders")
      .in(jsonBody[Order])
      .out(statusCode(sttp.model.StatusCode.Created))
      .out(jsonBody[Receipt])
      .serverLogicSuccess[IO] { order =>
        IO.pure(Receipt(
          order.customer,
          order.lines.map(line => line.quantity * line.unitPriceCents).sum,
          order.lines.map(_.quantity).sum,
          order.delivery
        ))
      }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(createOrder).orNotFound
    val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/orders"))
      .withEntity("""{
        "customer": "customer-17",
        "lines": [
          {"sku": "tea", "quantity": 2, "unitPriceCents": 450},
          {"sku": "cup", "quantity": 3, "unitPriceCents": 700}
        ],
        "delivery": {"city": "Oxford", "instructions": "Leave at reception"}
      }""")
      .putHeaders(`Content-Type`(MediaType.application.json))

    val (status, contentType, body) = (for {
      response <- app.run(request)
      body <- response.bodyText.compile.string
    } yield (response.status, response.contentType.map(_.mediaType), body))
      .timeout(30.seconds)
      .unsafeRunSync()

    val expected: Json = parse("""{
      "customer": "customer-17",
      "totalCents": 3000,
      "itemCount": 5,
      "delivery": {"city": "Oxford", "instructions": "Leave at reception"}
    }""").fold(throw _, identity)
    val actual: Json = parse(body).fold(throw _, identity)
    assertEquals(Status.Created, status)
    assertEquals(Some(MediaType.application.json), contentType)
    assertEquals(expected, actual)
  }
}
