/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import cats.MonadThrow
import cats.arrow.FunctionK
import cats.syntax.all.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.Error
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
import sttp.client4.impl.cats.implicits.*
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.monad.EitherMonad
import sttp.shared.Identity

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
import scala.math.BigDecimal.RoundingMode

/**
 * Exercises circe JSON calls through the cats integration's backend effect mapping: a
 * tagless-final exchange-rate client written against cats `MonadThrow` runs over a sync backend
 * mapped with `mapK` into `Either[Throwable, *]` and over the Future backend, lifting
 * `asJsonEither` failures into the cats error channel and composing calls with `traverse`,
 * `ensure` and `recoverWith`.
 */
class FxRatesCatsMappedBackendTest {
  private final case class Rate(from: String, to: String, rate: BigDecimal) {
    def invert: Rate = Rate(to, from, (BigDecimal(1) / rate).setScale(4, RoundingMode.HALF_UP))
  }
  private final case class ApiError(code: String, message: String)
  private final case class ConversionRequest(from: String, to: String, amount: BigDecimal)
  private final case class Conversion(from: String, to: String, amount: BigDecimal, converted: BigDecimal)

  private final case class RateUnavailable(code: String, detail: String, status: StatusCode)
      extends Exception(s"$code: $detail")
  private final case class MalformedReply(body: String, cause: Error) extends Exception(body)
  private final case class NonPositiveRate(from: String, to: String) extends Exception(s"$from/$to")

  private type Attempt[A] = Either[Throwable, A]

  /** An application client that only knows its effect through cats `MonadThrow`. */
  private final class RatesClient[F[_]](backend: Backend[F], baseUri: Uri)(using F: MonadThrow[F]) {
    def rate(from: String, to: String): F[Rate] =
      basicRequest
        .get(uri"$baseUri/rates/$from/$to")
        .response(asJsonEither[ApiError, Rate])
        .send(backend)
        .flatMap(response => F.fromEither(response.body.leftMap(toFailure)))
        .ensure(NonPositiveRate(from, to))(_.rate > 0)

    def rateOrInverse(from: String, to: String): F[Rate] =
      rate(from, to).recoverWith { case RateUnavailable("UNKNOWN_PAIR", _, _) =>
        rate(to, from).map(_.invert)
      }

    def basket(pairs: List[(String, String)]): F[List[Rate]] =
      pairs.traverse((from, to) => rate(from, to))

    def convert(from: String, to: String, amount: BigDecimal): F[Conversion] =
      basicRequest
        .post(uri"$baseUri/conversions")
        .body(asJson(ConversionRequest(from, to, amount)))
        .response(asJsonEither[ApiError, Conversion])
        .send(backend)
        .flatMap(response => F.fromEither(response.body.leftMap(toFailure)))

    private def toFailure(failure: ResponseException[ApiError]): Throwable = failure match {
      case ResponseException.UnexpectedStatusCode(error, metadata) =>
        RateUnavailable(error.code, error.message, metadata.code)
      case ResponseException.DeserializationException(body, cause: Error, _) =>
        MalformedReply(body, cause)
      case other =>
        other
    }
  }

  private val rates: Map[(String, String), BigDecimal] = Map(
    ("EUR", "USD") -> BigDecimal("1.0850"),
    ("USD", "JPY") -> BigDecimal("149.20"),
    ("GBP", "EUR") -> BigDecimal("1.1600"),
    ("CHF", "XAU") -> BigDecimal("0")
  )

  private var server: HttpServer = null
  private var serverExecutor: ExecutorService = null
  private var syncBackend: SyncBackend = null
  private val requestedPaths: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
  private val conversionRequests: ConcurrentLinkedQueue[ConversionRequest] =
    new ConcurrentLinkedQueue[ConversionRequest]()

  @BeforeEach
  def startServer(): Unit = {
    serverExecutor = Executors.newFixedThreadPool(4)
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.setExecutor(serverExecutor)
    server.createContext(
      "/rates/",
      (exchange: HttpExchange) => {
        try {
          val path: String = exchange.getRequestURI.getPath
          requestedPaths.add(path)
          path.stripPrefix("/rates/").split('/').toList match {
            case List("ZZZ", _) =>
              respond(exchange, 200, """{"from":"ZZZ","to":""")
            case List(from, to) if rates.contains((from, to)) =>
              respond(exchange, 200, Rate(from, to, rates((from, to))).asJson.noSpaces)
            case List(from, to) =>
              respond(exchange, 404, ApiError("UNKNOWN_PAIR", s"no rate for $from/$to").asJson.noSpaces)
            case _ =>
              respond(exchange, 400, ApiError("BAD_PATH", path).asJson.noSpaces)
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/conversions",
      (exchange: HttpExchange) => {
        try {
          val payload: String = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          decode[ConversionRequest](payload) match {
            case Right(request) if rates.contains((request.from, request.to)) =>
              conversionRequests.add(request)
              val converted: BigDecimal =
                (request.amount * rates((request.from, request.to))).setScale(2, RoundingMode.HALF_UP)
              respond(
                exchange,
                201,
                Conversion(request.from, request.to, request.amount, converted).asJson.noSpaces
              )
            case Right(request) =>
              respond(exchange, 422, ApiError("UNKNOWN_PAIR", s"cannot convert ${request.from}").asJson.noSpaces)
            case Left(error) =>
              respond(exchange, 400, ApiError("BAD_BODY", error.getMessage).asJson.noSpaces)
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    syncBackend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
  }

  @AfterEach
  def stopServer(): Unit = {
    syncBackend.close()
    server.stop(0)
    serverExecutor.shutdownNow()
  }

  @Test
  def mappedBackendRunsTaglessClientInEither(): Unit = {
    val client: RatesClient[Attempt] = new RatesClient[Attempt](attemptBackend, baseUri)

    assertThat(client.rate("EUR", "USD")).isEqualTo(Right(Rate("EUR", "USD", BigDecimal("1.0850"))))
    assertThat(client.basket(List(("EUR", "USD"), ("USD", "JPY"))))
      .isEqualTo(Right(List(Rate("EUR", "USD", BigDecimal("1.0850")), Rate("USD", "JPY", BigDecimal("149.20")))))

    requestedPaths.clear()
    val shortCircuited: Attempt[List[Rate]] = client.basket(List(("GBP", "EUR"), ("EUR", "SEK"), ("USD", "JPY")))
    assertThat(shortCircuited)
      .isEqualTo(Left(RateUnavailable("UNKNOWN_PAIR", "no rate for EUR/SEK", StatusCode.NotFound)))
    assertThat(requestedPaths.asScala.toList).isEqualTo(List("/rates/GBP/EUR", "/rates/EUR/SEK"))

    assertThat(client.convert("EUR", "USD", BigDecimal("250.00")))
      .isEqualTo(Right(Conversion("EUR", "USD", BigDecimal("250.00"), BigDecimal("271.25"))))
    assertThat(client.convert("SEK", "USD", BigDecimal("10")))
      .isEqualTo(Left(RateUnavailable("UNKNOWN_PAIR", "cannot convert SEK", StatusCode.UnprocessableEntity)))
    assertThat(conversionRequests.asScala.toList).isEqualTo(List(ConversionRequest("EUR", "USD", BigDecimal("250.00"))))
  }

  @Test
  def catsCombinatorsRecoverAndRejectReplies(): Unit = {
    val client: RatesClient[Attempt] = new RatesClient[Attempt](attemptBackend, baseUri)

    requestedPaths.clear()
    assertThat(client.rateOrInverse("EUR", "GBP")).isEqualTo(Right(Rate("EUR", "GBP", BigDecimal("0.8621"))))
    assertThat(requestedPaths.asScala.toList).isEqualTo(List("/rates/EUR/GBP", "/rates/GBP/EUR"))
    assertThat(client.rateOrInverse("NOK", "DKK"))
      .isEqualTo(Left(RateUnavailable("UNKNOWN_PAIR", "no rate for DKK/NOK", StatusCode.NotFound)))

    assertThat(client.rate("CHF", "XAU")).isEqualTo(Left(NonPositiveRate("CHF", "XAU")))

    client.rate("ZZZ", "EUR") match {
      case Left(MalformedReply(body, cause)) =>
        assertThat(body).isEqualTo("""{"from":"ZZZ","to":""")
        assertThat(cause).isInstanceOf(classOf[ParsingFailure])
      case other =>
        throw new AssertionError(s"Expected the malformed reply in the error channel, got $other")
    }
    assertThat(client.rateOrInverse("ZZZ", "EUR").left.map(_.getClass)).isEqualTo(Left(classOf[MalformedReply]))
    assertThat(client.rate("ZZZ", "EUR").handleError(_ => Rate("ZZZ", "EUR", BigDecimal(1))))
      .isEqualTo(Right(Rate("ZZZ", "EUR", BigDecimal(1))))
  }

  @Test
  def sameClientRunsOverFutureBackend(): Unit = {
    val clientExecutor: ExecutorService = Executors.newFixedThreadPool(4)
    given ExecutionContext = ExecutionContext.fromExecutorService(clientExecutor)
    val futureBackend: Backend[Future] = DefaultFutureBackend()
    try {
      val client: RatesClient[Future] = new RatesClient[Future](futureBackend, baseUri)

      val basket: List[Rate] =
        Await.result(client.basket(List(("EUR", "USD"), ("USD", "JPY"), ("GBP", "EUR"))), 30.seconds)
      assertThat(basket.map(rate => s"${rate.from}/${rate.to}=${rate.rate}"))
        .isEqualTo(List("EUR/USD=1.0850", "USD/JPY=149.20", "GBP/EUR=1.1600"))

      val inverse: Rate = Await.result(client.rateOrInverse("JPY", "USD"), 30.seconds)
      assertThat(inverse).isEqualTo(Rate("JPY", "USD", BigDecimal("0.0067")))

      val conversion: Conversion = Await.result(client.convert("USD", "JPY", BigDecimal("40")), 30.seconds)
      assertThat(conversion.converted).isEqualTo(BigDecimal("5968.00"))

      val refused: Attempt[Rate] = Await.result(client.rate("EUR", "SEK").attempt, 30.seconds)
      assertThat(refused).isEqualTo(Left(RateUnavailable("UNKNOWN_PAIR", "no rate for EUR/SEK", StatusCode.NotFound)))
    } finally {
      futureBackend.close()
      clientExecutor.shutdownNow()
    }
  }

  /** The sync backend mapped by the cats integration into the `Either[Throwable, *]` effect. */
  private def attemptBackend: Backend[Attempt] = {
    val toAttempt: FunctionK[Identity, Attempt] = new FunctionK[Identity, Attempt] {
      def apply[A](fa: Identity[A]): Attempt[A] = Right(fa)
    }
    val toIdentity: FunctionK[Attempt, Identity] = new FunctionK[Attempt, Identity] {
      def apply[A](fa: Attempt[A]): Identity[A] = fa.fold(error => throw error, value => value)
    }
    val backend: Backend[Identity] = syncBackend
    backend.mapK(toAttempt, toIdentity)(using EitherMonad)
  }

  private def baseUri: Uri = uri"http://127.0.0.1:${server.getAddress.getPort}"

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
