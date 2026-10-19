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
import play.api.http.Status.OK
import play.api.libs.EventSource
import play.api.mvc.{Action, AnyContent, DefaultActionBuilder, PlayBodyParsers, RequestHeader, Result, Results}
import play.api.test.FakeRequest

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/** Exercises Play server-sent events for a resumable order-progress notification feed. */
@Timeout(60)
class OrderProgressEventStreamTest {
  case class Progress(sequence: Int, state: String, description: String)
  case class BrowserEvent(id: String, name: String, data: String)

  // Decode the response as an EventSource client does: blank lines dispatch events,
  // and repeated data fields are joined by newlines rather than dispatched separately.
  private def browserEvents(wire: String): List[BrowserEvent] = {
    if (wire.isEmpty) Nil
    else {
      assertTrue(wire.endsWith("\n\n"), "The final event must have a dispatch delimiter")
      wire.split("\n\n").toList.map { frame =>
        val fields: List[(String, String)] = frame.split("\n").toList.map { line =>
          val separator: Int = line.indexOf(':')
          assertTrue(separator > 0, s"Invalid SSE field: $line")
          (line.substring(0, separator), line.substring(separator + 1).stripPrefix(" "))
        }
        assertTrue(fields.forall(field => Set("id", "event", "data").contains(field._1)))
        BrowserEvent(
          fields.find(_._1 == "id").get._2,
          fields.find(_._1 == "event").get._2,
          fields.filter(_._1 == "data").map(_._2).mkString("\n")
        )
      }
    }
  }

  @Test
  def streamsNamedMultilineEventsAndResumesAfterTheLastReceivedId(): Unit = {
    val system: ActorSystem = ActorSystem("order-progress-events")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    try {
      val history: List[Progress] = List(
        Progress(1, "Accepted", "Order ORD-101 received"),
        Progress(2, "Packing", "Coffee beans\nGift message: Thank you!"),
        Progress(3, "Dispatched", "Tracking: PKG-101")
      )
      val builder: DefaultActionBuilder = DefaultActionBuilder(PlayBodyParsers().defaultBodyParser)
      val feed: Action[AnyContent] = builder { request =>
        val lastSeen: Int = request.headers.get("Last-Event-ID").flatMap(_.toIntOption).getOrElse(0)
        val events: Source[EventSource.Event, _] = Source(history.filter(_.sequence > lastSeen)).map { progress =>
          EventSource.Event(
            data = s"${progress.state}\n${progress.description}",
            id = Some(progress.sequence.toString),
            name = Some("order-progress")
          )
        }
        Results.Ok.chunked(events).withHeaders("Cache-Control" -> "no-cache")
      }

      def connect(lastId: Option[String]): List[BrowserEvent] = {
        val base: RequestHeader = FakeRequest("GET", "/orders/ORD-101/events")
        val request: RequestHeader = lastId match {
          case Some(id) => base.withHeaders(base.headers.add("Last-Event-ID" -> id))
          case None => base
        }
        val response: Result = Await.result(feed(request).run(Source.empty), 10.seconds)
        assertEquals(OK, response.header.status)
        assertEquals(Some("text/event-stream; charset=utf-8"), response.body.contentType)
        assertEquals("no-cache", response.header.headers("Cache-Control"))
        val wire: String = Await.result(response.body.consumeData, 10.seconds).utf8String
        browserEvents(wire)
      }

      val expected: List[BrowserEvent] = List(
        BrowserEvent("1", "order-progress", "Accepted\nOrder ORD-101 received"),
        BrowserEvent("2", "order-progress", "Packing\nCoffee beans\nGift message: Thank you!"),
        BrowserEvent("3", "order-progress", "Dispatched\nTracking: PKG-101")
      )
      assertEquals(expected, connect(None))
      // Reconnection must not replay the notification the browser already displayed.
      assertEquals(expected.drop(1), connect(Some("1")))
      assertEquals(expected.drop(2), connect(Some("2")))
      assertEquals(List.empty[BrowserEvent], connect(Some("3")))
      // A new subscriber receives the complete history independently of earlier connections.
      assertEquals(expected, connect(None))
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
