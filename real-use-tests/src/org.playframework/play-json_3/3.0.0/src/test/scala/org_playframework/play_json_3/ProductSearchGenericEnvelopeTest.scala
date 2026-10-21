/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_json_3

import java.util.Currency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import play.api.libs.functional.syntax.*
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonValidationError
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.libs.json.__

/**
 * Type-parameterized formats: generic envelope codecs built from the element type's `Format` with
 * functional syntax and macros, and adapted through `inmap`, `map` and `contramap`.
 *
 * A product-search API wraps every response in the same `ApiResponse[A]` envelope and every listing
 * in the same `Page[A]` pagination block. The service declares each envelope codec once, generic in
 * its payload, and obtains the codec for a concrete response by summoning it for the concrete
 * element type: products, plain tag names or nested pages all reuse the same definitions. Element
 * errors surface at the indexed path inside the envelope, so a client can tell which listed item
 * was malformed.
 */
class ProductSearchGenericEnvelopeTest {
  import ProductSearchGenericEnvelopeTest.*

  @Test
  def productPageRoundTripsThroughTheGenericEnvelope(): Unit = {
    val response: ApiResponse[Page[Product]] = ApiResponse(
      data = Page(
        items = Seq(
          Product("LAMP-01", "Desk lamp", Price(BigDecimal("39.90"), Currency.getInstance("EUR"))),
          Product("CHAIR-7", "Office chair", Price(BigDecimal("249.00"), Currency.getInstance("USD")))
        ),
        page = 1,
        total = 42L,
        next = Some("/products?page=2")
      ),
      requestId = "req-7f3a"
    )

    val json: JsValue = Json.toJson(response)
    assertEquals(
      Json.obj(
        "data" -> Json.obj(
          "items" -> Json.arr(
            Json.obj("sku" -> "LAMP-01", "title" -> "Desk lamp", "price" -> Json.obj("amount" -> 39.90, "currency" -> "EUR")),
            Json.obj("sku" -> "CHAIR-7", "title" -> "Office chair", "price" -> Json.obj("amount" -> 249.00, "currency" -> "USD"))
          ),
          "page" -> 1,
          "total" -> 42,
          "next" -> "/products?page=2"
        ),
        "requestId" -> "req-7f3a"
      ),
      json
    )
    assertEquals(response, json.as[ApiResponse[Page[Product]]])

    val lastPage: Page[Product] = Page(Seq.empty, 5, 42L, None)
    val lastPageJson: JsObject = Json.toJsObject(lastPage)
    assertEquals(Set("items", "page", "total"), lastPageJson.keys)
    assertEquals(lastPage, lastPageJson.as[Page[Product]])
  }

  @Test
  def theSameGenericCodecsServeOtherPayloadTypes(): Unit = {
    val tags: JsValue = Json.parse(
      """{"data": {"items": ["lighting", "office", "outdoor"], "page": 1, "total": 3}, "requestId": "req-tags"}"""
    )
    val tagResponse: ApiResponse[Page[String]] = tags.as[ApiResponse[Page[String]]]
    assertEquals(Seq("lighting", "office", "outdoor"), tagResponse.data.items)
    assertEquals(None, tagResponse.data.next)

    val facets: ApiResponse[Page[Page[Int]]] = ApiResponse(
      Page(Seq(Page(Seq(10, 20), 1, 2L, None), Page(Seq(30), 2, 3L, None)), 1, 2L, None),
      "req-facets"
    )
    val facetJson: JsValue = Json.toJson(facets)
    assertEquals(Seq(10, 20, 30), (facetJson \ "data" \ "items" \\ "items").flatMap(_.as[Seq[Int]]).toSeq)
    assertEquals(facets, facetJson.as[ApiResponse[Page[Page[Int]]]])

    val skuReads: Reads[Seq[String]] = summon[Reads[Page[Product]]].map(_.items.map(_.sku))
    val catalogPage: JsValue = Json.obj(
      "items" -> Json.arr(Json.obj("sku" -> "MUG-3", "title" -> "Mug", "price" -> Json.obj("amount" -> 9.5, "currency" -> "GBP"))),
      "page" -> 3,
      "total" -> 21
    )
    assertEquals(Some(Seq("MUG-3")), catalogPage.validate(using skuReads).asOpt)
  }

  @Test
  def malformedItemsAreReportedAtTheirIndexInsideTheEnvelope(): Unit = {
    val payload: JsValue = Json.parse(
      """{
        |  "data": {
        |    "items": [
        |      {"sku": "LAMP-01", "title": "Desk lamp", "price": {"amount": 39.9, "currency": "EUR"}},
        |      {"sku": "CHAIR-7", "title": "Office chair", "price": {"amount": "cheap", "currency": "ZZZ"}}
        |    ],
        |    "page": "first",
        |    "total": 42
        |  },
        |  "requestId": "req-bad"
        |}""".stripMargin
    )

    val result: JsResult[ApiResponse[Page[Product]]] = payload.validate[ApiResponse[Page[Product]]]
    val errors: Map[String, Seq[String]] = result match {
      case JsError(failures) => failures.map((path, messages) => path.toString -> messages.flatMap(_.messages).toSeq).toMap
      case JsSuccess(value, _) => throw new AssertionError(s"unexpected success: $value")
    }
    assertEquals(
      Map(
        "/data/items(1)/price/amount" -> Seq("error.expected.numberformatexception"),
        "/data/items(1)/price/currency" -> Seq("error.unknown.currency"),
        "/data/page" -> Seq("error.expected.jsnumber")
      ),
      errors
    )
  }

  @Test
  def envelopeCodecsAreAdaptedToDomainTypesWithInmapAndContramap(): Unit = {
    val listing: SearchResult = SearchResult(
      query = "lamp",
      products = Seq(Product("LAMP-01", "Desk lamp", Price(BigDecimal("39.90"), Currency.getInstance("EUR")))),
      hasMore = true
    )
    val json: JsObject = Json.toJsObject(listing)
    assertEquals(Json.obj("query" -> "lamp"), json - "results")
    assertEquals(1, (json \ "results" \ "page").as[Int])
    assertEquals(Some("/products?q=lamp&page=2"), (json \ "results" \ "next").asOpt[String])
    assertEquals(listing, json.as[SearchResult])

    val exhausted: JsObject = Json.toJsObject(listing.copy(hasMore = false))
    assertEquals(false, (exhausted \ "results").as[JsObject].keys.contains("next"))
    assertEquals(false, exhausted.as[SearchResult].hasMore)

    val auditWrites: Writes[SearchResult] = summon[Writes[Page[String]]].contramap[SearchResult](result =>
      Page(result.products.map(_.sku), 1, result.products.size.toLong, None)
    )
    assertEquals(
      Json.obj("items" -> Json.arr("LAMP-01"), "page" -> 1, "total" -> 1),
      auditWrites.writes(listing)
    )
  }
}

object ProductSearchGenericEnvelopeTest {

  final case class Price(amount: BigDecimal, currency: Currency)

  final case class Product(sku: String, title: String, price: Price)

  /** Pagination block shared by every listing endpoint. */
  final case class Page[A](items: Seq[A], page: Int, total: Long, next: Option[String])

  given pageFormat[A](using Reads[A], Writes[A]): OFormat[Page[A]] = (
    (__ \ "items").format[Seq[A]] and
      (__ \ "page").format[Int] and
      (__ \ "total").format[Long] and
      (__ \ "next").formatNullable[String]
  )((items, page, total, next) => Page(items, page, total, next), page => (page.items, page.page, page.total, page.next))

  /** Response envelope shared by every endpoint. */
  final case class ApiResponse[A](data: A, requestId: String)

  given responseFormat[A](using Reads[A], Writes[A]): OFormat[ApiResponse[A]] = Json.format[ApiResponse[A]]

  /** Domain view of a search response, stored on the wire as a query plus a product page. */
  final case class SearchResult(query: String, products: Seq[Product], hasMore: Boolean)

  given searchResultFormat: OFormat[SearchResult] = (
    (__ \ "query").format[String] and
      (__ \ "results").format(using pageFormat[Product])
  ).tupled.inmap[SearchResult](
    (query, page) => SearchResult(query, page.items, page.next.isDefined),
    result => (
      result.query,
      Page(result.products, 1, result.products.size.toLong, Option.when(result.hasMore)(s"/products?q=${result.query}&page=2"))
    )
  )

  given currencyFormat: Format[Currency] = Format(
    Reads.of[String].collect(JsonValidationError("error.unknown.currency")) {
      case code if knownCurrencies.contains(code) => Currency.getInstance(code)
    },
    Writes.of[String].contramap[Currency](_.getCurrencyCode)
  )

  private val knownCurrencies: Set[String] = Set("EUR", "GBP", "USD")

  given priceFormat: OFormat[Price] = Json.format[Price]

  given productFormat: OFormat[Product] = Json.format[Product]
}
