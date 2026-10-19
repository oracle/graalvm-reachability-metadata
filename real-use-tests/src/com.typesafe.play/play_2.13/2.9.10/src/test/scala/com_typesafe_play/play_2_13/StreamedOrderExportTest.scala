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
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import play.api.http.HeaderNames.{CONTENT_DISPOSITION, X_CONTENT_TYPE_OPTIONS}
import play.api.http.Status.OK
import play.api.mvc.{Result, Results}

import scala.concurrent.Await
import scala.concurrent.duration._

/** Exercises Play's streaming HTTP results for a downloadable CSV order export. */
@Timeout(60)
class StreamedOrderExportTest {
  case class OrderRow(number: String, customer: String, total: BigDecimal)

  private def csvField(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""

  private def download(orders: List[OrderRow]): Result = {
    val header: Source[ByteString, _] = Source.single(ByteString("order,customer,total\r\n"))
    val rows: Source[ByteString, _] = Source(orders).map { order =>
      ByteString(
        Seq(csvField(order.number), csvField(order.customer), order.total.bigDecimal.toPlainString)
          .mkString(",") + "\r\n"
      )
    }
    Results.Ok.chunked(header.concat(rows))
      .as("text/csv; charset=utf-8")
      .withHeaders(
        CONTENT_DISPOSITION -> "attachment; filename=\"orders.csv\"",
        X_CONTENT_TYPE_OPTIONS -> "nosniff"
      )
  }

  @Test
  def downloadsAllOrdersWithCsvEscapingAndAttachmentHeaders(): Unit = {
    val system: ActorSystem = ActorSystem("streamed-order-export")
    implicit val materializer: Materializer = SystemMaterializer(system).materializer
    try {
      val orders: List[OrderRow] = List(
        OrderRow("ORD-101", "Ada, Inc.", BigDecimal("42.50")),
        OrderRow("ORD-102", "Grace \"Amazing\" Hopper", BigDecimal("19.95")),
        OrderRow("ORD-103", "First line\r\nSecond line", BigDecimal("0.00"))
      )
      val response: Result = download(orders)

      assertEquals(OK, response.header.status)
      assertEquals("attachment; filename=\"orders.csv\"", response.header.headers(CONTENT_DISPOSITION))
      assertEquals("nosniff", response.header.headers(X_CONTENT_TYPE_OPTIONS))
      assertEquals(Some("text/csv; charset=utf-8"), response.body.contentType)
      assertTrue(response.body.contentLength.isEmpty)

      val downloaded: ByteString = Await.result(response.body.consumeData, 20.seconds)
      assertEquals(
        "order,customer,total\r\n" +
          "\"ORD-101\",\"Ada, Inc.\",42.50\r\n" +
          "\"ORD-102\",\"Grace \"\"Amazing\"\" Hopper\",19.95\r\n" +
          "\"ORD-103\",\"First line\r\nSecond line\",0.00\r\n",
        downloaded.utf8String
      )

      // An empty export still downloads a valid CSV document with its column names.
      val emptyExport: Result = download(Nil)
      assertEquals(OK, emptyExport.header.status)
      assertEquals("order,customer,total\r\n", Await.result(emptyExport.body.consumeData, 20.seconds).utf8String)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
