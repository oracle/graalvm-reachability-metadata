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
import org.http4s.HttpApp
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises Circe JSON query parameters for structured catalog search filters. */
class CatalogJsonQueryTest extends TapirJsonCirce {
  case class SearchFilter(categories: List[String], maxPriceCents: Int, inStockOnly: Boolean)
      derives Codec.AsObject, Schema
  case class Product(name: String, category: String, priceCents: Int, stock: Int)

  @Test
  @Timeout(60)
  def searchesCatalogWithStructuredQueryAndRejectsInvalidFilters(): Unit = {
    val products: List[Product] = List(
      Product("Tea strainer", "Home & kitchen", 850, 12),
      Product("Coffee grinder", "Home & kitchen", 4200, 3),
      Product("Ceramic mug", "Home & kitchen", 600, 0),
      Product("Notebook", "Stationery", 500, 20)
    )
    val search = endpoint.get
      .in("catalog")
      .in(jsonQuery[SearchFilter]("filter"))
      .out(stringBody)
      .serverLogicSuccess[IO] { filter =>
        val matches: List[String] = products.filter { product =>
          filter.categories.contains(product.category) &&
          product.priceCents <= filter.maxPriceCents &&
          (!filter.inStockOnly || product.stock > 0)
        }.map(_.name)
        IO.pure(matches.mkString("\n"))
      }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(search).orNotFound

    def request(filter: String): Request[IO] = Request[IO](
      Method.GET,
      Uri.unsafeFromString("/catalog").withQueryParam("filter", filter)
    )

    def execute(request: Request[IO]): (Status, String) = (for {
      response <- app.run(request)
      body <- response.bodyText.compile.string
    } yield (response.status, body)).timeout(30.seconds).unsafeRunSync()

    val available: (Status, String) = execute(request(
      """{"categories":["Home & kitchen"],"maxPriceCents":1000,"inStockOnly":true}"""
    ))
    assertEquals(Status.Ok, available._1)
    assertEquals("Tea strainer", available._2)

    val includingSoldOut: (Status, String) = execute(request(
      """{"categories":["Home & kitchen","Stationery"],"maxPriceCents":1000,"inStockOnly":false}"""
    ))
    assertEquals(Status.Ok, includingSoldOut._1)
    assertEquals("Tea strainer\nCeramic mug\nNotebook", includingSoldOut._2)

    val wrongFieldType: (Status, String) = execute(request(
      """{"categories":["Home & kitchen"],"maxPriceCents":"cheap","inStockOnly":true}"""
    ))
    assertEquals(Status.BadRequest, wrongFieldType._1)

    val malformedJson: (Status, String) = execute(request("{not-json}"))
    assertEquals(Status.BadRequest, malformedJson._1)

    val missingFilter: (Status, String) = execute(
      Request[IO](Method.GET, Uri.unsafeFromString("/catalog"))
    )
    assertEquals(Status.BadRequest, missingFilter._1)
  }
}
