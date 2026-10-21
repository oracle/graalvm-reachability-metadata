/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.model.StatusCode

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.*

/**
 * Exercises circe-backed request bodies and response decoding with `asJson`: an order client
 * posts generically derived case classes to a live HTTP endpoint and reads the created and
 * listed orders back as typed values.
 */
class OrderApiJsonRoundTripTest {
  private final case class Customer(name: String, email: String)
  private final case class OrderLine(sku: String, quantity: Int, unitPrice: BigDecimal)
  private final case class NewOrder(customer: Customer, lines: List[OrderLine])
  private final case class Order(
      id: Long,
      customer: Customer,
      lines: List[OrderLine],
      total: BigDecimal,
      status: String
  )
  private final case class OrderPage(count: Int, orders: List[Order])

  private var server: HttpServer = null
  private var backend: SyncBackend = null
  private val orders: ConcurrentHashMap[Long, Order] = new ConcurrentHashMap[Long, Order]()
  private val requestContentTypes: ConcurrentHashMap[String, String] = new ConcurrentHashMap[String, String]()

  @BeforeEach
  def startServer(): Unit = {
    val ids: AtomicLong = new AtomicLong(100L)
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/orders",
      (exchange: HttpExchange) => {
        try {
          exchange.getRequestMethod match {
            case "POST" =>
              requestContentTypes.put("POST", exchange.getRequestHeaders.getFirst("Content-Type"))
              val payload: String =
                new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
              decode[NewOrder](payload) match {
                case Right(newOrder) =>
                  val total: BigDecimal = newOrder.lines.map(line => line.unitPrice * line.quantity).sum
                  val order: Order = Order(ids.incrementAndGet(), newOrder.customer, newOrder.lines, total, "OPEN")
                  orders.put(order.id, order)
                  respond(exchange, 201, order.asJson.noSpaces)
                case Left(error) =>
                  respond(exchange, 400, error.getMessage)
              }
            case "GET" =>
              val all: List[Order] = orders.values().toArray(Array.empty[Order]).toList.sortBy(_.id)
              respond(exchange, 200, OrderPage(all.size, all).asJson.noSpaces)
            case _ =>
              respond(exchange, 405, "")
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    backend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
  }

  @Test
  def createsOrdersAndListsThemAsTypedValues(): Unit = {
    val baseUri = uri"http://127.0.0.1:${server.getAddress.getPort}/orders"

    val keyboard: NewOrder = NewOrder(
      Customer("Ada Lovelace", "ada@example.com"),
      List(OrderLine("KB-01", 2, BigDecimal("49.90")), OrderLine("MS-07", 1, BigDecimal("19.99")))
    )
    val created: Response[Either[ResponseException[String], Order]] = basicRequest
      .post(baseUri)
      .body(asJson(keyboard))
      .response(asJson[Order])
      .readTimeout(10.seconds)
      .send(backend)

    assertThat(created.code).isEqualTo(StatusCode.Created)
    assertThat(requestContentTypes.get("POST")).startsWith("application/json")
    val createdOrder: Order = created.body.fold(error => throw error, identity)
    assertThat(createdOrder.id).isEqualTo(101L)
    assertThat(createdOrder.customer).isEqualTo(keyboard.customer)
    assertThat(createdOrder.lines).isEqualTo(keyboard.lines)
    assertThat(createdOrder.total).isEqualTo(BigDecimal("119.79"))
    assertThat(createdOrder.status).isEqualTo("OPEN")

    val monitor: NewOrder = NewOrder(
      Customer("Grace Hopper", "grace@example.com"),
      List(OrderLine("MN-27", 1, BigDecimal("229.00")))
    )
    val secondOrder: Order = basicRequest
      .post(baseUri)
      .body(asJson(monitor))
      .response(asJson[Order])
      .readTimeout(10.seconds)
      .send(backend)
      .body
      .fold(error => throw error, identity)
    assertThat(secondOrder.id).isEqualTo(102L)
    assertThat(secondOrder.total).isEqualTo(BigDecimal("229.00"))

    val listed: Response[Either[ResponseException[String], OrderPage]] = basicRequest
      .get(baseUri)
      .response(asJson[OrderPage])
      .readTimeout(10.seconds)
      .send(backend)

    assertThat(listed.code).isEqualTo(StatusCode.Ok)
    val page: OrderPage = listed.body.fold(error => throw error, identity)
    assertThat(page.count).isEqualTo(2)
    assertThat(page.orders.map(_.customer.name)).isEqualTo(List("Ada Lovelace", "Grace Hopper"))
    assertThat(page.orders).isEqualTo(List(createdOrder, secondOrder))
  }

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, if (bytes.isEmpty) -1 else bytes.length.toLong)
    if (bytes.nonEmpty) {
      exchange.getResponseBody.write(bytes)
    }
  }
}

