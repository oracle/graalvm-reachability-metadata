/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout => TestTimeout}
import play.api.http.websocket.{Message, TextMessage}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Result, Results, WebSocket}
import play.api.test.FakeRequest

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/** Exercises Play WebSocket handshakes and JSON message exchange for an inventory lookup channel. */
@TestTimeout(60)
class InventoryWebSocketTest {
  @Test
  def rejectsAnonymousConnectionsAndAnswersMultipleInventoryQueries(): Unit = {
    val system: ActorSystem = ActorSystem("inventory-websocket")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer

    try {
      val inventory: Map[String, Int] = Map("desk-lamp" -> 12, "office-chair" -> 0)
      val socket: WebSocket = WebSocket.acceptOrResult[JsValue, JsValue] { request =>
        if (!request.headers.get("X-Inventory-Key").contains("warehouse-key")) {
          Future.successful(Left(Results.Unauthorized("Inventory key required")))
        } else {
          val queries: Flow[JsValue, JsValue, _] = Flow[JsValue].map { query =>
            val requestId: String = (query \ "requestId").as[String]
            val sku: String = (query \ "sku").as[String]
            inventory.get(sku) match {
              case Some(quantity) => Json.obj(
                "requestId" -> requestId, "sku" -> sku, "available" -> quantity
              )
              case None => Json.obj(
                "requestId" -> requestId, "error" -> "Unknown product"
              )
            }
          }
          Future.successful(Right(queries))
        }
      }

      val rejected: Either[Result, Flow[Message, Message, _]] = Await.result(
        socket(FakeRequest("GET", "/inventory/live")), 10.seconds
      )
      rejected match {
        case Left(response) =>
          assertEquals(401, response.header.status)
          assertEquals("Inventory key required",
            Await.result(response.body.consumeData, 10.seconds).utf8String)
        case Right(_) => throw new AssertionError("Anonymous WebSocket connection was accepted")
      }

      val accepted: Either[Result, Flow[Message, Message, _]] = Await.result(
        socket(FakeRequest("GET", "/inventory/live")
          .withHeaders("X-Inventory-Key" -> "warehouse-key")), 10.seconds
      )
      val connection: Flow[Message, Message, _] = accepted match {
        case Right(flow) => flow
        case Left(response) =>
          throw new AssertionError(s"Inventory WebSocket rejected with ${response.header.status}")
      }
      val queries: Seq[JsValue] = Seq(
        Json.obj("requestId" -> "q1", "sku" -> "desk-lamp"),
        Json.obj("requestId" -> "q2", "sku" -> "unknown"),
        Json.obj("requestId" -> "q3", "sku" -> "office-chair")
      )
      val replies: Seq[Message] = Await.result(
        Source(queries).map[Message](query => TextMessage(Json.stringify(query)))
          .via(connection).runWith(Sink.seq),
        10.seconds
      )
      val documents: Seq[JsValue] = replies.map {
        case TextMessage(text) => Json.parse(text)
        case other => throw new AssertionError(s"Expected a JSON text reply, received $other")
      }
      assertEquals(Seq(
        Json.obj("requestId" -> "q1", "sku" -> "desk-lamp", "available" -> 12),
        Json.obj("requestId" -> "q2", "error" -> "Unknown product"),
        Json.obj("requestId" -> "q3", "sku" -> "office-chair", "available" -> 0)
      ), documents)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
