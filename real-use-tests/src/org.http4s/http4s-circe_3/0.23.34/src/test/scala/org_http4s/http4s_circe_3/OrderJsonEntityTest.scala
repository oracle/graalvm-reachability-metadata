/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
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
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises typed JSON entity codecs for an order-submission HTTP endpoint. */
class OrderJsonEntityTest {
  private final case class OrderLine(sku: String, quantity: Int, unitPriceCents: Long)
  private final case class Order(customer: String, lines: List[OrderLine], note: Option[String])
  private final case class Receipt(customer: String, units: Int, totalCents: Long, note: Option[String])

  private given Decoder[OrderLine] = deriveDecoder[OrderLine]
  private given Decoder[Order] = deriveDecoder[Order]
  private given Encoder[Receipt] = deriveEncoder[Receipt]
  private given Decoder[Receipt] = deriveDecoder[Receipt]
  private given EntityDecoder[IO, Order] = jsonOf[IO, Order]
  private given EntityEncoder[IO, Receipt] = jsonEncoderOf[IO, Receipt]
  private given EntityDecoder[IO, Receipt] = jsonOf[IO, Receipt]

  @Test
  @Timeout(60)
  def submitOrderAndReadReceipt(): Unit = {
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case request @ POST -> Root / "orders" =>
        request.as[Order].flatMap { order =>
          val receipt: Receipt = Receipt(
            order.customer,
            order.lines.map(_.quantity).sum,
            order.lines.map(line => line.quantity.toLong * line.unitPriceCents).sum,
            order.note
          )
          Created(receipt)
        }
    }

    val request: Request[IO] = Request[IO](Method.POST, Uri.unsafeFromString("/orders"))
      .withEntity(
        """{
          |  "customer": "buyer-42",
          |  "lines": [
          |    {"sku": "coffee", "quantity": 2, "unitPriceCents": 1250},
          |    {"sku": "filter", "quantity": 3, "unitPriceCents": 400}
          |  ],
          |  "note": "Leave at reception"
          |}""".stripMargin
      )
      .withContentType(`Content-Type`(MediaType.application.json))

    val result: IO[Unit] = for {
      response <- routes.orNotFound.run(request)
      receipt <- response.as[Receipt]
    } yield {
      assertEquals(Status.Created, response.status)
      assertEquals(Some(`Content-Type`(MediaType.application.json)), response.contentType)
      assertEquals(Receipt("buyer-42", 5, 3700L, Some("Leave at reception")), receipt)
    }

    result.timeout(30.seconds).unsafeRunSync()
  }
}
