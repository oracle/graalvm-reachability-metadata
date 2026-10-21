/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.Method
import org.http4s.QueryParamEncoder
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.UriTemplate
import org.http4s.UriTemplate.ParamContExp
import org.http4s.UriTemplate.ParamElm
import org.http4s.UriTemplate.ParamExp
import org.http4s.UriTemplate.PathElm
import org.http4s.UriTemplate.PathExp
import org.http4s.UriTemplate.SimpleFragmentExp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.util.Try

/**
 * Publishes and expands RFC 6570 URI templates with `org.http4s.UriTemplate`.
 *
 * An order API advertises hypermedia link templates for its order, order-search, and
 * shipment-tracking resources; a client renders them, expands their path, query, and
 * fragment variables with typed values, refuses to follow a template that still has
 * unexpanded variables, and sends the expanded URI back to the API.
 */
class OrderLinkTemplateTest {

  @Test
  def advertisesLinkTemplatesInRfc6570Notation(): Unit = {
    assertEquals("https://api.shop.example/orders{/orderId}", OrderTemplate.toString)
    assertEquals("https://api.shop.example/orders{?status,page,size}", SearchTemplate.toString)
    assertEquals(
      "/orders{/orderId}/shipments?embed=items{&cursor}{#section}",
      TrackingTemplate.toString
    )
  }

  @Test
  def refusesToFollowATemplateWithUnexpandedVariables(): Unit = {
    assertTrue(OrderTemplate.toUriIfPossible.isFailure)

    val partial: UriTemplate = SearchTemplate.expandQuery("status", OrderStatus.Open)
    assertEquals("https://api.shop.example/orders?status=open{&page,size}", partial.toString)
    assertTrue(partial.toUriIfPossible.isFailure)
  }

  @Test
  def followsAnExpandedOrderLinkToTheOrderResource(): Unit = {
    val link: Uri = followable(OrderTemplate.expandPath("orderId", "A-1001"))

    assertEquals(Some(Uri.Scheme.https), link.scheme)
    assertEquals(Some("api.shop.example"), link.host.map(_.value))
    assertEquals("/orders/A-1001", link.path.renderString)
    assertEquals("https://api.shop.example/orders/A-1001", link.renderString)

    val response: Response[IO] = OrderApi.serve(Request[IO](Method.GET, link))
    assertEquals(Status.Ok, response.status)
    assertEquals("order A-1001: 3 items", response.as[String].unsafeRunSync())
  }

  @Test
  def expandsTypedAndMultiValuedSearchParameters(): Unit = {
    val expanded: UriTemplate = SearchTemplate
      .expandQuery("status", List(OrderStatus.Open, OrderStatus.Shipped))
      .expandQuery("page", 2)
      .expandQuery("size", 50)
    val link: Uri = followable(expanded)

    assertEquals("/orders", link.path.renderString)
    assertEquals(Some(Seq("open", "shipped")), link.multiParams.get("status"))
    assertEquals(Some("2"), link.params.get("page"))
    assertEquals(Some("50"), link.params.get("size"))

    val response: Response[IO] = OrderApi.serve(Request[IO](Method.GET, link))
    assertEquals(Status.Ok, response.status)
    assertEquals("orders [open,shipped] page 2 of size 50", response.as[String].unsafeRunSync())
  }

  @Test
  def expandsTrackingContinuationAndFragmentVariables(): Unit = {
    val expanded: UriTemplate = TrackingTemplate
      .expandPath("orderId", "A-1001")
      .expandQuery("cursor", "c42")
      .expandFragment("section", "latest")
    val link: Uri = followable(expanded)

    assertEquals("/orders/A-1001/shipments", link.path.renderString)
    assertEquals(Some("items"), link.params.get("embed"))
    assertEquals(Some("c42"), link.params.get("cursor"))
    assertEquals(Some("latest"), link.fragment)

    val response: Response[IO] = OrderApi.serve(Request[IO](Method.GET, link))
    assertEquals(Status.Ok, response.status)
    assertEquals("shipments of A-1001 with items after c42", response.as[String].unsafeRunSync())
  }

  @Test
  def answersNotFoundForAnOrderOutsideTheCatalog(): Unit = {
    val link: Uri = followable(OrderTemplate.expandPath("orderId", "Z-9999"))

    val response: Response[IO] = OrderApi.serve(Request[IO](Method.GET, link))
    assertEquals(Status.NotFound, response.status)
  }

  /** Order state as the API spells it in query parameters. */
  enum OrderStatus(val wire: String) {
    case Open extends OrderStatus("open")
    case Shipped extends OrderStatus("shipped")
  }

  object OrderStatus {
    given QueryParamEncoder[OrderStatus] = QueryParamEncoder[String].contramap(_.wire)
  }

  val ApiAuthority: Uri.Authority = Uri.Authority(host = Uri.RegName("api.shop.example"))

  val OrderTemplate: UriTemplate = UriTemplate(
    scheme = Some(Uri.Scheme.https),
    authority = Some(ApiAuthority),
    path = List(PathElm("orders"), PathExp("orderId"))
  )

  val SearchTemplate: UriTemplate = UriTemplate(
    scheme = Some(Uri.Scheme.https),
    authority = Some(ApiAuthority),
    path = List(PathElm("orders")),
    query = List(ParamExp("status", "page", "size"))
  )

  val TrackingTemplate: UriTemplate = UriTemplate(
    path = List(PathElm("orders"), PathExp("orderId"), PathElm("shipments")),
    query = List(ParamElm("embed", "items"), ParamContExp("cursor")),
    fragment = List(SimpleFragmentExp("section"))
  )

  /** Converts a fully expanded template, failing the test when variables remain. */
  def followable(template: UriTemplate): Uri = {
    val attempt: Try[Uri] = template.toUriIfPossible
    attempt.fold(failure => throw new AssertionError(s"cannot follow $template", failure), identity)
  }

  /** The order API the link templates point into. */
  object OrderApi {
    private val itemCounts: Map[String, Int] = Map("A-1001" -> 3)

    def serve(request: Request[IO]): Response[IO] =
      request.uri.path.segments.map(_.decoded()).toList match {
        case List("orders", id) if itemCounts.contains(id) =>
          Response[IO](Status.Ok).withEntity(s"order $id: ${itemCounts(id)} items")
        case List("orders") =>
          val statuses: String = request.uri.multiParams.getOrElse("status", Nil).mkString(",")
          val page: String = request.uri.params.getOrElse("page", "1")
          val size: String = request.uri.params.getOrElse("size", "20")
          Response[IO](Status.Ok).withEntity(s"orders [$statuses] page $page of size $size")
        case List("orders", id, "shipments") if itemCounts.contains(id) =>
          val embed: String = request.uri.params.getOrElse("embed", "nothing")
          val cursor: String = request.uri.params.getOrElse("cursor", "start")
          Response[IO](Status.Ok).withEntity(s"shipments of $id with $embed after $cursor")
        case _ =>
          Response[IO](Status.NotFound)
      }
  }
}
