/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import akka.stream.scaladsl.Source
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.HeaderNames.{ACCEPT, VARY}
import play.api.http.Status.{NOT_ACCEPTABLE, OK}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AcceptExtractors, Action, AnyContent, DefaultActionBuilder, PlayBodyParsers,
  Rendering, RequestHeader, Result, Results}
import play.api.test.FakeRequest

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/** Exercises Play HTTP content negotiation for browser and API representations of a product. */
@Timeout(60)
class ProductContentNegotiationTest extends Rendering with AcceptExtractors {
  private val productJson: JsValue = Json.obj(
    "sku" -> "COFFEE-01", "name" -> "Coffee beans", "priceCents" -> 1250
  )
  private val productHtml: String =
    "<!doctype html><html><body><h1>Coffee beans</h1><p>USD 12.50</p></body></html>"

  private def product(implicit request: RequestHeader): Result = render {
    case Accepts.Html() =>
      Results.Ok(productHtml).as("text/html").withHeaders(VARY -> "Accept-Encoding")
    case Accepts.Json() =>
      Results.Ok(productJson).withHeaders(VARY -> "Accept-Encoding")
  }

  @Test
  def selectsRepresentationsByClientPreferenceAndVariesCachesByAccept(): Unit = {
    val system: ActorSystem = ActorSystem("product-content-negotiation")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    try {
      val builder: DefaultActionBuilder = DefaultActionBuilder(PlayBodyParsers().defaultBodyParser)
      val show: Action[AnyContent] = builder { implicit request => product }

      def get(accept: Option[String]): Result = {
        val request: RequestHeader = accept match {
          case Some(value) => FakeRequest("GET", "/products/COFFEE-01").withHeaders(ACCEPT -> value)
          case None => FakeRequest("GET", "/products/COFFEE-01")
        }
        Await.result(show(request).run(Source.empty), 10.seconds)
      }

      def body(result: Result): String =
        Await.result(result.body.consumeData, 10.seconds).utf8String

      def assertRepresentation(accept: Option[String], expectedType: String): Unit = {
        val result: Result = get(accept)
        assertEquals(OK, result.header.status)
        assertEquals(Some(expectedType), result.body.contentType)
        val vary: Set[String] = result.header.headers(VARY).split(",").map(_.trim.toLowerCase(
          java.util.Locale.ROOT
        )).toSet
        assertEquals(Set("accept", "accept-encoding"), vary)
        if (expectedType == "application/json") {
          assertEquals(productJson, Json.parse(body(result)))
        } else {
          assertEquals(productHtml, body(result))
        }
      }

      assertRepresentation(Some("application/json"), "application/json")
      assertRepresentation(Some("text/html"), "text/html")
      // Client quality weights, not the order of render's cases, decide the preferred representation.
      assertRepresentation(Some("text/html;q=0.3, application/json;q=0.9"), "application/json")
      assertRepresentation(Some("application/json;q=0.2, text/html;q=0.8"), "text/html")
      // Skip an unsupported first choice and serve the next acceptable representation.
      assertRepresentation(Some("image/png;q=1.0, application/json;q=0.7"), "application/json")
      assertRepresentation(Some("application/*"), "application/json")
      assertRepresentation(Some("*/*"), "text/html")
      assertRepresentation(None, "text/html")

      val unsupported: Result = get(Some("image/png, application/xml;q=0.5"))
      assertEquals(NOT_ACCEPTABLE, unsupported.header.status)
      assertTrue(unsupported.header.headers(VARY).split(",").exists(_.trim.equalsIgnoreCase(ACCEPT)))
      assertEquals("", body(unsupported))
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
