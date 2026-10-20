/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.ParsingFailure
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.attributes.AttributeKey
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.listener.ListenerBackend
import sttp.client4.listener.RequestListener
import sttp.model.ResponseMetadata
import sttp.model.StatusCode
import sttp.shared.Identity

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Try

/**
 * Exercises request lifecycle instrumentation of circe JSON calls through `ListenerBackend` and
 * `RequestListener`: a stock-ledger client tags requests with operation attributes, and a listener
 * observes body receipt, handled responses carrying the circe failures `asJsonOrFail` raises, and
 * transport exceptions on the sync backend and, lifted, on the `Future` backend.
 */
class StockLedgerRequestListenerTest {
  private final case class Operation(name: String)
  private final case class StockLevel(sku: String, warehouse: String, onHand: Int)
  private final case class Adjustment(sku: String, delta: Int, reason: String)
  private final case class LedgerEntry(id: Long, sku: String, delta: Int, balance: Int)

  private final case class CallTag(sequence: Long, operation: String, correlationId: Option[String])

  private final case class LifecycleEvent(
      kind: String,
      sequence: Long,
      operation: String,
      status: Option[Int],
      failure: Option[String]
  )

  /** Records every lifecycle callback the way an application-side metrics collector would. */
  private final class LedgerCallRecorder extends RequestListener[Identity, CallTag] {
    private val sequences: AtomicLong = new AtomicLong(0L)
    val events: ConcurrentLinkedQueue[LifecycleEvent] = new ConcurrentLinkedQueue[LifecycleEvent]()

    override def before(request: GenericRequest[?, ?]): CallTag = {
      val operation: String = request.attribute(OperationKey).map(_.name).getOrElse("untagged")
      val tag: CallTag = CallTag(sequences.incrementAndGet(), operation, request.header("X-Correlation-Id"))
      events.add(LifecycleEvent("before", tag.sequence, tag.operation, None, None))
      tag
    }

    override def responseBodyReceived(request: GenericRequest[?, ?], response: ResponseMetadata, tag: CallTag): Unit =
      events.add(LifecycleEvent("bodyReceived", tag.sequence, tag.operation, Some(response.code.code), None))

    override def responseHandled(
        request: GenericRequest[?, ?],
        response: ResponseMetadata,
        tag: CallTag,
        exception: Option[ResponseException[?]]
    ): Unit = {
      val failure: Option[String] = exception.map {
        case ResponseException.DeserializationException(_, cause, _) => s"deserialization:${cause.getClass.getSimpleName}"
        case ResponseException.UnexpectedStatusCode(_, metadata) => s"status:${metadata.code.code}"
      }
      events.add(LifecycleEvent("handled", tag.sequence, tag.operation, Some(response.code.code), failure))
    }

    override def exception(
        request: GenericRequest[?, ?],
        tag: CallTag,
        e: Throwable,
        responseBodyReceivedCalled: Boolean
    ): Unit =
      events.add(
        LifecycleEvent(
          "exception",
          tag.sequence,
          tag.operation,
          None,
          Some(s"${e.getClass.getSimpleName}:bodyReceived=$responseBodyReceivedCalled")
        )
      )

    def recorded: List[LifecycleEvent] = events.asScala.toList

    def forOperation(operation: String): List[LifecycleEvent] = recorded.filter(_.operation == operation)
  }

  private val OperationKey: AttributeKey[Operation] = AttributeKey[Operation]

  private val levels: Map[String, StockLevel] = Map(
    "BOLT-10" -> StockLevel("BOLT-10", "north", 420),
    "NUT-8" -> StockLevel("NUT-8", "south", 75)
  )
  private val postedAdjustments: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
  private val correlationIds: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()

  private var serverExecutor: ExecutorService = null
  private var server: HttpServer = null
  private var recorder: LedgerCallRecorder = null
  private var backend: SyncBackend = null

  @BeforeEach
  def startServer(): Unit = {
    serverExecutor = Executors.newFixedThreadPool(4)
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.setExecutor(serverExecutor)
    server.createContext(
      "/stock/",
      (exchange: HttpExchange) => {
        try {
          Option(exchange.getRequestHeaders.getFirst("X-Correlation-Id")).foreach(correlationIds.add)
          exchange.getRequestURI.getPath.stripPrefix("/stock/") match {
            case "LEGACY-1" => respond(exchange, 200, """{"sku":"LEGACY-1","warehouse":""")
            case sku if levels.contains(sku) => respond(exchange, 200, levels(sku).asJson.noSpaces)
            case sku => respond(exchange, 404, s"""{"code":"UNKNOWN_SKU","sku":"$sku"}""")
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/ledger",
      (exchange: HttpExchange) => {
        try {
          val payload: String = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          postedAdjustments.add(payload)
          decode[Adjustment](payload) match {
            case Right(adjustment) =>
              val balance: Int = levels.get(adjustment.sku).map(_.onHand).getOrElse(0) + adjustment.delta
              respond(exchange, 201, LedgerEntry(7001L, adjustment.sku, adjustment.delta, balance).asJson.noSpaces)
            case Left(error) => respond(exchange, 400, error.getMessage)
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    recorder = new LedgerCallRecorder()
    backend = ListenerBackend(DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds)), recorder)
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
    serverExecutor.shutdownNow()
  }

  @Test
  def tracesTaggedJsonExchangesFromRequestToHandledResponse(): Unit = {
    val level: Response[Either[ResponseException[String], StockLevel]] = stockRequest("BOLT-10", "stock.read")
      .header("X-Correlation-Id", "corr-1")
      .send(backend)

    val entry: Response[LedgerEntry] = basicRequest
      .post(uri"http://127.0.0.1:$port/ledger")
      .attribute(OperationKey, Operation("ledger.adjust"))
      .body(asJson(Adjustment("BOLT-10", -20, "picking")))
      .response(asJsonOrFail[LedgerEntry])
      .readTimeout(10.seconds)
      .send(backend)

    assertThat(level.body).isEqualTo(Right(levels("BOLT-10")))
    assertThat(entry.code).isEqualTo(StatusCode.Created)
    assertThat(entry.body).isEqualTo(LedgerEntry(7001L, "BOLT-10", -20, 400))
    assertThat(decode[Adjustment](postedAdjustments.asScala.toList.head))
      .isEqualTo(Right(Adjustment("BOLT-10", -20, "picking")))
    assertThat(correlationIds.asScala.toList).isEqualTo(List("corr-1"))

    assertThat(recorder.recorded).isEqualTo(
      List(
        LifecycleEvent("before", 1L, "stock.read", None, None),
        LifecycleEvent("bodyReceived", 1L, "stock.read", Some(200), None),
        LifecycleEvent("handled", 1L, "stock.read", Some(200), None),
        LifecycleEvent("before", 2L, "ledger.adjust", None, None),
        LifecycleEvent("bodyReceived", 2L, "ledger.adjust", Some(201), None),
        LifecycleEvent("handled", 2L, "ledger.adjust", Some(201), None)
      )
    )
  }

  @Test
  def seesFailuresKeptAsValuesAsPlainHandledResponses(): Unit = {
    val malformed: Response[Either[ResponseException[String], StockLevel]] =
      stockRequest("LEGACY-1", "stock.legacy").send(backend)
    val missing: Response[Either[ResponseException[String], StockLevel]] =
      stockRequest("GHOST-0", "stock.missing").send(backend)

    malformed.body match {
      case Left(ResponseException.DeserializationException(body, cause, metadata)) =>
        assertThat(body).isEqualTo("""{"sku":"LEGACY-1","warehouse":""")
        assertThat(cause).isInstanceOf(classOf[ParsingFailure])
        assertThat(metadata.code).isEqualTo(StatusCode.Ok)
      case other => throw new AssertionError(s"Expected a circe deserialization failure, got $other")
    }
    missing.body match {
      case Left(ResponseException.UnexpectedStatusCode(body, metadata)) =>
        assertThat(body).isEqualTo("""{"code":"UNKNOWN_SKU","sku":"GHOST-0"}""")
        assertThat(metadata.code).isEqualTo(StatusCode.NotFound)
      case other => throw new AssertionError(s"Expected an unexpected-status failure, got $other")
    }

    assertThat(recorder.forOperation("stock.legacy").last)
      .isEqualTo(LifecycleEvent("handled", 1L, "stock.legacy", Some(200), None))
    assertThat(recorder.forOperation("stock.missing").last)
      .isEqualTo(LifecycleEvent("handled", 2L, "stock.missing", Some(404), None))
    assertThat(recorder.recorded.count(_.kind == "exception")).isEqualTo(0)
  }

  @Test
  def reportsRaisedJsonFailuresAsHandledAndTransportErrorsAsExceptions(): Unit = {
    val strict: Try[Response[StockLevel]] = Try(strictStockRequest("LEGACY-1", "stock.strict").send(backend))
    val strictMissing: Try[Response[StockLevel]] =
      Try(strictStockRequest("GHOST-0", "stock.strictMissing").send(backend))
    val unreachablePort: Int = closedPort()
    val unreachable: Try[Response[Either[ResponseException[String], StockLevel]]] = Try(
      basicRequest
        .get(uri"http://127.0.0.1:$unreachablePort/stock/BOLT-10")
        .attribute(OperationKey, Operation("stock.offline"))
        .response(asJson[StockLevel])
        .send(backend)
    )

    assertThat(strict.failed.get).isInstanceOf(classOf[SttpClientException.ResponseHandlingException[?]])
    assertThat(strict.failed.get.getCause).isInstanceOf(classOf[ResponseException.DeserializationException])
    assertThat(strictMissing.failed.get.getCause).isInstanceOf(classOf[ResponseException.UnexpectedStatusCode[?]])
    assertThat(unreachable.failed.get).isInstanceOf(classOf[SttpClientException.ConnectException])

    assertThat(recorder.forOperation("stock.strict")).isEqualTo(
      List(
        LifecycleEvent("before", 1L, "stock.strict", None, None),
        LifecycleEvent("bodyReceived", 1L, "stock.strict", Some(200), None),
        LifecycleEvent("handled", 1L, "stock.strict", Some(200), Some("deserialization:ParsingFailure"))
      )
    )
    assertThat(recorder.forOperation("stock.strictMissing").last)
      .isEqualTo(LifecycleEvent("handled", 2L, "stock.strictMissing", Some(404), Some("status:404")))
    assertThat(recorder.forOperation("stock.offline"))
      .isEqualTo(
        List(
          LifecycleEvent("before", 3L, "stock.offline", None, None),
          LifecycleEvent("exception", 3L, "stock.offline", None, Some("ConnectException:bodyReceived=false"))
        )
      )
  }

  @Test
  def liftsSynchronousListenerOntoTheFutureBackend(): Unit = {
    val clientExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    given ExecutionContext = ExecutionContext.fromExecutorService(clientExecutor)
    val futureRecorder: LedgerCallRecorder = new LedgerCallRecorder()
    val futureBackend: Backend[Future] =
      ListenerBackend.lift(DefaultFutureBackend(BackendOptions.connectionTimeout(10.seconds)), futureRecorder)
    try {
      val lookups: Future[List[Either[ResponseException[String], StockLevel]]] =
        Future.traverse(List("BOLT-10", "NUT-8", "GHOST-0")) { sku =>
          stockRequest(sku, s"stock.async.$sku").send(futureBackend).map(_.body)
        }
      val results: List[Either[ResponseException[String], StockLevel]] = Await.result(lookups, 20.seconds)

      assertThat(results.take(2)).isEqualTo(List(Right(levels("BOLT-10")), Right(levels("NUT-8"))))
      assertThat(results(2).isLeft).isTrue()

      val events: List[LifecycleEvent] = futureRecorder.recorded
      assertThat(events.map(_.sequence).toSet).isEqualTo(Set(1L, 2L, 3L))
      List("BOLT-10", "NUT-8", "GHOST-0").foreach { sku =>
        val perCall: List[LifecycleEvent] = futureRecorder.forOperation(s"stock.async.$sku")
        assertThat(perCall.map(_.kind)).isEqualTo(List("before", "bodyReceived", "handled"))
        assertThat(perCall.map(_.sequence).distinct.size).isEqualTo(1)
      }
      assertThat(futureRecorder.forOperation("stock.async.GHOST-0").last.status).isEqualTo(Some(404))
      assertThat(recorder.recorded.isEmpty).isTrue()
    } finally {
      Await.result(futureBackend.close(), 10.seconds)
      clientExecutor.shutdownNow()
    }
  }

  private def stockRequest(sku: String, operation: String): Request[Either[ResponseException[String], StockLevel]] =
    basicRequest
      .get(uri"http://127.0.0.1:$port/stock/$sku")
      .attribute(OperationKey, Operation(operation))
      .response(asJson[StockLevel])
      .readTimeout(10.seconds)

  private def strictStockRequest(sku: String, operation: String): Request[StockLevel] =
    basicRequest
      .get(uri"http://127.0.0.1:$port/stock/$sku")
      .attribute(OperationKey, Operation(operation))
      .response(asJsonOrFail[StockLevel])
      .readTimeout(10.seconds)

  private def closedPort(): Int = {
    val socket: ServerSocket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }

  private def port: Int = server.getAddress.getPort

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
