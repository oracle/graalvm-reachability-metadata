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
import sttp.client4.*
import sttp.client4.circe.*
import sttp.model.StatusCode

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Failure

/**
 * Exercises circe JSON handling over the asynchronous `Future` backend: a dashboard service fans out
 * concurrent `asJsonOrFail` lookups, chains them in for-comprehensions, posts a circe-serialized
 * summary, and recovers typed `ResponseException` failures carried by failed futures.
 */
class DashboardFutureBackendTest {
  private final case class User(id: Int, name: String, tier: String)
  private final case class Order(id: String, userId: Int, cents: Long)
  private final case class UserSummary(user: User, orderCount: Int, totalCents: Long)
  private final case class Dashboard(generatedBy: String, summaries: List[UserSummary])
  private final case class Receipt(dashboardId: String, rows: Int)
  private final case class ApiError(code: String, message: String)

  private val users: Map[Int, User] = Map(
    1 -> User(1, "Ada", "gold"),
    2 -> User(2, "Grace", "silver"),
    3 -> User(3, "Barbara", "gold")
  )

  private val orders: List[Order] = List(
    Order("o-1", 1, 1250L),
    Order("o-2", 1, 870L),
    Order("o-3", 3, 4999L),
    Order("o-4", 1, 15L)
  )

  private val receivedDashboards: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()

  private var serverExecutor: ExecutorService = null
  private var server: HttpServer = null
  private val clientExecutor: ExecutorService = Executors.newFixedThreadPool(4)
  private var backend: Backend[Future] = null

  private given ExecutionContext = ExecutionContext.fromExecutorService(clientExecutor)

  @BeforeEach
  def startServer(): Unit = {
    serverExecutor = Executors.newFixedThreadPool(4)
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.setExecutor(serverExecutor)
    server.createContext(
      "/users/",
      (exchange: HttpExchange) => {
        try {
          exchange.getRequestURI.getPath.stripPrefix("/users/").split('/').toList match {
            case "corrupt" :: Nil =>
              respond(exchange, 200, """{"id":9,"name":"Trunc""")
            case id :: Nil if users.contains(id.toInt) =>
              respond(exchange, 200, users(id.toInt).asJson.noSpaces)
            case id :: "orders" :: Nil =>
              respond(exchange, 200, orders.filter(_.userId == id.toInt).asJson.noSpaces)
            case other =>
              respond(exchange, 404, ApiError("USER_NOT_FOUND", s"No user ${other.mkString("/")}").asJson.noSpaces)
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/dashboards",
      (exchange: HttpExchange) => {
        try {
          val body: String = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          receivedDashboards.add(body)
          val rows: Int = decode[Dashboard](body).fold(error => throw error, _.summaries.size)
          respond(exchange, 201, Receipt(s"d-$rows", rows).asJson.noSpaces)
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    backend = DefaultFutureBackend(BackendOptions.connectionTimeout(10.seconds))
  }

  @AfterEach
  def stopServer(): Unit = {
    Await.result(backend.close(), 10.seconds)
    server.stop(0)
    serverExecutor.shutdownNow()
    clientExecutor.shutdownNow()
  }

  @Test
  def fansOutConcurrentLookupsAndPostsSummary(): Unit = {
    val pipeline: Future[(Dashboard, Receipt)] = for {
      summaries <- Future.traverse(List(1, 2, 3))(summarize)
      dashboard = Dashboard("future-dashboard", summaries)
      receipt <- publish(dashboard)
    } yield (dashboard, receipt)

    val (dashboard, receipt): (Dashboard, Receipt) = Await.result(pipeline, 20.seconds)

    assertThat(dashboard.summaries.map(_.user.name)).isEqualTo(List("Ada", "Grace", "Barbara"))
    assertThat(dashboard.summaries.map(_.orderCount)).isEqualTo(List(3, 0, 1))
    assertThat(dashboard.summaries.map(_.totalCents)).isEqualTo(List(2135L, 0L, 4999L))
    assertThat(receipt).isEqualTo(Receipt("d-3", 3))
    val posted: List[String] = receivedDashboards.asScala.toList
    assertThat(posted.size).isEqualTo(1)
    assertThat(decode[Dashboard](posted.head)).isEqualTo(Right(dashboard))
  }

  @Test
  def failedFuturesCarryTypedResponseExceptions(): Unit = {
    responseFailure(fetchUser("42")) match {
      case ResponseException.UnexpectedStatusCode(body: String, metadata) =>
        assertThat(metadata.code).isEqualTo(StatusCode.NotFound)
        assertThat(decode[ApiError](body)).isEqualTo(Right(ApiError("USER_NOT_FOUND", "No user 42")))
      case other => throw new AssertionError(s"Expected an unexpected-status failure, got $other")
    }

    responseFailure(fetchUser("corrupt")) match {
      case ResponseException.DeserializationException(body, cause, metadata) =>
        assertThat(metadata.code).isEqualTo(StatusCode.Ok)
        assertThat(body).isEqualTo("""{"id":9,"name":"Trunc""")
        assertThat(cause).isInstanceOf(classOf[ParsingFailure])
      case other => throw new AssertionError(s"Expected a deserialization failure, got $other")
    }

    val withFallback: Future[List[String]] = Future.traverse(List("1", "42", "3")) { id =>
      fetchUser(id).map(_.name).recover {
        case failure: SttpClientException.ResponseHandlingException[?]
            if failure.getCause.isInstanceOf[ResponseException.UnexpectedStatusCode[?]] =>
          "<unknown>"
      }
    }
    assertThat(Await.result(withFallback, 20.seconds)).isEqualTo(List("Ada", "<unknown>", "Barbara"))
  }

  @Test
  def keepsErrorsAsValuesWithAsJsonEither(): Unit = {
    val lookups: Future[List[Either[ResponseException[ApiError], User]]] =
      Future.traverse(List("2", "7")) { id =>
        basicRequest
          .get(uri"http://127.0.0.1:${port}/users/$id")
          .response(asJsonEither[ApiError, User])
          .readTimeout(10.seconds)
          .send(backend)
          .map(_.body)
      }

    val results: List[Either[ResponseException[ApiError], User]] = Await.result(lookups, 20.seconds)

    assertThat(results.head).isEqualTo(Right(users(2)))
    results(1) match {
      case Left(ResponseException.UnexpectedStatusCode(error, metadata)) =>
        assertThat(metadata.code).isEqualTo(StatusCode.NotFound)
        assertThat(error).isEqualTo(ApiError("USER_NOT_FOUND", "No user 7"))
      case other => throw new AssertionError(s"Expected a typed API error, got $other")
    }
  }

  private def summarize(id: Int): Future[UserSummary] = {
    val userFuture: Future[User] = fetchUser(id.toString)
    val ordersFuture: Future[List[Order]] = basicRequest
      .get(uri"http://127.0.0.1:${port}/users/$id/orders")
      .response(asJsonOrFail[List[Order]])
      .readTimeout(10.seconds)
      .send(backend)
      .map(_.body)
    for {
      user <- userFuture
      userOrders <- ordersFuture
    } yield UserSummary(user, userOrders.size, userOrders.map(_.cents).sum)
  }

  private def fetchUser(id: String): Future[User] =
    basicRequest
      .get(uri"http://127.0.0.1:${port}/users/$id")
      .response(asJsonOrFail[User])
      .readTimeout(10.seconds)
      .send(backend)
      .map(_.body)

  private def publish(dashboard: Dashboard): Future[Receipt] =
    basicRequest
      .post(uri"http://127.0.0.1:${port}/dashboards")
      .body(asJson(dashboard))
      .response(asJsonOrFail[Receipt])
      .readTimeout(10.seconds)
      .send(backend)
      .map(_.body)

  /** Awaits a future the response handler failed and returns the `ResponseException` it wraps. */
  private def responseFailure(future: Future[User]): Throwable =
    Await.ready(future, 10.seconds).value.get match {
      case Failure(failure: SttpClientException.ResponseHandlingException[?]) => failure.getCause
      case other => throw new AssertionError(s"Expected a response handling failure, got $other")
    }

  private def port: Int = server.getAddress.getPort

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
