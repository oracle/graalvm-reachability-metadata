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
import akka.util.ByteString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status.{BAD_REQUEST, OK, UNSUPPORTED_MEDIA_TYPE}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, JsValue, Json, Reads}
import play.api.mvc.{Action, DefaultActionBuilder, PlayBodyParsers, RequestHeader, Result, Results}
import play.api.test.FakeRequest

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/** Exercises Play MVC JSON body parsing and validation for an order-quote endpoint. */
@Timeout(60)
class JsonOrderQuoteTest {
  case class QuoteRequest(sku: String, quantity: Int)

  @Test
  def quotesValidJsonAndRejectsInvalidRequestBodies(): Unit = {
    val system: ActorSystem = ActorSystem("json-order-quote")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    try {
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val quoteReads: Reads[QuoteRequest] = (
        (JsPath \ "sku").read[String](Reads.minLength[String](1)) and
          (JsPath \ "quantity").read[Int](Reads.min[Int](1) keepAnd Reads.max[Int](100))
      )(QuoteRequest.apply _)
      val quote: Action[QuoteRequest] = action(parsers.json[QuoteRequest](quoteReads)) { request =>
        val order: QuoteRequest = request.body
        Results.Ok(Json.obj(
          "sku" -> order.sku,
          "quantity" -> order.quantity,
          "currency" -> "USD",
          "totalCents" -> (order.quantity * 1250)
        ))
      }

      // Feed network-style byte chunks through the action, rather than bypassing its body parser.
      def submit(contentType: String, chunks: List[String]): Result = {
        val request: RequestHeader = FakeRequest("POST", "/orders/quote")
          .withHeaders(CONTENT_TYPE -> contentType)
        val bytes: Source[ByteString, _] = Source(chunks).map(ByteString(_))
        Await.result(quote(request).run(bytes), 10.seconds)
      }

      val accepted: Result = submit("application/json; charset=utf-8", List(
        "{\"sku\":\"COF", "FEE-01\",\"quantity\":", "3}"
      ))
      assertEquals(OK, accepted.header.status)
      assertEquals(Some("application/json"), accepted.body.contentType)
      val response: JsValue = Json.parse(Await.result(accepted.body.consumeData, 10.seconds).utf8String)
      assertEquals(Json.obj(
        "sku" -> "COFFEE-01", "quantity" -> 3, "currency" -> "USD", "totalCents" -> 3750
      ), response)

      val invalidBodies: List[String] = List(
        "{\"sku\":\"COFFEE-01\",\"quantity\":0}",
        "{\"sku\":\"COFFEE-01\",\"quantity\":101}",
        "{\"sku\":\"\",\"quantity\":1}",
        "{\"sku\":\"COFFEE-01\",\"quantity\":\"three\"}",
        "{\"sku\":\"COFFEE-01\"}",
        "{\"sku\":"
      )
      invalidBodies.foreach { body =>
        assertEquals(BAD_REQUEST, submit("application/json", List(body)).header.status, body)
      }
      assertEquals(
        UNSUPPORTED_MEDIA_TYPE,
        submit("text/plain", List("{\"sku\":\"COFFEE-01\",\"quantity\":3}")).header.status
      )

      // A rejected request does not prevent the endpoint from processing the next valid request.
      val next: Result = submit("application/json", List("{\"sku\":\"TEA-02\",\"quantity\":1}"))
      assertEquals(OK, next.header.status)
      val nextBody: JsValue = Json.parse(Await.result(next.body.consumeData, 10.seconds).utf8String)
      assertEquals("TEA-02", (nextBody \ "sku").as[String])
      assertEquals(1250, (nextBody \ "totalCents").as[Int])
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
