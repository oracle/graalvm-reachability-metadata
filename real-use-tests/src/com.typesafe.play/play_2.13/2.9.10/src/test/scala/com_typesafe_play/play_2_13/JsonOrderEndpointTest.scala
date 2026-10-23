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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, DefaultActionBuilder, PlayBodyParsers, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{call, contentAsJson, contentType, status}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play JSON body parsing and actions for an order submission endpoint. */
@TestTimeout(60)
class JsonOrderEndpointTest {
  @Test
  def acceptsOrdersAndRejectsInvalidRequestBodies(): Unit = {
    val system: ActorSystem = ActorSystem("json-order-endpoint")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)

    try {
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val actionBuilder: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val submitOrder: Action[JsValue] = actionBuilder(parsers.json(1024)) { request =>
        val sku: Option[String] = (request.body \ "sku").asOpt[String].filter(_.nonEmpty)
        val quantity: Option[Int] = (request.body \ "quantity").asOpt[Int].filter(_ > 0)
        (sku, quantity) match {
          case (Some(product), Some(count)) =>
            Results.Created(Json.obj(
              "sku" -> product,
              "quantity" -> count,
              "totalCents" -> (count * 2500)
            )).withHeaders("Location" -> s"/orders/$product")
          case _ =>
            Results.BadRequest(Json.obj("error" -> "A product and positive quantity are required"))
        }
      }

      val accepted: Future[Result] = call(submitOrder,
        FakeRequest("POST", "/orders")
          .withHeaders("Content-Type" -> "application/json")
          .withBody("""{"sku":"desk-lamp","quantity":3}"""))
      assertEquals(201, status(accepted))
      assertEquals(Some("application/json"), contentType(accepted))
      assertEquals(Some("/orders/desk-lamp"), Await.result(accepted, 10.seconds).header.headers.get("Location"))
      assertEquals(Json.obj("sku" -> "desk-lamp", "quantity" -> 3, "totalCents" -> 7500),
        contentAsJson(accepted))

      val invalidOrder: Future[Result] = call(submitOrder,
        FakeRequest("POST", "/orders")
          .withHeaders("Content-Type" -> "application/json")
          .withBody("""{"sku":"desk-lamp","quantity":0}"""))
      assertEquals(400, status(invalidOrder))
      assertEquals("A product and positive quantity are required",
        (contentAsJson(invalidOrder) \ "error").as[String])

      val malformed: Future[Result] = call(submitOrder,
        FakeRequest("POST", "/orders")
          .withHeaders("Content-Type" -> "application/json")
          .withBody("""{"sku":"desk-lamp","quantity":}"""))
      assertEquals(400, status(malformed))

      val wrongMediaType: Future[Result] = call(submitOrder,
        FakeRequest("POST", "/orders")
          .withHeaders("Content-Type" -> "text/plain")
          .withBody("""{"sku":"desk-lamp","quantity":3}"""))
      assertEquals(415, status(wrongMediaType))
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
