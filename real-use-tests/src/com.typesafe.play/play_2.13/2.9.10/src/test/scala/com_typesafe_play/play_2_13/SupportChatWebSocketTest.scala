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
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.Status.UNAUTHORIZED
import play.api.http.websocket.{Message, TextMessage}
import play.api.mvc.{RequestHeader, Result, Results, WebSocket}
import play.api.test.FakeRequest

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play WebSockets for an authenticated support chat with per-connection message acknowledgments. */
@Timeout(60)
class SupportChatWebSocketTest {
  private def supportChat(implicit executionContext: ExecutionContext): WebSocket =
    WebSocket.acceptOrResult[String, String] { request =>
      val decision: Either[Result, Flow[String, String, _]] = request.session.get("displayName") match {
        case None => Left(Results.Unauthorized("Sign in to contact support"))
        case Some(customer) =>
          Right(Flow[String].map(_.trim).filter(_.nonEmpty).statefulMapConcat { () =>
            var sequence: Int = 0
            message => {
              sequence += 1
              List(s"Received $sequence from $customer: $message")
            }
          })
      }
      Future.successful(decision)
    }

  @Test
  def rejectsAnonymousConnectionsAndAcknowledgesEachCustomersMessages(): Unit = {
    val system: ActorSystem = ActorSystem("support-chat-websocket")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    try {
      val chat: WebSocket = supportChat
      val anonymous: RequestHeader = FakeRequest("GET", "/support/chat")
      val rejection: Result = Await.result(chat(anonymous), 10.seconds) match {
        case Left(result) => result
        case Right(_) => throw new AssertionError("Anonymous chat connection was accepted")
      }
      assertEquals(UNAUTHORIZED, rejection.header.status)
      assertEquals(
        "Sign in to contact support",
        Await.result(rejection.body.consumeData, 10.seconds).utf8String
      )

      def exchange(customer: String, messages: List[String]): Seq[Message] = {
        val request: RequestHeader = FakeRequest("GET", "/support/chat").withSession("displayName" -> customer)
        val connection: Flow[Message, Message, _] = Await.result(chat(request), 10.seconds) match {
          case Right(flow) => flow
          case Left(result) => throw new AssertionError(s"Signed-in chat rejected: ${result.header.status}")
        }
        // Use protocol messages so Play's String WebSocket transformer participates in both directions.
        Await.result(
          Source(messages.map(message => TextMessage(message): Message)).via(connection).runWith(Sink.seq),
          10.seconds
        )
      }

      assertEquals(
        Seq(TextMessage("Received 1 from Ada: Where is order ORD-101?"),
          TextMessage("Received 2 from Ada: Please email the tracking link.")),
        exchange("Ada", List("  Where is order ORD-101?  ", "   ", "Please email the tracking link."))
      )
      // A different customer's connection starts its own conversation without the previous customer's state.
      assertEquals(
        Seq(TextMessage("Received 1 from Grace: Can I change my delivery address?")),
        exchange("Grace", List("Can I change my delivery address?"))
      )
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
