/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala case-class serialization for an outbound invoice event. */
@Timeout(60)
class InvoiceEventTest {
  case class InvoiceLine(sku: String, quantity: Int, unitPriceCents: Int) {
    def totalCents: Int = quantity * unitPriceCents
  }

  case class InvoiceEvent(invoiceId: String, customerId: String,
                          lines: List[InvoiceLine], totalCents: Int)

  @Test
  def publishesInvoiceWithNestedLineItems(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    // Money is represented in cents so that the event contains exact integer amounts.
    val cart: List[InvoiceLine] = List(
      InvoiceLine("notebook", 3, 450),
      InvoiceLine("pen", 2, 125))
    val event: InvoiceEvent = InvoiceEvent(
      "invoice-1042", "customer-17", cart, cart.map(_.totalCents).sum)
    assertEquals(1600, event.totalCents)

    val published: String = mapper.writeValueAsString(event)
    val document: JsonNode = mapper.readTree(published)
    val expected: JsonNode = mapper.readTree(
      """{
        |  "invoiceId": "invoice-1042",
        |  "customerId": "customer-17",
        |  "lines": [
        |    {"sku": "notebook", "quantity": 3, "unitPriceCents": 450},
        |    {"sku": "pen", "quantity": 2, "unitPriceCents": 125}
        |  ],
        |  "totalCents": 1600
        |}""".stripMargin)
    assertEquals(expected, document)

    // A consumer can reconcile the invoice total using only the published line items.
    val receivedLines: JsonNode = document.path("lines")
    val calculatedTotal: Int = (0 until receivedLines.size()).map { index: Int =>
      val line: JsonNode = receivedLines.get(index)
      line.path("quantity").asInt() * line.path("unitPriceCents").asInt()
    }.sum
    assertEquals(document.path("totalCents").asInt(), calculatedTotal)
  }
}
