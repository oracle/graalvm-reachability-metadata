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
import sttp.client4.wrappers.EitherBackend
import sttp.client4.wrappers.TryBackend
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.monad.syntax.*
import sttp.shared.Identity

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Exercises circe JSON exchanges over the effect-wrapping backends: a pricing client written
 * against any effect through the backend's `MonadError` runs unchanged over the direct sync
 * backend, `TryBackend` and `EitherBackend`; `TryBackend` reports `asJsonOrFail` decoding and
 * status failures and read timeouts as failed tries, and both recover through `handleError`.
 */
class PricingQuoteEffectBackendsTest {
  private final case class Quote(sku: String, unitPrice: BigDecimal, currency: String, validForMinutes: Int)
  private final case class Acceptance(sku: String, quantity: Int, agreedPrice: BigDecimal)
  private final case class Confirmation(reference: String, sku: String, total: BigDecimal)

  private type Attempt[T] = Either[Throwable, T]

  /** An application client that only knows its effect through the backend's `MonadError`. */
  private final class QuoteClient[F[_]](backend: Backend[F], baseUri: Uri) {
    private given MonadError[F] = backend.monad

    def quote(sku: String): F[Quote] =
      basicRequest
        .get(uri"$baseUri/quotes/$sku")
        .response(asJsonOrFail[Quote])
        .readTimeout(10.seconds)
        .send(backend)
        .map(_.body)

    def accept(quote: Quote, quantity: Int): F[Confirmation] =
      basicRequest
        .post(uri"$baseUri/acceptances")
        .body(asJson(Acceptance(quote.sku, quantity, quote.unitPrice)))
        .response(asJsonOrFail[Confirmation])
        .readTimeout(10.seconds)
        .send(backend)
        .map(_.body)

    def quoteAndAccept(sku: String, quantity: Int): F[Confirmation] =
      quote(sku).flatMap(found => accept(found, quantity))

    def quoteOrList(sku: String, listPrice: Quote): F[Quote] =
      quote(sku).handleError { case _: SttpClientException.ResponseHandlingException[?] =>
        summon[MonadError[F]].unit(listPrice)
      }
  }

  private var server: HttpServer = null
  private var serverExecutor: ExecutorService = null
  private var backend: SyncBackend = null
  private val slowReply: CountDownLatch = new CountDownLatch(1)
  private val acceptances: ConcurrentLinkedQueue[Acceptance] = new ConcurrentLinkedQueue[Acceptance]()

  @BeforeEach
  def startServer(): Unit = {
    serverExecutor = Executors.newFixedThreadPool(4)
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.setExecutor(serverExecutor)
    server.createContext(
      "/quotes/",
      (exchange: HttpExchange) => {
        try {
          exchange.getRequestURI.getPath.stripPrefix("/quotes/") match {
            case "KB-01" =>
              respond(exchange, 200, Quote("KB-01", BigDecimal("49.90"), "EUR", 15).asJson.noSpaces)
            case "MN-27" =>
              respond(exchange, 200, Quote("MN-27", BigDecimal("229.00"), "EUR", 5).asJson.noSpaces)
            case "BROKEN" =>
              respond(exchange, 200, """{"sku":"BROKEN","unitPrice":""")
            case "SLOW" =>
              slowReply.await(30, TimeUnit.SECONDS)
              respond(exchange, 200, Quote("SLOW", BigDecimal("1.00"), "EUR", 1).asJson.noSpaces)
            case sku =>
              respond(exchange, 404, s"""{"error":"no quote for $sku"}""")
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/acceptances",
      (exchange: HttpExchange) => {
        try {
          val payload: String = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          decode[Acceptance](payload) match {
            case Right(acceptance) =>
              acceptances.add(acceptance)
              val total: BigDecimal = acceptance.agreedPrice * acceptance.quantity
              val confirmation: Confirmation =
                Confirmation(s"ACC-${acceptances.size}", acceptance.sku, total)
              respond(exchange, 201, confirmation.asJson.noSpaces)
            case Left(error) =>
              respond(exchange, 400, s"""{"error":"${error.getMessage}"}""")
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
    slowReply.countDown()
    backend.close()
    server.stop(0)
    serverExecutor.shutdownNow()
  }

  @Test
  def tryBackendTurnsDecodingAndStatusFailuresIntoFailedTries(): Unit = {
    val client: QuoteClient[Try] = new QuoteClient[Try](TryBackend(backend), baseUri)

    val confirmed: Try[Confirmation] = client.quoteAndAccept("KB-01", 3)
    assertThat(confirmed).isEqualTo(Success(Confirmation("ACC-1", "KB-01", BigDecimal("149.70"))))
    assertThat(acceptances.asScala.toList).isEqualTo(List(Acceptance("KB-01", 3, BigDecimal("49.90"))))

    client.quote("BROKEN") match {
      case Failure(failure: SttpClientException.ResponseHandlingException[?]) =>
        failure.getCause match {
          case ResponseException.DeserializationException(body, cause, metadata) =>
            assertThat(body).isEqualTo("""{"sku":"BROKEN","unitPrice":""")
            assertThat(cause).isInstanceOf(classOf[ParsingFailure])
            assertThat(metadata.code).isEqualTo(StatusCode.Ok)
          case other =>
            throw new AssertionError(s"Expected a deserialization failure, got $other")
        }
      case other =>
        throw new AssertionError(s"Expected a failed Try for the broken quote, got $other")
    }

    client.quote("GONE-5") match {
      case Failure(failure: SttpClientException.ResponseHandlingException[?]) =>
        failure.getCause match {
          case ResponseException.UnexpectedStatusCode(body, metadata) =>
            assertThat(body).isEqualTo("""{"error":"no quote for GONE-5"}""")
            assertThat(metadata.code).isEqualTo(StatusCode.NotFound)
          case other =>
            throw new AssertionError(s"Expected an unexpected-status failure, got $other")
        }
      case other =>
        throw new AssertionError(s"Expected a failed Try for the missing quote, got $other")
    }

    val listPrice: Quote = Quote("GONE-5", BigDecimal("10.00"), "EUR", 0)
    assertThat(client.quoteOrList("GONE-5", listPrice)).isEqualTo(Success(listPrice))
    assertThat(client.quoteOrList("MN-27", listPrice))
      .isEqualTo(Success(Quote("MN-27", BigDecimal("229.00"), "EUR", 5)))
    assertThat(acceptances.size).isEqualTo(1)

    val slow: Try[Response[Either[ResponseException[String], Quote]]] = basicRequest
      .get(uri"$baseUri/quotes/SLOW")
      .response(asJson[Quote])
      .readTimeout(300.millis)
      .send(TryBackend(backend))
    slow match {
      case Failure(timeout: SttpClientException.TimeoutException) =>
        assertThat(timeout.request.uri.path).isEqualTo(List("quotes", "SLOW"))
      case other =>
        throw new AssertionError(s"Expected the read timeout as a failed Try, got $other")
    }
    slowReply.countDown()
  }

  @Test
  def eitherBackendKeepsLenientDecodingFailuresInsideSuccessfulExchanges(): Unit = {
    val attempts: Backend[Attempt] = EitherBackend(backend)
    val client: QuoteClient[Attempt] = new QuoteClient[Attempt](attempts, baseUri)

    val lenient: Attempt[Response[Either[ResponseException[String], Quote]]] = basicRequest
      .get(uri"$baseUri/quotes/BROKEN")
      .response(asJson[Quote])
      .readTimeout(10.seconds)
      .send(attempts)
    lenient match {
      case Right(response) =>
        assertThat(response.code).isEqualTo(StatusCode.Ok)
        response.body match {
          case Left(ResponseException.DeserializationException(_, cause, _)) =>
            assertThat(cause).isInstanceOf(classOf[ParsingFailure])
          case other =>
            throw new AssertionError(s"Expected the body to carry the parsing failure, got $other")
        }
      case Left(error) =>
        throw new AssertionError(s"Expected the exchange itself to succeed, got $error")
    }

    val listPrice: Quote = Quote("GONE-5", BigDecimal("10.00"), "EUR", 0)
    assertThat(client.quoteOrList("GONE-5", listPrice)).isEqualTo(Right(listPrice))
    assertThat(client.quoteOrList("BROKEN", listPrice)).isEqualTo(Right(listPrice))

    assertThat(client.quoteAndAccept("MN-27", 2))
      .isEqualTo(Right(Confirmation("ACC-1", "MN-27", BigDecimal("458.00"))))
  }

  @Test
  def sameClientDecodesIdenticallyOverDirectAndWrappedBackends(): Unit = {
    val direct: QuoteClient[Identity] = new QuoteClient[Identity](backend, baseUri)
    val tried: QuoteClient[Try] = new QuoteClient[Try](TryBackend(backend), baseUri)
    val attempted: QuoteClient[Attempt] = new QuoteClient[Attempt](EitherBackend(backend), baseUri)

    val expected: Quote = Quote("KB-01", BigDecimal("49.90"), "EUR", 15)
    assertThat(direct.quote("KB-01")).isEqualTo(expected)
    assertThat(tried.quote("KB-01")).isEqualTo(Success(expected))
    assertThat(attempted.quote("KB-01")).isEqualTo(Right(expected))

    val thrown: Throwable =
      try {
        direct.quote("BROKEN")
        throw new AssertionError("Expected the direct backend to throw for the broken quote")
      } catch {
        case e: SttpClientException.ResponseHandlingException[?] => e
      }
    val tryFailure: Throwable = tried.quote("BROKEN").failed.get
    List(thrown, tryFailure).foreach { failure =>
      assertThat(failure).isInstanceOf(classOf[SttpClientException.ResponseHandlingException[?]])
      assertThat(failure.getCause).isInstanceOf(classOf[ResponseException.DeserializationException])
    }

    val references: List[String] = List(
      direct.quoteAndAccept("KB-01", 1).reference,
      tried.quoteAndAccept("KB-01", 1).get.reference,
      attempted.quoteAndAccept("KB-01", 1).fold(error => throw error, _.reference)
    )
    assertThat(references).isEqualTo(List("ACC-1", "ACC-2", "ACC-3"))
    assertThat(acceptances.asScala.map(_.agreedPrice).toSet).isEqualTo(Set(BigDecimal("49.90")))
  }

  private def baseUri: Uri = uri"http://127.0.0.1:${server.getAddress.getPort}"

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
