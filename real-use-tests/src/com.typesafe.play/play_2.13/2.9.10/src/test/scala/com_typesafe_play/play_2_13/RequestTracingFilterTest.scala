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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.typedmap.TypedKey
import play.api.mvc.{Action, AnyContent, DefaultActionBuilder, EssentialAction, Filter, PlayBodyParsers,
  RequestHeader, Result, Results}
import play.api.test.FakeRequest

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/** Exercises Play HTTP filters for request correlation and completion auditing on an order-status endpoint. */
@Timeout(60)
class RequestTracingFilterTest {
  private val correlationId: TypedKey[String] = TypedKey[String]("correlation-id")
  private val correlationHeader: String = "X-Request-ID"

  case class AccessEvent(requestId: String, method: String, path: String, status: Int)

  class RequestTracingFilter(events: ConcurrentLinkedQueue[AccessEvent])(
      implicit val mat: Materializer, executionContext: ExecutionContext) extends Filter {
    override def apply(next: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] = {
      val id: String = request.headers.get(correlationHeader)
        .filter(_.matches("[A-Za-z0-9-]{1,64}"))
        .getOrElse(UUID.randomUUID().toString)
      next(request.addAttr(correlationId, id)).map { result =>
        events.add(AccessEvent(id, request.method, request.path, result.header.status))
        result.withHeaders(correlationHeader -> id)
      }
    }
  }

  @Test
  def correlatesControllerResponsesAndAuditEventsIncludingMissingOrders(): Unit = {
    val system: ActorSystem = ActorSystem("request-tracing-filter")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    try {
      val events: ConcurrentLinkedQueue[AccessEvent] = new ConcurrentLinkedQueue[AccessEvent]()
      val builder: DefaultActionBuilder = DefaultActionBuilder(PlayBodyParsers().defaultBodyParser)
      val orders: Map[String, String] = Map("ORD-101" -> "Dispatched", "ORD-102" -> "Packing")
      val status: Action[AnyContent] = builder.async { request =>
        val id: String = request.attrs(correlationId)
        val order: Option[String] = request.getQueryString("order").flatMap(orders.get)
        Future.successful(order match {
          case Some(state) => Results.Ok(s"$state; reference=$id")
          case None => Results.NotFound(s"Order not found; reference=$id")
        })
      }
      val endpoint: EssentialAction = new RequestTracingFilter(events).apply(status)

      def get(order: String, id: Option[String]): Future[Result] = {
        val base: RequestHeader = FakeRequest("GET", s"/orders/status?order=$order")
        val request: RequestHeader = id match {
          case Some(value) => base.withHeaders(base.headers.add(correlationHeader -> value))
          case None => base
        }
        endpoint(request).run(Source.empty)
      }

      def body(result: Result): String =
        Await.result(result.body.consumeData, 10.seconds).utf8String

      // Overlapping requests must keep their own context through the filter and asynchronous action.
      val responses: Seq[Result] = Await.result(Future.sequence(Seq(
        get("ORD-101", Some("checkout-101")),
        get("ORD-102", Some("checkout-102")),
        get("ORD-999", Some("checkout-999"))
      )), 10.seconds)
      assertEquals(Seq(OK, OK, NOT_FOUND), responses.map(_.header.status))
      assertEquals(Seq("checkout-101", "checkout-102", "checkout-999"),
        responses.map(_.header.headers(correlationHeader)))
      assertEquals(Seq("Dispatched; reference=checkout-101", "Packing; reference=checkout-102",
        "Order not found; reference=checkout-999"), responses.map(body))

      // Requests without a usable client ID receive an application-generated reference instead.
      val generated: Seq[Result] = Await.result(Future.sequence(Seq(
        get("ORD-101", None), get("ORD-999", Some("invalid client reference"))
      )), 10.seconds)
      assertEquals(Seq(OK, NOT_FOUND), generated.map(_.header.status))
      generated.foreach { result =>
        val id: String = result.header.headers(correlationHeader)
        assertEquals(id, UUID.fromString(id).toString)
        val message: String = if (result.header.status == OK) "Dispatched" else "Order not found"
        assertEquals(s"$message; reference=$id", body(result))
      }

      val allResponses: Seq[Result] = responses ++ generated
      val expectedEvents: Set[AccessEvent] = allResponses.map { result =>
        AccessEvent(result.header.headers(correlationHeader), "GET", "/orders/status", result.header.status)
      }.toSet
      assertEquals(5, events.size())
      assertEquals(expectedEvents, events.iterator().asScala.toSet)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
