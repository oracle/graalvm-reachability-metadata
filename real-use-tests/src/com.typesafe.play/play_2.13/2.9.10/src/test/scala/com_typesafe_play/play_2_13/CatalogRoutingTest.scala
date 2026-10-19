/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.mvc.{DefaultActionBuilder, PlayBodyParsers, Result, Results}
import play.api.routing.Router
import play.api.routing.sird._
import play.api.test.FakeRequest
import play.api.test.Helpers.{route, writeableOf_AnyContentAsEmpty}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/** Exercises Play SIRD routing for a catalog API with typed paths and search query parameters. */
@Timeout(60)
class CatalogRoutingTest {
  case class Product(id: Long, name: String, tags: Set[String], priceCents: Int)

  @Test
  def dispatchesCatalogRequestsUnderAnApiPrefix(): Unit = {
    val system: ActorSystem = ActorSystem("catalog-routing")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    try {
      val products: List[Product] = List(
        Product(41L, "Coffee beans", Set("coffee", "organic"), 1250),
        Product(42L, "Coffee grinder", Set("coffee", "equipment"), 4900),
        Product(43L, "Green tea", Set("tea", "organic"), 950)
      )
      val action: DefaultActionBuilder = DefaultActionBuilder(PlayBodyParsers().defaultBodyParser)
      val catalog: Router = Router.from {
        case GET(p"/products/${long(id)}") => action {
          products.find(_.id == id) match {
            case Some(product) => Results.Ok(s"${product.id}: ${product.name} (${product.priceCents} cents)")
            case None => Results.NotFound("Product not found")
          }
        }
        case GET(p"/search" ? q"term=$term" & q_s"tag=$tags" & q_o"sort=$sort") => action {
          val matching: List[Product] = products.filter { product =>
            product.name.toLowerCase(java.util.Locale.ROOT).contains(term.toLowerCase(java.util.Locale.ROOT)) &&
              tags.forall(product.tags.contains)
          }
          val ordered: List[Product] = sort match {
            case Some("price") => matching.sortBy(_.priceCents)
            case _ => matching.sortBy(_.name)
          }
          Results.Ok(ordered.map(_.name).mkString("\n"))
        }
      }
      val api: Router = catalog.withPrefix("/api")

      def get(uri: String): Result =
        Await.result(route(api, FakeRequest("GET", uri)).get, 10.seconds)

      def body(result: Result): String =
        Await.result(result.body.consumeData, 10.seconds).utf8String

      val detail: Result = get("/api/products/42")
      assertEquals(OK, detail.header.status)
      assertEquals("42: Coffee grinder (4900 cents)", body(detail))

      val missing: Result = get("/api/products/99")
      assertEquals(NOT_FOUND, missing.header.status)
      assertEquals("Product not found", body(missing))

      // Optional sorting and repeated tags are bound by the router, not manually parsed.
      val alphabetical: Result = get("/api/search?term=coffee")
      assertEquals(OK, alphabetical.header.status)
      assertEquals("Coffee beans\nCoffee grinder", body(alphabetical))
      assertEquals("Green tea\nCoffee beans", body(get("/api/search?term=&sort=price&tag=organic")))
      assertEquals("Coffee beans", body(get("/api/search?term=coffee&tag=coffee&tag=organic")))
      assertEquals("Green tea", body(get("/api/search?term=Green%20tea")))
      assertEquals("", body(get("/api/search?term=coffee&tag=tea")))

      // Requests outside the route's method, path type, prefix, or required query do not dispatch.
      assertTrue(route(api, FakeRequest("POST", "/api/products/42")).isEmpty)
      assertTrue(route(api, FakeRequest("GET", "/api/products/not-a-number")).isEmpty)
      assertTrue(route(api, FakeRequest("GET", "/api/products/9223372036854775808")).isEmpty)
      assertTrue(route(api, FakeRequest("GET", "/products/42")).isEmpty)
      assertTrue(route(api, FakeRequest("GET", "/api/search?tag=organic")).isEmpty)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
