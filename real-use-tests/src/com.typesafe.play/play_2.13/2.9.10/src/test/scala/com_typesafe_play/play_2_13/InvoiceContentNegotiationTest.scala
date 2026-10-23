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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout => TestTimeout}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerHelpers, DefaultActionBuilder, PlayBodyParsers, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{call, contentAsJson, contentAsString, contentType, status, writeableOf_AnyContentAsEmpty}
import play.twirl.api.Html

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play HTTP content negotiation for invoices served to browsers and JSON API clients. */
@TestTimeout(60)
class InvoiceContentNegotiationTest {
  class InvoiceController(action: DefaultActionBuilder) extends ControllerHelpers {
    val show: Action[AnyContent] = action { implicit request =>
      val invoiceId: String = "INV-42"
      val totalCents: Int = 7500
      render {
        case Accepts.Html() => Ok(Html(
          s"<h1>Invoice $invoiceId</h1><p>Total: $totalCents cents</p>"
        ))
        case Accepts.Json() => Ok(Json.obj("invoiceId" -> invoiceId, "totalCents" -> totalCents))
      }
    }
  }

  @Test
  def selectsThePreferredSupportedRepresentationAndRejectsUnsupportedFormats(): Unit = {
    val system: ActorSystem = ActorSystem("invoice-content-negotiation")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)

    try {
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val controller: InvoiceController = new InvoiceController(
        DefaultActionBuilder(parsers.defaultBodyParser)
      )
      val expectedHtml: String = "<h1>Invoice INV-42</h1><p>Total: 7500 cents</p>"

      val browser: Future[Result] = call(controller.show,
        FakeRequest("GET", "/invoices/INV-42")
          .withHeaders("Accept" -> "text/html,application/json;q=0.5"))
      assertEquals(200, status(browser))
      assertEquals(Some("text/html"), contentType(browser))
      assertEquals(expectedHtml, contentAsString(browser))
      assertEquals(Some("Accept"), Await.result(browser, 10.seconds).header.headers.get("Vary"))

      val apiClient: Future[Result] = call(controller.show,
        FakeRequest("GET", "/invoices/INV-42")
          .withHeaders("Accept" -> "text/html;q=0.3,application/json;q=0.9"))
      assertEquals(200, status(apiClient))
      assertEquals(Some("application/json"), contentType(apiClient))
      assertEquals(Json.obj("invoiceId" -> "INV-42", "totalCents" -> 7500), contentAsJson(apiClient))
      assertEquals(Some("Accept"), Await.result(apiClient, 10.seconds).header.headers.get("Vary"))

      val supportedFallback: Future[Result] = call(controller.show,
        FakeRequest("GET", "/invoices/INV-42")
          .withHeaders("Accept" -> "application/xml;q=1.0,application/json;q=0.8"))
      assertEquals(200, status(supportedFallback))
      assertEquals(Some("application/json"), contentType(supportedFallback))
      assertEquals(contentAsJson(apiClient), contentAsJson(supportedFallback))

      val unrestricted: Future[Result] = call(controller.show,
        FakeRequest("GET", "/invoices/INV-42"))
      assertEquals(200, status(unrestricted))
      assertEquals(Some("text/html"), contentType(unrestricted))
      assertEquals(expectedHtml, contentAsString(unrestricted))

      val xmlOnly: Future[Result] = call(controller.show,
        FakeRequest("GET", "/invoices/INV-42").withHeaders("Accept" -> "application/xml"))
      assertEquals(406, status(xmlOnly))
      assertEquals("", contentAsString(xmlOnly))
      assertEquals(Some("Accept"), Await.result(xmlOnly, 10.seconds).header.headers.get("Vary"))
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
