/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.{Environment, Mode}
import play.api.http.{DefaultHttpErrorHandler, HtmlOrJsonHttpErrorHandler, HttpErrorHandler,
  JsonHttpErrorHandler}
import play.api.http.HeaderNames.ACCEPT
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{RequestHeader, Result}
import play.api.test.FakeRequest

import scala.concurrent.Await
import scala.concurrent.duration._

/** Exercises Play production HTTP error handling for browser and API clients without exposing server secrets. */
@Timeout(60)
class ProductionHttpErrorsTest {
  @Test
  def rendersSafeErrorsForStorefrontBrowsersAndApiClients(): Unit = {
    val system: ActorSystem = ActorSystem("production-http-errors")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    try {
      val errors: HttpErrorHandler = new HtmlOrJsonHttpErrorHandler(
        new DefaultHttpErrorHandler(),
        new JsonHttpErrorHandler(Environment.simple(mode = Mode.Prod))
      )
      val browser: RequestHeader = FakeRequest("GET", "/orders/missing")
        .withHeaders(ACCEPT -> "text/html")
      val api: RequestHeader = FakeRequest("POST", "/orders")
        .withHeaders(ACCEPT -> "application/json")

      def body(result: Result): String =
        Await.result(result.body.consumeData, 10.seconds).utf8String

      val missing: Result = Await.result(errors.onClientError(browser, NOT_FOUND), 10.seconds)
      assertEquals(NOT_FOUND, missing.header.status)
      assertEquals(Some("text/html; charset=utf-8"), missing.body.contentType)
      val missingPage: String = body(missing)
      assertTrue(missingPage.contains("Not Found"))
      assertTrue(missingPage.contains("/orders/missing"))

      // Validation text can contain user input; the browser must see text, not executable markup.
      val validation: String = "Invalid order reference <script>alert(1)</script>"
      val invalidBrowser: Result = Await.result(
        errors.onClientError(browser, BAD_REQUEST, validation), 10.seconds
      )
      assertEquals(BAD_REQUEST, invalidBrowser.header.status)
      val invalidPage: String = body(invalidBrowser)
      assertTrue(invalidPage.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
      assertFalse(invalidPage.contains("<script>"))

      val invalidApi: Result = Await.result(
        errors.onClientError(api, BAD_REQUEST, validation), 10.seconds
      )
      assertEquals(BAD_REQUEST, invalidApi.header.status)
      assertEquals(Some("application/json"), invalidApi.body.contentType)
      assertEquals(Json.obj("error" -> Json.obj(
        "requestId" -> api.id, "message" -> validation
      )), Json.parse(body(invalidApi)))

      val secret: String = "Payment gateway credential merchant-secret-42 was rejected"
      val failure: IllegalStateException = new IllegalStateException(secret)
      List(browser, api).foreach { request =>
        val failed: Result = Await.result(errors.onServerError(request, failure), 10.seconds)
        assertEquals(INTERNAL_SERVER_ERROR, failed.header.status)
        val response: String = body(failed)
        assertFalse(response.contains(secret))
        assertFalse(response.contains("IllegalStateException"))
        assertFalse(response.contains("ProductionHttpErrorsTest"))
        if (request eq api) {
          assertEquals(Some("application/json"), failed.body.contentType)
          val document: JsValue = Json.parse(response)
          val details: JsObject = (document \ "error").as[JsObject]
          // The caller gets a support reference, not a stack trace or exception description.
          assertEquals(Set("id"), details.keys)
          assertTrue((details \ "id").as[String].nonEmpty)
        } else {
          assertEquals(Some("text/html; charset=utf-8"), failed.body.contentType)
          assertTrue(response.contains("Oops, an error occurred"))
          assertTrue(response.contains("This exception has been logged with id"))
        }
      }
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
