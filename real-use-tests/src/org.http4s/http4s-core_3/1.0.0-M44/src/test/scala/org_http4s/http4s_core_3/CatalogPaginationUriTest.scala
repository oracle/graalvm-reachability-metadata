/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import org.http4s.ParseFailure
import org.http4s.ParseResult
import org.http4s.QueryParamDecoder
import org.http4s.QueryParamEncoder
import org.http4s.QueryParameterValue
import org.http4s.Uri
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Parses, builds, and resolves URIs with `org.http4s.Uri` for a paginated catalog API.
 *
 * A storefront backend receives a search request URI, reads its typed query parameters,
 * builds the next and previous page links, and resolves relative links returned by an
 * upstream inventory service against that service's base URI.
 */
class CatalogPaginationUriTest {

  @Test
  def readsTypedSearchParametersFromAnIncomingRequestUri(): Unit = {
    val incoming: ParseResult[Uri] =
      Uri.fromString("https://shop.example.com:8443/api/v1/products?q=red%20shoes&size=42&size=43&page=3")
    val uri: Uri = incoming.fold(failure => throw new AssertionError(failure.sanitized), identity)

    assertEquals(Some(Uri.Scheme.https), uri.scheme)
    assertEquals(Some("shop.example.com"), uri.host.map(_.value))
    assertEquals(Some(8443), uri.port)
    assertEquals(Vector("api", "v1", "products"), uri.path.segments.map(_.decoded()))
    assertEquals(Some("red shoes"), uri.params.get("q"))
    assertEquals(Some(Seq("42", "43")), uri.multiParams.get("size"))

    val page: Page = uri.query
      .params
      .get("page")
      .map(raw => QueryParamDecoder[Page].decode(QueryParameterValue(raw)))
      .flatMap(_.toOption)
      .getOrElse(Page(1))
    assertEquals(Page(3), page)
  }

  @Test
  def buildsNextAndPreviousPageLinksPreservingTheSearchFilters(): Unit = {
    val base: Uri = uri"https://shop.example.com/api/v1"
    val search: Uri = (base / "products")
      .withQueryParam("q", "red shoes & boots")
      .withQueryParam("size", List(42, 43))
      .withQueryParam("page", Page(3))

    val next: Uri = search.withQueryParam("page", Page(4))
    val previous: Uri = search.withQueryParam("page", Page(2))
    val first: Uri = search.removeQueryParam("page")

    assertEquals(
      "https://shop.example.com/api/v1/products?q=red%20shoes%20%26%20boots&size=42&size=43&page=4",
      next.renderString
    )
    assertEquals(Some("2"), previous.params.get("page"))
    assertEquals(Some("red shoes & boots"), previous.params.get("q"))
    assertTrue(first.params.get("page").isEmpty)
    assertEquals(Some(Seq("42", "43")), first.multiParams.get("size"))

    val reparsed: Uri = Uri.unsafeFromString(next.renderString)
    assertEquals(next.params, reparsed.params)
    assertEquals(next.path, reparsed.path)
  }

  @Test
  def resolvesRelativeLinksFromAnUpstreamInventoryService(): Unit = {
    val inventoryBase: Uri = uri"http://inventory.internal:9000/stock/warehouses/eu/"

    val sibling: Uri = inventoryBase.resolve(Uri.unsafeFromString("berlin?fresh=true"))
    val parent: Uri = inventoryBase.resolve(Uri.unsafeFromString("../us/denver"))
    val absolutePath: Uri = inventoryBase.resolve(Uri.unsafeFromString("/health"))
    val fragmentOnly: Uri = inventoryBase.resolve(Uri.unsafeFromString("#totals"))

    assertEquals("http://inventory.internal:9000/stock/warehouses/eu/berlin?fresh=true", sibling.renderString)
    assertEquals("http://inventory.internal:9000/stock/warehouses/us/denver", parent.renderString)
    assertEquals("http://inventory.internal:9000/health", absolutePath.renderString)
    assertEquals(Some("totals"), fragmentOnly.fragment)
    assertEquals(inventoryBase.path, fragmentOnly.path)
  }

  @Test
  def rejectsMalformedUrisWithAParseFailure(): Unit = {
    val malformed: ParseResult[Uri] = Uri.fromString("http://shop.example.com/products?q=%zz")

    assertTrue(malformed.isLeft)
    assertEquals(
      "products/summer sale 50%",
      Uri.decode(Uri.pathEncode("products/summer sale 50%"))
    )
  }

  /** A one-based catalog page number carried in the `page` query parameter. */
  final case class Page(number: Int)

  given pageEncoder: QueryParamEncoder[Page] = QueryParamEncoder[Int].contramap(_.number)

  given pageDecoder: QueryParamDecoder[Page] =
    QueryParamDecoder[Int].emap { number =>
      if (number >= 1) Right(Page(number))
      else Left(ParseFailure("Invalid page", s"page must be positive, got $number"))
    }
}
