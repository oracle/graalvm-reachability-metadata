/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.core.AbortException
import upickle.core.TraceVisitor.TraceException
import upickle.default.*
import upickle.implicits.key

/**
 * Compile-time derived ReadWriter codecs for case classes and sealed hierarchies, as an order
 * service stores its order book as JSON and reads orders submitted by a partner storefront.
 */
class OrderBookDerivedCodecTest {

  import OrderBookDerivedCodecTest.*

  @Test
  def orderBookRoundTripsThroughCompactJson(): Unit = {
    val book: OrderBook = OrderBook(
      region = "eu-west",
      orders = List(
        Order(
          id = 1001L,
          customer = Customer("Ada Lovelace", Some("ada@example.org")),
          lines = Vector(OrderLine("SKU-1", 2, BigDecimal("9.99")), OrderLine("SKU-7", 1, BigDecimal("120.00"))),
          status = OrderStatus.Shipped("DHL", "TRACK-42"),
          tags = Set("priority")
        ),
        Order(
          id = 1002L,
          customer = Customer("Alan Turing", None),
          lines = Vector(OrderLine("SKU-3", 5, BigDecimal("1.50"))),
          status = OrderStatus.Cancelled(Some("customer request"))
        )
      )
    )

    val json: String = write(book)
    val restored: OrderBook = read[OrderBook](json)

    assertEquals(book, restored)
    assertTrue(json.contains("\"$type\":\"shipped\""), json)
    assertTrue(json.contains("\"tracking_number\":\"TRACK-42\""), json)
    assertEquals(BigDecimal("139.98"), restored.orders.head.total)
  }

  @Test
  def defaultValuesAreOmittedAndRestoredOnRead(): Unit = {
    val order: Order = Order(
      id = 7L,
      customer = Customer("Grace Hopper", None),
      lines = Vector(OrderLine("SKU-9", 1, BigDecimal("4.00")))
    )

    val tree: ujson.Value = writeJs(order)

    assertEquals(Set("id", "customer", "lines"), tree.obj.keySet.toSet)
    assertEquals(Set("name"), tree("customer").obj.keySet.toSet)
    assertEquals(order, read[Order](tree))
    assertEquals(OrderStatus.Pending, read[Order](tree).status)
  }

  @Test
  def partnerPayloadIsReadIntoTypedOrders(): Unit = {
    val payload: String =
      """[
        |  {"id": 2001, "customer": {"name": "Edsger Dijkstra", "email": "ewd@example.org"},
        |   "lines": [{"sku": "SKU-1", "quantity": 3, "unit_price": "2.25"}],
        |   "status": {"$type": "shipped", "carrier": "UPS", "tracking_number": "1Z999"}},
        |  {"id": 2002, "customer": {"name": "Barbara Liskov"},
        |   "lines": [{"sku": "SKU-2", "quantity": 1, "unit_price": "15"}],
        |   "status": "pending", "tags": ["gift", "fragile"]}
        |]""".stripMargin

    val orders: Seq[Order] = read[Seq[Order]](payload)

    assertEquals(2, orders.size)
    assertEquals(OrderStatus.Shipped("UPS", "1Z999"), orders(0).status)
    assertEquals(Some("ewd@example.org"), orders(0).customer.email)
    assertEquals(BigDecimal("6.75"), orders(0).total)
    assertEquals(OrderStatus.Pending, orders(1).status)
    assertEquals(Set("gift", "fragile"), orders(1).tags)
    assertEquals(None, orders(1).customer.email)
  }

  @Test
  def prettyPrintedJsonIsIndentedAndReadable(): Unit = {
    val status: OrderStatus = OrderStatus.Cancelled(None)

    val pretty: String = write(status, indent = 2)

    assertEquals("{\n  \"$type\": \"cancelled\"\n}", pretty)
    assertEquals(status, read[OrderStatus](pretty))
  }

  @Test
  def malformedOrdersAreReportedWithTheirJsonPath(): Unit = {
    val missingField: String = """{"customer": {"name": "Nobody"}, "lines": []}"""
    val unknownStatus: String =
      """{"id": 1, "customer": {"name": "N"}, "lines": [{"sku": "S", "quantity": 1, "unit_price": "1"}],
        | "status": "lost"}""".stripMargin

    val missing: TraceException = assertThrows(classOf[TraceException], () => read[Order](missingField))
    assertEquals("$", missing.jsonPath)
    assertTrue(missing.getCause.getMessage.contains("id"), missing.getCause.getMessage)

    val unknown: TraceException = assertThrows(classOf[TraceException], () => read[Order](unknownStatus))
    assertEquals("$['status']", unknown.jsonPath)
    assertTrue(unknown.getCause.isInstanceOf[AbortException], unknown.getCause.toString)
    assertTrue(unknown.getCause.getMessage.contains("invalid tag for tagged object: lost"), unknown.getCause.getMessage)
  }
}

object OrderBookDerivedCodecTest {

  final case class Customer(name: String, email: Option[String] = None) derives ReadWriter

  final case class OrderLine(sku: String, quantity: Int, @key("unit_price") unitPrice: BigDecimal)
      derives ReadWriter

  sealed trait OrderStatus derives ReadWriter

  object OrderStatus {
    @key("pending") case object Pending extends OrderStatus
    @key("shipped") final case class Shipped(carrier: String, @key("tracking_number") trackingNumber: String)
        extends OrderStatus
    @key("cancelled") final case class Cancelled(reason: Option[String] = None) extends OrderStatus
  }

  final case class Order(
      id: Long,
      customer: Customer,
      lines: Vector[OrderLine],
      status: OrderStatus = OrderStatus.Pending,
      tags: Set[String] = Set.empty
  ) derives ReadWriter {
    def total: BigDecimal = lines.map(line => line.unitPrice * line.quantity).sum
  }

  final case class OrderBook(region: String, orders: List[Order]) derives ReadWriter
}
