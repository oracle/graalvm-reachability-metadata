/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.StatusCode
import sttp.tapir.Schema
import sttp.tapir.json.zio.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.{Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Typed ZIO server logic (`zServerLogic`) that reads its service from the ZIO environment.
 *
 * An order desk exposes create, fetch and list endpoints whose logic is written as `ZIO`
 * effects requiring an `OrderRepository` service, provided through a `ZLayer`. The endpoints
 * are served by the ZIO HTTP interpreter on a real socket and called over HTTP with an sttp
 * client. The test checks that JSON bodies reach the logic and come back encoded, that typed
 * `ZIO.fail` errors are mapped to their declared status codes and JSON error bodies through
 * `oneOf` variants, that query parameters filter the listing, and that the repository state
 * written by one request is seen by later ones.
 */
class OrderDeskZioServerLogicTest {
  import OrderDeskZioServerLogicTest.*

  @Test
  def ordersFlowThroughZioLogicThatUsesTheProvidedRepository(): Unit = {
    val program: ZIO[Server & OrderRepository, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter().toHttp(serverEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
    } yield observed

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(program.provide(Server.defaultWithPort(0), InMemoryOrderRepository.layer)).getOrThrowFiberFailure()
    }

    assertEquals(StatusCode.Created, observed.created.code)
    val firstOrder: Order = decode[Order](observed.created.body)
    assertEquals(Order(1L, "Ada", List(Line("SKU-1", 2), Line("SKU-7", 1)), "open"), firstOrder)

    assertEquals(StatusCode.Created, observed.second.code)
    assertEquals(2L, decode[Order](observed.second.body).id)

    assertEquals(StatusCode.BadRequest, observed.invalid.code)
    assertEquals(
      InvalidOrder("an order needs at least one line with a positive quantity"),
      decode[InvalidOrder](observed.invalid.body)
    )

    assertEquals(StatusCode.Ok, observed.fetched.code)
    assertEquals(firstOrder, decode[Order](observed.fetched.body))

    assertEquals(StatusCode.NotFound, observed.missing.code)
    assertEquals(OrderNotFound(404L), decode[OrderNotFound](observed.missing.body))

    assertEquals(StatusCode.Ok, observed.cancelled.code)
    assertEquals("cancelled", decode[Order](observed.cancelled.body).status)

    assertEquals(StatusCode.Ok, observed.openOrders.code)
    assertEquals(List(2L), decode[List[Order]](observed.openOrders.body).map(_.id))

    assertEquals(StatusCode.Ok, observed.allOrders.code)
    assertEquals(List(1L, 2L), decode[List[Order]](observed.allOrders.body).map(_.id))

    assertEquals(StatusCode.BadRequest, observed.badBody.code)
    assertTrue(observed.badBody.body.nonEmpty)
  }

  private def exercise(port: Int, backend: Backend[Task]): Task[Observed] = {
    val base: String = s"http://localhost:$port"
    def send(request: Request[Either[String, String]]): Task[Reply] =
      request.send(backend).map(r => Reply(r.code, r.body.fold(identity, identity)))
    val json: String = "application/json"
    for {
      created <- send(basicRequest.post(uri"$base/orders").contentType(json)
        .body("""{"customer":"Ada","lines":[{"sku":"SKU-1","quantity":2},{"sku":"SKU-7","quantity":1}]}"""))
      second <- send(basicRequest.post(uri"$base/orders").contentType(json)
        .body("""{"customer":"Grace","lines":[{"sku":"SKU-3","quantity":5}]}"""))
      invalid <- send(basicRequest.post(uri"$base/orders").contentType(json)
        .body("""{"customer":"Linus","lines":[{"sku":"SKU-3","quantity":0}]}"""))
      fetched <- send(basicRequest.get(uri"$base/orders/1"))
      missing <- send(basicRequest.get(uri"$base/orders/404"))
      cancelled <- send(basicRequest.post(uri"$base/orders/1/cancel"))
      openOrders <- send(basicRequest.get(uri"$base/orders?status=open"))
      allOrders <- send(basicRequest.get(uri"$base/orders"))
      badBody <- send(basicRequest.post(uri"$base/orders").contentType(json).body("""{"customer":42}"""))
    } yield Observed(created, second, invalid, fetched, missing, cancelled, openOrders, allOrders, badBody)
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object OrderDeskZioServerLogicTest {
  final case class Line(sku: String, quantity: Int) derives Schema
  final case class CreateOrder(customer: String, lines: List[Line]) derives Schema
  final case class Order(id: Long, customer: String, lines: List[Line], status: String) derives Schema

  sealed trait OrderError
  final case class InvalidOrder(reason: String) extends OrderError derives Schema
  final case class OrderNotFound(id: Long) extends OrderError derives Schema

  given JsonCodec[Line] = DeriveJsonCodec.gen[Line]
  given JsonCodec[CreateOrder] = DeriveJsonCodec.gen[CreateOrder]
  given JsonCodec[Order] = DeriveJsonCodec.gen[Order]
  given JsonCodec[InvalidOrder] = DeriveJsonCodec.gen[InvalidOrder]
  given JsonCodec[OrderNotFound] = DeriveJsonCodec.gen[OrderNotFound]

  final case class Reply(code: StatusCode, body: String)

  final case class Observed(
      created: Reply,
      second: Reply,
      invalid: Reply,
      fetched: Reply,
      missing: Reply,
      cancelled: Reply,
      openOrders: Reply,
      allOrders: Reply,
      badBody: Reply
  )

  /** The service the endpoint logic requires from the ZIO environment. */
  trait OrderRepository {
    def create(request: CreateOrder): UIO[Order]
    def find(id: Long): UIO[Option[Order]]
    def update(order: Order): UIO[Order]
    def all: UIO[List[Order]]
  }

  final class InMemoryOrderRepository(orders: Ref[Map[Long, Order]], ids: Ref[Long]) extends OrderRepository {
    def create(request: CreateOrder): UIO[Order] = for {
      id <- ids.updateAndGet(_ + 1)
      order = Order(id, request.customer, request.lines, "open")
      _ <- orders.update(_.updated(id, order))
    } yield order

    def find(id: Long): UIO[Option[Order]] = orders.get.map(_.get(id))

    def update(order: Order): UIO[Order] = orders.update(_.updated(order.id, order)).as(order)

    def all: UIO[List[Order]] = orders.get.map(_.values.toList.sortBy(_.id))
  }

  object InMemoryOrderRepository {
    val layer: ZLayer[Any, Nothing, OrderRepository] = ZLayer.fromZIO(for {
      orders <- Ref.make(Map.empty[Long, Order])
      ids <- Ref.make(0L)
    } yield new InMemoryOrderRepository(orders, ids))
  }

  private val orderErrors = oneOf[OrderError](
    oneOfVariant(StatusCode.BadRequest, jsonBody[InvalidOrder]),
    oneOfVariant(StatusCode.NotFound, jsonBody[OrderNotFound])
  )

  private val createOrder =
    endpoint.post.in("orders").in(jsonBody[CreateOrder]).errorOut(orderErrors)
      .out(statusCode(StatusCode.Created)).out(jsonBody[Order])

  private val getOrder =
    endpoint.get.in("orders" / path[Long]("id")).errorOut(orderErrors).out(jsonBody[Order])

  private val cancelOrder =
    endpoint.post.in("orders" / path[Long]("id") / "cancel").errorOut(orderErrors).out(jsonBody[Order])

  private val listOrders =
    endpoint.get.in("orders").in(query[Option[String]]("status")).out(jsonBody[List[Order]])

  private def existing(id: Long): ZIO[OrderRepository, OrderError, Order] =
    ZIO.serviceWithZIO[OrderRepository](_.find(id)).someOrFail(OrderNotFound(id))

  val serverEndpoints: List[ZServerEndpoint[OrderRepository, Any]] = List(
    createOrder.zServerLogic[OrderRepository] { request =>
      if (request.lines.isEmpty || request.lines.exists(_.quantity <= 0))
        ZIO.fail(InvalidOrder("an order needs at least one line with a positive quantity"))
      else ZIO.serviceWithZIO[OrderRepository](_.create(request))
    },
    getOrder.zServerLogic[OrderRepository](existing),
    cancelOrder.zServerLogic[OrderRepository] { id =>
      existing(id).flatMap(order => ZIO.serviceWithZIO[OrderRepository](_.update(order.copy(status = "cancelled"))))
    },
    listOrders.zServerLogic[OrderRepository] { status =>
      ZIO.serviceWithZIO[OrderRepository](_.all).map(orders => orders.filter(o => status.forall(_ == o.status)))
    }
  )
}
