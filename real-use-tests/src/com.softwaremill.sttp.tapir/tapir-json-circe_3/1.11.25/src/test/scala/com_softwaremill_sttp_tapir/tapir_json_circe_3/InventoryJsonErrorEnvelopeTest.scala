/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import io.circe.parser.decode
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
import sttp.tapir.server.interceptor.exception.ExceptionContext
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler
import sttp.tapir.server.jdkhttp.JdkHttpServer
import sttp.tapir.server.jdkhttp.JdkHttpServerOptions
import sttp.tapir.server.model.ValuedEndpointOutput

import java.util.concurrent.CopyOnWriteArrayList
import scala.jdk.CollectionConverters.*

/**
 * Server exception and rejection handling rendered as circe JSON error envelopes: an application
 * exception handler and reject handler answer with `jsonBody` outputs on a live JDK HTTP server,
 * and the sttp client interpreter decodes them through the endpoints' JSON error outputs.
 *
 * An inventory service promises its callers one error shape for every failure. Business errors
 * come from the endpoint logic, a crashed warehouse link and any other unexpected exception are
 * turned into 503 and 500 envelopes carrying the caller's request id, and requests that match no
 * route or use the wrong method get 404 and 405 envelopes instead of tapir's plain-text defaults.
 */
class InventoryJsonErrorEnvelopeTest {
  import InventoryJsonErrorEnvelopeTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val inventory: Inventory = new Inventory

  @BeforeEach
  def startServer(): Unit = {
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .exceptionHandler(ExceptionHandler.pure(inventory.envelopeForException))
      .rejectHandler(DefaultRejectHandler(rejectionEnvelope, Some((StatusCode.NotFound, "Not Found"))))
      .options
    server = JdkHttpServer()
      .options(options)
      .host("localhost")
      .port(0)
      .addEndpoints(inventory.serverEndpoints)
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
  def reservesStockAndReportsBusinessErrorsInTheEnvelope(): Unit = {
    val client: SttpClientInterpreter = SttpClientInterpreter()

    val reserved: Either[(StatusCode, ApiError), StockLevel] = client
      .toRequestThrowDecodeFailures(reserve, Some(baseUri))
      .apply(("req-1", "SKU-42", Reservation(quantity = 3, warehouse = "berlin")))
      .send(backend)
      .body
    assertEquals(Right(StockLevel("SKU-42", available = 7, reserved = 3)), reserved)

    val tooMany: Response[Either[(StatusCode, ApiError), StockLevel]] = client
      .toRequestThrowDecodeFailures(reserve, Some(baseUri))
      .apply(("req-2", "SKU-42", Reservation(quantity = 8, warehouse = "berlin")))
      .send(backend)
    assertEquals(StatusCode.Conflict, tooMany.code)
    assertEquals(
      Left((StatusCode.Conflict, ApiError("insufficient-stock", "Only 7 of SKU-42 left", Some("req-2")))),
      tooMany.body
    )

    val level: Either[(StatusCode, ApiError), StockLevel] = client
      .toRequestThrowDecodeFailures(stockLevel, Some(baseUri))
      .apply(("req-3", "SKU-42"))
      .send(backend)
      .body
    assertEquals(Right(StockLevel("SKU-42", available = 7, reserved = 3)), level)
    assertTrue(inventory.incidents.isEmpty)
  }

  @Test
  def turnsAWarehouseOutageIntoAServiceUnavailableEnvelope(): Unit = {
    val response: Response[Either[(StatusCode, ApiError), StockLevel]] = SttpClientInterpreter()
      .toRequestThrowDecodeFailures(reserve, Some(baseUri))
      .apply(("req-outage", "SKU-42", Reservation(quantity = 1, warehouse = "lisbon")))
      .send(backend)

    assertEquals(StatusCode.ServiceUnavailable, response.code)
    assertEquals(
      Left(
        (
          StatusCode.ServiceUnavailable,
          ApiError("warehouse-unavailable", "Warehouse lisbon is not reachable", Some("req-outage"))
        )
      ),
      response.body
    )
    assertEquals(List(Incident("req-outage", "WarehouseUnavailable")), inventory.incidents)
    assertEquals(StockLevel("SKU-42", available = 10, reserved = 0), inventory.levelOf("SKU-42"))
  }

  @Test
  def hidesUnexpectedExceptionsBehindAGenericEnvelope(): Unit = {
    val response: Response[Either[String, String]] = basicRequest
      .get(uri"$baseUri/items/CORRUPT-1/stock")
      .header("X-Request-Id", "req-crash")
      .send(backend)

    assertEquals(StatusCode.InternalServerError, response.code)
    assertTrue(response.header("Content-Type").exists(_.startsWith("application/json")))
    assertEquals(
      """{"code":"internal-error","message":"Something went wrong","requestId":"req-crash"}""",
      response.body.fold(identity, identity)
    )
    assertEquals(List(Incident("req-crash", "IllegalStateException")), inventory.incidents)
  }

  @Test
  def answersUnknownRoutesWithANotFoundEnvelope(): Unit = {
    val response: Response[Either[(StatusCode, ApiError), List[String]]] = SttpClientInterpreter()
      .toRequestThrowDecodeFailures(itemHistory, Some(baseUri))
      .apply(("req-404", "SKU-42"))
      .send(backend)

    assertEquals(StatusCode.NotFound, response.code)
    assertEquals(Left((StatusCode.NotFound, ApiError("not-found", "Not Found", None))), response.body)

    val raw: Response[Either[String, String]] = basicRequest.get(uri"$baseUri/warehouses").send(backend)
    assertEquals(StatusCode.NotFound, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("application/json")))
    assertEquals(Right(ApiError("not-found", "Not Found", None)), decode[ApiError](raw.body.fold(identity, identity)))
  }

  @Test
  def answersAWrongMethodWithAMethodNotAllowedEnvelope(): Unit = {
    val response: Response[Either[String, String]] = basicRequest
      .delete(uri"$baseUri/items/SKU-42/reservations")
      .contentType(MediaType.ApplicationJson)
      .body("""{"quantity":1,"warehouse":"berlin"}""")
      .send(backend)

    assertEquals(StatusCode.MethodNotAllowed, response.code)
    assertEquals(
      Right(ApiError("method-not-allowed", "Method Not Allowed", None)),
      decode[ApiError](response.body.fold(identity, identity))
    )
    assertEquals(StockLevel("SKU-42", available = 10, reserved = 0), inventory.levelOf("SKU-42"))
  }
}

object InventoryJsonErrorEnvelopeTest {

  final case class Reservation(quantity: Int, warehouse: String) derives CirceCodec.AsObject, Schema

  final case class StockLevel(sku: String, available: Int, reserved: Int) derives CirceCodec.AsObject, Schema

  final case class ApiError(code: String, message: String, requestId: Option[String])
      derives CirceCodec.AsObject, Schema

  final case class Incident(requestId: String, exception: String)

  final class WarehouseUnavailable(val warehouse: String) extends RuntimeException(s"$warehouse is down")

  val RequestIdHeader: String = "X-Request-Id"

  /** The one error shape every failure of the service is written in. */
  val envelope: EndpointOutput[(StatusCode, ApiError)] = statusCode.and(jsonBody[ApiError])

  val reserve: Endpoint[Unit, (String, String, Reservation), (StatusCode, ApiError), StockLevel, Any] = endpoint.post
    .in(header[String](RequestIdHeader))
    .in("items" / path[String]("sku") / "reservations")
    .in(jsonBody[Reservation])
    .errorOut(envelope)
    .out(jsonBody[StockLevel])

  val stockLevel: Endpoint[Unit, (String, String), (StatusCode, ApiError), StockLevel, Any] = endpoint.get
    .in(header[String](RequestIdHeader))
    .in("items" / path[String]("sku") / "stock")
    .errorOut(envelope)
    .out(jsonBody[StockLevel])

  /** A route the client knows about but the service does not serve yet. */
  val itemHistory: Endpoint[Unit, (String, String), (StatusCode, ApiError), List[String], Any] = endpoint.get
    .in(header[String](RequestIdHeader))
    .in("items" / path[String]("sku") / "history")
    .errorOut(envelope)
    .out(jsonBody[List[String]])

  def rejectionEnvelope(code: StatusCode, message: String): ValuedEndpointOutput[?] = {
    val kind: String = if (code == StatusCode.MethodNotAllowed) "method-not-allowed" else "not-found"
    ValuedEndpointOutput(envelope, (code, ApiError(kind, message, None)))
  }

  /** In-memory stock per SKU; the `lisbon` warehouse link is down and `CORRUPT-` SKUs are broken. */
  final class Inventory {
    private val OfflineWarehouses: Set[String] = Set("lisbon")
    private var levels: Map[String, StockLevel] = Map("SKU-42" -> StockLevel("SKU-42", available = 10, reserved = 0))
    private val recorded: CopyOnWriteArrayList[Incident] = new CopyOnWriteArrayList[Incident]()

    def incidents: List[Incident] = recorded.asScala.toList

    def levelOf(sku: String): StockLevel = synchronized(levels(sku))

    def envelopeForException(context: ExceptionContext): Option[ValuedEndpointOutput[?]] = {
      val requestId: Option[String] = context.request.header(RequestIdHeader)
      recorded.add(Incident(requestId.getOrElse("unknown"), context.e.getClass.getSimpleName))
      context.e match {
        case outage: WarehouseUnavailable =>
          val error: ApiError =
            ApiError("warehouse-unavailable", s"Warehouse ${outage.warehouse} is not reachable", requestId)
          Some(ValuedEndpointOutput(envelope, (StatusCode.ServiceUnavailable, error)))
        case _ =>
          val error: ApiError = ApiError("internal-error", "Something went wrong", requestId)
          Some(ValuedEndpointOutput(envelope, (StatusCode.InternalServerError, error)))
      }
    }

    private def lookUp(sku: String): StockLevel = {
      if (sku.startsWith("CORRUPT-")) throw new IllegalStateException(s"Stock record for $sku is corrupt")
      levels.getOrElse(sku, StockLevel(sku, available = 0, reserved = 0))
    }

    val serverEndpoints = List(
      reserve.handle { (requestId, sku, reservation) =>
        if (OfflineWarehouses.contains(reservation.warehouse)) throw new WarehouseUnavailable(reservation.warehouse)
        synchronized {
          val current: StockLevel = lookUp(sku)
          if (reservation.quantity > current.available)
            Left((StatusCode.Conflict, ApiError("insufficient-stock", s"Only ${current.available} of $sku left", Some(requestId))))
          else {
            val updated: StockLevel = current.copy(
              available = current.available - reservation.quantity,
              reserved = current.reserved + reservation.quantity
            )
            levels = levels.updated(sku, updated)
            Right(updated)
          }
        }
      },
      stockLevel.handle { (_, sku) => synchronized(Right(lookUp(sku))) }
    )
  }
}
