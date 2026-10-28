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
import akka.util.Timeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout => TestTimeout}
import play.api.mvc.{Action, AnyContent, DefaultActionBuilder, PlayBodyParsers, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{call, charset, contentAsString, contentType, status, writeableOf_AnyContentAsEmpty}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Exercises Play streamed HTTP responses for downloading an order report as a CSV attachment. */
@TestTimeout(60)
class StreamedOrderExportTest {
  case class Order(id: String, customer: String, totalCents: Int)

  @Test
  def downloadsAnOrderedCsvReportWithoutBufferingTheResponse(): Unit = {
    val system: ActorSystem = ActorSystem("streamed-order-export")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val timeout: Timeout = Timeout(10.seconds)

    try {
      val orders: Seq[Order] = Seq(
        Order("A-100", "Ada", 2500),
        Order("A-101", "Ren\u00e9", 4200),
        Order("A-102", "Grace", 1800)
      )
      val parsers: PlayBodyParsers = PlayBodyParsers()
      val action: DefaultActionBuilder = DefaultActionBuilder(parsers.defaultBodyParser)
      val exportOrders: Action[AnyContent] = action {
        val csv: Source[String, _] = Source.single("orderId,customer,totalCents\r\n")
          .concat(Source(orders).map { order =>
            s"${order.id},${order.customer},${order.totalCents}\r\n"
          })
        Results.Ok.chunked(csv)
          .as("text/csv; charset=utf-8")
          .withHeaders("Content-Disposition" -> "attachment; filename=\"orders.csv\"")
      }

      val response: Future[Result] = call(exportOrders, FakeRequest("GET", "/reports/orders.csv"))
      assertEquals(200, status(response))
      assertEquals(Some("text/csv"), contentType(response))
      assertEquals(Some("utf-8"), charset(response))
      val download: Result = Await.result(response, 10.seconds)
      assertEquals(Some("attachment; filename=\"orders.csv\""),
        download.header.headers.get("Content-Disposition"))
      assertEquals(None, download.body.contentLength)
      assertEquals(
        "orderId,customer,totalCents\r\n" +
          "A-100,Ada,2500\r\n" +
          "A-101,Ren\u00e9,4200\r\n" +
          "A-102,Grace,1800\r\n",
        contentAsString(response)
      )
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
