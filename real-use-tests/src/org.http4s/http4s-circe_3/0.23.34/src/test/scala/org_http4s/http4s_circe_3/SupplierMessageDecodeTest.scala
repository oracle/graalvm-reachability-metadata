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
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises JSON message syntax for normalizing a supplier's nested availability response. */
class SupplierMessageDecodeTest {
  private final case class Warehouse(code: String, available: Int)
  private final case class SupplierStock(product: String, warehouses: List[Warehouse])

  private given Decoder[Warehouse] = deriveDecoder[Warehouse]
  private given Decoder[SupplierStock] = deriveDecoder[SupplierStock]

  @Test
  @Timeout(60)
  def exposeSellableStockWithoutLeakingSupplierFields(): Unit = {
    val supplierDocument: Json = Json.obj(
      "product" -> Json.fromString("BEANS-1"),
      "warehouses" -> Json.arr(
        Json.obj("code" -> Json.fromString("north"), "available" -> Json.fromInt(8)),
        Json.obj("code" -> Json.fromString("south"), "available" -> Json.fromInt(3))
      ),
      "internalCostCents" -> Json.fromInt(425)
    )
    val supplierRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "supplier" / "stock" / "BEANS-1" => Ok(supplierDocument)
    }
    val supplier: Client[IO] = Client.fromHttpApp(supplierRoutes.orNotFound)
    val storefront: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "availability" / "BEANS-1" =>
        // Decode this response explicitly without installing a domain EntityDecoder.
        supplier.run(Request[IO](uri = Uri.unsafeFromString("/supplier/stock/BEANS-1")))
          .use(_.asJsonDecode[SupplierStock]).flatMap { stock =>
            Ok(Json.obj(
              "sku" -> Json.fromString(stock.product),
              "sellable" -> Json.fromInt((stock.warehouses.map(_.available).sum - 2).max(0))
            ))
          }
    }
    val request: Request[IO] = Request[IO](uri = Uri.unsafeFromString("/availability/BEANS-1"))

    val result: IO[Unit] = for {
      response <- storefront.orNotFound.run(request)
      document <- response.asJson
      _ <- IO {
        assertEquals(Status.Ok, response.status)
        // Exact equality also verifies that internal cost and warehouse details stay private.
        assertEquals(Json.obj(
          "sku" -> Json.fromString("BEANS-1"),
          "sellable" -> Json.fromInt(9)
        ), document)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }
}
