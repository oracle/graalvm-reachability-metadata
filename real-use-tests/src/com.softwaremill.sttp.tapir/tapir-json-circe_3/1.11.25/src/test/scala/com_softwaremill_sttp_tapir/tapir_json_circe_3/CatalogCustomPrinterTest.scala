/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import io.circe.Codec as CirceCodec
import io.circe.Printer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.client3.Identity
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.basicRequest
import sttp.client3.testing.SttpBackendStub
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.stub.TapirStubInterpreter

/**
 * An application-defined `TapirJsonCirce` instance whose circe `Printer` drops null fields and
 * sorts keys, used for JSON bodies, a JSON query parameter and a standalone codec.
 *
 * A product catalog publishes compact JSON: absent optional fields are left out of the payload
 * rather than sent as `null`, and keys come out in a stable order. Clients filter the catalog with
 * a JSON document passed in a query parameter. The endpoints run in the sttp stub server
 * interpreter, the way application tests exercise server logic without opening a socket.
 */
class CatalogCustomPrinterTest {
  import CatalogCustomPrinterTest.*
  import CatalogCustomPrinterTest.CompactJson.*

  private val baseUri: Uri = uri"http://catalog.local"
  private val catalog: Catalog = new Catalog
  private val backend: SttpBackend[Identity, Any] = TapirStubInterpreter(SttpBackendStub.synchronous)
    .whenServerEndpointsRunLogic(catalog.serverEndpoints)
    .backend()

  @Test
  def encodesWithTheApplicationPrinter(): Unit = {
    val codec: Codec[String, Product, CodecFormat.Json] = circeCodec[Product]

    assertEquals(
      """{"priceCents":4999,"sku":"sku-lamp","tags":["home","light"],"title":"Desk lamp"}""",
      codec.encode(Product("sku-lamp", "Desk lamp", None, 4999L, List("home", "light")))
    )
    assertEquals(
      DecodeResult.Value(Product("sku-mug", "Mug", Some("Stoneware"), 1200L, Nil)),
      codec.decode("""{"sku":"sku-mug","title":"Mug","description":"Stoneware","priceCents":1200,"tags":[]}""")
    )
    assertTrue(codec.decode("""{"sku":"sku-mug","title":"Mug"}""").isInstanceOf[DecodeResult.Failure])
  }

  @Test
  def leavesAbsentOptionalFieldsOutOfTheResponseBody(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/products/sku-lamp")
      .send(backend)

    assertEquals(StatusCode.Ok, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("application/json")))
    assertEquals(
      """{"priceCents":4999,"sku":"sku-lamp","tags":["home","light"],"title":"Desk lamp"}""",
      raw.body.fold(identity, identity)
    )
  }

  @Test
  def filtersTheCatalogWithAJsonQueryParameter(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()

    val found: Either[Unit, List[Product]] = client
      .toRequestThrowDecodeFailures(searchProducts, Some(baseUri))
      .apply(SearchFilter(tag = Some("home"), maxPriceCents = 3000L))
      .send(backend)
      .body

    assertEquals(Right(List("sku-mug", "sku-rug")), found.map(_.map(_.sku)))

    val raw: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/products?filter=${"""{"maxPriceCents":1500}"""}")
      .send(backend)
    assertEquals(
      """[{"description":"Stoneware","priceCents":1200,"sku":"sku-mug","tags":["home","kitchen"],"title":"Mug"},"""
        + """{"priceCents":300,"sku":"sku-pen","tags":["office"],"title":"Pen"}]""",
      raw.body.fold(identity, identity)
    )
  }

  @Test
  def rejectsAFilterThatIsNotJson(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/products?filter=cheap")
      .send(backend)

    assertEquals(StatusCode.BadRequest, raw.code)
  }

  @Test
  def acceptsAProductBodyWithoutTheOptionalField(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .put(uri"$baseUri/products/sku-chair")
      .contentType(MediaType.ApplicationJson)
      .body("""{"sku":"sku-chair","title":"Chair","priceCents":8900,"tags":["office"]}""")
      .send(backend)

    assertEquals(StatusCode.Ok, raw.code)
    assertEquals(
      """{"priceCents":8900,"sku":"sku-chair","tags":["office"],"title":"Chair"}""",
      raw.body.fold(identity, identity)
    )
    assertEquals(Some(Product("sku-chair", "Chair", None, 8900L, List("office"))), catalog.find("sku-chair"))
  }
}

object CatalogCustomPrinterTest {

  /** The application's JSON settings: compact, without nulls, with sorted keys. */
  object CompactJson extends TapirJsonCirce {
    override def jsonPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true, sortKeys = true)
  }

  import CompactJson.*

  final case class Product(
      sku: String,
      title: String,
      description: Option[String],
      priceCents: Long,
      tags: List[String]
  ) derives CirceCodec.AsObject, Schema

  final case class SearchFilter(tag: Option[String] = None, maxPriceCents: Long)
      derives CirceCodec.AsObject, Schema

  val getProduct: PublicEndpoint[String, Unit, Product, Any] = endpoint.get
    .in("products" / path[String]("sku"))
    .out(jsonBody[Product])
    .errorOut(statusCode(StatusCode.NotFound))

  val searchProducts: PublicEndpoint[SearchFilter, Unit, List[Product], Any] = endpoint.get
    .in("products")
    .in(jsonQuery[SearchFilter]("filter"))
    .out(jsonBody[List[Product]])

  val putProduct: PublicEndpoint[(String, Product), Unit, Product, Any] = endpoint.put
    .in("products" / path[String]("sku"))
    .in(jsonBody[Product])
    .out(jsonBody[Product])
    .errorOut(statusCode(StatusCode.BadRequest))

  /** In-memory product catalog behind the endpoints, kept in insertion order. */
  final class Catalog {
    private var products: Vector[Product] = Vector(
      Product("sku-lamp", "Desk lamp", None, 4999L, List("home", "light")),
      Product("sku-mug", "Mug", Some("Stoneware"), 1200L, List("home", "kitchen")),
      Product("sku-rug", "Rug", Some("Wool, 120x80"), 2900L, List("home")),
      Product("sku-pen", "Pen", None, 300L, List("office"))
    )

    def find(sku: String): Option[Product] = synchronized(products.find(_.sku == sku))

    val serverEndpoints = List(
      getProduct.handle(sku => find(sku).toRight(())),
      searchProducts.handle { filter =>
        Right(synchronized(products).filter { product =>
          product.priceCents <= filter.maxPriceCents && filter.tag.forall(product.tags.contains)
        }.toList)
      },
      putProduct.handle { (sku, product) =>
        if (product.sku != sku) Left(())
        else {
          synchronized {
            products = products.filterNot(_.sku == sku) :+ product
          }
          Right(product)
        }
      }
    )
  }
}
