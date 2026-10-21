/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Runtime, Scope, Unsafe, ZIO}
import zio.http.*
import zio.http.template.*

/** Exercises ZIO HTTP HTML templates for a server-rendered order summary page. */
class OrderSummaryHtmlTest {
  case class OrderLine(description: String, quantity: Int, unitPriceCents: Int)
  case class OrderDetails(
      number: String,
      customer: String,
      lines: List[OrderLine],
      deliveryNote: Option[String]
  )

  @Test
  @Timeout(60)
  def rendersOrderDetailsWithoutTreatingCustomerContentAsMarkup(): Unit = {
    val order: OrderDetails = OrderDetails(
      "1042",
      "Ada & Sons",
      List(OrderLine("Gift \"boxed\" <set>", 2, 1250), OrderLine("Greeting card", 1, 300)),
      Some("Leave at <script>alert(\"paid\")</script> & ring bell")
    )
    val routes: Routes[Any, Nothing] = Routes(
      Method.GET / "orders" / "1042" -> handler(Response.html(orderPage(order)))
    )

    val program: ZIO[Scope, Throwable, Unit] = for {
      response <- routes.runZIO(Request.get("/orders/1042"))
      document <- response.body.asString
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, response.status)
        assertEquals(Some("text/html"), response.rawHeader("Content-Type"))
        assertTrue(document.startsWith("<!DOCTYPE html><html"))
        assertTrue(document.contains("<title>Order 1042</title>"))
        assertTrue(document.contains("<h1>Order 1042</h1>"))
        assertTrue(document.contains("<p>Customer: Ada &amp; Sons</p>"))
        assertTrue(document.contains(
          "<tr><td>Gift &quot;boxed&quot; &lt;set&gt;</td><td>2</td><td>25.00</td></tr>"
        ))
        assertTrue(document.contains(
          "<tr><td>Greeting card</td><td>1</td><td>3.00</td></tr>"
        ))
        assertTrue(document.contains("<strong>Total: 28.00</strong>"))
        assertTrue(document.contains(
          "title=\"Delivery note for Ada &amp; Sons\""
        ))
        assertTrue(document.contains(
          "Leave at &lt;script&gt;alert(&quot;paid&quot;)&lt;/script&gt; &amp; ring bell"
        ))
        assertTrue(document.contains(
          "href=\"/orders/1042/receipt?format=pdf&amp;download=true\""
        ))
        assertFalse(document.contains("<script>"))
        assertFalse(document.contains("<set>"))
      }
      withoutNote <- Response.html(orderPage(order.copy(deliveryNote = None))).body.asString
      _ <- ZIO.succeed {
        assertFalse(withoutNote.contains("Delivery note"))
        assertTrue(withoutNote.contains("<strong>Total: 28.00</strong>"))
      }
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.scoped(program)).getOrThrowFiberFailure()
    }
  }

  private def orderPage(order: OrderDetails): Html = {
    val totalCents: Int = order.lines.map(line => line.quantity * line.unitPriceCents).sum
    html(
      langAttr := "en",
      head(title(s"Order ${order.number}")),
      body(
        main(
          h1(s"Order ${order.number}"),
          p(s"Customer: ${order.customer}"),
          table(
            tHead(tr(th("Item"), th("Quantity"), th("Amount"))),
            tBody(order.lines.map(lineRow))
          ),
          strong(s"Total: ${money(totalCents)}"),
          order.deliveryNote.map(note => p(
            titleAttr := s"Delivery note for ${order.customer}",
            note
          )),
          a(
            hrefAttr := s"/orders/${order.number}/receipt?format=pdf&download=true",
            "Download receipt"
          )
        )
      )
    )
  }

  private def lineRow(line: OrderLine): Dom =
    tr(td(line.description), td(line.quantity.toString), td(money(line.quantity * line.unitPriceCents)))

  private def money(cents: Int): String = {
    val fraction: String = (cents % 100).toString.reverse.padTo(2, '0').reverse
    s"${cents / 100}.$fraction"
  }
}
