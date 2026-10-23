/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.Timeout
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout => TestTimeout}
import play.api.libs.json.Json
import play.api.mvc.{DefaultActionBuilder, PlayBodyParsers, Result, Results}
import play.api.routing.Router
import play.api.routing.sird._
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentAsString, route, status, writeableOf_AnyContentAsEmpty}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play SIRD routing with typed paths and query parameters for a catalog API. */
@TestTimeout(60)
class CatalogRoutingTest {
  case class Product(name: String, prices: Map[String, Int])

  @Test
  def dispatchesCatalogRequestsUnderAnApiPrefix(): Unit = {
    val system: ActorSystem = ActorSystem("catalog-routing")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)

    try {
      val products: Map[Long, Product] = Map(
        42L -> Product("Desk lamp", Map("USD" -> 2500, "EUR" -> 2300))
      )
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val catalog: Router = Router.from {
        case GET(p"/products/${long(id)}" ? q"currency=$currency") => action {
          products.get(id) match {
            case None => Results.NotFound("Product not found")
            case Some(product) => product.prices.get(currency) match {
              case None => Results.BadRequest("Unsupported currency")
              case Some(price) => Results.Ok(Json.obj(
                "id" -> id,
                "name" -> product.name,
                "currency" -> currency,
                "priceCents" -> price
              ))
            }
          }
        }
      }.withPrefix("/api")

      val dollars: Future[Result] = route(catalog,
        FakeRequest("GET", "/api/products/42?currency=USD")).get
      assertEquals(200, status(dollars))
      assertEquals(Json.obj(
        "id" -> 42L, "name" -> "Desk lamp", "currency" -> "USD", "priceCents" -> 2500
      ), contentAsJson(dollars))

      val euros: Future[Result] = route(catalog,
        FakeRequest("GET", "/api/products/42?currency=EUR")).get
      assertEquals(200, status(euros))
      assertEquals(2300, (contentAsJson(euros) \ "priceCents").as[Int])
      assertEquals("EUR", (contentAsJson(euros) \ "currency").as[String])

      val missingProduct: Future[Result] = route(catalog,
        FakeRequest("GET", "/api/products/99?currency=USD")).get
      assertEquals(404, status(missingProduct))
      assertEquals("Product not found", contentAsString(missingProduct))

      val unsupportedCurrency: Future[Result] = route(catalog,
        FakeRequest("GET", "/api/products/42?currency=GBP")).get
      assertEquals(400, status(unsupportedCurrency))
      assertEquals("Unsupported currency", contentAsString(unsupportedCurrency))

      assertTrue(route(catalog, FakeRequest("GET", "/api/products/not-a-number?currency=USD")).isEmpty)
      assertTrue(route(catalog, FakeRequest("GET", "/api/products/42")).isEmpty)
      assertTrue(route(catalog, FakeRequest("POST", "/api/products/42?currency=USD")).isEmpty)
      assertTrue(route(catalog, FakeRequest("GET", "/products/42?currency=USD")).isEmpty)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
