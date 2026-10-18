/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import io.circe.HCursor
import io.circe.Json
import io.circe.parser.parse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client3.HttpClientSyncBackend
import sttp.client3.Identity
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.basicRequest
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * JSON request and response bodies through `jsonBody` with circe codecs, served by a live JDK HTTP
 * server and called through the sttp client interpreter.
 *
 * An order service accepts new orders as JSON, answers with the stored order, reports a missing
 * order as a JSON error with a 404 status, and rejects a malformed body before the business logic
 * runs. A client built from the same endpoint definitions talks to it over real HTTP.
 */
class OrderApiJsonBodiesTest {
  import OrderApiJsonBodiesTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val store: OrderStore = new OrderStore

  @BeforeEach
  def startServer(): Unit = {
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(store.serverEndpoints)
      .start()
    backend = HttpClientSyncBackend()
    baseUri = uri"http://localhost:${server.getAddress.getPort}"
  }

  @AfterEach
  def stopServer(): Unit = {
    if (backend != null) backend.close()
    if (server != null) server.stop(0)
  }

  @Test
  def createsAnOrderAndReadsItBack(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()
    val newOrder: NewOrder = NewOrder(
      customer = "ada@example.com",
      lines = List(OrderLine("sku-keyboard", 1, 1999L), OrderLine("sku-cable", 3, 499L)),
      note = Some("leave at the door")
    )

    val created: Order = client
      .toRequestThrowDecodeFailures(createOrder, Some(baseUri))
      .apply(newOrder)
      .send(backend)
      .body
      .fold(error => throw new AssertionError(s"unexpected error $error"), identity)

    assertEquals(1L, created.id)
    assertEquals("ada@example.com", created.customer)
    assertEquals(3496L, created.totalCents)
    assertEquals(OrderStatus.Placed, created.status)
    assertEquals(newOrder.lines, created.lines)

    val fetched: Either[ApiError, Order] = client
      .toRequestThrowDecodeFailures(getOrder, Some(baseUri))
      .apply(created.id)
      .send(backend)
      .body

    assertEquals(Right(created), fetched)
  }

  @Test
  def reportsAMissingOrderAsAJsonErrorWithNotFoundStatus(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()

    val response: Response[Either[ApiError, Order]] = client
      .toRequestThrowDecodeFailures(getOrder, Some(baseUri))
      .apply(42L)
      .send(backend)

    assertEquals(StatusCode.NotFound, response.code)
    assertEquals(Left(ApiError("order-not-found", "No order with id 42")), response.body)
  }

  @Test
  def sendsTheDocumentedJsonShapeOnTheWire(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/orders")
      .contentType(MediaType.ApplicationJson)
      .body("""{"customer":"bob@example.com","lines":[{"sku":"sku-mouse","quantity":2,"unitCents":1250}]}""")
      .send(backend)

    assertEquals(StatusCode.Created, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("application/json")))
    val json: Json = parse(raw.body.fold(identity, identity)).fold(throw _, identity)
    val cursor: HCursor = json.hcursor
    assertEquals(Right(2500L), cursor.get[Long]("totalCents"))
    assertTrue(cursor.downField("status").downField("Placed").succeeded)
    assertEquals(Right(None), cursor.get[Option[String]]("note"))
  }

  @Test
  def rejectsAMalformedBodyBeforeTheBusinessLogicRuns(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .post(uri"$baseUri/orders")
      .contentType(MediaType.ApplicationJson)
      .body("""{"customer":"eve@example.com","lines":[{"sku":"sku-mouse","quantity":"many"}]}""")
      .send(backend)

    assertEquals(StatusCode.BadRequest, raw.code)
    assertEquals(0, store.size)
  }
}

object OrderApiJsonBodiesTest {
  final case class OrderLine(sku: String, quantity: Int, unitCents: Long) derives CirceCodec.AsObject, Schema

  final case class NewOrder(customer: String, lines: List[OrderLine], note: Option[String] = None)
      derives CirceCodec.AsObject, Schema

  enum OrderStatus derives CirceCodec.AsObject, Schema {
    case Placed, Shipped
  }

  final case class Order(
      id: Long,
      customer: String,
      lines: List[OrderLine],
      note: Option[String],
      totalCents: Long,
      status: OrderStatus
  ) derives CirceCodec.AsObject, Schema

  final case class ApiError(code: String, message: String) derives CirceCodec.AsObject, Schema

  val createOrder: PublicEndpoint[NewOrder, ApiError, Order, Any] = endpoint.post
    .in("orders")
    .in(jsonBody[NewOrder])
    .out(statusCode(StatusCode.Created))
    .out(jsonBody[Order])
    .errorOut(statusCode(StatusCode.BadRequest))
    .errorOut(jsonBody[ApiError])

  val getOrder: PublicEndpoint[Long, ApiError, Order, Any] = endpoint.get
    .in("orders" / path[Long]("orderId"))
    .out(jsonBody[Order])
    .errorOut(statusCode(StatusCode.NotFound))
    .errorOut(jsonBody[ApiError])

  /** In-memory order storage behind the two endpoints. */
  final class OrderStore {
    private val orders: ConcurrentHashMap[Long, Order] = new ConcurrentHashMap[Long, Order]()
    private val nextId: AtomicLong = new AtomicLong()

    def size: Int = orders.size()

    val serverEndpoints = List(
      createOrder.handle { newOrder =>
        if (newOrder.lines.isEmpty) Left(ApiError("empty-order", "An order needs at least one line"))
        else {
          val id: Long = nextId.incrementAndGet()
          val total: Long = newOrder.lines.map(line => line.quantity * line.unitCents).sum
          val order: Order = Order(id, newOrder.customer, newOrder.lines, newOrder.note, total, OrderStatus.Placed)
          orders.put(id, order)
          Right(order)
        }
      },
      getOrder.handle { id =>
        Option(orders.get(id)).toRight(ApiError("order-not-found", s"No order with id $id"))
      }
    )
  }
}
