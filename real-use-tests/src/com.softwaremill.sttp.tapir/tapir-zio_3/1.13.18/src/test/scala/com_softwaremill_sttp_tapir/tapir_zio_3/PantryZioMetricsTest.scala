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
import sttp.tapir.server.metrics.{EndpointMetric, Metric}
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.metrics.MetricLabel
import zio.{durationInt, Promise, RIO, Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Server metrics recorded into ZIO metrics through the `ZioMetrics` request interceptor.
 *
 * A pantry service registers `ZioMetrics.default` for its own namespace, adds a custom
 * per-endpoint call counter, and plugs both into the ZIO HTTP interpreter through
 * `ZioHttpServerOptions`, leaving its operations endpoint out of the measurements. The test
 * drives successful calls, typed errors, a body decode failure and a request held open by a
 * gate, then reads back what an operator would see: the in-flight gauge while the request is
 * held and after it completes, the request counters labelled by method, path template and
 * status class, the duration histogram for both response phases, and the custom counter as
 * reported by the operations endpoint.
 */
class PantryZioMetricsTest {
  import PantryZioMetricsTest.*

  @Test
  def requestsAreMeasuredIntoZioMetricsByTheInterceptor(): Unit = {
    val program: ZIO[Server & Env, Throwable, Observed] = for {
      port <- Server.install(ZioHttpInterpreter(serverOptions).toHttp(serverEndpoints))
      observed <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
    } yield observed

    val observed: Observed = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(
        program.provide(Server.defaultWithPort(0), InMemoryPantry.layer, ReservationDesk.layer)
      ).getOrThrowFiberFailure()
    }

    assertEquals(List(StatusCode.Created, StatusCode.Created, StatusCode.BadRequest), observed.additions)
    assertEquals(List(StatusCode.Ok, StatusCode.Ok, StatusCode.NotFound), observed.lookups)
    assertEquals(Item("apples", 6), decode[Item](observed.firstItem))

    assertEquals(1.0, observed.activeWhileHeld)
    assertEquals(Reply(StatusCode.Ok, """{"sku":"pears","quantity":3}"""), observed.reservation)
    assertEquals(0.0, observed.activeAfterRelease)

    assertEquals(expectedTotals, observed.totals)
    assertEquals(Map("headers" -> 2L, "body" -> 2L), observed.lookupDurations.view.mapValues(_.count).toMap)
    observed.lookupDurations.values.foreach { histogram =>
      assertTrue(histogram.min >= 0.0 && histogram.max >= histogram.min && histogram.sum >= 0.0)
    }

    assertEquals(StatusCode.Ok, observed.stats.code)
    assertEquals(
      Map("add-item" -> 3L, "get-item" -> 3L, "reserve-item" -> 1L),
      decode[Map[String, Long]](observed.stats.body)
    )
  }

  private def exercise(port: Int, backend: Backend[Task]): ZIO[ReservationDesk, Throwable, Observed] = {
    val base: String = s"http://localhost:$port"
    def send(request: Request[Either[String, String]]): Task[Reply] =
      request.send(backend).map(r => Reply(r.code, r.body.fold(identity, identity)))
    def add(body: String): Task[Reply] =
      send(basicRequest.post(uri"$base/items").contentType("application/json").body(body))
    val activePuts: UIO[Double] =
      ZioMetrics.getActiveRequestGauge(Namespace).tagged(MetricLabel("method", "PUT")).value.map(_.value)
    for {
      additions <- ZIO.foreach(List("""{"sku":"apples","quantity":6}""", """{"sku":"pears","quantity":3}""", """{"sku":7}"""))(add)
      lookups <- ZIO.foreach(List("apples", "pears", "plums"))(sku => send(basicRequest.get(uri"$base/items/$sku")))
      held <- send(basicRequest.put(uri"$base/items/pears/reservation")).fork
      activeWhileHeld <- poll(activePuts)(_ == 1.0)
      _ <- ZIO.serviceWithZIO[ReservationDesk](_.open)
      reservation <- held.join
      activeAfterRelease <- poll(activePuts)(_ == 0.0)
      totals <- poll(readTotals)(_ == expectedTotals)
      lookupDurations <- poll(readLookupDurations)(_.values.forall(_.count == 2L))
      stats <- send(basicRequest.get(uri"$base/ops/stats"))
    } yield Observed(
      additions.map(_.code),
      lookups.map(_.code),
      lookups.head.body,
      activeWhileHeld,
      reservation,
      activeAfterRelease,
      totals,
      lookupDurations,
      stats
    )
  }

  /** Re-reads a metric until it settles; response-body callbacks may finish after the reply. */
  private def poll[A](read: UIO[A])(done: A => Boolean): UIO[A] = {
    def attempt(remaining: Int): UIO[A] =
      read.flatMap(a => if (done(a) || remaining == 0) ZIO.succeed(a) else attempt(remaining - 1).delay(10.millis))
    attempt(1000)
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object PantryZioMetricsTest {
  val Namespace: String = "pantry"

  final case class Item(sku: String, quantity: Int) derives Schema
  final case class ItemNotFound(sku: String) derives Schema

  given JsonCodec[Item] = DeriveJsonCodec.gen[Item]
  given JsonCodec[ItemNotFound] = DeriveJsonCodec.gen[ItemNotFound]

  final case class Reply(code: StatusCode, body: String)

  final case class Histogram(count: Long, min: Double, max: Double, sum: Double)

  final case class Observed(
      additions: List[StatusCode],
      lookups: List[StatusCode],
      firstItem: String,
      activeWhileHeld: Double,
      reservation: Reply,
      activeAfterRelease: Double,
      totals: Map[(String, String, String), Long],
      lookupDurations: Map[String, Histogram],
      stats: Reply
  )

  /** The pantry stock the endpoint logic reads and writes. */
  trait Pantry {
    def put(item: Item): UIO[Item]
    def find(sku: String): UIO[Option[Item]]
  }

  final class InMemoryPantry(stock: Ref[Map[String, Item]]) extends Pantry {
    def put(item: Item): UIO[Item] = stock.update(_.updated(item.sku, item)).as(item)
    def find(sku: String): UIO[Option[Item]] = stock.get.map(_.get(sku))
  }

  object InMemoryPantry {
    val layer: ZLayer[Any, Nothing, Pantry] = ZLayer.fromZIO(Ref.make(Map.empty[String, Item]).map(new InMemoryPantry(_)))
  }

  /** Holds reservations until the desk opens, so a request stays in flight on demand. */
  final class ReservationDesk(gate: Promise[Nothing, Unit]) {
    def awaitOpen: UIO[Unit] = gate.await
    def open: UIO[Unit] = gate.succeed(()).unit
  }

  object ReservationDesk {
    val layer: ZLayer[Any, Nothing, ReservationDesk] = ZLayer.fromZIO(Promise.make[Nothing, Unit].map(new ReservationDesk(_)))
  }

  type Env = Pantry & ReservationDesk
  type AppTask[A] = RIO[Env, A]

  private val addItem =
    endpoint.post.in("items").in(jsonBody[Item]).out(statusCode(StatusCode.Created)).out(jsonBody[Item]).name("add-item")

  private val getItem =
    endpoint.get.in("items" / path[String]("sku")).errorOut(statusCode(StatusCode.NotFound).and(jsonBody[ItemNotFound]))
      .out(jsonBody[Item]).name("get-item")

  private val reserveItem =
    endpoint.put.in("items" / path[String]("sku") / "reservation")
      .errorOut(statusCode(StatusCode.NotFound).and(jsonBody[ItemNotFound])).out(jsonBody[Item]).name("reserve-item")

  private val opsStats = endpoint.get.in("ops" / "stats").out(jsonBody[Map[String, Long]]).name("ops-stats")

  /** The application's own metric: how often each named endpoint was invoked. */
  private val endpointCalls: zio.metrics.Metric.Counter[Long] = zio.metrics.Metric.counter(s"${Namespace}_endpoint_calls")

  private val endpointCallsMetric: Metric[AppTask, zio.metrics.Metric.Counter[Long]] =
    Metric[AppTask, zio.metrics.Metric.Counter[Long]](
      endpointCalls,
      onRequest = (_, counter, _) =>
        ZIO.succeed(EndpointMetric[AppTask]().onEndpointRequest { ep =>
          counter.tagged("endpoint", ep.info.name.getOrElse("anonymous")).increment
        })
    )

  private val metrics: ZioMetrics[AppTask] = ZioMetrics.default[AppTask](Namespace).addCustom(endpointCallsMetric)

  val serverOptions: ZioHttpServerOptions[Env] =
    ZioHttpServerOptions.customiseInterceptors[Env].metricsInterceptor(metrics.metricsInterceptor(Seq(opsStats))).options

  private def existing(sku: String): ZIO[Pantry, ItemNotFound, Item] =
    ZIO.serviceWithZIO[Pantry](_.find(sku)).someOrFail(ItemNotFound(sku))

  val serverEndpoints: List[ZServerEndpoint[Env, Any]] = List(
    addItem.zServerLogic[Env](item => ZIO.serviceWithZIO[Pantry](_.put(item))),
    getItem.zServerLogic[Env](existing),
    reserveItem.zServerLogic[Env](sku => ZIO.serviceWithZIO[ReservationDesk](_.awaitOpen) *> existing(sku)),
    opsStats.zServerLogic[Env] { _ =>
      ZIO.foreach(List("add-item", "get-item", "reserve-item", "ops-stats")) { name =>
        endpointCalls.tagged("endpoint", name).value.map(state => name -> state.count.toLong)
      }.map(_.filter(_._2 > 0).toMap)
    }
  )

  /** Request totals keyed by (method, path template, status class); the ignored endpoint stays at zero. */
  val expectedTotals: Map[(String, String, String), Long] = Map(
    ("POST", "/items", "2xx") -> 2L,
    ("POST", "/items", "4xx") -> 1L,
    ("GET", "/items/{sku}", "2xx") -> 2L,
    ("GET", "/items/{sku}", "4xx") -> 1L,
    ("PUT", "/items/{sku}/reservation", "2xx") -> 1L,
    ("GET", "/ops/stats", "2xx") -> 0L
  )

  val readTotals: UIO[Map[(String, String, String), Long]] =
    ZIO.foreach(expectedTotals.keys.toList) { case key @ (method, path, status) =>
      ZioMetrics.getRequestsTotalCounter(Namespace)
        .tagged(MetricLabel("method", method), MetricLabel("path", path), MetricLabel("status", status))
        .value.map(state => key -> state.count.toLong)
    }.map(_.toMap)

  val readLookupDurations: UIO[Map[String, Histogram]] =
    ZIO.foreach(List("headers", "body")) { phase =>
      ZioMetrics.getRequestDurationHistogram(Namespace)
        .tagged(MetricLabel("method", "GET"), MetricLabel("path", "/items/{sku}"), MetricLabel("status", "2xx"), MetricLabel("phase", phase))
        .value.map(state => phase -> Histogram(state.count, state.min, state.max, state.sum))
    }.map(_.toMap)
}
